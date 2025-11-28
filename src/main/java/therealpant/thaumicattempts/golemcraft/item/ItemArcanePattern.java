package therealpant.thaumicattempts.golemcraft.item;

import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.crafting.IArcaneRecipe;
import thaumcraft.api.crafting.IArcaneWorkbench;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.items.ItemTCEssentiaContainer;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.client.gui.GuiHandler;
import therealpant.thaumicattempts.util.ArcaneRecipeHelper;

import java.util.List;

public class ItemArcanePattern extends ItemBasePattern {

    /** Ключ NBT: массив из 6 целых под кристаллы [AER,TERRA,IGNIS,AQUA,ORDO,PERDITIO]. */
    public static final String TAG_CRYSTALS = "ArcCrystals";
    public static final String TAG_GRID     = "Grid";        // дублируем, чтобы не тянуть ItemCraftPattern
    public static final String TAG_RESULT   = "Result";

    public static final Aspect[] PRIMALS = new Aspect[] {
            Aspect.AIR, Aspect.EARTH, Aspect.FIRE, Aspect.WATER, Aspect.ORDER, Aspect.ENTROPY
    };

    public ItemArcanePattern() {
        setMaxStackSize(1);
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
        setTranslationKey(ThaumicAttempts.MODID + ".arcane_pattern");
    }

