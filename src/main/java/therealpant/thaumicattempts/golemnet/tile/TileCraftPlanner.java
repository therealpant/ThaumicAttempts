package therealpant.thaumicattempts.golemnet.tile;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.items.IItemHandler;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;
import therealpant.thaumicattempts.api.ICraftEndpoint;
import therealpant.thaumicattempts.api.IPatternedWorksite;
import therealpant.thaumicattempts.golemcraft.item.ItemResourceList;
import therealpant.thaumicattempts.golemcraft.tile.TileEntityGolemCrafter;
import therealpant.thaumicattempts.util.ItemKey;
import therealpant.thaumicattempts.util.ResourceIdentity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

public class TileCraftPlanner extends TileEntity implements ITickable, IAnimatable {
    private static final String TAG_MANAGER = "manager";
    private static final String TAG_ANIM_SEED = "animSeed";
    private static final String TAG_PLANNED_CHAINS = "plannedChains";
    private static final String TAG_NEXT_CHAIN_ID = "nextChainId";
    private static final int RESCAN_PERIOD_TICKS = 200;
    private static final int MAX_CRAFTS_PER_BATCH = 64;
    @Nullable
    private BlockPos managerPos;
    private int rescanCooldown = 0;
    private final Map<ItemKey, BlockPos> recipeIndex = new HashMap<>();
    private final Map<ItemKey, Integer> chainDepthCache = new HashMap<>();
    private final List<PlannedChain> plannedChains = new ArrayList<>();
    private int nextChainId = 1;

    // Визуальная анимация (копия параметров dispatcher)
    public float animSpeed = 0.625f;
    public long animSeed = 2L;
    public int[] picks = {0, 1, 2}; // индексы костей 0..7
    public int[] axes  = {0, 1, 2}; // 0=X,1=Y,2=Z
    public long cycleIdx = -1L;
    public static final int DUR_A = 20;
    public static final int GAP = 4;
    private final AnimationFactory factory = new AnimationFactory(this);

    public void reseedForCycle(long newIdx) {
        Random r = new Random(animSeed ^ (newIdx * 0x9E3779B97F4A7C15L));
        int[] a = {0,1,2,3,4,5,6,7};
        for (int i = a.length - 1; i > 0; --i) {
            int j = r.nextInt(i + 1);
            int t = a[i]; a[i] = a[j]; a[j] = t;
        }
        picks[0] = a[0];
        picks[1] = a[1];
        picks[2] = a[2];

        axes[0] = r.nextInt(3);
        axes[1] = r.nextInt(3);
        axes[2] = r.nextInt(3);

        cycleIdx = newIdx;
    }

    public static long calcCycleLenForSpeed(float animSpeed) {
        float sp = Math.max(0.05f, animSpeed);
        int base = DUR_A + GAP;
        long aLen = Math.max(1, Math.round(base / sp));
        return aLen * 4L;
    }

    public long cycleLen() {
        return calcCycleLenForSpeed(this.animSpeed);
    }

    @Override
    public void registerControllers(AnimationData data) {
        AnimationController<TileCraftPlanner> baseController =
                new AnimationController<>(this, "base_cycle", 0, this::baseCyclePredicate);
        data.addAnimationController(baseController);
    }

    private <E extends IAnimatable> PlayState baseCyclePredicate(AnimationEvent<E> event) {
        event.getController().setAnimation(
                new AnimationBuilder().addAnimation("animation.model.cycle_base", true)
        );
        return PlayState.CONTINUE;
    }

    @Override
    public AnimationFactory getFactory() {
        return factory;
    }
    @Nullable
    public BlockPos getManagerPos() {
        return managerPos;
    }

    public void setManagerPos(@Nullable BlockPos managerPos) {
        BlockPos next = managerPos == null ? null : managerPos.toImmutable();
        if (!Objects.equals(this.managerPos, next)) {
            this.managerPos = next;
            forceRescan();
            markDirty();
        }
    }

    public void forceRescan() {
        rescanCooldown = 0;
    }

    @Override
    public void update() {
        if (world == null || world.isRemote) return;
        if (managerPos == null) return;
        if (--rescanCooldown <= 0) {
            rebuildCraftIndex();
            rescanCooldown = RESCAN_PERIOD_TICKS;
        }
        tickPlannedChains();
    }

