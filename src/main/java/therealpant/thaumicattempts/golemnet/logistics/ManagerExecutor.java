package therealpant.thaumicattempts.golemnet.logistics;

import therealpant.thaumicattempts.golemnet.tile.TileMirrorManager;

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

            task.status = TaskStatus.IN_PROGRESS;

            /*
             * Инициализация baseline один раз.
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

            task.completedAmount = calculateDeliveredToTarget(task);
            long remaining = Math.max(0L, task.amount - task.completedAmount);

            if (remaining <= 0L) {
                task.status = TaskStatus.DONE;
                task.outboundDone = true;
                LOG.info("[ManagerExecutor {}] task={} status=DONE reason=target-amount-reached amount={} completed={} remaining=0",
                        manager.getPos(), task.taskId, task.amount, task.completedAmount);
                snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                it.remove();
                inboundBaseline.remove(task.taskId);
                continue;
            }

            /*
             * ===== SPECIAL CASE: обычная доставка в terminal/requester =====
             */
            if ("deliver".equals(task.metaPurpose)) {
                int bufferedNow = manager.countItemAtEndpoint(
                        EndpointRef.of(manager.getPos(), EndpointRef.AccessMode.BUFFER),
                        task.itemKey
                );
                int movedIntoBuffer = Math.max(0, bufferedNow - task.bufferBaseline);
                int queuedToBuffer = manager.countQueuedForEndpoint(
                        EndpointRef.of(manager.getPos(), EndpointRef.AccessMode.BUFFER),
                        task.itemKey
                );
                int inboundCovered = movedIntoBuffer + queuedToBuffer;

                // ---- inbound phase ----
                if (!task.inboundDone) {
                    int inboundStillNeed = (int) Math.max(0L, task.amount - inboundCovered);

                    /*
                     * Повторяем inbound-dispatch до полного покрытия amount.
                     * Дополнительное дублирование защищено проверкой queuedToBuffer выше.
                     */
                    if (inboundStillNeed > 0) {
                        int queueId = ensureQueueId(task);
                        boolean queued = manager.dispatchInboundToManagerByGolems(
                                task.source,
                                task.itemKey,
                                inboundStillNeed,
                                queueId
                        );

                        if (!queued) {
                            task.status = TaskStatus.BLOCKED;
                            LOG.info("[ManagerExecutor {}] task={} status=BLOCKED reason=inbound-dispatch-rejected amount={} completed={} buffered={} queued={} remaining={}",
                                    manager.getPos(), task.taskId, task.amount, task.completedAmount, movedIntoBuffer, queuedToBuffer, remaining);
                            snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                            it.remove();
                            inboundBaseline.remove(task.taskId);
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

                        LOG.info("[ManagerExecutor {}] task={} inbound-dispatch amount={} completed={} buffered={} queued={} remaining={}",
                                manager.getPos(), task.taskId, task.amount, task.completedAmount, movedIntoBuffer, queuedToBuffer, remaining);
                    }

                    /*
                     * inbound phase done — только когда реально покрыт весь amount.
                     */
                    if (movedIntoBuffer >= task.amount || inboundCovered >= task.amount || isInboundDone(task)) {
                        task.inboundDone = true;
                        task.updatedTick = manager.getServerTickCounter();
                        LOG.info("[ManagerExecutor {}] task={} inbound-done amount={} completed={} buffered={} queued={} remaining={}",
                                manager.getPos(), task.taskId, task.amount, task.completedAmount, movedIntoBuffer, queuedToBuffer, remaining);
                    } else {
                        LOG.info("[ManagerExecutor {}] task={} inbound-wait amount={} completed={} buffered={} queued={} remaining={}",
                                manager.getPos(), task.taskId, task.amount, task.completedAmount, movedIntoBuffer, queuedToBuffer, remaining);
                        snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, TaskStatus.IN_PROGRESS, task.completedAmount));
                        continue;
                    }
                }

                // ---- outbound phase ----
                if (!task.outboundDone) {
                    task.completedAmount = calculateDeliveredToTarget(task);

                    int queuedToTarget = manager.countQueuedForEndpoint(task.target, task.itemKey);
                    int coveredAtTarget = (int) Math.min(task.amount, task.completedAmount + queuedToTarget);
                    int outboundStillNeed = (int) Math.max(0L, task.amount - coveredAtTarget);

                    /*
                     * Повторяем outbound-dispatch до полного покрытия amount.
                     * Дополнительное дублирование защищено проверкой queuedToTarget выше.
                     */
                    if (outboundStillNeed > 0) {
                        int queueId = ensureQueueId(task);
                        boolean accepted = manager.dispatchTransferTask(
                                EndpointRef.of(manager.getPos(), EndpointRef.AccessMode.BUFFER),
                                task.target,
                                task.itemKey,
                                outboundStillNeed,
                                queueId
                        );

                        if (!accepted) {
                            task.status = TaskStatus.BLOCKED;
                            LOG.info("[ManagerExecutor {}] task={} status=BLOCKED reason=outbound-dispatch-rejected amount={} completed={} buffered={} queued={} remaining={}",
                                    manager.getPos(), task.taskId, task.amount, task.completedAmount, movedIntoBuffer, queuedToTarget, remaining);
                            snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                            it.remove();
                            inboundBaseline.remove(task.taskId);
                            continue;
                        }

                        task.outboundQueued = true;
                        task.updatedTick = manager.getServerTickCounter();
                        LOG.info("[ManagerExecutor {}] task={} outbound-dispatch amount={} completed={} buffered={} queued={} remaining={}",
                                manager.getPos(), task.taskId, task.amount, task.completedAmount, movedIntoBuffer, queuedToTarget, remaining);
                    }

                    task.completedAmount = calculateDeliveredToTarget(task);

                    if (task.completedAmount >= task.amount) {
                        task.outboundDone = true;
                        task.status = TaskStatus.DONE;
                        LOG.info("[ManagerExecutor {}] task={} status=DONE reason=target-amount-reached amount={} completed={} buffered={} queued={} remaining=0",
                                manager.getPos(), task.taskId, task.amount, task.completedAmount, movedIntoBuffer, 0);
                        snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                        it.remove();
                        inboundBaseline.remove(task.taskId);
                        continue;
                    }

                    LOG.info("[ManagerExecutor {}] task={} outbound-wait amount={} completed={} buffered={} queued={} remaining={}",
                            manager.getPos(), task.taskId, task.amount, task.completedAmount, movedIntoBuffer, queuedToTarget, Math.max(0L, task.amount - task.completedAmount));
                    snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, TaskStatus.IN_PROGRESS, task.completedAmount));
                    continue;
                }
            }

            /*
             * ===== Обычные transfer-задачи =====
             * craft-input / pickup-output / deliver-output
             *
             * dispatchTransferTask(...) повторяется до полного покрытия amount.
             * Это нужно для задач, где доставка приходит частями.
             */
            task.completedAmount = calculateDeliveredToTarget(task);

            int queuedToTarget = manager.countQueuedForEndpoint(task.target, task.itemKey);
            int coveredAtTarget = (int) Math.min(task.amount, task.completedAmount + queuedToTarget);
            int stillNeed = (int) Math.max(0L, task.amount - coveredAtTarget);

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
                    LOG.info("[ManagerExecutor {}] task={} status=BLOCKED reason=dispatch-rejected amount={} completed={} buffered={} queued={} remaining={}",
                            manager.getPos(), task.taskId, task.amount, task.completedAmount, 0, queuedToTarget, Math.max(0L, task.amount - task.completedAmount));
                    snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                    it.remove();
                    inboundBaseline.remove(task.taskId);
                    continue;
                }

                task.dispatchQueued = true;
                task.dispatchQueueId = queueId;
                task.updatedTick = manager.getServerTickCounter();
                LOG.info("[ManagerExecutor {}] task={} dispatch amount={} completed={} buffered={} queued={} remaining={}",
                        manager.getPos(), task.taskId, task.amount, task.completedAmount, 0, queuedToTarget, Math.max(0L, task.amount - task.completedAmount));
            }

            task.completedAmount = calculateDeliveredToTarget(task);

            if (task.completedAmount >= task.amount) {
                task.status = TaskStatus.DONE;
                LOG.info("[ManagerExecutor {}] task={} status=DONE reason=target-amount-reached amount={} completed={} buffered=0 queued=0 remaining=0",
                        manager.getPos(), task.taskId, task.amount, task.completedAmount);
                snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                it.remove();
                inboundBaseline.remove(task.taskId);
            } else {
                LOG.info("[ManagerExecutor {}] task={} wait amount={} completed={} buffered=0 queued={} remaining={}",
                        manager.getPos(), task.taskId, task.amount, task.completedAmount,
                        manager.countQueuedForEndpoint(task.target, task.itemKey),
                        Math.max(0L, task.amount - task.completedAmount));
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
        int base = inboundBaseline.getOrDefault(task.taskId, 0);
        int nowBuffered = manager.countBuffered(task.itemKey);
        int deliveredToBuffer = Math.max(0, nowBuffered - base);
        return deliveredToBuffer >= Math.max(1L, task.amount);
    }

    private long calculateDeliveredToTarget(TransferTask task) {
        int nowAtTarget = manager.countItemAtEndpoint(task.target, task.itemKey);
        int movedToTarget = Math.max(0, nowAtTarget - task.targetBaseline);
        long observed = movedToTarget;

        if (!"deliver".equals(task.metaPurpose)) {
            int nowAtSource = manager.countItemAtEndpoint(task.source, task.itemKey);
            int movedFromSource = Math.max(0, task.sourceBaseline - nowAtSource);
            observed = Math.max(observed, movedFromSource);
        }
        observed = Math.max(observed, task.completedAmount);
        return Math.min(task.amount, observed);
    }
}