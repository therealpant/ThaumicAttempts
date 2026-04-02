package therealpant.thaumicattempts.golemnet.logistics;

import therealpant.thaumicattempts.golemnet.tile.TileMirrorManager;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static therealpant.thaumicattempts.integration.ThaumcraftCompat.LOG;

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
        if (task == null || task.taskId == null) return false;

        TransferTask existing = running.get(task.taskId);
        if (existing != null) {
            return true;
        }

        running.put(task.taskId, task);
        snapshots.put(task.taskId, new TaskExecutionSnapshot(TaskStatus.DISPATCHED, task.completedAmount));
        LOG.info("[ManagerExecutor {}] task={} transfer accepted", manager != null ? manager.getPos() : null, task.taskId);
        return true;
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
                LOG.info("[ManagerExecutor {}] task={} transfer removed from running",
                        manager.getPos(), task.taskId);
                snapshots.put(task.taskId, new TaskExecutionSnapshot(task.status, task.completedAmount));
                it.remove();
                continue;
            }

            long remaining = Math.max(0L, task.amount - task.completedAmount);
            if (remaining <= 0L) {
                task.status = TaskStatus.DONE;
                snapshots.put(task.taskId, new TaskExecutionSnapshot(task.status, task.completedAmount));
                LOG.info("[ManagerExecutor {}] task={} transfer done", manager.getPos(), task.taskId);
                it.remove();
                continue;
            }

            task.status = TaskStatus.IN_PROGRESS;

            // Обычная delivery task: сначала inbound в manager, потом outbound в конечную точку
            if ("deliver".equals(task.metaPurpose)) {

                if (!task.inboundDone) {
                    if (!task.inboundQueued) {
                        UUID deliveryId = manager.dispatchInboundToManagerByGolems(task.itemKey, (int) remaining, task.source.pos);
                        if (deliveryId != null) {
                            task.legacyDeliveryId = deliveryId;
                            task.inboundQueued = true;
                            task.updatedTick = manager.getServerTickCounter();
                            LOG.info("[ManagerExecutor {}] task={} inbound queued via legacy backend deliveryId={}",
                                    manager.getPos(), task.taskId, deliveryId);
                        } else {
                            snapshots.put(task.taskId, new TaskExecutionSnapshot(TaskStatus.BLOCKED, task.completedAmount));
                            continue;
                        }
                    }

                    if (task.legacyDeliveryId != null && manager.isLegacyDeliveryComplete(task.legacyDeliveryId)) {
                        task.inboundDone = true;
                        task.updatedTick = manager.getServerTickCounter();
                        LOG.info("[ManagerExecutor {}] task={} inbound phase done", manager.getPos(), task.taskId);
                    } else {
                        snapshots.put(task.taskId, new TaskExecutionSnapshot(TaskStatus.IN_PROGRESS, task.completedAmount));
                        continue;
                    }
                }

                if (!task.outboundDone) {
                    long moved = manager.executeTransferTask(task.itemKey, (int) remaining, manager.getPos(), task.target.pos);
                    if (moved > 0) {
                        task.completedAmount += moved;
                        task.outboundDone = (task.completedAmount >= task.amount);
                        task.updatedTick = manager.getServerTickCounter();
                        LOG.info("[ManagerExecutor {}] task={} outbound moved {}", manager.getPos(), task.taskId, moved);
                    }

                    if (task.completedAmount >= task.amount) {
                        task.status = TaskStatus.DONE;
                        snapshots.put(task.taskId, new TaskExecutionSnapshot(TaskStatus.DONE, task.completedAmount));
                        LOG.info("[ManagerExecutor {}] task={} transfer done", manager.getPos(), task.taskId);
                        it.remove();
                        continue;
                    } else {
                        snapshots.put(task.taskId, new TaskExecutionSnapshot(TaskStatus.IN_PROGRESS, task.completedAmount));
                        continue;
                    }
                }
            }

            // Остальные transfer-задачи (craft-input / pickup-output / deliver-output) остаются как раньше
            long moved = manager.executeTransferTask(task.itemKey, (int) remaining, task.source.pos, task.target.pos);
            if (moved > 0) {
                task.completedAmount += moved;
                task.updatedTick = manager.getServerTickCounter();
                LOG.info("[ManagerExecutor {}] task={} transfer moved {}", manager.getPos(), task.taskId, moved);
            }

            if (task.completedAmount >= task.amount) {
                task.status = TaskStatus.DONE;
                snapshots.put(task.taskId, new TaskExecutionSnapshot(TaskStatus.DONE, task.completedAmount));
                LOG.info("[ManagerExecutor {}] task={} transfer done", manager.getPos(), task.taskId);
                it.remove();
            } else {
                snapshots.put(task.taskId, new TaskExecutionSnapshot(TaskStatus.IN_PROGRESS, task.completedAmount));
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
}