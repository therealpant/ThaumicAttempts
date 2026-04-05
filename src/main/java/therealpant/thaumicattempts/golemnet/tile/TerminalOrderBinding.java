package therealpant.thaumicattempts.golemnet.tile;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;
import therealpant.thaumicattempts.util.ItemKey;

import java.util.UUID;

public class TerminalOrderBinding {
    public final UUID slotId;
    public final UUID batchId;
    public final UUID rootOrderId;
    public final ItemKey key;
    public final int amount;
    public boolean completed;
    public boolean failed;

    public TerminalOrderBinding(UUID slotId, UUID batchId, UUID rootOrderId, ItemKey key, int amount) {
        this.slotId = slotId;
        this.batchId = batchId;
        this.rootOrderId = rootOrderId;
        this.key = key;
        this.amount = Math.max(1, amount);
    }

    public NBTTagCompound writeToNbt() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("slotId", slotId.toString());
        tag.setString("batchId", batchId.toString());
        tag.setString("rootOrderId", rootOrderId.toString());
        tag.setTag("key", key.toStack(1).serializeNBT());
        tag.setInteger("amount", amount);
        tag.setBoolean("completed", completed);
        tag.setBoolean("failed", failed);
        return tag;
    }

    public static TerminalOrderBinding readFromNbt(NBTTagCompound tag) {
        if (tag == null
                || !tag.hasKey("slotId", Constants.NBT.TAG_STRING)
                || !tag.hasKey("batchId", Constants.NBT.TAG_STRING)
                || !tag.hasKey("rootOrderId", Constants.NBT.TAG_STRING)
                || !tag.hasKey("key")) {
            return null;
        }

        UUID slotId;
        UUID batchId;
        UUID rootOrderId;
        try {
            slotId = UUID.fromString(tag.getString("slotId"));
            batchId = UUID.fromString(tag.getString("batchId"));
            rootOrderId = UUID.fromString(tag.getString("rootOrderId"));
        } catch (IllegalArgumentException ex) {
            return null;
        }

        ItemStack keyStack = new ItemStack(tag.getCompoundTag("key"));
        if (keyStack.isEmpty()) return null;
        ItemKey key = ItemKey.of(keyStack);
        if (key == null || key == ItemKey.EMPTY) return null;

        TerminalOrderBinding binding = new TerminalOrderBinding(
                slotId, batchId, rootOrderId, key, Math.max(1, tag.getInteger("amount"))
        );
        binding.completed = tag.getBoolean("completed");
        binding.failed = tag.getBoolean("failed");
        return binding;
    }
}