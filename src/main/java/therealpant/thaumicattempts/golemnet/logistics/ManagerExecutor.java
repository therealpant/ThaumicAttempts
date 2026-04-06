package therealpant.thaumicattempts.golemnet.logistics;

import therealpant.thaumicattempts.golemnet.tile.TileMirrorManager;
import therealpant.thaumicattempts.util.ItemKey;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static therealpant.thaumicattempts.integration.ThaumcraftCompat.LOG;

public class ManagerExecutor implements ILogisticsExecutor<TransferTask> {

    private static final int DISPATCH_COOLDOWN_TICKS = 10;
    private static final int STALLED_LIMIT_TICKS = 100;

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

        long nowTick = manager == null ? 0L : manager.getServerTickCounter();
        task.lastProgressTick = nowTick;
        task.lastDispatchTick = 0L;
        task.stalledTicks = 0;
        task.lastRemaining = -1L;

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

    @Nullable
    private NetworkOrder getOrder(TransferTask task) {
        if (manager == null || task == null || task.orderId == null) return null;
        LogisticsNetworkState state = manager.getLogisticsState();
        if (state == null) return null;
        return state.getOrder(task.orderId);
    }

    private boolean isTerminalDeliverOutput(TransferTask task) {
        if (!isOutputStageTask(task)) return false;

        NetworkOrder order = getOrder(task);
        return order != null
                && order.sourceType == OrderSourceType.TERMINAL
                && order.orderKind == NetworkOrder.OrderKind.DELIVERY;
    }

    private boolean isManagerBufferEndpoint(EndpointRef endpoint) {
        return manager != null
                && endpoint != null
                && endpoint.mode == EndpointRef.AccessMode.BUFFER
                && endpoint.pos != null
                && endpoint.pos.equals(manager.getPos());
    }

    private boolean isManagerInboundProvisionTask(TransferTask task) {
        if (!isInboundStageTask(task)) return false;
        return isManagerBufferEndpoint(task.target);
    }

    private boolean isInboundStageTask(TransferTask task) {
        return task != null && "deliver".equals(task.metaPurpose);
    }

    private boolean isOutputStageTask(TransferTask task) {
        return task != null && "deliver-output".equals(task.metaPurpose);
    }

    private boolean assertOutputStageTask(TransferTask task, String context) {
        if (isOutputStageTask(task)) return true;
        LOG.error("[ManagerExecutor {}] ERROR output logic invoked for non-output task task={} purpose={} context={}",
                manager != null ? manager.getPos() : null,
                task != null ? task.taskId : null,
                task != null ? task.metaPurpose : null,
                context);
        return false;
    }

    private boolean sameEndpoint(EndpointRef a, EndpointRef b) {
        if (a == null || b == null) return false;
        if (a.mode != b.mode) return false;
        return a.pos != null && a.pos.equals(b.pos);
    }

    private boolean sameResource(ItemKey a, ItemKey b) {
        return a != null && b != null && a.equals(b);
    }

    private boolean hasEarlierTerminalDeliverSibling(TransferTask task) {
        if (!isTerminalDeliverOutput(task)) return false;

        for (TransferTask other : running.values()) {
            if (other == null || other == task) continue;
            if (!isTerminalDeliverOutput(other)) continue;
            if (!sameEndpoint(task.target, other.target)) continue;
            if (!sameResource(task.itemKey, other.itemKey)) continue;

            if (isTerminal(other.status)) continue;

            // Более ранняя задача в той же "линии" держит эксклюзив
            if (other.createdTick < task.createdTick) return true;
            if (other.createdTick == task.createdTick
                    && other.taskId != null
                    && task.taskId != null
                    && other.taskId.toString().compareTo(task.taskId.toString()) < 0) {
                return true;
            }
        }

        return false;
    }

    private boolean isTerminalInboundToReservedSlot(TransferTask task) {
        if (!isInboundStageTask(task)) return false;
        NetworkOrder order = getOrder(task);
        if (order == null || order.sourceType != OrderSourceType.TERMINAL || order.orderKind != NetworkOrder.OrderKind.DELIVERY) {
            return false;
        }
        return task.target != null
                && task.target.mode == EndpointRef.AccessMode.BUFFER
                && task.target.stagingSlotIndex >= 0
                && task.stagingReservationId != null;
    }

