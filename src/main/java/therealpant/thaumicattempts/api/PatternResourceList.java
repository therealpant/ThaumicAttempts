package therealpant.thaumicattempts.api;

import net.minecraft.item.ItemStack;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.items.ItemsTC;
import therealpant.thaumicattempts.util.ItemKey;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility that produces normalized resource lists for pattern items.
 * Blocks like requesters and crafters can consume the results to
 * schedule provisioning in a consistent way.
 */
public final class PatternResourceList {

    private PatternResourceList() {}

    /** One required resource entry. */
    public static final class Entry {
        private final ItemKey key;
        private final int count;

        public Entry(ItemKey key, int count) {
            this.key = key == null ? ItemKey.EMPTY : key;
            this.count = Math.max(1, count);
        }

        public ItemKey getKey() { return key; }
        public int getCount() { return count; }

        /** Normalized stack with the requested amount. */
        public ItemStack toStack() { return key.toStack(count); }
        /** Normalized stack with a count of one (for matching). */
        public ItemStack getKeyStack() { return key.toStack(1); }
    }

    /**
     * Build a resource list for the given pattern if it implements
     * {@link IPatternResourceProvider}. Returns an empty list otherwise.
     */
    public static List<Entry> build(ItemStack pattern) {
        if (pattern == null || pattern.isEmpty()) return new ArrayList<>();
        if (pattern.getItem() instanceof IPatternResourceProvider) {
            return ((IPatternResourceProvider) pattern.getItem()).buildResourceList(pattern);
        }
        return new ArrayList<>();
    }

    /**
     * Aggregate a collection of stacks into a list of resource entries
     * keyed by {@link ItemKey} using a first-seen order.
     */
    public static List<Entry> aggregate(Iterable<ItemStack> stacks, boolean useStackCounts) {
        Map<ItemKey, Integer> map = new LinkedHashMap<>();
        append(map, stacks, useStackCounts);
        return toEntries(map);
    }

    /** Append a single resource entry to the accumulator map. */
    public static void append(Map<ItemKey, Integer> acc, ItemStack stack, int count) {
        if (acc == null || stack == null || stack.isEmpty() || count <= 0) return;
        ItemStack keyStack = normalizeForKey(stack);
        if (keyStack.isEmpty()) return;
        ItemKey key = ItemKey.of(keyStack);
        if (key == ItemKey.EMPTY) return;
        acc.merge(key, count, Integer::sum);
    }

    /** Append all stacks from the iterable into the accumulator map. */
    public static void append(Map<ItemKey, Integer> acc, Iterable<ItemStack> stacks, boolean useStackCounts) {
        if (acc == null || stacks == null) return;
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) continue;
            int amount = useStackCounts ? Math.max(1, stack.getCount()) : 1;
            append(acc, stack, amount);
        }
    }

    /** Convert an accumulator map into an ordered list of entries. */
    public static List<Entry> toEntries(Map<ItemKey, Integer> map) {
        List<Entry> result = new ArrayList<>();
        if (map == null) return result;
        for (Map.Entry<ItemKey, Integer> e : map.entrySet()) {
            if (e.getKey() == null || e.getKey() == ItemKey.EMPTY) continue;
            int amount = Math.max(1, e.getValue());
            result.add(new Entry(e.getKey(), amount));
        }
        return result;
    }

    /** Normalize stacks so that NBT/metadata rules match crafter matching logic. */
    public static ItemStack normalizeForKey(@Nullable ItemStack source) {
        if (source == null || source.isEmpty()) return ItemStack.EMPTY;
        if (isCrystal(source)) {
            Aspect aspect = crystalAspect(source);
            return aspect == null ? ItemStack.EMPTY : ThaumcraftApiHelper.makeCrystal(aspect, 1);
        }
        if (source.getMaxStackSize() == 1) {
            return new ItemStack(source.getItem(), 1, source.getMetadata());
        }
        ItemStack copy = source.copy();
        copy.setCount(1);
        return copy;
    }

    private static boolean isCrystal(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() == ItemsTC.crystalEssence;
    }

    @Nullable
    private static Aspect crystalAspect(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem() != ItemsTC.crystalEssence) return null;
        thaumcraft.api.aspects.AspectList al = ((thaumcraft.common.items.ItemTCEssentiaContainer) ItemsTC.crystalEssence)
                .getAspects(stack);
        if (al != null && al.size() == 1) return al.getAspects()[0];
        return null;
    }
}