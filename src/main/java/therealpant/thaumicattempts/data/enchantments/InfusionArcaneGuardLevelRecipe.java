package therealpant.thaumicattempts.data.enchantments;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.crafting.InfusionRecipe;

import java.util.List;

public class InfusionArcaneGuardLevelRecipe extends InfusionRecipe {
    private static final String KEY = TAInfusionEnchantmentData.ENCH_ARCANE_GUARD;
    private static final String RUNIC_TAG = "TC.RUNIC";
    private final int targetLevel;

    public InfusionArcaneGuardLevelRecipe(String research,
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
        if (!(central.getItem() instanceof ItemArmor)) return false;

        int cur = TAInfusionEnchantmentData.getLevel(central, KEY);
        return cur == (targetLevel - 1);
    }

    @Override
    public Object getRecipeOutput(EntityPlayer player, ItemStack central, List<ItemStack> comps) {
        ItemStack out = central.copy();
        out.setCount(1);
        TAInfusionEnchantmentData.setLevel(out, KEY, targetLevel);

        NBTTagCompound tag = out.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            out.setTagCompound(tag);
        }
        int runic = Math.max(0, tag.getByte(RUNIC_TAG));
        int updated = Math.min(127, runic + 1);
        tag.setByte(RUNIC_TAG, (byte) updated);

        return out;
    }
}
