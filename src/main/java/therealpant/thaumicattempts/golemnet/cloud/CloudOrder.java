package therealpant.thaumicattempts.golemnet.cloud;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import therealpant.thaumicattempts.util.ItemKey;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CloudOrder {
    private UUID orderId;
    private CloudOrderKind kind;
    private BlockPos customerPos;
    private CloudEndpointRef destination;
    private ItemKey itemKey;
    private int requestedAmount;
    private long createdTick;
    private long updatedTick;
    private CloudOrderStatus status;
    private String failReason;
    private UUID parentOrderId;
    private final Map<String, String> metadata = new HashMap<>();

    public CloudOrder(UUID orderId, CloudOrderKind kind, BlockPos customerPos, CloudEndpointRef destination,
                      ItemKey itemKey, int requestedAmount, long createdTick) {
        this.orderId = orderId == null ? UUID.randomUUID() : orderId;
        this.kind = kind == null ? CloudOrderKind.DELIVERY : kind;
        this.customerPos = customerPos == null ? BlockPos.ORIGIN : customerPos.toImmutable();
        this.destination = destination;
        this.itemKey = itemKey;
        this.requestedAmount = Math.max(0, requestedAmount);
        this.createdTick = createdTick;
        this.updatedTick = createdTick;
        this.status = CloudOrderStatus.NEW;
        this.failReason = "";
    }

    public UUID getOrderId() { return orderId; }
    public CloudOrderKind getKind() { return kind; }
    public BlockPos getCustomerPos() { return customerPos; }
    public CloudEndpointRef getDestination() { return destination; }
    public ItemKey getItemKey() { return itemKey; }
    public int getRequestedAmount() { return requestedAmount; }
    public long getCreatedTick() { return createdTick; }
    public long getUpdatedTick() { return updatedTick; }
    public CloudOrderStatus getStatus() { return status; }
    public String getFailReason() { return failReason; }
    @Nullable public UUID getParentOrderId() { return parentOrderId; }
    public Map<String, String> getMetadata() { return Collections.unmodifiableMap(metadata); }

    public void setStatus(CloudOrderStatus status, long tick) {
        this.status = status == null ? CloudOrderStatus.NEW : status;
        this.updatedTick = tick;
    }

    public void setFailReason(String failReason, long tick) {
        this.failReason = failReason == null ? "" : failReason;
        this.updatedTick = tick;
    }

    public void setParentOrderId(@Nullable UUID parentOrderId) {
        this.parentOrderId = parentOrderId;
    }

    public void setMetadataValue(String key, String value) {
        if (key == null || key.isEmpty()) return;
        metadata.put(key, value == null ? "" : value);
    }

    public NBTTagCompound serializeNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setUniqueId("orderId", orderId);
        nbt.setString("kind", kind.name());
        nbt.setLong("customerPos", customerPos.toLong());
        if (destination != null) nbt.setTag("destination", destination.serializeNBT());
        MirrorLogisticsCloud.writeItemKey(nbt, "item", itemKey);
        nbt.setInteger("requestedAmount", requestedAmount);
        nbt.setLong("createdTick", createdTick);
        nbt.setLong("updatedTick", updatedTick);
        nbt.setString("status", status.name());
        nbt.setString("failReason", failReason == null ? "" : failReason);
        if (parentOrderId != null) nbt.setUniqueId("parentOrderId", parentOrderId);

        NBTTagList tags = new NBTTagList();
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            NBTTagCompound pair = new NBTTagCompound();
            pair.setString("k", entry.getKey());
            pair.setString("v", entry.getValue());
            tags.appendTag(pair);
        }
        nbt.setTag("metadata", tags);
        return nbt;
    }

    public static CloudOrder deserializeNBT(NBTTagCompound nbt) {
        if (nbt == null) return null;
        UUID orderId = nbt.hasUniqueId("orderId") ? nbt.getUniqueId("orderId") : UUID.randomUUID();
        CloudOrderKind kind = MirrorLogisticsCloud.safeEnum(CloudOrderKind.class, nbt.getString("kind"), CloudOrderKind.DELIVERY);
        BlockPos customerPos = nbt.hasKey("customerPos") ? BlockPos.fromLong(nbt.getLong("customerPos")) : BlockPos.ORIGIN;
        CloudEndpointRef destination = nbt.hasKey("destination", 10)
                ? CloudEndpointRef.deserializeNBT(nbt.getCompoundTag("destination"))
                : null;
        ItemKey itemKey = MirrorLogisticsCloud.readItemKey(nbt, "item");
        CloudOrder order = new CloudOrder(orderId, kind, customerPos, destination, itemKey,
                nbt.getInteger("requestedAmount"), nbt.getLong("createdTick"));
        order.updatedTick = nbt.getLong("updatedTick");
        order.status = MirrorLogisticsCloud.safeEnum(CloudOrderStatus.class, nbt.getString("status"), CloudOrderStatus.NEW);
        order.failReason = nbt.getString("failReason");
        if (nbt.hasUniqueId("parentOrderId")) order.parentOrderId = nbt.getUniqueId("parentOrderId");

        NBTTagList tags = nbt.getTagList("metadata", 10);
        for (int i = 0; i < tags.tagCount(); i++) {
            NBTTagCompound pair = tags.getCompoundTagAt(i);
            String k = pair.getString("k");
            if (!k.isEmpty()) order.metadata.put(k, pair.getString("v"));
        }
        return order;
    }
}