    public boolean canCraftOrderViaPlanner(List<Map.Entry<ItemKey, Integer>> requests, TileMirrorManager manager) {
        if (world == null || manager == null || requests == null || requests.isEmpty()) return false;
        if (!manager.getPos().equals(managerPos)) return false;

        rebuildCraftIndex();

        Map<ItemKey, Integer> stock = buildStockSnapshot(manager);

        for (Map.Entry<ItemKey, Integer> req : requests) {
            if (req == null || req.getKey() == null || req.getKey() == ItemKey.EMPTY) continue;
            ItemStack resultLike = req.getKey().toStack(1);
            BlockPos endpoint = findRequesterForKey(req.getKey());
            if (endpoint == null) return false;
            int slot = findPatternSlot(endpoint, resultLike);
            PlannedCraft plan = buildPlannedCraft(endpoint, slot, resultLike, Math.max(1, req.getValue()),
                    manager.getPos(), -1, stock, new HashSet<ItemKey>());
            if (plan == null) return false;
        }
        return true;
    }

    public int enqueuePlannedCraftOrder(BlockPos terminalPos, BlockPos endpointPos, int patternSlot,
                                        ItemStack resultLike, int items, TileMirrorManager manager) {
        if (world == null || world.isRemote || manager == null) return 0;
        if (terminalPos == null || endpointPos == null || resultLike == null || resultLike.isEmpty() || items <= 0) return 0;
        if (!manager.getPos().equals(managerPos)) return 0;

        rebuildCraftIndex();

        Map<ItemKey, Integer> stock = buildStockSnapshot(manager);
        BlockPos outputDest = terminalPos.toImmutable();
        TileEntity endpointTile = world.getTileEntity(endpointPos);
        if (!(endpointTile instanceof ICraftEndpoint)) return 0;
        int perCraft = Math.max(1, ((ICraftEndpoint) endpointTile).getPerCraftOutputCountFor(resultLike));
        int maxItemsPerChain = Math.max(perCraft, perCraft * MAX_CRAFTS_PER_BATCH);

        int left = items;
        int accepted = 0;
        while (left > 0) {
            int chunk = Math.min(left, maxItemsPerChain);
            PlannedCraft root = buildPlannedCraft(endpointPos, patternSlot, resultLike, chunk,
                    outputDest, -1, stock, new HashSet<ItemKey>());
            if (root == null) break;
            enqueueChain(terminalPos, root);
            int got = Math.min(chunk, root.expectedItems);
            accepted += got;
            left -= got;
        }
        if (accepted <= 0) return 0;
        tickPlannedChains();
        return Math.min(items, accepted);
    }

    public int enqueuePlannedDeliveryOrder(BlockPos terminalPos, ItemStack like, int amount, TileMirrorManager manager) {
        if (world == null || world.isRemote || manager == null) return 0;
        if (terminalPos == null || like == null || like.isEmpty() || amount <= 0) return 0;
        if (!manager.getPos().equals(managerPos)) return 0;

        rebuildCraftIndex();

        ItemKey key = ItemKey.of(like);
        if (key == null || key == ItemKey.EMPTY) return 0;

        Map<ItemKey, Integer> stock = buildStockSnapshot(manager);
        int direct = takeFromStock(stock, key, amount);
        int accepted = 0;
        if (direct > 0) {
            accepted += manager.enqueueTerminalDeliveryOrderDirect(terminalPos, like, direct);
        }

        int missing = amount - direct;
        if (missing > 0) {
            BlockPos endpoint = findRequesterForKey(key);
            if (endpoint == null) return accepted;
            ItemStack resultLike = key.toStack(1);
            int slot = findPatternSlot(endpoint, resultLike);

            TileEntity endpointTile = world.getTileEntity(endpoint);
            int perCraft = endpointTile instanceof ICraftEndpoint
                    ? Math.max(1, ((ICraftEndpoint) endpointTile).getPerCraftOutputCountFor(resultLike))
                    : 1;
            int maxItemsPerChain = Math.max(perCraft, perCraft * MAX_CRAFTS_PER_BATCH);

            int left = missing;
            while (left > 0) {
                int chunk = Math.min(left, maxItemsPerChain);
                PlannedCraft root = buildPlannedCraft(endpoint, slot, resultLike, chunk,
                        terminalPos, -1, stock, new HashSet<ItemKey>());
                if (root == null) break;
                enqueueChain(terminalPos, root);
                int got = Math.min(chunk, root.expectedItems);
                accepted += got;
                left -= got;
            }
        }

        tickPlannedChains();
        return Math.min(amount, accepted);
    }

