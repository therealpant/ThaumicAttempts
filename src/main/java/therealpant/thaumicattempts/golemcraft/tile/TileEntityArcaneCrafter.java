package therealpant.thaumicattempts.golemcraft.tile;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.items.ItemStackHandler;
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
    protected void rebuildSequence(ItemStack pattern, NonNullList<ItemStack> grid) {
        super.rebuildSequence(pattern, grid);
        int[] crystCounts = ItemArcanePattern.getCrystalCounts(pattern);
        Aspect[] primals  = ItemArcanePattern.PRIMALS;
        for (int i = 0; i < 6; i++) {
            int need = (crystCounts != null && i < crystCounts.length) ? Math.max(0, crystCounts[i]) : 0;
            if (need <= 0) continue;
            ItemStack key1 = ThaumcraftApiHelper.makeCrystal(primals[i], 1);
            this.seq.add(new Req(key1, need));
        }
    }

    @Override
    protected boolean hasAllForGrid(NonNullList<ItemStack> grid) {
        if (!super.hasAllForGrid(grid)) return false;

        ItemStack pat = (this.jobPatternIndex >= 0 && this.jobPatternIndex < PATTERN_SLOTS)
                ? this.patterns.getStackInSlot(this.jobPatternIndex) : ItemStack.EMPTY;
        if (pat.isEmpty()) return false;

        int[] crystCounts = ItemArcanePattern.getCrystalCounts(pat);
        Aspect[] primals  = ItemArcanePattern.PRIMALS;

        for (int i = 0; i < 6; i++) {
            int need = (crystCounts != null && i < crystCounts.length) ? Math.max(0, crystCounts[i]) : 0;
            if (need <= 0) continue;

            ItemStack key1 = ThaumcraftApiHelper.makeCrystal(primals[i], 1);
            if (countInInput(key1) < need) return false;
        }
        return true;
    }

    @Override
    protected void consumeForGrid(NonNullList<ItemStack> grid) {
        super.consumeForGrid(grid);

        ItemStack pat = (this.jobPatternIndex >= 0 && this.jobPatternIndex < PATTERN_SLOTS)
                ? this.patterns.getStackInSlot(this.jobPatternIndex) : ItemStack.EMPTY;
        if (pat.isEmpty()) return;

        int[] crystCounts = ItemArcanePattern.getCrystalCounts(pat);
        Aspect[] primals  = ItemArcanePattern.PRIMALS;

        // возьмём writable handler (см. базовый класс — либо getInputHandlerWritable(), либо protected input)
        ItemStackHandler ih = (this instanceof TileEntityGolemCrafter)
                ? (this.getInputHandler() instanceof ItemStackHandler
                ? (ItemStackHandler) this.getInputHandler()
                : null)
                : null;
        // Если у вас есть метод getInputHandlerWritable(), замените строку выше на:
        // ItemStackHandler ih = getInputHandlerWritable();

        if (ih == null) return;

        for (int i = 0; i < 6; i++) {
            int left = (crystCounts != null && i < crystCounts.length) ? Math.max(0, crystCounts[i]) : 0;
            if (left <= 0) continue;

            ItemStack key1 = ThaumcraftApiHelper.makeCrystal(primals[i], 1);
            for (int si = 0; si < ih.getSlots() && left > 0; si++) {
                ItemStack s = ih.getStackInSlot(si);
                if (s.isEmpty()) continue;
                // Совпадение предмета и NBT (аспекта)
                if (!ItemStack.areItemsEqual(s, key1) || !ItemStack.areItemStackTagsEqual(s, key1)) continue;

                int take = Math.min(left, s.getCount());
                ItemStack ns = s.copy();
                ns.shrink(take);
                ih.setStackInSlot(si, ns);
                left -= take;
            }
        }
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
