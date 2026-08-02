package therealpant.thaumicattempts.golemnet.data;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import thaumcraft.api.golems.seals.SealPos;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Persistent per-seal golem whitelist.
 *
 * Empty/missing entries intentionally mean unrestricted. This preserves the
 * behaviour of existing seals until the first golem is assigned to them.
 */
public final class SealGolemWhitelistData extends WorldSavedData {

    private static final String DATA_NAME = "thaumicattempts_seal_golem_whitelists";

    private final Map<SealKey, Set<UUID>> whitelists = new HashMap<>();

    public SealGolemWhitelistData() {
        super(DATA_NAME);
    }

    public SealGolemWhitelistData(String name) {
        super(name);
    }

    public static SealGolemWhitelistData get(World world) {
        if (world == null || world.getMapStorage() == null) {
            return new SealGolemWhitelistData();
        }

        MapStorage storage = world.getMapStorage();
        SealGolemWhitelistData data = (SealGolemWhitelistData)
                storage.getOrLoadData(SealGolemWhitelistData.class, DATA_NAME);
        if (data == null) {
            data = new SealGolemWhitelistData();
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    /**
     * Adds the golem when absent and removes it when already present.
     *
     * @return true when the golem was added, false when it was removed.
     */
    public boolean toggle(int dimension, SealPos sealPos, UUID golemId) {
        if (sealPos == null || golemId == null) return false;

        SealKey key = SealKey.of(dimension, sealPos);
        Set<UUID> ids = whitelists.computeIfAbsent(key, ignored -> new HashSet<>());
        boolean added = ids.add(golemId);
        if (!added) {
            ids.remove(golemId);
            if (ids.isEmpty()) whitelists.remove(key);
        }
        markDirty();
        return added;
    }

    public boolean isAllowed(int dimension, @Nullable SealPos sealPos, UUID golemId) {
        if (sealPos == null) return true;
        Set<UUID> ids = whitelists.get(SealKey.of(dimension, sealPos));
        return ids == null || ids.isEmpty() || golemId != null && ids.contains(golemId);
    }

    public int size(int dimension, @Nullable SealPos sealPos) {
        if (sealPos == null) return 0;
        Set<UUID> ids = whitelists.get(SealKey.of(dimension, sealPos));
        return ids == null ? 0 : ids.size();
    }

    public Set<UUID> getWhitelist(int dimension, @Nullable SealPos sealPos) {
        if (sealPos == null) return Collections.emptySet();
        Set<UUID> ids = whitelists.get(SealKey.of(dimension, sealPos));
        return ids == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new HashSet<>(ids));
    }

    public void remove(int dimension, @Nullable SealPos sealPos) {
        if (sealPos != null && whitelists.remove(SealKey.of(dimension, sealPos)) != null) {
            markDirty();
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        whitelists.clear();
        NBTTagList seals = nbt.getTagList("seals", 10);
        for (int i = 0; i < seals.tagCount(); i++) {
            NBTTagCompound sealTag = seals.getCompoundTagAt(i);
            if (!sealTag.hasKey("pos", 4) || !sealTag.hasKey("face", 1)) continue;

            SealKey key = new SealKey(
                    sealTag.getInteger("dim"),
                    sealTag.getLong("pos"),
                    sealTag.getByte("face"));
            Set<UUID> ids = new HashSet<>();
            NBTTagList golems = sealTag.getTagList("golems", 10);
            for (int j = 0; j < golems.tagCount(); j++) {
                NBTTagCompound golemTag = golems.getCompoundTagAt(j);
                if (golemTag.hasUniqueId("id")) ids.add(golemTag.getUniqueId("id"));
            }
            if (!ids.isEmpty()) whitelists.put(key, ids);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList seals = new NBTTagList();
        for (Map.Entry<SealKey, Set<UUID>> entry : whitelists.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty()) continue;

            SealKey key = entry.getKey();
            NBTTagCompound sealTag = new NBTTagCompound();
            sealTag.setInteger("dim", key.dimension);
            sealTag.setLong("pos", key.pos);
            sealTag.setByte("face", key.face);

            NBTTagList golems = new NBTTagList();
            for (UUID id : entry.getValue()) {
                if (id == null) continue;
                NBTTagCompound golemTag = new NBTTagCompound();
                golemTag.setUniqueId("id", id);
                golems.appendTag(golemTag);
            }
            sealTag.setTag("golems", golems);
            seals.appendTag(sealTag);
        }
        nbt.setTag("seals", seals);
        return nbt;
    }

    private static final class SealKey {
        private final int dimension;
        private final long pos;
        private final byte face;

        private SealKey(int dimension, long pos, byte face) {
            this.dimension = dimension;
            this.pos = pos;
            this.face = face;
        }

        private static SealKey of(int dimension, SealPos sealPos) {
            return new SealKey(
                    dimension,
                    sealPos.pos.toLong(),
                    (byte) sealPos.face.ordinal());
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof SealKey)) return false;
            SealKey key = (SealKey) other;
            return dimension == key.dimension && pos == key.pos && face == key.face;
        }

        @Override
        public int hashCode() {
            int result = dimension;
            result = 31 * result + (int) (pos ^ pos >>> 32);
            result = 31 * result + face;
            return result;
        }

        @SuppressWarnings("unused")
        private BlockPos getPos() {
            return BlockPos.fromLong(pos);
        }
    }
}
