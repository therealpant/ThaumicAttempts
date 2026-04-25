package therealpant.thaumicattempts.golemnet.cloud;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.api.ICloudCraftConsumer;
import therealpant.thaumicattempts.golemnet.tile.TileMirrorManager;
import therealpant.thaumicattempts.util.ResourceIdentity;
import therealpant.thaumicattempts.util.ItemKey;

import javax.annotation.Nullable;
import java.util.*;

public class MirrorLogisticsCloud {
    private static final String TAG_ORDERS = "orders";
    private static final String TAG_TASKS = "tasks";
    private static final String TAG_NETWORK = "network";

    private static final String META_CONSUMER_POS = "craft.consumerPos";
    private static final String META_CONSUMER_IN_SIDE = "craft.inputSide";
    private static final String META_CONSUMER_OUT_SIDE = "craft.outputSide";
    private static final String META_CRAFT_CYCLES = "craft.cycles";
    private static final String META_WAIT_SINCE = "craft.waitSince";

    private final Map<UUID, CloudOrder> orders = new LinkedHashMap<>();
    private final Map<UUID, CloudTask> tasks = new LinkedHashMap<>();
    private CloudNetworkSnapshot snapshot = new CloudNetworkSnapshot();
    private static final int TASK_TIMEOUT_TICKS = 100;
    private static final int MAX_RETRIES = 3;
    private static final int REQUEST_CHUNK = 64;
    private static final long WAIT_RESOURCES_FAIL_TICKS = 200L;
    private final Map<String, ProviderChannel> providerChannels = new LinkedHashMap<>();

    public void tick(TileMirrorManager manager) {
        if (manager == null || manager.getWorld() == null || manager.getWorld().isRemote) return;

        final long tick = manager.getWorld().getTotalWorldTime();
        planOrders(manager, tick);
        refreshProviderChannels(manager, tick);
        scheduleProviderTasks(tick);
        executeTasks(manager, tick);
        syncOrderStates(tick);

        if ((tick % 100L) == 0L) {
            rebuildNetworkSnapshot(manager);
        }
    }

    public UUID submitOrder(CloudOrder order) {
        if (order == null) return null;
        orders.put(order.getOrderId(), order);
        return order.getOrderId();
    }

    private void planOrders(TileMirrorManager manager, long tick) {
        LinkedHashMap<ItemKey, Integer> reachableCatalog = manager.getReachableCatalog();

        for (CloudOrder order : orders.values()) {
            if (order == null || order.getStatus() == CloudOrderStatus.CANCELLED || order.getStatus() == CloudOrderStatus.DONE)
                continue;
            if (order.getStatus() == CloudOrderStatus.NEW) order.setStatus(CloudOrderStatus.PLANNING, tick);
            if (order.getStatus() != CloudOrderStatus.PLANNING && order.getStatus() != CloudOrderStatus.WAITING_RESOURCES)
                continue;

            if (order.getKind() == CloudOrderKind.DELIVERY) {
                planDeliveryOrder(manager, order, reachableCatalog, tick);
            } else if (order.getKind() == CloudOrderKind.CRAFT) {
                planCraftOrder(manager, order, reachableCatalog, tick);
            }
        }
    }

    private void planDeliveryOrder(TileMirrorManager manager, CloudOrder order, LinkedHashMap<ItemKey, Integer> reachableCatalog, long tick) {
        ItemKey key = order.getItemKey();
        int requested = Math.max(0, order.getRequestedAmount());
        if (requested <= 0 || key == null || key == ItemKey.EMPTY) {
            order.setStatus(CloudOrderStatus.FAILED, tick);
            order.setFailReason("Invalid delivery order payload", tick);
            return;
        }

        int availableInCatalog = Math.max(0, reachableCatalog.getOrDefault(key, 0));
        int availableInBuffer = Math.max(0, manager.countBuffered(key));
        if (availableInCatalog <= 0 && availableInBuffer <= 0) {
            order.setStatus(CloudOrderStatus.WAITING_RESOURCES, tick);
            order.setFailReason("Resource not reachable in provider catalog: " + key, tick);
            return;
        }

        if (getTaskByOrderAndKind(order.getOrderId(), CloudTaskKind.SUPPLY) == null) {
            CloudTask supply = new CloudTask(UUID.randomUUID(), order.getOrderId(), CloudTaskKind.SUPPLY, key, requested, tick);
            supply.setEndpoints(null, order.getDestination());
            supply.setAssignments(manager.getPos(), order.getDestination() == null ? null : order.getDestination().getPos());
            supply.setStatus(CloudTaskStatus.READY, tick);
            tasks.put(supply.getTaskId(), supply);
        }
        if (getTaskByOrderAndKind(order.getOrderId(), CloudTaskKind.TRANSFER) == null) {
            CloudTask transfer = new CloudTask(UUID.randomUUID(), order.getOrderId(), CloudTaskKind.TRANSFER, key, requested, tick);
            transfer.setEndpoints(null, order.getDestination());
            transfer.setAssignments(manager.getPos(), order.getDestination() == null ? null : order.getDestination().getPos());
            transfer.setStatus(CloudTaskStatus.READY, tick);
            tasks.put(transfer.getTaskId(), transfer);
        }

        order.setPlannedOutputAmount(requested, tick);
        order.setFailReason("", tick);
        order.setStatus(CloudOrderStatus.RUNNING, tick);
    }

