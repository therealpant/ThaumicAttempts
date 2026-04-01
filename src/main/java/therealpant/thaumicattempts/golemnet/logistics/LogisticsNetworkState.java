package therealpant.thaumicattempts.golemnet.logistics;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import therealpant.thaumicattempts.api.ICraftEndpoint;
import therealpant.thaumicattempts.golemnet.planner.ProviderType;
import therealpant.thaumicattempts.golemnet.tile.TileMirrorManager;
import therealpant.thaumicattempts.util.ItemKey;
import therealpant.thaumicattempts.util.ResourceIdentity;

import javax.annotation.Nullable;
import java.util.*;

public class LogisticsNetworkState {
    private static final Logger LOG = LogManager.getLogger("ThaumicAttempts/LogisticsHandler");
    private static final int MAX_SUBORDER_DEPTH = 8;

    private final LinkedHashMap<UUID, NetworkOrder> orders = new LinkedHashMap<UUID, NetworkOrder>();
    private final LinkedHashMap<UUID, RuntimeTask> runtimeTasks = new LinkedHashMap<UUID, RuntimeTask>();
    private final LinkedHashMap<String, UUID> activeTaskDedup = new LinkedHashMap<String, UUID>();
    private final LinkedHashMap<String, UUID> activeOrderDedup = new LinkedHashMap<String, UUID>();
    private final LinkedHashMap<ItemKey, List<RecipeNode>> recipesByResult = new LinkedHashMap<ItemKey, List<RecipeNode>>();
    private final ResourceReservationBook reservationBook = new ResourceReservationBook();

    private final ManagerExecutor managerExecutor = new ManagerExecutor();
    private final CrafterExecutor crafterExecutor = new CrafterExecutor();

    public UUID submitOrder(TileMirrorManager manager,
                            ItemKey key,
                            int amount,
                            OrderSourceType sourceType,
                            BlockPos sourcePos,
                            @Nullable BlockPos returnDestination,
                            @Nullable UUID parentOrderId,
                            int depth,
                            String reason) {
        if (key == null || key == ItemKey.EMPTY || amount <= 0) return null;
        String dedupeKey = sourceType.name() + "|" + sourcePos.toLong() + "|" + key.hashCode() + "|" + amount + "|" + (parentOrderId == null ? "root" : parentOrderId.toString());
        UUID existing = activeOrderDedup.get(dedupeKey);
        if (existing != null && orders.containsKey(existing)) {
            return existing;
        }

        long now = manager.getServerTickCounter();
        NetworkOrder order = new NetworkOrder();
        order.orderId = UUID.randomUUID();
        order.parentOrderId = parentOrderId;
        order.sourceType = sourceType;
        order.sourcePos = sourcePos.toImmutable();
        order.returnDestination = returnDestination == null ? null : returnDestination.toImmutable();
        order.requestedKey = key;
        order.requestedAmount = Math.max(1, amount);
        order.completedAmount = 0;
        order.status = OrderStatus.NEW;
        order.createdTick = now;
        order.updatedTick = now;
        order.debugReason = reason == null ? "" : reason;
        orders.put(order.orderId, order);
        activeOrderDedup.put(dedupeKey, order.orderId);
        LOG.info("[Logistics {}] request accepted order={} source={} key={} amount={} reason={}", manager.getPos(), order.orderId, sourceType, key, amount, reason);

        if (parentOrderId != null) {
            NetworkOrder parent = orders.get(parentOrderId);
            if (parent != null) parent.childOrderIds.add(order.orderId);
        }

        planOrder(manager, order, depth);
        return order.orderId;
    }

