package therealpant.thaumicattempts.golemnet.tile;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import therealpant.thaumicattempts.golemnet.planner.*;
import therealpant.thaumicattempts.util.ItemKey;

import javax.annotation.Nullable;
import java.util.*;

public class TileSequentialCraftPlanner extends TileEntity implements ITickable {
    private static final Logger LOG = LogManager.getLogger("ThaumicAttempts/SequentialPlanner");

    public enum PlannerStatus {
        IDLE,
        PLANNING,
        WAITING_DEPENDENCIES,
        DISPATCHING,
        RUNNING,
        FAILED,
        COMPLETED
    }

    private static final String TAG_MANAGER = "Manager";
    private static final String TAG_ACTIVE = "Active";
    private static final String TAG_STATUS = "PlannerStatus";
    private static final String TAG_QUEUE = "PlannerQueue";

    @Nullable
    private BlockPos managerPos;
    private boolean active = false;
    private PlannerStatus status = PlannerStatus.IDLE;

    private static final class PlannerRequest {
        final ItemKey key;
        final int amount;
        final BlockPos dest;
        final int destSide;
        final PlannerRequestKey dedupeKey;

        PlannerRequest(ItemKey key, int amount, BlockPos dest, int destSide) {
            this.key = key;
            this.amount = Math.max(1, amount);
            this.dest = dest;
            this.destSide = destSide;
            this.dedupeKey = new PlannerRequestKey(this.key, this.amount, this.dest, this.destSide);
        }
    }

    private static final class PlannerRequestKey {
        final ItemKey key;
        final int amount;
        final BlockPos dest;
        final int destSide;

