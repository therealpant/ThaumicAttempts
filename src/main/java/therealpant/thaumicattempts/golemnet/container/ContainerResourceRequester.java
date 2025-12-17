package therealpant.thaumicattempts.golemnet.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import therealpant.thaumicattempts.golemcraft.item.ItemResourceList;
import therealpant.thaumicattempts.golemnet.tile.TileResourceRequester;

public class ContainerResourceRequester extends Container {

    public static final int CELL = 18;

    /** ===== PATTERN (ItemResourceList) – поле 3×5 ===== */
    public static final int PATTERN_COLS = 3;
    public static final int PATTERN_ROWS = 5;

    /** ===== ВНУТРЕННИЙ ИНВЕНТАРЬ БЛОКА – 3×3 ===== */
    public static final int BUFFER_COLS = 3;
    public static final int BUFFER_ROWS = 3;

    /** ===== БАЗОВЫЕ КООРДИНАТЫ (под новый фон 354×256) ===== */
    public static final int PLAYER_INV_LEFT = 89;
    public static final int PLAYER_INV_TOP  = 164;
    public static final int HOTBAR_TOP      = PLAYER_INV_TOP + 58; // 222

    public static final int PATTERN_LEFT = 62;
    public static final int PATTERN_TOP  = 28;

    public static final int BUFFER_LEFT = 224;
    public static final int BUFFER_TOP  = 64;

    private final TileResourceRequester tile;
    private final IItemHandler patternHandler;
    private final IItemHandler bufferHandler;
    private int patternStart = -1;
    private int patternEnd = -1;
    private int bufferStart = -1;
    private int bufferEnd = -1;
    private int playerStart = -1;
    private int playerEnd = -1;

    public ContainerResourceRequester(InventoryPlayer playerInv, TileResourceRequester tile) {
        this.tile = tile;
        this.patternHandler = tile.getPatternHandler();
        this.bufferHandler = tile.getBufferHandler();

        // ===== PATTERN 3×5 =====
        if (patternHandler != null && patternHandler.getSlots() > 0) {
            patternStart = this.inventorySlots.size();
            int slots = Math.min(patternHandler.getSlots(), PATTERN_COLS * PATTERN_ROWS);
            for (int i = 0; i < slots; i++) {
                int col = i % PATTERN_COLS;
                int row = i / PATTERN_COLS;
                this.addSlotToContainer(new SlotResourceListOnly(
                        patternHandler,
                        i,
                        PATTERN_LEFT + col * CELL,
                        PATTERN_TOP + row * CELL
                ));
            }
            patternEnd = this.inventorySlots.size();
        }

        // ===== БУФЕР 3×3 =====
        if (bufferHandler != null && bufferHandler.getSlots() > 0) {
            bufferStart = this.inventorySlots.size();
            int slots = Math.min(bufferHandler.getSlots(), BUFFER_COLS * BUFFER_ROWS); // теперь максимум 9
            for (int i = 0; i < slots; i++) {
                int col = i % BUFFER_COLS;
                int row = i / BUFFER_COLS;
                this.addSlotToContainer(new SlotItemHandler(
                        bufferHandler,
                        i,
                        BUFFER_LEFT + col * CELL,
                        BUFFER_TOP + row * CELL
                ));
            }
            bufferEnd = this.inventorySlots.size();
        }

        // ===== ИНВЕНТАРЬ ИГРОКА =====
        playerStart = this.inventorySlots.size();

        // 9×3
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

        // хотбар
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

        if (inRange(index, patternStart, patternEnd)) {
            if (!this.mergeItemStack(stack, playerStart, playerEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (inRange(index, bufferStart, bufferEnd)) {
            if (!this.mergeItemStack(stack, playerStart, playerEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.getItem() instanceof ItemResourceList) {
            if (!hasRange(patternStart, patternEnd)
                    || !this.mergeItemStack(stack, patternStart, patternEnd, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!hasRange(bufferStart, bufferEnd)
                    || !this.mergeItemStack(stack, bufferStart, bufferEnd, false)) {
                return ItemStack.EMPTY;
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
}
