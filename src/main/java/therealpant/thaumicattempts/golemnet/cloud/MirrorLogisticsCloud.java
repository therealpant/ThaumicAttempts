package therealpant.thaumicattempts.golemnet.cloud;

import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import therealpant.thaumicattempts.golemnet.tile.TileMirrorManager;
import therealpant.thaumicattempts.util.ItemKey;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Каркас новой "облачной" логистики для менеджера зеркал.
 * Пока выполняет только хранение заказов/тасков и простую жизненную модель.
 */
public class MirrorLogisticsCloud {
    private static final String TAG_ORDERS = "orders";
    private static final String TAG_TASKS = "tasks";
    private static final String TAG_NETWORK = "network";

    private final Map<UUID, CloudOrder> orders = new LinkedHashMap<>();
    private final Map<UUID, CloudTask> tasks = new LinkedHashMap<>();
    private CloudNetworkSnapshot snapshot = new CloudNetworkSnapshot();
    private static final int TASK_TIMEOUT_TICKS = 100;
    private static final int MAX_RETRIES = 3;
    private static final int REQUEST_CHUNK = 64;

    public void tick(TileMirrorManager manager) {
        if (manager == null || manager.getWorld() == null || manager.getWorld().isRemote) return;

        final long tick = manager.getWorld().getTotalWorldTime();
        planOrders(manager, tick);
        executeTasksSequentially(manager, tick);
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
            if (order == null || order.getStatus() == CloudOrderStatus.CANCELLED || order.getStatus() == CloudOrderStatus.DONE) continue;
            if (order.getKind() != CloudOrderKind.DELIVERY) continue;

            if (order.getStatus() == CloudOrderStatus.NEW) {
                order.setStatus(CloudOrderStatus.PLANNING, tick);
            }
            if (order.getStatus() != CloudOrderStatus.PLANNING && order.getStatus() != CloudOrderStatus.WAITING_RESOURCES) continue;

            ItemKey key = order.getItemKey();
            int requested = Math.max(0, order.getRequestedAmount());
            if (requested <= 0 || key == null || key == ItemKey.EMPTY) {
                order.setStatus(CloudOrderStatus.FAILED, tick);
                order.setFailReason("Invalid delivery order payload", tick);
                continue;
            }

            int availableInCatalog = Math.max(0, reachableCatalog.getOrDefault(key, 0));
            int availableInBuffer = Math.max(0, manager.countBuffered(key));
            if (availableInCatalog <= 0 && availableInBuffer <= 0) {
                order.setStatus(CloudOrderStatus.WAITING_RESOURCES, tick);
                order.setFailReason("Resource not reachable in provider catalog: " + key, tick);
                continue;
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
            order.setFailReason("", tick);
            order.setStatus(CloudOrderStatus.RUNNING, tick);
        }
    }

    private void executeTasksSequentially(TileMirrorManager manager, long tick) {
        List<CloudTask> sorted = new ArrayList<>(tasks.values());
        sorted.sort(Comparator.comparingLong(CloudTask::getCreatedTick).thenComparing(CloudTask::getTaskId));
        for (CloudTask task : sorted) {
            if (task == null) continue;
            CloudOrder order = orders.get(task.getOrderId());
            if (order == null || order.getStatus() == CloudOrderStatus.CANCELLED || order.getStatus() == CloudOrderStatus.FAILED) continue;
            if (order.getKind() != CloudOrderKind.DELIVERY) continue;

            if (task.getStatus() == CloudTaskStatus.NEW) {
                task.setStatus(CloudTaskStatus.READY, tick);
            }
            if (task.getStatus() == CloudTaskStatus.READY) {
                task.setStatus(CloudTaskStatus.RUNNING, tick);
            }
            if (task.getStatus() != CloudTaskStatus.RUNNING && task.getStatus() != CloudTaskStatus.BLOCKED) continue;

            if (task.getStatus() == CloudTaskStatus.BLOCKED) {
                if (task.getRetryCount() >= MAX_RETRIES) {
                    task.fail("Retries exhausted after BLOCKED state", tick);
                    continue;
                }
                task.setStatus(CloudTaskStatus.RUNNING, tick);
            }

            if (task.getKind() == CloudTaskKind.SUPPLY) {
                runSupplyTask(manager, task, tick);
            } else if (task.getKind() == CloudTaskKind.TRANSFER) {
                runTransferTask(manager, task, tick);
            }

            handleTaskTimeout(task, tick);
        }
    }

    private void runSupplyTask(TileMirrorManager manager, CloudTask task, long tick) {
        ItemKey key = task.getItemKey();
        int target = task.getAmount();
        int buffered = manager.countBuffered(key);
        int completed = Math.min(target, Math.max(0, buffered));
        if (completed > task.getCompletedAmount()) {
            task.setCompletedAmount(completed, tick);
        } else {
            task.markNoProgress(tick);
        }

        if (task.getCompletedAmount() >= target) {
            task.setStatus(CloudTaskStatus.DONE, tick);
            return;
        }

        int need = Math.max(0, target - task.getCompletedAmount());
        if (need > 0) {
            int requested = Math.min(REQUEST_CHUNK, need);
            manager.requestFromStorageByGolem(key, requested, task.getTaskId());
        }
    }

    private void runTransferTask(TileMirrorManager manager, CloudTask task, long tick) {
        CloudOrder order = orders.get(task.getOrderId());
        if (order == null || order.getDestination() == null) {
            task.fail("Missing destination for transfer task", tick);
            return;
        }

        BlockPos dest = order.getDestination().getPos();
        int side = order.getDestination().getSide();
        int target = task.getAmount();

        if (task.getDestinationBaseline() < 0) {
            task.setDestinationBaseline(manager.countAtDestination(dest, side, task.getItemKey()));
        }

        int movedNow = manager.transferFromBufferTo(dest, side, task.getItemKey(), Math.max(0, target - task.getCompletedAmount()), task.getTaskId());

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
        } else if (manager.countBuffered(task.getItemKey()) <= 0) {
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
        }
    }

    private void syncOrderStates(long tick) {
        for (CloudOrder order : orders.values()) {
            if (order == null || order.getStatus() == CloudOrderStatus.CANCELLED || order.getKind() != CloudOrderKind.DELIVERY) continue;

            CloudTask supply = getTaskByOrderAndKind(order.getOrderId(), CloudTaskKind.SUPPLY);
            CloudTask transfer = getTaskByOrderAndKind(order.getOrderId(), CloudTaskKind.TRANSFER);
            if (supply == null || transfer == null) continue;

            if (supply.getStatus() == CloudTaskStatus.FAILED || transfer.getStatus() == CloudTaskStatus.FAILED) {
                order.setStatus(CloudOrderStatus.FAILED, tick);
                order.setFailReason("Task failed: " + (supply.getStatus() == CloudTaskStatus.FAILED ? supply.getFailReason() : transfer.getFailReason()), tick);
                continue;
            }

            if (transfer.getStatus() == CloudTaskStatus.DONE && transfer.getCompletedAmount() >= order.getRequestedAmount()) {
                order.setStatus(CloudOrderStatus.DONE, tick);
                order.setFailReason("", tick);
            } else if (order.getStatus() != CloudOrderStatus.WAITING_RESOURCES) {
                order.setStatus(CloudOrderStatus.RUNNING, tick);
            }
        }
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
}