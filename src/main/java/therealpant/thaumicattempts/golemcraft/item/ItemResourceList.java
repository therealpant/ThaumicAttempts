package therealpant.thaumicattempts.golemcraft.item;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.client.gui.GuiHandler;

/**
 * «Список сырья» — упрощённый шаблон без превью результата.
 * Хранит 3×3 сетку предметов с фактическим количеством и используется
 * для заказа ресурсов без попытки вычислить рецепт.
 */
public class ItemResourceList extends ItemBasePattern {

    public ItemResourceList() {
        setMaxStackSize(1);
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
        setTranslationKey(ThaumicAttempts.MODID + ".resource_list");
    }

    private static void ensureNBT(ItemStack stack) {
        if (!stack.isEmpty() && !stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
    }

    /**
     * Прочитать сетку 3×3 из NBT предмета в «призрачный» инвентарь.
     */
    public static void readGridToInventory(ItemStack pattern, IInventory ghostInv) {
        ensureNBT(pattern);
        NBTTagCompound tag = pattern.getTagCompound();
        NBTTagList list = (tag == null) ? new NBTTagList() : tag.getTagList(TAG_GRID, Constants.NBT.TAG_COMPOUND);

        for (int i = 0; i < 9; i++) ghostInv.setInventorySlotContents(i, ItemStack.EMPTY);

        int max = Math.min(9, list.tagCount());
        for (int i = 0; i < max; i++) {
            NBTTagCompound st = list.getCompoundTagAt(i);
            if (!st.getKeySet().isEmpty()) {
                ItemStack s = new ItemStack(st);
                if (!s.isEmpty()) ghostInv.setInventorySlotContents(i, s);
            }
        }
    }

    /**
     * Записать «призрачный» инвентарь обратно в предмет (с сохранением количества).
     */
    public static void writeInventoryToStack(ItemStack pattern, NonNullList<ItemStack> grid) {
        ensureNBT(pattern);
        NBTTagCompound tag = pattern.getTagCompound();
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < grid.size(); i++) {
            ItemStack s = grid.get(i);
            NBTTagCompound st = new NBTTagCompound();
            if (!s.isEmpty()) {
                ItemStack copy = s.copy();
                copy.writeToNBT(st);
            }
            list.appendTag(st);
        }
        tag.setTag(TAG_GRID, list);
    }

    /** Прочитать сетку 3×3 как список ItemStack (количество сохраняется). */
    public static NonNullList<ItemStack> readGrid(ItemStack pattern) {
        NonNullList<ItemStack> grid = NonNullList.withSize(9, ItemStack.EMPTY);
        NBTTagCompound tag = pattern.getTagCompound();
        if (tag == null || !tag.hasKey(TAG_GRID, Constants.NBT.TAG_LIST)) return grid;
        NBTTagList list = tag.getTagList(TAG_GRID, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < Math.min(9, list.tagCount()); i++) {
            NBTTagCompound st = list.getCompoundTagAt(i);
            if (!st.getKeySet().isEmpty()) {
                ItemStack s = new ItemStack(st);
                if (!s.isEmpty()) grid.set(i, s);
            }
        }
        return grid;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!world.isRemote) {
            player.openGui(ThaumicAttempts.INSTANCE, GuiHandler.GUI_CRAFT_PATTERN, world, 0, 0, 0);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
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