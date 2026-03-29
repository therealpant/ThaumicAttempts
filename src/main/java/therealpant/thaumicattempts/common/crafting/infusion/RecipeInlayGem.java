package therealpant.thaumicattempts.common.crafting.infusion;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
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
    private static final ItemStack VOID_INGOT = new ItemStack(ItemsTC.ingots, 1, 1);
    private static final ItemStack THAUMIUM_PLATE = new ItemStack(ItemsTC.plate, 1, 3);

    public RecipeInlayGem(String research, int instability, AspectList aspects, Object... components) {
        super(research, new ItemStack(ItemsTC.voidRobeChest), instability, aspects,
                new ItemStack(ItemsTC.voidRobeChest), components);
    }

    @Override
    public boolean matches(List<ItemStack> input, ItemStack central, World world, EntityPlayer player) {
        if (central == null || central.isEmpty()) return false;
        if (!(central.getItem() instanceof ItemArmor)) return false;
        if (TAGemInlayUtil.hasGem(central)) return false;
        if (!hasRequiredComponents(input)) return false;
        ItemStack gemStack = getSingleGem(input);
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
        ItemStack gemStack = getSingleGem(comps);
        if (gemStack.isEmpty()) return ItemStack.EMPTY;
        ResourceLocation id = ItemTAGem.getGemIdFromStack(gemStack);
        int tier = ItemTAGem.getTierFromStack(gemStack);
        int dmg = ItemTAGem.getGemDamage(gemStack);
        if (id == null) return ItemStack.EMPTY;
        ITAGemDefinition def = TAGemRegistry.get(id);
        if (def == null || tier < 1 || tier > def.getMaxTier()) return ItemStack.EMPTY;
        TAGemInlayUtil.setGem(out, id, tier, dmg);
        return out;
    }

    private static boolean hasRequiredComponents(List<ItemStack> input) {
        if (input == null || input.isEmpty()) return false;

        int needVoidIngots = 2;
        int needPlates = 2;
        int needPearl = 1;
        boolean foundGem = false;

        for (ItemStack stack : input) {
            if (stack == null || stack.isEmpty()) continue;

            if (ItemTAGem.isGem(stack)) {
                if (foundGem) return false;
                foundGem = true;
                continue;
            }
            if (ItemStack.areItemsEqual(stack, VOID_INGOT)) {
                needVoidIngots--;
                continue;
            }
            if (ItemStack.areItemsEqual(stack, THAUMIUM_PLATE)) {
                needPlates--;
                continue;
            }
            if (stack.getItem() == ItemsTC.primordialPearl) {
                needPearl--;
                continue;
            }
            return false;
        }

        return foundGem && needVoidIngots == 0 && needPlates == 0 && needPearl == 0;
    }

    private static ItemStack getSingleGem(List<ItemStack> stacks) {
        if (stacks == null) return ItemStack.EMPTY;
        ItemStack found = ItemStack.EMPTY;
        for (ItemStack stack : stacks) {
            if (!ItemTAGem.isGem(stack)) continue;
            if (!found.isEmpty()) return ItemStack.EMPTY;
            found = stack;
        }
        return found;
    }

}