    private void planCraftOrder(TileMirrorManager manager, CloudOrder order, LinkedHashMap<ItemKey, Integer> reachableCatalog, long tick) {
        ItemKey requestedKey = order.getItemKey();
        int requestedAmount = Math.max(0, order.getRequestedAmount());
        if (requestedKey == null || requestedKey == ItemKey.EMPTY || requestedAmount <= 0 || order.getDestination() == null) {
            order.setStatus(CloudOrderStatus.FAILED, tick);
            order.setFailReason("Invalid craft order payload", tick);
            return;
        }

        ICloudCraftConsumer consumer = findConsumerFor(manager, requestedKey);
        if (consumer == null) {
            order.setStatus(CloudOrderStatus.FAILED, tick);
            order.setFailReason("No cloud craft consumer for " + requestedKey, tick);
            return;
        }

        CloudEndpointRef inputEndpoint = consumer.getInputEndpoint();
        CloudEndpointRef outputEndpoint = consumer.getOutputEndpoint();
        if (inputEndpoint == null || outputEndpoint == null) {
            order.setStatus(CloudOrderStatus.FAILED, tick);
            order.setFailReason("Consumer endpoints unavailable", tick);
            return;
        }

        ItemStack resultLike = requestedKey.toStack(1);
        int outPerCycle = Math.max(1, consumer.getPerCraftOutputCountFor(resultLike));
        int cycles = Math.max(1, (requestedAmount + outPerCycle - 1) / outPerCycle);
        int expectedOutput = Math.max(1, cycles * outPerCycle);

        Map<ItemKey, Integer> totalInputs = new LinkedHashMap<>();
        Map<ItemKey, Integer> recipePerCycle = consumer.getInputsPerCycle(resultLike);
        for (Map.Entry<ItemKey, Integer> e : recipePerCycle.entrySet()) {
            if (e == null || e.getKey() == null || e.getKey() == ItemKey.EMPTY) continue;
            int req = Math.max(1, e.getValue()) * cycles;
            totalInputs.merge(e.getKey(), req, Integer::sum);
        }

        boolean plannerPresent = manager.getConnectedPlannerModifier() != null;
        if (plannerPresent) {
            Map<ItemKey, Integer> snapshotStock = new HashMap<>();
            for (Map.Entry<ItemKey, Integer> en : reachableCatalog.entrySet()) {
                if (en.getKey() == null || en.getKey() == ItemKey.EMPTY) continue;
                snapshotStock.put(en.getKey(), Math.max(0, en.getValue()) + Math.max(0, manager.countBuffered(en.getKey())));
            }

            CraftPlanningService planningService = new CraftPlanningService(8);
            CraftPlanningService.PlanningResult planning = planningService.plan(manager, requestedKey, requestedAmount, snapshotStock);
            if (!planning.success) {
                order.setStatus(CloudOrderStatus.FAILED, tick);
                order.setFailReason(planning.failReason, tick);
                return;
            }
            order.setMetadataValue("craft.planDump", planning.debugDump);
            debug("plan order=%s\n%s", order.getOrderId(), planning.debugDump);
            ensureChildCraftOrders(manager, order, totalInputs, inputEndpoint, tick);
        }

        List<String> missing = new ArrayList<>();
        for (Map.Entry<ItemKey, Integer> e : totalInputs.entrySet()) {
            int atInput = Math.max(0, manager.countAtDestination(inputEndpoint.getPos(), inputEndpoint.getSide(), e.getKey()));
            int available = Math.max(0, reachableCatalog.getOrDefault(e.getKey(), 0)) + Math.max(0, manager.countBuffered(e.getKey())) + atInput;
            if (available < e.getValue()) {
                missing.add(e.getKey() + " " + e.getValue() + "/" + available);
            }
        }
        if (!missing.isEmpty()) {
            order.setStatus(CloudOrderStatus.WAITING_RESOURCES, tick);
            order.setFailReason("Not enough resources for craft: " + String.join(", ", missing), tick);
            long waitSince = parseLong(order.getMetadata().get(META_WAIT_SINCE), tick);
            if (!order.getMetadata().containsKey(META_WAIT_SINCE)) order.setMetadataValue(META_WAIT_SINCE, Long.toString(tick));
            if (!plannerPresent && tick - waitSince > WAIT_RESOURCES_FAIL_TICKS) {
                order.setStatus(CloudOrderStatus.FAILED, tick);
                order.setFailReason("Craft resources timeout after waiting " + (tick - waitSince) + " ticks", tick);
            }
            return;
        }

        if (!order.getMetadata().containsKey(META_CONSUMER_POS)) {
            order.setMetadataValue(META_CONSUMER_POS, Long.toString(consumer.getOutputEndpoint().getPos().toLong()));
            order.setMetadataValue(META_CONSUMER_IN_SIDE, Integer.toString(inputEndpoint.getSide()));
            order.setMetadataValue(META_CONSUMER_OUT_SIDE, Integer.toString(outputEndpoint.getSide()));
            order.setMetadataValue(META_CRAFT_CYCLES, Integer.toString(cycles));
        }

        for (Map.Entry<ItemKey, Integer> e : totalInputs.entrySet()) {
            if (!hasTask(order.getOrderId(), CloudTaskKind.SUPPLY, e.getKey(), inputEndpoint)) {
                CloudTask supply = new CloudTask(UUID.randomUUID(), order.getOrderId(), CloudTaskKind.SUPPLY, e.getKey(), e.getValue(), tick);
                supply.setEndpoints(null, inputEndpoint);
                supply.setAssignments(manager.getPos(), inputEndpoint.getPos());
                supply.setStatus(CloudTaskStatus.READY, tick);
                tasks.put(supply.getTaskId(), supply);
            }
        }
        if (getTaskByOrderAndKind(order.getOrderId(), CloudTaskKind.CRAFT) == null) {
            CloudTask craft = new CloudTask(UUID.randomUUID(), order.getOrderId(), CloudTaskKind.CRAFT, requestedKey, expectedOutput, tick);
            craft.setEndpoints(inputEndpoint, outputEndpoint);
            craft.setAssignments(inputEndpoint.getPos(), outputEndpoint.getPos());
            craft.setStatus(CloudTaskStatus.READY, tick);
            tasks.put(craft.getTaskId(), craft);
        }

        if (getTaskByOrderAndKind(order.getOrderId(), CloudTaskKind.PICKUP) == null) {
            CloudTask pickup = new CloudTask(UUID.randomUUID(), order.getOrderId(), CloudTaskKind.PICKUP, requestedKey, expectedOutput, tick);
            pickup.setEndpoints(outputEndpoint, null);
            pickup.setAssignments(outputEndpoint.getPos(), manager.getPos());
            pickup.setStatus(CloudTaskStatus.READY, tick);
            tasks.put(pickup.getTaskId(), pickup);
        }

        if (getTaskByOrderAndKind(order.getOrderId(), CloudTaskKind.TRANSFER) == null) {
            CloudTask transfer = new CloudTask(UUID.randomUUID(), order.getOrderId(), CloudTaskKind.TRANSFER, requestedKey, expectedOutput, tick);
            transfer.setEndpoints(null, order.getDestination());
            transfer.setAssignments(manager.getPos(), order.getDestination().getPos());
            transfer.setStatus(CloudTaskStatus.READY, tick);
            tasks.put(transfer.getTaskId(), transfer);
        }

        order.setPlannedOutputAmount(expectedOutput, tick);
        order.setMetadataValue(META_WAIT_SINCE, "");
        order.setFailReason("", tick);
        order.setStatus(CloudOrderStatus.RUNNING, tick);
    }

