package therealpant.thaumicattempts.common.crafting.infusion;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.crafting.InfusionRecipe;
import therealpant.thaumicattempts.api.gems.ITAGemDefinition;
import therealpant.thaumicattempts.api.gems.TAGemRegistry;
import therealpant.thaumicattempts.gems.AmberGemDefinition;
import therealpant.thaumicattempts.gems.AmethystGemDefinition;
import therealpant.thaumicattempts.gems.DiamondGemDefinition;
import therealpant.thaumicattempts.items.ItemTAGem;
import therealpant.thaumicattempts.util.TAGemInlayUtil;

/**
 * Infusion recipe for inserting a gem into armor.
 */
public class RecipeInlayGem extends InfusionRecipe {
    private static final ItemStack DUMMY_CENTRAL = new ItemStack(Items.BOOK);
    private static final int MAX_TIER = 3;

    public RecipeInlayGem(String research, int instability, AspectList aspects, Object... components) {
        super(research, new ItemStack(Items.IRON_CHESTPLATE), instability, aspects,
                Ingredient.fromStacks(DUMMY_CENTRAL), components);
    }

    @Override
    public boolean matches(List<ItemStack> input, ItemStack central, World world, EntityPlayer player) {
        if (central == null || central.isEmpty()) return false;
        if (!(central.getItem() instanceof ItemArmor)) return false;
        if (TAGemInlayUtil.hasGem(central)) return false;
        if (!super.matches(input, DUMMY_CENTRAL, world, player)) return false;
        ItemStack gemStack = findGem(input);
        if (gemStack.isEmpty()) return false;
        ResourceLocation id = getGemIdFromStack(gemStack);
        if (id == null) return false;
        ITAGemDefinition def = TAGemRegistry.get(id);
        if (def == null) return false;
        int tier = getTierFromStack(gemStack);
        return tier >= 1 && tier <= def.getMaxTier();
    }

    @Override
    public ItemStack  getRecipeOutput(EntityPlayer player, ItemStack central, List<ItemStack> comps) {
        ItemStack out = central.copy();
        out.setCount(1);
        ItemStack gemStack = findGem(comps);
        ResourceLocation id = getGemIdFromStack(gemStack);
        int tier = getTierFromStack(gemStack);
        int dmg = ItemTAGem.getGemDamage(gemStack);
        if (id != null) {
            TAGemInlayUtil.setGem(out, id, tier, dmg);
        }
        return out;
    }

    private static ItemStack findGem(List<ItemStack> stacks) {
        if (stacks == null) return ItemStack.EMPTY;
        for (ItemStack stack : stacks) {
            if (ItemTAGem.isGem(stack)) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static ResourceLocation getGemIdFromStack(ItemStack stack) {
        ResourceLocation id = getGemIdFromMeta(stack.getMetadata());
        if (id == null) {
            id = ItemTAGem.getGemId(stack);
        }
        return id;
    }

    private static int getTierFromStack(ItemStack stack) {
        int tier = getTierFromMeta(stack.getMetadata());
        if (tier < 1) {
            tier = ItemTAGem.getTier(stack);
        }
        return tier;
    }

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
}