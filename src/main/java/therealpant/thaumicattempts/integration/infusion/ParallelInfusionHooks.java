package therealpant.thaumicattempts.integration.infusion;

import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import thaumcraft.api.ThaumcraftInvHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectSource;
import thaumcraft.common.lib.network.PacketHandler;
import thaumcraft.common.lib.network.fx.PacketFXInfusionSource;
import thaumcraft.common.tiles.crafting.TileInfusionMatrix;
import thaumcraft.common.tiles.crafting.TilePedestal;
import therealpant.thaumicattempts.init.TABlocks;
import therealpant.thaumicattempts.net.msg.S2C_ParallelEssentiaStreams;
import therealpant.thaumicattempts.world.block.BlockInfusionMatrixUpgrade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Server-side work-phase replacement used only when at least one upgrade is attached. */
public final class ParallelInfusionHooks {
    public static final float STABILITY_PENALTY_PER_UPGRADE = 0.15F;
    private static final int ITEM_ABSORPTION_CYCLES = 5;
    private static final Map<TileInfusionMatrix, ItemWorkState> ITEM_STATES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<TileInfusionMatrix, EssentiaWorkState> ESSENTIA_STATES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ParallelInfusionHooks() {}

    public static float adjustStabilityReplenish(TileInfusionMatrix matrix, float vanillaValue) {
        return vanillaValue - countAttachedUpgrades(matrix) * STABILITY_PENALTY_PER_UPGRADE;
    }

    /**
     * @return true when the vanilla essentia/item phase must be skipped for this cycle.
     */
    public static boolean processWorkPhase(TileInfusionMatrix matrix) {
        if (matrix == null || matrix.getWorld() == null || matrix.getWorld().isRemote) return false;
        if (!(matrix instanceof IParallelInfusionMatrixAccess)) return false;

        int upgrades = countAttachedUpgrades(matrix);
        if (upgrades <= 0) {
            ITEM_STATES.remove(matrix);
            ESSENTIA_STATES.remove(matrix);
            return false;
        }

        IParallelInfusionMatrixAccess access = (IParallelInfusionMatrixAccess) matrix;
        int streams = 1 + upgrades;
        AspectList remainingEssentia = access.ta$getRecipeEssentia();
        if (remainingEssentia != null && remainingEssentia.visSize() > 0) {
            ITEM_STATES.remove(matrix);
            processEssentia(matrix, access, remainingEssentia, streams);
            return true;
        }
        ESSENTIA_STATES.remove(matrix);

        ArrayList<ItemStack> ingredients = access.ta$getRecipeIngredients();
        if (ingredients == null || ingredients.isEmpty()) {
            ITEM_STATES.remove(matrix);
            return false;
        }
        return processItems(matrix, access, ingredients, streams);
    }

    public static int countAttachedUpgrades(TileInfusionMatrix matrix) {
        if (matrix == null || matrix.getWorld() == null || TABlocks.INFUSION_MATRIX_UPGRADE == null) return 0;
        int count = 0;
        for (EnumFacing direction : EnumFacing.VALUES) {
            BlockPos upgradePos = matrix.getPos().offset(direction);
            IBlockState state = matrix.getWorld().getBlockState(upgradePos);
            if (state.getBlock() != TABlocks.INFUSION_MATRIX_UPGRADE) continue;
            if (!state.getPropertyKeys().contains(BlockInfusionMatrixUpgrade.FACING)) continue;
            if (state.getValue(BlockInfusionMatrixUpgrade.FACING) == direction) count++;
        }
        return count;
    }

