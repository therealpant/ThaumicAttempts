package therealpant.thaumicattempts.golemnet.cloud;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import therealpant.thaumicattempts.util.ItemKey;

import javax.annotation.Nullable;
import java.util.UUID;

public class CloudTask {
    private UUID taskId;
    private UUID orderId;
    private CloudTaskKind kind;
    private ItemKey itemKey;
    private int amount;
    private CloudEndpointRef source;
    private CloudEndpointRef target;
    private BlockPos assignedProviderPos;
    private BlockPos assignedConsumerPos;
    private CloudTaskStatus status;
    private long createdTick;
    private long startedTick;
    private long updatedTick;
    private int completedAmount;
    private String failReason;
    private int retryCount;

    public CloudTask(UUID taskId, UUID orderId, CloudTaskKind kind, ItemKey itemKey, int amount, long createdTick) {
        this.taskId = taskId == null ? UUID.randomUUID() : taskId;
        this.orderId = orderId;
        this.kind = kind == null ? CloudTaskKind.TRANSFER : kind;
        this.itemKey = itemKey;
        this.amount = Math.max(0, amount);
        this.createdTick = createdTick;
        this.updatedTick = createdTick;
        this.status = CloudTaskStatus.NEW;
        this.failReason = "";
    }

    public UUID getTaskId() { return taskId; }
    public UUID getOrderId() { return orderId; }
    public CloudTaskKind getKind() { return kind; }
    public ItemKey getItemKey() { return itemKey; }
    public int getAmount() { return amount; }
    @Nullable public CloudEndpointRef getSource() { return source; }
    @Nullable public CloudEndpointRef getTarget() { return target; }
    @Nullable public BlockPos getAssignedProviderPos() { return assignedProviderPos; }
    @Nullable public BlockPos getAssignedConsumerPos() { return assignedConsumerPos; }
    public CloudTaskStatus getStatus() { return status; }
    public long getCreatedTick() { return createdTick; }
    public long getStartedTick() { return startedTick; }
    public long getUpdatedTick() { return updatedTick; }
    public int getCompletedAmount() { return completedAmount; }
    public String getFailReason() { return failReason; }
    public int getRetryCount() { return retryCount; }

    public void setStatus(CloudTaskStatus status, long tick) {
        this.status = status == null ? CloudTaskStatus.NEW : status;
        this.updatedTick = tick;
        if (this.status == CloudTaskStatus.RUNNING && startedTick <= 0L) startedTick = tick;
    }

    public void setEndpoints(@Nullable CloudEndpointRef source, @Nullable CloudEndpointRef target) {
        this.source = source;
        this.target = target;
    }

    public void setAssignments(@Nullable BlockPos providerPos, @Nullable BlockPos consumerPos) {
        this.assignedProviderPos = providerPos == null ? null : providerPos.toImmutable();
        this.assignedConsumerPos = consumerPos == null ? null : consumerPos.toImmutable();
    }

    public void setCompletedAmount(int completedAmount, long tick) {
        this.completedAmount = Math.max(0, completedAmount);
        this.updatedTick = tick;
    }

    public void fail(String reason, long tick) {
        this.failReason = reason == null ? "" : reason;
        this.status = CloudTaskStatus.FAILED;
        this.updatedTick = tick;
        this.retryCount++;
    }

    public NBTTagCompound serializeNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setUniqueId("taskId", taskId);
        if (orderId != null) nbt.setUniqueId("orderId", orderId);
        nbt.setString("kind", kind.name());
        MirrorLogisticsCloud.writeItemKey(nbt, "item", itemKey);
        nbt.setInteger("amount", amount);
        if (source != null) nbt.setTag("source", source.serializeNBT());
        if (target != null) nbt.setTag("target", target.serializeNBT());
        if (assignedProviderPos != null) nbt.setLong("assignedProvider", assignedProviderPos.toLong());
        if (assignedConsumerPos != null) nbt.setLong("assignedConsumer", assignedConsumerPos.toLong());
        nbt.setString("status", status.name());
        nbt.setLong("createdTick", createdTick);
        nbt.setLong("startedTick", startedTick);
        nbt.setLong("updatedTick", updatedTick);
        nbt.setInteger("completedAmount", completedAmount);
        nbt.setString("failReason", failReason == null ? "" : failReason);
        nbt.setInteger("retryCount", retryCount);
        return nbt;
    }

    public static CloudTask deserializeNBT(NBTTagCompound nbt) {
        if (nbt == null) return null;
        UUID taskId = nbt.hasUniqueId("taskId") ? nbt.getUniqueId("taskId") : UUID.randomUUID();
        UUID orderId = nbt.hasUniqueId("orderId") ? nbt.getUniqueId("orderId") : null;
        CloudTaskKind kind = MirrorLogisticsCloud.safeEnum(CloudTaskKind.class, nbt.getString("kind"), CloudTaskKind.TRANSFER);
        CloudTask task = new CloudTask(taskId, orderId, kind,
                MirrorLogisticsCloud.readItemKey(nbt, "item"), nbt.getInteger("amount"), nbt.getLong("createdTick"));

        task.source = nbt.hasKey("source", 10) ? CloudEndpointRef.deserializeNBT(nbt.getCompoundTag("source")) : null;
        task.target = nbt.hasKey("target", 10) ? CloudEndpointRef.deserializeNBT(nbt.getCompoundTag("target")) : null;
        task.assignedProviderPos = nbt.hasKey("assignedProvider") ? BlockPos.fromLong(nbt.getLong("assignedProvider")) : null;
        task.assignedConsumerPos = nbt.hasKey("assignedConsumer") ? BlockPos.fromLong(nbt.getLong("assignedConsumer")) : null;
        task.status = MirrorLogisticsCloud.safeEnum(CloudTaskStatus.class, nbt.getString("status"), CloudTaskStatus.NEW);
        task.startedTick = nbt.getLong("startedTick");
        task.updatedTick = nbt.getLong("updatedTick");
        task.completedAmount = nbt.getInteger("completedAmount");
        task.failReason = nbt.getString("failReason");
        task.retryCount = nbt.getInteger("retryCount");
        return task;
    }
}