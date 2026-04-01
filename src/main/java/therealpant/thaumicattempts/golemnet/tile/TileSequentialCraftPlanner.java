package therealpant.thaumicattempts.golemnet.tile;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.items.IItemHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import therealpant.thaumicattempts.util.ItemKey;
import therealpant.thaumicattempts.api.PatternResourceList;
import therealpant.thaumicattempts.golemcraft.item.ItemResourceList;
import therealpant.thaumicattempts.golemnet.logistics.OrderSourceType;
import therealpant.thaumicattempts.golemnet.planner.ProviderType;
import therealpant.thaumicattempts.util.ResourceIdentity;

import javax.annotation.Nullable;
import java.util.*;

public class TileSequentialCraftPlanner extends TileEntity implements ITickable {
    private static final Logger LOG = LogManager.getLogger("ThaumicAttempts/SequentialPlanner");

    public enum PlannerStatus {
        IDLE,
        SCANNING,
        PLANNING,
        FAILED
    }

    private static final String TAG_MANAGER = "Manager";
    private static final String TAG_ACTIVE = "Active";
    private static final String TAG_STATUS = "PlannerStatus";
    private static final String TAG_TRACKED = "TrackedPlannerOrders";

    private static final int KNOWLEDGE_REFRESH_TICKS = 100;
    private static final int STALL_TICKS = 80;

    @Nullable
    private BlockPos managerPos;
    private boolean active = false;
    private PlannerStatus status = PlannerStatus.IDLE;

    private int lastKnowledgeRefreshTick = -9999;

    private static final class RecipeKnowledge {
        final ItemKey result;
        final BlockPos source;
        final ProviderType providerType;
        final int outputPerCycle;
        final Map<ItemKey, Integer> inputs;

        RecipeKnowledge(ItemKey result, BlockPos source, ProviderType providerType, int outputPerCycle, Map<ItemKey, Integer> inputs) {
            this.result = result;
            this.source = source.toImmutable();
            this.providerType = providerType;
            this.outputPerCycle = Math.max(1, outputPerCycle);
            this.inputs = new LinkedHashMap<>(inputs);
        }
    }

    private static final class ObservationState {
        int firstTick;
        int lastUpdateTick;
        String missSignature;

        ObservationState(int tick, String missSignature) {
            this.firstTick = tick;
            this.lastUpdateTick = tick;
            this.missSignature = missSignature;
        }
    }

    private static final class PlannerOrderKey {
        final ItemKey key;
        final BlockPos destination;
        final int plannerQueue;

