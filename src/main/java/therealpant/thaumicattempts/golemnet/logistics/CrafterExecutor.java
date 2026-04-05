package therealpant.thaumicattempts.golemnet.logistics;

import therealpant.thaumicattempts.golemnet.tile.TileMirrorManager;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class CrafterExecutor implements ILogisticsExecutor<CraftTask> {
    private static final org.apache.logging.log4j.Logger LOG =
            org.apache.logging.log4j.LogManager.getLogger("ThaumicAttempts/CrafterExecutor");

    /**
     * Сколько "готового результата" максимум держим в окне у одного craft-task.
     * ВАЖНО: окно задаём В ПРЕДМЕТАХ, а не в циклах.
     *
     * Для рецептов 1->1 это просто размер очереди.
     * Для рецептов 1->4/1->8 это не душит большие партии.
     */
    private static final int MIN_WINDOW_ITEMS = 16;
    private static final int MAX_WINDOW_ITEMS = 64;

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

        if (task.outputPerCycle <= 0) {
            task.outputPerCycle = 1;
        }

        task.status = TaskStatus.ACCEPTED;
        running.put(task.taskId, task);
        started.put(task.taskId, false);

        int baseline = manager.countItemAtEndpoint(task.outputEndpoint, task.recipeKey);
        outputBaseline.put(task.taskId, baseline);
        scheduledAmount.put(task.taskId, 0L);
        stalledTicks.put(task.taskId, 0);

        LOG.info("[CrafterExecutor {}] task={} craft accepted crafter={} key={} amount={} output={} baseline={} perCycle={}",
                manager.getPos(), task.taskId, task.crafter.pos, task.recipeKey, task.amount,
                task.outputEndpoint, baseline, task.outputPerCycle);

        snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
        return true;
    }

    @Override
    public TaskExecutionSnapshot getSnapshot(UUID taskId) {
        return snapshots.get(taskId);
    }

    private int resolveWindowItems(CraftTask task) {
        int perCycle = Math.max(1, task.outputPerCycle);

        // хотя бы 4 цикла, но не меньше 16 и не больше 64 итоговых предметов
        int byCycles = perCycle * 4;
        int window = Math.max(MIN_WINDOW_ITEMS, byCycles);
        window = Math.min(MAX_WINDOW_ITEMS, window);

        // нет смысла открывать окно больше чем остаток задачи
        window = Math.toIntExact(Math.min(window, Math.max(1, task.amount)));
        return Math.max(1, window);
    }

    @Override
    public void tick() {
        if (manager == null) return;

        Iterator<Map.Entry<UUID, CraftTask>> it = running.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, CraftTask> e = it.next();
            CraftTask task = e.getValue();

            if (task.status == TaskStatus.FAILED
                    || task.status == TaskStatus.CANCELED
                    || task.status == TaskStatus.DONE) {
                snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                it.remove();
                started.remove(task.taskId);
                outputBaseline.remove(task.taskId);
                scheduledAmount.remove(task.taskId);
                stalledTicks.remove(task.taskId);
                continue;
            }

            boolean isStarted = started.getOrDefault(task.taskId, false);

            int outputPerCycle = Math.max(1, task.outputPerCycle);
            long totalNeededItems = Math.max(1L, task.amount);
            long alreadyScheduledItems = Math.max(0L, scheduledAmount.getOrDefault(task.taskId, 0L));
            long producedSoFarItems = Math.max(0L, task.completedAmount);

            long backlogItems = Math.max(0L, alreadyScheduledItems - producedSoFarItems);
            long maxBacklogItems = resolveWindowItems(task);

            if (alreadyScheduledItems < totalNeededItems && backlogItems < maxBacklogItems) {
                long remainingItems = totalNeededItems - alreadyScheduledItems;
                int toScheduleItems = (int) Math.min(remainingItems, maxBacklogItems - backlogItems);

                if (toScheduleItems > 0) {
                    int acceptedItems = manager.startCraftTask(task.crafter.pos, task.recipeKey, toScheduleItems);

                    if (acceptedItems > 0) {
                        alreadyScheduledItems += acceptedItems;
                        scheduledAmount.put(task.taskId, alreadyScheduledItems);
                        started.put(task.taskId, true);
                        isStarted = true;

                        LOG.info("[CrafterExecutor {}] task={} craft accepted crafter={} key={} acceptedItems={} scheduledItems={}/{} backlogItems={} perCycle={} windowItems={}",
                                manager.getPos(),
                                task.taskId,
                                task.crafter.pos,
                                task.recipeKey,
                                acceptedItems,
                                alreadyScheduledItems,
                                totalNeededItems,
                                Math.max(0L, alreadyScheduledItems - task.completedAmount),
                                outputPerCycle,
                                maxBacklogItems);

                        if (alreadyScheduledItems >= totalNeededItems) {
                            LOG.info("[CrafterExecutor {}] task={} craft fully scheduled key={} crafter={} neededItems={} scheduledItems={} outputPerCycle={} output={}",
                                    manager.getPos(), task.taskId, task.recipeKey, task.crafter,
                                    totalNeededItems, alreadyScheduledItems, outputPerCycle, task.outputEndpoint);
                        }
                    } else if (!isStarted && alreadyScheduledItems <= producedSoFarItems) {
                        task.status = TaskStatus.BLOCKED;
                        LOG.info("[CrafterExecutor {}] task={} craft start blocked crafter={} key={} needItems={} scheduledItems={} backlogItems={}",
                                manager.getPos(), task.taskId, task.crafter.pos, task.recipeKey,
                                toScheduleItems, alreadyScheduledItems, backlogItems);
                        snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                        continue;
                    }
                }
            }

            int readyOut = manager.countItemAtEndpoint(task.outputEndpoint, task.recipeKey);
            int baseOut = outputBaseline.getOrDefault(task.taskId, 0);
            int produced = Math.max(0, readyOut - baseOut);

            long prevCompleted = task.completedAmount;
            task.completedAmount = Math.max(task.completedAmount, produced);

            if (task.completedAmount > prevCompleted) {
                stalledTicks.put(task.taskId, 0);
                LOG.info("[CrafterExecutor {}] task={} craft output progress key={} produced={} ready={} baseline={} output={} scheduledItems={} outputPerCycle={}",
                        manager.getPos(), task.taskId, task.recipeKey, produced, readyOut, baseOut,
                        task.outputEndpoint, scheduledAmount.getOrDefault(task.taskId, 0L), outputPerCycle);
            } else if (scheduledAmount.getOrDefault(task.taskId, 0L) > 0L) {
                int stalled = stalledTicks.getOrDefault(task.taskId, 0) + 1;
                stalledTicks.put(task.taskId, stalled);

                if (stalled == 20 || stalled == 100 || stalled % 200 == 0) {
                    LOG.warn("[CrafterExecutor {}] task={} craft no new output yet key={} crafter={} output={} scheduledItems={} completedItems={} ready={} baseline={} backlogItems={}",
                            manager.getPos(),
                            task.taskId,
                            task.recipeKey,
                            task.crafter,
                            task.outputEndpoint,
                            scheduledAmount.getOrDefault(task.taskId, 0L),
                            task.completedAmount,
                            readyOut,
                            baseOut,
                            Math.max(0L, scheduledAmount.getOrDefault(task.taskId, 0L) - task.completedAmount));
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

            if (isStarted || scheduledAmount.getOrDefault(task.taskId, 0L) > 0L) {
                task.status = TaskStatus.IN_PROGRESS;
            } else {
                task.status = TaskStatus.BLOCKED;
            }

            snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
        }
    }

    @Override
    public boolean accepts(RuntimeTask task) {
        return task instanceof CraftTask;
    }
}