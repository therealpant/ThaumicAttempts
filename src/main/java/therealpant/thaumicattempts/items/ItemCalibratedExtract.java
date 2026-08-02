package therealpant.thaumicattempts.items;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import therealpant.thaumicattempts.ThaumicAttempts;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Random;

public class ItemCalibratedExtract extends Item {
    private static final String TAG_TARGET = "CreationTarget";
    private static final String TAG_SIGNATURE_COST = "SignatureCost";
    private static final String TAG_FATIGUE = "Fatigue";
    private static final String TAG_ABSORBED_FUEL = "FatigueFuel";
    public static final int MAX_FATIGUE = 6;
    public static final long FUEL_PER_FATIGUE_ROLL = 800L;
    public static final float FATIGUE_CHANCE = 0.08F;

    public ItemCalibratedExtract() {
        setRegistryName(ThaumicAttempts.MODID, "calibrated_extract");
        setTranslationKey(ThaumicAttempts.MODID + ".calibrated_extract");
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
        setMaxStackSize(1);
        setHasSubtypes(true);
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
        setFatigue(extract, 0);
        return extract;
    }

    public static int getFatigue(ItemStack extract) {
        if (extract == null || extract.isEmpty() || extract.getItem() == null) return 0;
        if (extract.hasTagCompound() && extract.getTagCompound().hasKey(TAG_FATIGUE)) {
            return Math.max(0, Math.min(MAX_FATIGUE, extract.getTagCompound().getInteger(TAG_FATIGUE)));
        }
        return Math.max(0, Math.min(MAX_FATIGUE, extract.getMetadata()));
    }

    public static void setFatigue(ItemStack extract, int fatigue) {
        if (extract == null || extract.isEmpty()) return;
        int value = Math.max(0, Math.min(MAX_FATIGUE, fatigue));
        NBTTagCompound tag = extract.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            extract.setTagCompound(tag);
        }
        tag.setInteger(TAG_FATIGUE, value);
        extract.setItemDamage(value);
    }

    public static void restoreOneFatigue(ItemStack extract) {
        int restored = Math.max(0, getFatigue(extract) - 1);
        setFatigue(extract, restored);
        if (restored <= 0 && extract.hasTagCompound()) extract.getTagCompound().setLong(TAG_ABSORBED_FUEL, 0L);
    }

    public static boolean isExhausted(ItemStack extract) {
        return getFatigue(extract) >= MAX_FATIGUE;
    }

    public static long getAbsorbedFuel(ItemStack extract) {
        if (extract == null || extract.isEmpty() || !extract.hasTagCompound()) return 0L;
        return Math.max(0L, extract.getTagCompound().getLong(TAG_ABSORBED_FUEL));
    }

    /** Records actually consumed fuel and rolls once for every complete 800-unit interval. */
    public static boolean recordAbsorbedFuel(ItemStack extract, long amount, Random random) {
        if (extract == null || extract.isEmpty() || amount <= 0L || random == null || isExhausted(extract)) return false;
        long accumulated = getAbsorbedFuel(extract) + amount;
        int fatigue = getFatigue(extract);
        boolean changed = false;
        while (accumulated >= FUEL_PER_FATIGUE_ROLL && fatigue < MAX_FATIGUE) {
            accumulated -= FUEL_PER_FATIGUE_ROLL;
            if (random.nextFloat() < FATIGUE_CHANCE) {
                fatigue++;
                changed = true;
            }
        }
        NBTTagCompound tag = extract.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            extract.setTagCompound(tag);
        }
        tag.setLong(TAG_ABSORBED_FUEL, fatigue >= MAX_FATIGUE ? 0L : accumulated);
        setFatigue(extract, fatigue);
        return changed;
    }

    public static int getFuelPercent(ItemStack extract) {
        return 100 + getFatigue(extract) * 4;
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
        int fatigue = getFatigue(stack);
        tooltip.add((fatigue >= MAX_FATIGUE ? TextFormatting.RED : TextFormatting.GRAY)
                + I18n.format("item.thaumicattempts.calibrated_extract.fatigue", fatigue, MAX_FATIGUE));
        tooltip.add(TextFormatting.DARK_GRAY
                + I18n.format("item.thaumicattempts.calibrated_extract.fuel", getFuelPercent(stack)));
    }
}