    private Map<ItemKey, Integer> buildStockSnapshot(TileMirrorManager manager) {
        Map<ItemKey, Integer> stock = new HashMap<>();
        LinkedHashMap<ItemKey, Integer> reachable = manager.getReachableCatalog();
        for (Map.Entry<ItemKey, Integer> en : reachable.entrySet()) {
            if (en.getKey() == null || en.getKey() == ItemKey.EMPTY) continue;
            stock.put(en.getKey(), Math.max(0, en.getValue()));
        }
        for (ItemKey key : new ArrayList<>(stock.keySet())) {
            stock.put(key, stock.getOrDefault(key, 0) + Math.max(0, manager.countBuffered(key)));
        }
        for (PlannedChain chain : plannedChains) {
            reservePlannedInputs(stock, chain.root);
        }
        return stock;
    }

    private void reservePlannedInputs(Map<ItemKey, Integer> stock, PlannedCraft node) {
        if (node == null || node.done) return;
        for (Map.Entry<ItemKey, Integer> e : node.rawInputs.entrySet()) {
            if (e.getKey() == null || e.getKey() == ItemKey.EMPTY) continue;
            takeFromStock(stock, e.getKey(), Math.max(0, e.getValue()));
        }
        for (PlannedCraft child : node.children) {
            reservePlannedInputs(stock, child);
        }
    }

    private static final class PlannedChain {
        final int id;
        final BlockPos terminalPos;
        final PlannedCraft root;

        PlannedChain(int id, BlockPos terminalPos, PlannedCraft root) {
            this.id = Math.max(1, id);
            this.terminalPos = terminalPos.toImmutable();
            this.root = root;
        }
    }

    private static final class PlannedCraft {
        final BlockPos endpointPos;
        final BlockPos outputDestPos;
        final int outputDestSide;
        final int patternSlot;
        final ItemStack resultLike;
        final int requestedItems;
        final int perCraft;
        final int crafts;
        final int expectedItems;
        final LinkedHashMap<ItemKey, Integer> rawInputs = new LinkedHashMap<>();
        final LinkedHashMap<ItemKey, Integer> directInputs = new LinkedHashMap<>();
        final List<PlannedCraft> children = new ArrayList<>();
        int batchId;
        int submittedCrafts;
        int completedItems;
        int activeBatchItems;
        boolean done;
        boolean failed;

        PlannedCraft(BlockPos endpointPos, BlockPos outputDestPos, int outputDestSide, int patternSlot,
                     ItemStack resultLike, int requestedItems, int perCraft, int crafts, int expectedItems) {
            this.endpointPos = endpointPos.toImmutable();
            this.outputDestPos = outputDestPos.toImmutable();
            this.outputDestSide = outputDestSide;
            this.patternSlot = patternSlot;
            this.resultLike = ResourceIdentity.stackForRequest(resultLike, 1);
            this.requestedItems = Math.max(1, requestedItems);
            this.perCraft = Math.max(1, perCraft);
            this.crafts = Math.max(1, crafts);
            this.expectedItems = Math.max(1, expectedItems);
        }
    }

