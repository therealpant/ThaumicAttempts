package therealpant.thaumicattempts.golemcraft.item;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.client.gui.GuiHandler;

import javax.annotation.Nullable;
import java.util.List;

public class ItemCraftPattern extends ItemBasePattern {

    /** Ключ NBT для сетки 3×3 (список из 9 TAG_Compound). */
    public static final String TAG_GRID = "Grid";

    public static final int GRID_SIZE = 9;

    public ItemCraftPattern() {
        setMaxStackSize(1);
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
        setTranslationKey(ThaumicAttempts.MODID + ".craft_pattern");
    }

    private static void ensureNBT(ItemStack stack) {
        if (!stack.isEmpty() && !stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
    }

    /** Читает 3×3 сетку из NBT шаблона в переданный "призрачный" инвентарь (ячейки 0..8). */
    public static void readGridToInventory(ItemStack pattern, IInventory ghostInv) {
        ensureNBT(pattern);
        NBTTagCompound tag = pattern.getTagCompound();
        NBTTagList list = tag.getTagList(TAG_GRID, Constants.NBT.TAG_COMPOUND);

        for (int i = 0; i < GRID_SIZE; i++) ghostInv.setInventorySlotContents(i, ItemStack.EMPTY);

        for (int i = 0; i < Math.min(GRID_SIZE, list.tagCount()); i++) {
            NBTTagCompound st = list.getCompoundTagAt(i);
            // вместо hasNoTags():
            if (!st.getKeySet().isEmpty()) {
                ItemStack s = new ItemStack(st);
                if (!s.isEmpty()) {
                    s.setCount(1); // призрачная копия
                    ghostInv.setInventorySlotContents(i, s);
                }
            }
        }
    }

    /** Считает превью результата по текущей сетке из NBT шаблона. */
    public static ItemStack calcResultPreview(ItemStack pattern, World world) {
        if (pattern == null || pattern.isEmpty() || world == null) return ItemStack.EMPTY;

        ItemStack[] grid = readGrid3x3FromPattern(pattern);
        if (grid == null) return ItemStack.EMPTY;

        InventoryCrafting inv = new InventoryCrafting(new Container() {
            @Override public boolean canInteractWith(EntityPlayer playerIn) { return false; }
        }, 3, 3);

        for (int i = 0; i < 9; i++) {
            inv.setInventorySlotContents(i, grid[i] == null ? ItemStack.EMPTY : grid[i]);
        }

        ItemStack out = CraftingManager.findMatchingResult(inv, world);
        return (out == null) ? ItemStack.EMPTY : out;
    }

    /** Читает сетку 3×3 из NBT шаблона. Возвращает null, если ничего не найдено. */
    private static ItemStack[] readGrid3x3FromPattern(ItemStack pattern) {
        NBTTagCompound tag = pattern.getTagCompound();
        if (tag == null) return null;

        if (tag.hasKey(TAG_GRID, Constants.NBT.TAG_LIST)) {
            NBTTagList list = tag.getTagList(TAG_GRID, Constants.NBT.TAG_COMPOUND);
            ItemStack[] grid = new ItemStack[9];
            for (int i = 0; i < 9; i++) grid[i] = ItemStack.EMPTY;

            for (int i = 0; i < Math.min(9, list.tagCount()); i++) {
                NBTTagCompound s = list.getCompoundTagAt(i);
                // вместо hasNoTags():
                if (!s.getKeySet().isEmpty()) {
                    grid[i] = new ItemStack(s);
                }
            }
            return grid;
        }

        // фоллбэк-формат "g0".."g8" (если когда-то использовался)
        boolean any = false;
        ItemStack[] grid = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            String key = "g" + i;
            if (tag.hasKey(key, Constants.NBT.TAG_COMPOUND)) {
                grid[i] = new ItemStack(tag.getCompoundTag(key));
                any = true;
            } else {
                grid[i] = ItemStack.EMPTY;
            }
        }
        return any ? grid : null;
    }

    /** Записывает сетку 3×3 обратно в NBT шаблона. */
    public static void writeInventoryToStack(ItemStack pattern, NonNullList<ItemStack> grid, ItemStack unusedResult) {
        ensureNBT(pattern);
        NBTTagCompound tag = pattern.getTagCompound();

        NBTTagList list = new NBTTagList();
        for (int i = 0; i < GRID_SIZE; i++) {
            ItemStack s = (i < grid.size()) ? grid.get(i) : ItemStack.EMPTY;
            NBTTagCompound st = new NBTTagCompound();
            if (!s.isEmpty()) {
                ItemStack one = s.copy();
                one.setCount(1);
                one.writeToNBT(st);
            }
            list.appendTag(st);
        }
        tag.setTag(TAG_GRID, list);
    }

    /** ПКМ по воздуху — открыть GUI редактирования шаблона. */
    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!world.isRemote) {
            player.openGui(ThaumicAttempts.INSTANCE, GuiHandler.GUI_CRAFT_PATTERN, world, 0, 0, 0);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        World ctx = world == null ? Minecraft.getMinecraft().world : world;
        NonNullList<ItemStack> grid = readGrid(stack);
        ItemStack out = ctx == null ? ItemStack.EMPTY : calcResultPreview(stack, ctx);
        boolean hasRecipe = hasAnyStack(grid) || !out.isEmpty();

        if (GuiScreen.isShiftKeyDown() && hasRecipe) {
            tooltip.add(I18n.format("ta.tooltip.result", out.isEmpty()
                    ? I18n.format("ta.tooltip.result.unknown")
                    : out.getDisplayName()));
            addIconPreviewLines(tooltip, 3);
        } else if (!out.isEmpty()) {
            tooltip.add(I18n.format("ta.tooltip.result", out.getDisplayName()));
            tooltip.add(I18n.format("ta.tooltip.hold_shift"));
        } else if (hasRecipe) {
            tooltip.add(I18n.format("ta.tooltip.hold_shift"));
        }
    }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (!isInCreativeTab(tab)) return;
        items.add(new ItemStack(this));
    }

    @Override
    public int getEssentiaCost(ItemStack pattern, World world) {
        return distinctCount(readGrid(pattern));
    }
}
