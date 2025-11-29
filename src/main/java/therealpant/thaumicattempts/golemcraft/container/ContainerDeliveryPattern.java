package therealpant.thaumicattempts.golemcraft.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.inventory.ClickType;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.Vec2f;
import net.minecraft.world.World;
import therealpant.thaumicattempts.golemcraft.SlotGhost;
import therealpant.thaumicattempts.golemcraft.item.ItemDeliveryPattern;

import java.util.ArrayList;
import java.util.List;

public class ContainerDeliveryPattern extends Container {

    private static final int CENTER_X = 88;
    private static final int CENTER_Y = 52;
    private static final int RADIUS = 36;

    private static final int RESULT_X = CENTER_X - 8;
    private static final int RESULT_Y = 18;

    private final ItemStack patternStack;
    private final InventoryBasic ghostInv;
    private final InventoryBasic resultInv;
    private int visibleSlots;
    private final int resultSlotIndex;
    private final World world;
    private boolean layoutReady = false;

    public ContainerDeliveryPattern(InventoryPlayer playerInv, ItemStack patternStack) {
        this.world = playerInv.player.world;
        this.patternStack = patternStack;
        this.ghostInv = new InventoryBasic("delivery_pattern", false, ItemDeliveryPattern.MAX_ENTRIES) {
            @Override
            public void markDirty() {
                super.markDirty();
                ItemDeliveryPattern.writeInventoryToStack(ContainerDeliveryPattern.this.patternStack, this);
                if (layoutReady) {
                    reflowSlots();
                }
            }
        };

        this.resultInv = new InventoryBasic("delivery_result", false, 1) {
            @Override public boolean isItemValid(int index, ItemStack stack) { return false; }
        };

        ItemDeliveryPattern.readListToInventory(patternStack, ghostInv);

        // ghost slots (позиции обновятся при первом reflow)
        for (int i = 0; i < ItemDeliveryPattern.MAX_ENTRIES; i++) {
            this.addSlotToContainer(new SlotGhost(ghostInv, i, -1000, -1000));
        }

        this.resultSlotIndex = this.inventorySlots.size();
        this.addSlotToContainer(new Slot(resultInv, 0, RESULT_X, RESULT_Y) {
            @Override public boolean isItemValid(ItemStack stack) { return false; }
            @Override public boolean canTakeStack(EntityPlayer playerIn) { return false; }
        });

        // inventory of player
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int x = 8 + col * 18;
                int y = 130 + row * 18;
                this.addSlotToContainer(new Slot(playerInv, col + row * 9 + 9, x, y));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlotToContainer(new Slot(playerInv, col, 8 + col * 18, 188));
        }

