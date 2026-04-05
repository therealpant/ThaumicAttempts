package therealpant.thaumicattempts.golemnet.logistics;

import therealpant.thaumicattempts.golemnet.tile.TileMirrorManager;
import therealpant.thaumicattempts.util.ItemKey;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static therealpant.thaumicattempts.integration.ThaumcraftCompat.LOG;

public class ManagerExecutor implements ILogisticsExecutor<TransferTask> {

    private TileMirrorManager manager;
    private final LinkedHashMap<UUID, TransferTask> running = new LinkedHashMap<UUID, TransferTask>();
    private final LinkedHashMap<UUID, TaskExecutionSnapshot> snapshots = new LinkedHashMap<UUID, TaskExecutionSnapshot>();
    private final LinkedHashMap<UUID, Integer> inboundBaseline = new LinkedHashMap<UUID, Integer>();

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

            if (task.status == TaskStatus.DONE
                    || task.status == TaskStatus.FAILED
                    || task.status == TaskStatus.CANCELED) {
                snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                it.remove();
                inboundBaseline.remove(task.taskId);
                continue;
            }

            if (task.itemKey == null || task.itemKey == ItemKey.EMPTY || task.source == null || task.target == null) {
                task.status = TaskStatus.FAILED;
                LOG.warn("[ManagerExecutor {}] task={} status=FAILED reason=invalid-task source={} target={} key={}",
                        manager.getPos(), task.taskId, task.source, task.target, task.itemKey);
                snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                it.remove();
                inboundBaseline.remove(task.taskId);
                continue;
            }

            if (task.sourceBaseline < 0) {
                task.sourceBaseline = manager.countItemAtEndpoint(task.source, task.itemKey);
            }
            if (task.targetBaseline < 0) {
                task.targetBaseline = manager.countItemAtEndpoint(task.target, task.itemKey);
            }

            /*
             * completedAmount считаем только по реально наблюдаемому количеству в target.
             * queuedToTarget используем только как защиту от повторной отправки.
             */
            task.completedAmount = calculateDeliveredToTarget(task);

            int queuedToTarget = manager.countQueuedForEndpoint(task.target, task.itemKey);
            int stillNeed = (int) Math.max(0L, task.amount - task.completedAmount - queuedToTarget);

            if (stillNeed > 0) {
                int queueId = ensureQueueId(task);
                boolean accepted = manager.dispatchTransferTask(
                        task.source,
                        task.target,
                        task.itemKey,
                        stillNeed,
                        queueId
                );

                if (!accepted) {
                    task.status = TaskStatus.BLOCKED;
                    LOG.info("[ManagerExecutor {}] task={} status=BLOCKED reason=dispatch-rejected amount={} completed={} queued={} remaining={}",
                            manager.getPos(), task.taskId, task.amount, task.completedAmount, queuedToTarget,
                            Math.max(0L, task.amount - task.completedAmount));
                    snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                    continue;
                }

                task.dispatchQueued = true;
                task.dispatchQueueId = queueId;
                task.updatedTick = manager.getServerTickCounter();

                LOG.info("[ManagerExecutor {}] task={} dispatch amount={} completed={} queued={} remaining={}",
                        manager.getPos(), task.taskId, task.amount, task.completedAmount, queuedToTarget,
                        Math.max(0L, task.amount - task.completedAmount));
            }

            task.completedAmount = calculateDeliveredToTarget(task);

            if (task.completedAmount >= task.amount) {
                task.status = TaskStatus.DONE;
                LOG.info("[ManagerExecutor {}] task={} status=DONE reason=target-amount-reached amount={} completed={} queued={} remaining=0",
                        manager.getPos(), task.taskId, task.amount, task.completedAmount,
                        manager.countQueuedForEndpoint(task.target, task.itemKey));
                snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                it.remove();
                inboundBaseline.remove(task.taskId);
            } else {
                task.status = TaskStatus.IN_PROGRESS;
                LOG.info("[ManagerExecutor {}] task={} wait amount={} completed={} queued={} remaining={}",
                        manager.getPos(), task.taskId, task.amount, task.completedAmount,
                        manager.countQueuedForEndpoint(task.target, task.itemKey),
                        Math.max(0L, task.amount - task.completedAmount));
                snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
            }
        }
    }

    @Override
    public boolean accepts(RuntimeTask task) { return task instanceof TransferTask; }

    private void LOG(TransferTask task, String msg) {
        if (manager == null || task == null) return;
        org.apache.logging.log4j.LogManager.getLogger("ThaumicAttempts/ManagerExecutor")
                .info("[ManagerExecutor {}] task={} {}", manager.getPos(), task.taskId, msg);
    }

    private int ensureQueueId(TransferTask task) {
        if (task.legacyDeliveryQueueId > 0) return task.legacyDeliveryQueueId;
        int queueId = Math.abs(task.taskId.hashCode());
        if (queueId == 0) queueId = 1;
        task.legacyDeliveryQueueId = queueId;
        return queueId;
    }

    private boolean isInboundDone(TransferTask task) {
        if (task == null) return false;
        long delivered = calculateDeliveredToTarget(task);
        return delivered >= Math.max(1L, task.amount);
    }

    private long calculateDeliveredToTarget(TransferTask task) {
        if (task == null || task.target == null || task.itemKey == null) return 0L;

        int nowAtTarget = manager.countItemAtEndpoint(task.target, task.itemKey);
        int movedToTarget = Math.max(0, nowAtTarget - Math.max(0, task.targetBaseline));

        long observedAtTarget = movedToTarget;
        observedAtTarget = Math.max(observedAtTarget, task.completedAmount);

        return Math.min(task.amount, observedAtTarget);
    }
}