package therealpant.thaumicattempts.api;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * Общие утилиты для интеграции блоков с Order Terminal.
 */
public final class TerminalOrderApi {
    private TerminalOrderApi() {}

    /** Тэг, внутри которого хранятся данные иконки. Совместим со старыми иконками RR. */
    public static final String TAG_ROOT = "thaumicattempts_rr";
    private static final String TAG_POS  = "Pos";
    private static final String TAG_SLOT = "Slot";
    private static final String TAG_BLOCK = "Block";

    public static ItemStack makeOrderIcon(ItemStack base, @Nullable Block block, BlockPos pos, int slot) {
        if (base == null || base.isEmpty() || pos == null) return ItemStack.EMPTY;

        ItemStack icon = base.copy();
        icon.setCount(1);

        NBTTagCompound tag = icon.hasTagCompound() ? icon.getTagCompound().copy() : new NBTTagCompound();
        NBTTagCompound inner = new NBTTagCompound();
        inner.setLong(TAG_POS, pos.toLong());
        inner.setInteger(TAG_SLOT, Math.max(0, slot));

        String blockId = (block == null || ForgeRegistries.BLOCKS.getKey(block) == null)
                ? ""
                : ForgeRegistries.BLOCKS.getKey(block).toString();

        inner.setString(TAG_BLOCK, blockId);

        tag.setTag(TAG_ROOT, inner);
        icon.setTagCompound(tag);
        return icon;
    }

    public static boolean isOrderIcon(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTagCompound()) return false;
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.hasKey(TAG_ROOT, Constants.NBT.TAG_COMPOUND);
    }

    /**
     * Возвращает копию стака без привязки к слоту/блоку терминала заказов.
     * Используется там, где нужна только визуальная часть иконки.
     */
    public static ItemStack stripOrderIconData(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTagCompound()) return stack;

        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(TAG_ROOT, Constants.NBT.TAG_COMPOUND)) return stack;

        ItemStack copy = stack.copy();
        NBTTagCompound copyTag = copy.getTagCompound();
        if (copyTag != null) {
            copyTag.removeTag(TAG_ROOT);
            // MCP 1.12: вместо hasNoTags()
            if (copyTag.getKeySet().isEmpty()) copy.setTagCompound(null);
        }
        return copy;
    }

    @Nullable
    public static BlockPos getOrderIconPos(ItemStack stack) {
        if (!isOrderIcon(stack)) return null;
        NBTTagCompound inner = stack.getTagCompound().getCompoundTag(TAG_ROOT);
        if (!inner.hasKey(TAG_POS, Constants.NBT.TAG_LONG)) return null;
        return BlockPos.fromLong(inner.getLong(TAG_POS));
    }

    public static int getOrderIconSlot(ItemStack stack) {
        if (!isOrderIcon(stack)) return -1;
        NBTTagCompound inner = stack.getTagCompound().getCompoundTag(TAG_ROOT);
        return inner.getInteger(TAG_SLOT);
    }

    public static boolean isOrderIconFor(ItemStack stack, Block block) {
        if (!isOrderIcon(stack) || block == null) return false;
        NBTTagCompound inner = stack.getTagCompound().getCompoundTag(TAG_ROOT);
        String stored = inner.getString(TAG_BLOCK);
        return stored.isEmpty() || ForgeRegistries.BLOCKS.getKey(block).toString().equals(stored);
    }
}