    private boolean isReservationDispatchAllowed(@Nullable LogisticsNetworkState logistics, @Nullable TransferTask task) {
        if (logistics == null || !isOutputStageTask(task)) return false;
        LogisticsNetworkState.ReservationDispatchSnapshot dispatch = logistics.getReservationDispatchSnapshot(task);
        if (dispatch.reservationId == null || dispatch.orderId == null) return false;
        if (task.orderId == null || !task.orderId.equals(dispatch.orderId)) return false;
        if (task.stagingReservationId != null && !task.stagingReservationId.equals(dispatch.reservationId)) return false;
        if (task.stagingSlotIndex < 0 || dispatch.slotIndex != task.stagingSlotIndex) return false;
        if (dispatch.stagedOwned < dispatch.requiredAmount) return false;
        return dispatch.readyForDispatch;
    }

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

            if (isTerminal(task.status)) {
                snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                it.remove();
                continue;
            }

            if (task.itemKey == null || task.itemKey == ItemKey.EMPTY || task.source == null || task.target == null) {
                task.status = TaskStatus.FAILED;
                LOG.warn("[ManagerExecutor {}] task={} status=FAILED reason=invalid-task source={} target={} key={}",
                        manager.getPos(), task.taskId, task.source, task.target, task.itemKey);
                snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                it.remove();
                continue;
            }

            boolean terminalDeliverOutput = isTerminalDeliverOutput(task);
            boolean terminalInboundReserved = isTerminalInboundToReservedSlot(task);
            boolean outputStageTask = isOutputStageTask(task);
            LogisticsNetworkState logistics = manager.getLogisticsState();

            /*
             * КРИТИЧЕСКАЯ ПРАВКА:
             * одинаковые terminal deliver-output задачи не должны одновременно
             * смотреть на один и тот же target inventory.
             * Иначе обе снимут baseline=0 и обе зачтут первый стак.
             *
             * Поэтому в одной линии (target + itemKey + deliver-output + TERMINAL)
             * одновременно активна только самая ранняя задача.
             */
            if (terminalDeliverOutput && hasEarlierTerminalDeliverSibling(task)) {
                transitionStatus(task, TaskStatus.BLOCKED, "waiting-terminal-lane");

                long remainingBlocked = Math.max(0L, task.amount - task.completedAmount);
                int queuedBlocked = manager.countQueuedForEndpoint(task.target, task.itemKey);
                logTransferDebug(task, remainingBlocked, queuedBlocked);

                snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                continue;
            }

            long previousCompleted = task.completedAmount;
            boolean dispatchAllowed = !outputStageTask || isReservationDispatchAllowed(logistics, task);
            if (!outputStageTask || dispatchAllowed) {
                task.completedAmount = calculateTaskProgress(task);
            } else {
                task.completedAmount = previousCompleted;
            }

            if (terminalInboundReserved && logistics != null) {
                int stagedAmount = logistics.getReservationStagedAmount(task);
                if (stagedAmount >= task.amount) {
                    task.completedAmount = task.amount;
                }
            }

            if (task.completedAmount > previousCompleted) {
                task.lastProgressTick = manager.getServerTickCounter();
                if (outputStageTask && logistics != null) {
                    if (!assertOutputStageTask(task, "reservation-consume")) {
                        task.completedAmount = previousCompleted;
                        snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                        continue;
                    }
                    boolean consumed = logistics.logReservationOutputConsumption(
                            manager,
                            task,
                            task.completedAmount - previousCompleted,
                            task.completedAmount
                    );
                    if (!consumed) {
                        task.completedAmount = previousCompleted;
                        transitionStatus(task, TaskStatus.BLOCKED, "reservation-consume-denied");
                    }
                }
            }

            int queued = manager.countQueuedForEndpoint(task.target, task.itemKey);
            long remaining = Math.max(0L, task.amount - task.completedAmount);

            if (remaining == task.lastRemaining) {
                task.stalledTicks++;
            } else {
                task.stalledTicks = 0;
                task.lastRemaining = remaining;
                task.lastProgressTick = manager.getServerTickCounter();
            }

