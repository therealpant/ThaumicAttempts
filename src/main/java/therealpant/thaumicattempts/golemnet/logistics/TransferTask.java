package therealpant.thaumicattempts.golemnet.logistics;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;
import therealpant.thaumicattempts.util.ItemKey;

import java.util.UUID;

public class TransferTask extends RuntimeTask {
    public ItemKey itemKey;
    public EndpointRef source;
    public EndpointRef target;
    public int legacyDeliveryQueueId = -1;
    public boolean legacyDeliveryQueued = false;
    public boolean inboundQueued = false;
    public boolean inboundDone = false;
    public boolean outboundDone = false;
    public UUID legacyDeliveryId = null;

    @Override
    public String getTaskType() {
        return "TRANSFER";
    }

    @Override
    public NBTTagCompound writeToNbt() {
        NBTTagCompound tag = new NBTTagCompound();
        writeCommon(tag);
        tag.setTag("key", itemKey.toStack(1).writeToNBT(new NBTTagCompound()));
        tag.setTag("source", source.writeToNbt());
        tag.setTag("target", target.writeToNbt());
        tag.setInteger("legacyQueueId", legacyDeliveryQueueId);
        tag.setBoolean("legacyQueued", legacyDeliveryQueued);
        tag.setBoolean("InboundQueued", inboundQueued);
        tag.setBoolean("InboundDone", inboundDone);
        tag.setBoolean("OutboundDone", outboundDone);
        if (legacyDeliveryId != null) {
            tag.setString("LegacyDeliveryId", legacyDeliveryId.toString());
        }
        return tag;
    }

    @Override
    protected void readFromNbtImpl(NBTTagCompound tag) {
        readCommon(tag);
        itemKey = ItemKey.of(new net.minecraft.item.ItemStack(tag.getCompoundTag("key")));
        source = EndpointRef.readFromNbt(tag.getCompoundTag("source"));
        target = EndpointRef.readFromNbt(tag.getCompoundTag("target"));
        legacyDeliveryQueueId = tag.hasKey("legacyQueueId") ? tag.getInteger("legacyQueueId") : -1;
        legacyDeliveryQueued = tag.hasKey("legacyQueued") && tag.getBoolean("legacyQueued");
        inboundQueued = tag.getBoolean("InboundQueued");
        inboundDone = tag.getBoolean("InboundDone");
        outboundDone = tag.getBoolean("OutboundDone");
        if (tag.hasKey("LegacyDeliveryId", Constants.NBT.TAG_STRING)) {
            try {
                legacyDeliveryId = UUID.fromString(tag.getString("LegacyDeliveryId"));
            } catch (Exception ignored) {
                legacyDeliveryId = null;
            }
        }
    }
}