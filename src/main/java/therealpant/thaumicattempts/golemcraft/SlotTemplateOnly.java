package therealpant.thaumicattempts.golemcraft;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import therealpant.thaumicattempts.golemcraft.item.ItemCraftPattern;

public class SlotTemplateOnly extends SlotItemHandler {
    public SlotTemplateOnly(IItemHandler handler, int index, int x, int y) {
        super(handler, index, x, y);
    }
    @Override public boolean isItemValid(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemCraftPattern;
    }
}
