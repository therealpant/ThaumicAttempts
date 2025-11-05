package therealpant.thaumicattempts.golemcraft;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

public class InventoryGhost implements IInventory {
    private final GhostGrid grid;
    final NonNullList<ItemStack> view;

    public InventoryGhost(GhostGrid grid){
        this.grid = grid;
        this.view = grid.ghosts; // 9 слотов
    }

    @Override public int getSizeInventory() { return 9; }
    @Override public boolean isEmpty(){ for (ItemStack s:view) if (!s.isEmpty()) return false; return true; }
    @Override public ItemStack getStackInSlot(int index){ return view.get(index); }

    @Override public ItemStack removeStackFromSlot(int index){
        ItemStack prev = view.get(index);
        view.set(index, ItemStack.EMPTY);
        return prev;
    }

    @Override public ItemStack decrStackSize(int index, int count){
        ItemStack prev = view.get(index);
        view.set(index, ItemStack.EMPTY);
        return prev;
    }

    @Override public void setInventorySlotContents(int index, ItemStack stack){
        ItemStack ghost = stack.isEmpty()? ItemStack.EMPTY : stack.copy();
        if (!ghost.isEmpty()) ghost.setCount(1);
        view.set(index, ghost);
    }

    @Override public int getInventoryStackLimit(){ return 1; }
    @Override public void markDirty() {}
    @Override public boolean isUsableByPlayer(EntityPlayer player){ return true; }
    @Override public void openInventory(EntityPlayer player) {}
    @Override public void closeInventory(EntityPlayer player) {}
    @Override public boolean isItemValidForSlot(int index, ItemStack stack){ return true; }
    @Override public int getField(int id){ return 0; }
    @Override public void setField(int id, int value) {}
    @Override public int getFieldCount(){ return 0; }
    @Override public void clear(){ for (int i=0;i<9;i++) view.set(i, ItemStack.EMPTY); }

    @Override public String getName(){ return "ghost_grid"; }
    @Override public boolean hasCustomName(){ return false; }
    @Override public ITextComponent getDisplayName(){ return new TextComponentString(getName()); }
}

