// src/main/java/.../item/pattern/ItemBasePattern.java
package therealpant.thaumicattempts.golemcraft.item;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.common.util.Constants;
import therealpant.thaumicattempts.api.IPatternEssentiaCost;

import java.util.List;

public abstract class ItemBasePattern extends Item implements IPatternEssentiaCost {
    public static final String TAG_GRID = "Grid";
    public static final String TAG_REPEAT = "Repeat";
    private static final int SLOT_LABEL_LIMIT = 14;
    private static final int ICON_PLACEHOLDER_WIDTH = 36;
    private static final int ICON_ROW_LINE_MULTIPLIER = 2;

    public static NonNullList<ItemStack> readGrid(ItemStack pattern) {
        NonNullList<ItemStack> grid = NonNullList.withSize(9, ItemStack.EMPTY);
        NBTTagCompound tag = pattern.getTagCompound();
        if (tag == null || !tag.hasKey(TAG_GRID, Constants.NBT.TAG_LIST)) return grid;
        NBTTagList list = tag.getTagList(TAG_GRID, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < Math.min(9, list.tagCount()); i++) {
            NBTTagCompound c = list.getCompoundTagAt(i);
            if (!c.getKeySet().isEmpty()) grid.set(i, new ItemStack(c));
        }
        return grid;
    }

    /** Подсчитать «разные типы предметов» в сетке (по item+meta+nbt). */
    protected static int distinctCount(NonNullList<ItemStack> grid) {
        java.util.HashSet<String> sig = new java.util.HashSet<>();
        for (ItemStack s : grid) {
            if (s.isEmpty()) continue;
            String key = s.getItem().getRegistryName() + "|" + s.getMetadata() + "|" +
                    (s.hasTagCompound() ? s.getTagCompound().toString() : "");
            sig.add(key);
        }
        return Math.max(1, sig.size());
    }

    @SideOnly(Side.CLIENT)
    protected static void addPatternPreviewTooltip(List<String> tooltip, NonNullList<ItemStack> grid, ItemStack result) {
        while (grid.size() < 9) {
            grid.add(ItemStack.EMPTY);
        }

        String resultLabel = I18n.format("ta.tooltip.result", result.isEmpty()
                ? I18n.format("ta.tooltip.result.unknown")
                : result.getDisplayName());

        String padding = repeat(' ', resultLabel.length());
        String row1 = formatRow(grid, 0);
        String row2 = formatRow(grid, 1);
        String row3 = formatRow(grid, 2);

        tooltip.add(resultLabel + "   " + row1);
        tooltip.add(padding + "   " + row2);
        tooltip.add(padding + "   " + row3);
    }

    @SideOnly(Side.CLIENT)
    protected static boolean hasAnyStack(NonNullList<ItemStack> grid) {
        for (ItemStack stack : grid) {
            if (stack != null && !stack.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @SideOnly(Side.CLIENT)
    protected static void addIconPreviewLines(List<String> tooltip, int iconRows) {
        int lines = Math.max(0, iconRows) * ICON_ROW_LINE_MULTIPLIER;
        if (lines == 0) return;

        String spacer = repeat(' ', ICON_PLACEHOLDER_WIDTH);
        for (int i = 0; i < lines; i++) {
            tooltip.add(spacer);
        }
    }

    private static String formatRow(NonNullList<ItemStack> grid, int rowIdx) {
        StringBuilder builder = new StringBuilder();
        for (int col = 0; col < 3; col++) {
            if (col > 0) builder.append(" | ");
            builder.append(formatSlot(grid.get(rowIdx * 3 + col)));
        }
        return builder.toString();
    }

    private static String formatSlot(ItemStack stack) {
        String name = stack == null || stack.isEmpty()
                ? I18n.format("ta.tooltip.slot.empty")
                : stack.getDisplayName();

        if (name.length() > SLOT_LABEL_LIMIT) {
            name = name.substring(0, SLOT_LABEL_LIMIT - 1) + "…";
        }
        return '[' + name + ']';
    }

    private static String repeat(char c, int count) {
        StringBuilder builder = new StringBuilder(Math.max(count, 0));
        for (int i = 0; i < count; i++) {
            builder.append(c);
        }
        return builder.toString();
    }

    public static int getRepeatCount(ItemStack pattern) {
        if (pattern == null || pattern.isEmpty()) return 1;
        NBTTagCompound tag = pattern.getTagCompound();
        int raw = (tag != null && tag.hasKey(TAG_REPEAT, Constants.NBT.TAG_INT))
                ? tag.getInteger(TAG_REPEAT) : 1;
        return MathHelper.clamp(raw, 1, 64);
    }

    public static int setRepeatCount(ItemStack pattern, int value) {
        if (pattern == null || pattern.isEmpty()) return 1;
        int clamped = MathHelper.clamp(value, 1, 64);
        if (!pattern.hasTagCompound()) pattern.setTagCompound(new NBTTagCompound());
        pattern.getTagCompound().setInteger(TAG_REPEAT, clamped);
        return clamped;
    }

    public static int adjustRepeatCount(ItemStack pattern, int delta) {
        int current = getRepeatCount(pattern);
        return setRepeatCount(pattern, current + delta);
    }
}
