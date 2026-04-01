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
import therealpant.thaumicattempts.api.CraftOrderApi;
import therealpant.thaumicattempts.api.ICraftEndpoint;
import therealpant.thaumicattempts.util.ItemKey;
import therealpant.thaumicattempts.util.ResourceIdentity;

import javax.annotation.Nullable;
import java.util.*;

public class TileSequentialCraftPlanner extends TileEntity implements ITickable {
    private static final Logger LOG = LogManager.getLogger("ThaumicAttempts/SequentialPlanner");

    public enum PlannerStatus {
        IDLE,
        EXPANDING,
        FAILED
    }

    private static final String TAG_MANAGER = "Manager";
    private static final String TAG_ACTIVE = "Active";
    private static final String TAG_STATUS = "PlannerStatus";
    private static final String TAG_TRACKED = "TrackedShortages";

    @Nullable
    private BlockPos managerPos;
    private boolean active = false;
    private PlannerStatus status = PlannerStatus.IDLE;

    private static final class ShortageKey {
        final ItemKey key;
        final int amount;
        final BlockPos destination;
        final BlockPos rootDestination;
        @Nullable final ItemKey rootOrder;

        private ShortageKey(ItemKey key, int amount, BlockPos destination, BlockPos rootDestination, @Nullable ItemKey rootOrder) {
            this.key = key;
            this.amount = Math.max(1, amount);
            this.destination = destination.toImmutable();
            this.rootDestination = rootDestination.toImmutable();
            this.rootOrder = rootOrder;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ShortageKey)) return false;
            ShortageKey that = (ShortageKey) o;
            return amount == that.amount
                    && Objects.equals(key, that.key)
                    && Objects.equals(destination, that.destination)
                    && Objects.equals(rootDestination, that.rootDestination)
                    && Objects.equals(rootOrder, that.rootOrder);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, amount, destination, rootDestination, rootOrder);
        }
    }

    private final Set<ShortageKey> trackedShortages = new HashSet<>();

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
            trackedShortages.clear();
            markDirty();
        }
    }

    public boolean isActivePlanner() {
        return active;
    }

    public PlannerStatus getStatus() {
        return status;
    }

    public boolean expandShortages(Map<ItemKey, Integer> shortages,
                                   BlockPos destination,
                                   int destinationSide,
                                   @Nullable ItemKey rootOrder,
                                   @Nullable BlockPos rootDestination,
                                   int queueId) {
        TileMirrorManager manager = getManager();
        if (world == null || world.isRemote || manager == null || shortages == null || shortages.isEmpty() || destination == null) {
            LOG.warn("[Planner {}] expandShortages aborted worldRemote={} manager={} shortages={} destination={}",
                    pos,
                    world != null && world.isRemote,
                    manager,
                    shortages,
                    destination);
            return false;
        }

        status = PlannerStatus.EXPANDING;

        final BlockPos rootDest = (rootDestination == null ? destination : rootDestination).toImmutable();
        List<Map.Entry<ItemKey, Integer>> suborders = new ArrayList<>();
        final Map<ItemKey, BlockPos> resolvedEndpoints = new HashMap<>();

        LOG.info("[Planner {}] expandShortages start rootOrder={} destination={} rootDest={} queueId={} shortages={}",
                pos, rootOrder, destination, rootDest, queueId, shortages);

        for (Map.Entry<ItemKey, Integer> e : shortages.entrySet()) {
            ItemKey key = e.getKey();
            int amount = Math.max(1, e.getValue());
            if (key == null || amount <= 0) continue;

            ItemStack probe = key.toStack(1);
            if (probe.isEmpty()) {
                LOG.warn("[Planner {}] expandShortages skip empty probe key={} amount={}",
                        pos, key, amount);
                continue;
            }

            BlockPos endpoint = findCraftEndpointFor(manager, probe);
            if (endpoint == null) {
                LOG.warn("[Planner {}] expandShortages no endpoint key={} amount={} probe={}",
                        pos, key, amount, probe);
                continue;
            }

            ShortageKey dedupeKey = new ShortageKey(key, amount, endpoint, rootDest, rootOrder);
            if (trackedShortages.contains(dedupeKey)) {
                LOG.info("[Planner {}] expandShortages duplicate key={} amount={} endpoint={} rootDest={} rootOrder={}",
                        pos, key, amount, endpoint, rootDest, rootOrder);
                continue;
            }

            trackedShortages.add(dedupeKey);
            suborders.add(new AbstractMap.SimpleImmutableEntry<>(key, amount));
            resolvedEndpoints.put(key, endpoint);

            LOG.info("[Planner {}] expandShortages accepted key={} amount={} endpoint={} rootOrder={}",
                    pos, key, amount, endpoint, rootOrder);
        }

        if (suborders.isEmpty()) {
            LOG.warn("[Planner {}] expandShortages produced empty suborders rootOrder={} destination={} shortages={}",
                    pos, rootOrder, destination, shortages);
            status = PlannerStatus.IDLE;
            markDirty();
            return false;
        }

        LOG.info("[Planner {}] expandShortages enqueueBatchCraft rootOrder={} destination={} queueId={} suborders={}",
                pos, rootOrder, destination, queueId, suborders);

        manager.enqueueBatchCraft(
                destination,
                destinationSide,
                queueId,
                suborders,
                key -> {
                    BlockPos resolved = resolvedEndpoints.get(key);
                    if (resolved != null) return resolved;
                    return findCraftEndpointFor(manager, key.toStack(1));
                }
        );

        LOG.info("[Planner {}] expandShortages submitted rootOrder={} destination={} queueId={} subordersCount={}",
                pos, rootOrder, destination, queueId, suborders.size());

        markDirty();
        status = PlannerStatus.IDLE;
        return true;
    }

    @Nullable
    private BlockPos findCraftEndpointFor(TileMirrorManager manager, ItemStack result) {
        if (world == null || manager == null || result == null || result.isEmpty()) {
            LOG.warn("[Planner {}] findCraftEndpointFor aborted manager={} result={}",
                    pos, manager, result);
            return null;
        }

        Set<BlockPos> requesters = manager.getRequestersSnapshot();
        if (requesters == null || requesters.isEmpty()) {
            LOG.warn("[Planner {}] findCraftEndpointFor no requesters result={}", pos, result);
            return null;
        }

        LOG.info("[Planner {}] findCraftEndpointFor start result={} requesters={}", pos, result, requesters.size());

        for (BlockPos rp : requesters) {
            TileEntity te = world.getTileEntity(rp);
            if (!(te instanceof ICraftEndpoint)) {
                LOG.debug("[Planner {}] findCraftEndpointFor skip non-endpoint pos={} te={}",
                        pos, rp, te == null ? "null" : te.getClass().getName());
                continue;
            }

            ICraftEndpoint endpoint = (ICraftEndpoint) te;
            if (!CraftOrderApi.isCrafter(endpoint)) {
                LOG.debug("[Planner {}] findCraftEndpointFor skip non-crafter pos={} te={}",
                        pos, rp, te.getClass().getName());
                continue;
            }

            List<ItemStack> outputs = endpoint.listCraftableResults();
            LOG.info("[Planner {}] findCraftEndpointFor inspect pos={} te={} outputs={}",
                    pos, rp, te.getClass().getSimpleName(), outputs == null ? 0 : outputs.size());

            if (outputs == null || outputs.isEmpty()) continue;

            for (ItemStack out : outputs) {
                if (out == null || out.isEmpty()) continue;

                LOG.debug("[Planner {}] findCraftEndpointFor compare wanted={} candidate={} pos={}",
                        pos, result, out, rp);

                if (therealpant.thaumicattempts.util.ResourceIdentity.sameResource(out, result)) {
                    LOG.info("[Planner {}] findCraftEndpointFor FOUND result={} endpoint={} te={}",
                            pos, result, rp, te.getClass().getSimpleName());
                    return rp.toImmutable();
                }
            }
        }

        LOG.warn("[Planner {}] findCraftEndpointFor FAILED result={} requesters={}",
                pos, result, requesters.size());
        return null;
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
            trackedShortages.clear();
            return;
        }
        clearSatisfiedShortages(manager);
    }

    private void clearSatisfiedShortages(TileMirrorManager manager) {
        if (manager == null || trackedShortages.isEmpty()) return;

        Map<ItemKey, Integer> stock = manager.getReachableCatalog();
        if (stock == null) stock = Collections.emptyMap();

        Iterator<ShortageKey> it = trackedShortages.iterator();
        while (it.hasNext()) {
            ShortageKey key = it.next();
            int have = stock.getOrDefault(key.key, 0);
            if (have >= key.amount) {
                it.remove();
            }
        }
    }

    @Nullable
    private TileMirrorManager getManager() {
        if (world == null || managerPos == null) return null;
        TileEntity te = world.getTileEntity(managerPos);
        return te instanceof TileMirrorManager ? (TileMirrorManager) te : null;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);

        if (managerPos != null) tag.setLong(TAG_MANAGER, managerPos.toLong());
        tag.setBoolean(TAG_ACTIVE, active);
        tag.setString(TAG_STATUS, status.name());

        NBTTagList tracked = new NBTTagList();
        for (ShortageKey k : trackedShortages) {
            NBTTagCompound e = new NBTTagCompound();
            e.setTag("Item", k.key.toStack(1).writeToNBT(new NBTTagCompound()));
            e.setInteger("Amount", k.amount);
            e.setLong("Dest", k.destination.toLong());
            e.setLong("RootDest", k.rootDestination.toLong());
            if (k.rootOrder != null) {
                e.setTag("RootItem", k.rootOrder.toStack(1).writeToNBT(new NBTTagCompound()));
            }
            tracked.appendTag(e);
        }
        tag.setTag(TAG_TRACKED, tracked);

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

        trackedShortages.clear();
        NBTTagList tracked = tag.getTagList(TAG_TRACKED, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < tracked.tagCount(); i++) {
            NBTTagCompound e = tracked.getCompoundTagAt(i);
            ItemStack stack = new ItemStack(e.getCompoundTag("Item"));
            if (stack.isEmpty()) continue;

            int amount = Math.max(1, e.getInteger("Amount"));
            BlockPos dest = e.hasKey("Dest", Constants.NBT.TAG_LONG) ? BlockPos.fromLong(e.getLong("Dest")) : this.pos;
            BlockPos rootDest = e.hasKey("RootDest", Constants.NBT.TAG_LONG) ? BlockPos.fromLong(e.getLong("RootDest")) : dest;

            ItemKey rootOrder = null;
            if (e.hasKey("RootItem", Constants.NBT.TAG_COMPOUND)) {
                ItemStack rootStack = new ItemStack(e.getCompoundTag("RootItem"));
                if (!rootStack.isEmpty()) rootOrder = ItemKey.of(rootStack);
            }

            trackedShortages.add(new ShortageKey(ItemKey.of(stack), amount, dest, rootDest, rootOrder));
        }
    }
}