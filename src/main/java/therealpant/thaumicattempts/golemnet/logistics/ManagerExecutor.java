package therealpant.thaumicattempts.golemnet.logistics;

import therealpant.thaumicattempts.golemnet.tile.TileMirrorManager;

import java.nio.charset.StandardCharsets;
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

            long remaining = Math.max(0L, task.amount - task.completedAmount);
            if (remaining <= 0L) {
                task.status = TaskStatus.DONE;
                task.outboundDone = true;
                snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                it.remove();
                inboundBaseline.remove(task.taskId);
                continue;
            }

            task.status = TaskStatus.IN_PROGRESS;

            /*
             * Инициализация baseline один раз.
             * Прогресс считаем только по дельте, иначе остатки в буфере/инвентаре ломают логику.
             */
            if (task.targetBaseline < 0) {
                task.targetBaseline = manager.countItemAtEndpoint(task.target, task.itemKey);
            }
            if (task.sourceBaseline < 0) {
                task.sourceBaseline = manager.countItemAtEndpoint(task.source, task.itemKey);
            }
            if (task.bufferBaseline < 0) {
                task.bufferBaseline = manager.countItemAtEndpoint(
                        EndpointRef.of(manager.getPos(), EndpointRef.AccessMode.BUFFER),
                        task.itemKey
                );
            }

            /*
             * ===== SPECIAL CASE: обычная доставка в terminal/requester =====
             * Фаза 1: один раз затянуть в буфер manager'а.
             * Фаза 2: один раз отправить из буфера в target.
             *
             * ВАЖНО: нельзя каждый тик снова вызывать dispatch/queue,
             * иначе одна runtime-задача создаёт несколько големных доставок.
             */
            if ("deliver".equals(task.metaPurpose)) {

                // ---- inbound phase ----
                if (!task.inboundDone) {
                    if (!task.inboundQueued) {
                        int queueId = ensureQueueId(task);
                        boolean queued = manager.dispatchInboundToManagerByGolems(
                                task.source,
                                task.itemKey,
                                (int) remaining,
                                queueId
                        );

                        if (!queued) {
                            task.status = TaskStatus.BLOCKED;
                            snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                            continue;
                        }

                        task.inboundQueued = true;
                        task.legacyDeliveryQueued = true;
                        task.legacyDeliveryQueueId = queueId;
                        inboundBaseline.put(task.taskId, manager.countBuffered(task.itemKey));
                        if (task.legacyDeliveryId == null) {
                            task.legacyDeliveryId = UUID.nameUUIDFromBytes(
                                    ("legacy-deliver-" + task.taskId.toString()).getBytes(java.nio.charset.StandardCharsets.UTF_8)
                            );
                        }
                        task.updatedTick = manager.getServerTickCounter();
                    }

                    int nowInBuffer = manager.countItemAtEndpoint(
                            EndpointRef.of(manager.getPos(), EndpointRef.AccessMode.BUFFER),
                            task.itemKey
                    );
                    int movedIntoBuffer = Math.max(0, nowInBuffer - task.bufferBaseline);
                    int queuedToBuffer = manager.countQueuedForEndpoint(
                            EndpointRef.of(manager.getPos(), EndpointRef.AccessMode.BUFFER),
                            task.itemKey
                    );

                    if (movedIntoBuffer >= task.amount || queuedToBuffer <= 0 || isInboundDone(task)) {
                        task.inboundDone = true;
                        task.updatedTick = manager.getServerTickCounter();
                        LOG.info("[ManagerExecutor {}] task={} inbound phase done", manager.getPos(), task.taskId);
                    } else {
                        snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, TaskStatus.IN_PROGRESS, task.completedAmount));
                        continue;
                    }
                }

                // ---- outbound phase ----
                if (!task.outboundDone) {
                    if (!task.outboundQueued) {
                        int queueId = ensureQueueId(task);
                        boolean accepted = manager.dispatchTransferTask(
                                EndpointRef.of(manager.getPos(), EndpointRef.AccessMode.BUFFER),
                                task.target,
                                task.itemKey,
                                (int) remaining,
                                queueId
                        );

                        if (!accepted) {
                            task.status = TaskStatus.BLOCKED;
                            snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                            continue;
                        }

                        task.outboundQueued = true;
                        task.updatedTick = manager.getServerTickCounter();
                    }

                    int nowAtTarget = manager.countItemAtEndpoint(task.target, task.itemKey);
                    int movedToTarget = Math.max(0, nowAtTarget - task.targetBaseline);

                    task.completedAmount = Math.min(task.amount, movedToTarget);

                    if (task.completedAmount >= task.amount) {
                        task.outboundDone = true;
                        task.status = TaskStatus.DONE;
                        snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                        it.remove();
                        inboundBaseline.remove(task.taskId);
                        continue;
                    }

                    snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, TaskStatus.IN_PROGRESS, task.completedAmount));
                    continue;
                }
            }

            /*
             * ===== Обычные transfer-задачи =====
             * craft-input / pickup-output / deliver-output
             *
             * КЛЮЧЕВОЙ ФИКС:
             * dispatchTransferTask(...) вызываем только ОДИН раз.
             * Потом просто ждём фактического изменения target по baseline.
             */
            if (!task.dispatchQueued) {
                int queueId = ensureQueueId(task);
                boolean accepted = manager.dispatchTransferTask(
                        task.source,
                        task.target,
                        task.itemKey,
                        (int) remaining,
                        queueId
                );

                if (!accepted) {
                    task.status = TaskStatus.BLOCKED;
                    snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                    continue;
                }

                task.dispatchQueued = true;
                task.dispatchQueueId = queueId;
                task.updatedTick = manager.getServerTickCounter();
            }

            int nowAtTarget = manager.countItemAtEndpoint(task.target, task.itemKey);
            int movedToTarget = Math.max(0, nowAtTarget - task.targetBaseline);
            task.completedAmount = Math.min(task.amount, movedToTarget);

            if (task.completedAmount >= task.amount) {
                task.status = TaskStatus.DONE;
                snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                it.remove();
                inboundBaseline.remove(task.taskId);
            } else {
                snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, TaskStatus.IN_PROGRESS, task.completedAmount));
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
        EndpointRef managerBuffer = EndpointRef.of(manager.getPos(), EndpointRef.AccessMode.BUFFER);
        int queued = manager.countQueuedForEndpoint(managerBuffer, task.itemKey);
        int base = inboundBaseline.getOrDefault(task.taskId, 0);
        int nowBuffered = manager.countBuffered(task.itemKey);
        int deliveredToBuffer = Math.max(0, nowBuffered - base);
        return queued <= 0 || deliveredToBuffer >= Math.max(1L, task.amount);
    }
}