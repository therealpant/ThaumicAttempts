package therealpant.thaumicattempts.golemnet.planner;

import therealpant.thaumicattempts.util.ItemKey;

public final class RequiredInput {
    private final ItemKey key;
    private final int count;

    public RequiredInput(ItemKey key, int count) {
        this.key = key == null ? ItemKey.EMPTY : key;
        this.count = Math.max(1, count);
    }

    public ItemKey getKey() {
        return key;
    }

    public int getCount() {
        return count;
    }
}
