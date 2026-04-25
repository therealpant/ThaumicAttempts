package therealpant.thaumicattempts.api;

import net.minecraft.item.ItemStack;
import therealpant.thaumicattempts.golemnet.cloud.CloudEndpointRef;
import therealpant.thaumicattempts.util.ItemKey;

import java.util.List;

/**
 * Unified craft-consumer contract for MirrorLogisticsCloud CRAFT workflow.
 */
public interface ICloudCraftConsumer extends ICraftEndpoint {

    /** Inputs required for one craft cycle of resultLike (already aggregated by same matching rules). */
    List<ItemStack> getRecipeInputsPerCycle(ItemStack resultLike);

    /** Endpoint where cloud should deliver recipe inputs. */
    CloudEndpointRef getInputEndpoint();

    /** Endpoint where cloud should pick crafted outputs. */
    CloudEndpointRef getOutputEndpoint();

    /** Current amount of matching output items present in output endpoint inventory. */
    int countOutput(ItemKey key);
}