        this.layoutReady = true;
        reflowSlots();
    }

    public ItemStack getPatternStack() {
        return patternStack;
    }

    public int getPatternSlotCount() { return ItemDeliveryPattern.MAX_ENTRIES; }

    public int getResultSlotIndex() { return resultSlotIndex; }

    public int getVisibleSlots() {
        return visibleSlots;
    }

    @Override
    public boolean canInteractWith(EntityPlayer playerIn) {
        return true;
    }

    @Override
    public ItemStack slotClick(int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player) {
        if (slotId >= 0 && slotId < this.inventorySlots.size()) {
            Slot slot = this.inventorySlots.get(slotId);

            if (clickTypeIn == ClickType.QUICK_MOVE) {
                if (slot != null && slot.inventory == player.inventory) {
                    ItemStack from = slot.getStack();
                    if (!from.isEmpty()) {
                        ItemStack copy = from.copy();
                        for (int i = 0; i < ghostInv.getSizeInventory(); i++) {
                            if (ghostInv.getStackInSlot(i).isEmpty()) {
                                ghostInv.setInventorySlotContents(i, copy);
                                detectAndSendChanges();
                                return ItemStack.EMPTY;
                            }
                        }
                    }
                }
                return ItemStack.EMPTY;
            }

            if (slot instanceof SlotGhost) {
                int ghostIndex = slot.getSlotIndex();

                if (dragType == 1) {
                    ghostInv.setInventorySlotContents(ghostIndex, ItemStack.EMPTY);
                    detectAndSendChanges();
                    return ItemStack.EMPTY;
                }

                ItemStack held = player.inventory.getItemStack();
                if (!held.isEmpty()) {
                    ghostInv.setInventorySlotContents(ghostIndex, held.copy());
                } else {
                    ghostInv.setInventorySlotContents(ghostIndex, ItemStack.EMPTY);
                }
                detectAndSendChanges();
                return ItemStack.EMPTY;
            }
        }

        return super.slotClick(slotId, dragType, clickTypeIn, player);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer playerIn, int index) {
        Slot slot = inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) return ItemStack.EMPTY;
        ItemStack stack = slot.getStack();
        if (slot.inventory == ghostInv || slot.inventory == resultInv) {
            return ItemStack.EMPTY;
        }
        // copy to first empty ghost slot
        for (int i = 0; i < ghostInv.getSizeInventory(); i++) {
            if (ghostInv.getStackInSlot(i).isEmpty()) {
                ItemStack ghost = stack.copy();
                ghostInv.setInventorySlotContents(i, ghost);
                return ItemStack.EMPTY;
            }
        }
        return ItemStack.EMPTY;
    }

    private int countNonEmpty() {
        int cnt = 0;
        for (int i = 0; i < ghostInv.getSizeInventory(); i++) {
            if (!ghostInv.getStackInSlot(i).isEmpty()) cnt++;
        }
        return cnt;
    }

    private List<Vec2f> layoutFor(int total) {
        List<Vec2f> coords = new ArrayList<>();
        if (total <= 0) return coords;

        coords.add(new Vec2f(CENTER_X - 8, CENTER_Y - 8));
        int ring = Math.max(0, total - 1);
        if (ring > 0) {
            double step = (Math.PI * 2.0) / ring;
            double angle0 = -Math.PI / 2.0; // вершина над центром
            for (int i = 0; i < ring; i++) {
                double a = angle0 + i * step;
                int x = CENTER_X + (int) Math.round(Math.cos(a) * RADIUS) - 8;
                int y = CENTER_Y + (int) Math.round(Math.sin(a) * RADIUS) - 8;
                coords.add(new Vec2f(x, y));
            }
        }
        return coords;
    }

    private void reflowSlots() {
        int active = countNonEmpty();
        visibleSlots = Math.min(ItemDeliveryPattern.MAX_ENTRIES, Math.max(1, active + 1));
        List<Vec2f> coords = layoutFor(visibleSlots);
        for (int i = 0; i < ItemDeliveryPattern.MAX_ENTRIES; i++) {
            Slot s = this.inventorySlots.get(i);
            if (i < coords.size()) {
                Vec2f p = coords.get(i);
                s.xPos = (int) p.x;
                s.yPos = (int) p.y;
            } else {
                s.xPos = -1000;
                s.yPos = -1000;
            }
        }

        updateResult();
    }

    private void updateResult() {
        if (patternStack.isEmpty() || !(patternStack.getItem() instanceof ItemDeliveryPattern)) {
            resultInv.setInventorySlotContents(0, ItemStack.EMPTY);
            return;
        }

        ItemStack tmp = patternStack.copy();
        NonNullList<ItemStack> items = NonNullList.withSize(ghostInv.getSizeInventory(), ItemStack.EMPTY);
        for (int i = 0; i < ghostInv.getSizeInventory(); i++) {
            ItemStack s = ghostInv.getStackInSlot(i);
            items.set(i, s.isEmpty() ? ItemStack.EMPTY : s.copy());
        }

        InventoryBasic tmpInv = new InventoryBasic("shadow", false, items.size());
        for (int i = 0; i < items.size(); i++) {
            tmpInv.setInventorySlotContents(i, items.get(i));
        }

        ItemDeliveryPattern.writeInventoryToStack(tmp, tmpInv);

        ItemStack out = ItemDeliveryPattern.getInfusionPreview(tmp, world);
        resultInv.setInventorySlotContents(0, out == null ? ItemStack.EMPTY : out);
    }
}