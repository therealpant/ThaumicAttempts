package therealpant.thaumicattempts.golemnet.net.msg;

import net.minecraft.item.ItemStack;

import java.util.Collections;
import java.util.List;

public final class PageResult {
    public int pageNo1 = 1;
    public int totalPages = 0;
    public boolean totalPagesKnown = false;
    public boolean hasMoreHeuristic = false;

    public List<ItemStack> items = Collections.emptyList();
    public List<Integer> counts = Collections.emptyList();
    public List<Integer> makeCounts = Collections.emptyList();
    public List<Boolean> makePossible = Collections.emptyList();
    public int itemCountOnPage = 0;

    public static PageResult emptyUnknown() { return new PageResult(); }
}