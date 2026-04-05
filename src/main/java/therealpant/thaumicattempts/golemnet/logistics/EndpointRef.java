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
    public final int stagingSlotIndex;

    public EndpointRef(BlockPos pos) {
        this(pos, AccessMode.DIRECT, -1);
    }

    public EndpointRef(BlockPos pos, AccessMode mode) {
        this(pos, mode, -1);
    }

    public EndpointRef(BlockPos pos, AccessMode mode, int stagingSlotIndex) {
        this.pos = pos == null ? BlockPos.ORIGIN : pos.toImmutable();
        this.mode = mode == null ? AccessMode.DIRECT : mode;
        this.stagingSlotIndex = stagingSlotIndex;
    }

    public static EndpointRef managerBuffer(BlockPos pos) {
        return new EndpointRef(pos, AccessMode.BUFFER);
    }

    public static EndpointRef managerBufferSlot(BlockPos pos, int slotIndex) {
        return new EndpointRef(pos, AccessMode.BUFFER, slotIndex);
    }

    public static EndpointRef managerInput(BlockPos pos) {
        return new EndpointRef(pos, AccessMode.INPUT);
    }

    public static EndpointRef managerOutput(BlockPos pos) {
        return new EndpointRef(pos, AccessMode.OUTPUT);
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
        tag.setInteger("stagingSlotIndex", stagingSlotIndex);
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
        int stagingSlotIndex = tag.hasKey("stagingSlotIndex") ? tag.getInteger("stagingSlotIndex") : -1;
        return new EndpointRef(BlockPos.fromLong(tag.getLong("pos")), mode, stagingSlotIndex);
    }
}