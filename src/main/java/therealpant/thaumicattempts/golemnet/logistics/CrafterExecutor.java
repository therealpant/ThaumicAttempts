package therealpant.thaumicattempts.golemnet.logistics;

import therealpant.thaumicattempts.golemnet.tile.TileMirrorManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class CrafterExecutor implements ILogisticsExecutor<CraftTask> {
    private TileMirrorManager manager;
    private final LinkedHashMap<UUID, CraftTask> running = new LinkedHashMap<UUID, CraftTask>();
    private final LinkedHashMap<UUID, TaskExecutionSnapshot> snapshots = new LinkedHashMap<UUID, TaskExecutionSnapshot>();
    private final LinkedHashMap<UUID, Boolean> started = new LinkedHashMap<UUID, Boolean>();
    private final LinkedHashMap<UUID, Integer> outputBaseline = new LinkedHashMap<UUID, Integer>();

    public void bind(TileMirrorManager manager) { this.manager = manager; }

    @Override
    public boolean canAccept(CraftTask task) { return manager != null && task != null; }

    @Override
    public boolean submit(CraftTask task) {
        if (!canAccept(task)) return false;
        if (running.containsKey(task.taskId)) return true;
        if (task.crafter == null || task.recipeKey == null || task.recipeKey == therealpant.thaumicattempts.util.ItemKey.EMPTY || task.outputEndpoint == null) {
            task.status = TaskStatus.FAILED;
            org.apache.logging.log4j.LogManager.getLogger("ThaumicAttempts/CrafterExecutor")
                    .warn("[CrafterExecutor {}] task={} craft rejected invalid fields crafter={} key={} output={}",
                            manager.getPos(), task.taskId, task.crafter, task.recipeKey, task.outputEndpoint);
            snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
            return false;
        }
        task.status = TaskStatus.ACCEPTED;
        running.put(task.taskId, task);
        started.put(task.taskId, false);
        outputBaseline.put(task.taskId, manager.countItemAtEndpoint(task.outputEndpoint, task.recipeKey));
        org.apache.logging.log4j.LogManager.getLogger("ThaumicAttempts/CrafterExecutor")
                .info("[CrafterExecutor {}] task={} craft accepted crafter={} key={} amount={}",
                        manager.getPos(), task.taskId, task.crafter.pos, task.recipeKey, task.amount);
        snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
        return true;
    }

    @Override
    public TaskExecutionSnapshot getSnapshot(UUID taskId) { return snapshots.get(taskId); }

    @Override
    public void tick() {
        if (manager == null) return;
        java.util.Iterator<Map.Entry<UUID, CraftTask>> it = running.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, CraftTask> e = it.next();
            CraftTask task = e.getValue();
            if (task.status == TaskStatus.FAILED || task.status == TaskStatus.CANCELED || task.status == TaskStatus.DONE) {
                snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                it.remove();
                started.remove(task.taskId);
                outputBaseline.remove(task.taskId);
                continue;
            }

            boolean isStarted = started.getOrDefault(task.taskId, false);
            if (!isStarted) {
                boolean inputsReady = true;
                for (Map.Entry<therealpant.thaumicattempts.util.ItemKey, Integer> in : task.requiredInputs.entrySet()) {
                    int have = manager.countItemAtEndpoint(task.crafter, in.getKey());
                    if (have < in.getValue()) {
                        inputsReady = false;
                        break;
                    }
                }
                if (!inputsReady) {
                    task.status = TaskStatus.BLOCKED;
                    org.apache.logging.log4j.LogManager.getLogger("ThaumicAttempts/CrafterExecutor")
                            .info("[CrafterExecutor {}] task={} craft waiting inputs crafter={} key={}",
                                    manager.getPos(), task.taskId, task.crafter.pos, task.recipeKey);
                    snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                    continue;
                }
                boolean accepted = manager.startCraftTask(task.crafter.pos, task.recipeKey, (int) task.amount);
                if (!accepted) {
                    task.status = TaskStatus.BLOCKED;
                    snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                    continue;
                }
                started.put(task.taskId, true);
                org.apache.logging.log4j.LogManager.getLogger("ThaumicAttempts/CrafterExecutor")
                        .info("[CrafterExecutor {}] task={} craft started crafter={} key={} amount={}",
                                manager.getPos(), task.taskId, task.crafter.pos, task.recipeKey, task.amount);
            }
            task.status = TaskStatus.IN_PROGRESS;
            int readyOut = manager.countItemAtEndpoint(task.outputEndpoint, task.recipeKey);
            int baseOut = outputBaseline.getOrDefault(task.taskId, 0);
            int produced = Math.max(0, readyOut - baseOut);
            task.completedAmount = Math.max(task.completedAmount, produced);
            if (task.completedAmount >= task.amount) {
                task.status = TaskStatus.DONE;
                org.apache.logging.log4j.LogManager.getLogger("ThaumicAttempts/CrafterExecutor")
                        .info("[CrafterExecutor {}] task={} craft output detected key={} produced={} ready={} baseline={}",
                                manager.getPos(), task.taskId, task.recipeKey, produced, readyOut, baseOut);
                org.apache.logging.log4j.LogManager.getLogger("ThaumicAttempts/CrafterExecutor")
                        .info("[CrafterExecutor {}] task={} craft done", manager.getPos(), task.taskId);
                snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                it.remove();
                started.remove(task.taskId);
                outputBaseline.remove(task.taskId);
                continue;
            }
            snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
        }
    }

    @Override
    public boolean accepts(RuntimeTask task) { return task instanceof CraftTask; }
}