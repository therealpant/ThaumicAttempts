package therealpant.thaumicattempts.golemcraft;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;

public final class CraftingHelperTA {
    private CraftingHelperTA() {}

    /** Возвращает результат для переданной 3x3 сетки (как в верстаке). */
    public static ItemStack getResultForGrid(NonNullList<ItemStack> grid, World world) {
        if (grid == null || grid.size() < 9) return ItemStack.EMPTY;

        InventoryCrafting inv = new InventoryCrafting(new Container() {
            @Override public boolean canInteractWith(EntityPlayer playerIn) { return false; }
        }, 3, 3);

        for (int i = 0; i < 9; i++) {
            ItemStack s = grid.get(i);
            inv.setInventorySlotContents(i, s.isEmpty() ? ItemStack.EMPTY : s.copy());
        }

        // На 1.12 это универсальный способ получить результат рецепта:
        return CraftingManager.findMatchingResult(inv, world);
    }
}