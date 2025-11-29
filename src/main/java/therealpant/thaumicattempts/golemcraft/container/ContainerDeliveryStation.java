package therealpant.thaumicattempts.golemcraft.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import therealpant.thaumicattempts.golemcraft.item.ItemDeliveryPattern;
import therealpant.thaumicattempts.golemcraft.tile.TileDeliveryStation;

public class ContainerDeliveryStation extends Container {

    public static final int CELL = 18;

    /** ===== PATTERN (ItemDeliveryPattern) – поле 3×5 ===== */
    public static final int PATTERN_COLS = 5;
    public static final int PATTERN_ROWS = 3;

    // Позиционирование совпадает с GuiResourceRequester
    public static final int PLAYER_INV_LEFT = 8;
    public static final int PATTERN_LEFT = 8;
    public static final int PATTERN_TOP  = 83;

    // Один слот для предмета ПКМ – по центру старого буфера 3×3
    public static final int PAYLOAD_LEFT = PLAYER_INV_LEFT + CELL * 7; // колонки 6,7,8 → центр = 7
    public static final int PAYLOAD_TOP  = PATTERN_TOP + CELL;         // средняя строка

    // Инвентарь игрока – под всем этим
    public static final int PLAYER_INV_TOP = PATTERN_TOP + PATTERN_ROWS * CELL + 14; // 151
    public static final int HOTBAR_TOP     = PLAYER_INV_TOP + 58;                    // 209

    private final TileDeliveryStation tile;
    private final IItemHandler patternHandler;
    private final IItemHandler payloadHandler;

    private int patternStart = -1;
    private int patternEnd = -1;
    private int payloadStart = -1;
    private int payloadEnd = -1;
    private int playerStart = -1;
    private int playerEnd = -1;

    public ContainerDeliveryStation(InventoryPlayer playerInv, TileDeliveryStation tile) {
        this.tile = tile;
        this.patternHandler = tile.getPatternHandler();
        this.payloadHandler = tile.getPayloadHandler();

        // ===== PATTERN 3×5 =====
        if (patternHandler != null && patternHandler.getSlots() > 0) {
            patternStart = this.inventorySlots.size();
            int slots = Math.min(patternHandler.getSlots(), PATTERN_COLS * PATTERN_ROWS);
            for (int i = 0; i < slots; i++) {
                int col = i % PATTERN_COLS;
                int row = i / PATTERN_COLS;
                this.addSlotToContainer(new SlotDeliveryPatternOnly(
                        patternHandler,
                        i,
                        PATTERN_LEFT + col * CELL,
                        PATTERN_TOP + row * CELL
                ));
            }
            patternEnd = this.inventorySlots.size();
        }

        // ===== PAYLOAD 1×1 =====
        if (payloadHandler != null && payloadHandler.getSlots() > 0) {
            payloadStart = this.inventorySlots.size();
            this.addSlotToContainer(new SlotItemHandler(payloadHandler, 0, PAYLOAD_LEFT, PAYLOAD_TOP));
            payloadEnd = this.inventorySlots.size();
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

    private boolean inRange(int index, int start, int end) { return start >= 0 && index >= start && index < end; }
    private boolean hasRange(int start, int end) { return start >= 0 && end > start; }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer playerIn, int index) {
        Slot slot = this.inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();

        if (inRange(index, patternStart, patternEnd)) {
            if (!this.mergeItemStack(stack, playerStart, playerEnd, true)) return ItemStack.EMPTY;
        } else if (inRange(index, payloadStart, payloadEnd)) {
            if (!this.mergeItemStack(stack, playerStart, playerEnd, true)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof ItemDeliveryPattern) {
            if (!hasRange(patternStart, patternEnd)
                    || !this.mergeItemStack(stack, patternStart, patternEnd, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!hasRange(payloadStart, payloadEnd)
                    || !this.mergeItemStack(stack, payloadStart, payloadEnd, false)) {
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
}
