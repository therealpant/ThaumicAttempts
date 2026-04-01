package therealpant.thaumicattempts.golemnet.logistics;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

public class EndpointRef {
    public enum AccessMode {
        DIRECT,
        INPUT,
        OUTPUT,
        BUFFER
    }

    public final BlockPos pos;
    public final AccessMode mode;

    public EndpointRef(BlockPos pos) {
        this(pos, AccessMode.DIRECT);
    }

    public EndpointRef(BlockPos pos, AccessMode mode) {
        this.pos = pos == null ? BlockPos.ORIGIN : pos.toImmutable();
        this.mode = mode == null ? AccessMode.DIRECT : mode;
    }

    public static EndpointRef of(BlockPos pos) {
        return new EndpointRef(pos);
    }

    public static EndpointRef of(BlockPos pos, AccessMode mode) {
        return new EndpointRef(pos, mode);
    }

    public NBTTagCompound writeToNbt() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong("pos", pos.toLong());
        tag.setString("mode", mode.name());
        return tag;
    }

    public static EndpointRef readFromNbt(NBTTagCompound tag) {
        AccessMode mode = AccessMode.DIRECT;
        if (tag.hasKey("mode")) {
            try {
                mode = AccessMode.valueOf(tag.getString("mode"));
            } catch (Exception ignored) {
                mode = AccessMode.DIRECT;
            }
        }
        return new EndpointRef(BlockPos.fromLong(tag.getLong("pos")), mode);
    }
}