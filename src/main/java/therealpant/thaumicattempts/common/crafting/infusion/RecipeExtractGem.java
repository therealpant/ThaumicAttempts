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
    private static final ItemStack VOID_INGOT = new ItemStack(ItemsTC.ingots, 1, 1);
    private static final ItemStack THAUMIUM_PLATE = new ItemStack(ItemsTC.plate, 1, 3);
    private static final ItemStack SALIS_MUNDUS = new ItemStack(ItemsTC.salisMundus);

    public RecipeExtractGem(String research, int instability, AspectList aspects, Object... components) {
        super(research, new ItemStack(ModBlocksItems.TA_GEM, 1, 0), instability, aspects,
                Ingredient.fromStacks(VOID_INGOT), components);
    }

    @Override
    public boolean matches(List<ItemStack> input, ItemStack central, World world, EntityPlayer player) {
        if (central == null || central.isEmpty()) return false;
        if (!ItemStack.areItemsEqual(VOID_INGOT, central)) return false;
        if (input == null || input.isEmpty()) return false;

        int needVoidIngots = 2;
        int needPlates = 2;
        int needSalis = 1;
        boolean foundArmorWithGem = false;

        for (ItemStack stack : input) {
            if (stack == null || stack.isEmpty()) continue;

            if (ItemStack.areItemsEqual(stack, VOID_INGOT)) {
                needVoidIngots--;
                continue;
            }
            if (ItemStack.areItemsEqual(stack, THAUMIUM_PLATE)) {
                needPlates--;
                continue;
            }
            if (ItemStack.areItemsEqual(stack, SALIS_MUNDUS)) {
                needSalis--;
                continue;
            }

            if (isArmorWithGem(stack)) {
                if (foundArmorWithGem) return false;
                foundArmorWithGem = true;
                continue;
            }

            return false;
        }

        if (needVoidIngots != 0 || needPlates != 0 || needSalis != 0 || !foundArmorWithGem) {
            return false;
        }

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
            if (!isArmorWithGem(stack)) continue;
            if (!found.isEmpty()) return ItemStack.EMPTY;
            found = stack;
            }
        return found;
    }

    private static boolean isArmorWithGem(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof ItemArmor && TAGemInlayUtil.hasGem(stack);
    }
}