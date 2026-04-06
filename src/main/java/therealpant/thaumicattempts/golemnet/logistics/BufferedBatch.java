package therealpant.thaumicattempts.golemnet.logistics;

import therealpant.thaumicattempts.util.ItemKey;

import java.util.UUID;

public class BufferedBatch {
    public UUID batchId;
    public UUID reservationId;
    public UUID orderId;
    public ItemKey itemKey;
    public int amount;
    public int slot;
}