    @Nullable
    private PlannedCraft buildPlannedCraft(BlockPos endpointPos, int patternSlot, ItemStack resultLike, int items,
                                           BlockPos outputDestPos, int outputDestSide,
                                           Map<ItemKey, Integer> stock, Set<ItemKey> visiting) {
        TileEntity te = world.getTileEntity(endpointPos);
        if (!(te instanceof ICraftEndpoint)) return null;
        ICraftEndpoint endpoint = (ICraftEndpoint) te;

        int perCraft = Math.max(1, endpoint.getPerCraftOutputCountFor(resultLike));
        int crafts = (items + perCraft - 1) / perCraft;
        if (crafts <= 0) return null;

        ItemKey resultKey = ItemKey.of(resultLike);
        if (!visiting.add(resultKey)) return null;

        PlannedCraft node = new PlannedCraft(endpointPos, outputDestPos, outputDestSide, patternSlot,
                resultLike, items, perCraft, crafts, crafts * perCraft);

        Map<ItemKey, Integer> perCycle = endpoint.getInputsPerCycle(resultLike);
        if (perCycle == null) perCycle = Collections.emptyMap();

        BlockPos ingredientDest = resolveCraftDeliveryPos(endpointPos);
        if (ingredientDest == null) {
            visiting.remove(resultKey);
            return null;
        }

        for (Map.Entry<ItemKey, Integer> e : perCycle.entrySet()) {
            if (e.getKey() == null || e.getKey() == ItemKey.EMPTY) continue;
            int total = Math.max(0, e.getValue()) * crafts;
            if (total <= 0) continue;
            node.rawInputs.merge(e.getKey(), total, Integer::sum);

            int direct = takeFromStock(stock, e.getKey(), total);
            if (direct > 0) {
                node.directInputs.merge(e.getKey(), direct, Integer::sum);
            }

            int missing = total - direct;
            if (missing <= 0) continue;

            BlockPos childEndpoint = findRequesterForKey(e.getKey());
            if (childEndpoint == null) {
                visiting.remove(resultKey);
                return null;
            }

            ItemStack childResult = e.getKey().toStack(1);
            if (visiting.contains(ItemKey.of(childResult))) {
                visiting.remove(resultKey);
                return null;
            }

            int childSlot = findPatternSlot(childEndpoint, childResult);
            BlockPos childDest = managerPos == null ? ingredientDest : managerPos.toImmutable();
            PlannedCraft child = buildPlannedCraft(childEndpoint, childSlot, childResult, missing,
                    childDest, -1, stock, visiting);
            if (child == null) {
                visiting.remove(resultKey);
                return null;
            }
            node.children.add(child);
        }

        visiting.remove(resultKey);
        return node;
    }

    private void enqueueChain(BlockPos terminalPos, PlannedCraft root) {
        int id = nextChainId++;
        if (nextChainId <= 0) nextChainId = 1;
        plannedChains.add(new PlannedChain(id, terminalPos, root));
        markDirty();
    }

    private void tickPlannedChains() {
        if (world == null || world.isRemote || managerPos == null || plannedChains.isEmpty()) return;
        TileEntity te = world.getTileEntity(managerPos);
        if (!(te instanceof TileMirrorManager)) return;
        TileMirrorManager manager = (TileMirrorManager) te;

        boolean changed = false;
        for (Iterator<PlannedChain> it = plannedChains.iterator(); it.hasNext(); ) {
            PlannedChain chain = it.next();
            if (tickPlannedNode(chain.root, chain.terminalPos, manager)) {
                if (chain.root.failed) notifyTerminalCraftFailed(chain.terminalPos, chain.root);
                it.remove();
                changed = true;
            }
        }
        if (changed) markDirty();
    }

    private boolean tickPlannedNode(PlannedCraft node, BlockPos terminalPos, TileMirrorManager manager) {
        if (node == null) return true;
        if (node.done) return true;

        boolean allChildrenDone = true;
        for (PlannedCraft child : node.children) {
            if (child.done) continue;
            tickPlannedNode(child, terminalPos, manager);
            if (!child.done) allChildrenDone = false;
            if (child.failed) {
                node.failed = true;
                node.done = true;
                markDirty();
                return true;
            }
        }
        if (!allChildrenDone) return false;

        if (node.batchId <= 0 && node.completedItems < node.expectedItems) {
            int remainingCrafts = Math.max(0, node.crafts - node.submittedCrafts);
            if (remainingCrafts <= 0) {
                node.done = true;
                markDirty();
                return true;
            }
            int batchCrafts = Math.min(MAX_CRAFTS_PER_BATCH, remainingCrafts);
            int batchItems = Math.max(1, batchCrafts * node.perCraft);
            int batchId = manager.enqueuePlannedCraftBatch(
                    terminalPos,
                    node.endpointPos,
                    node.patternSlot,
                    node.resultLike,
                    batchItems,
                    node.outputDestPos,
                    node.outputDestSide,
                    0,
                    scaledInputs(node.rawInputs, batchCrafts, node.crafts)
            );
            if (batchId <= 0) {
                node.failed = true;
                node.done = true;
                markDirty();
                return true;
            }
            node.batchId = batchId;
            node.activeBatchItems = batchItems;
            node.submittedCrafts += batchCrafts;
            manager.processTerminalDeliveryOrders();
            markDirty();
            return false;
        }

        if (node.batchId > 0 && !manager.isTerminalCraftBatchActive(node.batchId)) {
            if (manager.wasTerminalCraftBatchFailed(node.batchId)) {
                node.failed = true;
                node.done = true;
            } else {
                node.completedItems += Math.max(1, node.activeBatchItems);
                node.batchId = 0;
                node.activeBatchItems = 0;
                if (node.completedItems >= node.expectedItems) {
                    node.done = true;
                }
            }
            markDirty();
        }
        return node.done;
    }

