package therealpant.thaumicattempts.golemcraft.container;

import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

public interface IPatternContainer {
    ItemStack getPatternStack();
    boolean isInfusionMode();
    NonNullList<ItemStack> getOrderView();
}