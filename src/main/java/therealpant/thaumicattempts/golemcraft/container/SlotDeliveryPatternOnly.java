package therealpant.thaumicattempts.golemcraft.container;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import therealpant.thaumicattempts.golemcraft.item.ItemDeliveryPattern;

/**
 * Slot that accepts only delivery list patterns for the delivery station.
 */
public class SlotDeliveryPatternOnly extends SlotItemHandler {

    public SlotDeliveryPatternOnly(IItemHandler handler, int index, int xPosition, int yPosition) {
        super(handler, index, xPosition, yPosition);
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemDeliveryPattern;
    }
}