    private LinkedHashMap<ItemKey, Integer> scaledInputs(Map<ItemKey, Integer> totalInputs, int batchCrafts, int totalCrafts) {
        LinkedHashMap<ItemKey, Integer> out = new LinkedHashMap<>();
        if (totalInputs == null || totalInputs.isEmpty() || batchCrafts <= 0 || totalCrafts <= 0) return out;
        for (Map.Entry<ItemKey, Integer> e : totalInputs.entrySet()) {
            if (e.getKey() == null || e.getKey() == ItemKey.EMPTY) continue;
            int total = Math.max(0, e.getValue());
            if (total <= 0) continue;
            int count = (int) (((long) total * (long) batchCrafts) / (long) totalCrafts);
            if (count > 0) out.merge(e.getKey(), count, Integer::sum);
        }
        return out;
    }

    private void notifyTerminalCraftFailed(BlockPos terminalPos, PlannedCraft root) {
        if (world == null || terminalPos == null || root == null) return;
        TileEntity te = world.getTileEntity(terminalPos);
        if (te instanceof TileOrderTerminal) {
            ((TileOrderTerminal) te).onCraftOrderFailed(root.resultLike, root.requestedItems);
        } else if (te instanceof TileRevisionPiedestal) {
            ((TileRevisionPiedestal) te).onCraftOrderFailed(root.resultLike, root.requestedItems);
        }
    }

    private int takeFromStock(Map<ItemKey, Integer> stock, ItemKey key, int amount) {
        if (stock == null || stock.isEmpty() || key == null || key == ItemKey.EMPTY || amount <= 0) return 0;
        int left = amount;

        int exact = Math.max(0, stock.getOrDefault(key, 0));
        if (exact > 0) {
            int take = Math.min(exact, left);
            stock.put(key, exact - take);
            left -= take;
        }

        if (left <= 0) return amount;

        ItemStack like = key.toStack(1);
        for (Map.Entry<ItemKey, Integer> en : stock.entrySet()) {
            if (left <= 0) break;
            if (en.getKey() == null || en.getKey() == ItemKey.EMPTY || en.getKey().equals(key)) continue;
            ItemStack candidate = en.getKey().toStack(1);
            if (candidate.isEmpty() || !ResourceIdentity.sameProvisionResource(candidate, like)) continue;

            int have = Math.max(0, en.getValue());
            if (have <= 0) continue;
            int take = Math.min(have, left);
            en.setValue(have - take);
            left -= take;
        }

        return amount - left;
    }

    @Nullable
    private BlockPos resolveCraftDeliveryPos(BlockPos endpointPos) {
        if (world == null || endpointPos == null) return null;
        TileEntity te = world.getTileEntity(endpointPos);
        if (te instanceof TilePatternRequester) {
            TileEntity below = world.getTileEntity(endpointPos.down());
            if (below instanceof IPatternedWorksite || below instanceof ICraftEndpoint) {
                return endpointPos.down().toImmutable();
            }
        }
        return endpointPos.toImmutable();
    }

    private int findPatternSlot(BlockPos endpointPos, ItemStack resultLike) {
        if (world == null || endpointPos == null || resultLike == null || resultLike.isEmpty()) return -1;
        TileEntity te = world.getTileEntity(endpointPos);
        if (te instanceof TilePatternRequester) {
            return ((TilePatternRequester) te).findPatternIndexForResultLike(resultLike);
        }
        if (te instanceof TileInfusionRequester) {
            return ((TileInfusionRequester) te).findPatternSlotFor(resultLike);
        }
        if (te instanceof TileEntityGolemCrafter) {
            return ((TileEntityGolemCrafter) te).findPatternIndexForResultLike(resultLike);
        }
        if (te instanceof TileResourceRequester) {
            IItemHandler patt = ((TileResourceRequester) te).getPatternHandler();
            if (patt == null) return -1;
            for (int i = 0; i < patt.getSlots(); i++) {
                ItemStack pattern = patt.getStackInSlot(i);
                if (pattern.isEmpty() || !(pattern.getItem() instanceof ItemResourceList)) continue;
                ItemStack preview = ItemResourceList.getPreviewOrFirstEntry(pattern);
                if (!preview.isEmpty() && ResourceIdentity.sameResource(preview, resultLike)) return i;
            }
        }
        return -1;
    }