            if (task.completedAmount >= task.amount) {
                if (terminalDeliverOutput) {
                    transitionStatus(task, TaskStatus.DONE, "terminal-task-progress-reached");
                    LOG.info("[TRANSFER COMPLETE] task={} requested={} deliveredByTask={} reason=terminal-task-progress-reached",
                            task.taskId,
                            task.amount,
                            task.completedAmount);
                } else {
                    transitionStatus(task, TaskStatus.DONE, "target-reached");
                    LOG.info("[TRANSFER COMPLETE] task={} requested={} targetNow={} queuedToTarget={} needNow={} releasedBufferedOverage=true",
                            task.taskId,
                            task.amount,
                            manager.countItemAtEndpoint(task.target, task.itemKey),
                            queued,
                            remaining);
                }

                logTransferDebug(task, remaining, queued);
                snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                it.remove();
                continue;
            }

            if (task.stalledTicks > STALLED_LIMIT_TICKS) {
                transitionStatus(task, TaskStatus.BLOCKED, "stalled");
                logTransferDebug(task, remaining, queued);
                snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                continue;
            }

            long nowTick = manager.getServerTickCounter();
            if (nowTick - task.lastDispatchTick >= DISPATCH_COOLDOWN_TICKS) {
                if (outputStageTask && logistics != null && !isReservationDispatchAllowed(logistics, task)) {
                    if (!assertOutputStageTask(task, "reservation-ready-check")) {
                        snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                        continue;
                    }
                    LogisticsNetworkState.ReservationDispatchSnapshot dispatch = logistics.getReservationDispatchSnapshot(task);
                    transitionStatus(task, TaskStatus.BLOCKED, "waiting-reservation-ready");
                    task.stalledTicks = 0;
                    task.lastRemaining = remaining;
                    task.lastProgressTick = nowTick;
                    LOG.info("[TRANSFER DEBUG] task={} deliver-output waiting because reservation not ready order={} reservation={} slotIndex={} staged={} requested={} required={} ready={}",
                            task.taskId,
                            dispatch.orderId != null ? dispatch.orderId : task.orderId,
                            dispatch.reservationId != null ? dispatch.reservationId : task.stagingReservationId,
                            dispatch.slotIndex >= 0 ? dispatch.slotIndex : task.stagingSlotIndex,
                            dispatch.stagedOwned,
                            dispatch.requestedAmount,
                            dispatch.requiredAmount,
                            dispatch.readyForDispatch);
                    logTransferDebug(task, remaining, queued);
                    snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                    continue;
                }

                long need = Math.max(0L, remaining - queued);
                if (need > 0L) {
                    int sourceItemsBefore = manager.countItemAtEndpoint(task.source, task.itemKey);
                    int sourceQueuedBefore = manager.countQueuedForEndpoint(task.source, task.itemKey);
                    int targetItemsBefore = manager.countItemAtEndpoint(task.target, task.itemKey);
                    int targetQueuedBefore = manager.countQueuedForEndpoint(task.target, task.itemKey);
                    boolean managerInboundProvisionTask = isManagerInboundProvisionTask(task);

                    if (managerInboundProvisionTask) {
                        int queueId = ensureQueueId(task);
                        int needForBuffer = (int) Math.min(Integer.MAX_VALUE, need);
                        int inboundNeed = needForBuffer;
                        int incomingBefore = 0;
                        if (logistics != null) {
                            incomingBefore = logistics.getReservationIncomingQueued(task);
                            inboundNeed = logistics.getReservationNeed(task);
                            logistics.logCoverageCheck(manager, task);
                        }
                        boolean attemptedInboundProvisioning = inboundNeed > 0;
                        boolean inboundAccepted = false;

                        LOG.info("[TRANSFER MODE] task={} inbound-mode=golem-request purpose={} source={} target={} need={} reservationNeed={} incomingQueued={} queueId={}",
                                task.taskId, task.metaPurpose, task.source, task.target, needForBuffer, inboundNeed, incomingBefore, queueId);

                        if (attemptedInboundProvisioning) {
                            inboundAccepted = manager.dispatchInboundToManagerByGolems(task.source, task.itemKey, inboundNeed, queueId);
                            if (inboundAccepted && logistics != null) {
                                logistics.markReservationInboundQueued(manager, task, inboundNeed);
                            }
                            LOG.info("[TRANSFER INBOUND] task={} inbound-mode=golem-request request={} key={} amount={} queueId={}",
                                    task.taskId,
                                    inboundAccepted ? "accepted" : "rejected",
                                    task.itemKey,
                                    inboundNeed,
                                    queueId);
                        } else {
                            LOG.info("[TRANSFER INBOUND] task={} inbound-mode=golem-request request=skipped-covered-by-reservation need={} reservationNeed={} queueId={}",
                                    task.taskId,
                                    needForBuffer,
                                    inboundNeed,
                                    queueId);
                        }

                        int sourceQueuedAfter = manager.countQueuedForEndpoint(task.source, task.itemKey);
                        boolean queuedToManager = sourceQueuedAfter > sourceQueuedBefore || sourceQueuedAfter > 0;
                        task.inboundQueued = task.inboundQueued || inboundAccepted || queuedToManager;

                        if (inboundAccepted || queuedToManager) {
                            task.lastDispatchTick = nowTick;
                            transitionStatus(task, TaskStatus.DISPATCHED, "waiting-golem-delivery");
                            LOG.info("[TRANSFER INBOUND] task={} inbound-mode=golem-request waiting-for-golem-delivery queuedToManager={} queuedNow={}",
                                    task.taskId, sourceQueuedAfter, queuedToManager);
                        } else {
                            boolean sourceReadyAfterAttempt = manager.hasAvailableItems(task.source, task.itemKey);
                            if (!sourceReadyAfterAttempt) {
                                transitionStatus(task, TaskStatus.WAITING_SOURCE, "golem-request-no-source");
                            } else {
                                transitionStatus(task, TaskStatus.BLOCKED, "golem-request-rejected");
                            }
                            LOG.info("[TRANSFER INBOUND] task={} inbound-mode=golem-request request={} sourceReadyAfterAttempt={} state={}",
                                    task.taskId,
                                    attemptedInboundProvisioning ? "not-queued" : "skipped-covered-but-not-visible",
                                    sourceReadyAfterAttempt,
                                    task.status);
                        }
                    } else {
                        int queueId = ensureQueueId(task);
                        LOG.info("[TRANSFER MODE] task={} dispatch-mode=direct-manager-transfer purpose={} source={} target={} need={} queueId={}",
                                task.taskId, task.metaPurpose, task.source, task.target, need, queueId);

                        boolean accepted = manager.dispatchTransferTask(
                                task.source,
                                task.target,
                                task.itemKey,
                                (int) Math.min(Integer.MAX_VALUE, need),
                                queueId
                        );
                        if (isOutputStageTask(task) && task.source != null
                                && task.source.mode == EndpointRef.AccessMode.BUFFER
                                && task.source.stagingSlotIndex >= 0) {
                            if (!assertOutputStageTask(task, "output-dispatch-path")) {
                                snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
                                continue;
                            }
                            LOG.info("[TRANSFER DEBUG] task={} deliver-output consuming from reserved slot={} order={} reservation={}",
                                    task.taskId,
                                    task.source.stagingSlotIndex,
                                    task.orderId,
                                    task.stagingReservationId);
                        }

                        int sourceItemsAfter = manager.countItemAtEndpoint(task.source, task.itemKey);
                        int sourceQueuedAfter = manager.countQueuedForEndpoint(task.source, task.itemKey);
                        int targetItemsAfter = manager.countItemAtEndpoint(task.target, task.itemKey);
                        int targetQueuedAfter = manager.countQueuedForEndpoint(task.target, task.itemKey);

                        boolean movedNow = targetItemsAfter > targetItemsBefore;
                        boolean queuedNow = targetQueuedAfter > targetQueuedBefore
                                || sourceQueuedAfter > sourceQueuedBefore
                                || (task.isDeliverTask() && sourceQueuedAfter > 0 && sourceQueuedAfter != sourceQueuedBefore);
                        boolean sourceCoverageAppeared = (sourceItemsAfter + sourceQueuedAfter) > (sourceItemsBefore + sourceQueuedBefore);
                        boolean targetCoverageAppeared = (targetItemsAfter + targetQueuedAfter) > (targetItemsBefore + targetQueuedBefore);
                        boolean anyCoverageAppeared = sourceCoverageAppeared || targetCoverageAppeared;

                        if (accepted) {
                            task.lastDispatchTick = nowTick;
                            transitionStatus(task, TaskStatus.DISPATCHED, "dispatch-accepted");
                            LOG.info("[TRANSFER ACCEPT] task={} requested={} targetPresent={} targetNeedNow={} accepted={}",
                                    task.taskId, task.amount, targetItemsBefore, need, true);
                        } else {
                            boolean sourceReadyAfterAttempt = manager.hasAvailableItems(task.source, task.itemKey);
                            if (!movedNow && !queuedNow && !anyCoverageAppeared) {
                                if (!sourceReadyAfterAttempt) {
                                    transitionStatus(task, TaskStatus.WAITING_SOURCE, "source-empty-after-dispatch-attempt");
                                } else {
                                    transitionStatus(task, TaskStatus.BLOCKED, "dispatch-rejected-after-attempt");
                                }
                            } else {
                                transitionStatus(task, TaskStatus.IN_PROGRESS, "dispatch-pending");
                            }
                        }
                    }
                }
            }

