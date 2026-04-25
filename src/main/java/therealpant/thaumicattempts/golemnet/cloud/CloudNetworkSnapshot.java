package therealpant.thaumicattempts.golemnet.cloud;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.util.math.BlockPos;
import therealpant.thaumicattempts.util.ItemKey;

import java.util.*;

public class CloudNetworkSnapshot {
    private long builtTick;
    private boolean craftPlannerPresent;
    private final Set<BlockPos> requesterPositions = new HashSet<>();
    private final Map<Long, Set<ItemKey>> craftablesByConsumer = new LinkedHashMap<>();
    private final Map<ItemKey, Integer> availableStock = new LinkedHashMap<>();
    private final Set<ItemKey> intermediateCraftables = new LinkedHashSet<>();

    public long getBuiltTick() { return builtTick; }
    public void setBuiltTick(long builtTick) { this.builtTick = builtTick; }

    public boolean isCraftPlannerPresent() { return craftPlannerPresent; }
    public void setCraftPlannerPresent(boolean craftPlannerPresent) { this.craftPlannerPresent = craftPlannerPresent; }

    public Set<BlockPos> getRequesterPositions() { return Collections.unmodifiableSet(requesterPositions); }
    public void setRequesterPositions(Set<BlockPos> positions) {
        requesterPositions.clear();
        if (positions == null) return;
        for (BlockPos pos : positions) if (pos != null) requesterPositions.add(pos.toImmutable());
    }

    public void putCraftables(long consumerPos, Collection<ItemKey> craftables) {
        LinkedHashSet<ItemKey> set = new LinkedHashSet<>();
        if (craftables != null) {
            for (ItemKey key : craftables) {
                if (key != null && key != ItemKey.EMPTY) set.add(key);
            }
        }
        craftablesByConsumer.put(consumerPos, set);
    }

    public Map<Long, Set<ItemKey>> getCraftablesByConsumer() {
        return Collections.unmodifiableMap(craftablesByConsumer);
    }

    public void setAvailableStock(Map<ItemKey, Integer> stock) {
        availableStock.clear();
        if (stock == null) return;
        for (Map.Entry<ItemKey, Integer> en : stock.entrySet()) {
            if (en == null || en.getKey() == null || en.getKey() == ItemKey.EMPTY) continue;
            availableStock.put(en.getKey(), Math.max(0, en.getValue()));
        }
    }

    public Map<ItemKey, Integer> getAvailableStock() { return Collections.unmodifiableMap(availableStock); }

    public void setIntermediateCraftables(Collection<ItemKey> items) {
        intermediateCraftables.clear();
        if (items == null) return;
        for (ItemKey key : items) {
            if (key != null && key != ItemKey.EMPTY) intermediateCraftables.add(key);
        }
    }

    public Set<ItemKey> getIntermediateCraftables() { return Collections.unmodifiableSet(intermediateCraftables); }

    public NBTTagCompound serializeNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setLong("builtTick", builtTick);
        nbt.setBoolean("craftPlannerPresent", craftPlannerPresent);
        NBTTagList req = new NBTTagList();
        for (BlockPos pos : requesterPositions) req.appendTag(new NBTTagLong(pos.toLong()));
        nbt.setTag("requesters", req);

        NBTTagList craftables = new NBTTagList();
        for (Map.Entry<Long, Set<ItemKey>> en : craftablesByConsumer.entrySet()) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setLong("consumer", en.getKey());
            NBTTagList keys = new NBTTagList();
            for (ItemKey key : en.getValue()) {
                NBTTagCompound item = new NBTTagCompound();
                MirrorLogisticsCloud.writeItemKey(item, "k", key);
                keys.appendTag(item);
            }
            entry.setTag("keys", keys);
            craftables.appendTag(entry);
        }
        nbt.setTag("craftablesByConsumer", craftables);

        NBTTagList stock = new NBTTagList();
        for (Map.Entry<ItemKey, Integer> en : availableStock.entrySet()) {
            NBTTagCompound e = new NBTTagCompound();
            MirrorLogisticsCloud.writeItemKey(e, "k", en.getKey());
            e.setInteger("v", Math.max(0, en.getValue()));
            stock.appendTag(e);
        }
        nbt.setTag("availableStock", stock);

        NBTTagList intermediates = new NBTTagList();
        for (ItemKey key : intermediateCraftables) {
            NBTTagCompound e = new NBTTagCompound();
            MirrorLogisticsCloud.writeItemKey(e, "k", key);
            intermediates.appendTag(e);
        }
        nbt.setTag("intermediateCraftables", intermediates);
        return nbt;
    }

    public static CloudNetworkSnapshot deserializeNBT(NBTTagCompound nbt) {
        CloudNetworkSnapshot snapshot = new CloudNetworkSnapshot();
        if (nbt == null) return snapshot;

        snapshot.builtTick = nbt.getLong("builtTick");
        snapshot.craftPlannerPresent = nbt.getBoolean("craftPlannerPresent");
        NBTTagList req = nbt.getTagList("requesters", 4);
        for (int i = 0; i < req.tagCount(); i++) {
            snapshot.requesterPositions.add(BlockPos.fromLong(((NBTTagLong) req.get(i)).getLong()).toImmutable());
        }

        NBTTagList craftables = nbt.getTagList("craftablesByConsumer", 10);
        for (int i = 0; i < craftables.tagCount(); i++) {
            NBTTagCompound entry = craftables.getCompoundTagAt(i);
            long consumer = entry.getLong("consumer");
            NBTTagList keys = entry.getTagList("keys", 10);
            LinkedHashSet<ItemKey> set = new LinkedHashSet<>();
            for (int j = 0; j < keys.tagCount(); j++) {
                ItemKey k = MirrorLogisticsCloud.readItemKey(keys.getCompoundTagAt(j), "k");
                if (k != null && k != ItemKey.EMPTY) set.add(k);
            }
            snapshot.craftablesByConsumer.put(consumer, set);
        }

        NBTTagList stock = nbt.getTagList("availableStock", 10);
        for (int i = 0; i < stock.tagCount(); i++) {
            NBTTagCompound entry = stock.getCompoundTagAt(i);
            ItemKey key = MirrorLogisticsCloud.readItemKey(entry, "k");
            if (key == null || key == ItemKey.EMPTY) continue;
            snapshot.availableStock.put(key, Math.max(0, entry.getInteger("v")));
        }

        NBTTagList intermediates = nbt.getTagList("intermediateCraftables", 10);
        for (int i = 0; i < intermediates.tagCount(); i++) {
            ItemKey key = MirrorLogisticsCloud.readItemKey(intermediates.getCompoundTagAt(i), "k");
            if (key != null && key != ItemKey.EMPTY) snapshot.intermediateCraftables.add(key);
        }
        return snapshot;
    }
}