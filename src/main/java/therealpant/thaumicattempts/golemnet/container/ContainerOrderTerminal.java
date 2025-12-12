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

    /**
     * Базовый шаг сетки слотов
     */
    public static final int CELL = 18;

    /**
     * Координаты правой сетки 3×3 (слоты приёмки) — СДВИНУЛИ вправо на +8
     */
    public static final int RIGHT_GRID_LEFT = 269; // было 239
    public static final int RIGHT_GRID_TOP = 32;

    /**
     * Координаты инвентаря игрока — ОПУСТИЛИ на +16
     */
    public static final int PLAYER_INV_LEFT = 89;
    public static final int PLAYER_INV_TOP = 164; // было 162
    public static final int HOTBAR_TOP = PLAYER_INV_TOP + 3 * CELL + 4; // было 220

    private final TileOrderTerminal te;
    private final IItemHandler buffer9;

    // диапазоны слотов терминала (для shift-метания)
    private int termStart = -1, termEnd = -1;

    public ContainerOrderTerminal(InventoryPlayer playerInv, TileOrderTerminal te) {
        this.te = te;
        this.buffer9 = te.getBufferHandler(); // IItemHandler на 9 слотов (3×3)

        // === 3×3 реальные слоты «доставки» справа ===
        // === 3×N реальные слоты «доставки» справа ===
        if (buffer9 != null && buffer9.getSlots() > 0) {
            termStart = this.inventorySlots.size();

            int maxSlots = Math.min(15, buffer9.getSlots()); // максимум 15 слотов (3×5)
            for (int i = 0; i < maxSlots; i++) {
                int col = i % 3;      // 0..2
                int row = i / 3;      // 0..4
                this.addSlotToContainer(new SlotItemHandler(
                        buffer9, i,
                        RIGHT_GRID_LEFT + col * CELL,
                        RIGHT_GRID_TOP + row * CELL
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
                        PLAYER_INV_TOP + r * CELL
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

        if (slot == null || !slot.getHasStack()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getStack();
        ret = stack.copy();

        // ---- диапазоны ----
        int termSlots = (termStart >= 0 && termEnd > termStart) ? (termEnd - termStart) : 0;
        int invStart = termEnd;              // начало основного инвентаря
        int invEnd = invStart + 27;        // 3×9
        int hotbarStart = invEnd;               // хотбар
        int hotbarEnd = hotbarStart + 9;
        int totalSlots = this.inventorySlots.size();

        // страховка, если что-то изменят в будущем
        invEnd = Math.min(invEnd, totalSlots);
        hotbarEnd = Math.min(hotbarEnd, totalSlots);

        // === клик из слотов терминала ===
        if (index >= termStart && index < termEnd) {
            // сначала в основной инвентарь, потом в хотбар
            if (!this.mergeItemStack(stack, invStart, invEnd, false)) {
                if (!this.mergeItemStack(stack, hotbarStart, hotbarEnd, false)) {
                    return ItemStack.EMPTY;
                }
            }
        }
        // === из основного инвентаря игрока ===
        else if (index >= invStart && index < invEnd) {
            // пытаемся закинуть в терминал
            if (termSlots > 0 && !this.mergeItemStack(stack, termStart, termEnd, false)) {
                // если не влезло – в хотбар
                if (!this.mergeItemStack(stack, hotbarStart, hotbarEnd, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (termSlots == 0) {
                // терминала нет – пробуем в хотбар
                if (!this.mergeItemStack(stack, hotbarStart, hotbarEnd, false)) {
                    return ItemStack.EMPTY;
                }
            }
        }
        // === из хотбара ===
        else if (index >= hotbarStart && index < hotbarEnd) {
            // сперва терминал, потом основной инвентарь
            if (termSlots > 0 && !this.mergeItemStack(stack, termStart, termEnd, false)) {
                if (!this.mergeItemStack(stack, invStart, invEnd, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (termSlots == 0) {
                if (!this.mergeItemStack(stack, invStart, invEnd, false)) {
                    return ItemStack.EMPTY;
                }
            }
        }

        // ---- обновление исходного слота ----
        if (stack.isEmpty()) {
            slot.putStack(ItemStack.EMPTY);
        } else {
            slot.onSlotChanged();
        }

        if (stack.getCount() == ret.getCount()) {
            return ItemStack.EMPTY;
        }

        slot.onTake(playerIn, stack);
        return ret;
    }
}