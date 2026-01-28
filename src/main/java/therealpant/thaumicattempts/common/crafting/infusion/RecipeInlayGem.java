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
import therealpant.thaumicattempts.items.ItemTAGem;
import therealpant.thaumicattempts.util.TAGemInlayUtil;

/**
 * Infusion recipe for inserting a gem into armor.
 */
public class RecipeInlayGem extends InfusionRecipe {
    private static final ItemStack DUMMY_CENTRAL = new ItemStack(Items.BOOK);

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
        ResourceLocation id = ItemTAGem.getGemId(gemStack);
        if (id == null) return false;
        ITAGemDefinition def = TAGemRegistry.get(id);
        if (def == null) return false;
        int tier = ItemTAGem.getTier(gemStack);
        return tier >= 1 && tier <= def.getMaxTier();
    }

    @Override
    public Object getRecipeOutput(EntityPlayer player, ItemStack central, List<ItemStack> comps) {
        ItemStack out = central.copy();
        out.setCount(1);
        ItemStack gemStack = findGem(comps);
        ResourceLocation id = ItemTAGem.getGemId(gemStack);
        int tier = ItemTAGem.getTier(gemStack);
        int dmg = ItemTAGem.getDamage(gemStack);
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
}