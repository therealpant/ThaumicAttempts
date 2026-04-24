package therealpant.thaumicattempts.golemnet.cloud;

import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import therealpant.thaumicattempts.golemnet.tile.TileMirrorManager;
import therealpant.thaumicattempts.util.ItemKey;

import javax.annotation.Nullable;
import java.util.ArrayList;
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

    public void tick(TileMirrorManager manager) {
        if (manager == null || manager.getWorld() == null || manager.getWorld().isRemote) return;

        final long tick = manager.getWorld().getTotalWorldTime();
        for (CloudOrder order : orders.values()) {
            if (order == null) continue;
            if (order.getStatus() == CloudOrderStatus.NEW) {
                order.setStatus(CloudOrderStatus.PLANNING, tick);
            } else if (order.getStatus() == CloudOrderStatus.PLANNING) {
                order.setStatus(CloudOrderStatus.WAITING_RESOURCES, tick);
            }
        }

        if ((tick % 100L) == 0L) {
            rebuildNetworkSnapshot(manager);
        }
    }

    public UUID submitOrder(CloudOrder order) {
        if (order == null) return null;
        orders.put(order.getOrderId(), order);
        return order.getOrderId();
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