    private void ensureChildCraftOrders(TileMirrorManager manager,
                                        CloudOrder parent,
                                        Map<ItemKey, Integer> totalInputs,
                                        CloudEndpointRef parentInput,
                                        long tick) {
        if (parent == null || totalInputs == null || totalInputs.isEmpty() || parentInput == null) return;

        LinkedHashMap<ItemKey, Integer> reachableCatalog = manager.getReachableCatalog();
        for (Map.Entry<ItemKey, Integer> input : totalInputs.entrySet()) {
            ItemKey key = input.getKey();
            int needed = Math.max(0, input.getValue());
            int available = Math.max(0, reachableCatalog.getOrDefault(key, 0))
                    + Math.max(0, manager.countBuffered(key))
                    + Math.max(0, manager.countAtDestination(parentInput.getPos(), parentInput.getSide(), key));
            if (available >= needed) continue;

            ICloudCraftConsumer childConsumer = findConsumerFor(manager, key);
            if (childConsumer == null) continue;

            int deficit = needed - available;
            UUID existing = findChildOrder(parent.getOrderId(), key, deficit, childConsumer, "intermediate");
            if (existing != null) continue;

            CloudOrder child = new CloudOrder(UUID.randomUUID(), CloudOrderKind.CRAFT,
                    parent.getCustomerPos(), parentInput, key, deficit, tick);
            child.setParentOrderId(parent.getOrderId());
            child.setMetadataValue("craft.parentOrder", parent.getOrderId().toString());
            child.setMetadataValue("craft.stage", "intermediate");
            child.setMetadataValue("craft.consumer", Long.toString(childConsumer.getOutputEndpoint().getPos().toLong()));
            orders.put(child.getOrderId(), child);
        }
    }

    @Nullable
    private UUID findChildOrder(UUID parentOrderId, ItemKey itemKey, int amount, ICloudCraftConsumer consumer, String stage) {
        if (parentOrderId == null || itemKey == null || consumer == null) return null;
        long consumerPos = consumer.getOutputEndpoint() == null ? 0L : consumer.getOutputEndpoint().getPos().toLong();
        for (CloudOrder order : orders.values()) {
            if (order == null) continue;
            if (!Objects.equals(order.getParentOrderId(), parentOrderId)) continue;
            if (!Objects.equals(order.getItemKey(), itemKey)) continue;
            if (Math.max(0, order.getRequestedAmount()) != Math.max(0, amount)) continue;
            if (!stage.equals(order.getMetadata().get("craft.stage"))) continue;
            if (!Long.toString(consumerPos).equals(order.getMetadata().get("craft.consumer"))) continue;
            if (order.getStatus() != CloudOrderStatus.FAILED && order.getStatus() != CloudOrderStatus.CANCELLED) {
                return order.getOrderId();
            }
        }
        return null;
    }

