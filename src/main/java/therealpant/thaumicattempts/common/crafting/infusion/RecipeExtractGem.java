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
import therealpant.thaumicattempts.golemcraft.ModBlocksItems;
import therealpant.thaumicattempts.items.ItemTAGem;
import therealpant.thaumicattempts.util.TAGemInlayUtil;

/**
 * Infusion recipe for extracting a gem from armor.
 */
public class RecipeExtractGem extends InfusionRecipe {
    private static final ItemStack VOID_INGOT = new ItemStack(ItemsTC.ingots, 1, 1);

    public RecipeExtractGem(String research, int instability, AspectList aspects, Object... components) {
        super(research, new ItemStack(ModBlocksItems.TA_GEM, 1, 0), instability, aspects,
                Ingredient.fromStacks(VOID_INGOT), components);
    }

    @Override
    public boolean matches(List<ItemStack> input, ItemStack central, World world, EntityPlayer player) {
        if (central == null || central.isEmpty()) return false;
        if (!ItemStack.areItemsEqual(VOID_INGOT, central)) return false;
        if (!super.matches(input, central, world, player)) return false;

        ItemStack armorWithGem = findArmorWithGem(input);
        if (armorWithGem.isEmpty()) return false;
        return TAGemInlayUtil.getGemId(armorWithGem) != null;
    }

    @Override
    public ItemStack  getRecipeOutput(EntityPlayer player, ItemStack central, List<ItemStack> comps) {
        ItemStack armorWithGem = findArmorWithGem(comps);
        if (armorWithGem.isEmpty()) return ItemStack.EMPTY;

        ResourceLocation id = TAGemInlayUtil.getGemId(armorWithGem);
        if (id == null) return ItemStack.EMPTY;
        int tier = TAGemInlayUtil.getTier(armorWithGem);
        int dmg = TAGemInlayUtil.getDamage(armorWithGem);
        return ItemTAGem.makeGem(id, tier, dmg);
    }

    private static ItemStack findArmorWithGem(List<ItemStack> input) {
        if (input == null || input.isEmpty()) return ItemStack.EMPTY;
        for (ItemStack stack : input) {
            if (!stack.isEmpty() && stack.getItem() == ItemsTC.voidRobeChest && TAGemInlayUtil.hasGem(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}