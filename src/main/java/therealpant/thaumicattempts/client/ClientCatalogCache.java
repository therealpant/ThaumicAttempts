package therealpant.thaumicattempts.client;

import net.minecraft.item.ItemStack;

import java.util.*;

/**
 * Клиентский кэш каталога/черновиков/пэндинга для двух вкладок: delivery и craft.
 * Совместим с S2C_SnapshotCreated, S2C_CatalogPage, S2C_DraftSnapshot.
 */
public final class ClientCatalogCache {
    private ClientCatalogCache() {}

    /* ------------ внутренняя модель ------------ */

    private static final class PageData {
        long snapshotId = -1L;

        // постраничные данные (1-базовая нумерация страниц)
        final Map<Integer, List<ItemStack>> itemsByPage       = new HashMap<>();
        final Map<Integer, List<Integer>>   countsByPage      = new HashMap<>();
        final Map<Integer, List<Integer>>   makeCountsByPage  = new HashMap<>();
        final Map<Integer, List<Boolean>>   makePossibleByPage= new HashMap<>();
        final Map<Integer, Integer>         itemCountOnPage   = new HashMap<>();

        final Set<Integer> knownPages   = new HashSet<>();
        final Set<Integer> partialPages = new HashSet<>();
        boolean totalKnown = false;
        int     totalPages = 0;
        boolean hasMore    = false;

        // 3×3 панели
        List<ItemStack> draftStacks   = emptyItems9();
        List<Integer>   draftCounts   = emptyInts9();
        List<ItemStack> pendingStacks = emptyItems9();
        List<Integer>   pendingCounts = emptyInts9();

        boolean updated = false;

        void clearAll() {
            snapshotId = -1L;
            itemsByPage.clear();
            countsByPage.clear();
            makeCountsByPage.clear();
            makePossibleByPage.clear();
            itemCountOnPage.clear();
            knownPages.clear();
            partialPages.clear();
            totalKnown = false;
            totalPages = 0;
            hasMore    = false;
            draftStacks   = emptyItems9();
            draftCounts   = emptyInts9();
            pendingStacks = emptyItems9();
            pendingCounts = emptyInts9();
            updated = true;
        }
    }

    private static final PageData DELIVERY = new PageData();
    private static final PageData CRAFT    = new PageData();
    private static PageData pd(boolean craftTab) { return craftTab ? CRAFT : DELIVERY; }

    /* ------------ публичные API, которые зовут хэндлеры пакетов ------------ */

    /** Приходит из S2C_SnapshotCreated. Полностью сбрасывает вкладку и фиксирует новый snapshotId. */
    public static void onSnapshotCreated(boolean craftTab, long snapshotId) {
        PageData P = pd(craftTab);
        P.clearAll();
        P.snapshotId = snapshotId;
        P.updated = true;
    }

    /** Приходит из S2C_CatalogPage.Handler. */
    public static void applyPagePacket(
            boolean craftTab,
            long snapshotId,
            int pageNo1,
            int totalPages,
            boolean hasMore,
            List<ItemStack> items,
            List<Integer> counts,
            List<Integer> makeCounts,
            List<Boolean> makePossible,
            int itemCountOnThisPage,
            boolean partial
    ) {
        PageData P = pd(craftTab);
        if (snapshotId > 0L) P.snapshotId = snapshotId;

        if (items != null)        P.itemsByPage.put(pageNo1, deepCopyItems(items));
        if (counts != null)       P.countsByPage.put(pageNo1, new ArrayList<>(counts));
        if (makeCounts != null)   P.makeCountsByPage.put(pageNo1, new ArrayList<>(makeCounts));
        if (makePossible != null) P.makePossibleByPage.put(pageNo1, new ArrayList<>(makePossible));
        P.itemCountOnPage.put(pageNo1, Math.max(0, itemCountOnThisPage));

        if (partial) P.partialPages.add(pageNo1); else P.partialPages.remove(pageNo1);

        P.knownPages.add(pageNo1);
        if (totalPages > 0) { P.totalKnown = true; P.totalPages = totalPages; }
        P.hasMore = hasMore;
        P.updated = true;
    }

    /** Приходит из S2C_DraftSnapshot.Handler — черновик 3×3. */
    public static void setDraft(boolean craftTab, List<ItemStack> stacks, List<Integer> counts) {
        PageData P = pd(craftTab);
        P.draftStacks = normalizeItems9(stacks);
        P.draftCounts = normalizeInts9(counts);
        P.updated = true;
    }

    /** Приходит из S2C_DraftSnapshot.Handler — pending 3×3. */
    public static void setPending(boolean craftTab, List<ItemStack> stacks, List<Integer> counts) {
        PageData P = pd(craftTab);
        P.pendingStacks = normalizeItems9(stacks);
        P.pendingCounts = normalizeInts9(counts);
        P.updated = true;
    }

    /** Вызывается при открытии GUI (опционально; можно ничего не делать). */
    public static void guiOpened(boolean craftTab) {
        // no-op, но оставляем, если где-то завязан флоу
    }

    /* ------------ геттеры, которыми пользуется GUI ------------ */

    public static long getActiveSnapshotId(boolean craftTab) { return pd(craftTab).snapshotId; }

    public static boolean consumeUpdatedFlag(boolean craftTab) {
        PageData P = pd(craftTab);
        boolean u = P.updated;
        P.updated = false;
        return u;
    }

