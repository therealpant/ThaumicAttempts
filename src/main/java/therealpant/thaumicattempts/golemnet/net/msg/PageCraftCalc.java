package therealpant.thaumicattempts.golemnet.net.msg;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.items.ItemHandlerHelper;
import therealpant.thaumicattempts.golemnet.tile.TileMirrorManager;
import therealpant.thaumicattempts.golemnet.tile.TilePatternRequester;
import therealpant.thaumicattempts.util.ItemKey;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Подсчёт крафта теперь 1:1 совпадает с логикой голем-крафтера:
 *  - кристаллы TC6 — матч строго по аспекту,
 *  - нестакуемые — item + meta (NBT игнор),
 *  - стакаемые — relaxed stack.
 *
 * Для каждой позиции страницы:
 *  1) ищем реквестер, у которого есть подходящий паттерн для такого результата,
 *  2) узнаём выход за крафт (perCraft),
 *  3) бинарным поиском ищем максимальное число крафтов из текущего каталога,
 *  4) makeCounts = crafts * perCraft; possible = crafts > 0.
 */
public final class PageCraftCalc {

    public static final class CalcResult {
        public final List<Integer> counts;     // обычные склады (как было — сюда не лезем)
        public final List<Integer> makeCounts; // сколько ШТУК реально можно сделать (crafts * perCraft)
        public final List<Boolean> possible;   // можно ли сделать хотя бы один крафт
        public CalcResult(List<Integer> counts, List<Integer> mc, List<Boolean> pos) {
            this.counts = counts; this.makeCounts = mc; this.possible = pos;
        }
    }