    private void executeTasks(TileMirrorManager manager, long tick) {
        List<CloudTask> sorted = new ArrayList<>(tasks.values());
        sorted.sort(Comparator.comparingLong(CloudTask::getCreatedTick).thenComparing(CloudTask::getTaskId));
        for (CloudTask task : sorted) {
            if (task == null) continue;
            CloudOrder order = orders.get(task.getOrderId());
            if (order == null || order.getStatus() == CloudOrderStatus.CANCELLED || order.getStatus() == CloudOrderStatus.FAILED) continue;
            if (task.getStatus() == CloudTaskStatus.NEW) {
                task.setStatus(CloudTaskStatus.READY, tick);
            }
            if (isProviderTask(task) && task.getStatus() == CloudTaskStatus.READY) continue;
            if (task.getStatus() != CloudTaskStatus.RUNNING && task.getStatus() != CloudTaskStatus.BLOCKED) continue;
            if (isProviderTask(task) && !hasAssignedChannel(task)) continue;

            if (task.getStatus() == CloudTaskStatus.BLOCKED) {
                if (task.getRetryCount() >= MAX_RETRIES) {
                    task.fail("Retries exhausted after BLOCKED state", tick);
                    releaseChannel(task, tick, "task failed");
                    continue;
                }
                task.setStatus(CloudTaskStatus.RUNNING, tick);
            }

            if (task.getKind() == CloudTaskKind.SUPPLY) {
                runSupplyTask(manager, task, tick);
            } else if (task.getKind() == CloudTaskKind.CRAFT) {
                runCraftTask(manager, order, task, tick);
            } else if (task.getKind() == CloudTaskKind.PICKUP) {
                runPickupTask(manager, order, task, tick);
            } else if (task.getKind() == CloudTaskKind.TRANSFER) {
                runTransferTask(manager, task, tick);
            }

            handleTaskTimeout(task, tick);
            updateChannelProgressState(task, tick);
            if (task.getStatus() == CloudTaskStatus.DONE || task.getStatus() == CloudTaskStatus.FAILED) {
                releaseChannel(task, tick, task.getStatus().name());
            }
        }
    }

    private void runSupplyTask(TileMirrorManager manager, CloudTask task, long tick) {
        ProviderChannel channel = resolveTaskChannel(task);
        CloudEndpointRef target = task.getTarget();
        if (target == null) {
            runBufferOnlySupplyTask(manager, task, channel, tick);
            return;
        }

        ItemKey key = task.getItemKey();
        int targetAmount = task.getAmount();
        if (task.getDestinationBaseline() < 0) task.setDestinationBaseline(manager.countAtDestination(target.getPos(), target.getSide(), key));

        int moved = manager.transferFromBufferTo(target.getPos(), target.getSide(), key, Math.max(0, targetAmount - task.getCompletedAmount()), task.getTaskId());
        if (moved <= 0) {
            int need = Math.max(0, targetAmount - task.getCompletedAmount());
            if (need > 0) manager.requestFromStorageForChannel(key, Math.min(REQUEST_CHUNK, need), task.getTaskId(), channel == null ? null : channel.getGolemId());
        }

        int currentAtTarget = manager.countAtDestination(target.getPos(), target.getSide(), key);
        int deliveredByBaseline = Math.max(0, currentAtTarget - task.getDestinationBaseline());
        int completed = Math.min(targetAmount, deliveredByBaseline);
        if (completed > task.getCompletedAmount()) task.setCompletedAmount(completed, tick);
        else task.markNoProgress(tick);

        if (task.getCompletedAmount() >= targetAmount) task.setStatus(CloudTaskStatus.DONE, tick);
    }

    private void runBufferOnlySupplyTask(TileMirrorManager manager, CloudTask task, @Nullable ProviderChannel channel, long tick) {
        ItemKey key = task.getItemKey();
        int target = task.getAmount();
        int need = Math.max(0, target - task.getCompletedAmount());
        if (need > 0) {
            int accepted = manager.requestFromStorageForChannel(key, Math.min(REQUEST_CHUNK, need), task.getTaskId(), channel == null ? null : channel.getGolemId());
            if (accepted > 0) task.setCompletedAmount(Math.min(target, task.getCompletedAmount() + accepted), tick);
            else task.markNoProgress(tick);
        }

        if (task.getCompletedAmount() >= target) {
            task.setStatus(CloudTaskStatus.DONE, tick);
        }
    }

