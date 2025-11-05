package therealpant.thaumicattempts.golemcraft;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;

public class GhostGrid {
    public final NonNullList<ItemStack> ghosts = NonNullList.withSize(9, ItemStack.EMPTY);

    public void set(int idx, ItemStack stack){
        ghosts.set(idx, stack.isEmpty()? ItemStack.EMPTY : stack.copy());
    }
    public ItemStack get(int idx){ return ghosts.get(idx); }

    public void writeTo(NBTTagCompound tag){
        if (tag == null) return;
        for (int i=0;i<9;i++){
            NBTTagCompound s = new NBTTagCompound();
            ItemStack is = ghosts.get(i);
            if (!is.isEmpty()) is.writeToNBT(s);
            tag.setTag("g"+i, s);
        }
    }

    public static GhostGrid readFrom(NBTTagCompound tag){
        GhostGrid g = new GhostGrid();
        if (tag != null){
            for (int i=0;i<9;i++){
                NBTTagCompound s = tag.getCompoundTag("g"+i);
                // Надёжная проверка на «пустой стек» — у сериализованного ItemStack обязателен ключ "id"
                ItemStack is = (s == null || !s.hasKey("id", 8)) ? ItemStack.EMPTY : new ItemStack(s);
                g.ghosts.set(i, is);
            }
        }
        return g;
    }
}
