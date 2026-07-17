package therealpant.thaumicattempts.items;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import therealpant.thaumicattempts.ThaumicAttempts;

import javax.annotation.Nullable;
import java.util.List;

public class ItemCalibratedExtract extends Item {
    private static final String TAG_TARGET = "CreationTarget";
    private static final String TAG_SIGNATURE_COST = "SignatureCost";

    public ItemCalibratedExtract() {
        setRegistryName(ThaumicAttempts.MODID, "calibrated_extract");
        setTranslationKey(ThaumicAttempts.MODID + ".calibrated_extract");
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
        setMaxStackSize(1);
    }

    public static ItemStack withTarget(Item item, ItemStack target) {
        return withTarget(item, target, 400);
    }

    public static ItemStack withTarget(Item item, ItemStack target, int signatureCost) {
        ItemStack extract = new ItemStack(item);
        if (target == null || target.isEmpty()) return extract;
        ItemStack stored = target.copy();
        stored.setCount(1);
        if (stored.isItemStackDamageable()) stored.setItemDamage(0);
        NBTTagCompound root = new NBTTagCompound();
        root.setTag(TAG_TARGET, stored.writeToNBT(new NBTTagCompound()));
        root.setInteger(TAG_SIGNATURE_COST, Math.max(1, signatureCost));
        extract.setTagCompound(root);
        return extract;
    }

    public static int getSignatureCost(ItemStack extract) {
        if (extract == null || extract.isEmpty() || !extract.hasTagCompound()
                || !extract.getTagCompound().hasKey(TAG_SIGNATURE_COST)) return 400;
        return Math.max(1, extract.getTagCompound().getInteger(TAG_SIGNATURE_COST));
    }

    public static ItemStack getTarget(ItemStack extract) {
        if (extract == null || extract.isEmpty() || !extract.hasTagCompound()
                || !extract.getTagCompound().hasKey(TAG_TARGET)) return ItemStack.EMPTY;
        return new ItemStack(extract.getTagCompound().getCompoundTag(TAG_TARGET));
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        ItemStack target = getTarget(stack);
        if (!target.isEmpty()) {
            tooltip.add(TextFormatting.DARK_AQUA + target.getDisplayName());
        }
    }
}
