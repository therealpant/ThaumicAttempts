package therealpant.thaumicattempts.util;

import javax.annotation.Nullable;

import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.api.gems.ITAGemDefinition;
import therealpant.thaumicattempts.api.gems.TAGemNbtKeys;
import therealpant.thaumicattempts.api.gems.TAGemRegistry;

/**
 * Utility methods for reading and writing armor inlay NBT.
 */
public final class TAGemInlayUtil {

    private TAGemInlayUtil() {}

    /**
     * Check if armor has an inlaid gem.
     *
     * @param armor armor stack
     * @return true if gem inlay exists
     */
    public static boolean hasGem(ItemStack armor) {
        return getInlayTag(armor) != null;
    }

    /**
     * Set gem inlay data on armor.
     *
     * @param armor armor stack
     * @param id gem id
     * @param tier gem tier
     * @param dmg gem damage
     */
    public static void setGem(ItemStack armor, ResourceLocation id, int tier, int dmg) {
        if (armor == null || armor.isEmpty() || !(armor.getItem() instanceof ItemArmor)) {
            ThaumicAttempts.LOGGER.error("[TA] Inlay set failed: stack is not armor");
            return;
        }
        ITAGemDefinition def = TAGemRegistry.get(id);
        if (id == null || def == null) {
            ThaumicAttempts.LOGGER.error("[TA] Inlay set failed: unknown gem id {}", id);
            return;
        }
        if (tier < 1 || tier > def.getMaxTier()) {
            ThaumicAttempts.LOGGER.error("[TA] Inlay set failed: invalid tier {} for {}", tier, id);
            return;
        }
        NBTTagCompound root = armor.getTagCompound();
        if (root == null) {
            root = new NBTTagCompound();
            armor.setTagCompound(root);
        }
        NBTTagCompound inlay = new NBTTagCompound();
        inlay.setString(TAGemNbtKeys.KEY_ID, id.toString());
        inlay.setInteger(TAGemNbtKeys.KEY_TIER, tier);
        inlay.setInteger(TAGemNbtKeys.KEY_DAMAGE, dmg);
        root.setTag(TAGemNbtKeys.INLAY_TAG, inlay);
    }

    /**
     * Clear gem inlay data from armor.
     *
     * @param armor armor stack
     */
    public static void clearGem(ItemStack armor) {
        if (armor == null || armor.isEmpty()) return;
        NBTTagCompound root = armor.getTagCompound();
        if (root == null || !root.hasKey(TAGemNbtKeys.INLAY_TAG)) return;
        root.removeTag(TAGemNbtKeys.INLAY_TAG);
        if (root.hasNoTags()) {
            armor.setTagCompound(null);
        }
    }

    /**
     * Read gem id from armor inlay.
     *
     * @param armor armor stack
     * @return gem id or null
     */
    @Nullable
    public static ResourceLocation getGemId(ItemStack armor) {
        NBTTagCompound inlay = getInlayTag(armor);
        if (inlay == null || !inlay.hasKey(TAGemNbtKeys.KEY_ID)) return null;
        String raw = inlay.getString(TAGemNbtKeys.KEY_ID);
        if (raw == null || raw.isEmpty()) return null;
        try {
            return new ResourceLocation(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Read gem tier from armor inlay.
     *
     * @param armor armor stack
     * @return tier value
     */
    public static int getTier(ItemStack armor) {
        NBTTagCompound inlay = getInlayTag(armor);
        return inlay == null ? 0 : inlay.getInteger(TAGemNbtKeys.KEY_TIER);
    }

    /**
     * Read gem damage from armor inlay.
     *
     * @param armor armor stack
     * @return damage value
     */
    public static int getDamage(ItemStack armor) {
        NBTTagCompound inlay = getInlayTag(armor);
        return inlay == null ? 0 : inlay.getInteger(TAGemNbtKeys.KEY_DAMAGE);
    }

    /**
     * Update gem damage in armor inlay.
     *
     * @param armor armor stack
     * @param dmg new damage
     */
    public static void setDamage(ItemStack armor, int dmg) {
        NBTTagCompound inlay = getInlayTag(armor);
        if (inlay == null) return;
        inlay.setInteger(TAGemNbtKeys.KEY_DAMAGE, dmg);
    }

    @Nullable
    private static NBTTagCompound getInlayTag(ItemStack armor) {
        if (armor == null || armor.isEmpty()) return null;
        NBTTagCompound root = armor.getTagCompound();
        if (root == null || !root.hasKey(TAGemNbtKeys.INLAY_TAG)) return null;
        return root.getCompoundTag(TAGemNbtKeys.INLAY_TAG);
    }
}