    private void runCraftTask(TileMirrorManager manager, CloudOrder order, CloudTask task, long tick) {
        if (!areAllSupplyTasksDone(order.getOrderId())) {
            task.markNoProgress(tick);
            return;
        }

        ICloudCraftConsumer consumer = resolveConsumer(manager, order);
        if (consumer == null) {
            task.fail("Craft consumer is unavailable", tick);
            return;
        }

        if (!task.isCommandIssued()) {
            int baseline = Math.max(0, consumer.getOutputCount(task.getItemKey()));
            task.setDestinationBaseline(baseline);
            int cycles = Math.max(1, parseInt(order.getMetadata().get(META_CRAFT_CYCLES), 1));
            int accepted = consumer.enqueueCloudCraft(task.getItemKey().toStack(1), cycles, task.getTaskId());
            if (accepted <= 0) {
                task.fail("Consumer rejected cloud craft task", tick);
                return;
            }
            task.setCommandIssued(true, tick);
        }

        if (!consumer.hasCloudCraftTask(task.getTaskId())) {
            int producedNow = Math.max(0, consumer.getOutputCount(task.getItemKey()) - task.getDestinationBaseline());
            if (producedNow >= task.getAmount()) {
                task.setCompletedAmount(task.getAmount(), tick);
                task.setStatus(CloudTaskStatus.DONE, tick);
                return;
            }
        }

        int current = Math.max(0, consumer.getOutputCount(task.getItemKey()));
        int produced = Math.max(0, current - task.getDestinationBaseline());
        int completed = Math.min(task.getAmount(), produced);
        if (completed > task.getCompletedAmount()) task.setCompletedAmount(completed, tick);
        else task.markNoProgress(tick);

        if (task.getCompletedAmount() >= task.getAmount()) task.setStatus(CloudTaskStatus.DONE, tick);
    }

    private void runPickupTask(TileMirrorManager manager, CloudOrder order, CloudTask task, long tick) {
        CloudTask craft = getTaskByOrderAndKind(order.getOrderId(), CloudTaskKind.CRAFT);
        if (craft == null || craft.getStatus() != CloudTaskStatus.DONE) {
            task.markNoProgress(tick);
            return;
        }
        CloudEndpointRef source = task.getSource();
        if (source == null) {
            task.fail("Missing pickup source endpoint", tick);
            return;
        }

        int need = Math.max(0, task.getAmount() - task.getCompletedAmount());
        if (need <= 0) {
            task.setStatus(CloudTaskStatus.DONE, tick);
            return;
        }

        int moved = manager.pickupFromEndpointToBuffer(source.getPos(), source.getSide(), task.getItemKey(), need, task.getTaskId());
        if (moved > 0) task.setCompletedAmount(Math.min(task.getAmount(), task.getCompletedAmount() + moved), tick);
        else task.markNoProgress(tick);

        if (task.getCompletedAmount() >= task.getAmount()) task.setStatus(CloudTaskStatus.DONE, tick);
    }

    private void runTransferTask(TileMirrorManager manager, CloudTask task, long tick) {
        CloudOrder order = orders.get(task.getOrderId());
        if (order == null || order.getDestination() == null) {
            task.fail("Missing destination for transfer task", tick);
            return;
        }

        if (order.getKind() == CloudOrderKind.CRAFT) {
            CloudTask pickup = getTaskByOrderAndKind(order.getOrderId(), CloudTaskKind.PICKUP);
            if (pickup == null || pickup.getStatus() != CloudTaskStatus.DONE) {
                task.markNoProgress(tick);
                return;
            }
        }

        BlockPos dest = order.getDestination().getPos();
        int side = order.getDestination().getSide();
        int target = task.getAmount();

        if (task.getDestinationBaseline() < 0) {
            task.setDestinationBaseline(manager.countAtDestination(dest, side, task.getItemKey()));
        }

        int needNow = Math.max(0, target - task.getCompletedAmount());
        int movedNow = manager.transferFromBufferTo(dest, side, task.getItemKey(), needNow, task.getTaskId());
        if (movedNow <= 0 && needNow > 0 && order.getKind() == CloudOrderKind.DELIVERY) {
            ProviderChannel channel = resolveTaskChannel(task);
            manager.requestFromStorageForChannel(task.getItemKey(), Math.min(REQUEST_CHUNK, needNow), task.getTaskId(), channel == null ? null : channel.getGolemId());
        }
        int currentAtDest = manager.countAtDestination(dest, side, task.getItemKey());
        int deliveredByBaseline = Math.max(0, currentAtDest - task.getDestinationBaseline());
        int completed = Math.min(target, deliveredByBaseline);

        if (completed > task.getCompletedAmount()) {
            task.setCompletedAmount(completed, tick);
        } else if (movedNow > 0) {
            task.setCompletedAmount(Math.min(target, task.getCompletedAmount() + movedNow), tick);
        } else {
            task.markNoProgress(tick);
        }

        if (task.getCompletedAmount() >= target) {
            task.setStatus(CloudTaskStatus.DONE, tick);
        } else if (manager.countBuffered(task.getItemKey()) <= 0 && order.getKind() == CloudOrderKind.DELIVERY) {
            task.setStatus(CloudTaskStatus.BLOCKED, tick);
        }
    }

    private void handleTaskTimeout(CloudTask task, long tick) {
        if (task.getStatus() != CloudTaskStatus.RUNNING) return;
        long lastProgressTick = task.getLastProgressTick() > 0L ? task.getLastProgressTick() : task.getStartedTick();
        if (lastProgressTick <= 0L) lastProgressTick = task.getCreatedTick();
        if (tick - lastProgressTick <= TASK_TIMEOUT_TICKS) return;

        int retries = task.incrementRetry(tick);
        if (retries > MAX_RETRIES) {
            task.fail("Task timeout (" + TASK_TIMEOUT_TICKS + " ticks) and retries exhausted", tick);
        } else {
            task.setStatus(CloudTaskStatus.BLOCKED, tick);
            ProviderChannel channel = resolveTaskChannel(task);
            if (channel != null && channel.getStatus() != ProviderChannelStatus.STALLED) {
                channel.markStalled(tick);
                debug("stall channel=%s task=%s noProgress=%d", channel.getChannelId(), task.getTaskId(), tick - lastProgressTick);
            }
        }
    }