    public static Set<Integer> getKnownPages(boolean craftTab) {
        return new HashSet<>(pd(craftTab).knownPages);
    }

    public static boolean isTotalKnown(boolean craftTab) { return pd(craftTab).totalKnown; }
    public static int getTotalPages(boolean craftTab)    { return pd(craftTab).totalPages; }
    public static boolean getHasMoreFlag(boolean craftTab){ return pd(craftTab).hasMore; }

    public static List<ItemStack> getPage35ByNumber(boolean craftTab, int pageNo1) {
        List<ItemStack> lst = pd(craftTab).itemsByPage.get(pageNo1);
        return (lst == null) ? Collections.emptyList() : deepCopyItems(lst);
    }
    public static List<Integer> getCountsByNumber(boolean craftTab, int pageNo1) {
        List<Integer> lst = pd(craftTab).countsByPage.get(pageNo1);
        return (lst == null) ? Collections.emptyList() : new ArrayList<>(lst);
    }
    public static List<Integer> getMakeCountsByNumber(boolean craftTab, int pageNo1) {
        List<Integer> lst = pd(craftTab).makeCountsByPage.get(pageNo1);
        return (lst == null) ? Collections.emptyList() : new ArrayList<>(lst);
    }
    public static List<Boolean> getMakePossibleByNumber(boolean craftTab, int pageNo1) {
        List<Boolean> lst = pd(craftTab).makePossibleByPage.get(pageNo1);
        return (lst == null) ? Collections.emptyList() : new ArrayList<>(lst);
    }
    public static int getItemCountOnPage(boolean craftTab, int pageNo1) {
        Integer v = pd(craftTab).itemCountOnPage.get(pageNo1);
        return v == null ? 0 : v;
    }
    public static boolean isPagePartial(boolean craftTab, int pageNo1) {
        return pd(craftTab).partialPages.contains(pageNo1);
    }

    public static List<ItemStack> getDraftStacks(boolean craftTab)   { return deepCopyItems(pd(craftTab).draftStacks); }
    public static List<Integer>   getDraftCounts(boolean craftTab)   { return new ArrayList<>(pd(craftTab).draftCounts); }
    public static List<ItemStack> getPendingStacks(boolean craftTab) { return deepCopyItems(pd(craftTab).pendingStacks); }
    public static List<Integer>   getPendingCounts(boolean craftTab) { return new ArrayList<>(pd(craftTab).pendingCounts); }

    /* ------------ утилиты ------------ */

    private static List<ItemStack> emptyItems9() {
        ArrayList<ItemStack> r = new ArrayList<>(9);
        for (int i=0;i<9;i++) r.add(ItemStack.EMPTY);
        return r;
    }
    private static List<Integer> emptyInts9() {
        ArrayList<Integer> r = new ArrayList<>(9);
        for (int i=0;i<9;i++) r.add(0);
        return r;
    }

    private static List<ItemStack> normalizeItems9(List<ItemStack> src) {
        ArrayList<ItemStack> r = new ArrayList<>(9);
        if (src != null) {
            for (int i=0;i<Math.min(9, src.size()); i++) {
                ItemStack s = src.get(i);
                r.add(s == null ? ItemStack.EMPTY : s.copy());
            }
        }
        while (r.size() < 9) r.add(ItemStack.EMPTY);
        return r;
    }
    private static List<Integer> normalizeInts9(List<Integer> src) {
        ArrayList<Integer> r = new ArrayList<>(9);
        if (src != null) {
            for (int i=0;i<Math.min(9, src.size()); i++) {
                Integer v = src.get(i);
                r.add(v == null ? 0 : Math.max(0, v));
            }
        }
        while (r.size() < 9) r.add(0);
        return r;
    }

    private static List<ItemStack> deepCopyItems(List<ItemStack> src) {
        if (src == null) return Collections.emptyList();
        ArrayList<ItemStack> r = new ArrayList<>(src.size());
        for (ItemStack s : src) r.add(s == null ? ItemStack.EMPTY : s.copy());
        return r;
    }

    // ======== Адаптеры под старые имена из GUI ========

    // GUI ждёт getDraft(craftTab) и getPending(craftTab) → вернём стеки
    public static java.util.List<net.minecraft.item.ItemStack> getDraft(boolean craftTab) {
        return getDraftStacks(craftTab);
    }
    public static java.util.List<net.minecraft.item.ItemStack> getPending(boolean craftTab) {
        return getPendingStacks(craftTab);
    }

    // ======== Совместимость сигнатуры applyPagePacket(...) ========
// Текущий S2C_CatalogPage.Handler зовёт: (craftTab, snapshotId, pageNo1, totalPages, hasMore, nonEmpty, items, counts, makeCounts, makePossible)
// Добавим оверлоад, который просто прокидывает в основную версию (partial=false).
    public static void applyPagePacket(
            boolean craftTab,
            long snapshotId,
            int pageNo1,
            int totalPages,
            boolean hasMore,
            int itemCountOnThisPage,
            java.util.List<net.minecraft.item.ItemStack> items,
            java.util.List<java.lang.Integer> counts,
            java.util.List<java.lang.Integer> makeCounts,
            java.util.List<java.lang.Boolean> makePossible
    ) {
        applyPagePacket(
                craftTab,
                snapshotId,
                pageNo1,
                totalPages,
                hasMore,
                items,
                counts,
                makeCounts,
                makePossible,
                itemCountOnThisPage,
                false // partial
        );
    }

}
