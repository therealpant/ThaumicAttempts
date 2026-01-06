package therealpant.thaumicattempts.world.data;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TAInfectedChunksData extends WorldSavedData {

    private static final String DATA_NAME = "thaumicattempts_infected_chunks";

    private final Set<Long> infectedChunks = new LongOpenHashSet();
    private final Set<Long> activeInfectedChunks = new LongOpenHashSet();
    private final Map<UUID, Long> seedToChunk = new HashMap<>();
    private final Map<UUID, Long> anomalyToChunk = new HashMap<>();

    public long lastManagerTickTime;
    public long lastActivationAttemptTime;
    public String lastActivationFailReason = "";
    public int lastCandidatesChecked;

    public TAInfectedChunksData() {
        super(DATA_NAME);
    }

    public TAInfectedChunksData(String name) {
        super(name);
    }

    public static TAInfectedChunksData get(World world) {
        if (world == null) return new TAInfectedChunksData();

        MapStorage storage = world.getMapStorage();
        if (storage == null) return new TAInfectedChunksData();

        TAInfectedChunksData data = (TAInfectedChunksData) storage.getOrLoadData(TAInfectedChunksData.class, DATA_NAME);
        if (data == null) {
            data = new TAInfectedChunksData();
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    public static long pack(int cx, int cz) {
        return (((long) cx) << 32) ^ (cz & 0xffffffffL);
    }

    public static int unpackX(long key) {
        return (int) (key >> 32);
    }

    public static int unpackZ(long key) {
        return (int) key;
    }

    public boolean addInfectedChunk(long chunkKey) {
        boolean changed = infectedChunks.add(chunkKey);
        if (changed) {
            markDirty();
        }
        return changed;
    }

    public boolean removeInfectedChunk(long chunkKey) {
        boolean changed = infectedChunks.remove(chunkKey);
        if (changed) {
            markDirty();
        }
        return changed;
    }

    public boolean markChunkActive(long chunkKey) {
        boolean changed = activeInfectedChunks.add(chunkKey);
        if (changed) {
            markDirty();
        }
        return changed;
    }

    public boolean unmarkChunkActive(long chunkKey) {
        boolean changed = activeInfectedChunks.remove(chunkKey);
        if (changed) {
            markDirty();
        }
        return changed;
    }

    public boolean isInfected(long chunkKey) {
        return infectedChunks.contains(chunkKey);
    }

    public boolean isActive(long chunkKey) {
        return activeInfectedChunks.contains(chunkKey);
    }

    public void trackSeed(UUID seedId, long chunkKey) {
        if (seedId == null) return;
        seedToChunk.put(seedId, chunkKey);
        markDirty();
    }

    @Nullable
    public Long removeSeed(UUID seedId) {
        if (seedId == null) return null;
        Long key = seedToChunk.remove(seedId);
        if (key != null) {
            markDirty();
        }
        return key;
    }

    public void trackAnomaly(UUID anomalyId, long chunkKey) {
        if (anomalyId == null) return;
        anomalyToChunk.put(anomalyId, chunkKey);
        markDirty();
    }

    @Nullable
    public Long removeAnomaly(UUID anomalyId) {
        if (anomalyId == null) return null;
        Long key = anomalyToChunk.remove(anomalyId);
        if (key != null) {
            markDirty();
        }
        return key;
    }

    @Nullable
    public Long findChunkForSeed(UUID seedId) {
        return seedId == null ? null : seedToChunk.get(seedId);
    }

    @Nullable
    public Long findChunkForAnomaly(UUID anomalyId) {
        return anomalyId == null ? null : anomalyToChunk.get(anomalyId);
    }

    public Set<Long> getInfectedChunks() {
        return Collections.unmodifiableSet(infectedChunks);
    }

    public Set<Long> getActiveInfectedChunks() {
        return Collections.unmodifiableSet(activeInfectedChunks);
    }

    public int getTrackedSeeds() {
        return seedToChunk.size();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        infectedChunks.clear();
        activeInfectedChunks.clear();
        seedToChunk.clear();
        anomalyToChunk.clear();

        lastManagerTickTime = nbt.getLong("lastManagerTickTime");
        lastActivationAttemptTime = nbt.getLong("lastActivationAttemptTime");
        lastActivationFailReason = nbt.getString("lastActivationFailReason");
        lastCandidatesChecked = nbt.getInteger("lastCandidatesChecked");

        readChunkSet(nbt.getTagList("infectedChunks", 4), infectedChunks);
        readChunkSet(nbt.getTagList("activeInfectedChunks", 4), activeInfectedChunks);

        NBTTagList seeds = nbt.getTagList("seedToChunk", 10);
        for (int i = 0; i < seeds.tagCount(); i++) {
            NBTTagCompound tag = seeds.getCompoundTagAt(i);
            if (!tag.hasUniqueId("seed")) continue;
            UUID id = tag.getUniqueId("seed");
            long key = tag.getLong("chunk");
            seedToChunk.put(id, key);
        }

        NBTTagList anomalies = nbt.getTagList("anomalyToChunk", 10);
        for (int i = 0; i < anomalies.tagCount(); i++) {
            NBTTagCompound tag = anomalies.getCompoundTagAt(i);
            if (!tag.hasUniqueId("anomaly")) continue;
            UUID id = tag.getUniqueId("anomaly");
            long key = tag.getLong("chunk");
            anomalyToChunk.put(id, key);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        nbt.setLong("lastManagerTickTime", lastManagerTickTime);
        nbt.setLong("lastActivationAttemptTime", lastActivationAttemptTime);
        nbt.setString("lastActivationFailReason", lastActivationFailReason == null ? "" : lastActivationFailReason);
        nbt.setInteger("lastCandidatesChecked", lastCandidatesChecked);

        nbt.setTag("infectedChunks", writeChunkSet(infectedChunks));
        nbt.setTag("activeInfectedChunks", writeChunkSet(activeInfectedChunks));

        NBTTagList seeds = new NBTTagList();
        for (Map.Entry<UUID, Long> e : seedToChunk.entrySet()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setUniqueId("seed", e.getKey());
            tag.setLong("chunk", e.getValue());
            seeds.appendTag(tag);
        }
        nbt.setTag("seedToChunk", seeds);

        NBTTagList anomalies = new NBTTagList();
        for (Map.Entry<UUID, Long> e : anomalyToChunk.entrySet()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setUniqueId("anomaly", e.getKey());
            tag.setLong("chunk", e.getValue());
            anomalies.appendTag(tag);
        }
        nbt.setTag("anomalyToChunk", anomalies);

        return nbt;
    }

    private static void readChunkSet(NBTTagList list, Set<Long> target) {
        for (int i = 0; i < list.tagCount(); i++) {
            target.add(((NBTTagLong) list.get(i)).getLong());
        }
    }

    private static NBTTagList writeChunkSet(Set<Long> source) {
        NBTTagList list = new NBTTagList();
        for (Long key : source) {
            list.appendTag(new NBTTagLong(key));
        }
        return list;
    }
}