    private void syncOrderStates(long tick) {
        for (CloudOrder order : orders.values()) {
            if (order == null || order.getStatus() == CloudOrderStatus.CANCELLED) continue;

            CloudTask transfer = getTaskByOrderAndKind(order.getOrderId(), CloudTaskKind.TRANSFER);
            if (transfer == null) continue;

            if (hasFailedTask(order.getOrderId())) {
                order.setStatus(CloudOrderStatus.FAILED, tick);
                order.setFailReason("Task failed", tick);
                continue;
            }

            int expected = order.getKind() == CloudOrderKind.CRAFT
                    ? Math.max(0, order.getPlannedOutputAmount())
                    : Math.max(0, order.getRequestedAmount());

            if (transfer.getStatus() == CloudTaskStatus.DONE && transfer.getCompletedAmount() >= expected) {
                order.setStatus(CloudOrderStatus.DONE, tick);
                order.setFailReason("", tick);
            } else if (order.getStatus() != CloudOrderStatus.WAITING_RESOURCES) {
                order.setStatus(CloudOrderStatus.RUNNING, tick);
            }
        }
    }

    @Nullable
    private ICloudCraftConsumer findConsumerFor(TileMirrorManager manager, ItemKey key) {
        if (manager == null || manager.getWorld() == null || key == null || key == ItemKey.EMPTY) return null;
        ItemStack requestedLike = key.toStack(1);
        if (requestedLike.isEmpty()) return null;

        for (BlockPos pos : manager.getRequestersSnapshot()) {
            TileEntity te = manager.getWorld().getTileEntity(pos);
            if (!(te instanceof ICloudCraftConsumer)) continue;
            ICloudCraftConsumer consumer = (ICloudCraftConsumer) te;
            List<ItemStack> craftables = consumer.listCraftableResults();
            if (craftables == null) continue;
            for (ItemStack out : craftables) {
                if (out != null && !out.isEmpty() && ResourceIdentity.sameResource(out, requestedLike)) return consumer;
            }
        }
        return null;
    }

    @Nullable
    private ICloudCraftConsumer resolveConsumer(TileMirrorManager manager, CloudOrder order) {
        if (manager == null || manager.getWorld() == null || order == null) return null;
        String rawPos = order.getMetadata().get(META_CONSUMER_POS);
        if (rawPos != null && !rawPos.isEmpty()) {
            try {
                BlockPos pos = BlockPos.fromLong(Long.parseLong(rawPos));
                TileEntity te = manager.getWorld().getTileEntity(pos);
                if (te instanceof ICloudCraftConsumer) return (ICloudCraftConsumer) te;
            } catch (Exception ignored) {}
        }
        return findConsumerFor(manager, order.getItemKey());
    }

    private boolean hasTask(UUID orderId, CloudTaskKind kind, ItemKey key, CloudEndpointRef target) {
        for (CloudTask task : tasks.values()) {
            if (task == null || task.getKind() != kind || !Objects.equals(task.getOrderId(), orderId)) continue;
            if (!Objects.equals(task.getItemKey(), key)) continue;
            CloudEndpointRef t = task.getTarget();
            if (t != null && target != null && t.getPos().equals(target.getPos()) && t.getSide() == target.getSide()) return true;
        }
        return false;
    }

    private boolean areAllSupplyTasksDone(UUID orderId) {
        boolean found = false;
        for (CloudTask task : tasks.values()) {
            if (task == null || task.getKind() != CloudTaskKind.SUPPLY || !Objects.equals(task.getOrderId(), orderId)) continue;
            found = true;
            if (task.getStatus() != CloudTaskStatus.DONE) return false;
        }
        return found;
    }

    private boolean hasFailedTask(UUID orderId) {
        for (CloudTask task : tasks.values()) {
            if (task != null && Objects.equals(task.getOrderId(), orderId) && task.getStatus() == CloudTaskStatus.FAILED) return true;
        }
        return false;
    }

    @Nullable
    private CloudTask getTaskByOrderAndKind(@Nullable UUID orderId, CloudTaskKind kind) {
        if (orderId == null || kind == null) return null;
        for (CloudTask task : tasks.values()) {
            if (task == null) continue;
            if (kind == task.getKind() && orderId.equals(task.getOrderId())) return task;
        }
        return null;
    }

    public boolean cancelOrder(UUID orderId) {
        CloudOrder order = orders.get(orderId);
        if (order == null) return false;

        order.setStatus(CloudOrderStatus.CANCELLED, order.getUpdatedTick());
        order.setFailReason("Cancelled by user", order.getUpdatedTick());
        for (CloudTask task : tasks.values()) {
            if (task != null && orderId != null && orderId.equals(task.getOrderId())) {
                task.setStatus(CloudTaskStatus.BLOCKED, order.getUpdatedTick());
                releaseChannel(task, order.getUpdatedTick(), "order-cancelled");
            }
        }
        return true;
    }

    @Nullable
    public CloudOrderStatus getOrderStatus(UUID orderId) {
        CloudOrder order = orders.get(orderId);
        return order == null ? null : order.getStatus();
    }

