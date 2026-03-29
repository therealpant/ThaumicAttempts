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
import thaumcraft.api.items.ItemsTC;
import therealpant.thaumicattempts.api.gems.ITAGemDefinition;
import therealpant.thaumicattempts.api.gems.TAGemRegistry;
import therealpant.thaumicattempts.items.ItemTAGem;
import therealpant.thaumicattempts.util.TAGemInlayUtil;

/**
 * Infusion recipe for inserting a gem into armor.
 */
public class RecipeInlayGem extends InfusionRecipe {
    private static final ItemStack VOID_ROBE_CHEST = new ItemStack(ItemsTC.voidRobeChest);

    public RecipeInlayGem(String research, int instability, AspectList aspects, Object... components) {
        super(research, new ItemStack(ItemsTC.voidRobeChest), instability, aspects,
                Ingredient.fromStacks(VOID_ROBE_CHEST), components);
    }

    @Override
    public boolean matches(List<ItemStack> input, ItemStack central, World world, EntityPlayer player) {
        if (central.getItem() != ItemsTC.voidRobeChest) return false;
        if (!(central.getItem() instanceof ItemArmor)) return false;
        if (TAGemInlayUtil.hasGem(central)) return false;
        if (!super.matches(input, central, world, player)) return false;
        ItemStack gemStack = findGem(input);
        if (gemStack.isEmpty()) return false;
        ResourceLocation id = ItemTAGem.getGemIdFromStack(gemStack);
        if (id == null) return false;
        ITAGemDefinition def = TAGemRegistry.get(id);
        if (def == null) return false;
        int tier = ItemTAGem.getTierFromStack(gemStack);
        return tier >= 1 && tier <= def.getMaxTier();
    }

    @Override
    public ItemStack  getRecipeOutput(EntityPlayer player, ItemStack central, List<ItemStack> comps) {
        ItemStack out = central.copy();
        out.setCount(1);
        ItemStack gemStack = findGem(comps);
        ResourceLocation id = ItemTAGem.getGemIdFromStack(gemStack);
        int tier = ItemTAGem.getTierFromStack(gemStack);
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

}