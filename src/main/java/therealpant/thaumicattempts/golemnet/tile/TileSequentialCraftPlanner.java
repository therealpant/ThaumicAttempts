package therealpant.thaumicattempts.golemnet.tile;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import therealpant.thaumicattempts.golemnet.logistics.NetworkOrder;
import therealpant.thaumicattempts.golemnet.logistics.OrderSourceType;
import therealpant.thaumicattempts.golemnet.logistics.RecipeNode;
import therealpant.thaumicattempts.util.ItemKey;


import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

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
    private static final int RECIPE_INDEX_REFRESH_TICKS = 100;
    private static final int MAX_PLAN_DEPTH = 8;

    @Nullable
    private BlockPos managerPos;
    private boolean active = false;
    private PlannerStatus status = PlannerStatus.IDLE;

    private int lastRecipeRefreshTick = -9999;

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
            status = PlannerStatus.SCANNING;
            manager.refreshRecipeIndexFromPlanner();
            lastRecipeRefreshTick = now;
            LOG.debug("[Planner {}] refreshed logistics recipe index", pos);
        }

        status = PlannerStatus.PLANNING;
        if (!manager.isLogisticsHealthy()) {
            status = PlannerStatus.FAILED;
            LOG.warn("[Planner {}] logistics pipeline is not healthy", pos);
            return;
        }

        status = PlannerStatus.IDLE;
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

        LinkedHashMap<ItemKey, Integer> workingStock =
                new LinkedHashMap<ItemKey, Integer>(manager.getReachableCatalog());

        LinkedHashMap<ItemKey, Integer> steps = new LinkedHashMap<ItemKey, Integer>();
        if (!collectStandardCraftSteps(manager, workingStock, steps, key, amount, 0)) {
            LOG.warn("[Planner {}] failed to plan demand key={} amount={} reason=unbuildable-chain",
                    pos, key, amount);
            return null;
        }

        int finalPlannedAmount = Math.max(0, steps.getOrDefault(key, 0));
        steps.remove(key);

        if (steps.isEmpty() && finalPlannedAmount <= 0) {
            return manager.submitOrder(
                    key,
                    amount,
                    OrderSourceType.PLANNER,
                    sourcePos,
                    returnDestination,
                    intent
            );
        }

        UUID finalOrder = null;
        int intermediateSteps = 0;

        for (Map.Entry<ItemKey, Integer> e : steps.entrySet()) {
            ItemKey stepKey = e.getKey();
            int stepAmount = Math.max(1, e.getValue());

            UUID id = manager.submitOrder(
                    stepKey,
                    stepAmount,
                    OrderSourceType.PLANNER,
                    sourcePos,
                    manager.getPos(),
                    NetworkOrder.RequestIntent.CRAFT_ONLY
            );

            if (id == null) {
                LOG.warn("[Planner {}] failed to submit standard craft step key={} amount={}",
                        pos, stepKey, stepAmount);
                return null;
            }

            intermediateSteps++;
        }

        finalOrder = manager.submitOrder(
                key,
                Math.max(1, finalPlannedAmount > 0 ? finalPlannedAmount : amount),
                OrderSourceType.PLANNER,
                sourcePos,
                returnDestination,
                intent
        );

        if (finalOrder == null) {
            LOG.warn("[Planner {}] failed to submit final standard craft step key={} amount={}",
                    pos, key, amount);
            return null;
        }

        LOG.info("[Planner {}] standard craft-order chain submitted key={} amount={} intermediateSteps={} finalOrder={}",
                pos, key, amount, intermediateSteps, finalOrder);

        return finalOrder;
    }

    private boolean enqueueStandardCraftOrders(PlannerSubmitContext ctx,
                                               Map<ItemKey, Integer> workingStock,
                                               ItemKey requested,
                                               int amount,
                                               int depth) {
        if (depth > MAX_PLAN_DEPTH) {
            ctx.failureReason = "max-depth";
            return false;
        }
        if (requested == null || requested == ItemKey.EMPTY || amount <= 0) {
            ctx.failureReason = "invalid-demand";
            return false;
        }

        int remaining = consumeAvailable(workingStock, requested, amount);
        if (remaining <= 0) {
            return true;
        }

        ctx.manager.refreshRecipeIndexFromPlanner();

        RecipeNode recipe = ctx.manager.getPlannerRecipe(requested);
        if (recipe == null || recipe.source == null) {
            ctx.failureReason = "no-recipe:" + requested;
            return false;
        }

        int perCycle = Math.max(1, recipe.outputPerCycle);
        int cycles = (remaining + perCycle - 1) / perCycle;
        int producedAmount = cycles * perCycle;

        /*
         * Сначала deeper dependencies.
         */
        for (Map.Entry<ItemKey, Integer> input : recipe.inputs.entrySet()) {
            ItemKey inputKey = input.getKey();
            int inputNeed = Math.max(1, input.getValue() * cycles);

            if (!enqueueStandardCraftOrders(ctx, workingStock, inputKey, inputNeed, depth + 1)) {
                return false;
            }
        }

        ctx.plannedOrderSequence.merge(requested, remaining, Integer::sum);

        workingStock.merge(requested, producedAmount, Integer::sum);
        consumeAvailable(workingStock, requested, remaining);

        return true;
    }

    private static int consumeAvailable(Map<ItemKey, Integer> stock, ItemKey key, int amount) {
        int have = Math.max(0, stock.getOrDefault(key, 0));
        int consume = Math.min(have, Math.max(0, amount));
        if (consume > 0) {
            stock.put(key, have - consume);
        }
        return Math.max(0, amount - consume);
    }

    private static final class PlannerSubmitContext {
        private final TileMirrorManager manager;
        private final BlockPos sourcePos;
        @Nullable
        private final BlockPos returnDestination;
        private final LinkedHashMap<ItemKey, Integer> plannedOrderSequence = new LinkedHashMap<ItemKey, Integer>();
        private String failureReason = "";

        private PlannerSubmitContext(TileMirrorManager manager, BlockPos sourcePos, @Nullable BlockPos returnDestination) {
            this.manager = manager;
            this.sourcePos = sourcePos == null ? BlockPos.ORIGIN : sourcePos.toImmutable();
            this.returnDestination = returnDestination == null ? null : returnDestination.toImmutable();
        }
    }

    private static final class PlannedCraftStep {
        final ItemKey key;
        final int amount;
        final boolean finalStep;

        private PlannedCraftStep(ItemKey key, int amount, boolean finalStep) {
            this.key = key;
            this.amount = amount;
            this.finalStep = finalStep;
        }
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
    }
}