package therealpant.thaumicattempts.items;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.api.gems.ITAGemDefinition;
import therealpant.thaumicattempts.api.gems.TAGemNbtKeys;
import therealpant.thaumicattempts.api.gems.TAGemRegistry;
import therealpant.thaumicattempts.common.gems.StubGemDefinition;
import therealpant.thaumicattempts.gems.AmberGemDefinition;
import therealpant.thaumicattempts.gems.AmethystGemDefinition;
import therealpant.thaumicattempts.gems.DiamondGemDefinition;
import therealpant.thaumicattempts.golemcraft.ModBlocksItems;

import javax.annotation.Nullable;

/**
 * Base item for all inlay gems. Gem data is stored in NBT.
 */
public class ItemTAGem extends Item {

    private static final int MAX_TIER = 3;

    public ItemTAGem() {
        setMaxStackSize(1);
        setHasSubtypes(true);
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
        int meta = getMetaFromGem(id, tier);
        if (meta >= 0) {
            stack.setItemDamage(meta);
        }
        return stack;
    }

    /**
     * Check if the stack is a gem item.
     *
     * @param stack item stack
     * @return true if gem
     */
    public static boolean isGem(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof ItemTAGem)) {
            return false;
        }
        int meta = stack.getItemDamage();
        return (getGemIdFromMeta(meta) != null && getTierFromMeta(meta) > 0) || getGemId(stack) != null;
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
    public static int getGemDamage(ItemStack stack) {
        NBTTagCompound gem = getGemTag(stack);
        return gem == null ? 0 : gem.getInteger(TAGemNbtKeys.KEY_DAMAGE);
    }

    public static int getMaxGemDurability(ItemStack stack) {
        ResourceLocation id = getGemIdFromStack(stack);
        int tier = getTierFromStack(stack);
        ITAGemDefinition def = TAGemRegistry.get(id);
        if (def == null || tier < 1) {
            return 0;
        }
        return def.getBaseDurability(tier);
    }

    /**
     * Update gem damage in item NBT.
     *
     * @param stack item stack
     * @param dmg new damage
     */
    public static void setGemDamage(ItemStack stack, int dmg) {
        NBTTagCompound gem = getOrCreateGemTag(stack);
        if (gem == null) return;
        gem.setInteger(TAGemNbtKeys.KEY_DAMAGE, dmg);
    }

    public static ResourceLocation getGemIdFromStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        ResourceLocation id = getGemId(stack);
        if (id != null) {
            return id;
        }
        return getGemIdFromMeta(stack.getItemDamage());
    }

    public static int getTierFromStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        int tier = getTier(stack);
        if (tier > 0) {
            return tier;
        }
        return getTierFromMeta(stack.getItemDamage());
    }

    @Override
    public int getMetadata(int damage) {
        return damage;
    }

    @Override
    public String getTranslationKey(ItemStack stack) {
        ResourceLocation id = getGemIdFromMeta(stack.getItemDamage());
        int tier = getTierFromMeta(stack.getItemDamage());
        if (id == null || tier < 1) {
            id = getGemId(stack);
            tier = getTier(stack);
        }
        if (id == null || tier < 1) {
            return super.getTranslationKey(stack);
        }
        String typeKey = getGemTypeKey(id);
        if (typeKey == null) {
            return super.getTranslationKey(stack);
        }
        return "item." + ThaumicAttempts.MODID + ".gem." + typeKey + ".tier" + tier;
    }

    @Override
    public boolean showDurabilityBar(ItemStack stack) {
        return getMaxGemDurability(stack) > 0;
    }

    @Override
    public double getDurabilityForDisplay(ItemStack stack) {
        int maxDurability = getMaxGemDurability(stack);
        if (maxDurability <= 0) {
            return 0.0D;
        }
        int dmg = getGemDamage(stack);
        return MathHelper.clamp((double) dmg / (double) maxDurability, 0.0D, 1.0D);
    }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (!isInCreativeTab(tab)) return;
        items.add(makeGem(AmberGemDefinition.ID, 1, 0));
        items.add(makeGem(AmberGemDefinition.ID, 2, 0));
        items.add(makeGem(AmberGemDefinition.ID, 3, 0));
        items.add(makeGem(AmethystGemDefinition.ID, 1, 0));
        items.add(makeGem(AmethystGemDefinition.ID, 2, 0));
        items.add(makeGem(AmethystGemDefinition.ID, 3, 0));
        items.add(makeGem(DiamondGemDefinition.ID, 1, 0));
        items.add(makeGem(DiamondGemDefinition.ID, 2, 0));
        items.add(makeGem(DiamondGemDefinition.ID, 3, 0));
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
    private static int getMetaFromGem(ResourceLocation id, int tier) {
        int typeIndex = getTypeIndex(id);
        if (typeIndex < 0 || tier < 1 || tier > MAX_TIER) {
            return -1;
        }
        return (typeIndex * MAX_TIER) + (tier - 1);
    }

    @Nullable
    private static ResourceLocation getGemIdFromMeta(int meta) {
        int typeIndex = meta / MAX_TIER;
        switch (typeIndex) {
            case 0:
                return AmberGemDefinition.ID;
            case 1:
                return AmethystGemDefinition.ID;
            case 2:
                return DiamondGemDefinition.ID;
            default:
                return null;
        }
    }

    private static int getTierFromMeta(int meta) {
        if (meta < 0 || meta >= MAX_TIER * 3) {
            return 0;
        }
        return (meta % MAX_TIER) + 1;
    }

    private static int getTypeIndex(ResourceLocation id) {
        if (AmberGemDefinition.ID.equals(id)) {
            return 0;
        }
        if (AmethystGemDefinition.ID.equals(id)) {
            return 1;
        }
        if (DiamondGemDefinition.ID.equals(id)) {
            return 2;
        }
        return -1;
    }

    @Nullable
    private static String getGemTypeKey(ResourceLocation id) {
        if (AmberGemDefinition.ID.equals(id)) {
            return "amber";
        }
        if (AmethystGemDefinition.ID.equals(id)) {
            return "amethyst";
        }
        if (DiamondGemDefinition.ID.equals(id)) {
            return "diamond";
        }
        return null;
    }
}
