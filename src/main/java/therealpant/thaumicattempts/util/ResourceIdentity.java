package therealpant.thaumicattempts.util;

import net.minecraft.item.ItemStack;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.items.ItemTCEssentiaContainer;

import javax.annotation.Nullable;

/**
 * Unified resource identity helper used across TA logistics.
 * Identity = item + metadata (when relevant) + significant NBT.
 */
public final class ResourceIdentity {

    private ResourceIdentity() {}

    public static ItemStack normalizeForKey(@Nullable ItemStack source) {
        if (source == null || source.isEmpty()) return ItemStack.EMPTY;
        if (isCrystal(source)) {
            Aspect aspect = aspectOf(source);
            return aspect == null ? ItemStack.EMPTY : ThaumcraftApiHelper.makeCrystal(aspect, 1);
        }
        ItemStack copy = source.copy();
        copy.setCount(1);
        return copy;
    }

    public static ItemKey keyOf(@Nullable ItemStack source) {
        ItemStack normalized = normalizeForKey(source);
        return normalized.isEmpty() ? ItemKey.EMPTY : ItemKey.of(normalized);
    }

    public static ItemStack normalizeForProvisionIngredient(@Nullable ItemStack source) {
        if (source == null || source.isEmpty()) return ItemStack.EMPTY;
        if (isCrystal(source)) {
            Aspect aspect = aspectOf(source);
            return aspect == null ? ItemStack.EMPTY : ThaumcraftApiHelper.makeCrystal(aspect, 1);
        }
        if (source.getItem().isDamageable()) {
            int meta = source.getHasSubtypes() ? source.getMetadata() : 0;
            return new ItemStack(source.getItem(), 1, meta);
        }
        return normalizeForKey(source);
    }

    public static ItemKey keyOfProvisionIngredient(@Nullable ItemStack source) {
        ItemStack normalized = normalizeForProvisionIngredient(source);
        return normalized.isEmpty() ? ItemKey.EMPTY : ItemKey.of(normalized);
    }

    public static boolean sameResource(@Nullable ItemStack a, @Nullable ItemStack b) {
        ItemKey ka = keyOf(a);
        if (ka == ItemKey.EMPTY) return false;
        return ka.equals(keyOf(b));
    }

    public static boolean sameResource(@Nullable ItemStack stack, @Nullable ItemKey key) {
        if (key == null || key == ItemKey.EMPTY) return false;
        return key.equals(keyOf(stack));
    }

    public static boolean sameProvisionResource(@Nullable ItemStack a, @Nullable ItemStack b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;
        if (isCrystal(a) || isCrystal(b)) {
            Aspect aa = aspectOf(a);
            Aspect ba = aspectOf(b);
            return aa != null && ba != null && aa == ba;
        }
        if (a.getItem() != b.getItem()) return false;
        if (a.getHasSubtypes() || b.getHasSubtypes()) {
            if (a.getMetadata() != b.getMetadata()) return false;
        }
        if (a.getItem().isDamageable() || b.getItem().isDamageable()) return true;
        return sameResource(a, b);
    }

    public static ItemStack stackForRequest(@Nullable ItemStack like, int amount) {
        if (like == null || like.isEmpty()) return ItemStack.EMPTY;
        ItemStack copy = normalizeForKey(like);
        if (copy.isEmpty()) return ItemStack.EMPTY;
        copy.setCount(Math.max(1, amount));
        return copy;
    }

    public static boolean isCrystal(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() == ItemsTC.crystalEssence;
    }

    @Nullable
    public static Aspect aspectOf(@Nullable ItemStack stack) {
        if (!isCrystal(stack)) return null;
        AspectList al = ((ItemTCEssentiaContainer) ItemsTC.crystalEssence).getAspects(stack);
        return (al != null && al.size() == 1) ? al.getAspects()[0] : null;
    }
}
