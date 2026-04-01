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
    private static final Logger LOG = LogManager.getLogger("ThaumicAttempts/LogisticsNetworkState");
    private static final int MAX_SUBORDER_DEPTH = 8;

    private final LinkedHashMap<UUID, NetworkOrder> orders = new LinkedHashMap<UUID, NetworkOrder>();
    private final LinkedHashMap<UUID, LogisticsTask> tasks = new LinkedHashMap<UUID, LogisticsTask>();
    private final LinkedHashMap<String, UUID> activeTaskDedup = new LinkedHashMap<String, UUID>();
    private final LinkedHashMap<String, UUID> activeOrderDedup = new LinkedHashMap<String, UUID>();
    private final LinkedHashMap<ItemKey, List<RecipeNode>> recipesByResult = new LinkedHashMap<ItemKey, List<RecipeNode>>();
    private final ResourceReservationBook reservationBook = new ResourceReservationBook();

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
            LOG.info("[Logistics {}] dedupe skipped duplicate order={} key={} amount={} reason={}", manager.getPos(), existing, key, amount, reason);
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
        order.status = OrderStatus.PLACED;
        order.createdTick = now;
        order.updatedTick = now;
        order.debugReason = reason == null ? "" : reason;
        orders.put(order.orderId, order);
        activeOrderDedup.put(dedupeKey, order.orderId);
        LOG.info("[Logistics {}] order created id={} parent={} source={} key={} amount={} return={} reason={}",
                manager.getPos(), order.orderId, order.parentOrderId, order.sourceType, key, amount, returnDestination, reason);

        if (parentOrderId != null) {
            NetworkOrder parent = orders.get(parentOrderId);
            if (parent != null) parent.childOrderIds.add(order.orderId);
            LOG.info("[Logistics {}] sub-order created parent={} child={} key={} amount={}", manager.getPos(), parentOrderId, order.orderId, key, amount);
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
            LogisticsTask direct = createTask(manager, order.orderId, TaskType.DIRECT_DELIVERY, order.sourcePos, manager.getPos(), order.returnDestination != null ? order.returnDestination : order.sourcePos, order.requestedKey, Math.min(order.requestedAmount, available), null, "direct-stock");
            direct.status = TaskStatus.RESERVED;
            direct.reservedAmount = reservationBook.reserveStock(manager.getPos(), order.requestedKey, direct.amount, order.orderId, direct.taskId);
            LOG.info("[Logistics {}] task reserved id={} key={} amount={}", manager.getPos(), direct.taskId, direct.key, direct.reservedAmount);
        }

        if (need <= 0) {
            order.status = OrderStatus.IN_PROGRESS;
            return;
        }

        RecipeNode recipe = pickRecipe(order.requestedKey);
        if (recipe == null) {
            order.status = OrderStatus.BLOCKED;
            order.lastError = "No recipe for " + order.requestedKey;
            LOG.warn("[Logistics {}] task blocked order={} key={} amount={} reason=no_recipe", manager.getPos(), order.orderId, order.requestedKey, need);
            return;
        }

        if (depth >= MAX_SUBORDER_DEPTH) {
            order.status = OrderStatus.FAILED;
            order.lastError = "max sub-order depth reached";
            LOG.warn("[Logistics {}] task failed order={} key={} amount={} reason=max_depth", manager.getPos(), order.orderId, order.requestedKey, need);
            return;
        }

        int cycles = (need + recipe.outputPerCycle - 1) / recipe.outputPerCycle;
        List<UUID> inputTasks = new ArrayList<UUID>();
        for (Map.Entry<ItemKey, Integer> in : recipe.inputs.entrySet()) {
            int inputNeed = Math.max(1, in.getValue()) * cycles;
            UUID subOrderId = submitOrder(manager, in.getKey(), inputNeed, OrderSourceType.INTERNAL_SUBORDER, recipe.source, null, order.orderId, depth + 1, "input-for-" + order.requestedKey);
            if (subOrderId != null) {
                NetworkOrder child = orders.get(subOrderId);
                if (child != null && !child.taskIds.isEmpty()) inputTasks.addAll(child.taskIds);
            }
        }

        LogisticsTask execute = createTask(manager, order.orderId, TaskType.EXECUTE_CRAFT, recipe.source, null, recipe.source, order.requestedKey, cycles * recipe.outputPerCycle, inputTasks, "execute-craft");
        execute.executorPos = recipe.source;
        execute.status = inputTasks.isEmpty() ? TaskStatus.PLACED : TaskStatus.WAITING_DEPENDENCY;

        LogisticsTask pickup = createTask(manager, order.orderId, TaskType.PICKUP_OUTPUT, recipe.source, recipe.source, recipe.source, order.requestedKey, need, Collections.singletonList(execute.taskId), "pickup");
        LogisticsTask deliver = createTask(manager, order.orderId, TaskType.DELIVER_OUTPUT, recipe.source, recipe.source,
                order.returnDestination != null ? order.returnDestination : order.sourcePos,
                order.requestedKey,
                need,
                Collections.singletonList(pickup.taskId),
                "deliver-output");

        reservationBook.claimExpectedOutput(recipe.source, order.requestedKey, need, order.orderId, pickup.taskId);
        order.status = OrderStatus.WAITING_RESOURCES;
        LOG.info("[Logistics {}] expected output claimed order={} task={} key={} amount={}", manager.getPos(), order.orderId, pickup.taskId, order.requestedKey, need);
    }

    private LogisticsTask createTask(TileMirrorManager manager,
                                     UUID orderId,
                                     TaskType type,
                                     @Nullable BlockPos executor,
                                     @Nullable BlockPos src,
                                     @Nullable BlockPos dst,
                                     ItemKey key,
                                     int amount,
                                     @Nullable List<UUID> dependsOn,
                                     String purpose) {
        String dedupe = (dst == null ? "-" : Long.toString(dst.toLong())) + "|" + key.hashCode() + "|" + purpose + "|" + orderId;
        UUID existing = activeTaskDedup.get(dedupe);
        if (existing != null && tasks.containsKey(existing)) {
            LOG.info("[Logistics {}] dedupe skipped duplicate task={} key={} amount={} purpose={}", manager.getPos(), existing, key, amount, purpose);
            return tasks.get(existing);
        }

        LogisticsTask task = new LogisticsTask();
        task.taskId = UUID.randomUUID();
        task.orderId = orderId;
        task.type = type;
        task.status = TaskStatus.PLACED;
        task.executorPos = executor == null ? null : executor.toImmutable();
        task.sourcePos = src == null ? null : src.toImmutable();
        task.destinationPos = dst == null ? null : dst.toImmutable();
        task.key = key;
        task.amount = Math.max(1, amount);
        task.createdTick = manager.getServerTickCounter();
        task.updatedTick = task.createdTick;
        if (dependsOn != null) task.dependsOn.addAll(dependsOn);
        task.meta.put("purpose", purpose);
        tasks.put(task.taskId, task);
        activeTaskDedup.put(dedupe, task.taskId);

        NetworkOrder order = orders.get(orderId);
        if (order != null) order.taskIds.add(task.taskId);
        LOG.info("[Logistics {}] task created id={} order={} type={} key={} amount={} src={} dst={} exec={}",
                manager.getPos(), task.taskId, orderId, type, key, task.amount, src, dst, executor);
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
        for (LogisticsTask task : tasks.values()) {
            if (task.status == TaskStatus.WAITING_DEPENDENCY) {
                boolean ok = true;
                for (UUID dep : task.dependsOn) {
                    LogisticsTask depTask = tasks.get(dep);
                    if (depTask == null || depTask.status != TaskStatus.DONE) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    task.status = TaskStatus.PLACED;
                    task.updatedTick = manager.getServerTickCounter();
                    LOG.info("[Logistics {}] task started id={} type={} key={} amount={} reason=deps_done", manager.getPos(), task.taskId, task.type, task.key, task.amount);
                }
            }
        }
    }

    public void writeToNbt(NBTTagCompound tag) {
        NBTTagList ord = new NBTTagList();
        for (NetworkOrder o : orders.values()) ord.appendTag(o.writeToNbt());
        tag.setTag("orders", ord);

        NBTTagList t = new NBTTagList();
        for (LogisticsTask task : tasks.values()) t.appendTag(task.writeToNbt());
        tag.setTag("tasks", t);

        NBTTagList recipes = new NBTTagList();
        for (List<RecipeNode> list : recipesByResult.values()) {
            for (RecipeNode node : list) recipes.appendTag(node.writeToNbt());
        }
        tag.setTag("recipes", recipes);
        tag.setTag("reservations", reservationBook.writeToNbt());
    }

    public void readFromNbt(NBTTagCompound tag) {
        orders.clear();
        tasks.clear();
        recipesByResult.clear();
        activeOrderDedup.clear();
        activeTaskDedup.clear();

        NBTTagList ord = tag.getTagList("orders", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < ord.tagCount(); i++) {
            NetworkOrder o = NetworkOrder.readFromNbt(ord.getCompoundTagAt(i));
            orders.put(o.orderId, o);
        }

        NBTTagList tt = tag.getTagList("tasks", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < tt.tagCount(); i++) {
            LogisticsTask task = LogisticsTask.readFromNbt(tt.getCompoundTagAt(i));
            tasks.put(task.taskId, task);
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