    private void planOrder(TileMirrorManager manager, NetworkOrder order, int depth) {
        order.status = OrderStatus.PLANNING;
        order.updatedTick = manager.getServerTickCounter();

        LinkedHashMap<ItemKey, Integer> catalog = manager.getReachableCatalog();
        int reserved = reservationBook.getReservedAmount(order.requestedKey);
        int available = Math.max(0, catalog.getOrDefault(order.requestedKey, 0) - reserved);
        int need = Math.max(0, order.requestedAmount - available);

        if (available > 0) {
            TransferTask direct = createTransferTask(manager, order.orderId,
                    EndpointRef.of(manager.getPos()),
                    EndpointRef.of(order.returnDestination != null ? order.returnDestination : order.sourcePos),
                    order.requestedKey,
                    Math.min(order.requestedAmount, available),
                    null,
                    "direct-stock");
            direct.status = TaskStatus.READY;
            direct.reservedAmount = reservationBook.reserveStock(manager.getPos(), order.requestedKey, (int) direct.amount, order.orderId, direct.taskId);
        }

        if (need > 0) {
            RecipeNode recipe = pickRecipe(order.requestedKey);
            if (recipe == null) {
                order.status = OrderStatus.FAILED;
                order.lastError = "No recipe for " + order.requestedKey;
                LOG.warn("[Logistics {}] order failed={} reason={}", manager.getPos(), order.orderId, order.lastError);
                return;
            }
            if (depth >= MAX_SUBORDER_DEPTH) {
                order.status = OrderStatus.FAILED;
                order.lastError = "max sub-order depth reached";
                return;
            }

            int cycles = (need + recipe.outputPerCycle - 1) / recipe.outputPerCycle;
            List<UUID> inputDeliveries = new ArrayList<UUID>();
            for (Map.Entry<ItemKey, Integer> in : recipe.inputs.entrySet()) {
                int inputNeed = Math.max(1, in.getValue()) * cycles;
                UUID subOrderId = submitOrder(manager, in.getKey(), inputNeed, OrderSourceType.INTERNAL_SUBORDER, recipe.source, null, order.orderId, depth + 1, "input-for-" + order.requestedKey);
                if (subOrderId != null) {
                    NetworkOrder child = orders.get(subOrderId);
                    if (child != null) inputDeliveries.addAll(child.taskIds);
                }
            }
            TransferTask supplyInputs = createTransferTask(manager, order.orderId,
                    EndpointRef.of(manager.getPos()), EndpointRef.of(recipe.source), order.requestedKey, 0, inputDeliveries, "inputs-ready-marker");
            supplyInputs.status = inputDeliveries.isEmpty() ? TaskStatus.READY : TaskStatus.WAITING_DEPENDENCY;

            CraftTask craft = createCraftTask(manager, order.orderId, EndpointRef.of(recipe.source), order.requestedKey,
                    cycles * recipe.outputPerCycle, recipe.inputs, Collections.singletonList(supplyInputs.taskId), "execute-craft");
            craft.status = supplyInputs.status == TaskStatus.READY ? TaskStatus.READY : TaskStatus.WAITING_DEPENDENCY;

            TransferTask pickup = createTransferTask(manager, order.orderId,
                    EndpointRef.of(recipe.source), EndpointRef.of(manager.getPos()), order.requestedKey, need,
                    Collections.singletonList(craft.taskId), "pickup-output");
            TransferTask deliver = createTransferTask(manager, order.orderId,
                    EndpointRef.of(manager.getPos()),
                    EndpointRef.of(order.returnDestination != null ? order.returnDestination : order.sourcePos),
                    order.requestedKey,
                    need,
                    Collections.singletonList(pickup.taskId),
                    "deliver-output");
            reservationBook.claimExpectedOutput(recipe.source, order.requestedKey, need, order.orderId, pickup.taskId);
            if (deliver != null) { /* keep */ }
        }

        order.status = OrderStatus.RUNNING;
        LOG.info("[Logistics {}] order planned={} key={} amount={}", manager.getPos(), order.orderId, order.requestedKey, order.requestedAmount);
    }

    private TransferTask createTransferTask(TileMirrorManager manager,
                                            UUID orderId,
                                            EndpointRef source,
                                            EndpointRef target,
                                            ItemKey key,
                                            long amount,
                                            @Nullable List<UUID> dependsOn,
                                            String purpose) {
        String dedupe = "T|" + orderId + "|" + source.pos.toLong() + "|" + target.pos.toLong() + "|" + key.hashCode() + "|" + purpose;
        UUID existing = activeTaskDedup.get(dedupe);
        if (existing != null && runtimeTasks.containsKey(existing) && runtimeTasks.get(existing) instanceof TransferTask) {
            return (TransferTask) runtimeTasks.get(existing);
        }

        TransferTask task = new TransferTask();

        task.taskId = UUID.randomUUID();
        task.orderId = orderId;
        task.itemKey = key;
        task.amount = Math.max(0, amount);
        task.source = source;
        task.target = target;
        task.status = TaskStatus.NEW;
        task.createdTick = manager.getServerTickCounter();
        task.updatedTick = task.createdTick;
        if (dependsOn != null) task.dependsOn.addAll(dependsOn);
        task.metaPurpose = purpose;
        runtimeTasks.put(task.taskId, task);
        activeTaskDedup.put(dedupe, task.taskId);

        NetworkOrder order = orders.get(orderId);
        if (order != null) order.taskIds.add(task.taskId);
        LOG.info("[Logistics {}] runtime task created transfer={} order={} key={} amount={} src={} dst={}",
                manager.getPos(), task.taskId, orderId, key, task.amount, source.pos, target.pos);
        return task;
    }

