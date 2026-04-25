package therealpant.thaumicattempts.golemnet.cloud;

import javax.annotation.Nullable;
import java.util.UUID;

public class ProviderChannel {
    private final String channelId;
    @Nullable
    private final UUID golemId;
    @Nullable
    private UUID busyTaskId;
    private long lastProgressTick;
    private ProviderChannelStatus status;

    public ProviderChannel(String channelId, @Nullable UUID golemId, @Nullable UUID busyTaskId, long lastProgressTick, ProviderChannelStatus status) {
        this.channelId = channelId == null ? "default" : channelId;
        this.golemId = golemId;
        this.busyTaskId = busyTaskId;
        this.lastProgressTick = Math.max(0L, lastProgressTick);
        this.status = status == null ? ProviderChannelStatus.READY : status;
    }

    public String getChannelId() {
        return channelId;
    }

    @Nullable
    public UUID getGolemId() {
        return golemId;
    }

    @Nullable
    public UUID getBusyTaskId() {
        return busyTaskId;
    }

    public long getLastProgressTick() {
        return lastProgressTick;
    }

    public ProviderChannelStatus getStatus() {
        return status;
    }

    public boolean isFree() {
        return busyTaskId == null;
    }

    public void markBusy(UUID taskId, long tick) {
        this.busyTaskId = taskId;
        this.lastProgressTick = tick;
        this.status = ProviderChannelStatus.BUSY;
    }

    public void markProgress(long tick) {
        this.lastProgressTick = tick;
        if (busyTaskId == null) this.status = ProviderChannelStatus.READY;
        else this.status = ProviderChannelStatus.BUSY;
    }

    public void markStalled(long tick) {
        this.lastProgressTick = tick;
        this.status = ProviderChannelStatus.STALLED;
    }

    public void release(long tick) {
        this.busyTaskId = null;
        this.lastProgressTick = tick;
        this.status = ProviderChannelStatus.READY;
    }
}