    private void rebuildCraftIndex() {
        recipeIndex.clear();
        chainDepthCache.clear();
        if (world == null || managerPos == null) return;
        TileEntity teMgr = world.getTileEntity(managerPos);
        if (!(teMgr instanceof TileMirrorManager)) return;

        Set<BlockPos> requesters = ((TileMirrorManager) teMgr).getRequestersSnapshot();
        for (BlockPos rp : requesters) {
            TileEntity te = world.getTileEntity(rp);
            BlockPos endpointPos = rp;
            TileEntity below = world.getTileEntity(rp.down());
            if (te instanceof TilePatternRequester
                    && (below instanceof TileResourceRequester || below instanceof TileInfusionRequester)) {
                endpointPos = rp.down();
                te = world.getTileEntity(endpointPos);
            }
            if (!(te instanceof ICraftEndpoint)) continue;
            List<ItemStack> results = ((ICraftEndpoint) te).listCraftableResults();
            if (results == null || results.isEmpty()) continue;
            for (ItemStack out : results) {
                if (out == null || out.isEmpty()) continue;
                recipeIndex.put(ItemKey.of(out), endpointPos.toImmutable());
            }
        }
    }

    private boolean tryBuildPlanFor(ItemStack targetLike, int amount, Map<ItemKey, Integer> stock, Set<ItemKey> visiting) {
        if (targetLike == null || targetLike.isEmpty() || amount <= 0) return true;

        ItemKey key = ItemKey.of(targetLike);
        int have = Math.max(0, stock.getOrDefault(key, 0));
        if (have >= amount) {
            stock.put(key, have - amount);
            return true;
        }

        int missing = amount - have;
        stock.put(key, 0);

        if (!visiting.add(key)) return false;

        BlockPos rp = findRequesterForKey(key);
        if (rp == null) {
            visiting.remove(key);
            return false;
        }

        TileEntity te = world.getTileEntity(rp);
        if (!(te instanceof ICraftEndpoint)) {
            visiting.remove(key);
            return false;
        }

        ICraftEndpoint ep = (ICraftEndpoint) te;
        int perCraft = Math.max(1, ep.getPerCraftOutputCountFor(targetLike));
        int crafts = (missing + perCraft - 1) / perCraft;

        List<ItemStack> needs = getRecipeInputsForEndpoint(te, targetLike, crafts);
        for (ItemStack need : needs) {
            if (need == null || need.isEmpty()) continue;
            if (!tryBuildPlanFor(need, Math.max(1, need.getCount()), stock, visiting)) {
                visiting.remove(key);
                return false;
            }
        }

        int produced = crafts * perCraft;
        int leftover = Math.max(0, produced - missing);
        if (leftover > 0) {
            stock.put(key, stock.getOrDefault(key, 0) + leftover);
        }

        int bestChildDepth = 0;
        for (ItemStack need : needs) {
            if (need == null || need.isEmpty()) continue;
            bestChildDepth = Math.max(bestChildDepth, chainDepthCache.getOrDefault(ItemKey.of(need), 0));
        }
        chainDepthCache.put(key, bestChildDepth + 1);
        visiting.remove(key);
        return true;
    }

    private List<ItemStack> getRecipeInputsForEndpoint(TileEntity te, ItemStack like, int crafts) {
        if (te instanceof TilePatternRequester) {
            return ((TilePatternRequester) te).getRecipeInputsFor(like, crafts);
        }
        if (te instanceof TileInfusionRequester) {
            TileInfusionRequester inf = (TileInfusionRequester) te;
            int slot = inf.findPatternSlotFor(like);
            if (slot < 0) return Collections.emptyList();
            List<therealpant.thaumicattempts.api.PatternResourceList.Entry> entries = inf.getResourcesForSlot(slot);
            if (entries == null || entries.isEmpty()) return Collections.emptyList();
            List<ItemStack> out = new ArrayList<>();
            for (therealpant.thaumicattempts.api.PatternResourceList.Entry e : entries) {
                if (e == null || e.getKey() == null || e.getKey() == ItemKey.EMPTY) continue;
                ItemStack st = e.getKey().toStack(Math.max(1, e.getCount() * Math.max(1, crafts)));
                if (!st.isEmpty()) out.add(st);
            }
            return out;
        }
        return Collections.emptyList();
    }

