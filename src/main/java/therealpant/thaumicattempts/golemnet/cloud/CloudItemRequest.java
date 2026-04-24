package therealpant.thaumicattempts.golemnet.cloud;

import net.minecraft.nbt.NBTTagCompound;
import therealpant.thaumicattempts.util.ItemKey;

public class CloudItemRequest {
    private final ItemKey itemKey;
    private final int amount;

    public CloudItemRequest(ItemKey itemKey, int amount) {
        this.itemKey = itemKey;
        this.amount = Math.max(0, amount);
    }

    public ItemKey getItemKey() {
        return itemKey;
    }

    public int getAmount() {
        return amount;
    }

    public NBTTagCompound serializeNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        MirrorLogisticsCloud.writeItemKey(nbt, "item", itemKey);
        nbt.setInteger("amount", amount);
        return nbt;
    }

    public static CloudItemRequest deserializeNBT(NBTTagCompound nbt) {
        ItemKey key = MirrorLogisticsCloud.readItemKey(nbt, "item");
        return new CloudItemRequest(key, nbt == null ? 0 : nbt.getInteger("amount"));
    }
}