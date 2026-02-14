package therealpant.thaumicattempts.data.enchantments;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public final class TAInfusionEnchantmentData {

    public static final String ENCH_RIFT_MOMENTUM = "rift_momentum";
    public static final String ENCH_ARCANE_GUARD = "arcane_guard";

    private static final String ROOT = "ta_infusion_enchants";

    private TAInfusionEnchantmentData() {}

    public static int getLevel(ItemStack stack, String key) {
        if (stack == null || stack.isEmpty() || key == null || key.isEmpty()) return 0;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(ROOT, 10)) return 0;
        return Math.max(0, tag.getCompoundTag(ROOT).getInteger(key));
    }

    public static void setLevel(ItemStack stack, String key, int level) {
        if (stack == null || stack.isEmpty() || key == null || key.isEmpty()) return;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        NBTTagCompound ench = tag.hasKey(ROOT, 10) ? tag.getCompoundTag(ROOT) : new NBTTagCompound();
        ench.setInteger(key, Math.max(0, level));
        tag.setTag(ROOT, ench);
    }
}
