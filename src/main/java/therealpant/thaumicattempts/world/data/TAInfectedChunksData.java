package therealpant.thaumicattempts.world.data;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import javax.annotation.Nullable;
import java.util.*;

import therealpant.thaumicattempts.api.FluxAnomalyTier;

public class TAInfectedChunksData extends WorldSavedData {

    private static final String DATA_NAME = "thaumicattempts_infected_chunks";

    // planned anomaly spawns: chunkKey -> scheduledWorldTime
    private final Map<Long, Long> scheduledChunks = new HashMap<>();


    private final Set<Long> infectedChunks = new LongOpenHashSet();
    private final Set<Long> activeInfectedChunks = new LongOpenHashSet();
    private final Map<UUID, Long> seedToChunk = new HashMap<>();
    private final Map<UUID, Long> anomalyToChunk = new HashMap<>();
    private final Map<UUID, BlockPos> seedPositions = new HashMap<>();
    private final Map<Long, BlockPos> chunkSeedPositions = new HashMap<>();
    private final Map<Long, FluxAnomalyTier> activeChunkTiers = new HashMap<>();
    private BlockPos lastActivatedSeedPos = null;
    private FluxAnomalyTier lastActivatedTier = null;
    private String lastResourcePlacement = "";
    private int lastResourcePlacementAttempts = 0;
    private int lastResourcePlacementSuccess = 0;
    private String lastResourcePlacementFailure = "";
    private int lastResourcePlacementFailureCount = 0;

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
            activeChunkTiers.remove(chunkKey);
            chunkSeedPositions.remove(chunkKey);
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

    public void clearChunkTracking(long chunkKey) {
        activeChunkTiers.remove(chunkKey);
        chunkSeedPositions.remove(chunkKey);
        markDirty();
    }

    public void removeSeedsForChunk(long chunkKey) {
        boolean changed = false;
        for (java.util.Iterator<Map.Entry<UUID, Long>> it = seedToChunk.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> entry = it.next();
            if (entry.getValue() != null && entry.getValue() == chunkKey) {
                seedPositions.remove(entry.getKey());
                it.remove();
                changed = true;
            }
        }
        if (chunkSeedPositions.remove(chunkKey) != null) {
            changed = true;
        }
        if (changed) {
            markDirty();
        }
    }

    public void trackSeed(UUID seedId, long chunkKey, @Nullable BlockPos seedPos) {
        if (seedId == null) return;
        seedToChunk.put(seedId, chunkKey);
        if (seedPos != null) {
            seedPositions.put(seedId, seedPos.toImmutable());
            chunkSeedPositions.put(chunkKey, seedPos.toImmutable());
        }
        markDirty();
    }

    public void setActiveChunkTier(long chunkKey, FluxAnomalyTier tier) {
        if (tier == null) return;
        activeChunkTiers.put(chunkKey, tier);
        markDirty();
    }

    public void setLastActivatedInfo(@Nullable FluxAnomalyTier tier, @Nullable BlockPos seedPos) {
        lastActivatedTier = tier;
        lastActivatedSeedPos = seedPos == null ? null : seedPos.toImmutable();
        markDirty();
    }

    public void setLastResourcePlacement(String reason, int attempts, int success) {
        lastResourcePlacement = reason == null ? "" : reason;
        lastResourcePlacementAttempts = Math.max(0, attempts);
        lastResourcePlacementSuccess = Math.max(0, success);
        markDirty();
    }

    public void setLastResourcePlacementFailure(String reason, int count) {
        lastResourcePlacementFailure = reason == null ? "" : reason;
        lastResourcePlacementFailureCount = Math.max(0, count);
        markDirty();
    }

    public boolean isScheduled(long chunkKey) {
        return scheduledChunks.containsKey(chunkKey);
    }

    public boolean scheduleChunk(long chunkKey, long worldTime) {
        if (scheduledChunks.containsKey(chunkKey)) return false;
        scheduledChunks.put(chunkKey, worldTime);
        markDirty();
        return true;
    }

    /** Возвращает true если этот чанк был scheduled и мы его "забрали" на спавн */
    public boolean consumeSchedule(long chunkKey) {
        if (scheduledChunks.remove(chunkKey) != null) {
            markDirty();
            return true;
        }
        return false;
    }

