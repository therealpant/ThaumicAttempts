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

    public void bind(TileMirrorManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean canAccept(TransferTask task) { return manager != null && task != null; }

    @Override
    public boolean submit(TransferTask task) {
        if (task == null) return false;
        if (running.containsKey(task.taskId)) {
            LOG.info("[ManagerExecutor {}] task={} transfer submit skipped reason=already-running",
                    manager != null ? manager.getPos() : null, task.taskId);
            return false;
        }

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
        LOG.info("[ManagerExecutor {}] task={} transfer accepted order={} purpose={} amount={}",
                manager != null ? manager.getPos() : null, task.taskId, task.orderId, task.metaPurpose, task.amount);
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
                continue;
            }

            long remaining = Math.max(0L, task.amount - task.completedAmount);
            if (remaining <= 0L) {
                task.status = TaskStatus.DONE;
                task.outboundDone = true;
                snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                it.remove();
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
                    long inboundProgress = getInboundProgress(task);
                    long remainingInbound = Math.max(0L, task.amount - inboundProgress);

                    if (!task.inboundQueued && remainingInbound > 0L) {
                        int queueId = ensureQueueId(task);
                        int requestedAmount = safeToInt(remainingInbound);
                        boolean queued = manager.dispatchInboundToManagerByGolems(
                                task.source,
                                task.itemKey,
                                requestedAmount,
                                queueId
                        );

                        if (!queued) {
                            logDeliverProgress(task, "inbound provisioning failed");
                            task.status = TaskStatus.BLOCKED;
                            snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                            it.remove();
                            continue;
                        }

                        task.inboundQueued = true;
                        task.legacyDeliveryQueued = true;
                        task.legacyDeliveryQueueId = queueId;
                        if (task.legacyDeliveryId == null) {
                            task.legacyDeliveryId = UUID.nameUUIDFromBytes(
                                    ("legacy-deliver-" + task.taskId.toString()).getBytes(StandardCharsets.UTF_8)
                            );
                        }
                        task.updatedTick = manager.getServerTickCounter();
                        logDeliverProgress(task, "inbound provisioning requested amount=" + requestedAmount);
                    }

                    inboundProgress = getInboundProgress(task);
                    remainingInbound = Math.max(0L, task.amount - inboundProgress);
                    logDeliverProgress(task, "inbound tick");

                    if (remainingInbound <= 0L) {
                        task.inboundDone = true;
                        task.updatedTick = manager.getServerTickCounter();
                        task.inboundQueued = false;
                        logDeliverProgress(task, "inboundDone reason=bufferDelta-gte-amount");
                    } else if (task.inboundQueued && inboundProgress > 0L) {
                        task.inboundQueued = false;
                        logDeliverProgress(task, "inbound queue reopened reason=partial-progress remaining=" + remainingInbound);
                    } else if (!task.inboundQueued) {
                        snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, TaskStatus.IN_PROGRESS, task.completedAmount));
                        continue;
                    } else {
                        snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, TaskStatus.IN_PROGRESS, task.completedAmount));
                        continue;
                    }
                }

                // ---- outbound phase ----
                if (!task.outboundDone) {
                    if (!task.outboundQueued) {
                        long outboundProgress = getOutboundProgress(task);
                        long remainingOutbound = Math.max(0L, task.amount - outboundProgress);
                        if (remainingOutbound <= 0L) {
                            task.completedAmount = task.amount;
                            task.outboundDone = true;
                            task.status = TaskStatus.DONE;
                            snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                            it.remove();
                            logDeliverProgress(task, "outboundDone reason=targetDelta-gte-amount-before-queue");
                            continue;
                        }
                        int queueId = ensureQueueId(task);
                        int requestedAmount = safeToInt(remainingOutbound);
                        boolean accepted = manager.dispatchTransferTask(
                                EndpointRef.of(manager.getPos(), EndpointRef.AccessMode.BUFFER),
                                task.target,
                                task.itemKey,
                                requestedAmount,
                                queueId
                        );

                        if (!accepted) {
                            logDeliverProgress(task, "outbound dispatch failed");
                            task.status = TaskStatus.BLOCKED;
                            snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                            it.remove();
                            continue;
                        }

                        task.outboundQueued = true;
                        task.updatedTick = manager.getServerTickCounter();
                        logDeliverProgress(task, "outbound dispatch requested amount=" + requestedAmount);
                    }

                    long outboundProgress = getOutboundProgress(task);
                    long remainingOutbound = Math.max(0L, task.amount - outboundProgress);

                    if (remainingOutbound <= 0L) {
                        task.outboundDone = true;
                        task.status = TaskStatus.DONE;
                        logDeliverProgress(task, "outboundDone reason=targetDelta-gte-amount");
                        snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));

                        it.remove();
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
                if (requiresFullSourceBeforeDispatch(task)) {
                    int availableAtSource = manager.countItemAtEndpoint(task.source, task.itemKey);
                    if (availableAtSource < remaining) {
                        task.status = TaskStatus.BLOCKED;
                        snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                        it.remove();
                        continue;
                    }
                }
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
                    it.remove();
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

    private long getInboundProgress(TransferTask task) {
        int currentBuffer = manager.countItemAtEndpoint(
                EndpointRef.of(manager.getPos(), EndpointRef.AccessMode.BUFFER),
                task.itemKey
        );
        int currentTarget = manager.countItemAtEndpoint(task.target, task.itemKey);
        int bufferDelta = Math.max(0, currentBuffer - Math.max(0, task.bufferBaseline));
        int targetDelta = Math.max(0, currentTarget - Math.max(0, task.targetBaseline));
        return Math.min(task.amount, (long) bufferDelta + (long) targetDelta);
    }

    private long getOutboundProgress(TransferTask task) {
        int currentTarget = manager.countItemAtEndpoint(task.target, task.itemKey);
        return Math.min(task.amount, Math.max(0, currentTarget - Math.max(0, task.targetBaseline)));
    }

    private static int safeToInt(long value) {
        if (value <= 0L) return 0;
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private void logDeliverProgress(TransferTask task, String reason) {
        if (manager == null || task == null) return;
        int currentBuffer = manager.countItemAtEndpoint(
                EndpointRef.of(manager.getPos(), EndpointRef.AccessMode.BUFFER),
                task.itemKey
        );
        int currentTarget = manager.countItemAtEndpoint(task.target, task.itemKey);
        int safeBufferBaseline = Math.max(0, task.bufferBaseline);
        int safeTargetBaseline = Math.max(0, task.targetBaseline);
        int inboundProgress = Math.max(0, currentBuffer - safeBufferBaseline);
        int outboundProgress = Math.max(0, currentTarget - safeTargetBaseline);
        long remainingInbound = Math.max(0L, task.amount - getInboundProgress(task));
        long remainingOutbound = Math.max(0L, task.amount - getOutboundProgress(task));
        LOG.info("[ManagerExecutor {}] deliver task={} order={} amount={} bufferBaseline={} currentBuffer={} inboundProgress={} remainingInbound={} targetBaseline={} currentTarget={} outboundProgress={} remainingOutbound={} inboundQueued={} inboundDone={} outboundQueued={} outboundDone={} dispatchQueued={} reason={}",
                manager.getPos(),
                task.taskId,
                task.orderId,
                task.amount,
                safeBufferBaseline,
                currentBuffer,
                inboundProgress,
                remainingInbound,
                safeTargetBaseline,
                currentTarget,
                outboundProgress,
                remainingOutbound,
                task.inboundQueued,
                task.inboundDone,
                task.outboundQueued,
                task.outboundDone,
                task.dispatchQueued,
                reason);
    }

    private static boolean requiresFullSourceBeforeDispatch(TransferTask task) {
        if (task == null || task.metaPurpose == null) return false;
        return "craft-input".equals(task.metaPurpose)
                || "pickup-output".equals(task.metaPurpose)
                || "deliver-output".equals(task.metaPurpose);
    }
}