    /** Получить массив количества кристаллов из NBT (всегда длина 6). */
    public static int[] getCrystalCounts(ItemStack stack) {
        int[] arr = new int[6];
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null && tag.hasKey(TAG_CRYSTALS, Constants.NBT.TAG_INT_ARRAY)) {
            int[] saved = tag.getIntArray(TAG_CRYSTALS);
            for (int i = 0; i < Math.min(6, saved.length); i++) arr[i] = Math.max(0, saved[i]);
        }
        return arr;
    }

    /** Записать массив количества кристаллов в NBT. */
    public static void setCrystalCounts(ItemStack stack, int[] counts) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        int[] out = new int[6];
        if (counts != null) {
            for (int i = 0; i < Math.min(6, counts.length); i++) out[i] = Math.max(0, counts[i]);
        }
        stack.getTagCompound().setIntArray(TAG_CRYSTALS, out);
    }

    public static void setResultPreview(ItemStack pattern, @Nullable ItemStack result) {
        ensureNBT(pattern);
        NBTTagCompound tag = pattern.getTagCompound();
        if (result == null || result.isEmpty()) {
            tag.removeTag(TAG_RESULT);
        } else {
            NBTTagCompound r = new NBTTagCompound();
            result.copy().writeToNBT(r);
            tag.setTag(TAG_RESULT, r);
        }
    }

    public static boolean isCrystalOfAspect(ItemStack stack, Aspect aspect) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.getItem() != ItemsTC.crystalEssence) return false;

        AspectList al = ((ItemTCEssentiaContainer) ItemsTC.crystalEssence).getAspects(stack);
        return al != null && aspect != null && al.getAmount(aspect) > 0;
    }

    public static ItemStack getResultPreview(ItemStack pattern) {
        NBTTagCompound tag = pattern.getTagCompound();
        if (tag != null && tag.hasKey(TAG_RESULT, Constants.NBT.TAG_COMPOUND)) {
            ItemStack s = new ItemStack(tag.getCompoundTag(TAG_RESULT));
            return s == null ? ItemStack.EMPTY : s;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!world.isRemote) {
            player.openGui(ThaumicAttempts.INSTANCE, GuiHandler.GUI_ARCANE_PATTERN, world, 0, 0, 0);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    // читаем сетку 3x3 из NBT (локальная утилита)
    private static ItemStack[] readGrid3x3FromPattern(ItemStack pattern) {
        NBTTagCompound tag = pattern.getTagCompound();
        if (tag == null) return null;
        if (tag.hasKey(TAG_GRID, Constants.NBT.TAG_LIST)) {
            NBTTagList list = tag.getTagList(TAG_GRID, Constants.NBT.TAG_COMPOUND);
            ItemStack[] grid = new ItemStack[9];
            for (int i = 0; i < 9; i++) grid[i] = ItemStack.EMPTY;
            for (int i = 0; i < Math.min(9, list.tagCount()); i++) {
                NBTTagCompound s = list.getCompoundTagAt(i);
                if (!s.getKeySet().isEmpty()) grid[i] = new ItemStack(s);
            }
            return grid;
        }
        return null;
    }

    // === Стоимость эссенции для нашего менеджера ===
    @Override
    public int getEssentiaCost(ItemStack pattern, World world) {
        int vis = findVisCost(pattern, world);
        if (vis <= 0) vis = fallbackFromCrystals(pattern); // если рецепт не найден, минималка
        int aurum = (vis + 4) / 5; // ceil(vis/5)
        return Math.max(1, aurum);
    }

    /** Подсказка: показываем потенциальный результат (превью). */
    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        World ctx = world == null ? Minecraft.getMinecraft().world : world;
        NonNullList<ItemStack> grid = readGrid(stack);
        ItemStack out = ctx == null ? ItemStack.EMPTY : calcArcaneResultPreview(stack, ctx);
        boolean hasRecipe = hasAnyStack(grid) || !out.isEmpty();
        int[] crystals = getCrystalCounts(stack);
        boolean hasCrystals = false;
        for (int v : crystals) { if (v > 0) { hasCrystals = true; break; } }

        if (GuiScreen.isShiftKeyDown() && hasRecipe) {
            tooltip.add(I18n.format("ta.tooltip.result", out.isEmpty()
                    ? I18n.format("ta.tooltip.result.unknown")
                    : out.getDisplayName()));
            addIconPreviewLines(tooltip, 3 + (hasCrystals ? 1 : 0));
        } else if (!out.isEmpty()) {
            tooltip.add(I18n.format("ta.tooltip.result", out.getDisplayName()));
            tooltip.add(I18n.format("ta.tooltip.hold_shift"));
        } else if (hasRecipe) {
            tooltip.add(I18n.format("ta.tooltip.hold_shift"));
        }
    }

    private int findVisCost(ItemStack pattern, World world) {
        if (pattern == null || pattern.isEmpty() || world == null) return 0;

        // 1) читаем сетку 3x3 и нормализуем размер до 9
        NonNullList<ItemStack> grid = readGrid(pattern);
        if (grid == null) {
            grid = NonNullList.withSize(9, ItemStack.EMPTY);
        } else if (grid.size() != 9) {
            NonNullList<ItemStack> fixed = NonNullList.withSize(9, ItemStack.EMPTY);
            for (int i = 0; i < Math.min(9, grid.size()); i++) fixed.set(i, grid.get(i));
            grid = fixed;
        }

        // 2) пробуем найти аркановый рецепт через ваш хелпер
        ArcaneRecipeHelper.ArcaneMatch m = ArcaneRecipeHelper.findArcane(grid::get, world);
        return (m != null && m.visCost > 0) ? m.visCost : 0;
    }


    private int fallbackFromCrystals(ItemStack pattern) {
        // Хочешь — суммируй TAG_CRYSTALS. Пока минималка 5 vis => 1 Aurum.
        return 5;
    }


    private static void ensureNBT(ItemStack stack) {
        if (!stack.isEmpty() && !stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
    }
    public static void readGridToInventory(ItemStack pattern, IInventory ghostInv) {
        ensureNBT(pattern);
        NBTTagCompound tag = pattern.getTagCompound();
        NBTTagList list = tag.getTagList(TAG_GRID, Constants.NBT.TAG_COMPOUND);

        for (int i = 0; i < 9; i++) ghostInv.setInventorySlotContents(i, ItemStack.EMPTY);

        for (int i = 0; i < Math.min(9, list.tagCount()); i++) {
            NBTTagCompound st = list.getCompoundTagAt(i);
            if (!st.getKeySet().isEmpty()) {
                ItemStack s = new ItemStack(st);
                if (!s.isEmpty()) {
                    s.setCount(1); // призрак
                    ghostInv.setInventorySlotContents(i, s);
                }
            }
        }
    }
    public static void writeInventoryToStack(ItemStack pattern, NonNullList<ItemStack> grid, ItemStack unusedResult) {
        ensureNBT(pattern);
        NBTTagCompound tag = pattern.getTagCompound();

        NBTTagList list = new NBTTagList();
        for (int i = 0; i < 9; i++) {
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

    // === ПРЕВЬЮ ДЛЯ АРКАНОВЫХ РЕЦЕПТОВ ===
    public static ItemStack calcArcaneResultPreview(ItemStack pattern, World world) {
        if (pattern == null || pattern.isEmpty() || world == null) return ItemStack.EMPTY;

        // читаем сетку 3x3 и счётчики кристаллов
        NonNullList<ItemStack> grid = readGrid(pattern);
        if (grid == null) grid = NonNullList.withSize(9, ItemStack.EMPTY);
        if (grid.size() != 9) {
            NonNullList<ItemStack> fixed = NonNullList.withSize(9, ItemStack.EMPTY);
            for (int i = 0; i < Math.min(9, grid.size()); i++) fixed.set(i, grid.get(i));
            grid = fixed;
        }
        int[] crystals = getCrystalCounts(pattern);

        // собираем аркановый инвентарь 5x3 (15 слотов) как у верстака: 0..8 — сетка, 9..14 — кристаллы
        ArcanePreviewInv inv = new ArcanePreviewInv();

        for (int i = 0; i < 9; i++) {
            ItemStack s = grid.get(i);
            inv.setInventorySlotContents(i, (s == null || s.isEmpty()) ? ItemStack.EMPTY : s.copy());
        }
        for (int i = 0; i < 6; i++) {
            int cnt = Math.max(0, i < crystals.length ? crystals[i] : 0);
            if (cnt > 0) {
                Aspect a = PRIMALS[i];
                // ThaumcraftApiHelper.makeCrystal(aspect, amount) — кристалл с нужным NBT и количеством
                ItemStack cs = ThaumcraftApiHelper.makeCrystal(a, cnt);
                inv.setInventorySlotContents(9 + i, cs);
            } else {
                inv.setInventorySlotContents(9 + i, ItemStack.EMPTY);
            }
        }

        // ищем ПЕРВЫЙ совпавший аркан-рецепт
        for (IRecipe r : ForgeRegistries.RECIPES) {
            if (r instanceof IArcaneRecipe) {
                if (r.matches(inv, world)) {
                    ItemStack out = r.getCraftingResult(inv);
                    return (out == null) ? ItemStack.EMPTY : out.copy();
                }
            }
        }
        return ItemStack.EMPTY;
    }

    // Вспомогательный 5×3 инвентарь, который годится для ArcaneWorkbench
    private static final Container DUMMY_CONT = new Container() {
        @Override public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer playerIn) { return false; }
    };

    private static final class ArcanePreviewInv extends InventoryCrafting implements IArcaneWorkbench {
        ArcanePreviewInv() { super(DUMMY_CONT, 5, 3); } // 5 * 3 = 15 слотов
    }
}
