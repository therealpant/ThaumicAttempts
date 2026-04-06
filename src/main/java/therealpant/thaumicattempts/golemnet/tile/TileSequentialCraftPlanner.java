package therealpant.thaumicattempts.golemnet.tile;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import therealpant.thaumicattempts.golemnet.logistics.CreationOutputMode;
import therealpant.thaumicattempts.golemnet.logistics.NetworkOrder;
import therealpant.thaumicattempts.golemnet.logistics.OrderSourceType;
import therealpant.thaumicattempts.golemnet.logistics.OrderStatus;
import therealpant.thaumicattempts.golemnet.logistics.RecipeNode;
import therealpant.thaumicattempts.util.ItemKey;

import javax.annotation.Nullable;
import java.util.*;

public class TileSequentialCraftPlanner extends TileEntity implements ITickable {
    private static final Logger LOG = LogManager.getLogger("ThaumicAttempts/SequentialPlanner");

    public enum PlannerStatus {
        IDLE,
        ANALYZING,
        SUBMITTING_DEPENDENCY,
        WAITING_DEPENDENCY,
        SUBMITTING_FINAL,
        WAITING_FINAL,
        DONE,
        FAILED
    }

    private static final String TAG_MANAGER = "Manager";
    private static final String TAG_ACTIVE = "Active";
    private static final String TAG_STATUS = "PlannerStatus";
    private static final int RECIPE_INDEX_REFRESH_TICKS = 100;
    private static final int MAX_PLAN_DEPTH = 8;

    private static final class CraftChainNode {
        final ItemKey key;
        final int amount;
        final BlockPos sourcePos;
        @Nullable final BlockPos returnDestination;
        final CreationOutputMode outputMode;
        @Nullable UUID orderId;
        PlannerStatus nodeStatus = PlannerStatus.IDLE;

        private CraftChainNode(ItemKey key, int amount, BlockPos sourcePos, @Nullable BlockPos returnDestination, CreationOutputMode outputMode) {
            this.key = key;
            this.amount = Math.max(1, amount);
            this.sourcePos = sourcePos == null ? BlockPos.ORIGIN : sourcePos.toImmutable();
            this.returnDestination = returnDestination == null ? null : returnDestination.toImmutable();
            this.outputMode = outputMode;
        }
    }

    @Nullable
    private BlockPos managerPos;
    private boolean active = false;
    private PlannerStatus status = PlannerStatus.IDLE;

