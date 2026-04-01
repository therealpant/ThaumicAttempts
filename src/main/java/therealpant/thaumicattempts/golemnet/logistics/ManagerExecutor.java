package therealpant.thaumicattempts.golemnet.logistics;

import therealpant.thaumicattempts.golemnet.tile.TileMirrorManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class ManagerExecutor implements ILogisticsExecutor<TransferTask> {
    private static final int BLOCKED_COOLDOWN_TICKS = 10;

    private TileMirrorManager manager;
    private final LinkedHashMap<UUID, TransferTask> running = new LinkedHashMap<UUID, TransferTask>();
    private final LinkedHashMap<UUID, TaskExecutionSnapshot> snapshots = new LinkedHashMap<UUID, TaskExecutionSnapshot>();
    private final LinkedHashMap<UUID, Long> targetBaseline = new LinkedHashMap<UUID, Long>();
    private final LinkedHashMap<UUID, Long> blockedUntil = new LinkedHashMap<UUID, Long>();

    public void bind(TileMirrorManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean canAccept(TransferTask task) { return manager != null && task != null; }

    @Override
    public boolean submit(TransferTask task) {
        if (!canAccept(task)) return false;
        if (running.containsKey(task.taskId)) return true;
        if (task.status == TaskStatus.DONE || task.status == TaskStatus.FAILED || task.status == TaskStatus.CANCELED) return false;
        long base = manager.countItemAtEndpoint(task.target, task.itemKey);
        targetBaseline.put(task.taskId, base);
        task.status = TaskStatus.ACCEPTED;
        running.put(task.taskId, task);
        LOG(task, "transfer accepted");
        snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, TaskStatus.ACCEPTED, task.completedAmount));
        return true;
    }

    @Override
    public TaskExecutionSnapshot getSnapshot(UUID taskId) { return snapshots.get(taskId); }

    @Override
    public void tick() {
        if (manager == null) return;
        long now = manager.getServerTickCounter();
        java.util.Iterator<Map.Entry<UUID, TransferTask>> it = running.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, TransferTask> e = it.next();
            TransferTask task = e.getValue();
            if (task.status == TaskStatus.DONE || task.status == TaskStatus.FAILED || task.status == TaskStatus.CANCELED) {
                snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                LOG(task, "transfer removed from running status=" + task.status);
                it.remove();
                targetBaseline.remove(task.taskId);
                blockedUntil.remove(task.taskId);
                continue;
            }

            long remaining = Math.max(0L, task.amount - task.completedAmount);
            if (remaining <= 0L) {
                task.status = TaskStatus.DONE;
                snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                LOG(task, "transfer done");
                LOG(task, "transfer removed from running");
                it.remove();
                targetBaseline.remove(task.taskId);
                blockedUntil.remove(task.taskId);
                continue;
            }

            long blockedUntilTick = blockedUntil.getOrDefault(task.taskId, 0L);
            if (task.status == TaskStatus.BLOCKED && now < blockedUntilTick) {
                snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                continue;
            }

            task.status = TaskStatus.IN_PROGRESS;
            if (!snapshots.containsKey(task.taskId)) {
                snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, TaskStatus.ACCEPTED, task.completedAmount));
            }

            LOG(task, "transfer submit item=" + task.itemKey + " amount=" + remaining + " source=" + manager.resolveEndpointPos(task.source) + " target=" + manager.resolveEndpointPos(task.target));
            boolean accepted = manager.dispatchTransferTask(task.source, task.target, task.itemKey, (int) Math.min(Integer.MAX_VALUE, remaining), 0);

            long baseline = targetBaseline.getOrDefault(task.taskId, 0L);
            long atTarget = manager.countItemAtEndpoint(task.target, task.itemKey);
            long delivered = Math.max(0L, atTarget - baseline);
            long newCompleted = Math.min(task.amount, delivered);
            long moved = Math.max(0L, newCompleted - task.completedAmount);
            task.completedAmount = newCompleted;

            if (moved > 0) {
                LOG(task, "transfer moved " + moved);
            }

            if (task.completedAmount >= task.amount) {
                task.status = TaskStatus.DONE;
                LOG(task, "transfer done");
                snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                LOG(task, "transfer removed from running");
                it.remove();
                targetBaseline.remove(task.taskId);
                blockedUntil.remove(task.taskId);
                continue;
            }

            if (!accepted && moved == 0) {
                task.status = TaskStatus.BLOCKED;
                blockedUntil.put(task.taskId, now + BLOCKED_COOLDOWN_TICKS);
                LOG(task, "transfer blocked reason=no-source-or-no-path");
            }
            snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
        }
    }

    @Override
    public boolean accepts(RuntimeTask task) { return task instanceof TransferTask; }

    private void LOG(TransferTask task, String msg) {
        if (manager == null || task == null) return;
        org.apache.logging.log4j.LogManager.getLogger("ThaumicAttempts/ManagerExecutor")
                .info("[ManagerExecutor {}] task={} {}", manager.getPos(), task.taskId, msg);
    }
}