package therealpant.thaumicattempts.world.block;

import net.minecraft.block.Block;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import thaumcraft.common.entities.monster.tainted.EntityTaintSeed;
import thaumcraft.common.entities.monster.tainted.EntityTaintSeedPrime;
import therealpant.thaumicattempts.world.EntityFluxAnomalyBurst;
import therealpant.thaumicattempts.world.tile.AnomalyLinkedTile;

import javax.annotation.Nullable;
import java.util.Random;
import java.util.UUID;

/**
 * Общая логика для ресурсных блоков флюкс-аномалий.
 */
public final class FluxResourceHelper {

    private FluxResourceHelper() {}

    public static int countBlocks(World world, BlockPos center, Block target, int radius) {
        if (world == null || center == null || target == null) return 0;

        int count = 0;
        for (BlockPos pos : BlockPos.getAllInBoxMutable(
                center.add(-radius, -radius, -radius),
                center.add(radius, radius, radius)
        )) {
            if (world.getBlockState(pos).getBlock() == target) {
                count++;
            }
        }
        return count;
    }

    public static boolean shouldReproduce(Random rand, int count, int limit, double baseChance) {
        if (rand == null || limit <= 0) return false;

        double density = count / (double) limit;
        double multiplier = 1.0 - density * density;
        if (multiplier <= 0) return false;

        double chance = baseChance * multiplier;
        return rand.nextDouble() < chance;
    }

    public static BlockPos randomOffset(BlockPos origin, Random rand, int horizontalRadius, int verticalRadius) {
        if (origin == null || rand == null) return origin;

        int dx = rand.nextInt(horizontalRadius * 2 + 1) - horizontalRadius;
        int dy = rand.nextInt(verticalRadius * 2 + 1) - verticalRadius;
        int dz = rand.nextInt(horizontalRadius * 2 + 1) - horizontalRadius;
        return origin.add(dx, dy, dz);
    }

    public static void damageNearestSeed(World world, BlockPos pos, float amount) {
        if (world == null || pos == null || amount <= 0) return;

        AxisAlignedBB box = new AxisAlignedBB(pos).grow(32);
        EntityTaintSeed nearestSeed = null;
        double nearestSq = Double.MAX_VALUE;

        for (EntityTaintSeed seed : world.getEntitiesWithinAABB(EntityTaintSeed.class, box)) {
            if (seed.isDead) continue;
            double distSq = seed.getDistanceSq(
                    pos.getX() + 0.5,
                    pos.getY() + 0.5,
                    pos.getZ() + 0.5
            );
            if (distSq < nearestSq) {
                nearestSq = distSq;
                nearestSeed = seed;
            }
        }

        EntityTaintSeedPrime nearestPrime = null;
        for (EntityTaintSeedPrime prime : world.getEntitiesWithinAABB(EntityTaintSeedPrime.class, box)) {
            if (prime.isDead) continue;
            double distSq = prime.getDistanceSq(
                    pos.getX() + 0.5,
                    pos.getY() + 0.5,
                    pos.getZ() + 0.5
            );
            if (distSq < nearestSq) {
                nearestSq = distSq;
                nearestPrime = prime;
                nearestSeed = null;
            }
        }

        if (nearestPrime != null) {
            nearestPrime.attackEntityFrom(DamageSource.MAGIC, amount);
        } else if (nearestSeed != null) {
            nearestSeed.attackEntityFrom(DamageSource.MAGIC, amount);
        }
    }

    public static void linkBlockToAnomaly(World world, BlockPos pos, @Nullable UUID anomalyId, @Nullable BlockPos seedPos) {
        if (world == null || pos == null) return;
        if (!world.isBlockLoaded(pos)) return;
        if (!(world.getTileEntity(pos) instanceof AnomalyLinkedTile)) return;

        AnomalyLinkedTile linked = (AnomalyLinkedTile) world.getTileEntity(pos);
        if (linked == null) return;
        linked.setAnomalyLink(anomalyId, seedPos);
    }

    @Nullable
    public static EntityFluxAnomalyBurst findAnomaly(World world, @Nullable UUID anomalyId, @Nullable BlockPos seedPos) {
        if (world == null || (anomalyId == null && seedPos == null)) return null;
        for (net.minecraft.entity.Entity entity : world.loadedEntityList) {
            if (!(entity instanceof EntityFluxAnomalyBurst)) continue;
            EntityFluxAnomalyBurst anomaly = (EntityFluxAnomalyBurst) entity;
            if (anomalyId != null && anomalyId.equals(anomaly.getAnomalyId())) return anomaly;
            BlockPos anomalySeed = anomaly.getSeedPos();
            if (seedPos != null && anomalySeed != null && seedPos.equals(anomalySeed)) return anomaly;
        }
        return null;
    }

    public static int getOvergrowthCap(Block block) {
        if (block instanceof BlockRiftBush) return 8;
        if (block instanceof BlockAnomalyStone) return 7;
        if (block instanceof BlockRiftGeod) return 5;
        return 0;
    }
}