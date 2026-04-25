package therealpant.thaumicattempts.api;

import net.minecraft.item.ItemStack;
import therealpant.thaumicattempts.golemnet.cloud.CloudEndpointRef;
import therealpant.thaumicattempts.util.ItemKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
/**
 * Unified craft-consumer contract for MirrorLogisticsCloud CRAFT workflow.
 */
public interface ICloudCraftConsumer extends ICraftEndpoint {

    /** Inputs required for one craft cycle of resultLike, aggregated by ItemKey. */
    Map<ItemKey, Integer> getInputsPerCycle(ItemStack resultLike);

    /** Queue cloud craft task for N cycles and bind it to taskId. Returns accepted cycle count. */
    int enqueueCloudCraft(ItemStack resultLike, int cycles, UUID taskId);

    /** True while this consumer still tracks taskId as active/queued. */
    boolean hasCloudCraftTask(UUID taskId);

    /** Current amount of matching output items present in output endpoint inventory. */
    int getOutputCount(ItemKey key);

    /** Endpoint where cloud should deliver recipe inputs. */
    CloudEndpointRef getInputEndpoint();

    /** Endpoint where cloud should pick crafted outputs. */
    CloudEndpointRef getOutputEndpoint();

    // ---- Backward-compatible adapters for legacy call-sites ----
    @Deprecated
    default List<ItemStack> getRecipeInputsPerCycle(ItemStack resultLike) {
        Map<ItemKey, Integer> mapped = getInputsPerCycle(resultLike);
        if (mapped == null || mapped.isEmpty()) return java.util.Collections.emptyList();
        ArrayList<ItemStack> out = new ArrayList<>();
        for (Map.Entry<ItemKey, Integer> e : mapped.entrySet()) {
            if (e == null || e.getKey() == null || e.getKey() == ItemKey.EMPTY) continue;
            ItemStack st = e.getKey().toStack(Math.max(1, e.getValue()));
            if (!st.isEmpty()) out.add(st);
        }
        return out;
    }

    @Deprecated
    default int countOutput(ItemKey key) {
        return getOutputCount(key);
    }

    @Deprecated
    @Override
    default void enqueueCraft(ItemStack resultLike, int crafts) {
        enqueueCloudCraft(resultLike, crafts, UUID.randomUUID());
    }
}