    public static CalcResult computeMakeForPage(TileMirrorManager mgr,
                                                List<ItemStack> pageWindow,
                                                LinkedHashMap<ItemKey,Integer> catalog) {
        if (mgr == null || pageWindow == null) {
            return new CalcResult(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        }

        final int N = pageWindow.size();
        ArrayList<Integer> makeCounts = new ArrayList<>(N); // ← сюда кладём КОЛ-ВО КРАФТОВ
        ArrayList<Boolean> possible   = new ArrayList<>(N);

        // моментальный снапшот реквестеров от менеджера
        Set<BlockPos> reqs = mgr.getRequestersSnapshot();

        for (int i = 0; i < N; i++) {
            ItemStack result = pageWindow.get(i);
            if (result == null || result.isEmpty()) {
                makeCounts.add(0);
                possible.add(false);
                continue;
            }

            TilePatternRequester requester = findRequesterForResult(mgr, reqs, result);
            if (requester == null) {
                makeCounts.add(0);
                possible.add(false);
                continue;
            }

            // «выход за крафт» может пригодиться для UI, но здесь мы считаем именно КРАФТЫ
            int perCraft = Math.max(1, requester.getPerCraftOutputCountFor(result));

            // верхняя грубая оценка (по самому узкому ингредиенту), чтобы ограничить бинпоиск
            int upper = roughUpperBound(catalog, requester, result, perCraft);

            int lo = 0, hi = Math.max(0, upper);
            while (lo < hi) {
                int mid = lo + (hi - lo + 1) / 2;
                if (canMakeTimes(catalog, requester, result, mid)) lo = mid; else hi = mid - 1;
            }
            int crafts = lo;

            // ВАЖНО: в makeCounts пишем число КРАФТОВ, а не предметов!
            makeCounts.add(crafts);
            possible.add(crafts > 0);
        }

        // counts (складские числа) заполняются в другом месте — здесь не трогаем
        return new CalcResult(Collections.emptyList(), makeCounts, possible);
    }


    /* ===================== внутрянка ===================== */

    @Nullable
    private static TilePatternRequester findRequesterForResult(TileMirrorManager mgr, Set<BlockPos> reqs, ItemStack like) {
        if (reqs == null || reqs.isEmpty() || mgr.getWorld() == null) return null;
        for (BlockPos rp : reqs) {
            TileEntity te = mgr.getWorld().getTileEntity(rp);
            if (!(te instanceof TilePatternRequester)) continue;
            TilePatternRequester r = (TilePatternRequester) te;
            int idx = r.findPatternIndexForResultLike(like);
            if (idx >= 0) return r;
        }
        return null;
    }

    /** Грубый верхний предел по самому «узкому» ингредиенту из каталога. */
    private static int roughUpperBound(Map<ItemKey,Integer> catalog, TilePatternRequester req, ItemStack result, int perCraft) {
        List<ItemStack> one = req.getRecipeInputsFor(result, 1);
        if (one == null || one.isEmpty()) return 0;

        int minTimes = Integer.MAX_VALUE;
        for (ItemStack need : one) {
            if (need == null || need.isEmpty()) continue;
            int have = countInCatalogForNeed(catalog, need);
            int onetimes = Math.max(1, need.getCount());
            int bound = have / onetimes;
            if (bound < minTimes) minTimes = bound;
            if (minTimes == 0) break;
        }
        return Math.max(0, minTimes);
    }

    /** Проверяем, хватит ли каталога на 'times' крафтов (с учётом их правил совпадения). */
    private static boolean canMakeTimes(Map<ItemKey,Integer> catalog, TilePatternRequester req, ItemStack result, int times) {
        if (times <= 0) return true;
        List<ItemStack> needs = req.getRecipeInputsFor(result, times);
        if (needs == null || needs.isEmpty()) return true;

        Map<ItemKey,Integer> pool = new LinkedHashMap<>(catalog);
        for (ItemStack need : needs) {
            if (need == null || need.isEmpty()) continue;
            int want = Math.max(1, need.getCount());
            int taken = takeFromCatalog(pool, need, want);
            if (taken < want) return false;
        }
        return true;
    }

    /** Сколько единиц нужного ингредиента есть в каталоге. */
    private static int countInCatalogForNeed(Map<ItemKey,Integer> cat, ItemStack need) {
        if (cat == null || cat.isEmpty() || need == null || need.isEmpty()) return 0;
        int sum = 0;
        for (Map.Entry<ItemKey,Integer> e : cat.entrySet()) {
            ItemStack k = e.getKey().toStack(1);
            if (matchForRecipe(k, need)) {
                sum += Math.max(0, e.getValue());
            }
        }
        return sum;
    }

    /** Зарезервировать в каталоге требуемое количество под «need». */
    private static int takeFromCatalog(Map<ItemKey,Integer> pool, ItemStack need, int want) {
        int left = want;
        for (Map.Entry<ItemKey,Integer> e : pool.entrySet()) {
            if (left <= 0) break;
            ItemStack k = e.getKey().toStack(1);
            if (!matchForRecipe(k, need)) continue;
            int have = Math.max(0, e.getValue());
            if (have <= 0) continue;
            int take = Math.min(have, left);
            e.setValue(have - take);
            left -= take;
        }
        return want - left;
    }

    /* === правила совпадения ИДЕНТИЧНЫ крафтеру === */

    private static boolean isCrystal(ItemStack s) {
        return s != null && !s.isEmpty()
                && s.getItem() == thaumcraft.api.items.ItemsTC.crystalEssence;
    }
    private static boolean crystalSame(ItemStack a, ItemStack b) {
        thaumcraft.api.aspects.Aspect x = aspectOf(a), y = aspectOf(b);
        return x != null && x == y;
    }
    @Nullable
    private static thaumcraft.api.aspects.Aspect aspectOf(ItemStack s) {
        if (!isCrystal(s)) return null;
        thaumcraft.api.aspects.AspectList al =
                ((thaumcraft.common.items.ItemTCEssentiaContainer) thaumcraft.api.items.ItemsTC.crystalEssence)
                        .getAspects(s);
        return (al != null && al.size() == 1) ? al.getAspects()[0] : null;
    }

    /**
     * Тот же самый матчинг, что у TileEntityGolemCrafter.sameForGrid():
     *  - кристаллы по аспекту,
     *  - нестакаемые — item + meta,
     *  - стакаемые — relaxed.
     */
    private static boolean matchForRecipe(ItemStack a, ItemStack b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;
        if (isCrystal(a) || isCrystal(b)) {
            return isCrystal(a) && isCrystal(b) && crystalSame(a, b);
        }
        if (a.getMaxStackSize() == 1 || b.getMaxStackSize() == 1) {
            if (a.getItem() != b.getItem()) return false;
            if (a.getHasSubtypes() && a.getMetadata() != b.getMetadata()) return false;
            return true; // NBT игнорируем
        }
        return ItemHandlerHelper.canItemStacksStackRelaxed(a, b);
    }
}
