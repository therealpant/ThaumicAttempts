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

    @Nullable
    public OrderStatus getOrderStatus(@Nullable UUID orderId) {
        if (orderId == null) return null;
        NetworkOrder order = orders.get(orderId);
        return order == null ? null : order.status;
    }

    public boolean isOrderActive(@Nullable UUID orderId) {
        OrderStatus status = getOrderStatus(orderId);
        return status != null && status != OrderStatus.DONE && status != OrderStatus.FAILED && status != OrderStatus.CANCELED;
    }

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

        int totalReachable = Math.max(0, catalog.getOrDefault(order.requestedKey, 0));
        int localBuffered = Math.max(0, manager.countBuffered(order.requestedKey));
        int reserved = reservationBook.getReservedAmount(order.requestedKey);

        int directAvailable = Math.max(0, localBuffered - reserved);
        int totalAvailable = Math.max(0, totalReachable - reserved);

        int directNow = Math.min(order.requestedAmount, directAvailable);
        int stillNeed = Math.max(0, order.requestedAmount - directNow);

        EndpointRef managerBuffer = EndpointRef.of(manager.getPos(), EndpointRef.AccessMode.BUFFER);
        EndpointRef finalTarget = EndpointRef.of(
                order.returnDestination != null ? order.returnDestination : order.sourcePos,
                EndpointRef.AccessMode.INPUT
        );

        // 1. Что уже реально лежит в буфере менеджера — можно сразу отдать
        if (directNow > 0) {
            TransferTask direct = createTransferTask(
                    manager,
                    order.orderId,
                    managerBuffer,
                    finalTarget,
                    order.requestedKey,
                    directNow,
                    null,
                    "direct-buffer"
            );
            if (direct != null) {
                direct.status = TaskStatus.NEW;
                direct.updatedTick = manager.getServerTickCounter();
            }
        }

        // 2. Если остального не нужно — заказ уже закрывается доставкой из локального буфера
        if (stillNeed <= 0) {
            order.status = OrderStatus.RUNNING;
            LOG.info("[Logistics {}] order planned={} key={} amount={} directOnly={}",
                    manager.getPos(), order.orderId, order.requestedKey, order.requestedAmount, directNow);
            return;
        }

        // 3. Если результат есть в сети, но не лежит в буфере менеджера — сначала inbound в manager, потом выдача
        int networkRest = Math.max(0, totalAvailable - directNow);
        if (networkRest > 0) {
            int inboundAmount = Math.min(stillNeed, networkRest);

            TransferTask inbound = createTransferTask(
                    manager,
                    order.orderId,
                    EndpointRef.of(order.sourcePos, EndpointRef.AccessMode.DIRECT),
                    managerBuffer,
                    order.requestedKey,
                    inboundAmount,
                    null,
                    "stock-to-manager"
            );
            if (inbound != null) {
                inbound.status = TaskStatus.NEW;
                inbound.updatedTick = manager.getServerTickCounter();

                TransferTask deliverAfterInbound = createTransferTask(
                        manager,
                        order.orderId,
                        managerBuffer,
                        finalTarget,
                        order.requestedKey,
                        inboundAmount,
                        Collections.singletonList(inbound.taskId),
                        "deliver-after-inbound"
                );
                if (deliverAfterInbound != null) {
                    deliverAfterInbound.status = TaskStatus.NEW;
                    deliverAfterInbound.updatedTick = manager.getServerTickCounter();
                }
            }

            stillNeed -= inboundAmount;
        }

        // 4. Если после stock/inbound остаток нулевой — крафт не нужен
        if (stillNeed <= 0) {
            order.status = OrderStatus.RUNNING;
            LOG.info("[Logistics {}] order planned={} key={} amount={} direct={} inbound={}",
                    manager.getPos(), order.orderId, order.requestedKey, order.requestedAmount,
                    directNow, Math.max(0, order.requestedAmount - directNow));
            return;
        }

        // 5. Теперь идем строго в recipe path для остатка, и больше НЕ используем requestedKey как stock input
        RecipeNode recipe = pickRecipe(order.requestedKey);
        if (recipe == null || recipe.source == null) {
            order.status = OrderStatus.FAILED;
            order.lastError = "No recipe for " + order.requestedKey;
            LOG.warn("[Logistics {}] order failed={} key={} amount={} reason=no_recipe",
                    manager.getPos(), order.orderId, order.requestedKey, stillNeed);
            return;
        }

        if (depth >= MAX_SUBORDER_DEPTH) {
            order.status = OrderStatus.FAILED;
            order.lastError = "max sub-order depth reached";
            LOG.warn("[Logistics {}] order failed={} key={} amount={} reason=max_depth",
                    manager.getPos(), order.orderId, order.requestedKey, stillNeed);
            return;
        }

        int perCycle = Math.max(1, recipe.outputPerCycle);
        int cycles = (stillNeed + perCycle - 1) / perCycle;

        List<UUID> inputDeps = new ArrayList<UUID>();

        EndpointRef crafterInput = EndpointRef.of(recipe.source, EndpointRef.AccessMode.INPUT);
        EndpointRef crafterOutput = EndpointRef.of(recipe.source, EndpointRef.AccessMode.OUTPUT);

        // 6. Для каждого ингредиента из шаблона строим доставку сырья или suborder на сырье
        for (Map.Entry<ItemKey, Integer> in : recipe.inputs.entrySet()) {
            ItemKey inputKey = in.getKey();
            int perCycleNeed = Math.max(1, in.getValue());
            int inputNeed = perCycleNeed * cycles;

            if (inputKey == null || inputKey == ItemKey.EMPTY || inputKey.equals(order.requestedKey)) {
                LOG.warn("[Logistics {}] skipped invalid recipe input order={} result={} input={} inputNeed={}",
                        manager.getPos(), order.orderId, order.requestedKey, inputKey, inputNeed);
                continue;
            }

            int inputTotalReachable = Math.max(0, catalog.getOrDefault(inputKey, 0));
            int inputReserved = reservationBook.getReservedAmount(inputKey);
            int inputAvailable = Math.max(0, inputTotalReachable - inputReserved);

            if (inputAvailable > 0) {
                int deliverInput = Math.min(inputNeed, inputAvailable);

                TransferTask t = createTransferTask(
                        manager,
                        order.orderId,
                        EndpointRef.of(order.sourcePos, EndpointRef.AccessMode.DIRECT),
                        crafterInput,
                        inputKey,
                        deliverInput,
                        null,
                        "craft-input"
                );
                if (t != null) {
                    t.status = TaskStatus.NEW;
                    t.updatedTick = manager.getServerTickCounter();
                    inputDeps.add(t.taskId);
                }

                inputNeed -= deliverInput;
            }

            if (inputNeed > 0) {
                UUID subOrderId = submitOrder(
                        manager,
                        inputKey,
                        inputNeed,
                        OrderSourceType.INTERNAL_SUBORDER,
                        recipe.source,
                        recipe.source,
                        order.orderId,
                        depth + 1,
                        "input-for-" + order.requestedKey
                );

                if (subOrderId != null) {
                    NetworkOrder child = orders.get(subOrderId);
                    if (child != null && !child.taskIds.isEmpty()) {
                        inputDeps.addAll(child.taskIds);
                    }
                }
            }
        }

        // 7. Task ожидания/исполнения крафта
        CraftTask craft = createCraftTask(
                manager,
                order.orderId,
                crafterInput,
                order.requestedKey,
                stillNeed,
                recipe.inputs,
                inputDeps,
                "execute-craft"
        );

        if (craft == null) {
            order.status = OrderStatus.FAILED;
            order.lastError = "craft-task-build-failed";
            LOG.warn("[Logistics {}] order failed={} key={} reason=craft_task_invalid",
                    manager.getPos(), order.orderId, order.requestedKey);
            return;
        }

        // 8. Забрать результат из output крафтера в manager
        TransferTask pickup = createTransferTask(
                manager,
                order.orderId,
                crafterOutput,
                managerBuffer,
                order.requestedKey,
                stillNeed,
                Collections.singletonList(craft.taskId),
                "pickup-output"
        );
        if (pickup != null) {
            pickup.status = TaskStatus.NEW;
            pickup.updatedTick = manager.getServerTickCounter();
        }

        // 9. Отправить результат заказчику
        TransferTask deliver = createTransferTask(
                manager,
                order.orderId,
                managerBuffer,
                finalTarget,
                order.requestedKey,
                stillNeed,
                pickup == null ? Collections.singletonList(craft.taskId) : Collections.singletonList(pickup.taskId),
                "deliver-output"
        );
        if (deliver != null) {
            deliver.status = TaskStatus.NEW;
            deliver.updatedTick = manager.getServerTickCounter();
        }

        reservationBook.claimExpectedOutput(
                recipe.source,
                order.requestedKey,
                stillNeed,
                order.orderId,
                pickup != null ? pickup.taskId : craft.taskId
        );

        order.status = OrderStatus.RUNNING;
        LOG.info("[Logistics {}] craft order planned order={} key={} amount={} recipeSource={} inputs={}",
                manager.getPos(), order.orderId, order.requestedKey, stillNeed, recipe.source, recipe.inputs);
    }

    private TransferTask createTransferTask(TileMirrorManager manager,
                                            UUID orderId,
                                            EndpointRef source,
                                            EndpointRef target,
                                            ItemKey key,
                                            long amount,
                                            @Nullable List<UUID> dependsOn,
                                            String purpose) {
        if (manager == null
                || orderId == null
                || source == null
                || source.pos == null
                || target == null
                || target.pos == null
                || key == null
                || key == ItemKey.EMPTY
                || amount <= 0) {
            LOG.warn("[Logistics {}] skipped transfer task build order={} key={} amount={} source={} target={} purpose={} reason=invalid-args",
                    manager != null ? manager.getPos() : null,
                    orderId, key, amount,
                    source == null ? null : source.pos,
                    target == null ? null : target.pos,
                    purpose);
            return null;
        }

        NetworkOrder order = orders.get(orderId);
        if ("craft-input".equals(purpose) && order != null && key.equals(order.requestedKey)) {
            LOG.warn("[Logistics {}] skipped invalid craft-input task order={} key={} amount={} source={} target={} reason=requested-key-used-as-input",
                    manager.getPos(), orderId, key, amount, source.pos, target.pos);
            return null;
        }

        String dedupe = "T|" + orderId
                + "|" + source.pos.toLong()
                + "|" + target.pos.toLong()
                + "|" + key.hashCode()
                + "|" + purpose;

        UUID existing = activeTaskDedup.get(dedupe);
        if (existing != null) {
            RuntimeTask existingTask = runtimeTasks.get(existing);
            if (existingTask instanceof TransferTask) {
                TransferTask transfer = (TransferTask) existingTask;
                if (transfer.status != TaskStatus.DONE
                        && transfer.status != TaskStatus.FAILED
                        && transfer.status != TaskStatus.CANCELED) {
                    return transfer;
                }
                activeTaskDedup.remove(dedupe);
            }
        }

        TransferTask task = new TransferTask();
        task.taskId = UUID.randomUUID();
        task.orderId = orderId;
        task.itemKey = key;
        task.amount = amount;
        task.source = source;
        task.target = target;
        task.status = TaskStatus.NEW;
        task.createdTick = manager.getServerTickCounter();
        task.updatedTick = task.createdTick;
        task.metaPurpose = purpose;

        if (dependsOn != null && !dependsOn.isEmpty()) {
            task.dependsOn.addAll(dependsOn);
        }

        runtimeTasks.put(task.taskId, task);
        activeTaskDedup.put(dedupe, task.taskId);

        if (order != null && !order.taskIds.contains(task.taskId)) {
            order.taskIds.add(task.taskId);
        }

        LOG.info("[Logistics {}] runtime task created transfer={} order={} key={} amount={} src={} dst={} purpose={}",
                manager.getPos(),
                task.taskId,
                orderId,
                key,
                task.amount,
                source.pos,
                target.pos,
                purpose);

        return task;
    }

    @Nullable
    private CraftTask createCraftTask(TileMirrorManager manager,
                                      UUID orderId,
                                      EndpointRef crafter,
                                      ItemKey key,
                                      long amount,
                                      Map<ItemKey, Integer> requiredInputs,
                                      @Nullable List<UUID> dependsOn,
                                      String purpose) {
        if (crafter == null || crafter.pos == null || key == null || key == ItemKey.EMPTY || amount <= 0) {
            LOG.info("[Logistics {}] craft task build started order={} key={} crafter={} purpose={}",
                    manager.getPos(), orderId, key, crafter, purpose);
            LOG.warn("[Logistics {}] craft task rejected order={} reason=missing-required crafter={} key={} amount={} purpose={}",
                    manager.getPos(), orderId, crafter, key, amount, purpose);
            return null;
        }

        LOG.info("[Logistics {}] craft task build started order={} key={} amount={} crafter={} purpose={}",
                manager.getPos(), orderId, key, amount, crafter.pos, purpose);

        CraftTask task = new CraftTask();
        task.taskId = UUID.randomUUID();
        task.orderId = orderId;
        task.crafter = crafter;
        task.outputEndpoint = EndpointRef.of(crafter.pos, EndpointRef.AccessMode.OUTPUT);
        task.recipeKey = key;
        task.amount = Math.max(1, amount);

        if (requiredInputs != null) {
            for (Map.Entry<ItemKey, Integer> e : requiredInputs.entrySet()) {
                ItemKey inputKey = e.getKey();
                int inputAmount = (e.getValue() == null ? 0 : e.getValue());

                if (inputKey == null || inputKey == ItemKey.EMPTY) continue;
                if (inputAmount <= 0) continue;

                if (inputKey.equals(task.recipeKey)) {
                    LOG.warn("[Logistics {}] craft task validation failed order={} reason=result-used-as-input key={}",
                            manager.getPos(), orderId, task.recipeKey);
                    return null;
                }

                task.requiredInputs.put(inputKey, inputAmount);
            }
        }

        String invalidReason = task.validationError();
        if (invalidReason != null) {
            LOG.warn("[Logistics {}] craft task validation failed order={} reason={} crafter={} key={} output={} inputs={}",
                    manager.getPos(), orderId, invalidReason, task.crafter, task.recipeKey, task.outputEndpoint, task.requiredInputs.size());
            return null;
        }

        task.status = TaskStatus.NEW;
        task.createdTick = manager.getServerTickCounter();
        task.updatedTick = task.createdTick;

        if (dependsOn != null && !dependsOn.isEmpty()) {
            task.dependsOn.addAll(dependsOn);
        }

        task.metaPurpose = purpose;
        runtimeTasks.put(task.taskId, task);

        NetworkOrder order = orders.get(orderId);
        if (order != null && !order.taskIds.contains(task.taskId)) {
            order.taskIds.add(task.taskId);
        }

        LOG.info("[Logistics {}] craft task created task={} order={} key={} amount={} crafter={} inputs={}",
                manager.getPos(), task.taskId, orderId, key, amount, crafter.pos, task.requiredInputs);

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
        if (manager == null || manager.getWorld() == null) return;

        Set<BlockPos> requesters = manager.getRequestersSnapshot();
        for (BlockPos rp : requesters) {
            TileEntity te = manager.getWorld().getTileEntity(rp);
            if (!(te instanceof ICraftEndpoint)) continue;

            ICraftEndpoint ep = (ICraftEndpoint) te;
            List<ItemStack> outputs = ep.listCraftableResults();
            if (outputs == null || outputs.isEmpty()) continue;

            for (ItemStack out : outputs) {
                if (out == null || out.isEmpty()) continue;

                ItemKey result = ItemKey.of(out);
                if (result == null || result == ItemKey.EMPTY) continue;

                Map<ItemKey, Integer> inputs = new LinkedHashMap<>();
                List<ItemStack> req = ep.getRecipeInputsFor(out, 1);
                if (req != null) {
                    for (ItemStack need : req) {
                        if (need == null || need.isEmpty()) continue;
                        ItemKey nk = ItemKey.of(need);
                        if (nk == null || nk == ItemKey.EMPTY) continue;
                        inputs.merge(nk, Math.max(1, need.getCount()), Integer::sum);
                    }
                }

                int perCraft = Math.max(1, ep.getPerCraftOutputCountFor(out));
                ProviderType pt = ProviderType.GOLEM_CRAFTER;

                if (te.getClass().getSimpleName().contains("Infusion")) {
                    pt = ProviderType.INFUSION_REQUESTER;
                } else if (te.getClass().getSimpleName().contains("ResourceRequester")) {
                    pt = ProviderType.RESOURCE_REQUESTER;
                } else if (te.getClass().getSimpleName().contains("PatternRequester")) {
                    pt = ProviderType.GOLEM_CRAFTER;
                }

                RecipeNode node = new RecipeNode(result, rp, pt, perCraft, inputs);

                List<RecipeNode> nodes = recipesByResult.get(result);
                if (nodes == null) {
                    nodes = new ArrayList<>();
                    recipesByResult.put(result, nodes);
                }

                boolean exists = false;
                for (RecipeNode ex : nodes) {
                    if (ex.source.equals(node.source)
                            && ex.outputPerCycle == node.outputPerCycle
                            && ex.inputs.equals(node.inputs)
                            && ResourceIdentity.sameResource(ex.result.toStack(1), node.result.toStack(1))) {
                        exists = true;
                        break;
                    }
                }

                if (!exists) {
                    nodes.add(node);
                }
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
                if (t.status == TaskStatus.FAILED || t.status == TaskStatus.CANCELED) {
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
        for (RuntimeTask task : runtimeTasks.values()) {
            if (task instanceof CraftTask) {
                CraftTask craftTask = (CraftTask) task;
                String invalidReason = craftTask.validationError();
                if (invalidReason != null) {
                    LOG.warn("[Logistics] craft task skipped because invalid planning state task={} order={} reason={}",
                            craftTask.taskId, craftTask.orderId, invalidReason);
                    continue;
                }
                LOG.info("[Logistics] craft task serialized task={} order={}", craftTask.taskId, craftTask.orderId);
            }
            rt.appendTag(task.writeToNbt());
        }
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