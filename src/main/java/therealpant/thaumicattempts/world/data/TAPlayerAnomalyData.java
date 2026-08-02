package therealpant.thaumicattempts.world.data;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import therealpant.thaumicattempts.api.FluxAnomalyTier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player natural anomaly budgets. Stored in the overworld so that the
 * Nether and End share the same authoritative save.
 */
public final class TAPlayerAnomalyData extends WorldSavedData {

    private static final String DATA_NAME = "thaumicattempts_player_anomalies";
    public static final long CYCLE_COOLDOWN_TICKS = 15L * 60L * 20L;

    private final Map<UUID, Progress> players = new HashMap<>();

    public enum Pool {
        OVERWORLD,
        NETHER,
        END
    }

    public TAPlayerAnomalyData() {
        super(DATA_NAME);
    }

    public TAPlayerAnomalyData(String name) {
        super(name);
    }

    public static TAPlayerAnomalyData get(World world) {
        World storageWorld = world;
        if (world instanceof WorldServer && world.getMinecraftServer() != null) {
            WorldServer overworld = world.getMinecraftServer().getWorld(0);
            if (overworld != null) storageWorld = overworld;
        }
        MapStorage storage = storageWorld.getMapStorage();
        TAPlayerAnomalyData data =
                (TAPlayerAnomalyData) storage.getOrLoadData(TAPlayerAnomalyData.class, DATA_NAME);
        if (data == null) {
            data = new TAPlayerAnomalyData();
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    public boolean canSpend(UUID playerId, Pool pool, FluxAnomalyTier tier, long now) {
        Progress progress = progress(playerId);
        if (refresh(progress, pool, now)) markDirty();
        switch (pool) {
            case OVERWORLD:
                if (tier == FluxAnomalyTier.SURFACE) return progress.surfaceUsed < 3;
                if (tier == FluxAnomalyTier.SHALLOW) return progress.shallowUsed < 3;
                return progress.deepUsed < 2;
            case NETHER:
                return progress.netherUsed < 3;
            case END:
                return progress.endUsed < 3;
            default:
                return false;
        }
    }

    public void spend(UUID playerId, Pool pool, FluxAnomalyTier tier, long now) {
        Progress progress = progress(playerId);
        refresh(progress, pool, now);
        switch (pool) {
            case OVERWORLD:
                if (tier == FluxAnomalyTier.SURFACE) progress.surfaceUsed++;
                else if (tier == FluxAnomalyTier.SHALLOW) progress.shallowUsed++;
                else progress.deepUsed++;
                break;
            case NETHER:
                progress.netherUsed++;
                break;
            case END:
                progress.endUsed++;
                break;
            default:
                return;
        }
        markDirty();
    }

    public void recordClosed(UUID playerId, Pool pool, long now) {
        if (playerId == null) return;
        Progress progress = progress(playerId);
        switch (pool) {
            case OVERWORLD:
                progress.overworldClosed++;
                if (progress.overworldClosed >= 5 && progress.overworldUnlockAt == 0L) {
                    progress.overworldUnlockAt = now + CYCLE_COOLDOWN_TICKS;
                }
                break;
            case NETHER:
                progress.netherClosed++;
                if (progress.netherClosed >= 2 && progress.netherUnlockAt == 0L) {
                    progress.netherUnlockAt = now + CYCLE_COOLDOWN_TICKS;
                }
                break;
            case END:
                progress.endClosed++;
                if (progress.endClosed >= 2 && progress.endUnlockAt == 0L) {
                    progress.endUnlockAt = now + CYCLE_COOLDOWN_TICKS;
                }
                break;
            default:
                return;
        }
        markDirty();
    }

    private Progress progress(UUID playerId) {
        Progress progress = players.get(playerId);
        if (progress == null) {
            progress = new Progress();
            players.put(playerId, progress);
            markDirty();
        }
        return progress;
    }

    private static boolean refresh(Progress progress, Pool pool, long now) {
        switch (pool) {
            case OVERWORLD:
                if (progress.overworldClosed >= 5
                        && progress.overworldUnlockAt > 0L
                        && now >= progress.overworldUnlockAt) {
                    progress.surfaceUsed = 0;
                    progress.shallowUsed = 0;
                    progress.deepUsed = 0;
                    progress.overworldClosed = 0;
                    progress.overworldUnlockAt = 0L;
                    return true;
                }
                return false;
            case NETHER:
                if (progress.netherClosed >= 2
                        && progress.netherUnlockAt > 0L
                        && now >= progress.netherUnlockAt) {
                    progress.netherUsed = 0;
                    progress.netherClosed = 0;
                    progress.netherUnlockAt = 0L;
                    return true;
                }
                return false;
            case END:
                if (progress.endClosed >= 2
                        && progress.endUnlockAt > 0L
                        && now >= progress.endUnlockAt) {
                    progress.endUsed = 0;
                    progress.endClosed = 0;
                    progress.endUnlockAt = 0L;
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        players.clear();
        NBTTagList list = nbt.getTagList("players", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            if (!tag.hasUniqueId("player")) continue;
            Progress progress = new Progress();
            progress.read(tag);
            players.put(tag.getUniqueId("player"), progress);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<UUID, Progress> entry : players.entrySet()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setUniqueId("player", entry.getKey());
            entry.getValue().write(tag);
            list.appendTag(tag);
        }
        nbt.setTag("players", list);
        return nbt;
    }

    private static final class Progress {
        int surfaceUsed;
        int shallowUsed;
        int deepUsed;
        int overworldClosed;
        long overworldUnlockAt;
        int netherUsed;
        int netherClosed;
        long netherUnlockAt;
        int endUsed;
        int endClosed;
        long endUnlockAt;

        void read(NBTTagCompound tag) {
            surfaceUsed = tag.getInteger("surfaceUsed");
            shallowUsed = tag.getInteger("shallowUsed");
            deepUsed = tag.getInteger("deepUsed");
            overworldClosed = tag.getInteger("overworldClosed");
            overworldUnlockAt = tag.getLong("overworldUnlockAt");
            netherUsed = tag.getInteger("netherUsed");
            netherClosed = tag.getInteger("netherClosed");
            netherUnlockAt = tag.getLong("netherUnlockAt");
            endUsed = tag.getInteger("endUsed");
            endClosed = tag.getInteger("endClosed");
            endUnlockAt = tag.getLong("endUnlockAt");
        }

        void write(NBTTagCompound tag) {
            tag.setInteger("surfaceUsed", surfaceUsed);
            tag.setInteger("shallowUsed", shallowUsed);
            tag.setInteger("deepUsed", deepUsed);
            tag.setInteger("overworldClosed", overworldClosed);
            tag.setLong("overworldUnlockAt", overworldUnlockAt);
            tag.setInteger("netherUsed", netherUsed);
            tag.setInteger("netherClosed", netherClosed);
            tag.setLong("netherUnlockAt", netherUnlockAt);
            tag.setInteger("endUsed", endUsed);
            tag.setInteger("endClosed", endClosed);
            tag.setLong("endUnlockAt", endUnlockAt);
        }
    }
}