    private int lastRecipeRefreshTick = -9999;
    private final List<CraftChainNode> activeChain = new ArrayList<CraftChainNode>();
    private int currentNode = -1;

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
            this.activeChain.clear();
            this.currentNode = -1;
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
            return;
        }

        final int now = manager.getServerTickCounter();
        if ((now - lastRecipeRefreshTick) >= RECIPE_INDEX_REFRESH_TICKS) {
            manager.refreshRecipeIndexFromPlanner();
            lastRecipeRefreshTick = now;
        }

        tickActiveChain(manager);
        if (status == PlannerStatus.DONE) {
            status = PlannerStatus.IDLE;
        }
    }

    private void tickActiveChain(TileMirrorManager manager) {
        if (activeChain.isEmpty() || currentNode < 0 || currentNode >= activeChain.size()) {
            return;
        }

        CraftChainNode node = activeChain.get(currentNode);
        if (node.orderId == null) {
            status = node.outputMode == CreationOutputMode.RETURN_TO_REQUESTER ? PlannerStatus.SUBMITTING_FINAL : PlannerStatus.SUBMITTING_DEPENDENCY;
            UUID id = manager.submitCreationOrder(
                    node.key,
                    node.amount,
                    OrderSourceType.PLANNER,
                    node.sourcePos,
                    node.returnDestination,
                    NetworkOrder.RequestIntent.CRAFT_ONLY,
                    node.outputMode
            );
            if (id == null) {
                status = PlannerStatus.FAILED;
                return;
            }
            node.orderId = id;
            node.nodeStatus = node.outputMode == CreationOutputMode.RETURN_TO_REQUESTER ? PlannerStatus.WAITING_FINAL : PlannerStatus.WAITING_DEPENDENCY;
            status = node.nodeStatus;
            markDirty();
            return;
        }

        OrderStatus orderStatus = manager.getOrderStatus(node.orderId);
        if (orderStatus == null) {
            status = PlannerStatus.FAILED;
            return;
        }

        if (orderStatus == OrderStatus.DONE) {
            if (currentNode + 1 < activeChain.size()) {
                currentNode++;
                status = PlannerStatus.SUBMITTING_DEPENDENCY;
            } else {
                status = PlannerStatus.DONE;
                activeChain.clear();
                currentNode = -1;
            }
            markDirty();
            return;
        }

        if (orderStatus == OrderStatus.FAILED || orderStatus == OrderStatus.CANCELED) {
            status = PlannerStatus.FAILED;
        }
    }

    @Nullable
    private TileMirrorManager getManager() {
        if (world == null || managerPos == null) return null;
        TileEntity te = world.getTileEntity(managerPos);
        return te instanceof TileMirrorManager ? (TileMirrorManager) te : null;
    }

    @Nullable
    public UUID planAndSubmitRootDemand(TileMirrorManager manager,
                                        ItemKey key,
                                        int amount,
                                        BlockPos sourcePos,
                                        @Nullable BlockPos returnDestination,
                                        NetworkOrder.RequestIntent intent) {
        if (manager == null || key == null || key == ItemKey.EMPTY || amount <= 0) return null;

        manager.refreshRecipeIndexFromPlanner();
        status = PlannerStatus.ANALYZING;

        LinkedHashMap<ItemKey, Integer> workingStock =
                new LinkedHashMap<ItemKey, Integer>(manager.getReachableCatalog());

        LinkedHashMap<ItemKey, Integer> steps = new LinkedHashMap<ItemKey, Integer>();
        if (!collectStandardCraftSteps(manager, workingStock, steps, key, amount, 0)) {
            status = PlannerStatus.FAILED;
            return null;
        }

        int finalPlannedAmount = Math.max(1, steps.getOrDefault(key, amount));
        steps.remove(key);

        UUID finalOrderId = manager.submitCreationOrder(
                key,
                finalPlannedAmount,
                OrderSourceType.PLANNER,
                sourcePos,
                returnDestination,
                intent,
                CreationOutputMode.RETURN_TO_REQUESTER
        );
        if (finalOrderId == null) {
            status = PlannerStatus.FAILED;
            return null;
        }

        activeChain.clear();
        currentNode = -1;
        for (Map.Entry<ItemKey, Integer> e : steps.entrySet()) {
            activeChain.add(new CraftChainNode(
                    e.getKey(),
                    e.getValue(),
                    this.pos,
                    null,
                    CreationOutputMode.LEAVE_IN_CRAFTER
            ));
        }

        CraftChainNode finalNode = new CraftChainNode(
                key,
                finalPlannedAmount,
                sourcePos,
                returnDestination,
                CreationOutputMode.RETURN_TO_REQUESTER
        );
        finalNode.orderId = finalOrderId;
        activeChain.add(finalNode);

        if (activeChain.isEmpty()) {
            status = PlannerStatus.FAILED;
            return null;
        }

        currentNode = 0;
        tickActiveChain(manager);

        return finalOrderId;
    }

    private static int consumeAvailable(Map<ItemKey, Integer> stock, ItemKey key, int amount) {
        int have = Math.max(0, stock.getOrDefault(key, 0));
        int consume = Math.min(have, Math.max(0, amount));
        if (consume > 0) {
            stock.put(key, have - consume);
        }
        return Math.max(0, amount - consume);
    }

    private boolean collectStandardCraftSteps(TileMirrorManager manager,
                                              Map<ItemKey, Integer> workingStock,
                                              LinkedHashMap<ItemKey, Integer> steps,
                                              ItemKey requested,
                                              int amount,
                                              int depth) {
        if (manager == null || requested == null || requested == ItemKey.EMPTY || amount <= 0) return false;
        if (depth > MAX_PLAN_DEPTH) return false;

        int remaining = consumeAvailable(workingStock, requested, amount);
        if (remaining <= 0) return true;

        manager.refreshRecipeIndexFromPlanner();
        RecipeNode recipe = manager.getPlannerRecipe(requested);
        if (recipe == null || recipe.source == null) return false;

        int perCycle = Math.max(1, recipe.outputPerCycle);
        int cycles = (remaining + perCycle - 1) / perCycle;
        int producedAmount = cycles * perCycle;

        for (Map.Entry<ItemKey, Integer> input : recipe.inputs.entrySet()) {
            ItemKey inputKey = input.getKey();
            int inputNeed = Math.max(1, input.getValue() * cycles);
            if (!collectStandardCraftSteps(manager, workingStock, steps, inputKey, inputNeed, depth + 1)) {
                return false;
            }
        }

        steps.merge(requested, remaining, Integer::sum);
        workingStock.merge(requested, producedAmount, Integer::sum);
        consumeAvailable(workingStock, requested, remaining);
        return true;
    }

    public boolean canPlanDemand(TileMirrorManager manager, ItemKey key, int amount) {
        if (manager == null || key == null || key == ItemKey.EMPTY || amount <= 0) {
            return false;
        }

        manager.refreshRecipeIndexFromPlanner();

        LinkedHashMap<ItemKey, Integer> workingStock =
                new LinkedHashMap<ItemKey, Integer>(manager.getReachableCatalog());

        return canPlanDemandRecursive(manager, workingStock, key, amount, 0);
    }

    private boolean canPlanDemandRecursive(TileMirrorManager manager,
                                           Map<ItemKey, Integer> workingStock,
                                           ItemKey requested,
                                           int amount,
                                           int depth) {
        if (manager == null || requested == null || requested == ItemKey.EMPTY || amount <= 0) return false;
        if (depth > MAX_PLAN_DEPTH) return false;

        int remaining = consumeAvailable(workingStock, requested, amount);
        if (remaining <= 0) return true;

        manager.refreshRecipeIndexFromPlanner();
        RecipeNode recipe = manager.getPlannerRecipe(requested);
        if (recipe == null || recipe.source == null) return false;

        int perCycle = Math.max(1, recipe.outputPerCycle);
        int cycles = (remaining + perCycle - 1) / perCycle;
        int producedAmount = cycles * perCycle;

        for (Map.Entry<ItemKey, Integer> input : recipe.inputs.entrySet()) {
            ItemKey inputKey = input.getKey();
            int inputNeed = Math.max(1, input.getValue() * cycles);
            if (!canPlanDemandRecursive(manager, workingStock, inputKey, inputNeed, depth + 1)) {
                return false;
            }
        }

        workingStock.merge(requested, producedAmount, Integer::sum);
        consumeAvailable(workingStock, requested, remaining);
        return true;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);

        if (managerPos != null) tag.setLong(TAG_MANAGER, managerPos.toLong());
        tag.setBoolean(TAG_ACTIVE, active);
        tag.setString(TAG_STATUS, status.name());

        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);

        managerPos = tag.hasKey(TAG_MANAGER, Constants.NBT.TAG_LONG) ? BlockPos.fromLong(tag.getLong(TAG_MANAGER)) : null;
        active = tag.getBoolean(TAG_ACTIVE);

        String st = tag.getString(TAG_STATUS).toUpperCase(Locale.ROOT);
        try {
            status = PlannerStatus.valueOf(st);
        } catch (IllegalArgumentException ignored) {
            status = PlannerStatus.IDLE;
        }
        activeChain.clear();
        currentNode = -1;
    }
}