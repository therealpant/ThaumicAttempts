package therealpant.thaumicattempts.golemnet.logistics;

import therealpant.thaumicattempts.golemnet.tile.TileMirrorManager;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class CrafterExecutor implements ILogisticsExecutor<CraftTask> {
    private static final org.apache.logging.log4j.Logger LOG =
            org.apache.logging.log4j.LogManager.getLogger("ThaumicAttempts/CrafterExecutor");

    private TileMirrorManager manager;
    private final LinkedHashMap<UUID, CraftTask> running = new LinkedHashMap<UUID, CraftTask>();
    private final LinkedHashMap<UUID, TaskExecutionSnapshot> snapshots = new LinkedHashMap<UUID, TaskExecutionSnapshot>();
    private final LinkedHashMap<UUID, Boolean> started = new LinkedHashMap<UUID, Boolean>();
    private final LinkedHashMap<UUID, Integer> outputBaseline = new LinkedHashMap<UUID, Integer>();
    private final LinkedHashMap<UUID, Long> scheduledAmount = new LinkedHashMap<UUID, Long>();
    private final LinkedHashMap<UUID, Integer> stalledTicks = new LinkedHashMap<UUID, Integer>();

    public void bind(TileMirrorManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean canAccept(CraftTask task) {
        return manager != null && task != null;
    }

    @Override
    public boolean submit(CraftTask task) {
        if (!canAccept(task)) {
            LOG.warn("[CrafterExecutor] task rejected manager-bound={} taskNull={}",
                    manager != null, task == null);
            return false;
        }
        if (running.containsKey(task.taskId)) return true;

        String err = task.validationError();
        if (err != null) {
            task.status = TaskStatus.FAILED;
            LOG.warn("[CrafterExecutor {}] task={} craft rejected invalid fields reason={} crafter={} key={} output={}",
                    manager.getPos(), task.taskId, err, task.crafter, task.recipeKey, task.outputEndpoint);
            snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
            return false;
        }

        task.status = TaskStatus.ACCEPTED;
        running.put(task.taskId, task);
        started.put(task.taskId, false);

        int baseline = manager.countItemAtEndpoint(task.outputEndpoint, task.recipeKey);
        outputBaseline.put(task.taskId, baseline);
        scheduledAmount.put(task.taskId, 0L);
        stalledTicks.put(task.taskId, 0);

        LOG.info("[CrafterExecutor {}] task={} craft accepted crafter={} key={} amount={} output={} baseline={}",
                manager.getPos(), task.taskId, task.crafter.pos, task.recipeKey, task.amount, task.outputEndpoint, baseline);

        snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
        return true;
    }

    @Override
    public TaskExecutionSnapshot getSnapshot(UUID taskId) {
        return snapshots.get(taskId);
    }

    @Override
    public void tick() {
        if (manager == null) return;

        Iterator<Map.Entry<UUID, CraftTask>> it = running.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, CraftTask> e = it.next();
            CraftTask task = e.getValue();

            if (task.status == TaskStatus.FAILED || task.status == TaskStatus.CANCELED || task.status == TaskStatus.DONE) {
                snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                it.remove();
                started.remove(task.taskId);
                outputBaseline.remove(task.taskId);
                scheduledAmount.remove(task.taskId);
                stalledTicks.remove(task.taskId);
                continue;
            }

            boolean isStarted = started.getOrDefault(task.taskId, false);
            long alreadyScheduled = Math.max(0L, scheduledAmount.getOrDefault(task.taskId, 0L));

            if (alreadyScheduled < task.amount) {
                int toSchedule = (int) Math.min((long) Integer.MAX_VALUE, task.amount - alreadyScheduled);
                int accepted = manager.startCraftTask(task.crafter.pos, task.recipeKey, toSchedule);

                if (accepted > 0) {
                    alreadyScheduled += accepted;
                    scheduledAmount.put(task.taskId, alreadyScheduled);
                    started.put(task.taskId, true);
                    isStarted = true;

                    LOG.info("[CrafterExecutor {}] task={} craft cycle accepted crafter={} key={} accepted={} scheduled={}/{}",
                            manager.getPos(), task.taskId, task.crafter.pos, task.recipeKey, accepted, alreadyScheduled, task.amount);

                    if (alreadyScheduled >= task.amount) {
                        LOG.info("[CrafterExecutor {}] task={} craft fully scheduled key={} crafter={} amount={} scheduled={} output={}",
                                manager.getPos(), task.taskId, task.recipeKey, task.crafter, task.amount, alreadyScheduled, task.outputEndpoint);
                    }
                } else if (!isStarted || alreadyScheduled <= task.completedAmount) {
                    task.status = TaskStatus.BLOCKED;
                    LOG.info("[CrafterExecutor {}] task={} craft start blocked crafter={} key={} need={} scheduled={}",
                            manager.getPos(), task.taskId, task.crafter.pos, task.recipeKey, toSchedule, alreadyScheduled);
                    snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                    continue;
                }
            }

            int readyOut = manager.countItemAtEndpoint(task.outputEndpoint, task.recipeKey);
            int baseOut = outputBaseline.getOrDefault(task.taskId, 0);
            int produced = Math.max(0, readyOut - baseOut);

            long  prevCompleted = task.completedAmount;
            task.completedAmount = Math.max(task.completedAmount, produced);

            if (task.completedAmount > prevCompleted) {
                stalledTicks.put(task.taskId, 0);
                LOG.info("[CrafterExecutor {}] task={} craft output progress key={} produced={} ready={} baseline={} output={}",
                        manager.getPos(), task.taskId, task.recipeKey, produced, readyOut, baseOut, task.outputEndpoint);
            } else if (alreadyScheduled >= task.amount) {
                int stalled = stalledTicks.getOrDefault(task.taskId, 0) + 1;
                stalledTicks.put(task.taskId, stalled);

                if (stalled == 20 || stalled == 100 || stalled % 200 == 0) {
                    LOG.warn("[CrafterExecutor {}] task={} craft scheduled but no output yet key={} crafter={} output={} scheduled={} completed={} ready={} baseline={}",
                            manager.getPos(), task.taskId, task.recipeKey, task.crafter, task.outputEndpoint, alreadyScheduled, task.completedAmount, readyOut, baseOut);
                }
            }

            if (task.completedAmount >= task.amount) {
                task.status = TaskStatus.DONE;

                LOG.info("[CrafterExecutor {}] task={} craft output detected key={} produced={} ready={} baseline={} output={}",
                        manager.getPos(), task.taskId, task.recipeKey, produced, readyOut, baseOut, task.outputEndpoint);
                LOG.info("[CrafterExecutor {}] task={} craft done", manager.getPos(), task.taskId);

                snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                it.remove();
                started.remove(task.taskId);
                outputBaseline.remove(task.taskId);
                scheduledAmount.remove(task.taskId);
                stalledTicks.remove(task.taskId);
                continue;
            }

            task.status = task.completedAmount < alreadyScheduled ? TaskStatus.IN_PROGRESS : TaskStatus.BLOCKED;
            snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
        }
    }

    @Override
    public boolean accepts(RuntimeTask task) {
        return task instanceof CraftTask;
    }
}