package therealpant.thaumicattempts.integration.rift;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Contamination survives controller replacement; deferred burns never force-load monolith chunks. */
public class RiftWellWorldData extends WorldSavedData {
    private static final String KEY = "thaumicattempts_rift_wells";
    private final Map<Long, UUID> contaminated = new HashMap<>();
    private final Set<Long> burns = new HashSet<>();
    public RiftWellWorldData() { super(KEY); }
    public RiftWellWorldData(String name) { super(name); }
    public static RiftWellWorldData get(World world) {
        RiftWellWorldData data = (RiftWellWorldData) world.getPerWorldStorage().getOrLoadData(RiftWellWorldData.class, KEY);
        if (data == null) { data = new RiftWellWorldData(); world.getPerWorldStorage().setData(KEY, data); }
        return data;
    }
    public boolean isContaminated(BlockPos pos) { return contaminated.containsKey(pos.toLong()); }
    public UUID rift(BlockPos pos) { return contaminated.get(pos.toLong()); }
    public void contaminate(BlockPos pos, UUID id) { contaminated.put(pos.toLong(), id); markDirty(); }
    public void clean(BlockPos pos) { contaminated.remove(pos.toLong()); markDirty(); }
    public void burn(BlockPos pos) { burns.add(pos.toLong()); markDirty(); }
    public boolean consumeBurn(BlockPos pos) {
        if (!burns.remove(pos.toLong())) return false;
        markDirty(); return true;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        contaminated.clear(); burns.clear();
        NBTTagList list = tag.getTagList("Contaminated", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound e = list.getCompoundTagAt(i);
            contaminated.put(e.getLong("Pos"), e.hasUniqueId("Rift") ? e.getUniqueId("Rift") : null);
        }
        list = tag.getTagList("Burns", 10);
        for (int i = 0; i < list.tagCount(); i++) burns.add(list.getCompoundTagAt(i).getLong("Pos"));
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<Long, UUID> entry : contaminated.entrySet()) {
            NBTTagCompound e = new NBTTagCompound(); e.setLong("Pos", entry.getKey());
            if (entry.getValue() != null) e.setUniqueId("Rift", entry.getValue());
            list.appendTag(e);
        }
        tag.setTag("Contaminated", list); list = new NBTTagList();
        for (long pos : burns) { NBTTagCompound e = new NBTTagCompound(); e.setLong("Pos", pos); list.appendTag(e); }
        tag.setTag("Burns", list); return tag;
    }
}
