package therealpant.thaumicattempts.golemcraft;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public class SlotGhost extends Slot {

    public SlotGhost(IInventory inv, int index, int x, int y) {
        super(inv, index, x, y);
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        // Разрешаем класть что угодно (это «шаблонный» ghost-слот)
        return true;
    }

    @Override
    public boolean canTakeStack(EntityPlayer playerIn) {
        // Нельзя забирать предмет (ghost)
        return false;
    }

    @Override
    public int getSlotStackLimit() {
        // Обычно 1 достаточно для «призрака»
        return 1;
    }

    @Override
    public ItemStack decrStackSize(int amount) {
        // Никакого реального снятия — возвращаем пусто
        return ItemStack.EMPTY;
    }
}
