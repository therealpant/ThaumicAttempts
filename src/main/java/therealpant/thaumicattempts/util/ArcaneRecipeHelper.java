package therealpant.thaumicattempts.util;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraft.item.crafting.IRecipe;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.crafting.IArcaneRecipe;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.IntFunction;

/**
 * Поиск арканового рецепта по сетке 3x3.
 * Работает и через Forge-регистр (рецепты, которые туда проксируют),
 * и через список рецептов Thaumcraft (через reflection), игнорируя «ресёрч-гейтинг».
 */
public final class ArcaneRecipeHelper {

    private ArcaneRecipeHelper() {}

    public static final class ArcaneMatch {
        public final ItemStack output;
        public final AspectList crystals; // может быть null, если нет кристаллов
        public final int visCost;
        public final IArcaneRecipe recipe;
        public ArcaneMatch(ItemStack out, @Nullable AspectList cr, int vis, IArcaneRecipe r) {
            this.output = out == null ? ItemStack.EMPTY : out.copy();
            this.crystals = cr;
            this.visCost = Math.max(0, vis);
            this.recipe = r;
        }
    }

    /** Удобный вход: читаем сетку из лямбды slot->ItemStack. */
    public static @Nullable ArcaneMatch findArcane(IntFunction<ItemStack> gridGetter, World world) {
        return findArcane(gridGetter, world, null, true);
    }

    /** Полный вход: можно передать player; ignoreResearch=true — не проверять открытия из книги. */
    public static @Nullable ArcaneMatch findArcane(IntFunction<ItemStack> gridGetter,
                                                   World world,
                                                   @Nullable EntityPlayer player,
                                                   boolean ignoreResearch)
    {
        if (world == null) return null;

        // Собираем InventoryCrafting 3x3
        final InventoryCrafting inv = new InventoryCrafting(new Container() {
            @Override public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer p) { return false; }
        }, 3, 3);

        for (int i = 0; i < 9; i++) {
            ItemStack s = gridGetter.apply(i);
            inv.setInventorySlotContents(i, (s == null || s.isEmpty()) ? ItemStack.EMPTY : copyOne(s));
        }

        // === 1) фордж-регистр: рецепты, реализующие IArcaneRecipe ===
        Collection<IRecipe> all = ForgeRegistries.RECIPES.getValuesCollection();
        for (IRecipe r : all) {
            if (!(r instanceof IArcaneRecipe)) continue;
            IArcaneRecipe ar = (IArcaneRecipe) r;
            if (matchesArcane(ar, r, inv, world, player, ignoreResearch)) {
                ItemStack out = safeGetResult(r, inv);
                return new ArcaneMatch(out, safeGetCrystals(ar), safeGetVis(ar), ar);
            }
        }

        // === 2) прямой список TC (на случай, если не проксируют в фордж-регистр) ===
        for (Object o : getThaumcraftCraftingListReflect()) {
            if (!(o instanceof IArcaneRecipe)) continue;
            IArcaneRecipe ar = (IArcaneRecipe) o;
            IRecipe base = (ar instanceof IRecipe) ? (IRecipe) ar : null;
            if (matchesArcane(ar, base, inv, world, player, ignoreResearch)) {
                ItemStack out = base != null ? safeGetResult(base, inv) : tryGetResultReflect(ar, inv, world, player);
                return new ArcaneMatch(out, safeGetCrystals(ar), safeGetVis(ar), ar);
            }
        }

        return null;
    }

    /* ================= helpers ================= */

    private static ItemStack copyOne(ItemStack s) {
        ItemStack x = s.copy(); x.setCount(1); return x;
    }

    private static boolean matchesArcane(IArcaneRecipe ar, @Nullable IRecipe base,
                                         InventoryCrafting inv, World w,
                                         @Nullable EntityPlayer pl, boolean ignoreResearch)
    {
        // 1) Пытаемся обычный IRecipe.matches (часто TC-рецепт проксируется без проверки ресёрча)
        if (base != null) {
            try {
                if (base.matches(inv, w)) return true;
            } catch (Throwable ignored) {}
        }

        // 2) Метод с игроком: matches(InventoryCrafting, World, EntityPlayer)
        if (pl != null) {
            try {
                Method m = ar.getClass().getMethod("matches", InventoryCrafting.class, World.class, EntityPlayer.class);
                Object ok = m.invoke(ar, inv, w, pl);
                if (ok instanceof Boolean && (Boolean) ok) return true;
            } catch (NoSuchMethodException ignored) {
            } catch (Throwable ignored) {}
        }

        // 3) Метод без игрока: matches(InventoryCrafting, World)
        try {
            Method m2 = ar.getClass().getMethod("matches", InventoryCrafting.class, World.class);
            Object ok = m2.invoke(ar, inv, w);
            if (ok instanceof Boolean && (Boolean) ok) return true;
        } catch (NoSuchMethodException ignored) {
        } catch (Throwable ignored) {}

        // 4) Если проверка спотыкается о «ресёрч», попробуем (по возможности) игнорировать её.
        //    Многие реализации сперва сверяют ингредиенты, и лишь потом знание.
        //    Унифицированного способа вынуть внутренности нет, поэтому здесь заканчиваем.
        return false;
    }

    private static ItemStack safeGetResult(IRecipe r, InventoryCrafting inv) {
        try {
            ItemStack out = r.getCraftingResult(inv);
            return out == null ? ItemStack.EMPTY : out.copy();
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    private static AspectList safeGetCrystals(IArcaneRecipe ar) {
        try { return ar.getCrystals(); } catch (Throwable ignored) { }
        try {
            Method m = ar.getClass().getMethod("getCrystals");
            return (AspectList) m.invoke(ar);
        } catch (Throwable ignored) { }
        return null;
    }

    private static int safeGetVis(IArcaneRecipe ar) {
        try { return ar.getVis(); } catch (Throwable ignored) { }
        try {
            Method m = ar.getClass().getMethod("getVis");
            Object v = m.invoke(ar);
            if (v instanceof Integer) return (Integer) v;
        } catch (Throwable ignored) { }
        return 0;
    }

    private static ItemStack tryGetResultReflect(IArcaneRecipe ar, InventoryCrafting inv, World w, @Nullable EntityPlayer p) {
        // Попробуем подряд: (inv,w,player), (inv,w)
        try {
            Method m = ar.getClass().getMethod("getCraftingResult", InventoryCrafting.class, World.class, EntityPlayer.class);
            Object res = m.invoke(ar, inv, w, p);
            return res instanceof ItemStack ? ((ItemStack) res).copy() : ItemStack.EMPTY;
        } catch (Throwable ignored) {}
        try {
            Method m = ar.getClass().getMethod("getCraftingResult", InventoryCrafting.class);
            Object res = m.invoke(ar, inv);
            return res instanceof ItemStack ? ((ItemStack) res).copy() : ItemStack.EMPTY;
        } catch (Throwable ignored) {}
        return ItemStack.EMPTY;
    }

    /** Достаём список крафтов TC через reflection. Работает и в релизных банках. */
    @SuppressWarnings("unchecked")
    private static List<Object> getThaumcraftCraftingListReflect() {
        try {
            Class<?> api = Class.forName("thaumcraft.api.ThaumcraftApi");
            // В 1.12 это обычно статическое поле "craftingRecipes" (List<IArcaneRecipe>)
            Field f = api.getDeclaredField("craftingRecipes");
            f.setAccessible(true);
            Object v = f.get(null);
            if (v instanceof List) return (List<Object>) v;
        } catch (Throwable ignored) {}
        return new ArrayList<>();
    }


}