    private static void processEssentia(TileInfusionMatrix matrix,
                                        IParallelInfusionMatrixAccess access,
                                        AspectList remaining,
                                        int streams) {
        EssentiaWorkState state = ESSENTIA_STATES.get(matrix);
        if (state == null || state.recipeEssentia != remaining) {
            state = new EssentiaWorkState(remaining);
            ESSENTIA_STATES.put(matrix, state);
        }

        Iterator<Aspect> laneIterator = state.lanes.iterator();
        while (laneIterator.hasNext()) {
            Aspect aspect = laneIterator.next();
            if (aspect == null || remaining.getAmount(aspect) <= 0) laneIterator.remove();
        }

        Set<Aspect> assigned = new HashSet<>(state.lanes);
        Aspect[] aspects = remaining.getAspects();
        if (aspects != null) {
            for (Aspect aspect : aspects) {
                if (state.lanes.size() >= streams) break;
                if (aspect != null && remaining.getAmount(aspect) > 0 && assigned.add(aspect)) {
                    state.lanes.add(aspect);
                }
            }
        }

        boolean changed = false;
        boolean drained = false;
        boolean sourceListNeedsRefresh = false;
        int effectLength = Math.max(1, access.ta$getCountDelay());
        List<S2C_ParallelEssentiaStreams.Stream> visualStreams = new ArrayList<>();
        for (Aspect aspect : new ArrayList<>(state.lanes)) {
            int amount = remaining.getAmount(aspect);
            if (amount <= 0) continue;

            BlockPos source = drainEssentia(matrix, state, aspect);
            if (source != null) {
                remaining.reduce(aspect, 1);
                changed = true;
                drained = true;
                visualStreams.add(new S2C_ParallelEssentiaStreams.Stream(
                        source, aspect.getColor(), amount > 1 ? effectLength : 0));
                if (remaining.getAmount(aspect) <= 0) state.lanes.remove(aspect);
            } else {
                access.ta$setStability(Math.max(-100.0F, access.ta$getStability() - 0.25F));
                changed = true;
                sourceListNeedsRefresh = true;
            }
        }

        if (changed) {
            matrix.syncTile(false);
            matrix.markDirty();
        }
        S2C_ParallelEssentiaStreams.dispatch(matrix.getWorld(), matrix.getPos(), visualStreams);
        if (!drained && sourceListNeedsRefresh) {
            state.sources = null;
        }
        if (!drained) {
            access.ta$setCheckSurroundings(true);
        }
    }

    private static BlockPos drainEssentia(TileInfusionMatrix matrix,
                                           EssentiaWorkState state,
                                           Aspect aspect) {
        if (state.sources == null) state.sources = findEssentiaSources(matrix);
        for (BlockPos sourcePos : state.sources) {
            TileEntity tile = matrix.getWorld().getTileEntity(sourcePos);
            if (!(tile instanceof IAspectSource)) continue;
            IAspectSource source = (IAspectSource) tile;
            if (!source.isBlocked() && source.takeFromContainer(aspect, 1)) {
                return sourcePos;
            }
        }
        return null;
    }

