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
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

    private static final int MIN_CORRUPTION_RADIUS = 8;
    private static final int MAX_CORRUPTION_RADIUS = 10;
    private static final int RESOURCE_RETRY_TICKS = 20;
    private static final int RESOURCE_SPREAD_ATTEMPTS = 3;
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
    private int totalSpreads = 18500;   // сколько spreadFibres всего
    private int budgetPerTick = 320;   // сколько spreadFibres за тик
    private FluxAnomalySpawnMethod spawnMethod = FluxAnomalySpawnMethod.API;
    private ResourceLocation resourceBlockId = null;
    private Block resourceBlock = null;
    private int resourceCount = 0;
    private int resourceCap = 0;
    private boolean initialResourcesPlaced = false;
    private long lastResourceAttemptTick = 0L;

    private boolean awakened = false;
    private int remainingSpreads = 0;
    private long sourceChunkKey = 0L;
    private FluxAnomalyTier tier = FluxAnomalyTier.SURFACE;
    private long lastGrowthTick = 0;
    private boolean finished = false;

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
                resourceCount = resource.getBlockCount();
                resourceCap = determineCap(resourceBlock);
                initialResourcesPlaced = false;
            } else {
                resourceBlockId = null;
                resourceBlock = null;
                resourceCount = 0;
                resourceCap = 0;
            }
        } else {
            resourceBlockId = null;
            resourceCount = 0;
            resourceCap = 0;
            resourceBlock = null;
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
        ensureSeedPosition();

        if (!awakened) {
            awakened = true;
            remainingSpreads = Math.max(0, totalSpreads);

            // 1) Спавним настоящий EntityTaintSeed (обязательно!)
            spawnTaintSeed();

            ensureInitialResources();
        } else if (!initialResourcesPlaced && world.getTotalWorldTime() - lastResourceAttemptTick >= RESOURCE_RETRY_TICKS) {
            ensureInitialResources();
        }

        runGrowthTick();

        if (seedKilledByOvergrowth) {
            finishAnomaly(FinishReason.SEED_KILLED_BY_OVERGROWTH, true);
            return;
        }

        if (remainingSpreads <= 0) {
            finishAnomaly(FinishReason.REMAINING_ZERO, false);
            return;
        }

        final int runs = Math.min(budgetPerTick, remainingSpreads);
        final Random rnd = world.rand;

        for (int i = 0; i < runs; i++) {
            BlockPos col = pickTargetColumn(rnd);
            BlockPos top = world.getTopSolidOrLiquidBlock(col);

            // ковёр: 0..2 вниз, но в основном поверхность
            BlockPos target;
            if (rnd.nextFloat() < 0.70f) target = top;
            else target = top.down(1 + rnd.nextInt(2));

            // защита от “ухода в глубину”
            int minY = Math.max(1, world.getSeaLevel() - 3);
            if (target.getY() < minY) target = top;

            // Агрессивный spread (игнор wussMode и шанс)
            TaintHelper.spreadFibres(world, target, true);
        }

        remainingSpreads -= runs;

        if ((world.getTotalWorldTime() % 40L) == 0L) {
            LOG.info("[FluxAnomaly] TICK id={} remaining={} seedPos={} tier={} resourcesPlaced={}", anomalyId, remainingSpreads, seedPos, tier, initialResourcesPlaced);
        }
    }

    private void ensureInitialResources() {
        lastResourceAttemptTick = world.getTotalWorldTime();

        if (resourceBlockId == null || resourceBlock == null || resourceCount <= 0) {
            initialResourcesPlaced = true;
            TAInfectedChunksData.get(world).setLastResourcePlacement("RESOURCE_SKIPPED", 0, 0);
            return;
        }

        Block block = ForgeRegistries.BLOCKS.getValue(resourceBlockId);
        if (block == null || block == Blocks.AIR) {
            LOG.warn("[FluxAnomaly] Resource block {} missing, skipping placement", resourceBlockId);
            TAInfectedChunksData.get(world).setLastResourcePlacement("RESOURCE_BLOCK_MISSING", 0, 0);
            return;
        }

        BlockPos anchor = getAnchor();
        int placed = FluxResourceHelper.countBlocks(world, anchor, block, 8);
        final Random rnd = world.rand;
        final int attempts = Math.max(resourceCount * 8, resourceCount);
        int successes = 0;
        int usedAttempts = 0;
        Map<String, Integer> failureReasons = new HashMap<>();

        for (int i = 0; i < attempts && placed < resourceCount; i++) {
            PlacementAttempt attempt = tryPlaceResource(block, rnd);
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
                placed >= resourceCount ? "PLACEMENT_OK" : "PLACEMENT_INSUFFICIENT",
                usedAttempts,
                successes
        );
        if (placed >= resourceCount) {
            initialResourcesPlaced = true;
        }

        LOG.info("[FluxAnomaly] Resource placement tier={} seedPos={} placed={}/{} attempts={} success={} failures={}"
                        + "",
                tier,
                anchor,
                placed,
                resourceCount,
                usedAttempts,
                successes,
                failureReasons);
    }

    private void runGrowthTick() {
        if (resourceBlock == null || resourceCap <= 0) return;
        long now = world.getTotalWorldTime();
        if (now - lastGrowthTick < 40) return;
        lastGrowthTick = now;

        int current = FluxResourceHelper.countBlocks(world, getAnchor(), resourceBlock, 8);
        if (current < resourceCap) {
            tryPlaceResource(resourceBlock, world.rand);
        } else if (world.rand.nextFloat() < 0.33f) {
            seedKilledByOvergrowth = true;
        }
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
        BlockPos column = pickTargetColumn(rnd);
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
            BlockPos target = randomNearbyPos(rnd, minY, maxY);
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
            return  PlacementAttempt.success();
        }
        return PlacementAttempt.fail(lastFail);
    }

    private PlacementAttempt trySpawnAnomalousResource(BlockAnomalyStone stone, Random rnd) {
        BlockPos anchor = enforceTierVertical(getAnchor());
        if (!isInTaintZone(anchor)) {
            return PlacementAttempt.fail("NO_TAINT_ZONE");
        }

        String lastFail = "NO_POSITION";
        for (int i = 0; i < RESOURCE_SPREAD_ATTEMPTS; i++) {
            BlockPos target = pickSpreadLikePosition(rnd);
            if (!world.isBlockLoaded(target)) {
                lastFail = "CHUNK_NOT_LOADED";
                continue;
            }
            if (!isInTaintZone(target)) {
                lastFail = "NOT_TAINTED";
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
        if (TAConfig.ENABLE_FLUX_ANOMALY_DEBUG_LOGS) {
            LOG.debug("[FluxAnomaly] Converted {} -> anomaly_stone at {} (tier={})", targetState, target, tier);
        }
        return PlacementAttempt.success();
    }

    private boolean isInTaintZone(BlockPos pos) {
        if (pos == null) return false;
        if (!world.isBlockLoaded(pos)) {
            world.getChunk(pos);
        }
        return TaintHelper.isNearTaintSeed(world, pos);
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

    private BlockPos pickSpreadLikePosition(Random rnd) {
        BlockPos anchor = enforceTierVertical(getAnchor());
        // Адаптация логики выбора позиции из Thaumcraft TaintHelper.spreadFibres:
        // небольшой радиус, случайные смещения по горизонтали и вертикали.
        int horizontal = 2 + rnd.nextInt(5); // 2..6
        int dx = rnd.nextInt(horizontal * 2 + 1) - horizontal;
        int dz = rnd.nextInt(horizontal * 2 + 1) - horizontal;
        int dy = rnd.nextInt(3) - 1; // -1..1

        BlockPos candidate = new BlockPos(anchor.getX() + dx, anchor.getY() + dy, anchor.getZ() + dz);
        candidate = enforceTierVertical(candidate);
        int safeY = MathHelper.clamp(candidate.getY(), 1, world.getHeight() - 2);
        return new BlockPos(candidate.getX(), safeY, candidate.getZ());
    }

    private void spawnTaintSeed() {
        try {
            ensureSeedPosition();
            if (seedPos == null) {
                if (TAConfig.ENABLE_FLUX_ANOMALY_DEBUG_LOGS) {
                    LOG.debug("[FluxAnomaly] Seed spawn skipped: no safe position near {}", center);
                }
                finishAnomaly(FinishReason.SPAWN_POS_INVALID, false);
                return;
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
                return;
            }
            seedEntityId = seed.getUniqueID();
            lastSeedSeenTick = world.getTotalWorldTime();
            if (!world.isRemote) {
                TaintHelper.addTaintSeed(world, seedPos);
                if (TAConfig.ENABLE_FLUX_ANOMALY_DEBUG_LOGS) {
                    LOG.debug("[FluxAnomaly] Registered taint seed at {}", seedPos);
                }
            }
            InfectedChunkAnomalyManager.onSeedSpawned(world, seedEntityId, sourceChunkKey, seedPos);

            TAInfectedChunksData data = TAInfectedChunksData.get(world);
            data.setLastActivatedInfo(tier, seedPos);

            LOG.info("[FluxAnomaly] Seed spawned uuid={} at {}", seedEntityId, seedPos);
        } catch (Throwable t) {
            LOG.error("[FluxAnomaly] Failed to spawn EntityTaintSeed at {}", seedPos, t);
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

    private void finishAnomaly(FinishReason reason, boolean killSeed) {
        if (finished) {
            setDead();
            return;
        }
        finished = true;
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

    private BlockPos pickTargetColumn(Random rnd) {
        float angle = rnd.nextFloat() * (float) (Math.PI * 2);

        float targetRadius;
        if (rnd.nextFloat() < 0.55f) {
            // плотное заражение в пределах безопасной зоны
            targetRadius = MIN_CORRUPTION_RADIUS * MathHelper.sqrt(rnd.nextFloat());
        } else {
            float outerBase = MIN_CORRUPTION_RADIUS + rnd.nextFloat() * (radiusBlocks - MIN_CORRUPTION_RADIUS);
            float jitter = (rnd.nextFloat() - rnd.nextFloat()) * 3.0f;
            targetRadius = MathHelper.clamp(outerBase + jitter, MIN_CORRUPTION_RADIUS * 0.5f, radiusBlocks);
        }

        int dx = Math.round(MathHelper.cos(angle) * targetRadius);
        int dz = Math.round(MathHelper.sin(angle) * targetRadius);

        return new BlockPos(getAnchor().getX() + dx, 0, getAnchor().getZ() + dz);
    }

    private BlockPos randomNearbyPos(Random rnd, int minY, int maxY) {
        int dx = rnd.nextInt(9) - 4;
        int dz = rnd.nextInt(9) - 4;
        int dy = minY + rnd.nextInt(Math.max(1, maxY - minY + 1));
        BlockPos anchor = getAnchor();
        return new BlockPos(anchor.getX() + dx, dy, anchor.getZ() + dz);
    }

    private int determineCap(Block block) {
        if (block instanceof BlockRiftBush) return 8;
        if (block instanceof BlockAnomalyStone) return 7;
        if (block instanceof BlockRiftGeod) return 5;
        return 0;
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
        resourceCount = Math.max(0, tag.getInteger("resCount"));
        resourceCap = tag.getInteger("resCap");
        initialResourcesPlaced = tag.getBoolean("resPlaced");
        lastResourceAttemptTick = tag.getLong("resAttempt");
        resourceBlock = resourceBlockId == null ? null : ForgeRegistries.BLOCKS.getValue(resourceBlockId);

        if (tag.hasUniqueId("seedId")) seedEntityId = tag.getUniqueId("seedId");
        else seedEntityId = null;
        if (tag.hasKey("seedX", 3) && tag.hasKey("seedY", 3) && tag.hasKey("seedZ", 3)) {
            seedPos = new BlockPos(tag.getInteger("seedX"), tag.getInteger("seedY"), tag.getInteger("seedZ"));
        }
        lastSeedSeenTick = tag.getLong("seedLastSeen");
        seedKilledByOvergrowth = tag.getBoolean("seedOvergrowth");

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
        tag.setInteger("resCount", resourceCount);
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
        tag.setString("tier", tier.name());
        tag.setLong("srcChunk", sourceChunkKey);
        if (finishReason != null) {
            tag.setString("finishReason", finishReason.name());
        }
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
        SPAWN_POS_INVALID,
        MANUAL
    }

}
