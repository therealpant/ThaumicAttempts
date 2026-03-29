package therealpant.thaumicattempts.common.crafting.infusion;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
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
    private static final ItemStack SALIS_MUNDUS = new ItemStack(ItemsTC.salisMundus);
    private static final ItemStack VIS_RESONATOR = new ItemStack(ItemsTC.visResonator);
    private static final ItemStack MORPHIC_RESONATOR = new ItemStack(ItemsTC.morphicResonator);
    private static final int PREVIEW_GEM_META = 0;

    public RecipeExtractGem(String research, int instability, AspectList aspects, Object... components) {
        super(
                research,
                new ItemStack(ItemsTC.voidRobeChest),
                instability,
                aspects,
                Ingredient.fromStacks(makeInlaidRobePreview()),
                components
        );
    }

    @Override
    public boolean matches(List<ItemStack> input, ItemStack central, World world, EntityPlayer player) {
        if (!hasGemInlay(central)) return false;
        if (input == null || input.isEmpty()) return false;

        int needSalis = 2;
        int needVisResonator = 2;
        int needMorphicResonator = 2;

        for (ItemStack stack : input) {
            if (stack == null || stack.isEmpty()) continue;

            if (ItemStack.areItemsEqual(stack, SALIS_MUNDUS)) {
                needSalis--;
                continue;
            }
            if (ItemStack.areItemsEqual(stack, VIS_RESONATOR)) {
                needVisResonator--;
                continue;
            }
            if (ItemStack.areItemsEqual(stack, MORPHIC_RESONATOR)) {
                needMorphicResonator--;
                continue;
            }

            return false;
        }

        if (needSalis != 0 || needVisResonator != 0 || needMorphicResonator != 0) {
            return false;
        }

        ResourceLocation id = TAGemInlayUtil.getGemId(central);
        if (id == null) return false;
        ITAGemDefinition def = TAGemRegistry.get(id);
        if (def == null) return false;
        int tier = TAGemInlayUtil.getTier(central);
        return tier >= 1 && tier <= def.getMaxTier();
    }

    @Override
    public ItemStack  getRecipeOutput(EntityPlayer player, ItemStack central, List<ItemStack> comps) {
        if (!hasGemInlay(central)) return ItemStack.EMPTY;
        ItemStack cleaned = central.copy();
        cleaned.setCount(1);
        TAGemInlayUtil.removeGem(cleaned);
        return cleaned;
    }

    private static boolean hasGemInlay(ItemStack stack) {
        return stack != null && !stack.isEmpty() && TAGemInlayUtil.hasGem(stack);
    }

    private static ItemStack makeInlaidRobePreview() {
        ItemStack robe = new ItemStack(ItemsTC.voidRobeChest);
        ItemStack gem = new ItemStack(ModBlocksItems.TA_GEM, 1, PREVIEW_GEM_META);
        ResourceLocation id = ItemTAGem.getGemIdFromStack(gem);
        int tier = ItemTAGem.getTierFromStack(gem);
        int dmg = ItemTAGem.getGemDamage(gem);
        if (id != null) {
            TAGemInlayUtil.setGem(robe, id, tier, dmg);
        }
        return robe;
    }
}