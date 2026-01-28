package therealpant.thaumicattempts.items;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.api.gems.ITAGemDefinition;
import therealpant.thaumicattempts.api.gems.TAGemNbtKeys;
import therealpant.thaumicattempts.api.gems.TAGemRegistry;
import therealpant.thaumicattempts.common.gems.StubGemDefinition;
import therealpant.thaumicattempts.golemcraft.ModBlocksItems;

import javax.annotation.Nullable;

/**
 * Base item for all inlay gems. Gem data is stored in NBT.
 */
public class ItemTAGem extends Item {

    public ItemTAGem() {
        setMaxStackSize(1);
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
        setTranslationKey(ThaumicAttempts.MODID + ".ta_gem");
    }

    /**
     * Create a gem ItemStack with NBT payload.
     *
     * @param id  gem id
     * @param tier gem tier
     * @param dmg gem damage
     * @return gem item stack
     */
    public static ItemStack makeGem(ResourceLocation id, int tier, int dmg) {
        ItemStack stack = new ItemStack(ModBlocksItems.TA_GEM);
        if (id == null) {
            return stack;
        }
        NBTTagCompound root = new NBTTagCompound();
        NBTTagCompound gem = new NBTTagCompound();
        gem.setString(TAGemNbtKeys.KEY_ID, id.toString());
        gem.setInteger(TAGemNbtKeys.KEY_TIER, tier);
        gem.setInteger(TAGemNbtKeys.KEY_DAMAGE, dmg);
        root.setTag(TAGemNbtKeys.GEM_TAG, gem);
        stack.setTagCompound(root);
        return stack;
    }

    /**
     * Check if the stack is a gem item.
     *
     * @param stack item stack
     * @return true if gem
     */
    public static boolean isGem(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && stack.getItem() instanceof ItemTAGem
                && getGemId(stack) != null;
    }

    /**
     * Read gem id from item NBT.
     *
     * @param stack item stack
     * @return gem id or null
     */
    @Nullable
    public static ResourceLocation getGemId(ItemStack stack) {
        NBTTagCompound gem = getGemTag(stack);
        if (gem == null || !gem.hasKey(TAGemNbtKeys.KEY_ID)) return null;
        String raw = gem.getString(TAGemNbtKeys.KEY_ID);
        if (raw == null || raw.isEmpty()) return null;
        try {
            return new ResourceLocation(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Read gem tier from item NBT.
     *
     * @param stack item stack
     * @return tier value
     */
    public static int getTier(ItemStack stack) {
        NBTTagCompound gem = getGemTag(stack);
        return gem == null ? 0 : gem.getInteger(TAGemNbtKeys.KEY_TIER);
    }

    /**
     * Read gem damage from item NBT.
     *
     * @param stack item stack
     * @return damage value
     */
    public static int getDamage(ItemStack stack) {
        NBTTagCompound gem = getGemTag(stack);
        return gem == null ? 0 : gem.getInteger(TAGemNbtKeys.KEY_DAMAGE);
    }

    /**
     * Update gem damage in item NBT.
     *
     * @param stack item stack
     * @param dmg new damage
     */
    public static void setDamage(ItemStack stack, int dmg) {
        NBTTagCompound gem = getOrCreateGemTag(stack);
        if (gem == null) return;
        gem.setInteger(TAGemNbtKeys.KEY_DAMAGE, dmg);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        String base = super.getItemStackDisplayName(stack);
        int tier = getTier(stack);
        return tier > 0 ? base + " (Tier " + tier + ")" : base;
    }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (!isInCreativeTab(tab)) return;
        for (ITAGemDefinition def : TAGemRegistry.all()) {
            ResourceLocation id = def.getId();
            for (int tier = 1; tier <= def.getMaxTier(); tier++) {
                items.add(makeGem(id, tier, 0));
            }
        }
        if (TAGemRegistry.all().isEmpty()) {
            items.add(makeGem(StubGemDefinition.ID, 1, 0));
        }
    }

    @Nullable
    private static NBTTagCompound getGemTag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        NBTTagCompound root = stack.getTagCompound();
        if (root == null || !root.hasKey(TAGemNbtKeys.GEM_TAG)) return null;
        return root.getCompoundTag(TAGemNbtKeys.GEM_TAG);
    }

    @Nullable
    private static NBTTagCompound getOrCreateGemTag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        NBTTagCompound root = stack.getTagCompound();
        if (root == null) {
            root = new NBTTagCompound();
            stack.setTagCompound(root);
        }
        if (!root.hasKey(TAGemNbtKeys.GEM_TAG)) {
            root.setTag(TAGemNbtKeys.GEM_TAG, new NBTTagCompound());
        }
        return root.getCompoundTag(TAGemNbtKeys.GEM_TAG);
    }
}
