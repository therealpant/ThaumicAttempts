// src/main/java/therealpant/thaumicattempts/world/anomaly/EntityFluxAnomalyBurst.java
package therealpant.thaumicattempts.world;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.blocks.world.taint.TaintHelper;
import thaumcraft.common.entities.monster.tainted.EntityTaintSeed;
import therealpant.thaumicattempts.api.FluxAnomalyResource;
import therealpant.thaumicattempts.api.FluxAnomalySettings;
import therealpant.thaumicattempts.api.FluxAnomalySpawnMethod;
import therealpant.thaumicattempts.api.FluxAnomalyTier;
import therealpant.thaumicattempts.config.TAConfig;
import therealpant.thaumicattempts.util.WorldSpawnUtil;
import therealpant.thaumicattempts.world.block.BlockAnomalyStone;
import therealpant.thaumicattempts.world.block.BlockRiftBush;
import therealpant.thaumicattempts.world.block.BlockRiftGeod;
import therealpant.thaumicattempts.world.block.FluxResourceHelper;
import therealpant.thaumicattempts.world.data.TAInfectedChunksData;

import java.util.HashMap;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class EntityFluxAnomalyBurst extends Entity {

    private static final Logger LOG = LogManager.getLogger("ThaumicAttempts|FluxAnomaly");

    private static final int MIN_CORRUPTION_RADIUS = 10;
    private static final int MAX_CORRUPTION_RADIUS = 12;
    private static final int RESOURCE_RETRY_TICKS = 20;
    private static final int RESOURCE_SPREAD_ATTEMPTS = 3;
    private static final int RESOURCE_SPAWN_INTERVAL = 40;
    private static final int TAINT_KICKSTART_ATTEMPTS = 8;
    private static final int TAINT_KICKSTART_RADIUS = 2;
    private static final int BUSH_PLACEMENT_ATTEMPTS = 400;
    private static final int STONE_PLACEMENT_ATTEMPTS = 600;
    private static final int GEOD_PLACEMENT_ATTEMPTS = 400;
    private static final int SEED_MISSING_GRACE_TICKS = 40;
    private static final float ANOMALOUS_STONE_CONVERSION_CHANCE = 0.2f;
    private static final Set<Block> CONVERTIBLE_STONE = new HashSet<>(Arrays.asList(
            Blocks.STONE,
            Blocks.COBBLESTONE,
            Blocks.STONEBRICK,
            Blocks.NETHERRACK,
            Blocks.END_STONE
    ));

    private UUID anomalyId = UUID.randomUUID();

    private BlockPos center = BlockPos.ORIGIN;

    private int radiusBlocks = MAX_CORRUPTION_RADIUS;      // 2–3 чанка ковром
    private int totalSpreads = 25500;   // сколько spreadFibres всего
    private int budgetPerTick = 900;   // сколько spreadFibres за тик
    private FluxAnomalySpawnMethod spawnMethod = FluxAnomalySpawnMethod.API;
    private ResourceLocation resourceBlockId = null;
    private Block resourceBlock = null;
    private int maxResources = 0;
    private int resourceCap = 0;
    private int resourceRadius = 0;
    private int activeResourceCount = 0;
    private boolean initialResourcesPlaced = false;
    private long lastResourceAttemptTick = 0L;

    private boolean awakened = false;
    private int remainingSpreads = 0;
    private long sourceChunkKey = 0L;
    private FluxAnomalyTier tier = FluxAnomalyTier.SURFACE;
    private long lastGrowthTick = 0;
    private boolean finished = false;
    private boolean infectionFinished = false;
    private AnomalyPhase phase = AnomalyPhase.SEED;

    private UUID seedEntityId = null;
    private BlockPos seedPos = null;
    private long lastSeedSeenTick = 0L;
    private boolean seedKilledByOvergrowth = false;
    private FinishReason finishReason = null;

    public EntityFluxAnomalyBurst(World worldIn) {
        super(worldIn);
        this.noClip = true;
        this.setSize(0.1f, 0.1f);
    }

    public EntityFluxAnomalyBurst(World worldIn, BlockPos center, int radiusBlocks, int totalSpreads, int budgetPerTick) {
        this(worldIn);
        this.center = center.toImmutable();
        this.radiusBlocks = MathHelper.clamp(radiusBlocks, MIN_CORRUPTION_RADIUS, MAX_CORRUPTION_RADIUS);
        this.totalSpreads = Math.max(0, totalSpreads);
        this.budgetPerTick = Math.max(1, budgetPerTick);
        this.remainingSpreads = this.totalSpreads;
        this.resourceRadius = Math.max(4, this.radiusBlocks / 2);

        this.setPosition(center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5);
    }

    public EntityFluxAnomalyBurst(World worldIn, BlockPos center, FluxAnomalySettings settings) {
        this(worldIn,
                center,
                settings != null ? settings.getRadiusBlocks() : MAX_CORRUPTION_RADIUS,
                settings != null ? settings.getTotalSpreads() : 16500,
                settings != null ? settings.getBudgetPerTick() : 220);

        if (settings != null) {
            spawnMethod = settings.getSpawnMethod();
            tier = settings.getTier();
            sourceChunkKey = settings.getSourceChunkKey();
            FluxAnomalyResource resource = settings.getResource();
            if (resource != null) {
                applyResource(resource);
            }
        }
    }

    void setHostingChunkKey(long chunkKey) {
        this.sourceChunkKey = chunkKey;
    }


    private void applyResource(FluxAnomalyResource resource) {
        if (resource.isPresent()) {
            ResourceLocation key = resource.getBlock().getRegistryName();
            if (key != null) {
                resourceBlockId = key;
                resourceBlock = resource.getBlock();
                maxResources = resource.getBlockCount();
                resourceCap = FluxResourceHelper.getOvergrowthCap(resourceBlock);
                resourceRadius = Math.max(4, radiusBlocks / 2);
                initialResourcesPlaced = false;
            } else {
                resourceBlockId = null;
                resourceBlock = null;
                maxResources = 0;
                resourceCap = 0;
                resourceRadius = Math.max(4, radiusBlocks / 2);
            }
        } else {
            resourceBlockId = null;
            maxResources = 0;
            resourceCap = 0;
            resourceBlock = null;
            resourceRadius = Math.max(4, radiusBlocks / 2);
        }
    }

    @Override
    protected void entityInit() {}

    @Override
    public void onUpdate() {
        super.onUpdate();

        if (world.isRemote) return;

        if (center == BlockPos.ORIGIN) {
            center = new BlockPos(this.posX, this.posY, this.posZ);
        }

        if (seedPos != null && !seedPos.equals(center)) {
            center = seedPos;
        }

        if (!awakened) {
            awakened = true;
            remainingSpreads = Math.max(0, totalSpreads);

        }

        switch (phase) {
            case SEED: {
                ensureSeedPosition();
                if (seedPos == null) {
                    LOG.warn("[FluxAnomaly] Seed spawn failed: no valid position near {}", center);
                    finishAnomaly(FinishReason.SPAWN_POS_INVALID, false);
                    return;
                }
                if (seedEntityId == null) {
                    if (!spawnTaintSeed()) {
                        return;
                    }
                }
                transitionToPhase(AnomalyPhase.INFECT);
                break;
            }
            case INFECT: {
                if (!isSeedAlive()) {
                    finishAnomaly(FinishReason.SEED_DEAD, false);
                    return;
                }
                if (seedKilledByOvergrowth) {
                    killSeedIfPresent();
                    finishAnomaly(FinishReason.SEED_KILLED_BY_OVERGROWTH, false);
                    return;
                }
                spreadInfection();
                if (remainingSpreads <= 0) {
                    remainingSpreads = 0;
                    infectionFinished = true;
                    transitionToPhase(AnomalyPhase.PLACE_RESOURCES);
                }
                break;
            }
            case PLACE_RESOURCES: {
                if (!isSeedAlive()) {
                    finishAnomaly(FinishReason.SEED_DEAD, false);
                    return;
                }
                if (!initialResourcesPlaced
                        && world.getTotalWorldTime() - lastResourceAttemptTick >= RESOURCE_RETRY_TICKS) {
                    if (placeInitialResources()) {
                        initialResourcesPlaced = true;
                        transitionToPhase(AnomalyPhase.ACTIVE);
                    }
                } else if (initialResourcesPlaced) {
                    transitionToPhase(AnomalyPhase.ACTIVE);
                }
                break;
            }
            case ACTIVE: {
                if (!isSeedAlive()) {
                    finishAnomaly(FinishReason.SEED_DEAD, false);
                    return;
                }
                if (seedKilledByOvergrowth) {
                    killSeedIfPresent();
                    finishAnomaly(FinishReason.SEED_KILLED_BY_OVERGROWTH, false);
                    return;
                }
                tickResourceSpawner();
                break;
            }
            case FINISH: {
                finishAnomaly(FinishReason.SEED_DEAD, false);
                return;
            }
            default:
                break;
        }


        if ((world.getTotalWorldTime() % 40L) == 0L) {
            LOG.info("[FluxAnomaly] TICK id={} phase={} remaining={} seedPos={} tier={} resourcesPlaced={}",
                    anomalyId,
                    phase,
                    remainingSpreads,
                    seedPos,
                    tier,
                    initialResourcesPlaced);
        }
    }

    private boolean placeInitialResources() {
        lastResourceAttemptTick = world.getTotalWorldTime();

        if (resourceBlockId == null || resourceBlock == null || maxResources <= 0) {
            initialResourcesPlaced = true;
            TAInfectedChunksData.get(world).setLastResourcePlacement("RESOURCE_SKIPPED", 0, 0);
            LOG.info("[FluxAnomaly] Resource placement skipped (no resource block) seedPos={}", seedPos);
            return true;
        }

        Block block = ForgeRegistries.BLOCKS.getValue(resourceBlockId);
        if (block == null || block == Blocks.AIR) {
            LOG.warn("[FluxAnomaly] Resource block {} missing, skipping placement", resourceBlockId);
            TAInfectedChunksData.get(world).setLastResourcePlacement("RESOURCE_BLOCK_MISSING", 0, 0);
            initialResourcesPlaced = true;
            return true;
        }

        BlockPos anchor = getAnchor();
        int targetCount = getInitialResourceTargetCount(block);
        int placed = countExistingResources(block, anchor);
        int attempts = getInitialResourceAttempts(block);
        final Random rnd = world.rand;
        int successes = 0;
        int usedAttempts = 0;
        Map<String, Integer> failureReasons = new HashMap<>();

        for (int i = 0; i < attempts && placed < targetCount; i++) {
            PlacementAttempt attempt = tryPlaceInitialResource(block, rnd);
            usedAttempts++;
            if (attempt.success) {
                placed++;
                successes++;
            } else {
                failureReasons.merge(attempt.reason, 1, Integer::sum);
            }
        }

        TAInfectedChunksData data = TAInfectedChunksData.get(world);
        data.setLastResourcePlacement(
                placed >= targetCount ? "PLACEMENT_OK" : "PLACEMENT_INSUFFICIENT",
                usedAttempts,
                successes
        );
        boolean completed = placed >= targetCount;
        if (completed) {
            initialResourcesPlaced = true;
        }

        LOG.info("[FluxAnomaly] Resource placement tier={} seedPos={} placed={}/{} attempts={} success={} failures={}",
                tier,
                anchor,
                placed,
                targetCount,
                usedAttempts,
                successes,
                failureReasons);
        return completed;
    }

    private void tickResourceSpawner() {
        if (resourceBlock == null || maxResources <= 0 || finished) return;
        long now = world.getTotalWorldTime();
        if (now - lastGrowthTick < RESOURCE_SPAWN_INTERVAL) return;
        lastGrowthTick = now;

        activeResourceCount = FluxResourceHelper.countBlocks(world, getAnchor(), resourceBlock, resourceRadius);
        if (activeResourceCount < maxResources) {
            tryPlaceResource(resourceBlock, world.rand);
        }
    }

    private int getInitialResourceTargetCount(Block block) {
        if (block instanceof BlockRiftBush) {
            return 3;
        }
        if (block instanceof BlockRiftGeod) {
            int desired = maxResources > 0 ? maxResources : 1;
            return MathHelper.clamp(desired, 1, 2);
        }
        if (block instanceof BlockAnomalyStone) {
            int desired = maxResources > 0 ? maxResources : 4;
            return Math.max(2, desired);
        }
        return maxResources;
    }

    private int getInitialResourceAttempts(Block block) {
        if (block instanceof BlockRiftBush) return BUSH_PLACEMENT_ATTEMPTS;
        if (block instanceof BlockAnomalyStone) return STONE_PLACEMENT_ATTEMPTS;
        if (block instanceof BlockRiftGeod) return GEOD_PLACEMENT_ATTEMPTS;
        return Math.max(maxResources * 8, maxResources);
    }

    private int countExistingResources(Block block, BlockPos anchor) {
        if (block instanceof BlockRiftBush) {
            int count = 0;
            for (BlockPos pos : BlockPos.getAllInBoxMutable(
                    anchor.add(-resourceRadius, -resourceRadius, -resourceRadius),
                    anchor.add(resourceRadius, resourceRadius, resourceRadius))) {
                IBlockState state = world.getBlockState(pos);
                if (state.getBlock() instanceof BlockRiftBush
                        && state.getValue(BlockRiftBush.HALF) == BlockRiftBush.BlockHalf.LOWER) {
                    count++;
                }
            }
            return count;
        }
        return FluxResourceHelper.countBlocks(world, anchor, block, resourceRadius);
    }

    private PlacementAttempt tryPlaceInitialResource(Block block, Random rnd) {
        if (block instanceof BlockRiftBush) {
            return placeInitialBush((BlockRiftBush) block, rnd);
        }
        if (block instanceof BlockAnomalyStone) {
            return placeInitialAnomalyStone((BlockAnomalyStone) block, rnd);
        }
        if (block instanceof BlockRiftGeod) {
            return placeInitialGeod((BlockRiftGeod) block, rnd);
        }
        return PlacementAttempt.fail("UNSUPPORTED_BLOCK");
    }

    private PlacementAttempt placeInitialBush(BlockRiftBush bush, Random rnd) {
        if (seedPos == null) return PlacementAttempt.fail("NO_SEED_POS");

        BlockPos candidate = randomPosInRadius(rnd, resourceRadius);
        if (!world.isBlockLoaded(candidate)) return PlacementAttempt.fail("CHUNK_NOT_LOADED");

        IBlockState soilState = world.getBlockState(candidate);
        if (soilState.getBlock() != BlocksTC.taintSoil) {
            return PlacementAttempt.fail("NO_TAINT_SOIL");
        }

        BlockPos lower = candidate.up();
        BlockPos upper = lower.up();
        if (upper.getY() >= world.getHeight()) return PlacementAttempt.fail("Y_RANGE");

        IBlockState lowerState = world.getBlockState(lower);
        if (!world.isAirBlock(lower) && !isTaintDecoration(lowerState)) {
            return PlacementAttempt.fail("BLOCKED_LOWER");
        }
        IBlockState upperState = world.getBlockState(upper);
        if (!world.isAirBlock(upper) && !isTaintDecoration(upperState)) {
            return PlacementAttempt.fail("BLOCKED_UPPER");
        }

        clearTaintDecoration(lower, lowerState);
        clearTaintDecoration(upper, upperState);

        IBlockState lowerPlacement = bush.getDefaultState().withProperty(BlockRiftBush.HALF, BlockRiftBush.BlockHalf.LOWER);
        IBlockState upperPlacement = bush.getDefaultState().withProperty(BlockRiftBush.HALF, BlockRiftBush.BlockHalf.UPPER);
        world.setBlockState(lower, lowerPlacement, 3);
        world.setBlockState(upper, upperPlacement, 3);
        FluxResourceHelper.linkBlockToAnomaly(world, lower, anomalyId, seedPos);
        FluxResourceHelper.linkBlockToAnomaly(world, upper, anomalyId, seedPos);
        return PlacementAttempt.success();
    }

    private PlacementAttempt placeInitialAnomalyStone(BlockAnomalyStone stone, Random rnd) {
        if (seedPos == null) return PlacementAttempt.fail("NO_SEED_POS");

        BlockPos candidate = randomPosInRadius(rnd, resourceRadius);
        if (!world.isBlockLoaded(candidate)) return PlacementAttempt.fail("CHUNK_NOT_LOADED");

        IBlockState state = world.getBlockState(candidate);
        if (state.getBlock() != BlocksTC.taintRock) {
            return PlacementAttempt.fail("NO_TAINT_ROCK");
        }

        EnumFacing openFace = findOpenFace(candidate, false);
        if (openFace == null) {
            return PlacementAttempt.fail("NO_OPEN_FACE");
        }

        BlockPos neighbor = candidate.offset(openFace);
        IBlockState neighborState = world.getBlockState(neighbor);
        clearTaintDecoration(neighbor, neighborState);

        world.setBlockState(candidate, stone.getDefaultState(), 3);
        FluxResourceHelper.linkBlockToAnomaly(world, candidate, anomalyId, seedPos);
        return PlacementAttempt.success();
    }

    private PlacementAttempt placeInitialGeod(BlockRiftGeod geod, Random rnd) {
        if (seedPos == null) return PlacementAttempt.fail("NO_SEED_POS");

        BlockPos candidate = randomPosInRadius(rnd, resourceRadius);
        if (!world.isBlockLoaded(candidate)) return PlacementAttempt.fail("CHUNK_NOT_LOADED");

        IBlockState state = world.getBlockState(candidate);
        if (state.getBlock() != BlocksTC.taintRock) {
            return PlacementAttempt.fail("NO_TAINT_ROCK");
        }

        EnumFacing openFace = findOpenFace(candidate, true);
        if (openFace == null) {
            return PlacementAttempt.fail("NO_OPEN_FACE");
        }

        BlockPos placePos = candidate.offset(openFace);
        IBlockState placeState = world.getBlockState(placePos);
        clearTaintDecoration(placePos, placeState);
        if (!world.isAirBlock(placePos)) {
            return PlacementAttempt.fail("PLACE_NOT_AIR");
        }

        world.setBlockState(placePos, geod.getDefaultState().withProperty(BlockRiftGeod.FACING, openFace), 3);
        FluxResourceHelper.linkBlockToAnomaly(world, placePos, anomalyId, seedPos);
        return PlacementAttempt.success();
    }

    private boolean isTaintDecoration(IBlockState state) {
        if (state == null) return false;
        Block block = state.getBlock();
        return block == BlocksTC.taintFibre || block == BlocksTC.taintFeature;
    }

    private void clearTaintDecoration(BlockPos pos, IBlockState state) {
        if (pos == null || state == null) return;
        if (isTaintDecoration(state)) {
            world.setBlockToAir(pos);
        }
    }

    private EnumFacing findOpenFace(BlockPos rockPos, boolean prioritizeUp) {
        EnumFacing[] order;
        if (prioritizeUp) {
            order = new EnumFacing[] {
                    EnumFacing.UP,
                    EnumFacing.NORTH,
                    EnumFacing.SOUTH,
                    EnumFacing.WEST,
                    EnumFacing.EAST,
                    EnumFacing.DOWN
            };
        } else {
            order = EnumFacing.values();
        }

        for (EnumFacing face : order) {
            BlockPos neighbor = rockPos.offset(face);
            IBlockState neighborState = world.getBlockState(neighbor);
            if (world.isAirBlock(neighbor) || isTaintDecoration(neighborState)) {
                return face;
            }
        }
        return null;
    }

    private PlacementAttempt tryPlaceResource(Block block, Random rnd) {

        if (block instanceof BlockRiftBush) {
            return placeBush((BlockRiftBush) block, rnd);
        }
        if (block instanceof BlockRiftGeod) {
            return placeGeod((BlockRiftGeod) block, rnd);
        }
        if (block instanceof BlockAnomalyStone) {
            return placeAnomalyStone((BlockAnomalyStone) block, rnd);
        }

        LOG.warn("[FluxAnomaly] Unsupported resource placement for block {}", block);
        return PlacementAttempt.fail("UNSUPPORTED_BLOCK");
    }

    private PlacementAttempt  placeBush(BlockRiftBush bush, Random rnd) {
        if (tier != FluxAnomalyTier.SURFACE) return PlacementAttempt.fail("NOT_SURFACE_TIER");
        BlockPos column = pickResourceColumn(rnd);
        BlockPos base = world.getTopSolidOrLiquidBlock(column);
        BlockPos lower = base.up();
        if (lower.getY() >= world.getHeight() - 1 || lower.getY() < 1) return PlacementAttempt.fail("Y_RANGE");
        if (!world.isBlockLoaded(lower)) return PlacementAttempt.fail("CHUNK_NOT_LOADED");
        if (!world.isAirBlock(lower) || !world.isAirBlock(lower.up())) return PlacementAttempt.fail("NO_AIR");
        IBlockState baseState = world.getBlockState(base);
        if (baseState.getMaterial().isLiquid() || baseState.getBlock() == Blocks.AIR) return PlacementAttempt.fail("INVALID_BASE");
        if (!bush.canPlaceBlockAt(world, lower)) return PlacementAttempt.fail("CANNOT_PLACE");

        IBlockState lowerState = bush.getDefaultState().withProperty(BlockRiftBush.HALF, BlockRiftBush.BlockHalf.LOWER);
        IBlockState upperState = bush.getDefaultState().withProperty(BlockRiftBush.HALF, BlockRiftBush.BlockHalf.UPPER);

        world.setBlockState(lower, lowerState, 3);
        world.setBlockState(lower.up(), upperState, 3);
        FluxResourceHelper.linkBlockToAnomaly(world, lower, anomalyId, seedPos);
        FluxResourceHelper.linkBlockToAnomaly(world, lower.up(), anomalyId, seedPos);
        return PlacementAttempt.success();
    }

    private PlacementAttempt placeAnomalyStone(BlockAnomalyStone stone, Random rnd) {
        if (tier != FluxAnomalyTier.SHALLOW) return PlacementAttempt.fail("NOT_SHALLOW_TIER");
        return trySpawnAnomalousResource(stone, rnd);
    }

    private PlacementAttempt placeGeod(BlockRiftGeod geod, Random rnd) {
        if (tier != FluxAnomalyTier.DEEP) return PlacementAttempt.fail("NOT_DEEP_TIER");
        final int minY = 1;
        final int maxY = 24;
        String lastFail = "NO_POSITION";
        for (int i = 0; i < 32; i++) {
            BlockPos target = randomNearbyPos(rnd, minY, maxY, resourceRadius);
            if (!world.isBlockLoaded(target)) {
                lastFail = "CHUNK_NOT_LOADED";
                continue;
            }
            if (!world.isAirBlock(target)) {
                lastFail = "NO_AIR";
                continue;
            }
            if (target.getY() > maxY) {
                lastFail = "Y_RANGE";
                continue;
            }

            EnumFacing facing = null;
            for (EnumFacing face : EnumFacing.values()) {
                BlockPos neighbor = target.offset(face.getOpposite());
                IBlockState neighborState = world.getBlockState(neighbor);
                if (neighborState.isSideSolid(world, neighbor, face)) {
                    facing = face;
                    break;
                }
            }
            if (facing == null) {
                lastFail = "NO_SOLID_NEIGHBOR";
                continue;
            }
            IBlockState state = geod.getDefaultState().withProperty(BlockRiftGeod.FACING, facing);
            if (!geod.canPlaceBlockAt(world, target)) {
                lastFail = "CANNOT_PLACE";
                continue;
            }
            if (!world.getBlockState(target).getMaterial().isReplaceable() && !world.isAirBlock(target)) {
                lastFail = "NOT_REPLACEABLE";
                continue;
            }
            if (world.canBlockSeeSky(target)) {
                lastFail = "SKY_EXPOSED";
                continue;
            }
            world.setBlockState(target, state, 3);
            FluxResourceHelper.linkBlockToAnomaly(world, target, anomalyId, seedPos);
            return  PlacementAttempt.success();
        }
        return PlacementAttempt.fail(lastFail);
    }

    private PlacementAttempt trySpawnAnomalousResource(BlockAnomalyStone stone, Random rnd) {
        BlockPos anchor = enforceTierVertical(getAnchor());


        String lastFail = "NO_POSITION";
        for (int i = 0; i < RESOURCE_SPREAD_ATTEMPTS; i++) {
            BlockPos target = randomResourcePos(rnd, 2);
            if (!world.isBlockLoaded(target)) {
                lastFail = "CHUNK_NOT_LOADED";
                continue;
            }

            PlacementAttempt attempt = attemptStoneConversion(stone, target, rnd);
            if (attempt.success) {
                return attempt;
            }
            lastFail = attempt.reason;
        }
        if (TAConfig.ENABLE_FLUX_ANOMALY_DEBUG_LOGS) {
            LOG.debug("[FluxAnomaly] Failed anomaly stone attempt near {} reason={}", anchor, lastFail);
        }
        return PlacementAttempt.fail(lastFail);
    }

    private PlacementAttempt attemptStoneConversion(BlockAnomalyStone stone, BlockPos target, Random rnd) {
        IBlockState targetState = world.getBlockState(target);
        if (targetState.getMaterial().isLiquid()) return PlacementAttempt.fail("LIQUID");
        if (targetState.getBlock() instanceof BlockAnomalyStone) return PlacementAttempt.fail("ALREADY_ANOMALY");
        if (targetState.getBlock().hasTileEntity(targetState) || world.getTileEntity(target) != null) {
            return PlacementAttempt.fail("HAS_TILE");
        }
        if (!isConvertibleStone(targetState)) return PlacementAttempt.fail("NOT_CONVERTIBLE");
        if (rnd.nextFloat() > ANOMALOUS_STONE_CONVERSION_CHANCE) return PlacementAttempt.fail("CHANCE");

        world.setBlockState(target, stone.getDefaultState(), 3);
        FluxResourceHelper.linkBlockToAnomaly(world, target, anomalyId, seedPos);
        if (TAConfig.ENABLE_FLUX_ANOMALY_DEBUG_LOGS) {
            LOG.debug("[FluxAnomaly] Converted {} -> anomaly_stone at {} (tier={})", targetState, target, tier);
        }
        return PlacementAttempt.success();
    }

    private boolean isConvertibleStone(IBlockState state) {
        if (state == null) return false;
        Block block = state.getBlock();
        if (CONVERTIBLE_STONE.contains(block)) return true;

        Material material = state.getMaterial();
        if (material == Material.ROCK && !material.isLiquid() && !material.isReplaceable()) {
            return state.isFullCube();
        }
        return false;
    }

    private BlockPos randomResourcePos(Random rnd, int verticalRange) {
        BlockPos anchor = enforceTierVertical(getAnchor());
        int dx = rnd.nextInt(resourceRadius * 2 + 1) - resourceRadius;
        int dz = rnd.nextInt(resourceRadius * 2 + 1) - resourceRadius;
        int dy = rnd.nextInt(verticalRange * 2 + 1) - verticalRange;

        BlockPos candidate = new BlockPos(anchor.getX() + dx, anchor.getY() + dy, anchor.getZ() + dz);
        candidate = enforceTierVertical(candidate);
        int safeY = MathHelper.clamp(candidate.getY(), 1, world.getHeight() - 2);
        return new BlockPos(candidate.getX(), safeY, candidate.getZ());
    }

    private BlockPos randomPosInRadius(Random rnd, int radius) {
        BlockPos anchor = getAnchor();
        int dx = 0;
        int dy = 0;
        int dz = 0;
        for (int i = 0; i < 5; i++) {
            dx = rnd.nextInt(radius * 2 + 1) - radius;
            dy = rnd.nextInt(radius * 2 + 1) - radius;
            dz = rnd.nextInt(radius * 2 + 1) - radius;
            if (dx * dx + dy * dy + dz * dz <= radius * radius) {
                break;
            }
        }
        int y = MathHelper.clamp(anchor.getY() + dy, 1, world.getHeight() - 2);
        return new BlockPos(anchor.getX() + dx, y, anchor.getZ() + dz);
    }

    private void spreadInfection() {
        if (seedPos == null || remainingSpreads <= 0) return;
        final int runs = Math.min(budgetPerTick, remainingSpreads);
        final Random rnd = world.rand;

        for (int i = 0; i < runs; i++) {
            BlockPos target = randomPosInRadius(rnd, radiusBlocks);
            if (!world.isBlockLoaded(target)) continue;
            TaintHelper.spreadFibres(world, target, true);
        }

        remainingSpreads -= runs;
    }

    private boolean spawnTaintSeed() {
        try {
            ensureSeedPosition();
            if (seedPos == null) {
                if (TAConfig.ENABLE_FLUX_ANOMALY_DEBUG_LOGS) {
                    LOG.debug("[FluxAnomaly] Seed spawn skipped: no safe position near {}", center);
                }
                finishAnomaly(FinishReason.SPAWN_POS_INVALID, false);
                return false;
            }
            EntityTaintSeed seed = new EntityTaintSeed(world);
            seed.setPosition(seedPos.getX() + 0.5, seedPos.getY() + 0.5, seedPos.getZ() + 0.5);

            // Сделаем его “как объект”, а не как моб
            seed.enablePersistence();           // не деспавнится
            seed.setSilent(true);               // без звуков
            seed.setNoAI(true);                 // не двигается и не атакует (в 1.12 у EntityLiving есть setNoAI)
            seed.boost = 999;                   // усиленный (по желанию)

            boolean spawned = world.spawnEntity(seed);
            if (!spawned) {
                LOG.error("[FluxAnomaly] Failed to spawn EntityTaintSeed at {}", seedPos);
                return false;
            }
            seedEntityId = seed.getUniqueID();
            lastSeedSeenTick = world.getTotalWorldTime();
            if (!world.isRemote) {
                TaintHelper.addTaintSeed(world, seedPos);
                kickstartTaintSpread();
                if (TAConfig.ENABLE_FLUX_ANOMALY_DEBUG_LOGS) {
                    LOG.debug("[FluxAnomaly] Registered taint seed at {}", seedPos);
                }
            }
            InfectedChunkAnomalyManager.onSeedSpawned(world, seedEntityId, sourceChunkKey, seedPos);

            TAInfectedChunksData data = TAInfectedChunksData.get(world);
            data.setLastActivatedInfo(tier, seedPos);

            LOG.info("[FluxAnomaly] Seed spawned uuid={} at {}", seedEntityId, seedPos);
            return true;
        } catch (Throwable t) {
            LOG.error("[FluxAnomaly] Failed to spawn EntityTaintSeed at {}", seedPos, t);
            return false;
        }
    }

    private void kickstartTaintSpread() {
        if (seedPos == null) return;
        Random rnd = world.rand;
        for (int i = 0; i < TAINT_KICKSTART_ATTEMPTS; i++) {
            int dx = rnd.nextInt(TAINT_KICKSTART_RADIUS * 2 + 1) - TAINT_KICKSTART_RADIUS;
            int dy = rnd.nextInt(3) - 1;
            int dz = rnd.nextInt(TAINT_KICKSTART_RADIUS * 2 + 1) - TAINT_KICKSTART_RADIUS;
            BlockPos target = seedPos.add(dx, dy, dz);
            if (world.isBlockLoaded(target)) {
                TaintHelper.spreadFibres(world, target, true);
            }
        }
    }

    private void killSeedIfPresent() {
        if (seedEntityId == null) return;
        for (Entity ent : world.loadedEntityList) {
            if (seedEntityId.equals(ent.getUniqueID())) {
                ent.setDead();
                break;
            }
        }
    }

    private boolean isSeedAlive() {
        if (seedEntityId == null) return false;
        Entity seed = findSeedEntity();
        if (seed != null && !seed.isDead) {
            lastSeedSeenTick = world.getTotalWorldTime();
            return true;
        }
        return world.getTotalWorldTime() - lastSeedSeenTick <= SEED_MISSING_GRACE_TICKS;
    }

    private Entity findSeedEntity() {
        if (seedEntityId == null) return null;
        if (world instanceof WorldServer) {
            return ((WorldServer) world).getEntityFromUuid(seedEntityId);
        }
        for (Entity ent : world.loadedEntityList) {
            if (seedEntityId.equals(ent.getUniqueID())) {
                return ent;
            }
        }
        return null;
    }

    private void transitionToPhase(AnomalyPhase nextPhase) {
        if (phase == nextPhase) return;
        LOG.info("[FluxAnomaly] Phase change id={} {} -> {} seedPos={}", anomalyId, phase, nextPhase, seedPos);
        phase = nextPhase;
    }

    private void finishAnomaly(FinishReason reason, boolean killSeed) {
        if (finished) {
            setDead();
            return;
        }
        finished = true;
        phase = AnomalyPhase.FINISH;
        finishReason = reason;
        if (killSeed) {
            killSeedIfPresent();
        }
        if (!world.isRemote && seedPos != null) {
            TaintHelper.removeTaintSeed(world, seedPos);
            if (TAConfig.ENABLE_FLUX_ANOMALY_DEBUG_LOGS) {
                LOG.debug("[FluxAnomaly] Deregistered taint seed at {}", seedPos);
            }
        }
        LOG.info("[FluxAnomaly] FINISH id={} dim={} seedPos={} center={} remaining={} reason={} killSeed={}",
                anomalyId,
                world.provider.getDimension(),
                seedPos,
                center,
                remainingSpreads,
                reason,
                killSeed);
        InfectedChunkAnomalyManager.onAnomalyEnded(world, seedEntityId, anomalyId, sourceChunkKey);
        setDead();
    }

    private BlockPos pickResourceColumn(Random rnd) {
        int dx = rnd.nextInt(resourceRadius * 2 + 1) - resourceRadius;
        int dz = rnd.nextInt(resourceRadius * 2 + 1) - resourceRadius;

        BlockPos anchor = getAnchor();
        return new BlockPos(anchor.getX() + dx, 0, anchor.getZ() + dz);
    }

    private BlockPos randomNearbyPos(Random rnd, int minY, int maxY, int radius) {
        int dx = rnd.nextInt(radius * 2 + 1) - radius;
        int dz = rnd.nextInt(radius * 2 + 1) - radius;
        int dy = minY + rnd.nextInt(Math.max(1, maxY - minY + 1));
        BlockPos anchor = getAnchor();
        return new BlockPos(anchor.getX() + dx, dy, anchor.getZ() + dz);
    }

    private void ensureSeedPosition() {
        if (seedPos != null) {
            if (isSeedPositionValid(seedPos)) return;
            if (TAConfig.ENABLE_FLUX_ANOMALY_DEBUG_LOGS) {
                LOG.debug("[FluxAnomaly] Cached seed position {} invalid, reselecting", seedPos);
            }
            seedPos = null;
        }

        BlockPos candidate = enforceTierVertical(center);
        BlockPos safePos = findValidatedSeedPos(candidate);

        if (safePos == null && !candidate.equals(center)) {
            safePos = findValidatedSeedPos(center);
        }

        if (safePos != null) {
            seedPos = safePos.toImmutable();
            center = seedPos;
            if (TAConfig.ENABLE_FLUX_ANOMALY_DEBUG_LOGS) {
                LOG.debug("[FluxAnomaly] Selected safe seed position center={} seedPos={}", candidate, seedPos);
            }
            return;
        }
        if (TAConfig.ENABLE_FLUX_ANOMALY_DEBUG_LOGS) {
            LOG.debug("[FluxAnomaly] Failed to find safe seed position near {}", center);
        }
    }

    private BlockPos findValidatedSeedPos(BlockPos candidate) {
        BlockPos safePos = WorldSpawnUtil.findSafeSeedPos(world, candidate, world.rand);
        if (safePos != null && !isSeedPositionValid(safePos)) {
            if (TAConfig.ENABLE_FLUX_ANOMALY_DEBUG_LOGS) {
                LOG.debug("[FluxAnomaly] Seed position {} rejected after validation (tier={})", safePos, tier);
            }
            return null;
        }
        return safePos;
    }

    private boolean isSeedPositionValid(BlockPos pos) {
        if (!WorldSpawnUtil.isSafeSeedPos(world, pos)) {
            if (TAConfig.ENABLE_FLUX_ANOMALY_DEBUG_LOGS) {
                LOG.debug("[FluxAnomaly] Seed position {} rejected: not safe", pos);
            }
            return false;
        }
        if (tier != FluxAnomalyTier.SURFACE && world.canBlockSeeSky(pos)) {
            if (TAConfig.ENABLE_FLUX_ANOMALY_DEBUG_LOGS) {
                LOG.debug("[FluxAnomaly] Seed position {} rejected: sky exposed for tier {}", pos, tier);
            }
            return false;
        }
        return true;
    }

    private BlockPos enforceTierVertical(BlockPos pos) {
        switch (tier) {
            case SHALLOW: {
                int maxY = Math.min(56, Math.max(1, world.getSeaLevel() - 5));
                return clampVertical(pos, 20, maxY);
            }
            case DEEP:
                return clampVertical(pos, 1, 24);
            case SURFACE:
            default:
                return pos;
        }
    }

    private BlockPos clampVertical(BlockPos pos, int minY, int maxY) {
        int y = MathHelper.clamp(pos.getY(), minY, maxY);
        return new BlockPos(pos.getX(), y, pos.getZ());
    }

    private BlockPos getAnchor() {
        if (seedPos != null) return seedPos;
        if (center != null) return center;
        return new BlockPos(this.posX, this.posY, this.posZ);
    }

    public UUID getAnomalyId() {
        return anomalyId;
    }

    public BlockPos getSeedPos() {
        return seedPos;
    }

    public boolean isResourceBlock(Block block) {
        return block != null && block == resourceBlock;
    }

    public int getResourceOvergrowthCap() {
        return resourceCap;
    }

    public boolean canSpawnResource(Block block) {
        if (!isResourceBlock(block) || maxResources <= 0) return false;
        activeResourceCount = FluxResourceHelper.countBlocks(world, getAnchor(), resourceBlock, resourceRadius);
        return activeResourceCount < maxResources;
    }

    public void requestOvergrowthKill() {
        seedKilledByOvergrowth = true;
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound tag) {
        if (tag.hasUniqueId("anId")) anomalyId = tag.getUniqueId("anId");
        awakened = tag.getBoolean("awake");

        center = new BlockPos(tag.getInteger("cx"), tag.getInteger("cy"), tag.getInteger("cz"));

        radiusBlocks = MathHelper.clamp(tag.getInteger("rad"), MIN_CORRUPTION_RADIUS, MAX_CORRUPTION_RADIUS);
        totalSpreads = Math.max(0, tag.getInteger("total"));
        budgetPerTick = Math.max(1, tag.getInteger("budget"));
        remainingSpreads = Math.max(0, tag.getInteger("remain"));

        if (tag.hasKey("spawnMethod", 8)) {
            try {
                spawnMethod = FluxAnomalySpawnMethod.valueOf(tag.getString("spawnMethod"));
            } catch (IllegalArgumentException ignored) {
                spawnMethod = FluxAnomalySpawnMethod.API;
            }
        } else {
            spawnMethod = FluxAnomalySpawnMethod.API;
        }

        resourceBlockId = null;
        if (tag.hasKey("resBlock", 8)) {
            try {
                ResourceLocation resId = new ResourceLocation(tag.getString("resBlock"));
                resourceBlockId = resId;
            } catch (Exception ignored) {
                resourceBlockId = null;
            }
        }
        maxResources = Math.max(0, tag.getInteger("resCount"));
        resourceCap = tag.getInteger("resCap");
        initialResourcesPlaced = tag.getBoolean("resPlaced");
        lastResourceAttemptTick = tag.getLong("resAttempt");
        resourceBlock = resourceBlockId == null ? null : ForgeRegistries.BLOCKS.getValue(resourceBlockId);
        resourceRadius = Math.max(4, radiusBlocks / 2);
        if (resourceCap <= 0 && resourceBlock != null) {
            resourceCap = FluxResourceHelper.getOvergrowthCap(resourceBlock);
        }

        if (tag.hasUniqueId("seedId")) seedEntityId = tag.getUniqueId("seedId");
        else seedEntityId = null;
        if (tag.hasKey("seedX", 3) && tag.hasKey("seedY", 3) && tag.hasKey("seedZ", 3)) {
            seedPos = new BlockPos(tag.getInteger("seedX"), tag.getInteger("seedY"), tag.getInteger("seedZ"));
        }
        lastSeedSeenTick = tag.getLong("seedLastSeen");
        seedKilledByOvergrowth = tag.getBoolean("seedOvergrowth");
        infectionFinished = tag.getBoolean("infectionFinished");

        tier = FluxAnomalyTier.SURFACE;
        if (tag.hasKey("tier", 8)) {
            try {
                tier = FluxAnomalyTier.valueOf(tag.getString("tier"));
            } catch (IllegalArgumentException ignored) {
                tier = FluxAnomalyTier.SURFACE;
            }
        }
        sourceChunkKey = tag.getLong("srcChunk");

        if (seedPos != null) {
            center = seedPos;
        }

        if (tag.hasKey("finishReason", 8)) {
            try {
                finishReason = FinishReason.valueOf(tag.getString("finishReason"));
            } catch (IllegalArgumentException ignored) {
                finishReason = null;
            }
        }

        if (tag.hasKey("phase", 8)) {
            try {
                phase = AnomalyPhase.valueOf(tag.getString("phase"));
            } catch (IllegalArgumentException ignored) {
                phase = AnomalyPhase.SEED;
            }
        } else {
            if (seedEntityId != null) {
                if (remainingSpreads > 0) {
                    phase = AnomalyPhase.INFECT;
                } else if (!initialResourcesPlaced) {
                    phase = AnomalyPhase.PLACE_RESOURCES;
                } else {
                    phase = AnomalyPhase.ACTIVE;
                }
            } else {
                phase = AnomalyPhase.SEED;
            }
        }
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {
        tag.setUniqueId("anId", anomalyId);
        tag.setBoolean("awake", awakened);

        BlockPos anchor = getAnchor();
        tag.setInteger("cx", anchor.getX());
        tag.setInteger("cy", anchor.getY());
        tag.setInteger("cz", anchor.getZ());

        tag.setInteger("rad", radiusBlocks);
        tag.setInteger("total", totalSpreads);
        tag.setInteger("budget", budgetPerTick);
        tag.setInteger("remain", remainingSpreads);

        tag.setString("spawnMethod", spawnMethod.name());
        if (resourceBlockId != null) {
            tag.setString("resBlock", resourceBlockId.toString());
        }
        tag.setInteger("resCount", maxResources);
        tag.setInteger("resCap", resourceCap);
        tag.setBoolean("resPlaced", initialResourcesPlaced);
        tag.setLong("resAttempt", lastResourceAttemptTick);

        if (seedEntityId != null) tag.setUniqueId("seedId", seedEntityId);
        if (seedPos != null) {
            tag.setInteger("seedX", seedPos.getX());
            tag.setInteger("seedY", seedPos.getY());
            tag.setInteger("seedZ", seedPos.getZ());
        }
        tag.setLong("seedLastSeen", lastSeedSeenTick);
        tag.setBoolean("seedOvergrowth", seedKilledByOvergrowth);
        tag.setBoolean("infectionFinished", infectionFinished);
        tag.setString("tier", tier.name());
        tag.setLong("srcChunk", sourceChunkKey);
        if (finishReason != null) {
            tag.setString("finishReason", finishReason.name());
        }
        tag.setString("phase", phase.name());
    }

    private static final class PlacementAttempt {
        final boolean success;
        final String reason;

        private PlacementAttempt(boolean success, String reason) {
            this.success = success;
            this.reason = reason == null ? "UNKNOWN" : reason;
        }

        static PlacementAttempt success() {
            return new PlacementAttempt(true, "OK");
        }

        static PlacementAttempt fail(String reason) {
            return new PlacementAttempt(false, reason);
        }
    }

    private enum FinishReason {
        REMAINING_ZERO,
        SEED_KILLED_BY_OVERGROWTH,
        SEED_DEAD,
        SPAWN_POS_INVALID,
        MANUAL
    }

    private enum AnomalyPhase {
        SEED,
        INFECT,
        PLACE_RESOURCES,
        ACTIVE,
        FINISH
    }

}
