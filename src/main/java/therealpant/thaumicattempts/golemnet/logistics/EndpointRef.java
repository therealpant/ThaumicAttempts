package therealpant.thaumicattempts.golemnet.logistics;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

public class EndpointRef {
    public final BlockPos pos;

    public EndpointRef(BlockPos pos) {
        this.pos = pos == null ? BlockPos.ORIGIN : pos.toImmutable();
    }

    public static EndpointRef of(BlockPos pos) {
        return new EndpointRef(pos);
    }

    public NBTTagCompound writeToNbt() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong("pos", pos.toLong());
        return tag;
    }

    public static EndpointRef readFromNbt(NBTTagCompound tag) {
        return new EndpointRef(BlockPos.fromLong(tag.getLong("pos")));
    }
}