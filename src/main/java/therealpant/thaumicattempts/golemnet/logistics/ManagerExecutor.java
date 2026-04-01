package therealpant.thaumicattempts.golemnet.logistics;

import therealpant.thaumicattempts.golemnet.tile.TileMirrorManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class ManagerExecutor implements ILogisticsExecutor<TransferTask> {
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
        if (!canAccept(task)) return false;
        task.status = TaskStatus.ACCEPTED;
        running.put(task.taskId, task);
        snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, TaskStatus.ACCEPTED, task.completedAmount));
        return true;
    }

    @Override
    public TaskExecutionSnapshot getSnapshot(UUID taskId) { return snapshots.get(taskId); }

    @Override
    public void tick() {
        if (manager == null) return;
        for (Map.Entry<UUID, TransferTask> e : running.entrySet()) {
            TransferTask task = e.getValue();
            task.status = TaskStatus.IN_PROGRESS;
            int moved = manager.executeTransferTask(task.itemKey, (int) Math.max(1, task.amount - task.completedAmount), task.source.pos, task.target.pos);
            if (moved > 0) task.completedAmount += moved;
            if (task.completedAmount >= task.amount) {
                task.status = TaskStatus.DONE;
            } else if (moved == 0) {
                task.status = TaskStatus.BLOCKED;
            }
            snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
        }
    }

    @Override
    public boolean accepts(RuntimeTask task) { return task instanceof TransferTask; }
}