    @Nullable
    private BlockPos findRequesterForKey(ItemKey key) {
        if (key == null || key == ItemKey.EMPTY) return null;
        BlockPos cached = recipeIndex.get(key);
        if (cached != null) return cached;

        ItemStack like = key.toStack(1);
        if (like.isEmpty()) return null;
        for (Map.Entry<ItemKey, BlockPos> en : recipeIndex.entrySet()) {
            ItemStack candidate = en.getKey().toStack(1);
            if (!candidate.isEmpty() && ResourceIdentity.sameResource(candidate, like)) {
                return en.getValue();
            }
        }
        return null;
    }

    private NBTTagCompound writeChain(PlannedChain chain) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("id", chain.id);
        tag.setLong("terminal", chain.terminalPos.toLong());
        tag.setTag("root", writeCraftNode(chain.root));
        return tag;
    }

    private NBTTagCompound writeCraftNode(PlannedCraft node) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong("endpoint", node.endpointPos.toLong());
        tag.setLong("outputDest", node.outputDestPos.toLong());
        tag.setInteger("outputSide", node.outputDestSide);
        tag.setInteger("patternSlot", node.patternSlot);
        tag.setTag("result", node.resultLike.writeToNBT(new NBTTagCompound()));
        tag.setInteger("requested", node.requestedItems);
        tag.setInteger("perCraft", node.perCraft);
        tag.setInteger("crafts", node.crafts);
        tag.setInteger("expected", node.expectedItems);
        tag.setInteger("batchId", node.batchId);
        tag.setInteger("submittedCrafts", node.submittedCrafts);
        tag.setInteger("completedItems", node.completedItems);
        tag.setInteger("activeBatchItems", node.activeBatchItems);
        tag.setBoolean("done", node.done);
        tag.setBoolean("failed", node.failed);

        NBTTagList raw = new NBTTagList();
        for (Map.Entry<ItemKey, Integer> e : node.rawInputs.entrySet()) {
            if (e.getKey() == null || e.getKey() == ItemKey.EMPTY) continue;
            int count = Math.max(0, e.getValue());
            if (count <= 0) continue;
            NBTTagCompound in = new NBTTagCompound();
            in.setTag("stack", e.getKey().toStack(1).writeToNBT(new NBTTagCompound()));
            in.setInteger("count", count);
            raw.appendTag(in);
        }
        tag.setTag("rawInputs", raw);

        NBTTagList direct = new NBTTagList();
        for (Map.Entry<ItemKey, Integer> e : node.directInputs.entrySet()) {
            if (e.getKey() == null || e.getKey() == ItemKey.EMPTY) continue;
            int count = Math.max(0, e.getValue());
            if (count <= 0) continue;
            NBTTagCompound in = new NBTTagCompound();
            in.setTag("stack", e.getKey().toStack(1).writeToNBT(new NBTTagCompound()));
            in.setInteger("count", count);
            direct.appendTag(in);
        }
        tag.setTag("directInputs", direct);

        NBTTagList children = new NBTTagList();
        for (PlannedCraft child : node.children) {
            children.appendTag(writeCraftNode(child));
        }
        tag.setTag("children", children);
        return tag;
    }

    @Nullable
    private PlannedChain readChain(NBTTagCompound tag) {
        if (!tag.hasKey("terminal") || !tag.hasKey("root")) return null;
        PlannedCraft root = readCraftNode(tag.getCompoundTag("root"));
        if (root == null) return null;
        return new PlannedChain(Math.max(1, tag.getInteger("id")), BlockPos.fromLong(tag.getLong("terminal")), root);
    }

    @Nullable
    private PlannedCraft readCraftNode(NBTTagCompound tag) {
        if (!tag.hasKey("endpoint") || !tag.hasKey("outputDest") || !tag.hasKey("result")) return null;
        ItemStack result = new ItemStack(tag.getCompoundTag("result"));
        if (result.isEmpty()) return null;

        PlannedCraft node = new PlannedCraft(
                BlockPos.fromLong(tag.getLong("endpoint")),
                BlockPos.fromLong(tag.getLong("outputDest")),
                tag.getInteger("outputSide"),
                tag.hasKey("patternSlot") ? tag.getInteger("patternSlot") : -1,
                result,
                Math.max(1, tag.getInteger("requested")),
                Math.max(1, tag.hasKey("perCraft") ? tag.getInteger("perCraft") : tag.getInteger("expected") / Math.max(1, tag.getInteger("crafts"))),
                Math.max(1, tag.getInteger("crafts")),
                Math.max(1, tag.getInteger("expected"))
        );
        node.batchId = Math.max(0, tag.getInteger("batchId"));
        node.submittedCrafts = Math.max(0, tag.getInteger("submittedCrafts"));
        node.completedItems = Math.max(0, tag.getInteger("completedItems"));
        node.activeBatchItems = Math.max(0, tag.getInteger("activeBatchItems"));
        node.done = tag.getBoolean("done");
        node.failed = tag.getBoolean("failed");

        if (tag.hasKey("rawInputs", 9)) {
            NBTTagList raw = tag.getTagList("rawInputs", 10);
            for (int i = 0; i < raw.tagCount(); i++) {
                NBTTagCompound in = raw.getCompoundTagAt(i);
                if (!in.hasKey("stack")) continue;
                ItemStack stack = new ItemStack(in.getCompoundTag("stack"));
                if (stack.isEmpty()) continue;
                ItemKey key = ItemKey.of(stack);
                if (key == null || key == ItemKey.EMPTY) continue;
                int count = Math.max(0, in.getInteger("count"));
                if (count > 0) node.rawInputs.merge(key, count, Integer::sum);
            }
        }
        if (tag.hasKey("directInputs", 9)) {
            NBTTagList direct = tag.getTagList("directInputs", 10);
            for (int i = 0; i < direct.tagCount(); i++) {
                NBTTagCompound in = direct.getCompoundTagAt(i);
                if (!in.hasKey("stack")) continue;
                ItemStack stack = new ItemStack(in.getCompoundTag("stack"));
                if (stack.isEmpty()) continue;
                ItemKey key = ItemKey.of(stack);
                if (key == null || key == ItemKey.EMPTY) continue;
                int count = Math.max(0, in.getInteger("count"));
                if (count > 0) node.directInputs.merge(key, count, Integer::sum);
            }
        } else {
            node.directInputs.putAll(node.rawInputs);
        }

        if (tag.hasKey("children", 9)) {
            NBTTagList children = tag.getTagList("children", 10);
            for (int i = 0; i < children.tagCount(); i++) {
                PlannedCraft child = readCraftNode(children.getCompoundTagAt(i));
                if (child != null) node.children.add(child);
            }
        }
        return node;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        if (managerPos != null) compound.setLong(TAG_MANAGER, managerPos.toLong());
        compound.setLong(TAG_ANIM_SEED, animSeed);
        compound.setInteger(TAG_NEXT_CHAIN_ID, nextChainId);
        NBTTagList chains = new NBTTagList();
        for (PlannedChain chain : plannedChains) {
            if (chain != null && chain.root != null && !chain.root.done) chains.appendTag(writeChain(chain));
        }
        compound.setTag(TAG_PLANNED_CHAINS, chains);
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        managerPos = compound.hasKey(TAG_MANAGER) ? BlockPos.fromLong(compound.getLong(TAG_MANAGER)) : null;
        if (compound.hasKey(TAG_ANIM_SEED)) animSeed = compound.getLong(TAG_ANIM_SEED);
        nextChainId = Math.max(1, compound.getInteger(TAG_NEXT_CHAIN_ID));
        plannedChains.clear();
        if (compound.hasKey(TAG_PLANNED_CHAINS, 9)) {
            NBTTagList chains = compound.getTagList(TAG_PLANNED_CHAINS, 10);
            for (int i = 0; i < chains.tagCount(); i++) {
                PlannedChain chain = readChain(chains.getCompoundTagAt(i));
                if (chain != null && chain.root != null && !chain.root.done) {
                    plannedChains.add(chain);
                    nextChainId = Math.max(nextChainId, chain.id + 1);
                }
            }
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (world != null && !world.isRemote && animSeed == 0L) {
            animSeed = world.rand.nextLong() ^ getPos().toLong();
        }
    }
}