    /** Лимитируем рост scheduled (TTL, например 30 минут) */
    public void cleanupSchedules(long now, long ttlTicks, int hardCap) {
        if (scheduledChunks.isEmpty()) return;

        // жёсткий кап на всякий случай
        if (scheduledChunks.size() > hardCap) {
            List<Map.Entry<Long, Long>> entries = new ArrayList<>(scheduledChunks.entrySet());
            entries.sort(Map.Entry.comparingByValue());
            int toRemove = scheduledChunks.size() - hardCap;
            for (int i = 0; i < toRemove; i++) {
                scheduledChunks.remove(entries.get(i).getKey());
            }
            markDirty();
            return;
        }

        boolean removed = false;
        Iterator<Map.Entry<Long, Long>> it = scheduledChunks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Long> e = it.next();
            if (now - e.getValue() > ttlTicks) {
                it.remove();
                removed = true;
            }
        }
        if (removed) markDirty();
    }

    @Nullable
    public Long removeSeed(UUID seedId) {
        if (seedId == null) return null;
        Long key = seedToChunk.remove(seedId);
        if (key != null) {
            seedPositions.remove(seedId);
            if (key != null) {
                chunkSeedPositions.remove(key);
            }
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

    public Set<Long> getInfectedChunkKeys() {
        return infectedChunks;
    }

    public Set<Long> getActiveChunkKeys() {
        return activeInfectedChunks;
    }

    public Map<Long, FluxAnomalyTier> getActiveChunkTiers() {
        return Collections.unmodifiableMap(activeChunkTiers);
    }

    @Nullable
    public BlockPos getSeedPosition(UUID seedId) {
        return seedId == null ? null : seedPositions.get(seedId);
    }

    @Nullable
    public BlockPos getSeedPositionForChunk(long chunkKey) {
        return chunkSeedPositions.get(chunkKey);
    }

    @Nullable
    public FluxAnomalyTier getTierForChunk(long chunkKey) {
        return activeChunkTiers.get(chunkKey);
    }

    public int getTrackedSeeds() {
        return seedToChunk.size();
    }

    public int getTrackedAnomalies() {
        return anomalyToChunk.size();
    }

    @Nullable
    public BlockPos getLastActivatedSeedPos() {
        return lastActivatedSeedPos;
    }

    @Nullable
    public FluxAnomalyTier getLastActivatedTier() {
        return lastActivatedTier;
    }

    public String getLastResourcePlacement() {
        return lastResourcePlacement;
    }

    public int getLastResourcePlacementAttempts() {
        return lastResourcePlacementAttempts;
    }

    public int getLastResourcePlacementSuccess() {
        return lastResourcePlacementSuccess;
    }

    public String getLastResourcePlacementFailure() {
        return lastResourcePlacementFailure;
    }

    public int getLastResourcePlacementFailureCount() {
        return lastResourcePlacementFailureCount;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        infectedChunks.clear();
        activeInfectedChunks.clear();
        seedToChunk.clear();
        anomalyToChunk.clear();
        seedPositions.clear();
        chunkSeedPositions.clear();
        activeChunkTiers.clear();
        lastActivatedSeedPos = null;
        lastActivatedTier = null;
        lastResourcePlacement = "";
        lastResourcePlacementAttempts = 0;
        lastResourcePlacementSuccess = 0;
        lastResourcePlacementFailure = "";
        lastResourcePlacementFailureCount = 0;

        lastManagerTickTime = nbt.getLong("lastManagerTickTime");
        lastActivationAttemptTime = nbt.getLong("lastActivationAttemptTime");
        lastActivationFailReason = nbt.getString("lastActivationFailReason");
        lastCandidatesChecked = nbt.getInteger("lastCandidatesChecked");
        lastResourcePlacement = nbt.getString("lastResourcePlacement");
        lastResourcePlacementAttempts = nbt.getInteger("lastResourcePlacementAttempts");
        lastResourcePlacementSuccess = nbt.getInteger("lastResourcePlacementSuccess");
        lastResourcePlacementFailure = nbt.getString("lastResourcePlacementFailure");
        lastResourcePlacementFailureCount = nbt.getInteger("lastResourcePlacementFailureCount");
        if (nbt.hasKey("lastSeedX", 3) && nbt.hasKey("lastSeedY", 3) && nbt.hasKey("lastSeedZ", 3)) {
            lastActivatedSeedPos = new BlockPos(nbt.getInteger("lastSeedX"), nbt.getInteger("lastSeedY"), nbt.getInteger("lastSeedZ"));
        }
        if (nbt.hasKey("lastTier", 8)) {
            try {
                lastActivatedTier = FluxAnomalyTier.valueOf(nbt.getString("lastTier"));
            } catch (IllegalArgumentException ignored) {
                lastActivatedTier = null;
            }
        }

        readChunkSet(nbt.getTagList("infectedChunks", 4), infectedChunks);
        readChunkSet(nbt.getTagList("activeInfectedChunks", 4), activeInfectedChunks);

        NBTTagList seeds = nbt.getTagList("seedToChunk", 10);
        for (int i = 0; i < seeds.tagCount(); i++) {
            NBTTagCompound tag = seeds.getCompoundTagAt(i);
            if (!tag.hasUniqueId("seed")) continue;
            UUID id = tag.getUniqueId("seed");
            long key = tag.getLong("chunk");
            seedToChunk.put(id, key);
            if (tag.hasKey("sx", 3) && tag.hasKey("sy", 3) && tag.hasKey("sz", 3)) {
                BlockPos pos = new BlockPos(tag.getInteger("sx"), tag.getInteger("sy"), tag.getInteger("sz"));
                seedPositions.put(id, pos);
                chunkSeedPositions.put(key, pos);
            }
        }

        NBTTagList anomalies = nbt.getTagList("anomalyToChunk", 10);
        for (int i = 0; i < anomalies.tagCount(); i++) {
            NBTTagCompound tag = anomalies.getCompoundTagAt(i);
            if (!tag.hasUniqueId("anomaly")) continue;
            UUID id = tag.getUniqueId("anomaly");
            long key = tag.getLong("chunk");
            anomalyToChunk.put(id, key);
        }

        NBTTagList tiers = nbt.getTagList("activeChunkTiers", 10);
        for (int i = 0; i < tiers.tagCount(); i++) {
            NBTTagCompound tag = tiers.getCompoundTagAt(i);
            long key = tag.getLong("chunk");
            if (!tag.hasKey("tier", 8)) continue;
            try {
                FluxAnomalyTier tier = FluxAnomalyTier.valueOf(tag.getString("tier"));
                activeChunkTiers.put(key, tier);
            } catch (IllegalArgumentException ignored) {
                // ignore bad tiers
            }
        }

        scheduledChunks.clear();
        if (nbt.hasKey("scheduled", 9)) { // 9 = TAG_LIST
            NBTTagList list = nbt.getTagList("scheduled", 10); // 10 = TAG_COMPOUND
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound t = list.getCompoundTagAt(i);
                long k = t.getLong("k");
                long time = t.getLong("t");
                scheduledChunks.put(k, time);
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        nbt.setLong("lastManagerTickTime", lastManagerTickTime);
        nbt.setLong("lastActivationAttemptTime", lastActivationAttemptTime);
        nbt.setString("lastActivationFailReason", lastActivationFailReason == null ? "" : lastActivationFailReason);
        nbt.setInteger("lastCandidatesChecked", lastCandidatesChecked);
        nbt.setString("lastResourcePlacement", lastResourcePlacement == null ? "" : lastResourcePlacement);
        nbt.setInteger("lastResourcePlacementAttempts", lastResourcePlacementAttempts);
        nbt.setInteger("lastResourcePlacementSuccess", lastResourcePlacementSuccess);
        nbt.setString("lastResourcePlacementFailure", lastResourcePlacementFailure == null ? "" : lastResourcePlacementFailure);
        nbt.setInteger("lastResourcePlacementFailureCount", lastResourcePlacementFailureCount);
        if (lastActivatedSeedPos != null) {
            nbt.setInteger("lastSeedX", lastActivatedSeedPos.getX());
            nbt.setInteger("lastSeedY", lastActivatedSeedPos.getY());
            nbt.setInteger("lastSeedZ", lastActivatedSeedPos.getZ());
        }
        if (lastActivatedTier != null) {
            nbt.setString("lastTier", lastActivatedTier.name());
        }

        nbt.setTag("infectedChunks", writeChunkSet(infectedChunks));
        nbt.setTag("activeInfectedChunks", writeChunkSet(activeInfectedChunks));

        NBTTagList seeds = new NBTTagList();
        for (Map.Entry<UUID, Long> e : seedToChunk.entrySet()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setUniqueId("seed", e.getKey());
            tag.setLong("chunk", e.getValue());
            BlockPos pos = seedPositions.get(e.getKey());
            if (pos != null) {
                tag.setInteger("sx", pos.getX());
                tag.setInteger("sy", pos.getY());
                tag.setInteger("sz", pos.getZ());
            }
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

        NBTTagList tiers = new NBTTagList();
        for (Map.Entry<Long, FluxAnomalyTier> e : activeChunkTiers.entrySet()) {
            if (e.getValue() == null) continue;
            NBTTagCompound tag = new NBTTagCompound();
            tag.setLong("chunk", e.getKey());
            tag.setString("tier", e.getValue().name());
            tiers.appendTag(tag);
        }
        nbt.setTag("activeChunkTiers", tiers);

        NBTTagList list = new NBTTagList();
        for (Map.Entry<Long, Long> e : scheduledChunks.entrySet()) {
            NBTTagCompound t = new NBTTagCompound();
            t.setLong("k", e.getKey());
            t.setLong("t", e.getValue());
            list.appendTag(t);
        }
        nbt.setTag("scheduled", list);

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