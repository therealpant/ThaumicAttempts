package therealpant.thaumicattempts.golemnet.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import therealpant.thaumicattempts.golemcraft.item.ItemCraftPatternInfusion;
import therealpant.thaumicattempts.golemnet.tile.TileInfusionRequester;

public class ContainerInfusionRequester extends Container {

    public static final int CELL = 18;
    public static final int PATTERN_COLS = 5;
    public static final int PATTERN_ROWS = 3;

    public static final int PATTERN_LEFT = 8;
    public static final int PATTERN_TOP = 20;

    public static final int SPECIAL_LEFT = PATTERN_LEFT + PATTERN_COLS * CELL + 14;
    public static final int SPECIAL_TOP = PATTERN_TOP;

    public static final int RESULT_COLS = 2;
    public static final int RESULT_ROWS = 2;
    public static final int RESULT_LEFT = SPECIAL_LEFT;
    public static final int RESULT_TOP = SPECIAL_TOP + CELL + 10;

    public static final int PLAYER_INV_LEFT = 8;
    public static final int PLAYER_INV_TOP = PATTERN_TOP + PATTERN_ROWS * CELL + 34;
    public static final int HOTBAR_TOP = PLAYER_INV_TOP + 58;

    private final TileInfusionRequester tile;
    private final IItemHandler patternHandler;
    private final IItemHandler specialHandler;
    private final IItemHandler resultHandler;

    private int patternStart = -1, patternEnd = -1;
    private int specialStart = -1, specialEnd = -1;
    private int resultStart = -1, resultEnd = -1;
    private int playerStart = -1, playerEnd = -1;

    public ContainerInfusionRequester(InventoryPlayer playerInv, TileInfusionRequester tile) {
        this.tile = tile;
        this.patternHandler = tile.getPatternHandler();
        this.specialHandler = tile.getSpecialHandler();
        this.resultHandler = tile.getResultHandler();

        if (patternHandler != null && patternHandler.getSlots() > 0) {
            patternStart = this.inventorySlots.size();
            int slots = Math.min(patternHandler.getSlots(), PATTERN_COLS * PATTERN_ROWS);
            for (int i = 0; i < slots; i++) {
                int col = i % PATTERN_COLS;
                int row = i / PATTERN_COLS;
                this.addSlotToContainer(new SlotItemHandler(
                        patternHandler,
                        i,
                        PATTERN_LEFT + col * CELL,
                        PATTERN_TOP + row * CELL
                ));
            }
            patternEnd = this.inventorySlots.size();
        }

        if (specialHandler != null) {
            specialStart = this.inventorySlots.size();
            this.addSlotToContainer(new SlotItemHandler(specialHandler, 0, SPECIAL_LEFT, SPECIAL_TOP));
            specialEnd = this.inventorySlots.size();
        }

        if (resultHandler != null && resultHandler.getSlots() > 0) {
            resultStart = this.inventorySlots.size();
            int slots = Math.min(resultHandler.getSlots(), RESULT_COLS * RESULT_ROWS);
            for (int i = 0; i < slots; i++) {
                int col = i % RESULT_COLS;
                int row = i / RESULT_COLS;
                this.addSlotToContainer(new SlotResultOnly(
                        resultHandler,
                        i,
                        RESULT_LEFT + col * CELL,
                        RESULT_TOP + row * CELL
                ));
            }
            resultEnd = this.inventorySlots.size();
        }

        playerStart = this.inventorySlots.size();
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                this.addSlotToContainer(new Slot(
                        playerInv,
                        c + r * 9 + 9,
                        PLAYER_INV_LEFT + c * CELL,
                        PLAYER_INV_TOP + r * CELL
                ));
            }
        }

        for (int c = 0; c < 9; c++) {
            this.addSlotToContainer(new Slot(
                    playerInv,
                    c,
                    PLAYER_INV_LEFT + c * CELL,
                    HOTBAR_TOP
            ));
        }
        playerEnd = this.inventorySlots.size();
    }

    @Override
    public boolean canInteractWith(EntityPlayer playerIn) {
        return playerIn.getDistanceSq(tile.getPos()) <= 64.0D;
    }

    private boolean inRange(int index, int start, int end) {
        return start >= 0 && index >= start && index < end;
    }

    private boolean hasRange(int start, int end) {
        return start >= 0 && end > start;
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer playerIn, int index) {
        Slot slot = this.inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();

        if (inRange(index, patternStart, patternEnd)
                || inRange(index, specialStart, specialEnd)
                || inRange(index, resultStart, resultEnd)) {
            if (!this.mergeItemStack(stack, playerStart, playerEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (stack.getItem() instanceof ItemCraftPatternInfusion) {
                if (!hasRange(patternStart, patternEnd)
                        || !this.mergeItemStack(stack, patternStart, patternEnd, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!hasRange(specialStart, specialEnd)
                        || !this.mergeItemStack(stack, specialStart, specialEnd, false)) {
                    return ItemStack.EMPTY;
                }
            }
        }

        if (stack.isEmpty()) {
            slot.putStack(ItemStack.EMPTY);
        } else {
            slot.onSlotChanged();
        }

        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }

        slot.onTake(playerIn, stack);
        return original;
    }

    @Override
    public ItemStack slotClick(int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player) {
        return super.slotClick(slotId, dragType, clickTypeIn, player);
    }

    private static class SlotResultOnly extends SlotItemHandler {
        public SlotResultOnly(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return false;
        }
    }
}