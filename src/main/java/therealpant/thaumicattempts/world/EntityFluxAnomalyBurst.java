// src/main/java/therealpant/thaumicattempts/world/anomaly/EntityFluxAnomalyBurst.java
package therealpant.thaumicattempts.world;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
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
import therealpant.thaumicattempts.world.block.BlockRiftBush;
import therealpant.thaumicattempts.world.block.BlockRiftGeod;

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
    private int resourceCount = 0;
    private boolean resourcesPlaced = false;

    private boolean awakened = false;
    private int remainingSpreads = 0;

    // ВАЖНО: настоящий таинт-seed энтити, без него spreadFibres ничего не делает
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
            FluxAnomalyResource resource = settings.getResource();
            if (resource != null) {
                applyResource(resource);
            }
        }
    }

    private void applyResource(FluxAnomalyResource resource) {
        if (resource.isPresent()) {
            ResourceLocation key = resource.getBlock().getRegistryName();
            if (key != null) {
                resourceBlockId = key;
                resourceCount = resource.getBlockCount();
                resourcesPlaced = false;
            }
        } else {
            resourceBlockId = null;
            resourceCount = 0;
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
            spawnOrRefreshTaintSeed();

            // 2) Записываем seed позицию (не строго обязательно, но норм)
            try {
                TaintHelper.addTaintSeed(world, center);
            } catch (Throwable t) {
                LOG.error("[FluxAnomaly] addTaintSeed failed at {}", center, t);
            }
            placeResourcesIfConfigured();
        } else if (!resourcesPlaced) {
            placeResourcesIfConfigured();
        }

        if (remainingSpreads <= 0) {
            LOG.info("[FluxAnomaly] FINISH id={} dim={} center={}", anomalyId, world.provider.getDimension(), center);

            // Если НЕ хочешь, чтобы заражение продолжало жить — убиваем seed
           // killSeedIfPresent();

            setDead();
            return;
        }

        // На всякий случай: если seed каким-то образом исчез, восстановим
        ensureSeedAlive();

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

    private void placeResourcesIfConfigured() {
        resourcesPlaced = true;

        if (resourceBlockId == null || resourceCount <= 0) return;

        Block block = ForgeRegistries.BLOCKS.getValue(resourceBlockId);
        if (block == null || block == Blocks.AIR) {
            LOG.warn("[FluxAnomaly] Resource block {} missing, skipping placement", resourceBlockId);
            return;
        }

        int placed = 0;
        final Random rnd = world.rand;
        final int attempts = Math.max(resourceCount * 6, resourceCount);

        for (int i = 0; i < attempts && placed < resourceCount; i++) {
            BlockPos col = pickTargetColumn(rnd);
            BlockPos top = world.getTopSolidOrLiquidBlock(col);
            BlockPos target = top.getY() <= 1 ? center : top;

            if (placeSingleResource(block, target)) {
                placed++;
            }
        }

        LOG.info("[FluxAnomaly] Placed {} of {} resource blocks ({})", placed, resourceCount, resourceBlockId);
    }

    private boolean placeSingleResource(Block block, BlockPos pos) {
        if (!world.isBlockLoaded(pos)) return false;

        if (block instanceof BlockRiftBush) {
            return placeBush((BlockRiftBush) block, pos);
        }
        if (block instanceof BlockRiftGeod) {
            return placeGeod((BlockRiftGeod) block, pos);
        }

        IBlockState state = block.getDefaultState();
        if (!block.canPlaceBlockAt(world, pos)) return false;
        if (!world.getBlockState(pos).getMaterial().isReplaceable() && !world.isAirBlock(pos)) return false;
        return world.setBlockState(pos, state, 3);
    }

    private boolean placeBush(BlockRiftBush bush, BlockPos pos) {
        if (pos.getY() >= world.getHeight() - 1) return false;
        if (!bush.canPlaceBlockAt(world, pos)) return false;

        IBlockState lower = bush.getDefaultState().withProperty(BlockRiftBush.HALF, BlockRiftBush.BlockHalf.LOWER);
        IBlockState upper = bush.getDefaultState().withProperty(BlockRiftBush.HALF, BlockRiftBush.BlockHalf.UPPER);

        world.setBlockState(pos, lower, 3);
        world.setBlockState(pos.up(), upper, 3);
        return true;
    }

    private boolean placeGeod(BlockRiftGeod geod, BlockPos pos) {
        if (!geod.canPlaceBlockAt(world, pos)) return false;

        EnumFacing facing = EnumFacing.UP;
        for (EnumFacing face : EnumFacing.values()) {
            BlockPos neighbor = pos.offset(face.getOpposite());
            IBlockState neighborState = world.getBlockState(neighbor);
            if (neighborState.isSideSolid(world, neighbor, face)) {
                facing = face;
                break;
            }
        }

        IBlockState state = geod.getDefaultState().withProperty(BlockRiftGeod.FACING, facing);
        if (!world.getBlockState(pos).getMaterial().isReplaceable() && !world.isAirBlock(pos)) return false;
        return world.setBlockState(pos, state, 3);
    }

    private void spawnOrRefreshTaintSeed() {
        try {
            EntityTaintSeed seed = new EntityTaintSeed(world);
            seed.setPosition(center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5);

            // Сделаем его “как объект”, а не как моб
            seed.enablePersistence();           // не деспавнится
            seed.setSilent(true);               // без звуков
            seed.setNoAI(true);                 // не двигается и не атакует (в 1.12 у EntityLiving есть setNoAI)
            seed.setEntityInvulnerable(true);   // чтобы случайно не убили
            seed.boost = 999;                   // усиленный (по желанию)

            world.spawnEntity(seed);
            seedEntityId = seed.getUniqueID();

            LOG.info("[FluxAnomaly] Seed spawned uuid={} at {}", seedEntityId, center);
        } catch (Throwable t) {
            LOG.error("[FluxAnomaly] Failed to spawn EntityTaintSeed at {}", center, t);
        }
    }

    private void ensureSeedAlive() {
        if (seedEntityId == null) return;
        Entity e = world.getPlayerEntityByUUID(seedEntityId); // не найдёт, это игроки
        // Поэтому ищем “ручками” через loadedEntityList
        for (Entity ent : world.loadedEntityList) {
            if (seedEntityId.equals(ent.getUniqueID())) return;
        }
        // Не нашли — восстановим
        LOG.warn("[FluxAnomaly] Seed missing, respawning...");
        spawnOrRefreshTaintSeed();
    }

    private void killSeedIfPresent() {
        if (seedEntityId == null) return;
        Entity target = null;
        for (Entity ent : world.loadedEntityList) {
            if (seedEntityId.equals(ent.getUniqueID())) {
                target = ent;
                break;
            }
        }
        if (target != null) {
            target.setDead();
            LOG.info("[FluxAnomaly] Seed killed uuid={}", seedEntityId);
        }
        seedEntityId = null;
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
        resourcesPlaced = tag.getBoolean("resPlaced");

        if (tag.hasUniqueId("seedId")) seedEntityId = tag.getUniqueId("seedId");
        else seedEntityId = null;
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
        tag.setBoolean("resPlaced", resourcesPlaced);

        if (seedEntityId != null) tag.setUniqueId("seedId", seedEntityId);
    }
}
