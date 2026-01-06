// src/main/java/therealpant/thaumicattempts/world/anomaly/EntityFluxAnomalyBurst.java
package therealpant.thaumicattempts.world;

import net.minecraft.block.Block;
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
import therealpant.thaumicattempts.world.block.BlockAnomalyStone;
import therealpant.thaumicattempts.world.block.BlockRiftBush;
import therealpant.thaumicattempts.world.block.BlockRiftGeod;
import therealpant.thaumicattempts.world.block.FluxResourceHelper;
import therealpant.thaumicattempts.world.InfectedChunkAnomalyManager;

import java.util.Random;
import java.util.UUID;

public class EntityFluxAnomalyBurst extends Entity {

    private static final Logger LOG = LogManager.getLogger("ThaumicAttempts|FluxAnomaly");

    private static final int MIN_CORRUPTION_RADIUS = 8;
    private static final int MAX_CORRUPTION_RADIUS = 10;

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

    private boolean awakened = false;
    private int remainingSpreads = 0;
    private long sourceChunkKey = 0L;
    private FluxAnomalyTier tier = FluxAnomalyTier.SURFACE;
    private long lastGrowthTick = 0;
    private boolean finished = false;

    private UUID seedEntityId = null;

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

    public FluxAnomalySpawnMethod getSpawnMethod() {
        return spawnMethod;
    }

    public FluxAnomalyTier getTier() {
        return tier;
    }

    public long getSourceChunkKey() {
        return sourceChunkKey;
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

        if (!awakened) {
            awakened = true;
            remainingSpreads = Math.max(0, totalSpreads);

            // 1) Спавним настоящий EntityTaintSeed (обязательно!)
            spawnTaintSeed();

            ensureInitialResources();
        } else if (!initialResourcesPlaced) {
            ensureInitialResources();
        }

        if (!isSeedAlive()) {
            remainingSpreads = 0;
            finishAnomaly("SEED_DEAD", false);
            return;
        }

        runGrowthTick();

        if (remainingSpreads <= 0) {
            LOG.info("[FluxAnomaly] FINISH id={} dim={} center={}", anomalyId, world.provider.getDimension(), center);

            finishAnomaly("SPREAD_FINISHED", true);
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
            LOG.info("[FluxAnomaly] TICK id={} remaining={}", anomalyId, remainingSpreads);
        }
    }

    private void ensureInitialResources() {
        initialResourcesPlaced = true;

        if (resourceBlockId == null || resourceBlock == null || resourceCount <= 0) return;

        Block block = ForgeRegistries.BLOCKS.getValue(resourceBlockId);
        if (block == null || block == Blocks.AIR) {
            LOG.warn("[FluxAnomaly] Resource block {} missing, skipping placement", resourceBlockId);
            return;
        }

        int placed = FluxResourceHelper.countBlocks(world, center, block, 8);
        final Random rnd = world.rand;
        final int attempts = Math.max(resourceCount * 8, resourceCount);

        for (int i = 0; i < attempts && placed < resourceCount; i++) {
            if (placeSingleResource(block, rnd)) {                placed++;
            }
        }
    }

    private void runGrowthTick() {
        if (resourceBlock == null || resourceCap <= 0) return;
        long now = world.getTotalWorldTime();
        if (now - lastGrowthTick < 40) return;
        lastGrowthTick = now;

        int current = FluxResourceHelper.countBlocks(world, center, resourceBlock, 8);
        if (current < resourceCap) {
            placeSingleResource(resourceBlock, world.rand);
        } else if (world.rand.nextFloat() < 0.33f) {
            finishAnomaly("GROWTH_CAP_REACHED", true);
        }
    }

    private boolean placeSingleResource(Block block, Random rnd) {

        if (block instanceof BlockRiftBush) {
            return placeBush((BlockRiftBush) block, rnd);
        }
        if (block instanceof BlockRiftGeod) {
            return placeGeod((BlockRiftGeod) block, rnd);
        }
        if (block instanceof BlockAnomalyStone) {
            return placeAnomalyStone((BlockAnomalyStone) block, rnd);
        }

        IBlockState state = block.getDefaultState();
        BlockPos pos = pickTargetColumn(rnd).up();
        if (!world.isBlockLoaded(pos)) return false;
        if (!block.canPlaceBlockAt(world, pos)) return false;
        if (!world.getBlockState(pos).getMaterial().isReplaceable() && !world.isAirBlock(pos)) return false;
        return world.setBlockState(pos, state, 3);
    }

    private boolean placeBush(BlockRiftBush bush, Random rnd) {
        BlockPos column = pickTargetColumn(rnd);
        BlockPos base = world.getTopSolidOrLiquidBlock(column);
        BlockPos lower = base.up();
        if (lower.getY() >= world.getHeight() - 1 || lower.getY() < 1) return false;
        if (!world.isBlockLoaded(lower)) return false;
        if (!world.isAirBlock(lower) || !world.isAirBlock(lower.up())) return false;
        IBlockState baseState = world.getBlockState(base);
        if (baseState.getMaterial().isLiquid() || baseState.getBlock() == Blocks.AIR) return false;
        if (!bush.canPlaceBlockAt(world, lower)) return false;

        IBlockState lowerState = bush.getDefaultState().withProperty(BlockRiftBush.HALF, BlockRiftBush.BlockHalf.LOWER);
        IBlockState upperState = bush.getDefaultState().withProperty(BlockRiftBush.HALF, BlockRiftBush.BlockHalf.UPPER);

        world.setBlockState(lower, lowerState, 3);
        world.setBlockState(lower.up(), upperState, 3);
        return true;
    }

