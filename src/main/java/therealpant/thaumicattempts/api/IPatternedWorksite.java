package therealpant.thaumicattempts.api;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;

/**
 * Public contract for blocks that consume pattern/resource-list items
 * (golem crafter, arcane crafter, resource requester).
 * <p>
 * The goal is to expose a stable surface for pattern_requester and external
 * automation to:
 * <ul>
 *     <li>discover the pattern inventory,</li>
 *     <li>enqueue work as if a pattern_requester/manager triggered it,</li>
 *     <li>propagate mirror-manager bindings that should suppress self-provision,</li>
 *     <li>know how redstone triggers are interpreted,</li>
 *     <li>learn how the block publishes provisioning tasks for golems.</li>
 * </ul>
 * Existing tiles implement this contract without changing their vanilla
 * behaviour; the interface is strictly descriptive and delegates to the
 * current logic in each tile.
 */
public interface IPatternedWorksite {

    /** @return live handler with pattern stacks. Slots follow the tile's rules. */
    IItemHandler getPatternHandler();

    /**
     * Enqueue work by pattern slot as if requested by a PatternRequester/manager.
     * Implementations should clamp slot indices and respect internal queue limits.
     *
     * @return how many logical starts were accepted (0 if rejected)
     */
    int enqueueFromPatternRequester(int patternSlot, int times);

    /**
     * Enqueue work for a pattern that produces the given result-like stack.
     * Implementations may fall back to slot-based scheduling if result matching
     * is not supported.
     *
     * @return how many logical starts were accepted (0 if unsupported)
     */
    int enqueueFromPatternRequester(ItemStack resultLike, int times);

    /**
     * Propagate manager binding from the PatternRequester above this block.
     * Passing {@code null} should clear the binding.
     */
    void setManagerPosFromPattern(@Nullable BlockPos managerPos);

    /**
     * Current manager binding, or {@code null} if none. Used by requesters to
     * mirror manager attachments when stacked above the worksite.
     */
    @Nullable BlockPos getManagerPosForPattern();

    /**
     * Describe how the tile interprets redstone. This is a constant contract
     * (e.g., rising edge vs. level queue) and does not change at runtime.
     */
    PatternRedstoneMode getRedstoneMode();

    /**
     * Provisioning details: how this block issues golem delivery tasks when it
     * is not backed by a pattern manager.
     */
    PatternProvisioningSpec getProvisioningSpec();
}