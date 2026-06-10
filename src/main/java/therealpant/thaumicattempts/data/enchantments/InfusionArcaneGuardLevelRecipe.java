package therealpant.thaumicattempts.data.enchantments;

import baubles.api.IBauble;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.crafting.InfusionRecipe;
import thaumcraft.api.items.ItemsTC;
import therealpant.thaumicattempts.init.ModBlocksItems;

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
        if (central == null || central.isEmpty()) return false;
        if (!isSupportedItem(central)) return false;

        int cur = TAInfusionEnchantmentData.getLevel(central, KEY);
        if (targetLevel < 3) {
            return cur == (targetLevel - 1) && hasGuardComponents(input, targetLevel);
        }
        return cur >= 2 && hasGuardComponents(input, cur + 1);
    }

    @Override
    public Object getRecipeOutput(EntityPlayer player, ItemStack central, List<ItemStack> comps) {
        ItemStack out = central.copy();
        out.setCount(1);
        int nextLevel = Math.max(1, TAInfusionEnchantmentData.getLevel(central, KEY) + 1);
        if (targetLevel < 3) nextLevel = targetLevel;
        TAInfusionEnchantmentData.setLevel(out, KEY, nextLevel);

        NBTTagCompound tag = out.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            out.setTagCompound(tag);
        }
        int runic = getRunic(tag);
        int updated = Math.min(127, Math.max(runic + 1, nextLevel));
        tag.setByte(RUNIC_TAG, (byte) updated);

        return out;
    }

    private static boolean isSupportedItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem() instanceof ItemArmor || stack.getItem() instanceof IBauble;
    }

    private static boolean hasGuardComponents(List<ItemStack> input, int amethystCount) {
        if (input == null || amethystCount <= 0) return false;

        int needPearl = 1;
        int needAmethyst = amethystCount;
        int seen = 0;

        for (ItemStack stack : input) {
            if (stack == null || stack.isEmpty()) continue;
            seen++;

            if (matchesStack(stack, new ItemStack(ItemsTC.primordialPearl, 1, OreDictionary.WILDCARD_VALUE))) {
                needPearl--;
                continue;
            }
            if (matchesStack(stack, new ItemStack(ModBlocksItems.RIFT_AMETIST))) {
                needAmethyst--;
                continue;
            }
            return false;
        }

        return seen == (amethystCount + 1) && needPearl == 0 && needAmethyst == 0;
    }

    private static boolean matchesStack(ItemStack actual, ItemStack expected) {
        if (actual == null || actual.isEmpty() || expected == null || expected.isEmpty()) return false;
        if (!OreDictionary.itemMatches(expected, actual, false)) return false;
        return !expected.hasTagCompound() || ItemStack.areItemStackTagsEqual(expected, actual);
    }

    private static int getRunic(NBTTagCompound tag) {
        if (tag == null || !tag.hasKey(RUNIC_TAG)) return 0;
        return Math.max(0, tag.getInteger(RUNIC_TAG));
    }
}