    private boolean placeAnomalyStone(BlockAnomalyStone stone, Random rnd) {
        int minY = tier == FluxAnomalyTier.SHALLOW ? 16 : 4;
        int maxY = tier == FluxAnomalyTier.SHALLOW ? 64 : 32;
        for (int i = 0; i < 24; i++) {
            BlockPos target = randomNearbyPos(rnd, minY, maxY);
            if (!world.isBlockLoaded(target)) continue;
            if (world.getBlockState(target).getBlock() != Blocks.STONE) continue;
            if (!hasAdjacentAir(target)) continue;
            world.setBlockState(target, stone.getDefaultState(), 3);
            return true;
        }
        return false;
    }

    private boolean placeGeod(BlockRiftGeod geod, Random rnd) {
        int minY = tier == FluxAnomalyTier.SHALLOW ? 16 : 4;
        int maxY = tier == FluxAnomalyTier.SHALLOW ? 64 : 32;
        for (int i = 0; i < 24; i++) {
            BlockPos target = randomNearbyPos(rnd, minY, maxY);
            if (!world.isBlockLoaded(target)) continue;
            if (!world.isAirBlock(target)) continue;

            EnumFacing facing = null;
            for (EnumFacing face : EnumFacing.values()) {
                BlockPos neighbor = target.offset(face.getOpposite());
                IBlockState neighborState = world.getBlockState(neighbor);
                if (neighborState.isSideSolid(world, neighbor, face)) {
                    facing = face;
                    break;
                }
            }
            if (facing == null) continue;
            IBlockState state = geod.getDefaultState().withProperty(BlockRiftGeod.FACING, facing);
            if (!geod.canPlaceBlockAt(world, target)) continue;
            if (!world.getBlockState(target).getMaterial().isReplaceable() && !world.isAirBlock(target)) continue;
            world.setBlockState(target, state, 3);
            return true;
        }
        return false;
    }

    private void spawnTaintSeed() {
        try {
            EntityTaintSeed seed = new EntityTaintSeed(world);
            seed.setPosition(center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5);

            // Сделаем его “как объект”, а не как моб
            seed.enablePersistence();           // не деспавнится
            seed.setSilent(true);               // без звуков
            seed.setNoAI(true);                 // не двигается и не атакует (в 1.12 у EntityLiving есть setNoAI)
            seed.boost = 999;                   // усиленный (по желанию)

            world.spawnEntity(seed);
            seedEntityId = seed.getUniqueID();
            InfectedChunkAnomalyManager.onSeedSpawned(world, seedEntityId, hostingChunkKey);

            LOG.info("[FluxAnomaly] Seed spawned uuid={} at {}", seedEntityId, center);
        } catch (Throwable t) {
            LOG.error("[FluxAnomaly] Failed to spawn EntityTaintSeed at {}", center, t);
        }
    }

    private boolean isSeedAlive() {
        if (seedEntityId == null) return false;
        for (Entity ent : world.loadedEntityList) {
            if (seedEntityId.equals(ent.getUniqueID())) {
                return !ent.isDead;
            }
        }
        return false;
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

    private void finishAnomaly(String reason, boolean killSeed) {
        if (finished) {
            setDead();
            return;
        }
        finished = true;
        if (killSeed) {
            killSeedIfPresent();
        }
        FluxAnomalySpawner.onAnomalyFinished(world, anomalyId, sourceChunkKey, center);
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

        return new BlockPos(center.getX() + dx, 0, center.getZ() + dz);
    }

    private BlockPos randomNearbyPos(Random rnd, int minY, int maxY) {
        int dx = rnd.nextInt(9) - 4;
        int dz = rnd.nextInt(9) - 4;
        int dy = minY + rnd.nextInt(Math.max(1, maxY - minY));
        return new BlockPos(center.getX() + dx, dy, center.getZ() + dz);
    }

    private boolean hasAdjacentAir(BlockPos pos) {
        for (EnumFacing face : EnumFacing.values()) {
            if (world.isAirBlock(pos.offset(face))) {
                return true;
            }
        }
        return false;
    }

    private int determineCap(Block block) {
        if (block instanceof BlockRiftBush) return 8;
        if (block instanceof BlockAnomalyStone) return 7;
        if (block instanceof BlockRiftGeod) return 5;
        return 0;
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
        resourceBlock = resourceBlockId == null ? null : ForgeRegistries.BLOCKS.getValue(resourceBlockId);

        if (tag.hasUniqueId("seedId")) seedEntityId = tag.getUniqueId("seedId");
        else seedEntityId = null;

        tier = FluxAnomalyTier.SURFACE;
        if (tag.hasKey("tier", 8)) {
            try {
                tier = FluxAnomalyTier.valueOf(tag.getString("tier"));
            } catch (IllegalArgumentException ignored) {
                tier = FluxAnomalyTier.SURFACE;
            }
        }
        sourceChunkKey = tag.getLong("srcChunk");
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {
        tag.setUniqueId("anId", anomalyId);
        tag.setBoolean("awake", awakened);

        tag.setInteger("cx", center.getX());
        tag.setInteger("cy", center.getY());
        tag.setInteger("cz", center.getZ());

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

        if (seedEntityId != null) tag.setUniqueId("seedId", seedEntityId);
        tag.setString("tier", tier.name());
        tag.setLong("srcChunk", sourceChunkKey);
    }

}
