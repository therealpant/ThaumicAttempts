package therealpant.thaumicattempts.data.enchantments;

import java.util.List;

import baubles.api.IBauble;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.crafting.InfusionRecipe;
import therealpant.thaumicattempts.init.ModBlocksItems;

/**
 * A fixed-cost version of TC6 runic shielding. Each craft adds one ward,
 * while only instability scales with the ward value already on the item.
 */
public final class InfusionRunicShieldingRecipe extends InfusionRecipe {
    private static final String RUNIC_TAG = "TC.RUNIC";
    private static final int MAX_RUNIC = Byte.MAX_VALUE;

    public InfusionRunicShieldingRecipe(String research) {
        super(
                research,
                makePreview(),
                6,
                new AspectList()
                        .add(Aspect.PROTECT, 175)
                        .add(Aspect.ELDRITCH, 120)
                        .add(Aspect.CRYSTAL, 75),
                Ingredient.EMPTY,
                new ItemStack(ModBlocksItems.RIFT_AMETIST),
                new ItemStack(ModBlocksItems.RIFT_AMETIST),
                new ItemStack(ItemsTC.primordialPearl, 1, 32767)
        );
    }

    @Override
    public boolean matches(List<ItemStack> input, ItemStack central, World world, EntityPlayer player) {
        return isSupportedItem(central)
                && getRunic(central) < MAX_RUNIC
                && super.matches(input, central, world, player);
    }

    @Override
    public Object getRecipeOutput(EntityPlayer player, ItemStack central, List<ItemStack> components) {
        if (!isSupportedItem(central)) return ItemStack.EMPTY;
        ItemStack output = central.copy();
        output.setCount(1);
        output.setTagInfo(RUNIC_TAG, new NBTTagByte((byte) Math.min(MAX_RUNIC, getRunic(central) + 1)));
        return output;
    }

    @Override
    public int getInstability(EntityPlayer player, ItemStack central, List<ItemStack> components) {
        return 6 + getRunic(central) / 2;
    }

    private static ItemStack makePreview() {
        ItemStack preview = new ItemStack(ItemsTC.baubles, 1, 1);
        preview.setTagInfo(RUNIC_TAG, new NBTTagByte((byte) 1));
        return preview;
    }

    private static boolean isSupportedItem(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && (stack.getItem() instanceof ItemArmor || stack.getItem() instanceof IBauble);
    }

    private static int getRunic(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(RUNIC_TAG, 99)) return 0;
        return Math.max(0, tag.getInteger(RUNIC_TAG));
    }
}