    public List<CloudTask> getTasksSnapshot() {
        return Collections.unmodifiableList(new ArrayList<>(tasks.values()));
    }

    public void rebuildNetworkSnapshot(TileMirrorManager manager) {
        CloudNetworkSnapshot network = new CloudNetworkSnapshot();
        if (manager != null && manager.getWorld() != null) {
            network.setBuiltTick(manager.getWorld().getTotalWorldTime());
            network.setRequesterPositions(manager.getRequestersSnapshot());
            network.setCraftPlannerPresent(manager.getConnectedPlannerModifier() != null);

            LinkedHashMap<ItemKey, Integer> stock = manager.getReachableCatalog();
            Map<ItemKey, Integer> mergedStock = new LinkedHashMap<>();
            for (Map.Entry<ItemKey, Integer> en : stock.entrySet()) {
                if (en == null || en.getKey() == null || en.getKey() == ItemKey.EMPTY) continue;
                mergedStock.put(en.getKey(), Math.max(0, en.getValue()) + Math.max(0, manager.countBuffered(en.getKey())));
            }
            network.setAvailableStock(mergedStock);

            Set<ItemKey> craftablesAll = new LinkedHashSet<>();
            for (BlockPos pos : manager.getRequestersSnapshot()) {
                TileEntity te = manager.getWorld().getTileEntity(pos);
                if (!(te instanceof ICloudCraftConsumer)) continue;
                ICloudCraftConsumer consumer = (ICloudCraftConsumer) te;
                List<ItemKey> craftables = new ArrayList<>();
                List<ItemStack> results = consumer.listCraftableResults();
                if (results != null) {
                    for (ItemStack out : results) {
                        if (out == null || out.isEmpty()) continue;
                        ItemKey key = ItemKey.of(out);
                        if (key == null || key == ItemKey.EMPTY) continue;
                        craftables.add(key);
                        craftablesAll.add(key);
                    }
                }
                network.putCraftables(pos.toLong(), craftables);
            }
            network.setIntermediateCraftables(craftablesAll);
        }
        this.snapshot = network;
    }

    public NBTTagCompound serializeNBT() {
        NBTTagCompound nbt = new NBTTagCompound();

        NBTTagList orderList = new NBTTagList();
        for (CloudOrder order : orders.values()) {
            if (order != null) orderList.appendTag(order.serializeNBT());
        }
        nbt.setTag(TAG_ORDERS, orderList);

        NBTTagList taskList = new NBTTagList();
        for (CloudTask task : tasks.values()) {
            if (task != null) taskList.appendTag(task.serializeNBT());
        }
        nbt.setTag(TAG_TASKS, taskList);
        nbt.setTag(TAG_NETWORK, snapshot.serializeNBT());
        return nbt;
    }

    public void deserializeNBT(@Nullable NBTTagCompound nbt) {
        orders.clear();
        tasks.clear();
        providerChannels.clear();
        snapshot = new CloudNetworkSnapshot();

        if (nbt == null) return;

        NBTTagList orderList = nbt.getTagList(TAG_ORDERS, 10);
        for (int i = 0; i < orderList.tagCount(); i++) {
            CloudOrder order = CloudOrder.deserializeNBT(orderList.getCompoundTagAt(i));
            if (order != null) orders.put(order.getOrderId(), order);
        }

        NBTTagList taskList = nbt.getTagList(TAG_TASKS, 10);
        for (int i = 0; i < taskList.tagCount(); i++) {
            CloudTask task = CloudTask.deserializeNBT(taskList.getCompoundTagAt(i));
            if (task != null) tasks.put(task.getTaskId(), task);
        }

        if (nbt.hasKey(TAG_NETWORK, 10)) {
            snapshot = CloudNetworkSnapshot.deserializeNBT(nbt.getCompoundTag(TAG_NETWORK));
        }
    }