            if (!isTerminal(task.status)
                    && task.status != TaskStatus.DISPATCHED
                    && task.status != TaskStatus.WAITING_SOURCE
                    && task.status != TaskStatus.BLOCKED) {
                transitionStatus(task, TaskStatus.IN_PROGRESS, "awaiting-delivery");
            }

            logTransferDebug(task, remaining, queued);
            snapshots.put(task.taskId, new TaskExecutionSnapshot(task.taskId, task.status, task.completedAmount));
        }
    }

    private boolean isTerminal(TaskStatus status) {
        return status == TaskStatus.DONE || status == TaskStatus.FAILED || status == TaskStatus.CANCELED;
    }

    @Override
    public boolean accepts(RuntimeTask task) { return task instanceof TransferTask; }

    private void transitionStatus(TransferTask task, TaskStatus next, String reason) {
        if (task == null || next == null) return;
        TaskStatus prev = task.status;
        if (prev == next) return;
        task.status = next;
        task.updatedTick = manager != null ? manager.getServerTickCounter() : task.updatedTick;
        LOG.info("[TRANSFER STATE] task={} {} -> {} reason={} item={}",
                task.taskId,
                prev,
                next,
                reason,
                task.itemKey);
    }

    private void logTransferDebug(TransferTask task, long remaining, int queued) {
        if (task == null || manager == null) return;
        LOG.info("[TRANSFER DEBUG] task={} item={} remaining={} queued={} delivered={} stalledTicks={} state={}",
                task.taskId,
                task.itemKey,
                Math.max(0L, remaining),
                Math.max(0, queued),
                task.completedAmount,
                task.stalledTicks,
                task.status);
    }

    private int ensureQueueId(TransferTask task) {
        if (task.legacyDeliveryQueueId > 0) return task.legacyDeliveryQueueId;
        int queueId = Math.abs(task.taskId.hashCode());
        if (queueId == 0) queueId = 1;
        task.legacyDeliveryQueueId = queueId;
        return queueId;
    }

    private long calculateTaskProgress(TransferTask task) {
        if (task == null || task.target == null || task.itemKey == null || manager == null) return 0L;

        if (isManagerInboundProvisionTask(task)) {
            int queuedToManager = manager.countQueuedForEndpoint(task.target, task.itemKey);
            if (task.inboundQueued && queuedToManager <= 0) {
                return task.amount;
            }
            return Math.min(task.completedAmount, task.amount);
        }

        int nowAtTarget = manager.countItemAtEndpoint(task.target, task.itemKey);
        if (task.targetBaseline < 0) {
            task.targetBaseline = nowAtTarget;
        }

        long delivered = Math.max(0, nowAtTarget - task.targetBaseline);
        return Math.min(task.amount, delivered);
    }
}