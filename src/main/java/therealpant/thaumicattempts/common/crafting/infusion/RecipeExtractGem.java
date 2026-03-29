package therealpant.thaumicattempts.common.crafting.infusion;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.crafting.InfusionRecipe;
import thaumcraft.api.items.ItemsTC;
import therealpant.thaumicattempts.golemcraft.ModBlocksItems;
import therealpant.thaumicattempts.api.gems.ITAGemDefinition;
import therealpant.thaumicattempts.api.gems.TAGemRegistry;
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

        ItemStack armorWithGem = getSingleArmorWithGem(input);
        if (armorWithGem.isEmpty()) return false;
        ResourceLocation id = TAGemInlayUtil.getGemId(armorWithGem);
        if (id == null) return false;
        ITAGemDefinition def = TAGemRegistry.get(id);
        if (def == null) return false;
        int tier = TAGemInlayUtil.getTier(armorWithGem);
        return tier >= 1 && tier <= def.getMaxTier();
    }

    @Override
    public ItemStack  getRecipeOutput(EntityPlayer player, ItemStack central, List<ItemStack> comps) {
        ItemStack armorWithGem = getSingleArmorWithGem(comps);
        if (armorWithGem.isEmpty()) return ItemStack.EMPTY;

        ResourceLocation id = TAGemInlayUtil.getGemId(armorWithGem);
        if (id == null) return ItemStack.EMPTY;
        ITAGemDefinition def = TAGemRegistry.get(id);
        if (def == null) return ItemStack.EMPTY;
        int tier = TAGemInlayUtil.getTier(armorWithGem);
        if (tier < 1 || tier > def.getMaxTier()) return ItemStack.EMPTY;
        int dmg = TAGemInlayUtil.getDamage(armorWithGem);
        return ItemTAGem.makeGem(id, tier, dmg);
    }

    private static ItemStack getSingleArmorWithGem(List<ItemStack> input) {
        if (input == null || input.isEmpty()) return ItemStack.EMPTY;
        ItemStack found = ItemStack.EMPTY;
        for (ItemStack stack : input) {
            if (stack.isEmpty() || stack.getItem() != ItemsTC.voidRobeChest || !TAGemInlayUtil.hasGem(stack)) continue;
            if (!found.isEmpty()) return ItemStack.EMPTY;
            found = stack;
            }
        return found;
    }
}