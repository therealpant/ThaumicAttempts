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
import therealpant.thaumicattempts.golemnet.tile.TileOrderTerminal;
import therealpant.thaumicattempts.util.ItemKey;
import therealpant.thaumicattempts.util.ResourceIdentity;

import javax.annotation.Nullable;
import java.util.*;

public class LogisticsNetworkState {
    private static final Logger LOG = LogManager.getLogger("ThaumicAttempts/LogisticsHandler");

    private final LinkedHashMap<UUID, NetworkOrder> orders = new LinkedHashMap<UUID, NetworkOrder>();
    private final LinkedHashMap<UUID, RuntimeTask> runtimeTasks = new LinkedHashMap<UUID, RuntimeTask>();
    private final LinkedHashMap<String, UUID> activeTaskDedup = new LinkedHashMap<String, UUID>();
    private final LinkedHashMap<String, UUID> activeOrderDedup = new LinkedHashMap<String, UUID>();
    private final LinkedHashMap<ItemKey, List<RecipeNode>> recipesByResult = new LinkedHashMap<ItemKey, List<RecipeNode>>();
    private final ResourceReservationBook reservationBook = new ResourceReservationBook();

    private final ManagerExecutor managerExecutor = new ManagerExecutor();
    private final CrafterExecutor crafterExecutor = new CrafterExecutor();

    private static final class EffectiveNeedSnapshot {
        int requested;
        int presentAtTarget;
        int reservedInbound;
        int availableInNetwork;
        int missingNow;
        int craftNeeded;
    }
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

    @Nullable
    public String getOrderLastError(@Nullable UUID orderId) {
        if (orderId == null) return null;
        NetworkOrder order = orders.get(orderId);
        if (order == null) return null;
        return order.lastError;
    }

    public boolean hasActiveChildOrders(@Nullable UUID orderId) {
        if (orderId == null) return false;
        NetworkOrder root = orders.get(orderId);
        if (root == null || root.childOrderIds.isEmpty()) return false;

        Set<UUID> visited = new HashSet<UUID>();
        Deque<UUID> queue = new ArrayDeque<UUID>(root.childOrderIds);

        while (!queue.isEmpty()) {
            UUID id = queue.removeFirst();
            if (id == null || !visited.add(id)) continue;
            NetworkOrder child = orders.get(id);
            if (child == null) continue;
            if (isActiveOrder(child.status)) return true;
            if (!child.childOrderIds.isEmpty()) queue.addAll(child.childOrderIds);
        }

        return false;
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
        return submitOrder(
                manager,
                key,
                amount,
                sourceType,
                sourcePos,
                returnDestination,
                parentOrderId,
                depth,
                reason,
                NetworkOrder.RequestIntent.NORMAL,
                NetworkOrder.OrderKind.DELIVERY,
                CreationOutputMode.RETURN_TO_REQUESTER
        );
    }

    public UUID submitOrder(TileMirrorManager manager,
                            ItemKey key,
                            int amount,
                            OrderSourceType sourceType,
                            BlockPos sourcePos,
                            @Nullable BlockPos returnDestination,
                            @Nullable UUID parentOrderId,
                            int depth,
                            String reason,
                            @Nullable NetworkOrder.RequestIntent intent) {
        return submitOrder(
                manager,
                key,
                amount,
                sourceType,
                sourcePos,
                returnDestination,
                parentOrderId,
                depth,
                reason,
                intent,
                NetworkOrder.OrderKind.DELIVERY,
                CreationOutputMode.RETURN_TO_REQUESTER
        );
    }

