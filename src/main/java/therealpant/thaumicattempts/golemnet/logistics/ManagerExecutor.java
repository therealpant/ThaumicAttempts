package therealpant.thaumicattempts.golemnet.logistics;

import therealpant.thaumicattempts.golemnet.tile.TileMirrorManager;
import therealpant.thaumicattempts.util.ItemKey;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static therealpant.thaumicattempts.integration.ThaumcraftCompat.LOG;

public class ManagerExecutor implements ILogisticsExecutor<TransferTask> {

    private static final int DISPATCH_COOLDOWN_TICKS = 10;
    private static final int STALLED_LIMIT_TICKS = 100;

    private TileMirrorManager manager;
    private final LinkedHashMap<UUID, TransferTask> running = new LinkedHashMap<UUID, TransferTask>();
    private final LinkedHashMap<UUID, TaskExecutionSnapshot> snapshots = new LinkedHashMap<UUID, TaskExecutionSnapshot>();

    public void bind(TileMirrorManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean canAccept(TransferTask task) { return manager != null && task != null; }

    @Override
    public boolean submit(TransferTask task) {
        if (task == null) return false;
        if (running.containsKey(task.taskId)) return true;

        task.dispatchQueued = false;
        task.dispatchQueueId = -1;

        task.sourceBaseline = -1;
        task.targetBaseline = -1;
        task.bufferBaseline = -1;

        task.inboundQueued = false;
        task.inboundDone = false;
        task.outboundQueued = false;
        task.outboundDone = false;

        task.legacyDeliveryQueued = false;
        task.legacyDeliveryQueueId = -1;

        long nowTick = manager == null ? 0L : manager.getServerTickCounter();
        task.lastProgressTick = nowTick;
        task.lastDispatchTick = 0L;
        task.stalledTicks = 0;
        task.lastRemaining = -1L;

        running.put(task.taskId, task);
        snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, TaskStatus.DISPATCHED, task.completedAmount));
        LOG.info("[ManagerExecutor {}] task={} transfer accepted", manager != null ? manager.getPos() : null, task.taskId);
        return true;
    }

    public boolean isRunning(UUID taskId) {
        return taskId != null && running.containsKey(taskId);
    }

    @Override
    public TaskExecutionSnapshot getSnapshot(UUID taskId) { return snapshots.get(taskId); }

    @Override
    public void tick() {
        if (manager == null) return;

        Iterator<Map.Entry<UUID, TransferTask>> it = running.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, TransferTask> e = it.next();
            TransferTask task = e.getValue();

            if (task == null) {
                it.remove();
                continue;
            }

            if (isTerminal(task.status)) {
                snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                it.remove();
                continue;
            }

            if (task.itemKey == null || task.itemKey == ItemKey.EMPTY || task.source == null || task.target == null) {
                task.status = TaskStatus.FAILED;
                LOG.warn("[ManagerExecutor {}] task={} status=FAILED reason=invalid-task source={} target={} key={}",
                        manager.getPos(), task.taskId, task.source, task.target, task.itemKey);
                snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                it.remove();
                continue;
            }

            long previousCompleted = task.completedAmount;
            task.completedAmount = calculateDeliveredToTarget(task);

            if (task.completedAmount > previousCompleted) {
                task.lastProgressTick = manager.getServerTickCounter();
            }

            long remaining = Math.max(0L, task.amount - task.completedAmount);
            int queued = manager.countQueuedForEndpoint(task.target, task.itemKey);

            if (remaining == task.lastRemaining) {
                task.stalledTicks++;
            } else {
                task.stalledTicks = 0;
                task.lastRemaining = remaining;
                task.lastProgressTick = manager.getServerTickCounter();
            }

            if (task.completedAmount >= task.amount) {
                transitionStatus(task, TaskStatus.DONE, "target-reached");
                logTransferDebug(task, remaining, queued);
                snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                it.remove();
                continue;
            }

            if (task.stalledTicks > STALLED_LIMIT_TICKS) {
                transitionStatus(task, TaskStatus.BLOCKED, "stalled");
                logTransferDebug(task, remaining, queued);
                snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                continue;
            }
            boolean sourceReady = manager.hasAvailableItems(task.source, task.itemKey);
            if (!sourceReady) {
                transitionStatus(task, TaskStatus.WAITING_SOURCE, "source-empty");
                logTransferDebug(task, remaining, queued);
                snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                continue;
            }

            long nowTick = manager.getServerTickCounter();
            if (nowTick - task.lastDispatchTick >= DISPATCH_COOLDOWN_TICKS) {
                long need = Math.max(0L, remaining - queued);
                if (need > 0L) {
                    int queueId = ensureQueueId(task);
                    boolean accepted = manager.dispatchTransferTask(task.source, task.target, task.itemKey, (int) Math.min(Integer.MAX_VALUE, need), queueId);
                    if (accepted) {
                        task.lastDispatchTick = nowTick;
                        transitionStatus(task, TaskStatus.DISPATCHED, "dispatch-accepted");
                    } else {
                        transitionStatus(task, TaskStatus.BLOCKED, "dispatch-rejected");
                    }
                }
            }

            if (!isTerminal(task.status) && task.status != TaskStatus.DISPATCHED && task.status != TaskStatus.WAITING_SOURCE) {
                transitionStatus(task, TaskStatus.IN_PROGRESS, "awaiting-delivery");
            }

            logTransferDebug(task, remaining, queued);
            snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
        }
    }

    @Override
    public boolean accepts(RuntimeTask task) { return task instanceof TransferTask; }

    private void transitionStatus(TransferTask task, TaskStatus next, String reason) {
        if (task == null || next == null) return;
        TaskStatus prev = task.status;
        if (prev == next) return;
        task.status = next;
        task.updatedTick = manager != null ? manager.getServerTickCounter() : task.updatedTick;
        LOG.info("[TRANSFER STATE] task={} {} -> {} reason={} item={}",
                task.taskId,
                prev,
                next,
                reason,
                task.itemKey);
    }

    private void logTransferDebug(TransferTask task, long remaining, int queued) {
        if (task == null || manager == null) return;
        LOG.info("[TRANSFER DEBUG] task={} item={} remaining={} queued={} delivered={} stalledTicks={} state={}",
                task.taskId,
                task.itemKey,
                Math.max(0L, remaining),
                Math.max(0, queued),
                task.completedAmount,
                task.stalledTicks,
                task.status);
    }

    private int ensureQueueId(TransferTask task) {
        if (task.legacyDeliveryQueueId > 0) return task.legacyDeliveryQueueId;
        int queueId = Math.abs(task.taskId.hashCode());
        if (queueId == 0) queueId = 1;
        task.legacyDeliveryQueueId = queueId;
        return queueId;
    }

    private long calculateDeliveredToTarget(TransferTask task) {
        if (task == null || task.target == null || task.itemKey == null || manager == null) return 0L;

        int nowAtTarget = manager.countItemAtEndpoint(task.target, task.itemKey);
        if (task.targetBaseline < 0) {
            task.targetBaseline = nowAtTarget;
        }

        long delivered = Math.max(0, nowAtTarget - task.targetBaseline);
        return Math.min(task.amount, delivered);
    }
        private boolean isTerminal(TaskStatus status) {
            return status == TaskStatus.DONE || status == TaskStatus.FAILED || status == TaskStatus.CANCELED;
    }
}