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
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.client.gui.GuiHandler;
import therealpant.thaumicattempts.util.InfusionRecipeHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Паттерн для линейного списка предметов. Первый слот считается центром,
 * остальные располагаются по окружности.
 */
public class ItemDeliveryPattern extends ItemBasePattern {

    public static final String TAG_ITEMS = "Items";
    public static final int MAX_ENTRIES = 12;

    public ItemDeliveryPattern() {
        setMaxStackSize(1);
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
        setTranslationKey(ThaumicAttempts.MODID + ".delivery_pattern");
    }

    private static void ensureNBT(ItemStack stack) {
        if (!stack.isEmpty() && !stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
    }

    public static NonNullList<ItemStack> readList(ItemStack pattern) {
        NonNullList<ItemStack> items = NonNullList.withSize(MAX_ENTRIES, ItemStack.EMPTY);
        NBTTagCompound tag = pattern.getTagCompound();
        if (tag == null || !tag.hasKey(TAG_ITEMS, Constants.NBT.TAG_LIST)) return items;
        NBTTagList list = tag.getTagList(TAG_ITEMS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < Math.min(items.size(), list.tagCount()); i++) {
            NBTTagCompound st = list.getCompoundTagAt(i);
            if (!st.getKeySet().isEmpty()) {
                ItemStack s = new ItemStack(st);
                if (!s.isEmpty()) items.set(i, s);
            }
        }
        return items;
    }

    public static void readListToInventory(ItemStack pattern, IInventory inv) {
        NonNullList<ItemStack> src = readList(pattern);
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            inv.setInventorySlotContents(i, i < src.size() ? src.get(i) : ItemStack.EMPTY);
        }
    }

    public static void writeInventoryToStack(ItemStack pattern, IInventory inv) {
        ensureNBT(pattern);
        NBTTagCompound tag = pattern.getTagCompound();
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < Math.min(inv.getSizeInventory(), MAX_ENTRIES); i++) {
            ItemStack s = inv.getStackInSlot(i);
            NBTTagCompound st = new NBTTagCompound();
            if (s != null && !s.isEmpty()) {
                ItemStack copy = s.copy();
                copy.writeToNBT(st);
            }
            list.appendTag(st);
        }
        tag.setTag(TAG_ITEMS, list);
    }

    public static ItemStack getPreview(ItemStack pattern) {
        NonNullList<ItemStack> list = readList(pattern);
        for (ItemStack s : list) {
            if (s == null || s.isEmpty()) continue;
            ItemStack copy = s.copy();
            if (copy.getCount() <= 0) copy.setCount(1);
            return copy;
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack getInfusionPreview(ItemStack pattern, World world) {
        if (pattern == null || pattern.isEmpty()) return ItemStack.EMPTY;

        NonNullList<ItemStack> list = readList(pattern);
        ItemStack center = ItemStack.EMPTY;
        List<ItemStack> others = new ArrayList<>();
        for (ItemStack s : list) {
            if (s == null || s.isEmpty()) continue;
            if (center.isEmpty()) center = s;
            else others.add(s);
        }

        return InfusionRecipeHelper.findResult(center, others, world);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!world.isRemote) {
            player.openGui(ThaumicAttempts.INSTANCE, GuiHandler.GUI_DELIVERY_PATTERN, world, 0, 0, 0);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        ItemStack stack = player.getHeldItem(hand);
        if (!world.isRemote) {
            player.openGui(ThaumicAttempts.INSTANCE, GuiHandler.GUI_DELIVERY_PATTERN, world, 0, 0, 0);
        }
        return EnumActionResult.SUCCESS;
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX, float hitY, float hitZ, EnumHand hand) {
        if (!world.isRemote) {
            player.openGui(ThaumicAttempts.INSTANCE, GuiHandler.GUI_DELIVERY_PATTERN, world, 0, 0, 0);
        }
        return EnumActionResult.SUCCESS;
    }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (!isInCreativeTab(tab)) return;
        items.add(new ItemStack(this));
    }

    @Override
    public int getEssentiaCost(ItemStack pattern, World world) {
        return distinctCount(readList(pattern));
    }
}