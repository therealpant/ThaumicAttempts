package therealpant.thaumicattempts.util;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.crafting.InfusionRecipe;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Поиск и расчёт превью инфузионных рецептов (матрица наполнения).
 */
public final class InfusionRecipeHelper {

    private InfusionRecipeHelper() { }

    /**
     * Возвращает копию результата подходящего инфузионного рецепта или ItemStack.EMPTY.
     */
    public static ItemStack findResult(ItemStack center, List<ItemStack> components, World world) {
        InfusionRecipe r = findMatch(center, components, world);
        ItemStack out = getOutput(r, center);
        return out == null ? ItemStack.EMPTY : copySafe(out);
    }

    @Nullable
    public static InfusionRecipe findMatch(ItemStack center, List<ItemStack> components, World world) {
        if (world == null || center == null || center.isEmpty()) return null;

        ArrayList<ItemStack> comps = new ArrayList<>();
        for (ItemStack s : components) {
            if (s == null || s.isEmpty()) continue;
            comps.add(copyOne(s));
        }

        ItemStack central = copyOne(center);

        for (Object o : getAllRecipes()) {
            if (!(o instanceof InfusionRecipe)) continue;
            InfusionRecipe ir = (InfusionRecipe) o;
            if (matches(ir, comps, central, world)) {
                return ir;
            }
        }
        return null;
    }

    /* ================= helpers ================= */

    private static ItemStack getOutput(@Nullable InfusionRecipe r, ItemStack center) {
        if (r == null) return ItemStack.EMPTY;
        ItemStack central = center == null ? ItemStack.EMPTY : copyOne(center);

        try {
            Method m = r.getClass().getMethod("getRecipeOutput", ItemStack.class);
            Object res = m.invoke(r, central);
            if (res instanceof ItemStack) return (ItemStack) res;
        } catch (Throwable ignored) {}

        try {
            Method m = r.getClass().getMethod("getRecipeOutput");
            Object res = m.invoke(r);
            if (res instanceof ItemStack) return (ItemStack) res;
        } catch (Throwable ignored) {}

        try {
            Field f = r.getClass().getDeclaredField("recipeOutput");
            f.setAccessible(true);
            Object res = f.get(r);
            if (res instanceof ItemStack) return (ItemStack) res;
        } catch (Throwable ignored) {}

        return ItemStack.EMPTY;
    }

    private static boolean matches(InfusionRecipe r, ArrayList<ItemStack> comps, ItemStack central, World w) {
        try {
            if (r.matches(comps, central, w, (EntityPlayer) null)) return true;
        } catch (Throwable ignored) {}

        try {
            Method m = r.getClass().getMethod("matches", ArrayList.class, ItemStack.class, World.class, EntityPlayer.class);
            Object ok = m.invoke(r, comps, central, w, null);
            if (ok instanceof Boolean && (Boolean) ok) return true;
        } catch (Throwable ignored) {}

        try {
            Method m = r.getClass().getMethod("matches", ArrayList.class, ItemStack.class, World.class);
            Object ok = m.invoke(r, comps, central, w);
            return ok instanceof Boolean && (Boolean) ok;
        } catch (Throwable ignored) {}

        return false;
    }

    private static List<?> getAllRecipes() {
        try {
            Method m = ThaumcraftApi.class.getMethod("getInfusionRecipes");
            Object v = m.invoke(null);
            if (v instanceof List) return (List<?>) v;
        } catch (Throwable ignored) {}

        try {
            Field f = ThaumcraftApi.class.getDeclaredField("infusionRecipes");
            f.setAccessible(true);
            Object v = f.get(null);
            if (v instanceof List) return (List<?>) v;
        } catch (Throwable ignored) {}

        return Collections.emptyList();
    }

    private static ItemStack copySafe(ItemStack s) {
        ItemStack x = s.copy();
        if (x.getCount() <= 0) x.setCount(1);
        return x;
    }

    private static ItemStack copyOne(ItemStack s) {
        ItemStack x = s.copy();
        x.setCount(1);
        return x;
    }
}
