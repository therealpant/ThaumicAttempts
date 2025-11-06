// src/main/java/.../item/pattern/ItemBasePattern.java
package therealpant.thaumicattempts.golemcraft.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import therealpant.thaumicattempts.api.IPatternEssentiaCost;

public abstract class ItemBasePattern extends Item implements IPatternEssentiaCost {
    public static final String TAG_GRID = "Grid";

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
}
