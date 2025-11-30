package therealpant.thaumicattempts.api;

import net.minecraft.item.ItemStack;
import java.util.List;

/**
 * Public contract for items that describe crafting/resource patterns.
 * Implementors should return a normalized list of required ingredients
 * that other blocks (requesters, crafters) can use to schedule deliveries.
 */
public interface IPatternResourceProvider {
    /**
     * Build a list of required resources for the given pattern stack.
     * Counts should reflect the amount needed for a single craft/order.
     */
    List<PatternResourceList.Entry> buildResourceList(ItemStack pattern);
}
