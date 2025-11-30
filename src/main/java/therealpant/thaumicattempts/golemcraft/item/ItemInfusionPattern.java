package therealpant.thaumicattempts.golemcraft.item;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.api.IPatternResourceProvider;
import therealpant.thaumicattempts.api.PatternResourceList;
import therealpant.thaumicattempts.client.gui.GuiHandler;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Шаблон инфузии: запоминает предметы в порядке добавления (первый в центре,
 * остальные — по радиусу), но умеет показывать превью результата обычного
 * верстачного рецепта из полученной девятки.
 */
public class ItemInfusionPattern extends ItemBasePattern implements IPatternResourceProvider {
    /** Ключ NBT для последовательности предметов (без жёсткого лимита в 9). */
    public static final String TAG_SEQUENCE = "Grid";
    public static final String TAG_RESULT   = "Result";

    /** Максимальное число записанных предметов (перестраховка против бесконечных списков). */
    public static final int MAX_ORDER = 32;

    /** Порядок размещения предметов в сетке 3×3 для превью (первый — центр). */
    private static final int[] ORDER_TO_GRID = {4, 1, 5, 7, 3, 0, 2, 6, 8};

    public ItemInfusionPattern() {
        setMaxStackSize(1);
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
        setTranslationKey(ThaumicAttempts.MODID + ".infusion_pattern");
    }

    private static void ensureNBT(ItemStack stack) {
        if (!stack.isEmpty() && !stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
    }

    /** Читает записанную последовательность в «призрачный» инвентарь. */
    public static void readOrderToInventory(ItemStack pattern, IInventory ghostInv, int limit) {
        ensureNBT(pattern);
        NBTTagCompound tag = pattern.getTagCompound();
        NBTTagList list = (tag == null) ? new NBTTagList() : tag.getTagList(TAG_SEQUENCE, Constants.NBT.TAG_COMPOUND);

        int target = Math.min(limit, Math.max(0, ghostInv.getSizeInventory() - 1));
        for (int i = 0; i < target; i++) ghostInv.setInventorySlotContents(i, ItemStack.EMPTY);

        for (int i = 0; i < Math.min(target, list.tagCount()); i++) {
            NBTTagCompound st = list.getCompoundTagAt(i);
            if (!st.getKeySet().isEmpty()) {
                ItemStack s = new ItemStack(st);
                if (!s.isEmpty()) {
                    s.setCount(1);
                    ghostInv.setInventorySlotContents(i, s);
                }
            }
        }
    }

    /** Совместимость для старых вызовов без лимита. */
    public static void readOrderToInventory(ItemStack pattern, IInventory ghostInv) {
        readOrderToInventory(pattern, ghostInv, MAX_ORDER);
    }

    /** Прочитать последовательность как список. */
    public static NonNullList<ItemStack> readOrder(ItemStack pattern) {
        NonNullList<ItemStack> order = NonNullList.create();
        NBTTagCompound tag = pattern.getTagCompound();
        if (tag == null || !tag.hasKey(TAG_SEQUENCE, Constants.NBT.TAG_LIST)) return order;

        NBTTagList list = tag.getTagList(TAG_SEQUENCE, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < Math.min(MAX_ORDER, list.tagCount()); i++) {
            NBTTagCompound st = list.getCompoundTagAt(i);
            ItemStack s = st.getKeySet().isEmpty() ? ItemStack.EMPTY : new ItemStack(st);
            order.add(s.isEmpty() ? ItemStack.EMPTY : s);
        }
        return order;
    }

    /** Записать последовательность и результат обратно в предмет. */
    public static void writeInventoryToStack(ItemStack pattern, NonNullList<ItemStack> order, ItemStack result) {
        ensureNBT(pattern);
        NBTTagCompound tag = pattern.getTagCompound();

        // --- сохраняем последовательность (как было) ---
        NBTTagList list = new NBTTagList();
        int limit = Math.min(MAX_ORDER, order.size());
        int lastNonEmpty = -1;
        for (int i = 0; i < limit; i++) {
            if (!order.get(i).isEmpty()) lastNonEmpty = i;
        }

        for (int i = 0; i <= lastNonEmpty; i++) {
            ItemStack s = order.get(i);
            NBTTagCompound st = new NBTTagCompound();
            if (!s.isEmpty()) {
                ItemStack one = s.copy();
                one.setCount(1);
                one.writeToNBT(st);
            }
            list.appendTag(st);
        }
        tag.setTag(TAG_SEQUENCE, list);

        // --- сохраняем результат из превью-слота ---
        if (result != null && !result.isEmpty()) {
            NBTTagCompound rt = new NBTTagCompound();
            ItemStack one = result.copy();
            one.setCount(1);
            one.writeToNBT(rt);
            tag.setTag(TAG_RESULT, rt);
        } else {
            // если результата нет – очищаем тег
            tag.removeTag(TAG_RESULT);
        }
    }
    /** Прочитать результат крафта, сохранённый в предмете. */
    public static ItemStack readResult(ItemStack pattern) {
        if (pattern == null || pattern.isEmpty()) return ItemStack.EMPTY;
        NBTTagCompound tag = pattern.getTagCompound();
        if (tag == null || !tag.hasKey(TAG_RESULT, Constants.NBT.TAG_COMPOUND)) return ItemStack.EMPTY;

        NBTTagCompound rt = tag.getCompoundTag(TAG_RESULT);
        if (rt.getKeySet().isEmpty()) return ItemStack.EMPTY;

        return new ItemStack(rt);
    }

    /** Конвертирует последовательность в сетку 3×3 (для превью рецепта). */
    public static NonNullList<ItemStack> orderToGrid(NonNullList<ItemStack> order) {
        NonNullList<ItemStack> grid = NonNullList.withSize(ORDER_TO_GRID.length, ItemStack.EMPTY);
        for (int i = 0; i < ORDER_TO_GRID.length; i++) {
            int targetIdx = ORDER_TO_GRID[i];
            if (i < order.size() && targetIdx >= 0 && targetIdx < grid.size()) {
                grid.set(targetIdx, order.get(i));
            }
        }
        return grid;
    }

    /** Считает превью результата по текущей последовательности (читает сохранённый результат). */
    public static ItemStack calcResultPreview(ItemStack pattern, World world) {
        if (pattern == null || pattern.isEmpty()) return ItemStack.EMPTY;
        return readResult(pattern);
    }


    @Override
    public List<PatternResourceList.Entry> buildResourceList(ItemStack pattern) {
        return PatternResourceList.aggregate(readOrder(pattern), false);
    }

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
        NonNullList<ItemStack> order = readOrder(stack);
        ItemStack out = ctx == null ? ItemStack.EMPTY : calcResultPreview(stack, ctx);
        boolean hasRecipe = hasAnyStack(order) || !out.isEmpty();

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
        return distinctCount(readOrder(pattern));
    }
}