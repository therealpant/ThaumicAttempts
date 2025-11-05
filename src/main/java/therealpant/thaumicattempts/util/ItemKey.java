// therealpant/thaumicattempts/golemnet/util/ItemKey.java
package therealpant.thaumicattempts.util;


import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import thaumcraft.api.items.ItemsTC;

import java.util.Objects;

public final class ItemKey {
    public final net.minecraft.item.Item item;
    public final int meta;
    public final NBTTagCompound tag; // может быть null

    public static final ItemKey EMPTY = new ItemKey(null, 0, null);

    public ItemKey(net.minecraft.item.Item item, int meta, NBTTagCompound tag) {
        this.item = item;
        this.meta = meta;
        this.tag = tag == null ? null : tag.copy();
    }

    public static ItemKey of( ItemStack s) {
        if (s == null || s.isEmpty()) return EMPTY;

        final Item it = s.getItem();

        // Таум-кристаллы: ключ = item + "чистый" NBT с одним аспектом (без лишних полей)
        if (it == ItemsTC.crystalEssence) {
            // Достаём тип аспекта, и создаём нормализованный кристалл через API
            thaumcraft.api.aspects.Aspect asp = null;
            try {
                thaumcraft.api.aspects.AspectList al =
                        ((thaumcraft.common.items.ItemTCEssentiaContainer) ItemsTC.crystalEssence).getAspects(s);
                if (al != null && al.size() == 1) asp = al.getAspects()[0];
            } catch (Throwable ignored) {}

            if (asp == null) return EMPTY;

            ItemStack norm = thaumcraft.api.ThaumcraftApiHelper.makeCrystal(asp, 1);
            NBTTagCompound tag = norm.hasTagCompound() ? norm.getTagCompound().copy() : null;
            return new ItemKey(it, 0, tag);
        }

        // Остальные: item + (meta если подтипы) + NBT как есть
        int meta = s.getHasSubtypes() ? s.getMetadata() : 0;
        NBTTagCompound tag = s.hasTagCompound() ? s.getTagCompound().copy() : null;
        return new ItemKey(it, meta, tag);
    }



    public ItemStack toStack(int count) {
        ItemStack s = new ItemStack(item, count, meta);
        if (tag != null) s.setTagCompound(tag.copy());
        return s;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemKey)) return false;
        ItemKey k = (ItemKey) o;
        return item == k.item && meta == k.meta && Objects.equals(tag, k.tag);
    }

    @Override public int hashCode() {
        return Objects.hash(item, meta, tag == null ? 0 : tag.hashCode());
    }
}
