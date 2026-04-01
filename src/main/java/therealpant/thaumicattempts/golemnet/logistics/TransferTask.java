package therealpant.thaumicattempts.golemnet.logistics;

import net.minecraft.nbt.NBTTagCompound;
import therealpant.thaumicattempts.util.ItemKey;

public class TransferTask extends RuntimeTask {
    public ItemKey itemKey;
    public EndpointRef source;
    public EndpointRef target;

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
        return tag;
    }

    @Override
    protected void readFromNbtImpl(NBTTagCompound tag) {
        readCommon(tag);
        itemKey = ItemKey.of(new net.minecraft.item.ItemStack(tag.getCompoundTag("key")));
        source = EndpointRef.readFromNbt(tag.getCompoundTag("source"));
        target = EndpointRef.readFromNbt(tag.getCompoundTag("target"));
    }
}