package therealpant.thaumicattempts.golemcraft.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import therealpant.thaumicattempts.golemcraft.tile.TileEntityGolemCrafter;

import java.lang.reflect.Method;

public class ContainerGolemCrafter extends Container {

    private final TileEntityGolemCrafter te;
    private IItemHandler patterns;

    private int teStart = 0, teEnd = 0;
    private int resultStart = 0, resultEnd = 0;

    /* ---------- Геометрия GUI ---------- */

    // Инвентарь игрока
    public static final int PLAYER_TOP_DEFAULT  = 164;
    public static final int PLAYER_LEFT_DEFAULT = 89;

    // Панель 3×5 (паттерны)
    public static final int PANEL_COLS = 3;
    public static final int PANEL_ROWS = 5;
    public static final int PANEL_LEFT_DEFAULT = 62;
    public static final int PANEL_TOP_DEFAULT  = 28;

    // Сетка результатов 3×3
    public static final int RESULT_COLS = 3;
    public static final int RESULT_ROWS = 3;
    public static final int RESULT_LEFT_DEFAULT = 224;
    public static final int RESULT_TOP_DEFAULT  = 64;

    private final int playerTop;
    private final int playerLeft;
    private final int panelLeft;
    private final int panelTop;
    private final int resultLeft;
    private final int resultTop;

    public ContainerGolemCrafter(InventoryPlayer playerInv, TileEntityGolemCrafter te) {
        this(playerInv, te, PLAYER_LEFT_DEFAULT, PLAYER_TOP_DEFAULT, PANEL_LEFT_DEFAULT, PANEL_TOP_DEFAULT,
                RESULT_LEFT_DEFAULT, RESULT_TOP_DEFAULT);
    }

    protected ContainerGolemCrafter(InventoryPlayer playerInv, TileEntityGolemCrafter te, int playerLeft, int playerTop,
                                    int panelLeft, int panelTop, int resultLeft, int resultTop) {

        this.te = te;
        this.playerLeft = playerLeft;
        this.playerTop = playerTop;
        this.panelLeft = panelLeft;
        this.panelTop = panelTop;
        this.resultLeft = resultLeft;
        this.resultTop = resultTop;

        this.patterns = tryGetPatternHandlerReflective(te);
        if (this.patterns == null) {
            IItemHandler h = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
            if (h == null) h = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.UP);
            if (h != null && h.getSlots() >= 15) this.patterns = h;
        }

        // 15 слотов на сетке 3×5
        if (this.patterns != null) {
            teStart = this.inventorySlots.size();
            for (int r = 0; r < PANEL_ROWS; r++) {
                for (int c = 0; c < PANEL_COLS; c++) {
                    int idx = r * PANEL_COLS + c; // 0..14
                    this.addSlotToContainer(new SlotItemHandler(
                            this.patterns, idx,
                            panelLeft + c * 18,   // шаг 18 = 16 + 2
                            panelTop  + r * 18));
                }
            }
            teEnd = this.inventorySlots.size();
        } else {
            teStart = teEnd = 0;
        }

        // 9 выходных слотов 3×3
        IItemHandler output = te.getOutputHandler();
        if (output != null && output.getSlots() > 0) {
            resultStart = this.inventorySlots.size();
            int slots = Math.min(output.getSlots(), RESULT_COLS * RESULT_ROWS);
            for (int i = 0; i < slots; i++) {
                int col = i % RESULT_COLS;
                int row = i / RESULT_COLS;
                this.addSlotToContainer(new SlotResultOnly(
                        output,
                        i,
                        resultLeft + col * 18,
                        resultTop  + row * 18
                ));
            }
            resultEnd = this.inventorySlots.size();
        }

        // Инвентарь игрока — по центру
        int invLeft =  playerLeft;
        int invTop  = playerTop;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                this.addSlotToContainer(new Slot(playerInv, c + r * 9 + 9, invLeft + c * 18, invTop + r * 18));
            }
        }
        int hotTop = invTop + 58;
        for (int c = 0; c < 9; c++) {
            this.addSlotToContainer(new Slot(playerInv, c, invLeft + c * 18, hotTop));
        }
    }
    public int getPanelLeft() {
        return panelLeft;
    }

    public int getPanelTop() {
        return panelTop;
    }

    public int getResultLeft() {
        return resultLeft;
    }

    public int getResultTop() {
        return resultTop;
    }

    public int getPlayerLeft() {
        return playerLeft;
    }

    public int getPlayerTop() {
        return playerTop;
    }

    private static IItemHandler tryGetPatternHandlerReflective(TileEntityGolemCrafter te) {
        try {
            Method m = te.getClass().getMethod("getPatternHandler");
            Object res = m.invoke(te);
            if (res instanceof IItemHandler) return (IItemHandler) res;
        } catch (Exception ignored) {}
        return null;
    }

    private static class SlotResultOnly extends SlotItemHandler {
        SlotResultOnly(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return false;
        }
    }

    @Override public boolean canInteractWith(EntityPlayer playerIn) {
        return playerIn.getDistanceSq(te.getPos()) <= 64.0D;
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer playerIn, int index) {
        ItemStack stack = ItemStack.EMPTY;
        Slot slot = this.inventorySlots.get(index);
        if (slot != null && slot.getHasStack()) {
            ItemStack in = slot.getStack();
            stack = in.copy();

            int playerStart = resultEnd > resultStart ? resultEnd : teEnd;
            int playerEnd   = this.inventorySlots.size();

            if (index < teEnd || (resultEnd > resultStart && index < resultEnd)) {
                if (!mergeItemStack(in, playerStart, playerEnd, true)) return ItemStack.EMPTY;
            } else {
                if (teEnd > teStart) {
                    if (!mergeItemStack(in, teStart, teEnd, false)) return ItemStack.EMPTY;
                } else return ItemStack.EMPTY;
            }

            if (in.isEmpty()) slot.putStack(ItemStack.EMPTY);
            else slot.onSlotChanged();

            if (in.getCount() == stack.getCount()) return ItemStack.EMPTY;
            slot.onTake(playerIn, in);
        }
        return stack;
    }
}
