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

        PlannerRequest(ItemKey key, int amount, BlockPos dest, int destSide) {
            this.key = key;
            this.amount = Math.max(1, amount);
            this.dest = dest;
            this.destSide = destSide;
        }
    }

    private final Deque<PlannerRequest> queue = new ArrayDeque<>();
    private SequentialOperationPlan currentPlan = null;

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
        if (like == null || like.isEmpty() || amount <= 0 || dest == null) return false;
        queue.addLast(new PlannerRequest(ItemKey.of(like), amount, dest.toImmutable(), destSide));
        markDirty();
        return true;
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
            return;
        }

        if (currentPlan != null && status == PlannerStatus.RUNNING) {
            status = PlannerStatus.COMPLETED;
            currentPlan = null;
            markDirty();
            return;
        }

        if (status == PlannerStatus.FAILED || status == PlannerStatus.COMPLETED) {
            status = PlannerStatus.IDLE;
        }

        if (status != PlannerStatus.IDLE || queue.isEmpty()) return;

        PlannerRequest req = queue.pollFirst();
        if (req == null) return;

        status = PlannerStatus.PLANNING;
        LOG.info("[Planner {}] request {} x{}", pos, req.key, req.amount);

        SequentialOperationPlan plan = buildPlan(manager, req.key, req.amount);
        if (!plan.isSuccess()) {
            LOG.warn("[Planner {}] planning failed: {} ({})", pos, plan.getFailure(), plan.getFailureDetails());
            status = PlannerStatus.FAILED;
            currentPlan = null;
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
        markDirty();
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
        NBTTagList list = tag.getTagList(TAG_QUEUE, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound e = list.getCompoundTagAt(i);
            ItemStack like = new ItemStack(e.getCompoundTag("Item"));
            if (like.isEmpty()) continue;
            int amount = Math.max(1, e.getInteger("Amount"));
            BlockPos dest = e.hasKey("Dest", Constants.NBT.TAG_LONG) ? BlockPos.fromLong(e.getLong("Dest")) : this.pos;
            int side = e.getInteger("Side");
            queue.addLast(new PlannerRequest(ItemKey.of(like), amount, dest, side));
        }
    }
}