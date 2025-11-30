package therealpant.thaumicattempts.golemcraft.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import therealpant.thaumicattempts.golemcraft.SlotGhost;
import therealpant.thaumicattempts.golemcraft.item.ItemBasePattern;
import therealpant.thaumicattempts.golemcraft.item.ItemInfusionPattern;

public class ContainerInfusionPattern extends Container implements IPatternContainer {

    private static final int ORDER_SIZE = ItemInfusionPattern.MAX_ORDER;

    private final InventoryPlayer playerInv;
    private final ItemStack patternStack;
    private final IInventory ghostInv;
    private final int resultIndex;

    private static final int GRID_LEFT = 62;
    private static final int GRID_TOP  = 17;
    private static final int CELL = 18;

    public ContainerInfusionPattern(InventoryPlayer playerInv, ItemStack patternStack) {
        this.playerInv = playerInv;
        this.patternStack = patternStack;
        this.resultIndex = ORDER_SIZE;

        this.ghostInv = new InventoryBasic("pattern_ghost", false, ORDER_SIZE + 1) {
            @Override public int getInventoryStackLimit() { return 1; }
            @Override public boolean isItemValidForSlot(int index, ItemStack stack) { return true; }
        };

        readPatternToInventory(patternStack);

        for (int i = 0; i < ORDER_SIZE; i++) {
            this.addSlotToContainer(new SlotGhost(ghostInv, i, -10000, -10000));
        }

        int baseW = 3 * CELL;
        int resultX = GRID_LEFT + baseW / 2 - 8;
        int gap = 8;
        int resultY = GRID_TOP - (24 + gap);
        this.addSlotToContainer(new Slot(ghostInv, resultIndex, resultX, resultY) {
            @Override public boolean isItemValid(ItemStack stack) { return false; }
            @Override public boolean canTakeStack(EntityPlayer playerIn) { return false; }
        });

        addPlayerInventorySlots(playerInv, 8, 130);
    }

    @Override
    public ItemStack getPatternStack() {
        return patternStack;
    }

    @Override
    public boolean isInfusionMode() {
        return true;
    }

    @Override
    public NonNullList<ItemStack> getOrderView() {
        NonNullList<ItemStack> view = NonNullList.withSize(ORDER_SIZE, ItemStack.EMPTY);
        for (int i = 0; i < ORDER_SIZE; i++) {
            ItemStack stack = ghostInv.getStackInSlot(i);
            view.set(i, stack == null ? ItemStack.EMPTY : stack.copy());
        }
        return view;
    }

    private void addPlayerInventorySlots(InventoryPlayer inv, int left, int top) {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                this.addSlotToContainer(new Slot(inv, c + r * 9 + 9, left + c * 18, top + r * 18));
            }
        }
        for (int c = 0; c < 9; c++) {
            this.addSlotToContainer(new Slot(inv, c, left + c * 18, top + 58));
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer playerIn) {
        return true;
    }

    @Override
    public ItemStack slotClick(int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player) {
        if (slotId >= 0 && slotId < this.inventorySlots.size()) {
            Slot slot = this.inventorySlots.get(slotId);

            if (slot != null && slot.getSlotIndex() == resultIndex && slot.inventory == ghostInv) {
                ItemStack held = player.inventory.getItemStack();

                if (!held.isEmpty() && clickTypeIn == ClickType.PICKUP) {
                    ItemStack ghost = held.copy();
                    ghost.setCount(1);

                    NonNullList<ItemStack> order = NonNullList.withSize(ORDER_SIZE, ItemStack.EMPTY);
                    for (int i = 0; i < ORDER_SIZE; i++) {
                        ItemStack s = ghostInv.getStackInSlot(i);
                        order.set(i, s.isEmpty() ? ItemStack.EMPTY : s.copy());
                    }

                    if (patternStack.getItem() instanceof ItemInfusionPattern) {
                        ItemInfusionPattern.writeInventoryToStack(patternStack, order, ghost);
                    }

                    ghostInv.setInventorySlotContents(resultIndex, ghost);
                    detectAndSendChanges();
                    return ItemStack.EMPTY;
                }

                if (clickTypeIn == ClickType.PICKUP) {
                    int delta = (dragType == 1) ? -1 : 1;
                    ItemBasePattern.adjustRepeatCount(patternStack, delta);
                    player.inventory.markDirty();
                    detectAndSendChanges();
                }
                return ItemStack.EMPTY;
            }
        }

        return super.slotClick(slotId, dragType, clickTypeIn, player);
    }

    @Override
    public void onContainerClosed(EntityPlayer playerIn) {
        super.onContainerClosed(playerIn);
        if (patternStack.isEmpty()) return;

        NonNullList<ItemStack> grid = NonNullList.withSize(ORDER_SIZE, ItemStack.EMPTY);
        for (int i = 0; i < ORDER_SIZE; i++) {
            grid.set(i, ghostInv.getStackInSlot(i).copy());
        }

        ItemStack preview = ghostInv.getStackInSlot(resultIndex).copy();
        if (patternStack.getItem() instanceof ItemInfusionPattern) {
            ItemInfusionPattern.writeInventoryToStack(patternStack, grid, preview);
        }
    }

    private void readPatternToInventory(ItemStack patternStack) {
        if (patternStack.isEmpty()) {
            for (int i = 0; i < ORDER_SIZE; i++) ghostInv.setInventorySlotContents(i, ItemStack.EMPTY);
            return;
        }

        if (patternStack.getItem() instanceof ItemInfusionPattern) {
            ItemInfusionPattern.readOrderToInventory(patternStack, ghostInv, ORDER_SIZE);
            ItemStack preview = ItemInfusionPattern.readResult(patternStack);
            ghostInv.setInventorySlotContents(resultIndex, preview);
        } else {
            for (int i = 0; i < ORDER_SIZE; i++) ghostInv.setInventorySlotContents(i, ItemStack.EMPTY);
        }
    }

    @Override
    public boolean enchantItem(EntityPlayer player, int id) {
        boolean changed = false;
        if (id == 0) {
            ItemStack held = player.inventory.getItemStack();
            if (!held.isEmpty()) {
                ItemStack copy = held.copy();
                copy.setCount(1);
                for (int i = 0; i < ORDER_SIZE; i++) {
                    if (ghostInv.getStackInSlot(i).isEmpty()) {
                        ghostInv.setInventorySlotContents(i, copy);
                        changed = true;
                        break;
                    }
                }
            }
        } else if (id == 1) {
            for (int i = ORDER_SIZE - 1; i >= 0; i--) {
                if (!ghostInv.getStackInSlot(i).isEmpty()) {
                    ghostInv.setInventorySlotContents(i, ItemStack.EMPTY);
                    changed = true;
                    break;
                }
            }
        }

        if (changed) {
            detectAndSendChanges();
        }

        return changed;
    }
}