    private CraftTask createCraftTask(TileMirrorManager manager,
                                      UUID orderId,
                                      EndpointRef crafter,
                                      ItemKey key,
                                      long amount,
                                      Map<ItemKey, Integer> requiredInputs,
                                      @Nullable List<UUID> dependsOn,
                                      String purpose) {
        CraftTask task = new CraftTask();
        task.taskId = UUID.randomUUID();
        task.orderId = orderId;
        task.crafter = crafter;
        task.outputEndpoint = crafter;
        task.recipeKey = key;
        task.amount = Math.max(1, amount);
        task.requiredInputs.putAll(requiredInputs);
        task.status = TaskStatus.NEW;
        task.createdTick = manager.getServerTickCounter();
        task.updatedTick = task.createdTick;
        if (dependsOn != null) task.dependsOn.addAll(dependsOn);
        task.metaPurpose = purpose;
        runtimeTasks.put(task.taskId, task);
        NetworkOrder order = orders.get(orderId);
        if (order != null) order.taskIds.add(task.taskId);
        LOG.info("[Logistics {}] runtime task created craft={} order={} key={} amount={} crafter={}",
                manager.getPos(), task.taskId, orderId, key, amount, crafter.pos);
        return task;
    }

    @Nullable
    private RecipeNode pickRecipe(ItemKey key) {
        List<RecipeNode> nodes = recipesByResult.get(key);
        if (nodes == null || nodes.isEmpty()) return null;
        return nodes.get(0);
    }

    public void refreshRecipeIndex(TileMirrorManager manager) {
        recipesByResult.clear();
        Set<BlockPos> requesters = manager.getRequestersSnapshot();
        for (BlockPos rp : requesters) {
            TileEntity te = manager.getWorld().getTileEntity(rp);
            if (!(te instanceof ICraftEndpoint)) continue;
            ICraftEndpoint ep = (ICraftEndpoint) te;
            List<ItemStack> outputs = ep.listCraftableResults();
            if (outputs == null) continue;
            for (ItemStack out : outputs) {
                if (out == null || out.isEmpty()) continue;
                ItemKey result = ItemKey.of(out);
                Map<ItemKey, Integer> inputs = new LinkedHashMap<ItemKey, Integer>();
                List<ItemStack> req = ep.getRecipeInputsFor(out, 1);
                if (req != null) {
                    for (ItemStack need : req) {
                        if (need == null || need.isEmpty()) continue;
                        inputs.merge(ItemKey.of(need), Math.max(1, need.getCount()), Integer::sum);
                    }
                }
                ProviderType pt = ProviderType.GOLEM_CRAFTER;
                if (te.getClass().getSimpleName().contains("Infusion")) pt = ProviderType.INFUSION_REQUESTER;
                if (te.getClass().getSimpleName().contains("ResourceRequester")) pt = ProviderType.RESOURCE_REQUESTER;
                RecipeNode node = new RecipeNode(result, rp, pt, Math.max(1, ep.getPerCraftOutputCountFor(out)), inputs);
                List<RecipeNode> nodes = recipesByResult.get(result);
                if (nodes == null) {
                    nodes = new ArrayList<RecipeNode>();
                    recipesByResult.put(result, nodes);
                }
                boolean exists = false;
                for (RecipeNode ex : nodes) {
                    if (ex.source.equals(node.source) && ResourceIdentity.sameResource(ex.result.toStack(1), node.result.toStack(1))) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) nodes.add(node);
            }
        }
    }

    public void tick(TileMirrorManager manager) {
        managerExecutor.bind(manager);
        crafterExecutor.bind(manager);

        for (RuntimeTask task : runtimeTasks.values()) {
            if (task.status == TaskStatus.NEW) task.status = TaskStatus.WAITING_DEPENDENCY;
            if (task.status == TaskStatus.WAITING_DEPENDENCY && depsDone(task)) {
                task.status = TaskStatus.READY;
                task.updatedTick = manager.getServerTickCounter();
            }
            if (task.status != TaskStatus.READY) continue;
            boolean accepted = false;
            if (task instanceof TransferTask) {
                accepted = managerExecutor.canAccept((TransferTask) task) && managerExecutor.submit((TransferTask) task);
            } else if (task instanceof CraftTask) {
                accepted = crafterExecutor.canAccept((CraftTask) task) && crafterExecutor.submit((CraftTask) task);
            }
            if (accepted) {
                task.status = TaskStatus.DISPATCHED;
                LOG.info("[Logistics {}] task dispatched id={} type={}", manager.getPos(), task.taskId, task.getTaskType());
            }
        }

        managerExecutor.tick();
        crafterExecutor.tick();

        collectSnapshot(manager, managerExecutor);
        collectSnapshot(manager, crafterExecutor);
        updateOrders(manager);
    }