    private static List<BlockPos> findEssentiaSources(TileInfusionMatrix matrix) {
        List<BlockPos> sources = new ArrayList<>();
        BlockPos origin = matrix.getPos();
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
                for (int y = -12; y < 12; y++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    BlockPos candidate = origin.add(x, y, z);
                    if (matrix.getWorld().getTileEntity(candidate) instanceof IAspectSource) {
                        sources.add(candidate.toImmutable());
                    }
                }
            }
        }
        sources.sort((left, right) -> Long.compare(
                distanceSquared(origin, left), distanceSquared(origin, right)));
        return sources;
    }

    private static long distanceSquared(BlockPos first, BlockPos second) {
        long x = first.getX() - second.getX();
        long y = first.getY() - second.getY();
        long z = first.getZ() - second.getZ();
        return x * x + y * y + z * z;
    }

    private static boolean processItems(TileInfusionMatrix matrix,
                                        IParallelInfusionMatrixAccess access,
                                        ArrayList<ItemStack> ingredients,
                                        int streams) {
        ItemWorkState state = ITEM_STATES.get(matrix);
        if (state == null || state.recipeIngredients != ingredients) {
            state = new ItemWorkState(ingredients);
            ITEM_STATES.put(matrix, state);
        }

        boolean changed = tickActiveLanes(matrix, ingredients, state);
        if (ingredients.isEmpty()) {
            ITEM_STATES.remove(matrix);
            if (changed) {
                matrix.markDirty();
                matrix.syncTile(false);
            }
            return false;
        }

        Set<ItemStack> reservedIngredients = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<BlockPos> reservedPedestals = new java.util.HashSet<>();
        for (ItemLane lane : state.lanes) {
            reservedIngredients.add(lane.ingredient);
            reservedPedestals.add(lane.pedestal);
        }

        while (state.lanes.size() < streams) {
            ItemLane lane = findLane(matrix, access.ta$getPedestals(), ingredients,
                    reservedIngredients, reservedPedestals);
            if (lane == null) break;
            state.lanes.add(lane);
            reservedIngredients.add(lane.ingredient);
            reservedPedestals.add(lane.pedestal);
            sendItemStream(matrix, lane.pedestal);
        }

        if (state.lanes.isEmpty()) {
            ITEM_STATES.remove(matrix);
            return false;
        }
        if (changed) {
            matrix.markDirty();
            matrix.syncTile(false);
        }
        return true;
    }

    private static boolean tickActiveLanes(TileInfusionMatrix matrix,
                                           ArrayList<ItemStack> ingredients,
                                           ItemWorkState state) {
        boolean changed = false;
        Iterator<ItemLane> iterator = state.lanes.iterator();
        while (iterator.hasNext()) {
            ItemLane lane = iterator.next();
            lane.remainingCycles--;
            if (lane.remainingCycles > 0) continue;

            TileEntity tile = matrix.getWorld().getTileEntity(lane.pedestal);
            if (tile instanceof TilePedestal && ingredients.contains(lane.ingredient)) {
                TilePedestal pedestal = (TilePedestal) tile;
                ItemStack current = pedestal.getStackInSlot(0);
                if (!current.isEmpty()
                        && ThaumcraftInvHelper.areItemStacksEqualForCrafting(current, lane.ingredient)) {
                    ItemStack container = current.getItem().getContainerItem(current);
                    pedestal.setInventorySlotContents(0,
                            container == null || container.isEmpty() ? ItemStack.EMPTY : container.copy());
                    pedestal.markDirty();
                    pedestal.syncTile(false);
                    ingredients.remove(lane.ingredient);
                    changed = true;
                }
            }
            iterator.remove();
        }
        return changed;
    }

    private static ItemLane findLane(TileInfusionMatrix matrix,
                                     List<BlockPos> pedestals,
                                     List<ItemStack> ingredients,
                                     Set<ItemStack> reservedIngredients,
                                     Set<BlockPos> reservedPedestals) {
        if (pedestals == null) return null;
        for (ItemStack ingredient : ingredients) {
            if (ingredient == null || reservedIngredients.contains(ingredient)) continue;
            for (BlockPos pedestalPos : pedestals) {
                if (pedestalPos == null || reservedPedestals.contains(pedestalPos)) continue;
                TileEntity tile = matrix.getWorld().getTileEntity(pedestalPos);
                if (!(tile instanceof TilePedestal)) continue;
                ItemStack current = ((TilePedestal) tile).getStackInSlot(0);
                if (!current.isEmpty()
                        && ThaumcraftInvHelper.areItemStacksEqualForCrafting(current, ingredient)) {
                    return new ItemLane(pedestalPos.toImmutable(), ingredient);
                }
            }
        }
        return null;
    }

    private static void sendItemStream(TileInfusionMatrix matrix, BlockPos pedestal) {
        PacketHandler.INSTANCE.sendToAllAround(
                new PacketFXInfusionSource(matrix.getPos(), pedestal, 0),
                new NetworkRegistry.TargetPoint(
                        matrix.getWorld().provider.getDimension(),
                        matrix.getPos().getX(), matrix.getPos().getY(), matrix.getPos().getZ(), 32.0D));
    }

    private static final class ItemWorkState {
        private final ArrayList<ItemStack> recipeIngredients;
        private final List<ItemLane> lanes = new ArrayList<>();

        private ItemWorkState(ArrayList<ItemStack> recipeIngredients) {
            this.recipeIngredients = recipeIngredients;
        }
    }

    private static final class EssentiaWorkState {
        private final AspectList recipeEssentia;
        private final List<Aspect> lanes = new ArrayList<>();
        private List<BlockPos> sources;

        private EssentiaWorkState(AspectList recipeEssentia) {
            this.recipeEssentia = recipeEssentia;
        }
    }

    private static final class ItemLane {
        private final BlockPos pedestal;
        private final ItemStack ingredient;
        private int remainingCycles = ITEM_ABSORPTION_CYCLES;

        private ItemLane(BlockPos pedestal, ItemStack ingredient) {
            this.pedestal = pedestal;
            this.ingredient = ingredient;
        }
    }
}
