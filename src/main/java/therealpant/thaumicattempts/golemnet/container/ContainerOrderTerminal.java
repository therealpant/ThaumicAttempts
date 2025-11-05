// src/main/java/therealpant/thaumicattempts/golemnet/container/ContainerOrderTerminal.java
package therealpant.thaumicattempts.golemnet.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import therealpant.thaumicattempts.golemnet.tile.TileOrderTerminal;

/**
 * Контейнер терминала:
 * - Справа 3×3 реальные слоты «доставки» (IItemHandler из терминала)
 * - Инвентарь игрока снизу по центру
 *
 * Все координаты вынесены в константы и используются и контейнером, и GUI.
 */
public class ContainerOrderTerminal extends Container {

    /** Базовый шаг сетки слотов */
    public static final int CELL = 18;

    /** Координаты правой сетки 3×3 (слоты приёмки) — СДВИНУЛИ вправо на +8 */
    public static final int RIGHT_GRID_LEFT = 239 + 8; // было 239
    public static final int RIGHT_GRID_TOP  = 60;

    /** Координаты инвентаря игрока — ОПУСТИЛИ на +16 */
    public static final int PLAYER_INV_LEFT = 69;
    public static final int PLAYER_INV_TOP  = 162 + 16; // было 162
    public static final int HOTBAR_TOP      = 220 + 16; // было 220

    private final TileOrderTerminal te;
    private final IItemHandler buffer9;

    // диапазоны слотов терминала (для shift-метания)
    private int termStart = -1, termEnd = -1;

    public ContainerOrderTerminal(InventoryPlayer playerInv, TileOrderTerminal te) {
        this.te = te;
        this.buffer9 = te.getBufferHandler(); // IItemHandler на 9 слотов (3×3)

        // === 3×3 реальные слоты «доставки» справа ===
        if (buffer9 != null && buffer9.getSlots() >= 9) {
            termStart = this.inventorySlots.size();
            for (int i = 0; i < 9; i++) {
                int col = i % 3;
                int row = i / 3;
                this.addSlotToContainer(new SlotItemHandler(
                        buffer9, i,
                        RIGHT_GRID_LEFT + col * CELL,
                        RIGHT_GRID_TOP  + row * CELL
                ));
            }
            termEnd = this.inventorySlots.size();
        }

        // === Инвентарь игрока — снизу по центру ===
        // 3 строки 3×9
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                this.addSlotToContainer(new Slot(
                        playerInv, c + r * 9 + 9,
                        PLAYER_INV_LEFT + c * CELL,
                        PLAYER_INV_TOP  + r * CELL
                ));
            }
        }
        // хотбар
        for (int c = 0; c < 9; c++) {
            this.addSlotToContainer(new Slot(
                    playerInv, c,
                    PLAYER_INV_LEFT + c * CELL,
                    HOTBAR_TOP
            ));
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer playerIn) {
        return playerIn.getDistanceSq(te.getPos()) <= 64.0D;
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer playerIn, int index) {
        ItemStack ret = ItemStack.EMPTY;
        Slot slot = this.inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) return ItemStack.EMPTY;

        ItemStack stack = slot.getStack();
        ret = stack.copy();

        int playerStart = (termEnd < 0 ? 0 : termEnd);
        int playerEnd   = this.inventorySlots.size();

        if (termStart >= 0 && index >= termStart && index < termEnd) {
            // из терминала → в инвентарь
            if (!this.mergeItemStack(stack, playerStart, playerEnd, true)) return ItemStack.EMPTY;
        } else {
            // из инвентаря → в терминал
            if (termStart >= 0 && !this.mergeItemStack(stack, termStart, termEnd, false)) return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.putStack(ItemStack.EMPTY);
        else slot.onSlotChanged();

        if (stack.getCount() == ret.getCount()) return ItemStack.EMPTY;
        slot.onTake(playerIn, stack);
        return ret;
    }
}