    public UUID submitOrder(TileMirrorManager manager,
                            ItemKey key,
                            int amount,
                            OrderSourceType sourceType,
                            BlockPos sourcePos,
                            @Nullable BlockPos returnDestination,
                            @Nullable UUID parentOrderId,
                            int depth,
                            String reason,
                            @Nullable NetworkOrder.RequestIntent intent,
                            NetworkOrder.OrderKind orderKind,
                            CreationOutputMode creationOutputMode) {
        if (key == null || key == ItemKey.EMPTY || amount <= 0) return null;

        NetworkOrder.RequestIntent safeIntent = (intent == null ? NetworkOrder.RequestIntent.NORMAL : intent);
        NetworkOrder.OrderKind safeKind = (orderKind == null ? NetworkOrder.OrderKind.DELIVERY : orderKind);
        CreationOutputMode safeOutputMode = (creationOutputMode == null ? CreationOutputMode.RETURN_TO_REQUESTER : creationOutputMode);

        String dedupeKey = sourceType.name()
                + "|" + sourcePos.toLong()
                + "|" + key.hashCode()
                + "|" + amount
                + "|" + (returnDestination == null ? "null" : returnDestination.toLong())
                + "|" + (parentOrderId == null ? "root" : parentOrderId.toString())
                + "|" + safeIntent.name()
                + "|" + safeKind.name()
                + "|" + safeOutputMode.name();

        UUID existing = activeOrderDedup.get(dedupeKey);
        if (existing != null) {
            NetworkOrder existingOrder = orders.get(existing);
            if (existingOrder != null
                    && existingOrder.status != OrderStatus.DONE
                    && existingOrder.status != OrderStatus.FAILED
                    && existingOrder.status != OrderStatus.CANCELED) {
                return existing;
            }
            activeOrderDedup.remove(dedupeKey);
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
        order.operationalNeeded = order.requestedAmount;
        order.completedAmount = 0;
        order.status = OrderStatus.NEW;
        order.createdTick = now;
        order.updatedTick = now;
        order.debugReason = (reason == null ? "" : reason);
        order.intent = safeIntent;
        order.orderKind = safeKind;
        order.creationOutputMode = safeOutputMode;
        order.recipePath = false;

        orders.put(order.orderId, order);
        activeOrderDedup.put(dedupeKey, order.orderId);

        LOG.info("[Logistics {}] request accepted order={} source={} key={} amount={} reason={} intent={} kind={} outputMode={}",
                manager.getPos(), order.orderId, sourceType, key, amount, reason, order.intent, order.orderKind, order.creationOutputMode);

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

        EndpointRef managerBuffer = EndpointRef.of(manager.getPos(), EndpointRef.AccessMode.BUFFER);
        EndpointRef finalTarget = EndpointRef.of(
                order.returnDestination != null ? order.returnDestination : order.sourcePos,
                EndpointRef.AccessMode.INPUT
        );


        EffectiveNeedSnapshot need = computeEffectiveTargetNeed(manager, order.requestedKey, order.requestedAmount, finalTarget, order.orderId, true);
        order.operationalNeeded = need.missingNow;

        LOG.info("[Logistics {}] order={} effective-need rawRequested={} presentAtTarget={} inboundReserved={} directAvailable={} effectiveMissing={} kind={}",
                manager.getPos(),
                order.orderId,
                order.requestedAmount,
                need.presentAtTarget,
                need.reservedInbound,
                need.availableInNetwork,
                need.missingNow,
                order.orderKind);

        if (need.missingNow <= 0) {
            order.completedAmount = 0;
            order.status = OrderStatus.DONE;
            removeOrderFromDedup(order.orderId);
            LOG.info("[Logistics {}] order={} status=DONE reason=already-satisfied key={} requested={}",
                    manager.getPos(), order.orderId, order.requestedKey, order.requestedAmount);
            return;
        }

        if (order.orderKind == NetworkOrder.OrderKind.CREATION) {
            order.recipePath = true;
            planCreationOrder(manager, order, catalog, finalTarget, managerBuffer, need);
            return;
        }

        planDeliveryOrder(manager, order, catalog, finalTarget, managerBuffer, need);
    }

    private void planDeliveryOrder(TileMirrorManager manager,
                                   NetworkOrder order,
                                   LinkedHashMap<ItemKey, Integer> catalog,
                                   EndpointRef finalTarget,
                                   EndpointRef managerBuffer,
                                   EffectiveNeedSnapshot need) {
        int deliverAmount = Math.min(Math.max(0, need.missingNow), Math.max(0, need.availableInNetwork));

        if (deliverAmount <= 0) {
            order.status = OrderStatus.FAILED;
            order.lastError = "delivery-missing-stock:" + order.requestedKey;
            return;
        }

        ItemStack like = order.requestedKey.toStack(1);
        int stackLimit = Math.max(1, like.getMaxStackSize());
        int remaining = deliverAmount;

        while (remaining > 0) {
            int chunk = Math.min(stackLimit, remaining);

            TransferTask stageToManager = createTransferTask(
                    manager,
                    order.orderId,
                    stockSource(manager),
                    managerBuffer,
                    order.requestedKey,
                    chunk,
                    null,
                    "deliver"
            );
            if (stageToManager == null) {
                order.status = OrderStatus.FAILED;
                order.lastError = "delivery-task-build-failed:" + order.requestedKey;
                return;
            }
            stageToManager.status = TaskStatus.NEW;
            stageToManager.updatedTick = manager.getServerTickCounter();

            TransferTask dispatchToTarget = createTransferTask(
                    manager,
                    order.orderId,
                    managerBuffer,
                    finalTarget,
                    order.requestedKey,
                    chunk,
                    Collections.singletonList(stageToManager.taskId),
                    "deliver-output"
            );
            if (dispatchToTarget == null) {
                order.status = OrderStatus.FAILED;
                order.lastError = "delivery-task-build-failed:" + order.requestedKey;
                return;
            }
            dispatchToTarget.status = TaskStatus.NEW;
            dispatchToTarget.updatedTick = manager.getServerTickCounter();

            remaining -= chunk;
        }

        if (need.missingNow > deliverAmount) {
            order.status = OrderStatus.FAILED;
            order.lastError = "delivery-partial-stock:" + order.requestedKey;
            return;
        }

        order.status = OrderStatus.RUNNING;
    }

    private void planCreationOrder(TileMirrorManager manager,
                                   NetworkOrder order,
                                   LinkedHashMap<ItemKey, Integer> catalog,
                                   EndpointRef finalTarget,
                                   EndpointRef managerBuffer,
                                   EffectiveNeedSnapshot need) {
        int missingAfterTargetAdjust = Math.max(0, need.missingNow);
        int directAvailable = Math.min(Math.max(0, need.availableInNetwork), missingAfterTargetAdjust);
        int stillNeed = Math.max(0, missingAfterTargetAdjust - directAvailable);

        LOG.info("[Logistics {}] order={} creation-need requested={} afterTargetSubtract={} directAvailable={} craftResidual={}",
                manager.getPos(),
                order.orderId,
                order.requestedAmount,
                missingAfterTargetAdjust,
                directAvailable,
                stillNeed);

        if (directAvailable > 0) {
            int remainingDirect = directAvailable;
            ItemStack like = order.requestedKey.toStack(1);
            int stackLimit = Math.max(1, like.getMaxStackSize());
            while (remainingDirect > 0) {
                int chunk = Math.min(stackLimit, remainingDirect);
                TransferTask stageToManager = createTransferTask(
                        manager,
                        order.orderId,
                        stockSource(manager),
                        managerBuffer,
                        order.requestedKey,
                        chunk,
                        null,
                        "deliver"
                );
                if (stageToManager != null) {
                    stageToManager.status = TaskStatus.NEW;
                    stageToManager.updatedTick = manager.getServerTickCounter();
                    TransferTask deliverDirect = createTransferTask(
                            manager,
                            order.orderId,
                            managerBuffer,
                            finalTarget,
                            order.requestedKey,
                            chunk,
                            Collections.singletonList(stageToManager.taskId),
                            "deliver-output"
                    );
                    if (deliverDirect != null) {
                        deliverDirect.status = TaskStatus.NEW;
                        deliverDirect.updatedTick = manager.getServerTickCounter();
                    }
                }
                remainingDirect -= chunk;
            }
        }
        if (stillNeed <= 0) {
            order.status = OrderStatus.RUNNING;
            return;
        }

        RecipeNode recipe = pickRecipe(order.requestedKey);
        if (recipe == null || recipe.source == null) {
            order.status = OrderStatus.FAILED;
            order.lastError = "No recipe for " + order.requestedKey;
            LOG.warn("[Logistics {}] order failed={} key={} amount={} reason=no_recipe intent={}",
                    manager.getPos(), order.orderId, order.requestedKey, stillNeed, order.intent);
            return;
        }

        int outputPerCycle = Math.max(1, recipe.outputPerCycle);
        int cycles = (stillNeed + outputPerCycle - 1) / outputPerCycle;
        int producedAmount = cycles * outputPerCycle;

        /*
         * recipe.inputs хранит входы ровно на 1 цикл.
         * Здесь масштабируем их на нужное число циклов.
         */
        LinkedHashMap<ItemKey, Integer> scaledInputs = scaleRecipeInputs(recipe.inputs, cycles);

        EndpointRef crafterInput = EndpointRef.of(recipe.source, EndpointRef.AccessMode.INPUT);
        EndpointRef crafterOutput = resolveCrafterOutputEndpoint(manager, recipe.source);

        /*
         * Сначала проверяем, что ВСЕ входы доступны.
         * Пока проверка не пройдена — не создаём runtime-task.
         */
        LinkedHashMap<ItemKey, Integer> availableFromStock = new LinkedHashMap<ItemKey, Integer>();
        ItemKey firstMissingKey = null;
        int firstMissingAmount = 0;

        for (Map.Entry<ItemKey, Integer> in : scaledInputs.entrySet()) {
            ItemKey inputKey = in.getKey();
            if (inputKey == null || inputKey == ItemKey.EMPTY || inputKey.equals(order.requestedKey)) {
                LOG.warn("[Logistics {}] skipped invalid recipe input order={} result={} input={}",
                        manager.getPos(), order.orderId, order.requestedKey, inputKey);
                continue;
            }

            int inputNeed = Math.max(1, in.getValue());

            int inputTotalReachable = Math.max(0, catalog.getOrDefault(inputKey, 0));
            int inputReserved = reservationBook.getReservedAmount(inputKey);
            int inputAvailable = Math.max(0, inputTotalReachable - inputReserved);

            int fromStockNow = Math.min(inputNeed, inputAvailable);
            int shortage = Math.max(0, inputNeed - fromStockNow);

            availableFromStock.put(inputKey, fromStockNow);

            if (shortage > 0) {
                firstMissingKey = inputKey;
                firstMissingAmount = shortage;
                break;
            }
        }

        if (firstMissingKey != null) {
            order.updatedTick = manager.getServerTickCounter();

            if (order.sourceType != OrderSourceType.PLANNER) {
                order.status = OrderStatus.FAILED;
                order.lastError = "missing-input:" + firstMissingKey + ":" + firstMissingAmount;
                LOG.info("[Logistics {}] creation failed-immediate order={} key={} missingInput={} amount={} sourceType={}",
                        manager.getPos(), order.orderId, order.requestedKey, firstMissingKey, firstMissingAmount, order.sourceType);
                return;
            }

            order.status = OrderStatus.WAITING_INPUTS;
            order.lastError = "waiting-input:" + firstMissingKey + ":" + firstMissingAmount;

            LOG.info("[Logistics {}] creation waiting-inputs order={} key={} missingInput={} amount={} reason=waiting_planner_inputs",
                    manager.getPos(), order.orderId, order.requestedKey, firstMissingKey, firstMissingAmount);
            return;
        }

        /*
         * Все входы доступны — теперь строим runtime-chain.
         */
        List<UUID> inputDeps = new ArrayList<UUID>();

        for (Map.Entry<ItemKey, Integer> in : scaledInputs.entrySet()) {
            ItemKey inputKey = in.getKey();
            if (inputKey == null || inputKey == ItemKey.EMPTY || inputKey.equals(order.requestedKey)) {
                continue;
            }

            int inputNeed = Math.max(1, in.getValue());
            int fromStockNow = Math.max(0, availableFromStock.getOrDefault(inputKey, 0));

            List<UUID> feedDeps = new ArrayList<UUID>();

            if (fromStockNow > 0) {
                TransferTask stageFromStock = createTransferTask(
                        manager,
                        order.orderId,
                        stockSource(manager),
                        managerBuffer,
                        inputKey,
                        fromStockNow,
                        null,
                        "deliver"
                );
                if (stageFromStock != null) {
                    stageFromStock.status = TaskStatus.NEW;
                    stageFromStock.updatedTick = manager.getServerTickCounter();
                    feedDeps.add(stageFromStock.taskId);
                }
            }

            TransferTask unifiedFeedToCrafter = createTransferTask(
                    manager,
                    order.orderId,
                    managerBuffer,
                    crafterInput,
                    inputKey,
                    inputNeed,
                    feedDeps.isEmpty() ? null : feedDeps,
                    "craft-input"
            );
            if (unifiedFeedToCrafter != null) {
                unifiedFeedToCrafter.status = TaskStatus.NEW;
                unifiedFeedToCrafter.updatedTick = manager.getServerTickCounter();
                inputDeps.add(unifiedFeedToCrafter.taskId);
            }
        }

        CraftTask craft = createCraftTask(
                manager,
                order.orderId,
                crafterInput,
                crafterOutput,
                order.requestedKey,
                producedAmount,
                outputPerCycle,
                scaledInputs,
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

        /*
         * КРИТИЧЕСКАЯ ПРАВКА:
         * pickup-output не должен ждать полного DONE у craft-task,
         * иначе output крафтера не разгружается во время серии,
         * и большие партии начинают идти "по одному", а финальный результат
         * может застревать в output.
         *
         * Вместо этого pickup зависит только от подачи входов в крафтер.
         * После этого он может стартовать параллельно, а если output пока пуст —
         * executor просто подержит задачу в blocked/running до появления предметов.
         */
        TransferTask pickup = null;
        if (order.creationOutputMode != CreationOutputMode.LEAVE_IN_CRAFTER) {
            pickup = createTransferTask(
                    manager,
                    order.orderId,
                    crafterOutput,
                    managerBuffer,
                    order.requestedKey,
                    producedAmount,
                    inputDeps.isEmpty() ? null : new ArrayList<UUID>(inputDeps),
                    "pickup-output"
            );
            if (pickup != null) {
                pickup.status = TaskStatus.NEW;
                pickup.updatedTick = manager.getServerTickCounter();
            }

            reservationBook.claimExpectedOutput(
                    recipe.source,
                    order.requestedKey,
                    producedAmount,
                    order.orderId,
                    pickup != null ? pickup.taskId : craft.taskId
            );
        }

        if (order.creationOutputMode == CreationOutputMode.RETURN_TO_REQUESTER) {
            TransferTask deliver = createTransferTask(
                    manager,
                    order.orderId,
                    managerBuffer,
                    finalTarget,
                    order.requestedKey,
                    stillNeed,
                    pickup == null
                            ? Collections.singletonList(craft.taskId)
                            : Collections.singletonList(pickup.taskId),
                    "deliver-output"
            );
            if (deliver != null) {
                deliver.status = TaskStatus.NEW;
                deliver.updatedTick = manager.getServerTickCounter();
            }
        }

        order.status = OrderStatus.RUNNING;
        order.updatedTick = manager.getServerTickCounter();

        LOG.info("[Logistics {}] craft order planned order={} key={} amount={} produced={} perCycle={} cycles={} recipeSource={} inputs={} outputMode={}",
                manager.getPos(), order.orderId, order.requestedKey, stillNeed, producedAmount, outputPerCycle, cycles, recipe.source, scaledInputs, order.creationOutputMode);
    }

    private TransferTask createTransferTask(TileMirrorManager manager,
                                            UUID orderId,
                                            EndpointRef source,
                                            EndpointRef target,
                                            ItemKey key,
                                            int amount,
                                            List<UUID> dependsOn,
                                            String purpose) {
        if (manager == null || orderId == null || source == null || target == null || key == null || key == ItemKey.EMPTY || amount <= 0) {
            return null;
        }

        BlockPos managerPos = manager.getPos();

        EndpointRef safeSource = source;
        EndpointRef safeTarget = target;

        if ("craft-input".equals(purpose)) {
            BlockPos srcPos = endpointPos(source);
            if (srcPos != null && !srcPos.equals(managerPos) && isTerminalPos(manager, srcPos)) {
                safeSource = EndpointRef.of(managerPos, EndpointRef.AccessMode.DIRECT);
            }
        }

        RuntimeTask existing = existsActiveTask(orderId, key, safeSource, safeTarget, purpose);
        if (existing instanceof TransferTask) {
            return (TransferTask) existing;
        }

        TransferTask task = new TransferTask();
        task.taskId = UUID.randomUUID();
        task.orderId = orderId;
        task.itemKey = key;
        task.source = safeSource;
        task.target = safeTarget;
        task.amount = amount;
        task.status = TaskStatus.NEW;
        task.createdTick = manager.getServerTickCounter();
        task.updatedTick = task.createdTick;
        task.metaPurpose = purpose;

        if (dependsOn != null && !dependsOn.isEmpty()) {
            task.dependsOn.addAll(dependsOn);
        }

        runtimeTasks.put(task.taskId, task);

        NetworkOrder order = orders.get(orderId);
        if (order != null && !order.taskIds.contains(task.taskId)) {
            order.taskIds.add(task.taskId);
        }

        if (!isTerminalTask(task.status)) {
            String dedupe = "T|" + task.orderId
                    + "|" + task.source.pos.toLong()
                    + "|" + task.target.pos.toLong()
                    + "|" + task.itemKey.hashCode()
                    + "|" + task.metaPurpose;
            activeTaskDedup.put(dedupe, task.taskId);
        }

        LOG.info("[Logistics {}] runtime task created transfer={} order={} key={} amount={} src={} dst={} purpose={}",
                managerPos,
                task.taskId,
                orderId,
                key,
                amount,
                endpointPos(task.source),
                endpointPos(task.target),
                task.metaPurpose
        );

        int targetPresent = countExactInTargetInventory(manager, task.target, task.itemKey);
        int targetNeedNow = Math.max(0, amount - targetPresent);
        LOG.info("[Logistics {}] transfer task details task={} requested={} targetPresent={} targetNeedNow={} accepted=true",
                managerPos, task.taskId, amount, targetPresent, targetNeedNow);

        return task;
    }

    private BlockPos endpointPos(EndpointRef ref) {
        return ref == null ? null : ref.pos;
    }

    private boolean isTerminalPos(TileMirrorManager manager, BlockPos pos) {
        if (manager == null || pos == null || manager.getWorld() == null) {
            return false;
        }

        TileEntity te = manager.getWorld().getTileEntity(pos);
        return te instanceof TileOrderTerminal;
    }

    private CraftTask createCraftTask(TileMirrorManager manager,
                                      UUID orderId,
                                      EndpointRef crafter,
                                      EndpointRef crafterOutput,
                                      ItemKey recipeKey,
                                      int amount,
                                      int outputPerCycle,
                                      Map<ItemKey, Integer> requiredInputs,
                                      java.util.List<UUID> dependencies,
                                      String purpose) {
        if (manager == null || crafter == null || crafter.pos == null || recipeKey == null || recipeKey == ItemKey.EMPTY || amount <= 0) {
            return null;
        }

        EndpointRef safeOutput = (crafterOutput == null || crafterOutput.pos == null)
                ? EndpointRef.of(crafter.pos, EndpointRef.AccessMode.OUTPUT)
                : crafterOutput;

        RuntimeTask existing = existsActiveTask(orderId, recipeKey, crafter, safeOutput, purpose);
        if (existing instanceof CraftTask) {
            return (CraftTask) existing;
        }

        CraftTask task = new CraftTask();
        task.taskId = UUID.randomUUID();
        task.orderId = orderId;
        task.status = TaskStatus.NEW;
        task.createdTick = manager.getServerTickCounter();
        task.updatedTick = task.createdTick;
        task.crafter = crafter;
        task.recipeKey = recipeKey;
        task.outputEndpoint = safeOutput;
        task.amount = Math.max(1, amount);
        task.completedAmount = 0;
        task.outputPerCycle = Math.max(1, outputPerCycle);
        task.scheduledCycles = 0;
        task.metaPurpose = purpose == null ? "craft" : purpose;

        if (requiredInputs != null) {
            for (Map.Entry<ItemKey, Integer> e : requiredInputs.entrySet()) {
                if (e.getKey() == null || e.getKey() == ItemKey.EMPTY) continue;
                task.requiredInputs.put(e.getKey(), Math.max(1, e.getValue()));
            }
        }

        if (dependencies != null && !dependencies.isEmpty()) {
            task.dependsOn.addAll(dependencies);
        }

        String err = task.validationError();
        if (err != null) {
            LOG.warn("[Logistics {}] craft task create invalid order={} reason={} crafter={} key={} output={} amount={} perCycle={}",
                    manager.getPos(), orderId, err, task.crafter, task.recipeKey, task.outputEndpoint, amount, task.outputPerCycle);
            return null;
        }

        LOG.info("[Logistics {}] craft task created task={} order={} key={} amount={} perCycle={} crafter={} output={} inputs={}",
                manager.getPos(), task.taskId, orderId, recipeKey, amount, task.outputPerCycle, task.crafter, task.outputEndpoint, task.requiredInputs);

        runtimeTasks.put(task.taskId, task);

        NetworkOrder order = orders.get(orderId);
        if (order != null && !order.taskIds.contains(task.taskId)) {
            order.taskIds.add(task.taskId);
        }

        return task;
    }

    @Nullable
    private RuntimeTask existsActiveTask(UUID orderId,
                                         ItemKey itemKey,
                                         EndpointRef source,
                                         EndpointRef target,
                                         String purpose) {
        if (orderId == null || itemKey == null || itemKey == ItemKey.EMPTY || source == null || target == null) {
            return null;
        }

        String safePurpose = purpose == null ? "" : purpose;
        for (RuntimeTask rt : runtimeTasks.values()) {
            if (rt == null || isTerminalTask(rt.status)) continue;
            if (!orderId.equals(rt.orderId)) continue;

            if (rt instanceof TransferTask) {
                TransferTask tt = (TransferTask) rt;
                if (!itemKey.equals(tt.itemKey)) continue;
                if (tt.source == null || tt.target == null) continue;
                if (source.mode != tt.source.mode || !source.pos.equals(tt.source.pos)) continue;
                if (target.mode != tt.target.mode || !target.pos.equals(tt.target.pos)) continue;
                if (!safePurpose.equals(tt.metaPurpose == null ? "" : tt.metaPurpose)) continue;
                return rt;
            }

            if (rt instanceof CraftTask) {
                CraftTask ct = (CraftTask) rt;
                if (!itemKey.equals(ct.recipeKey)) continue;
                if (source.pos != null && ct.crafter != null && !source.pos.equals(ct.crafter.pos)) continue;
                if (!safePurpose.equals(ct.metaPurpose == null ? "" : ct.metaPurpose)) continue;
                return rt;
            }
        }
        return null;
    }

    private EndpointRef resolveCrafterOutputEndpoint(TileMirrorManager manager, BlockPos sourcePos) {
        if (sourcePos == null) {
            return EndpointRef.of(BlockPos.ORIGIN, EndpointRef.AccessMode.OUTPUT);
        }

        BlockPos observedPos = sourcePos;
        if (manager != null && manager.getWorld() != null) {
            TileEntity te = manager.getWorld().getTileEntity(sourcePos);
            if (te instanceof ICraftEndpoint) {
                try {
                    BlockPos candidate = ((ICraftEndpoint) te).getCraftTaskOutputPos(sourcePos);
                    if (candidate != null) {
                        observedPos = candidate;
                    }
                } catch (Throwable t) {
                    LOG.warn("[Logistics {}] resolve craft output failed source={} err={}",
                            manager.getPos(), sourcePos, String.valueOf(t));
                }
            }
        }

        return EndpointRef.of(observedPos, EndpointRef.AccessMode.OUTPUT);
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

                int outputPerCycle = Math.max(1, ep.getPerCraftOutputCountFor(out));

                Map<ItemKey, Integer> inputs = new LinkedHashMap<ItemKey, Integer>();
                List<ItemStack> req = ep.getRecipeInputsFor(out, 1);

                if (req != null) {
                    for (ItemStack need : req) {
                        if (need == null || need.isEmpty()) continue;

                        ItemKey nk = ItemKey.of(need);
                        if (nk == null || nk == ItemKey.EMPTY) continue;

                        int count = Math.max(1, need.getCount());
                        inputs.merge(nk, count, Integer::sum);
                    }
                }

                ProviderType pt = ProviderType.GOLEM_CRAFTER;

                if (te.getClass().getSimpleName().contains("Infusion")) {
                    pt = ProviderType.INFUSION_REQUESTER;
                } else if (te.getClass().getSimpleName().contains("ResourceRequester")) {
                    pt = ProviderType.RESOURCE_REQUESTER;
                } else if (te.getClass().getSimpleName().contains("PatternRequester")) {
                    pt = ProviderType.GOLEM_CRAFTER;
                }

                RecipeNode node = new RecipeNode(result, rp, pt, outputPerCycle, inputs);

                List<RecipeNode> nodes = recipesByResult.get(result);
                if (nodes == null) {
                    nodes = new ArrayList<RecipeNode>();
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

                LOG.info("[Logistics {}] recipe indexed result={} perCycle={} inputsPerCycle={} source={}",
                        manager.getPos(), result, outputPerCycle, inputs, rp);
            }
        }
    }

    public void tick(TileMirrorManager manager) {
        managerExecutor.bind(manager);
        crafterExecutor.bind(manager);

        for (RuntimeTask task : runtimeTasks.values()) {
            if (isTerminalTask(task.status)) continue;

            if (task.status == TaskStatus.NEW) {
                task.status = TaskStatus.WAITING_DEPENDENCY;
                task.updatedTick = manager.getServerTickCounter();
            }

            if (task.status == TaskStatus.WAITING_DEPENDENCY) {
                if (depsDone(task)) {
                    task.status = TaskStatus.READY;
                    task.updatedTick = manager.getServerTickCounter();
                } else {
                    continue;
                }
            }

            if (task.status == TaskStatus.BLOCKED) {
                if (task instanceof TransferTask) {
                    TransferTask tt = (TransferTask) task;
                    if (managerExecutor.isRunning(tt.taskId)) {
                        continue;
                    }
                }

                if (depsDone(task)) {
                    task.status = TaskStatus.READY;
                    task.updatedTick = manager.getServerTickCounter();
                } else {
                    task.status = TaskStatus.WAITING_DEPENDENCY;
                    task.updatedTick = manager.getServerTickCounter();
                    continue;
                }
            }

            if (task.status != TaskStatus.READY) continue;

            boolean accepted = false;
            if (task instanceof TransferTask) {
                TransferTask tt = (TransferTask) task;

                if (managerExecutor.isRunning(tt.taskId)) {
                    task.status = TaskStatus.DISPATCHED;
                    task.updatedTick = manager.getServerTickCounter();
                    continue;
                }

                accepted = managerExecutor.canAccept(tt) && managerExecutor.submit(tt);
            } else if (task instanceof CraftTask) {
                accepted = crafterExecutor.canAccept((CraftTask) task) && crafterExecutor.submit((CraftTask) task);
            }

            if (accepted) {
                task.status = TaskStatus.DISPATCHED;
                task.updatedTick = manager.getServerTickCounter();
                LOG.info("[Logistics {}] task dispatched id={} type={}",
                        manager.getPos(),
                        task.taskId,
                        (task instanceof TransferTask ? "TRANSFER" : "CRAFT"));
            }
        }

        managerExecutor.tick();
        crafterExecutor.tick();

        collectSnapshot(manager, managerExecutor);
        collectSnapshot(manager, crafterExecutor);
        retryWaitingCreationOrders(manager);
        updateOrders(manager);
        pruneFinalizedTasks(manager);
    }

    private void retryWaitingCreationOrders(TileMirrorManager manager) {
        LinkedHashMap<ItemKey, Integer> catalog = manager.getReachableCatalog();
        EndpointRef managerBuffer = EndpointRef.of(manager.getPos(), EndpointRef.AccessMode.BUFFER);

        for (NetworkOrder order : orders.values()) {
            if (order == null) continue;
            if (order.status != OrderStatus.WAITING_INPUTS) continue;
            if (order.orderKind != NetworkOrder.OrderKind.CREATION) continue;

            boolean hasLiveTasks = false;
            if (order.taskIds != null && !order.taskIds.isEmpty()) {
                for (UUID tid : order.taskIds) {
                    RuntimeTask task = runtimeTasks.get(tid);
                    if (task != null && !isTerminalTask(task.status)) {
                        hasLiveTasks = true;
                        break;
                    }
                }
            }

            if (hasLiveTasks) {
                continue;
            }

            if (order.taskIds != null && !order.taskIds.isEmpty()) {
                order.taskIds.clear();
            }

            EndpointRef finalTarget = EndpointRef.of(
                    order.returnDestination != null ? order.returnDestination : order.sourcePos,
                    EndpointRef.AccessMode.INPUT
            );

            EffectiveNeedSnapshot need = computeEffectiveTargetNeed(manager, order.requestedKey, order.requestedAmount, finalTarget, order.orderId, true);
            order.operationalNeeded = need.missingNow;
            planCreationOrder(manager, order, catalog, finalTarget, managerBuffer, need);
        }
    }

    private <T extends RuntimeTask> void collectSnapshot(TileMirrorManager manager, ILogisticsExecutor<T> executor) {
        for (RuntimeTask task : runtimeTasks.values()) {
            if (!executor.accepts(task)) continue;
            TaskExecutionSnapshot snapshot = executor.getSnapshot(task.taskId);
            if (snapshot == null) continue;
            if (isTerminalTask(task.status) && !isTerminalTask(snapshot.status)) {
                LOG.info("[Logistics {}] snapshot ignored task={} current={} snapshot={} reason=final-state",
                        manager.getPos(), task.taskId, task.status, snapshot.status);
                continue;
            }
            task.status = snapshot.status;
            task.completedAmount = snapshot.completedAmount;
            task.updatedTick = manager.getServerTickCounter();
        }
    }

    private boolean depsDone(RuntimeTask task) {
        for (UUID dep : task.dependsOn) {
            RuntimeTask depTask = runtimeTasks.get(dep);
            if (depTask == null) {
                continue;
            }
            if (depTask.status != TaskStatus.DONE) {
                return false;
            }
        }
        return true;
    }

    private void updateOrders(TileMirrorManager manager) {
        for (NetworkOrder order : orders.values()) {
            if (!isActiveOrder(order.status)) {
                removeOrderFromDedup(order.orderId);
                continue;
            }

            boolean allDone = true;
            boolean hasTasks = false;
            boolean craftDone = !order.recipePath;
            boolean outputDone = !order.recipePath || order.creationOutputMode == CreationOutputMode.LEAVE_IN_CRAFTER;
            long actualCompleted = 0L;
            int runningTasks = 0;
            EndpointRef finalTarget = EndpointRef.of(
                    order.returnDestination != null ? order.returnDestination : order.sourcePos,
                    EndpointRef.AccessMode.INPUT
            );
            EffectiveNeedSnapshot needNow = computeEffectiveTargetNeed(manager, order.requestedKey, order.requestedAmount, finalTarget, order.orderId, true);

            for (UUID tid : order.taskIds) {
                RuntimeTask t = runtimeTasks.get(tid);
                if (t == null) continue;
                hasTasks = true;
                if (!isTerminalTask(t.status)) runningTasks++;

                if (contributesToOrderProgress(order, t)) {
                    actualCompleted += Math.max(0L, t.completedAmount);
                }

                if (t.status == TaskStatus.FAILED
                        || t.status == TaskStatus.CANCELED
                        || t.status == TaskStatus.STALLED_OUTPUT
                        || t.status == TaskStatus.BLOCKED) {
                    order.status = OrderStatus.FAILED;
                    Iterator<Map.Entry<String, UUID>> it = activeOrderDedup.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry<String, UUID> en = it.next();
                        if (order.orderId.equals(en.getValue())) {
                            it.remove();
                        }
                    }
                    order.lastError = "task-failed:" + tid + ":" + t.status;
                    LOG.warn("[Logistics {}] order failed={} task={} status={}",
                            manager.getPos(), order.orderId, tid, t.status);
                    LOG.warn("[Logistics {}] order={} reason=task-terminal amount={} completed={} runningTasks={}",
                            manager.getPos(), order.orderId, order.requestedAmount, Math.max(0L, actualCompleted), runningTasks);
                    allDone = false;
                    break;
                }

                if (t.status != TaskStatus.DONE) allDone = false;
                if ("execute-craft".equals(t.metaPurpose) && t.status == TaskStatus.DONE) craftDone = true;
                if ("deliver-output".equals(t.metaPurpose) && t.status == TaskStatus.DONE) outputDone = true;
            }

            if (!hasTasks && order.status == OrderStatus.WAITING_INPUTS) {
                order.updatedTick = manager.getServerTickCounter();
                continue;
            }

            if (needNow.missingNow <= 0) {
                order.completedAmount = order.requestedAmount;
                order.status = OrderStatus.DONE;
                removeOrderFromDedup(order.orderId);
                LOG.info("[Logistics {}] order={} status=DONE reason=effective-need-zero rawRequested={} presentAtTarget={} inboundReserved={} directAvailable={}",
                        manager.getPos(),
                        order.orderId,
                        order.requestedAmount,
                        needNow.presentAtTarget,
                        needNow.reservedInbound,
                        needNow.availableInNetwork);
                order.updatedTick = manager.getServerTickCounter();
                continue;
            }

            int effectiveGoal = Math.max(0, order.operationalNeeded);
            order.completedAmount = (int) Math.min(effectiveGoal, Math.max(0L, actualCompleted));
            int remaining = Math.max(0, effectiveGoal - order.completedAmount);

            if (!hasTasks) {
                order.status = OrderStatus.FAILED;
                Iterator<Map.Entry<String, UUID>> it = activeOrderDedup.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, UUID> en = it.next();
                    if (order.orderId.equals(en.getValue())) {
                        it.remove();
                    }
                }
                removeOrderFromDedup(order.orderId);
                order.lastError = "no-runtime-tasks";
                LOG.warn("[Logistics {}] order failed={} reason=no-runtime-tasks", manager.getPos(), order.orderId);
                LOG.warn("[Logistics {}] order={} status=FAILED amount={} completed={} remaining={} runningTasks={}",
                        manager.getPos(), order.orderId, order.requestedAmount, order.completedAmount, remaining, runningTasks);
            } else if (order.recipePath && (!craftDone || !outputDone)) {
                allDone = false;
            } else if (allDone && order.completedAmount >= effectiveGoal) {
                order.status = OrderStatus.DONE;
                Iterator<Map.Entry<String, UUID>> it = activeOrderDedup.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, UUID> en = it.next();
                    if (order.orderId.equals(en.getValue())) {
                        it.remove();
                    }
                }
                removeOrderFromDedup(order.orderId);
                LOG.info("[Logistics {}] order={} status=DONE reason=completed-amount-reached key={} amount={} completed={} remaining=0 recipePath={}",
                        manager.getPos(), order.orderId, order.requestedKey, effectiveGoal, order.completedAmount, order.recipePath);
            } else if (allDone) {
                order.status = OrderStatus.FAILED;
                order.lastError = "incomplete-delivery:" + order.requestedKey + ":" + order.completedAmount + "/" + effectiveGoal;
                removeOrderFromDedup(order.orderId);
                LOG.warn("[Logistics {}] order={} status=FAILED reason=incomplete-after-all-tasks key={} amount={} completed={} remaining={} recipePath={}",
                        manager.getPos(), order.orderId, order.requestedKey, effectiveGoal, order.completedAmount, remaining, order.recipePath);
            } else if (order.status == OrderStatus.FAILED || order.status == OrderStatus.CANCELED) {
                Iterator<Map.Entry<String, UUID>> it = activeOrderDedup.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, UUID> en = it.next();
                    if (order.orderId.equals(en.getValue())) {
                        it.remove();
                    }
                }
                removeOrderFromDedup(order.orderId);
            } else if (order.status == OrderStatus.PLANNING || order.status == OrderStatus.NEW) {
                order.status = OrderStatus.RUNNING;
            }

            if (order.status == OrderStatus.RUNNING || order.status == OrderStatus.WAITING_INPUTS) {
                LOG.info("[Logistics {}] order={} status={} rawRequested={} effectiveGoal={} completed={} remaining={} runningTasks={} recipePath={}",
                        manager.getPos(), order.orderId, order.status, order.requestedAmount, effectiveGoal, order.completedAmount, remaining, runningTasks, order.recipePath);
            }

            order.updatedTick = manager.getServerTickCounter();
        }
    }

    private boolean contributesToOrderProgress(NetworkOrder order, RuntimeTask task) {
        if (order == null || task == null) return false;
        if (order.orderKind == NetworkOrder.OrderKind.CREATION
                && order.creationOutputMode == CreationOutputMode.LEAVE_IN_CRAFTER) {
            return "execute-craft".equals(task.metaPurpose);
        }
        return "deliver-output".equals(task.metaPurpose);
    }

    public int countExactInEndpoint(TileMirrorManager manager, EndpointRef endpoint, ItemKey key) {
        if (manager == null || endpoint == null || key == null || key == ItemKey.EMPTY) return 0;
        return Math.max(0, manager.countItemAtEndpoint(endpoint, key));
    }

    public int countExactInTargetInventory(TileMirrorManager manager, EndpointRef endpoint, ItemKey key) {
        return countExactInEndpoint(manager, endpoint, key);
    }

    public int countInboundReservedForTarget(ItemKey key,
                                             EndpointRef target,
                                             @Nullable UUID excludeOrder,
                                             @Nullable UUID excludeTask) {
        if (key == null || key == ItemKey.EMPTY || target == null) return 0;
        int total = 0;
        for (RuntimeTask task : runtimeTasks.values()) {
            if (!(task instanceof TransferTask) || isTerminalTask(task.status)) continue;
            if (excludeTask != null && excludeTask.equals(task.taskId)) continue;
            if (excludeOrder != null && excludeOrder.equals(task.orderId)) continue;

            TransferTask transfer = (TransferTask) task;
            if (transfer.itemKey == null || !key.equals(transfer.itemKey)) continue;
            if (transfer.target == null) continue;
            if (transfer.target.mode != target.mode || !transfer.target.pos.equals(target.pos)) continue;

            long outstanding = Math.max(0L, transfer.amount - transfer.completedAmount);
            if (outstanding > 0L) {
                total += (int) Math.min(Integer.MAX_VALUE, outstanding);
            }
        }
        return Math.max(0, total);
    }

    public int countDirectAvailableInNetwork(TileMirrorManager manager, ItemKey key, @Nullable UUID excludeOrder) {
        if (manager == null || key == null || key == ItemKey.EMPTY) return 0;
        int reachable = Math.max(0, manager.getReachableCatalog().getOrDefault(key, 0));
        int reserved = reservationBook.getReservedAmount(key);
        return Math.max(0, reachable - reserved);
    }

    public int computeEffectiveMissing(TileMirrorManager manager,
                                       ItemKey key,
                                       int requestedAmount,
                                       EndpointRef target,
                                       @Nullable UUID excludeOrder,
                                       boolean includeInboundReservations) {
        EffectiveNeedSnapshot snapshot = computeEffectiveTargetNeed(manager, key, requestedAmount, target, excludeOrder, includeInboundReservations);
        return snapshot.missingNow;
    }

    private EffectiveNeedSnapshot computeEffectiveTargetNeed(TileMirrorManager manager,
                                                             ItemKey key,
                                                             int requestedAmount,
                                                             EndpointRef target,
                                                             @Nullable UUID excludeOrder,
                                                             boolean includeInboundReservations) {
        EffectiveNeedSnapshot snapshot = new EffectiveNeedSnapshot();
        snapshot.requested = Math.max(0, requestedAmount);
        snapshot.presentAtTarget = countExactInTargetInventory(manager, target, key);
        snapshot.reservedInbound = includeInboundReservations
                ? countInboundReservedForTarget(key, target, excludeOrder, null)
                : 0;
        snapshot.availableInNetwork = countDirectAvailableInNetwork(manager, key, excludeOrder);
        snapshot.missingNow = Math.max(0, snapshot.requested - snapshot.presentAtTarget - snapshot.reservedInbound);
        snapshot.craftNeeded = Math.max(0, snapshot.missingNow - snapshot.availableInNetwork);
        return snapshot;
    }

    public boolean isTargetSatisfied(TileMirrorManager manager, TransferTask task) {
        if (manager == null || task == null || task.target == null || task.itemKey == null || task.itemKey == ItemKey.EMPTY) {
            return false;
        }
        int atTarget = countExactInTargetInventory(manager, task.target, task.itemKey);
        int inbound = countInboundReservedForTarget(task.itemKey, task.target, task.orderId, task.taskId);
        long requested = Math.max(0L, task.amount);
        boolean satisfied = atTarget >= requested || (atTarget + inbound) >= requested;
        if (satisfied) {
            task.completedAmount = Math.min(task.amount, Math.max(task.completedAmount, atTarget));
        }
        return satisfied;
    }

    public void releaseBufferedOverageForTask(TileMirrorManager manager, TransferTask task) {
        if (manager == null || task == null || task.itemKey == null) return;
        int buffered = manager.countItemAtEndpoint(EndpointRef.of(manager.getPos(), EndpointRef.AccessMode.BUFFER), task.itemKey);
        LOG.info("[Logistics {}] manager-buffer-overage-release task={} key={} buffered={} action=release-to-buffer",
                manager.getPos(), task.taskId, task.itemKey, Math.max(0, buffered));
    }

    private void removeOrderFromDedup(UUID orderId) {
        Iterator<Map.Entry<String, UUID>> it = activeOrderDedup.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, UUID> e = it.next();
            if (orderId.equals(e.getValue())) {
                it.remove();
            }
        }
    }

    public List<RecipeNode> getAllRecipeNodesForPlanning() {
        List<RecipeNode> out = new ArrayList<RecipeNode>();
        for (List<RecipeNode> list : recipesByResult.values()) {
            if (list == null) continue;
            out.addAll(list);
        }
        return out;
    }

    private void pruneFinalizedTasks(TileMirrorManager manager) {
        Iterator<Map.Entry<UUID, RuntimeTask>> it = runtimeTasks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, RuntimeTask> e = it.next();
            RuntimeTask task = e.getValue();
            if (!isTerminalTask(task.status)) continue;

            if (hasLiveDependents(task.taskId)) {
                continue;
            }

            NetworkOrder order = orders.get(task.orderId);
            if (order == null || !isActiveOrder(order.status)) {
                it.remove();
                removeTaskFromDedup(task.taskId);
                LOG.info("[Logistics {}] transfer task finalized and removed task={} order={} status={}",
                        manager.getPos(), task.taskId, task.orderId, task.status);
            }
        }
    }

    private void removeTaskFromDedup(UUID taskId) {
        Iterator<Map.Entry<String, UUID>> it = activeTaskDedup.entrySet().iterator();
        while (it.hasNext()) {
            if (taskId.equals(it.next().getValue())) {
                it.remove();
            }
        }
    }

    private static boolean isTerminalTask(TaskStatus status) {
        return status == TaskStatus.DONE
                || status == TaskStatus.FAILED
                || status == TaskStatus.CANCELED
                || status == TaskStatus.STALLED_OUTPUT;
    }

    private static boolean isActiveOrder(OrderStatus status) {
        return status != OrderStatus.DONE && status != OrderStatus.FAILED && status != OrderStatus.CANCELED;
    }

    @Nullable
    public RecipeNode getRecipeForPlanner(ItemKey key) {
        RecipeNode node = pickRecipe(key);
        if (node == null) return null;
        return new RecipeNode(node.result, node.source, node.providerType, node.outputPerCycle, node.inputs);
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
            if (isActiveOrder(o.status)) {
                String dedupeKey = o.sourceType.name()
                        + "|" + o.sourcePos.toLong()
                        + "|" + o.requestedKey.hashCode()
                        + "|" + o.requestedAmount
                        + "|" + (o.returnDestination == null ? "null" : o.returnDestination.toLong())
                        + "|" + (o.parentOrderId == null ? "root" : o.parentOrderId.toString())
                        + "|" + (o.intent == null ? NetworkOrder.RequestIntent.NORMAL.name() : o.intent.name())
                        + "|" + (o.orderKind == null ? NetworkOrder.OrderKind.DELIVERY.name() : o.orderKind.name())
                        + "|" + (o.creationOutputMode == null ? CreationOutputMode.RETURN_TO_REQUESTER.name() : o.creationOutputMode.name());
                activeOrderDedup.put(dedupeKey, o.orderId);
            }
        }

        NBTTagList rt = tag.getTagList("runtimeTasks", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < rt.tagCount(); i++) {
            RuntimeTask task = RuntimeTask.readFromNbt(rt.getCompoundTagAt(i));
            runtimeTasks.put(task.taskId, task);
            if (task instanceof TransferTask && !isTerminalTask(task.status)) {
                TransferTask transfer = (TransferTask) task;
                String dedupe = "T|" + task.orderId
                        + "|" + transfer.source.pos.toLong()
                        + "|" + transfer.target.pos.toLong()
                        + "|" + transfer.itemKey.hashCode()
                        + "|" + transfer.metaPurpose;
                activeTaskDedup.put(dedupe, task.taskId);
            }
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

    private EndpointRef stockSource(TileMirrorManager manager) {
        return EndpointRef.of(manager.getPos(), EndpointRef.AccessMode.DIRECT);
    }

    private boolean isEnoughInStock(LinkedHashMap<ItemKey, Integer> catalog, ItemKey key, int needed) {
        if (catalog == null || key == null || key == ItemKey.EMPTY || needed <= 0) return false;
        int reachable = Math.max(0, catalog.getOrDefault(key, 0));
        int reserved = reservationBook.getReservedAmount(key);
        int available = Math.max(0, reachable - reserved);
        return available >= needed;
    }

    private boolean isEnoughInStockIgnoringReservations(LinkedHashMap<ItemKey, Integer> catalog, ItemKey key, int needed) {
        if (catalog == null || key == null || key == ItemKey.EMPTY || needed <= 0) return false;
        int reachable = Math.max(0, catalog.getOrDefault(key, 0));
        return reachable >= needed;
    }

    private boolean hasLiveDependents(UUID taskId) {
        for (RuntimeTask other : runtimeTasks.values()) {
            if (other == null) continue;
            if (isTerminalTask(other.status)) continue;
            if (other.dependsOn.contains(taskId)) {
                return true;
            }
        }
        return false;
    }

    private LinkedHashMap<ItemKey, Integer> scaleRecipeInputs(Map<ItemKey, Integer> inputs, int cycles) {
        LinkedHashMap<ItemKey, Integer> scaled = new LinkedHashMap<ItemKey, Integer>();
        if (inputs == null || inputs.isEmpty() || cycles <= 0) {
            return scaled;
        }

        for (Map.Entry<ItemKey, Integer> e : inputs.entrySet()) {
            ItemKey key = e.getKey();
            if (key == null || key == ItemKey.EMPTY) continue;

            int perCycleValue = (e.getValue() == null ? 0 : e.getValue());
            if (perCycleValue <= 0) continue;

            scaled.put(key, perCycleValue * cycles);
        }

        return scaled;
    }

    @Nullable
    public RecipeNode getRecipeNodeForPlanning(ItemKey key) {
        if (key == null || key == ItemKey.EMPTY) return null;
        List<RecipeNode> nodes = recipesByResult.get(key);
        if (nodes == null || nodes.isEmpty()) return null;
        return nodes.get(0);
    }
}