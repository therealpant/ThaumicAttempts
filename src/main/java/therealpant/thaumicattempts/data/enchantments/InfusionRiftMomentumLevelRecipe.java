package therealpant.thaumicattempts.data.enchantments;

import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.world.World;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.crafting.InfusionRecipe;

import java.util.List;

public class InfusionRiftMomentumLevelRecipe extends InfusionRecipe {
    private static final String KEY = TAInfusionEnchantmentData.ENCH_RIFT_MOMENTUM;
    private final int targetLevel;

    public InfusionRiftMomentumLevelRecipe(String research,
                                           int instability,
                                           AspectList aspects,
                                           ItemStack displayCentral,
                                           int targetLevel,
                                           Object... components) {
        super(
                research,
                makePreview(displayCentral, targetLevel),
                instability,
                aspects,
                Ingredient.fromStacks(displayCentral),
                components
        );
        this.targetLevel = targetLevel;
    }

    private static ItemStack makePreview(ItemStack base, int lvl) {
        ItemStack out = base.copy();
        TAInfusionEnchantmentData.setLevel(out, KEY, lvl);
        return out;
    }

    @Override
    public boolean matches(List<ItemStack> input, ItemStack central, World world, EntityPlayer player) {
        if (!super.matches(input, central, world, player)) return false;
        if (central == null || central.isEmpty()) return false;
        if (!isSupportedItem(central)) return false;

        int cur = TAInfusionEnchantmentData.getLevel(central, KEY);
        return cur == (targetLevel - 1);
    }

    @Override
    public Object getRecipeOutput(EntityPlayer player, ItemStack central, List<ItemStack> comps) {
        ItemStack out = central.copy();
        out.setCount(1);
        TAInfusionEnchantmentData.setLevel(out, KEY, targetLevel);
        return out;
    }

    private static boolean isSupportedItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.getItem() instanceof ItemSword) return true;
        if (stack.getItem() instanceof ItemAxe) return true;
        if (stack.getItem() instanceof ItemTool) return true;

        for (AttributeModifier modifier : stack.getAttributeModifiers(net.minecraft.inventory.EntityEquipmentSlot.MAINHAND)
                .get(net.minecraft.entity.SharedMonsterAttributes.ATTACK_DAMAGE.getName())) {
            if (modifier != null && modifier.getAmount() > 0.0d) {
                return true;
            }
        }
        return false;
    }
}