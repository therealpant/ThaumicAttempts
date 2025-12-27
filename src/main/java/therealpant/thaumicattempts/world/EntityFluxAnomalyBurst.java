// src/main/java/therealpant/thaumicattempts/world/anomaly/EntityFluxAnomalyBurst.java
package therealpant.thaumicattempts.world;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import thaumcraft.common.blocks.world.taint.TaintHelper;
import thaumcraft.common.entities.monster.tainted.EntityTaintSeed;

import java.util.Random;
import java.util.UUID;

public class EntityFluxAnomalyBurst extends Entity {

    private static final Logger LOG = LogManager.getLogger("ThaumicAttempts|FluxAnomaly");

    private UUID anomalyId = UUID.randomUUID();

    private BlockPos center = BlockPos.ORIGIN;

    private int radiusBlocks = 20;     // 2–3 чанка ковром
    private int totalSpreads = 16500;   // сколько spreadFibres всего
    private int budgetPerTick = 220;   // сколько spreadFibres за тик

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
        this.radiusBlocks = Math.max(8, radiusBlocks);
        this.totalSpreads = Math.max(0, totalSpreads);
        this.budgetPerTick = Math.max(1, budgetPerTick);
        this.remainingSpreads = this.totalSpreads;

        this.setPosition(center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5);
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

            LOG.info("[FluxAnomaly] AWAKEN id={} dim={} center={} radius={} totalSpreads={} budgetPerTick={}",
                    anomalyId, world.provider.getDimension(), center, radiusBlocks, totalSpreads, budgetPerTick);

            // 1) Спавним настоящий EntityTaintSeed (обязательно!)
            spawnOrRefreshTaintSeed();

            // 2) Записываем seed позицию (не строго обязательно, но норм)
            try {
                TaintHelper.addTaintSeed(world, center);
            } catch (Throwable t) {
                LOG.error("[FluxAnomaly] addTaintSeed failed at {}", center, t);
            }
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
        int dx, dz;
        while (true) {
            dx = rnd.nextInt(radiusBlocks * 2 + 1) - radiusBlocks;
            dz = rnd.nextInt(radiusBlocks * 2 + 1) - radiusBlocks;
            int d2 = dx * dx + dz * dz;
            if (d2 > radiusBlocks * radiusBlocks) continue;

            float falloff = 1.0f - (float) d2 / (float) (radiusBlocks * radiusBlocks);
            if (rnd.nextFloat() <= Math.max(0.15f, falloff)) break;
        }
        return new BlockPos(center.getX() + dx, 0, center.getZ() + dz);
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound tag) {
        if (tag.hasUniqueId("anId")) anomalyId = tag.getUniqueId("anId");
        awakened = tag.getBoolean("awake");

        center = new BlockPos(tag.getInteger("cx"), tag.getInteger("cy"), tag.getInteger("cz"));

        radiusBlocks = Math.max(8, tag.getInteger("rad"));
        totalSpreads = Math.max(0, tag.getInteger("total"));
        budgetPerTick = Math.max(1, tag.getInteger("budget"));
        remainingSpreads = Math.max(0, tag.getInteger("remain"));

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

        if (seedEntityId != null) tag.setUniqueId("seedId", seedEntityId);
    }
}
