package therealpant.thaumicattempts.golemnet.cloud;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

public class CloudEndpointRef {
    private final BlockPos pos;
    private final int side;

    public CloudEndpointRef(BlockPos pos, int side) {
        this.pos = pos == null ? BlockPos.ORIGIN : pos.toImmutable();
        this.side = side;
    }

    public BlockPos getPos() {
        return pos;
    }

    public int getSide() {
        return side;
    }

    public NBTTagCompound serializeNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setLong("pos", pos.toLong());
        nbt.setInteger("side", side);
        return nbt;
    }

    public static CloudEndpointRef deserializeNBT(NBTTagCompound nbt) {
        if (nbt == null || !nbt.hasKey("pos")) return null;
        return new CloudEndpointRef(BlockPos.fromLong(nbt.getLong("pos")), nbt.getInteger("side"));
    }
}