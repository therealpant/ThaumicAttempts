package therealpant.thaumicattempts.golemcraft;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import therealpant.thaumicattempts.golemcraft.item.ItemCraftPattern;
import therealpant.thaumicattempts.golemcraft.item.ItemResourceList;

public class SlotTemplateOnly extends SlotItemHandler {
    public SlotTemplateOnly(IItemHandler handler, int index, int x, int y) {
        super(handler, index, x, y);
    }
    @Override public boolean isItemValid(ItemStack stack) {
        if (stack.isEmpty()) return false;
        net.minecraft.item.Item item = stack.getItem();
        return item instanceof ItemCraftPattern || item instanceof ItemResourceList;
    }
}
