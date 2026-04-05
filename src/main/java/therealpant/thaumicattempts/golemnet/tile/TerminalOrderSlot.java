package therealpant.thaumicattempts.golemnet.tile;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import therealpant.thaumicattempts.util.ItemKey;

import java.util.UUID;

public class TerminalOrderSlot {
    public final UUID slotId;
    public final ItemKey key;
    public int amount;
    public final long createdTick;

    public TerminalOrderSlot(UUID slotId, ItemKey key, int amount, long createdTick) {
        this.slotId = slotId;
        this.key = key;
        this.amount = Math.max(1, amount);
        this.createdTick = createdTick;
    }

    public NBTTagCompound writeToNbt() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("slotId", slotId.toString());
        tag.setTag("key", key.toStack(1).serializeNBT());
        tag.setInteger("amount", Math.max(1, amount));
        tag.setLong("createdTick", createdTick);
        return tag;
    }

    public static TerminalOrderSlot readFromNbt(NBTTagCompound tag) {
        if (tag == null || !tag.hasKey("slotId") || !tag.hasKey("key")) return null;
        UUID slotId;
        try {
            slotId = UUID.fromString(tag.getString("slotId"));
        } catch (IllegalArgumentException ex) {
            return null;
        }

        ItemStack keyStack = new ItemStack(tag.getCompoundTag("key"));
        if (keyStack.isEmpty()) return null;
        ItemKey key = ItemKey.of(keyStack);
        if (key == null || key == ItemKey.EMPTY) return null;

        int amount = Math.max(1, tag.getInteger("amount"));
        long createdTick = tag.getLong("createdTick");
        return new TerminalOrderSlot(slotId, key, amount, createdTick);
    }
}