    private void refreshProviderChannels(TileMirrorManager manager, long tick) {
        List<ProviderChannel> snapshotChannels = manager.getCloudProviderChannelsSnapshot(tick);
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (ProviderChannel ch : snapshotChannels) {
            if (ch == null || ch.getChannelId() == null || ch.getChannelId().isEmpty()) continue;
            seen.add(ch.getChannelId());
            ProviderChannel existing = providerChannels.get(ch.getChannelId());
            if (existing == null) {
                providerChannels.put(ch.getChannelId(), ch);
            } else if (existing.getGolemId() == null && ch.getGolemId() != null) {
                providerChannels.put(ch.getChannelId(), ch);
            }
        }
        Iterator<Map.Entry<String, ProviderChannel>> it = providerChannels.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ProviderChannel> e = it.next();
            if (seen.contains(e.getKey())) continue;
            String removedChannelId = e.getKey();
            it.remove();
            for (CloudTask task : tasks.values()) {
                if (task == null) continue;
                if (!removedChannelId.equals(task.getAssignedChannelId())) continue;
                task.setAssignedChannelId(null, tick);
                if (task.getStatus() == CloudTaskStatus.RUNNING || task.getStatus() == CloudTaskStatus.BLOCKED) {
                    task.setStatus(CloudTaskStatus.READY, tick);
                }
            }
        }
        if (providerChannels.isEmpty()) {
            providerChannels.put("default", new ProviderChannel("default", null, null, tick, ProviderChannelStatus.READY));
        }
        if ((tick % 40L) == 0L) {
            debug("channels snapshot: %s", describeChannels());
        }
    }

    private void scheduleProviderTasks(long tick) {
        List<CloudTask> candidates = new ArrayList<>();
        for (CloudTask task : tasks.values()) {
            if (task == null || !isProviderTask(task) || task.getStatus() != CloudTaskStatus.READY || hasAssignedChannel(task)) continue;
            candidates.add(task);
        }
        candidates.sort(Comparator.comparingLong(CloudTask::getCreatedTick).thenComparing(CloudTask::getTaskId));
        for (CloudTask task : candidates) {
            ProviderChannel free = findFreeChannel();
            if (free == null) break;
            free.markBusy(task.getTaskId(), tick);
            task.setAssignedChannelId(free.getChannelId(), tick);
            task.setStatus(CloudTaskStatus.RUNNING, tick);
            debug("assign task=%s kind=%s -> channel=%s", task.getTaskId(), task.getKind(), free.getChannelId());
        }
    }

    private boolean isProviderTask(CloudTask task) {
        return task != null && (task.getKind() == CloudTaskKind.SUPPLY || task.getKind() == CloudTaskKind.TRANSFER);
    }

    private boolean hasAssignedChannel(CloudTask task) {
        return task != null && task.getAssignedChannelId() != null && !task.getAssignedChannelId().isEmpty();
    }

    @Nullable
    private ProviderChannel resolveTaskChannel(@Nullable CloudTask task) {
        if (task == null || task.getAssignedChannelId() == null) return null;
        return providerChannels.get(task.getAssignedChannelId());
    }

    @Nullable
    private ProviderChannel findFreeChannel() {
        for (ProviderChannel channel : providerChannels.values()) {
            if (channel != null && channel.isFree()) return channel;
        }
        return null;
    }

    private void updateChannelProgressState(CloudTask task, long tick) {
        if (!isProviderTask(task) || !hasAssignedChannel(task)) return;
        ProviderChannel channel = resolveTaskChannel(task);
        if (channel == null) return;
        if (task.getLastProgressTick() >= tick) {
            channel.markProgress(tick);
        } else if ((tick - task.getLastProgressTick()) > TASK_TIMEOUT_TICKS) {
            channel.markStalled(tick);
        }
    }

    private void releaseChannel(@Nullable CloudTask task, long tick, String reason) {
        if (!isProviderTask(task) || task == null || !hasAssignedChannel(task)) return;
        ProviderChannel channel = resolveTaskChannel(task);
        String channelId = task.getAssignedChannelId();
        if (channel != null && task.getTaskId().equals(channel.getBusyTaskId())) {
            channel.release(tick);
            debug("release channel=%s task=%s reason=%s", channel.getChannelId(), task.getTaskId(), reason);
        } else {
            debug("release skipped channel=%s task=%s reason=%s", channelId, task.getTaskId(), reason);
        }
        task.setAssignedChannelId(null, tick);
    }

    private String describeChannels() {
        StringBuilder sb = new StringBuilder();
        for (ProviderChannel ch : providerChannels.values()) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append(ch.getChannelId())
                    .append("{g=").append(ch.getGolemId())
                    .append(",busy=").append(ch.getBusyTaskId())
                    .append(",status=").append(ch.getStatus())
                    .append(",last=").append(ch.getLastProgressTick())
                    .append("}");
        }
        return sb.toString();
    }

    private void debug(String fmt, Object... args) {
        ThaumicAttempts.LOGGER.debug("[Cloud] " + String.format(fmt, args));
    }

    static void writeItemKey(NBTTagCompound root, String key, ItemKey itemKey) {
        if (root == null || key == null || key.isEmpty() || itemKey == null || itemKey.item == null) return;

        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger("itemId", Item.getIdFromItem(itemKey.item));
        nbt.setInteger("meta", itemKey.meta);
        if (itemKey.tag != null) nbt.setTag("tag", itemKey.tag.copy());
        root.setTag(key, nbt);
    }

    static ItemKey readItemKey(NBTTagCompound root, String key) {
        if (root == null || key == null || !root.hasKey(key, 10)) return ItemKey.EMPTY;
        NBTTagCompound nbt = root.getCompoundTag(key);
        Item item = Item.getItemById(nbt.getInteger("itemId"));
        int meta = nbt.getInteger("meta");
        NBTTagCompound tag = nbt.hasKey("tag", 10) ? nbt.getCompoundTag("tag") : null;
        if (item == null) return ItemKey.EMPTY;
        return new ItemKey(item, meta, tag);
    }

    static <E extends Enum<E>> E safeEnum(Class<E> type, String value, E def) {
        if (value == null || value.isEmpty()) return def;
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException ex) {
            return def;
        }
    }

    private static int parseInt(String s, int def) {
        try {
            return s == null || s.isEmpty() ? def : Integer.parseInt(s);
        } catch (Exception ignored) {
            return def;
        }
    }

    private static long parseLong(String s, long def) {
        try {
            return s == null || s.isEmpty() ? def : Long.parseLong(s);
        } catch (Exception ignored) {
            return def;
        }
    }
}