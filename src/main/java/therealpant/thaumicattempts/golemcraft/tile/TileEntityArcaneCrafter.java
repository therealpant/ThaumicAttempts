package therealpant.thaumicattempts.golemcraft.tile;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.crafting.IArcaneRecipe;

import therealpant.thaumicattempts.golemcraft.item.ItemArcanePattern;

public class TileEntityArcaneCrafter extends TileEntityGolemCrafter {

    public TileEntityArcaneCrafter() {
        super();
        this.requiredAspect = thaumcraft.api.aspects.Aspect.AURA; // как просили
    }

    @Override
    protected int getPerCraftCost(ItemStack pattern, NonNullList<ItemStack> grid) {
        if (world == null || pattern == null || pattern.isEmpty()) return 1;

        ArcaneWorkbenchDummy inv = new ArcaneWorkbenchDummy();
        for (int i = 0; i < 9; i++) inv.setInventorySlotContents(i, grid.get(i).copy());

        int[] crystCounts = ItemArcanePattern.getCrystalCounts(pattern);
        Aspect[] primals  = ItemArcanePattern.PRIMALS;
        for (int i = 0; i < 6; i++) {
            int cnt = (crystCounts != null && i < crystCounts.length) ? Math.max(0, crystCounts[i]) : 0;
            ItemStack cs = cnt > 0 ? ThaumcraftApiHelper.makeCrystal(primals[i], cnt) : ItemStack.EMPTY;
            inv.setInventorySlotContents(9 + i, cs);
        }

        // 1) ForgeRegistries
        for (IRecipe r : ForgeRegistries.RECIPES.getValuesCollection()) {
            if (r instanceof IArcaneRecipe && r.matches(inv, world)) {
                int vis = Math.max(0, ((IArcaneRecipe) r).getVis());
                return Math.max(1, (vis + 3) / 4);
            }
        }
        // 2) Fallback: CraftingManager
        for (IRecipe r : net.minecraft.item.crafting.CraftingManager.REGISTRY) {
            if (r instanceof IArcaneRecipe && r.matches(inv, world)) {
                int vis = Math.max(0, ((IArcaneRecipe) r).getVis());
                return Math.max(1, (vis + 3) / 4);
            }
        }
        return 1;
    }


    private static final class ArcaneWorkbenchDummy extends InventoryCrafting implements thaumcraft.api.crafting.IArcaneWorkbench {
        ArcaneWorkbenchDummy() {
            super(new net.minecraft.inventory.Container() { @Override public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer p){ return false; } }, 5, 3);
        }
    }
}
