package therealpant.thaumicattempts.golemnet.planner;

import therealpant.thaumicattempts.util.ItemKey;

import java.util.HashMap;
import java.util.Map;

public final class PlannerResourceSnapshot {
    private final Map<ItemKey, Integer> available = new HashMap<>();

    public PlannerResourceSnapshot(Map<ItemKey, Integer> seed) {
        if (seed != null) available.putAll(seed);
    }

    public int getAvailable(ItemKey key) {
        if (key == null) return 0;
        return Math.max(0, available.getOrDefault(key, 0));
    }

    public int consumeUpTo(ItemKey key, int amount) {
        if (key == null || amount <= 0) return 0;
        int have = getAvailable(key);
        int take = Math.min(have, amount);
        if (take > 0) available.put(key, have - take);
        return take;
    }

    public void produce(ItemKey key, int amount) {
        if (key == null || amount <= 0) return;
        available.merge(key, amount, Integer::sum);
    }
}