// src/main/java/therealpant/thaumicattempts/golemnet/catalog/CatalogSnapshots.java
package therealpant.thaumicattempts.golemnet.net.msg;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Менеджер каталожных снимков.
 * Привязывает snapshotId к (терминал, игрок, вкладка, поисковая строка) и хранит индекс.
 */
public final class CatalogSnapshots {

    private static final CatalogSnapshots I = new CatalogSnapshots();
    public static CatalogSnapshots get() { return I; }

    private final AtomicLong seq = new AtomicLong(1000L);

    /** Ключ снэпшота: позиция терминала + UUID игрока + craftTab */
    public static final class Key {
        public final BlockPos pos;
        public final UUID player;
        public final boolean craftTab;

        public Key(BlockPos pos, UUID player, boolean craftTab) {
            this.pos = pos; this.player = player; this.craftTab = craftTab;
        }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key)) return false;
            Key k = (Key) o;
            return craftTab == k.craftTab &&
                    Objects.equals(pos, k.pos) &&
                    Objects.equals(player, k.player);
        }
        @Override public int hashCode() {
            return Objects.hash(pos, player, craftTab);
        }
    }

    /** Лёгкая структура записи каталога */
    public static final class Entry {
        public final ItemStack stack;
        public final int have;
        public final int makeCount;
        public final boolean canMake;
        public Entry(ItemStack s, int have, int makeCount, boolean canMake){
            this.stack = (s==null)? ItemStack.EMPTY : s.copy();
            this.have = Math.max(0, have);
            this.makeCount = Math.max(0, makeCount);
            this.canMake = canMake;
        }
    }

    /** Индекс снэпшота: исходные записи + текущая поисковая строка + отфильтрованный список */
    private static final class Snapshot {
        public final long id;
        public final Key key;
        public String search = "";
        public List<Entry> all = Collections.emptyList();
        public List<Entry> filtered = Collections.emptyList();
        public Snapshot(long id, Key key){ this.id=id; this.key=key; }
    }

    // id → snapshot
    private final Map<Long, Snapshot> byId = new ConcurrentHashMap<>();
    // key → id (последний для пары терминал/игрок/вкладка)
    private final Map<Key, Long> lastByKey = new ConcurrentHashMap<>();

    public long createNew(Key key, String search, List<Entry> all) {
        long id = seq.incrementAndGet();
        Snapshot s = new Snapshot(id, key);
        s.search = (search==null) ? "" : search.trim().toLowerCase(Locale.ROOT);
        s.all = (all==null) ? Collections.emptyList() : new ArrayList<>(all);
        s.filtered = applyFilter(s.all, s.search);
        byId.put(id, s);
        lastByKey.put(key, id);
        return id;
    }

    public Snapshot get(long id) { return byId.get(id); }

    public boolean recreate(long id, String search, List<Entry> all) {
        Snapshot s = byId.get(id);
        if (s == null) return false;
        s.search = (search==null) ? "" : search.trim().toLowerCase(Locale.ROOT);
        s.all = (all==null) ? Collections.emptyList() : new ArrayList<>(all);
        s.filtered = applyFilter(s.all, s.search);
        return true;
    }

    private static List<Entry> applyFilter(List<Entry> all, String search) {
        if (search == null || search.trim().isEmpty()) return all;
        final String q = search.trim().toLowerCase(Locale.ROOT);
        return all.stream().filter(e -> {
            String name = e.stack.getDisplayName();
            return name != null && name.toLowerCase(Locale.ROOT).contains(q);
        }).collect(Collectors.toList());
    }

    /** Построить страницу (1-базовую) на 35 позиций */
    public PageResult buildPage(long snapshotId, int pageNo1) {
        Snapshot s = byId.get(snapshotId);
        if (s == null) return PageResult.emptyUnknown();
        final int pageSize = 35;
        final int total = s.filtered.size();
        final int totalPages = (total == 0) ? 1 : ((total + pageSize - 1) / pageSize);
        final int page = Math.max(1, Math.min(pageNo1, totalPages));
        final int from = (page - 1) * pageSize;
        final int to = Math.min(total, from + pageSize);

        List<Entry> window = (from < to) ? s.filtered.subList(from, to) : Collections.emptyList();

        List<ItemStack> items = new ArrayList<>(35);
        List<Integer> counts = new ArrayList<>(35);
        List<Integer> makeCounts = new ArrayList<>(35);
        List<Boolean> makePossible = new ArrayList<>(35);

        int nonEmpty = 0;
        for (int i = 0; i < window.size(); i++) {
            Entry e = window.get(i);
            items.add(e.stack);
            counts.add(e.have);
            makeCounts.add(e.makeCount);
            makePossible.add(e.canMake);
            if (e.stack != null && !e.stack.isEmpty()) nonEmpty++;
        }
        // Западдинг до 35
        while (items.size() < 35) { items.add(ItemStack.EMPTY); counts.add(0); makeCounts.add(0); makePossible.add(Boolean.FALSE); }

        PageResult r = new PageResult();
        r.items = items;
        r.counts = counts;
        r.makeCounts = makeCounts;
        r.makePossible = makePossible;
        r.pageNo1 = page;
        r.totalPages = totalPages;        // известны
        r.totalPagesKnown = true;
        r.hasMoreHeuristic = page < totalPages;
        r.itemCountOnPage = nonEmpty;
        return r;
    }

    /** Конвертер из сырых данных (ItemStack + числа) */
    public static List<Entry> fromRaw(List<ItemStack> stacks, List<Integer> have, List<Integer> craft, List<Boolean> canMake) {
        int n = Math.max(
                Math.max(stacks == null ? 0 : stacks.size(), have == null ? 0 : have.size()),
                Math.max(craft == null ? 0 : craft.size(), canMake == null ? 0 : canMake.size())
        );
        List<Entry> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ItemStack s = (stacks != null && i < stacks.size()) ? stacks.get(i) : ItemStack.EMPTY;
            int hv = (have != null && i < have.size() && have.get(i) != null) ? Math.max(0, have.get(i)) : 0;
            int mc = (craft != null && i < craft.size() && craft.get(i) != null) ? Math.max(0, craft.get(i)) : 0;
            boolean cm = (canMake != null && i < canMake.size() && canMake.get(i) != null) && canMake.get(i);
            out.add(new Entry(s, hv, mc, cm));
        }
        return out;
    }
}