        PlannerRequestKey(ItemKey key, int amount, BlockPos dest, int destSide) {
            this.key = key;
            this.amount = Math.max(1, amount);
            this.dest = dest == null ? BlockPos.ORIGIN : dest.toImmutable();
            this.destSide = destSide;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PlannerRequestKey)) return false;
            PlannerRequestKey that = (PlannerRequestKey) o;
            return amount == that.amount
                    && destSide == that.destSide
                    && Objects.equals(key, that.key)
                    && Objects.equals(dest, that.dest);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, amount, dest, destSide);
        }

        @Override
        public String toString() {
            return key + " x" + amount + " -> " + dest + " side=" + destSide;
        }
    }

    private final Deque<PlannerRequest> queue = new ArrayDeque<>();
    private final Set<PlannerRequestKey> queuedRequestKeys = new HashSet<>();
    private SequentialOperationPlan currentPlan = null;
    @Nullable private PlannerRequest planningRequest = null;
    @Nullable private PlannerRequest runningRequest = null;
    @Nullable private INetworkOperationSource runningRootSource = null;
    private int runningTargetAtDest = 0;
    private int runningTicks = 0;
    private int runningBaselineAtDest = 0;
    private int runningRequiredDelta = 0;
    private static final int MAX_RUNNING_TICKS = 20 * 60;

    @Nullable
    public BlockPos getManagerPos() {
        return managerPos;
    }

    public void setManagerPos(@Nullable BlockPos managerPos) {
        this.managerPos = managerPos;
        markDirty();
    }

    public void clearManagerPosFromManager(BlockPos pos) {
        if (this.managerPos != null && this.managerPos.equals(pos)) {
            this.managerPos = null;
            this.active = false;
            this.status = PlannerStatus.IDLE;
            markDirty();
        }
    }

    public boolean isActivePlanner() {
        return active;
    }

    public PlannerStatus getStatus() {
        return status;
    }

    public boolean enqueueRequest(ItemStack like, int amount, BlockPos dest, int destSide) {
        return enqueueRequest(like, amount, dest, destSide, "unknown");
    }

    public boolean enqueueRequest(ItemStack like, int amount, BlockPos dest, int destSide, @Nullable String caller) {
        if (like == null || like.isEmpty() || amount <= 0 || dest == null) return false;

        PlannerRequest incoming = new PlannerRequest(ItemKey.of(like), amount, dest.toImmutable(), destSide);
        String callerTag = caller == null ? "unknown" : caller;

        LOG.info("[Planner {}] enqueue incoming root={} amount={} dest={} side={} caller={}",
                pos, like, amount, dest, destSide, callerTag);

        if (isRequestInFlight(incoming.dedupeKey)) {
            LOG.info("[Planner {}] enqueue skipped as duplicate root={} caller={}",
                    pos, incoming.dedupeKey, callerTag);
            return true;
        }

        queue.addLast(incoming);
        queuedRequestKeys.add(incoming.dedupeKey);

        if (currentPlan != null || status != PlannerStatus.IDLE) {
            LOG.info("[Planner {}] enqueue accepted (queued behind active plan) root={} caller={}",
                    pos, incoming.dedupeKey, callerTag);
        } else {
            LOG.info("[Planner {}] enqueue accepted (will start immediately) root={} caller={}",
                    pos, incoming.dedupeKey, callerTag);
        }

        markDirty();
        return true;
    }

    public boolean hasActiveRequest(ItemStack like, int amount, BlockPos dest, int destSide) {
        if (like == null || like.isEmpty() || amount <= 0 || dest == null) return false;
        return isRequestInFlight(new PlannerRequestKey(ItemKey.of(like), amount, dest, destSide));
    }

    private boolean isRequestInFlight(PlannerRequestKey key) {
        if (queuedRequestKeys.contains(key)) return true;
        if (planningRequest != null && planningRequest.dedupeKey.equals(key)) return true;
        return runningRequest != null && runningRequest.dedupeKey.equals(key);
    }

    @Override
    public void update() {
        if (world == null || world.isRemote) return;

        TileMirrorManager manager = getManager();
        active = manager != null && manager.tryBindPlanner(pos);
        if (!active) {
            if (status != PlannerStatus.IDLE) {
                LOG.info("[Planner {}] inactive: no manager binding/compute slot", pos);
            }
            status = PlannerStatus.IDLE;
            currentPlan = null;
            planningRequest = null;
            runningRequest = null;
            runningRootSource = null;
            runningTicks = 0;
            return;
        }

        if (currentPlan != null && status == PlannerStatus.RUNNING) {
            if (isRunningRequestSatisfied()) {
                LOG.info("[Planner {}] completed root={} amount={} dest={} reachedTarget={}",
                        pos,
                        runningRequest == null ? "?" : runningRequest.key,
                        runningRequest == null ? 0 : runningRequest.amount,
                        runningRequest == null ? "?" : runningRequest.dest,
                        runningTargetAtDest);
                status = PlannerStatus.COMPLETED;
                completeRunningRequest(true, "request completed");
                markDirty();
            } else {
                runningTicks++;
                if (runningTicks > MAX_RUNNING_TICKS) {
                    completeRunningRequest(false, "request timed out");
                    status = PlannerStatus.FAILED;
                    return;
                }
                status = PlannerStatus.WAITING_DEPENDENCIES;
            }
            return;
        }

        if (currentPlan != null && status == PlannerStatus.WAITING_DEPENDENCIES) {
            if (isRunningRequestSatisfied()) {
                LOG.info("[Planner {}] dependencies finished for root={}", pos, runningRequest == null ? "?" : runningRequest.key);
                status = PlannerStatus.COMPLETED;
                completeRunningRequest(true, "request completed");
                markDirty();
            } else {
                status = PlannerStatus.RUNNING;
            }
            return;
        }

        if (status == PlannerStatus.FAILED || status == PlannerStatus.COMPLETED) {
            status = PlannerStatus.IDLE;
        }

        if (status != PlannerStatus.IDLE || queue.isEmpty()) return;

        PlannerRequest req = queue.pollFirst();
        if (req == null) return;
        queuedRequestKeys.remove(req.dedupeKey);

        planningRequest = req;
        status = PlannerStatus.PLANNING;
        LOG.info("[Planner {}] request {} x{}", pos, req.key, req.amount);

        SequentialOperationPlan plan = buildPlan(manager, req.key, req.amount);
        if (!plan.isSuccess()) {
            LOG.warn("[Planner {}] planning failed: {} ({})", pos, plan.getFailure(), plan.getFailureDetails());
            status = PlannerStatus.FAILED;
            currentPlan = null;
            planningRequest = null;
            LOG.info("[Planner {}] request failed root={} reason=planning_failure", pos, req.dedupeKey);
            return;
        }

        currentPlan = plan;
        status = PlannerStatus.DISPATCHING;

        boolean dispatched = dispatchPlan(plan.getRoot());
        if (!dispatched) {
            status = PlannerStatus.FAILED;
            currentPlan = null;
            LOG.warn("[Planner {}] dispatch failed", pos);
            return;
        }

        Map<ItemKey, Integer> deliver = new LinkedHashMap<>();
        deliver.put(req.key, req.amount);
        manager.ensureDeliveryFor(req.dest, deliver, 0);

        status = PlannerStatus.RUNNING;
        planningRequest = null;
        runningRequest = req;
        runningTicks = 0;
        LOG.info("[Planner {}] dispatch done root={} amount={} dest={} rootSource={}",
                pos,
                req.key,
                req.amount,
                req.dest,
                plan.getRoot() == null || plan.getRoot().source == null ? "?" : plan.getRoot().source.getDebugName());
        markDirty();
    }

    private void completeRunningRequest(boolean success, String reason) {
        PlannerRequest finished = runningRequest;
        currentPlan = null;
        runningRequest = null;
        runningRootSource = null;
        planningRequest = null;
        runningTargetAtDest = 0;
        runningBaselineAtDest = 0;
        runningRequiredDelta = 0;
        runningTicks = 0;

        if (finished != null) {
            LOG.info("[Planner {}] request {} root={} reason={}",
                    pos,
                    success ? "completed" : "failed",
                    finished.dedupeKey,
                    reason);
        }
    }

    @Nullable
    private TileMirrorManager getManager() {
        if (world == null || managerPos == null) return null;
        TileEntity te = world.getTileEntity(managerPos);
        return te instanceof TileMirrorManager ? (TileMirrorManager) te : null;
    }

    private SequentialOperationPlan buildPlan(TileMirrorManager manager, ItemKey root, int amount) {
        Map<ItemKey, Integer> seed = manager.getReachableCatalog();
        PlannerResourceSnapshot snapshot = new PlannerResourceSnapshot(seed);
        List<INetworkOperationSource> sources = NetworkOperationAdapters.collectSources(manager.getOperationProviderTiles());
        Set<ItemKey> stack = new HashSet<>();

        PlanResult res = planResource(root, Math.max(1, amount), snapshot, sources, stack);
        if (!res.ok) return SequentialOperationPlan.fail(res.reason, res.message);
        return SequentialOperationPlan.success(res.node);
    }

    private static final class PlanResult {
        final boolean ok;
        final PlannedOperationNode node;
        final PlannerFailureReason reason;
        final String message;

        private PlanResult(boolean ok, PlannedOperationNode node, PlannerFailureReason reason, String message) {
            this.ok = ok;
            this.node = node;
            this.reason = reason;
            this.message = message;
        }

        static PlanResult ok(@Nullable PlannedOperationNode n) { return new PlanResult(true, n, PlannerFailureReason.NONE, ""); }
        static PlanResult fail(PlannerFailureReason r, String m) { return new PlanResult(false, null, r, m); }
    }

    private PlanResult planResource(ItemKey key,
                                    int amount,
                                    PlannerResourceSnapshot snapshot,
                                    List<INetworkOperationSource> sources,
                                    Set<ItemKey> recursionStack) {
        int need = Math.max(1, amount);

        int taken = snapshot.consumeUpTo(key, need);
        if (taken >= need) {
            return PlanResult.ok(null);
        }

        int missing = need - taken;
        if (recursionStack.contains(key)) {
            return PlanResult.fail(PlannerFailureReason.CYCLE_DETECTED, "cycle at " + key);
        }

        INetworkOperationSource source = findSourceFor(key, sources);
        if (source == null) {
            LOG.info("[Planner {}] no recursive source for {} -> planner cannot build chain, fallback expected", pos, key);
            return PlanResult.fail(PlannerFailureReason.NO_PROVIDER, "no source for " + key);
        }

        int outPerOp = source.getOutputCountFor(key);
        if (outPerOp <= 0) {
            return PlanResult.fail(PlannerFailureReason.INVALID_OUTPUT, "output<=0 for " + key + " from " + source.getDebugName());
        }

        int operations = (missing + outPerOp - 1) / outPerOp;
        PlannedOperationNode node = new PlannedOperationNode(key, missing, source, operations);

        recursionStack.add(key);
        List<RequiredInput> reqs = source.getRequiredInputsFor(key, operations);
        for (RequiredInput in : reqs) {
            PlanResult dep = planResource(in.getKey(), in.getCount(), snapshot, sources, recursionStack);
            if (!dep.ok) return dep;
            if (dep.node != null) node.addDependency(dep.node);
        }
        recursionStack.remove(key);

        int produced = operations * outPerOp;
        snapshot.produce(key, produced);
        snapshot.consumeUpTo(key, missing);

        LOG.info("[Planner {}] source={} type={} for={} missing={} ops={}",
                pos, source.getDebugName(), source.getType(), key, missing, operations);
        return PlanResult.ok(node);
    }

    private boolean isRunningRequestSatisfied() {
        if (runningRequest == null || currentPlan == null) return false;

        boolean settled = runningTicks > 2 && isCurrentPlanSettled();
        String outstanding = settled ? null : findFirstOutstanding(currentPlan.getRoot());

        LOG.debug("[Planner {}] waiting root={} dest={} settled={} outstanding={} status={} ticks={}",
                pos,
                runningRequest.key,
                runningRequest.dest,
                settled,
                outstanding == null ? "-" : outstanding,
                status,
                runningTicks);

        return settled;
    }

    private int countAtDestination(BlockPos dest, int destSide, ItemStack like) {
        if (world == null || dest == null || like == null || like.isEmpty()) return 0;
        TileEntity te = world.getTileEntity(dest);
        if (te == null) return 0;

        net.minecraftforge.items.IItemHandler handler = null;
        if (destSide >= 0 && destSide < 6) {
            handler = te.getCapability(net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,
                    net.minecraft.util.EnumFacing.VALUES[destSide]);
        }
        if (handler == null) {
            handler = te.getCapability(net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        }
        if (handler == null) return 0;

        int total = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack in = handler.getStackInSlot(i);
            if (in.isEmpty()) continue;
            if (therealpant.thaumicattempts.util.ResourceIdentity.sameResource(in, like)) {
                total += in.getCount();
            }
        }
        return total;
    }

    @Nullable
    private INetworkOperationSource findSourceFor(ItemKey key, List<INetworkOperationSource> sources) {
        for (INetworkOperationSource source : sources) {
            for (ItemKey provided : source.getProvidedResults()) {
                if (provided != null && provided.equals(key)) return source;
            }
        }
        return null;
    }

    private boolean dispatchPlan(@Nullable PlannedOperationNode node) {
        if (node == null) return true;
        for (PlannedOperationNode dep : node.dependencies) {
            if (!dispatchPlan(dep)) return false;
        }
        boolean ok = node.source.enqueueExecution(node.requested, node.operations);
        if (!ok) {
            LOG.warn("[Planner {}] enqueue rejected by {} for {} x{}",
                    pos, node.source.getDebugName(), node.requested, node.operations);
        }
        return ok;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);

        if (managerPos != null) tag.setLong(TAG_MANAGER, managerPos.toLong());
        tag.setBoolean(TAG_ACTIVE, active);
        tag.setString(TAG_STATUS, status.name());

        NBTTagList list = new NBTTagList();
        for (PlannerRequest req : queue) {
            NBTTagCompound e = new NBTTagCompound();
            e.setTag("Item", req.key.toStack(1).writeToNBT(new NBTTagCompound()));
            e.setInteger("Amount", req.amount);
            e.setLong("Dest", req.dest.toLong());
            e.setInteger("Side", req.destSide);
            list.appendTag(e);
        }
        tag.setTag(TAG_QUEUE, list);

        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);

        managerPos = tag.hasKey(TAG_MANAGER, Constants.NBT.TAG_LONG) ? BlockPos.fromLong(tag.getLong(TAG_MANAGER)) : null;
        active = tag.getBoolean(TAG_ACTIVE);

        String st = tag.getString(TAG_STATUS);
        try {
            status = PlannerStatus.valueOf(st);
        } catch (Exception ignored) {
            status = PlannerStatus.IDLE;
        }

        queue.clear();
        queuedRequestKeys.clear();
        NBTTagList list = tag.getTagList(TAG_QUEUE, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound e = list.getCompoundTagAt(i);
            ItemStack like = new ItemStack(e.getCompoundTag("Item"));
            if (like.isEmpty()) continue;
            int amount = Math.max(1, e.getInteger("Amount"));
            BlockPos dest = e.hasKey("Dest", Constants.NBT.TAG_LONG) ? BlockPos.fromLong(e.getLong("Dest")) : this.pos;
            int side = e.getInteger("Side");
            PlannerRequest req = new PlannerRequest(ItemKey.of(like), amount, dest, side);
            queue.addLast(req);
            queuedRequestKeys.add(req.dedupeKey);
        }
    }

    @Nullable
    private String findFirstOutstanding(@Nullable PlannedOperationNode node) {
        if (node == null) return null;

        if (node.source != null && node.source.hasOutstandingWorkFor(node.requested)) {
            return node.source.getDebugName() + " -> " + node.requested;
        }

        return null;
    }

    private boolean isCurrentPlanSettled() {
        if (currentPlan == null || currentPlan.getRoot() == null) return true;

        PlannedOperationNode root = currentPlan.getRoot();
        if (root.source == null) return true;

        return !root.source.hasOutstandingWorkFor(root.requested);
    }
}