        PlannerOrderKey(ItemKey key, BlockPos destination, int plannerQueue) {
            this.key = key;
            this.destination = destination.toImmutable();
            this.plannerQueue = plannerQueue;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PlannerOrderKey)) return false;
            PlannerOrderKey that = (PlannerOrderKey) o;
            return plannerQueue == that.plannerQueue
                    && Objects.equals(key, that.key)
                    && Objects.equals(destination, that.destination);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, destination, plannerQueue);
        }

        @Override
        public String toString() {
            return key + " -> " + destination + " q=" + plannerQueue;
        }
    }

    private final Map<ItemKey, List<RecipeKnowledge>> knowledgeByResult = new HashMap<>();
    private final Map<String, ObservationState> observationStates = new HashMap<>();
    private final Map<PlannerOrderKey, Integer> trackedPlannerOrders = new HashMap<>();

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
            knowledgeByResult.clear();
            observationStates.clear();
            trackedPlannerOrders.clear();
            markDirty();
        }
    }

    public boolean isActivePlanner() {
        return active;
    }

    public PlannerStatus getStatus() {
        return status;
    }

    @Override
    public void update() {
        if (world == null || world.isRemote) return;
        TileMirrorManager manager = getManager();
        active = manager != null && manager.tryBindPlanner(pos);
        if (!active || manager == null) {
            status = PlannerStatus.IDLE;
            knowledgeByResult.clear();
            observationStates.clear();
            trackedPlannerOrders.clear();
            return;
        }

        final int now = manager.getServerTickCounter();
        if (now - lastKnowledgeRefreshTick >= KNOWLEDGE_REFRESH_TICKS || knowledgeByResult.isEmpty()) {
            rebuildKnowledgeBase(manager);
            lastKnowledgeRefreshTick = now;
        }

        status = PlannerStatus.PLANNING;
        observeAndPlan(manager, now);
        status = PlannerStatus.IDLE;
    }

    private void rebuildKnowledgeBase(TileMirrorManager manager) {
        status = PlannerStatus.SCANNING;
        knowledgeByResult.clear();

        Set<BlockPos> requesters = manager.getRequestersSnapshot();
        if (requesters == null || requesters.isEmpty()) return;


        for (BlockPos rp : requesters) {
            TileEntity te = world.getTileEntity(rp);
            if (te == null || te.isInvalid()) continue;

            if (te instanceof TilePatternRequester) {
                scanPatternRequester((TilePatternRequester) te, rp);
            } else if (te instanceof TileInfusionRequester) {
                scanInfusionRequester((TileInfusionRequester) te, rp);
            } else if (te instanceof TileResourceRequester) {
                scanResourceRequester((TileResourceRequester) te, rp);
            }
        }
        LOG.info("[Planner {}] knowledge rebuilt entries={}", pos, knowledgeByResult.size());
    }

    private void scanPatternRequester(TilePatternRequester requester, BlockPos pos) {
        ProviderType type = ProviderType.GOLEM_CRAFTER;
        TileEntity below = world == null ? null : world.getTileEntity(pos.down());
        if (below instanceof therealpant.thaumicattempts.golemcraft.tile.TileEntityArcaneCrafter) {
            type = ProviderType.ARCANE_CRAFTER;
        }

        List<ItemStack> results = requester.listCraftableResults();
        for (ItemStack out : results) {
            if (out == null || out.isEmpty()) continue;

            int outPerCycle = Math.max(1, requester.getPerCraftOutputCountFor(out));
            List<ItemStack> need = requester.getRecipeInputsFor(out, 1);
            Map<ItemKey, Integer> inputs = new LinkedHashMap<>();
            if (need != null) {
                for (ItemStack s : need) {
                    if (s == null || s.isEmpty()) continue;
                    inputs.merge(ItemKey.of(s), Math.max(1, s.getCount()), Integer::sum);
                }
            }
            registerKnowledge(new RecipeKnowledge(ItemKey.of(out), pos, type, outPerCycle, inputs));
        }
    }
    private void scanInfusionRequester(TileInfusionRequester requester, BlockPos pos) {
        List<ItemStack> results = requester.listCraftableResults();
        for (ItemStack out : results) {
            if (out == null || out.isEmpty()) continue;
            int slot = requester.findPatternSlotFor(out);
            if (slot < 0) continue;

            int outPerCycle = Math.max(1, requester.getPerCraftOutputCountFor(out));
            List<PatternResourceList.Entry> resources = requester.getResourcesForSlot(slot);

            Map<ItemKey, Integer> inputs = new LinkedHashMap<>();
            if (resources != null) {
                for (PatternResourceList.Entry e : resources) {
                    if (e == null) continue;
                    inputs.merge(e.getKey(), Math.max(1, e.getCount()), Integer::sum);
                }
            }
            registerKnowledge(new RecipeKnowledge(ItemKey.of(out), pos, ProviderType.INFUSION_REQUESTER, outPerCycle, inputs));
        }
    }

        private void scanResourceRequester(TileResourceRequester requester, BlockPos pos) {
        IItemHandler patterns = requester.getPatternHandler();
        if (patterns == null) return;

            for (int i = 0; i < patterns.getSlots(); i++) {
                ItemStack pattern = patterns.getStackInSlot(i);
                if (pattern.isEmpty() || !(pattern.getItem() instanceof ItemResourceList)) continue;

                ItemStack out = ItemResourceList.getPreviewOrFirstEntry(pattern);
                if (out.isEmpty()) continue;

                Map<ItemKey, Integer> inputs = new LinkedHashMap<>();
                List<PatternResourceList.Entry> resources = PatternResourceList.build(pattern);
                if (resources != null) {
                    for (PatternResourceList.Entry e : resources) {
                        if (e == null) continue;
                        inputs.merge(e.getKey(), Math.max(1, e.getCount()), Integer::sum);
                    }
                }
                registerKnowledge(new RecipeKnowledge(ItemKey.of(out), pos, ProviderType.RESOURCE_REQUESTER, Math.max(1, out.getCount()), inputs));
            }
    }

    private void registerKnowledge(RecipeKnowledge knowledge) {
        if (knowledge == null || knowledge.result == null || knowledge.result.toStack(1).isEmpty()) return;
        List<RecipeKnowledge> lst = knowledgeByResult.computeIfAbsent(knowledge.result, x -> new ArrayList<>());
        for (RecipeKnowledge ex : lst) {
            if (ex.source.equals(knowledge.source)) return;
        }
        lst.add(knowledge);
    }

    private void observeAndPlan(TileMirrorManager manager, int now) {
        List<TileMirrorManager.PlannerCraftObservation> activeCrafts = manager.getActiveCraftObservations();
        if (activeCrafts.isEmpty()) {
            observationStates.clear();
            trackedPlannerOrders.clear();
            return;
        }

        Map<ItemKey, Integer> stock = manager.getReachableCatalog();
        if (stock == null) stock = Collections.emptyMap();

        Set<PlannerOrderKey> currentlyMissing = new HashSet<>();
        Set<String> seenObs = new HashSet<>();

        for (TileMirrorManager.PlannerCraftObservation obs : activeCrafts) {
            if (obs == null || obs.wanted == null || obs.wanted.toStack(1).isEmpty()) continue;

            RecipeKnowledge rootRecipe = pickRecipe(obs.wanted);
            if (rootRecipe == null) {
                LOG.info("[Planner {}] skip observation: no recipe for root={} crafter={} queue={}",
                        pos, obs.wanted, obs.crafter, obs.queueId);
                continue;
            }

            int craftsNeeded = (Math.max(1, obs.remaining) + rootRecipe.outputPerCycle - 1) / rootRecipe.outputPerCycle;
            if (craftsNeeded <= 0) continue;

            Map<ItemKey, Integer> miss = computeMissingForCrafter(obs.crafter, rootRecipe, craftsNeeded);
            if (miss.isEmpty()) continue;

            String obsId = obs.queueId + "|" + obs.destination.toLong() + "|" + obs.crafter.toLong() + "|" + obs.wanted.hashCode();
            seenObs.add(obsId);

            String missSig = miss.toString();
            ObservationState st = observationStates.get(obsId);
            if (st == null) {
                observationStates.put(obsId, new ObservationState(now, missSig));
                LOG.info("[Planner {}] observe start root={} crafter={} queue={} miss={}",
                        pos, obs.wanted, obs.crafter, obs.queueId, miss);
                continue;
            }

            if (!Objects.equals(st.missSignature, missSig)) {
                st.firstTick = now;
                st.lastUpdateTick = now;
                st.missSignature = missSig;
                LOG.info("[Planner {}] observe reset root={} crafter={} queue={} missChanged={}",
                        pos, obs.wanted, obs.crafter, obs.queueId, miss);
                continue;
            }

            st.lastUpdateTick = now;

            if ((now - st.firstTick) < STALL_TICKS) {
                LOG.debug("[Planner {}] observe waiting root={} crafter={} queue={} age={} miss={}",
                        pos, obs.wanted, obs.crafter, obs.queueId, (now - st.firstTick), miss);
                continue;
            }

            Map<ItemKey, Integer> virtualStock = new HashMap<>(stock);
            List<Map.Entry<ItemKey, Integer>> toOrder = new ArrayList<>();

            boolean chainOk = true;
            for (Map.Entry<ItemKey, Integer> e : miss.entrySet()) {
                if (!planRecursively(e.getKey(), e.getValue(), virtualStock, new HashSet<>(), toOrder)) {
                    chainOk = false;
                    LOG.warn("[Planner {}] recursive planning failed root={} missingKey={} amount={} crafter={} queue={}",
                            pos, obs.wanted, e.getKey(), e.getValue(), obs.crafter, obs.queueId);
                    break;
                }
            }

            if (!chainOk || toOrder.isEmpty()) continue;

            int plannerQueue = manager.allocatePlannerQueue(obs.queueId);

            LinkedHashMap<ItemKey, Integer> dedupPlan = new LinkedHashMap<>();
            for (Map.Entry<ItemKey, Integer> e : toOrder) {
                PlannerOrderKey k = new PlannerOrderKey(e.getKey(), obs.crafter, plannerQueue);
                currentlyMissing.add(k);

                if (trackedPlannerOrders.containsKey(k)) {
                    LOG.debug("[Planner {}] suborder already tracked root={} sub={} amount={} dest={} plannerQueue={}",
                            pos, obs.wanted, e.getKey(), e.getValue(), obs.crafter, plannerQueue);
                    continue;
                }

                dedupPlan.merge(e.getKey(), Math.max(1, e.getValue()), Integer::sum);
            }

            if (dedupPlan.isEmpty()) {
                LOG.debug("[Planner {}] no new suborders after dedupe root={} crafter={} plannerQueue={}",
                        pos, obs.wanted, obs.crafter, plannerQueue);
                continue;
            }

            List<Map.Entry<ItemKey, Integer>> entries = new ArrayList<>();
            for (Map.Entry<ItemKey, Integer> e : dedupPlan.entrySet()) {
                entries.add(new AbstractMap.SimpleImmutableEntry<>(e.getKey(), e.getValue()));
            }

            LOG.info("[Planner {}] planner created fallback sub-order root={} rootQueue={} plannerQueue={} crafter={} orders={}",
                    pos, obs.wanted, obs.queueId, plannerQueue, obs.crafter, dedupPlan);

            for (Map.Entry<ItemKey, Integer> e : dedupPlan.entrySet()) {
                manager.submitOrder(e.getKey(), e.getValue(), OrderSourceType.PLANNER, this.pos, null);
            }

            for (Map.Entry<ItemKey, Integer> e : dedupPlan.entrySet()) {
                trackedPlannerOrders.put(new PlannerOrderKey(e.getKey(), obs.crafter, plannerQueue), e.getValue());
            }

            markDirty();
        }

        observationStates.keySet().retainAll(seenObs);
        trackedPlannerOrders.keySet().removeIf(k -> !currentlyMissing.contains(k));
    }

    private Map<ItemKey, Integer> computeMissingForCrafter(BlockPos crafterPos, RecipeKnowledge rootRecipe, int craftsNeeded) {
        Map<ItemKey, Integer> miss = new LinkedHashMap<>();
        TileEntity te = world == null ? null : world.getTileEntity(crafterPos);
        if (!(te instanceof therealpant.thaumicattempts.golemcraft.tile.TileEntityGolemCrafter)) return miss;

        IItemHandler in = ((therealpant.thaumicattempts.golemcraft.tile.TileEntityGolemCrafter) te).getInputHandler();
        if (in == null) return miss;

        for (Map.Entry<ItemKey, Integer> e : rootRecipe.inputs.entrySet()) {
            ItemKey key = e.getKey();
            int want = Math.max(1, e.getValue()) * Math.max(1, craftsNeeded);
            ItemStack like = key.toStack(1);
            if (like.isEmpty()) continue;

            int have = 0;
            for (int s = 0; s < in.getSlots(); s++) {
                ItemStack cur = in.getStackInSlot(s);
                if (cur.isEmpty()) continue;
                if (ResourceIdentity.sameResource(cur, like)) {
                    have += cur.getCount();
                }
            }
            if (have < want) {
                miss.put(key, want - have);
            }
        }

        return miss;
    }

    private boolean planRecursively(ItemKey key,
                                    int amount,
                                    Map<ItemKey, Integer> stock,
                                    Set<ItemKey> visiting,
                                    List<Map.Entry<ItemKey, Integer>> orders) {
        if (key == null || key.toStack(1).isEmpty() || amount <= 0) return true;

        int available = Math.max(0, stock.getOrDefault(key, 0));
        if (available >= amount) {
            stock.put(key, available - amount);
            return true;
        }

        int need = amount - available;
        stock.put(key, 0);

        if (!visiting.add(key)) return false;

        RecipeKnowledge recipe = pickRecipe(key);
        if (recipe == null) {
            visiting.remove(key);
            return false;
        }

        int crafts = (need + recipe.outputPerCycle - 1) / recipe.outputPerCycle;
        for (Map.Entry<ItemKey, Integer> in : recipe.inputs.entrySet()) {
            int required = Math.max(1, in.getValue()) * crafts;
            if (!planRecursively(in.getKey(), required, stock, visiting, orders)) {
                visiting.remove(key);
                return false;
            }
        }

        int produced = crafts * recipe.outputPerCycle;
        int leftover = Math.max(0, produced - need);
        if (leftover > 0) stock.merge(key, leftover, Integer::sum);

        orders.add(new AbstractMap.SimpleImmutableEntry<>(key, need));

        visiting.remove(key);
        return true;
    }

    @Nullable
    private BlockPos findSourceFor(ItemKey key) {
        RecipeKnowledge recipe = pickRecipe(key);
        return recipe == null ? null : recipe.source;
    }

    @Nullable
    private RecipeKnowledge pickRecipe(ItemKey key) {
        List<RecipeKnowledge> options = knowledgeByResult.get(key);
        if (options == null || options.isEmpty()) return null;
        return options.get(0);
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
        for (Map.Entry<PlannerOrderKey, Integer> e : trackedPlannerOrders.entrySet()) {
            NBTTagCompound row = new NBTTagCompound();
            row.setTag("Item", e.getKey().key.toStack(1).writeToNBT(new NBTTagCompound()));
            row.setLong("Dest", e.getKey().destination.toLong());
            row.setInteger("PlannerQueue", e.getKey().plannerQueue);
            row.setInteger("Amount", Math.max(1, e.getValue()));
            tracked.appendTag(row);
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

        trackedPlannerOrders.clear();
        NBTTagList tracked = tag.getTagList(TAG_TRACKED, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < tracked.tagCount(); i++) {
            NBTTagCompound row = tracked.getCompoundTagAt(i);
            ItemStack stack = new ItemStack(row.getCompoundTag("Item"));
            if (stack.isEmpty()) continue;

            BlockPos dest = row.hasKey("Dest", Constants.NBT.TAG_LONG) ? BlockPos.fromLong(row.getLong("Dest")) : this.pos;
            int plannerQueue = row.hasKey("PlannerQueue", Constants.NBT.TAG_INT) ? row.getInteger("PlannerQueue") : 0;
            int amount = Math.max(1, row.getInteger("Amount"));
            trackedPlannerOrders.put(new PlannerOrderKey(ItemKey.of(stack), dest, plannerQueue), amount);
        }
    }
}