    private <T extends RuntimeTask> void collectSnapshot(TileMirrorManager manager, ILogisticsExecutor<T> executor) {
        for (RuntimeTask task : runtimeTasks.values()) {
            if (!executor.accepts(task)) continue;
            TaskExecutionSnapshot snapshot = executor.getSnapshot(task.taskId);
            if (snapshot == null) continue;
            task.status = snapshot.status;
            task.completedAmount = snapshot.completedAmount;
            task.updatedTick = manager.getServerTickCounter();
        }
    }

    private boolean depsDone(RuntimeTask task) {
        for (UUID dep : task.dependsOn) {
            RuntimeTask depTask = runtimeTasks.get(dep);
            if (depTask == null || depTask.status != TaskStatus.DONE) {
                return false;
            }
        }
        return true;
    }

    private void updateOrders(TileMirrorManager manager) {
        for (NetworkOrder order : orders.values()) {
            if (order.status == OrderStatus.DONE || order.status == OrderStatus.FAILED || order.status == OrderStatus.CANCELED) continue;
            boolean allDone = true;
            for (UUID tid : order.taskIds) {
                RuntimeTask t = runtimeTasks.get(tid);
                if (t == null) continue;
                if (t.status == TaskStatus.FAILED || t.status == TaskStatus.BLOCKED || t.status == TaskStatus.CANCELED) {
                    order.status = OrderStatus.FAILED;
                    order.lastError = "task-failed:" + tid;
                    LOG.warn("[Logistics {}] order failed={} task={} status={}", manager.getPos(), order.orderId, tid, t.status);
                    allDone = false;
                    break;
                }
                if (t.status != TaskStatus.DONE) allDone = false;
            }
            if (allDone) {
                order.status = OrderStatus.DONE;
                order.completedAmount = order.requestedAmount;
                LOG.info("[Logistics {}] order done={} key={} amount={}", manager.getPos(), order.orderId, order.requestedKey, order.requestedAmount);
            } else if (order.status == OrderStatus.PLANNING || order.status == OrderStatus.NEW) {
                order.status = OrderStatus.RUNNING;
            }
            order.updatedTick = manager.getServerTickCounter();
        }
    }

    public void writeToNbt(NBTTagCompound tag) {
        NBTTagList ord = new NBTTagList();
        for (NetworkOrder o : orders.values()) ord.appendTag(o.writeToNbt());
        tag.setTag("orders", ord);

        NBTTagList rt = new NBTTagList();
        for (RuntimeTask task : runtimeTasks.values()) rt.appendTag(task.writeToNbt());
        tag.setTag("runtimeTasks", rt);


        NBTTagList recipes = new NBTTagList();
        for (List<RecipeNode> list : recipesByResult.values()) {
            for (RecipeNode node : list) recipes.appendTag(node.writeToNbt());
        }
        tag.setTag("recipes", recipes);
        tag.setTag("reservations", reservationBook.writeToNbt());
    }

    public void readFromNbt(NBTTagCompound tag) {
        orders.clear();
        runtimeTasks.clear();
        recipesByResult.clear();
        activeOrderDedup.clear();
        activeTaskDedup.clear();

        NBTTagList ord = tag.getTagList("orders", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < ord.tagCount(); i++) {
            NetworkOrder o = NetworkOrder.readFromNbt(ord.getCompoundTagAt(i));
            orders.put(o.orderId, o);
        }

        NBTTagList rt = tag.getTagList("runtimeTasks", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < rt.tagCount(); i++) {
            RuntimeTask task = RuntimeTask.readFromNbt(rt.getCompoundTagAt(i));
            runtimeTasks.put(task.taskId, task);
        }

        NBTTagList recipes = tag.getTagList("recipes", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < recipes.tagCount(); i++) {
            RecipeNode node = RecipeNode.readFromNbt(recipes.getCompoundTagAt(i));
            List<RecipeNode> list = recipesByResult.get(node.result);
            if (list == null) {
                list = new ArrayList<RecipeNode>();
                recipesByResult.put(node.result, list);
            }
            list.add(node);
        }

        if (tag.hasKey("reservations", Constants.NBT.TAG_COMPOUND)) {
            reservationBook.readFromNbt(tag.getCompoundTag("reservations"));
        }
    }
}