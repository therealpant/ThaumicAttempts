package therealpant.thaumicattempts.golemnet.cloud;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.util.math.BlockPos;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class CloudNetworkSnapshot {
    private long builtTick;
    private final Set<BlockPos> requesterPositions = new HashSet<>();

    public long getBuiltTick() {
        return builtTick;
    }

    public void setBuiltTick(long builtTick) {
        this.builtTick = builtTick;
    }

    public Set<BlockPos> getRequesterPositions() {
        return Collections.unmodifiableSet(requesterPositions);
    }

    public void setRequesterPositions(Set<BlockPos> positions) {
        requesterPositions.clear();
        if (positions == null) return;
        for (BlockPos pos : positions) {
            if (pos != null) requesterPositions.add(pos.toImmutable());
        }
    }

    public NBTTagCompound serializeNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setLong("builtTick", builtTick);
        NBTTagList req = new NBTTagList();
        for (BlockPos pos : requesterPositions) {
            req.appendTag(new NBTTagLong(pos.toLong()));
        }
        nbt.setTag("requesters", req);
        return nbt;
    }

    public static CloudNetworkSnapshot deserializeNBT(NBTTagCompound nbt) {
        CloudNetworkSnapshot snapshot = new CloudNetworkSnapshot();
        if (nbt == null) return snapshot;

        snapshot.builtTick = nbt.getLong("builtTick");
        NBTTagList req = nbt.getTagList("requesters", 4);
        for (int i = 0; i < req.tagCount(); i++) {
            snapshot.requesterPositions.add(BlockPos.fromLong(((NBTTagLong) req.get(i)).getLong()).toImmutable());
        }
        return snapshot;
    }
}