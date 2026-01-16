package therealpant.thaumicattempts.data.enchantments;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.world.World;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.crafting.InfusionRecipe;
import thaumcraft.api.items.IRechargable;
import thaumcraft.common.lib.enchantment.EnumInfusionEnchantment;

import java.util.List;

public class InfusionVisChargeLevelRecipe extends InfusionRecipe {

    private static final EnumInfusionEnchantment ENCH = EnumInfusionEnchantment.VISBATTERY;
    private final int targetLevel;

    public InfusionVisChargeLevelRecipe(String research,
                                        int instability,
                                        AspectList aspects,
                                        ItemStack displayCentral,
                                        int targetLevel,
                                        Object... components) {

        super(
                research,
                makePreview(displayCentral, targetLevel), // было makePreview(..., 1)
                instability,
                aspects,
                Ingredient.fromStacks(displayCentral),
                components
        );


        this.targetLevel = targetLevel;
    }

    private static ItemStack makePreview(ItemStack base, int lvl) {
        ItemStack out = base.copy();
        EnumInfusionEnchantment.addInfusionEnchantment(out, ENCH, lvl);
        return out;
    }

    @Override
    public boolean matches(List<ItemStack> input, ItemStack central, World world, EntityPlayer player) {
        if (!super.matches(input, central, world, player)) return false;
        if (central == null || central.isEmpty()) return false;
        if (!(central.getItem() instanceof IRechargable)) return false;

        int cur = EnumInfusionEnchantment.getInfusionEnchantmentLevel(central, ENCH);
        return cur == (targetLevel - 1); // 0->1, 1->2, 2->3
    }

    @Override
    public Object getRecipeOutput(EntityPlayer player, ItemStack central, List<ItemStack> comps) {
        ItemStack out = central.copy();
        out.setCount(1);
        EnumInfusionEnchantment.addInfusionEnchantment(out, ENCH, targetLevel); // РЕАЛЬНЫЙ уровень
        return out;
    }
}
