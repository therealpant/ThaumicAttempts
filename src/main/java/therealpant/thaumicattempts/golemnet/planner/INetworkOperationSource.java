package therealpant.thaumicattempts.golemnet.planner;

import therealpant.thaumicattempts.util.ItemKey;

import java.util.List;

public interface INetworkOperationSource {
    List<ItemKey> getProvidedResults();
    int getOutputCountFor(ItemKey key);
    List<RequiredInput> getRequiredInputsFor(ItemKey key, int times);
    boolean enqueueExecution(ItemKey key, int times);
    ProviderType getType();
    String getDebugName();
}
