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

        if (order.orderKind == NetworkOrder.OrderKind.CREATION) {
            order.recipePath = true;
            planCreationOrder(manager, order, catalog, finalTarget, managerBuffer);
            return;
        }

        planDeliveryOrder(manager, order, catalog, finalTarget, managerBuffer);
    }

    private void planDeliveryOrder(TileMirrorManager manager,
                                   NetworkOrder order,
                                   LinkedHashMap<ItemKey, Integer> catalog,
                                   EndpointRef finalTarget,
                                   EndpointRef managerBuffer) {
        int totalReachable = Math.max(0, catalog.getOrDefault(order.requestedKey, 0));
        int reserved = reservationBook.getReservedAmount(order.requestedKey);
        int totalAvailable = Math.max(0, totalReachable - reserved);
        int deliverAmount = Math.min(order.requestedAmount, totalAvailable);

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

        if (order.requestedAmount > deliverAmount) {
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
                                   EndpointRef managerBuffer) {
        int stillNeed = Math.max(0, order.requestedAmount);
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
         * recipe.inputs теперь обязаны хранить входы РОВНО НА 1 ЦИКЛ.
         * Здесь это масштабируется единственный раз.
         */
        LinkedHashMap<ItemKey, Integer> scaledInputs = scaleRecipeInputs(recipe.inputs, cycles);

        EndpointRef crafterInput = EndpointRef.of(recipe.source, EndpointRef.AccessMode.INPUT);
        EndpointRef crafterOutput = EndpointRef.of(recipe.source, EndpointRef.AccessMode.OUTPUT);

        /*
         * Сначала полностью проверяем, что ВСЕ входы доступны.
         * Никаких runtime-task'ов до завершения этой проверки не создаём.
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

            /*
             * Для обычных CRAFT_ONLY запросов заказ,
             * который нельзя выполнить сразу, не должен висеть бесконечно.
             */
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
         * До этого места доходим только если ВСЕ входы доступны.
         * Теперь уже безопасно строить runtime-task chain.
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
                EndpointRef.of(recipe.source, EndpointRef.AccessMode.INPUT),
                order.requestedKey,
                producedAmount,
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

        TransferTask pickup = null;
        if (order.creationOutputMode != CreationOutputMode.LEAVE_IN_CRAFTER) {
            pickup = createTransferTask(
                    manager,
                    order.orderId,
                    crafterOutput,
                    managerBuffer,
                    order.requestedKey,
                    producedAmount,
                    Collections.singletonList(craft.taskId),
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
                    pickup == null ? Collections.singletonList(craft.taskId) : Collections.singletonList(pickup.taskId),
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
                                      ItemKey recipeKey,
                                      int amount,
                                      Map<ItemKey, Integer> requiredInputs,
                                      java.util.List<UUID> dependencies,
                                      String purpose) {
        if (manager == null || crafter == null || crafter.pos == null || recipeKey == null || recipeKey == ItemKey.EMPTY || amount <= 0) {
            return null;
        }

        EndpointRef crafterOutput = EndpointRef.of(crafter.pos, EndpointRef.AccessMode.OUTPUT);

        CraftTask task = new CraftTask();
        task.taskId = UUID.randomUUID();
        task.orderId = orderId;
        task.status = TaskStatus.NEW;
        task.createdTick = manager.getServerTickCounter();
        task.updatedTick = task.createdTick;
        task.crafter = crafter;
        task.recipeKey = recipeKey;
        task.outputEndpoint = crafterOutput;
        task.amount = Math.max(1, amount);
        task.completedAmount = 0;
        task.purpose = purpose == null ? "craft" : purpose;

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
            LOG.warn("[Logistics {}] craft task create invalid order={} reason={} crafter={} key={} output={} amount={}",
                    manager.getPos(), orderId, err, task.crafter, task.recipeKey, task.outputEndpoint, amount);
            return null;
        }

        LOG.info("[Logistics {}] craft task created task={} order={} key={} amount={} crafter={} output={} inputs={}",
                manager.getPos(), task.taskId, orderId, recipeKey, amount, task.crafter, task.outputEndpoint, task.requiredInputs);

        runtimeTasks.put(task.taskId, task);
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

                int outputPerCycle = Math.max(1, ep.getPerCraftOutputCountFor(out));

                /*
                 * ВАЖНО:
                 * getRecipeInputsFor(resultLike, times) принимает количество ЦИКЛОВ крафта.
                 * Поэтому индекс рецептов должен хранить входы ровно на 1 цикл,
                 * а не на outputPerCycle.
                 *
                 * Иначе для рецептов типа:
                 *   2 доски -> 4 палки
                 * мы сначала получим входы уже на 4 цикла,
                 * а потом ещё раз умножим их в planCreationOrder(...),
                 * что и раздувает большие партии.
                 */
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

                /*
                 * КРИТИЧЕСКИЙ ФИКС:
                 * если executor уже ведёт эту задачу, не надо повторно её dispatch'ить.
                 * Иначе один и тот же taskId многократно submit'ится каждый тик.
                 */
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

            /*
             * Если у waiting-order остались только завершённые/мертвые task-ссылки,
             * очищаем их и пробуем спланировать заново.
             */
            if (order.taskIds != null && !order.taskIds.isEmpty()) {
                order.taskIds.clear();
            }

            EndpointRef finalTarget = EndpointRef.of(
                    order.returnDestination != null ? order.returnDestination : order.sourcePos,
                    EndpointRef.AccessMode.INPUT
            );

            planCreationOrder(manager, order, catalog, finalTarget, managerBuffer);
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
        LinkedHashMap<ItemKey, Integer> catalog = manager.getReachableCatalog();
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

            for (UUID tid : order.taskIds) {
                RuntimeTask t = runtimeTasks.get(tid);
                if (t == null) continue;
                hasTasks = true;
                if (!isTerminalTask(t.status)) runningTasks++;

                if (contributesToOrderProgress(order, t)) {
                    actualCompleted += Math.max(0L, t.completedAmount);
                }

                if (t.status == TaskStatus.FAILED || t.status == TaskStatus.CANCELED) {
                    order.status = OrderStatus.FAILED;
                    Iterator<Map.Entry<String, UUID>> it = activeOrderDedup.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry<String, UUID> en = it.next();
                        if (order.orderId.equals(en.getValue())) {
                            it.remove();
                        }
                    }
                    order.lastError = "task-failed:" + tid;
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

            order.completedAmount = (int) Math.min(order.requestedAmount, Math.max(0L, actualCompleted));
            int remaining = Math.max(0, order.requestedAmount - order.completedAmount);

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
            } else if (allDone && order.completedAmount >= order.requestedAmount) {
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
                        manager.getPos(), order.orderId, order.requestedKey, order.requestedAmount, order.completedAmount, order.recipePath);
            } else if (allDone) {
                /*
                 * Все runtime-task завершились, но требуемое количество не достигнуто.
                 * Если оставить RUNNING, заказ зависает навсегда без живых задач.
                 */
                order.status = OrderStatus.FAILED;
                order.lastError = "incomplete-delivery:" + order.requestedKey + ":" + order.completedAmount + "/" + order.requestedAmount;
                removeOrderFromDedup(order.orderId);
                LOG.warn("[Logistics {}] order={} status=FAILED reason=incomplete-after-all-tasks key={} amount={} completed={} remaining={} recipePath={}",
                        manager.getPos(), order.orderId, order.requestedKey, order.requestedAmount, order.completedAmount, remaining, order.recipePath);
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
                LOG.info("[Logistics {}] order={} status={} amount={} completed={} remaining={} runningTasks={} recipePath={}",
                        manager.getPos(), order.orderId, order.status, order.requestedAmount, order.completedAmount, remaining, runningTasks, order.recipePath);
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
        return status == TaskStatus.DONE || status == TaskStatus.FAILED || status == TaskStatus.CANCELED;
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