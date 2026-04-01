package therealpant.thaumicattempts.golemnet.logistics;

import therealpant.thaumicattempts.golemnet.tile.TileMirrorManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class CrafterExecutor implements ILogisticsExecutor<CraftTask> {
    private TileMirrorManager manager;
    private final LinkedHashMap<UUID, CraftTask> running = new LinkedHashMap<UUID, CraftTask>();
    private final LinkedHashMap<UUID, TaskExecutionSnapshot> snapshots = new LinkedHashMap<UUID, TaskExecutionSnapshot>();

    public void bind(TileMirrorManager manager) { this.manager = manager; }

    @Override
    public boolean canAccept(CraftTask task) { return manager != null && task != null; }

    @Override
    public boolean submit(CraftTask task) {
        if (!canAccept(task)) return false;
        boolean accepted = manager.startCraftTask(task.crafter.pos, task.recipeKey, (int) task.amount);
        task.status = accepted ? TaskStatus.ACCEPTED : TaskStatus.FAILED;
        running.put(task.taskId, task);
        snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
        return accepted;
    }

    @Override
    public TaskExecutionSnapshot getSnapshot(UUID taskId) { return snapshots.get(taskId); }

    @Override
    public void tick() {
        if (manager == null) return;
        for (Map.Entry<UUID, CraftTask> e : running.entrySet()) {
            CraftTask task = e.getValue();
            if (task.status == TaskStatus.FAILED || task.status == TaskStatus.DONE) continue;
            task.status = TaskStatus.IN_PROGRESS;
            int readyOut = manager.countItemAt(task.outputEndpoint.pos, task.recipeKey);
            task.completedAmount = Math.max(task.completedAmount, readyOut);
            if (readyOut >= task.amount) {
                task.status = TaskStatus.DONE;
            }
            snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
        }
    }

    @Override
    public boolean accepts(RuntimeTask task) { return task instanceof CraftTask; }
}