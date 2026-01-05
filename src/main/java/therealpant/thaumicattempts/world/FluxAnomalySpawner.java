package therealpant.thaumicattempts.world;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.items.CapabilityItemHandler;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.api.FluxAnomalyApi;
import therealpant.thaumicattempts.api.FluxAnomalyPresets;
import therealpant.thaumicattempts.api.FluxAnomalySettings;
import therealpant.thaumicattempts.api.FluxAnomalyTier;
import therealpant.thaumicattempts.world.data.TAWorldFluxData;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Random;

@Mod.EventBusSubscriber(modid = ThaumicAttempts.MODID)
public final class FluxAnomalySpawner {

    private static final int CHECK_PERIOD = 1200;
    private static final int MIN_WORLD_AGE = 1 * 24000;
    private static final int MIN_INHABITED_TICKS = 20 * 60 * 20;
    private static final double SURFACE_CHANCE = 0.55;

    private FluxAnomalySpawner() {}

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        World world = event.world;
        if (world == null || world.isRemote) return;
        if (event.phase != TickEvent.Phase.END) return;
        if (world.provider.getDimension() != 0) return;

        long now = world.getTotalWorldTime();
        if (now < MIN_WORLD_AGE || (now % CHECK_PERIOD) != 0) return;
        if (world.playerEntities.isEmpty()) return;

        TAWorldFluxData data = TAWorldFluxData.get(world);
        if (!data.canTrySpawn(world)) return;

        double chance = stageChance(data.stage);
        if (chance <= 0 || world.rand.nextDouble() >= chance) return;

        EntityPlayer player = pickPlayer(world.rand, world.playerEntities);
        if (player == null) return;

        BlockPos column = findSpawnColumn(world, player, world.rand);
        if (column == null) return;

        FluxAnomalyTier tier = pickTier(data.stage, world.rand);
        BlockPos spawnPos = findSpawnPosition(world, column, tier, world.rand);
        if (spawnPos == null) return;

        FluxAnomalySettings settings = FluxAnomalyPresets.createSettings(tier);
        FluxAnomalyApi.spawn(world, spawnPos, settings);

        long cooldownTicks = pickCooldownTicks(data.stage, world.rand);
        data.nextAnomalySpawnTime = now + cooldownTicks;
        data.markDirty();
    }

    private static double stageChance(int stage) {
        switch (stage) {
            case 1:
                return 0.20;
            case 2:
                return 0.35;
            case 3:
                return 0.50;
            default:
                return 0.0;
        }
    }

    @Nullable
    private static EntityPlayer pickPlayer(Random rand, List<EntityPlayer> players) {
        if (players == null || players.isEmpty()) return null;
        return players.get(rand.nextInt(players.size()));
    }

    @Nullable
    private static BlockPos findSpawnColumn(World world, EntityPlayer player, Random rand) {
        if (player == null) return null;

        BlockPos spawn = world.getSpawnPoint();
        for (int i = 0; i < 50; i++) {
            double angle = rand.nextDouble() * Math.PI * 2;
            double radius = 512 + rand.nextDouble() * (4096 - 512);
            int x = MathHelper.floor(player.posX + Math.cos(angle) * radius);
            int z = MathHelper.floor(player.posZ + Math.sin(angle) * radius);

            if (spawn != null) {
                double dx = x + 0.5 - spawn.getX();
                double dz = z + 0.5 - spawn.getZ();
                if (dx * dx + dz * dz < (12 * 16) * (12 * 16)) continue;
            }

            BlockPos column = new BlockPos(x, 0, z);
            Chunk chunk = world.getChunk(column);
            if (chunk == null) continue;
            if (chunk.getInhabitedTime() > MIN_INHABITED_TICKS) continue;

            if (world.getClosestPlayer(x + 0.5, 64, z + 0.5, 256, false) != null) continue;
            if (hasBaseSignals(world, column, rand)) continue;

            return column;
        }
        return null;
    }

    private static boolean hasBaseSignals(World world, BlockPos center, Random rand) {
        int hits = 0;
        for (int i = 0; i < 30; i++) {
            BlockPos sample = randomSample(center, rand, 64, world.getActualHeight());
            if (!world.isBlockLoaded(sample)) continue;

            TileEntity te = world.getTileEntity(sample);
            if (te != null && (te instanceof net.minecraft.inventory.IInventory
                    || te.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null))) {
                return true;
            }
        }

        for (int i = 0; i < 30; i++) {
            BlockPos sample = randomSample(center, rand, 64, world.getActualHeight());
            if (!world.isBlockLoaded(sample)) continue;
            Block block = world.getBlockState(sample).getBlock();
            if (block == Blocks.TORCH || block == Blocks.PLANKS || block == Blocks.COBBLESTONE
                    || block == Blocks.BRICK_BLOCK
                    || block == Blocks.GLASS || block == Blocks.GLASS_PANE) {
                hits++;
                if (hits >= 6) return true;
            }
        }
        return false;
    }

    private static BlockPos randomSample(BlockPos center, Random rand, int radius, int heightLimit) {
        int dx = rand.nextInt(radius * 2 + 1) - radius;
        int dz = rand.nextInt(radius * 2 + 1) - radius;
        int dy = MathHelper.clamp(rand.nextInt(heightLimit), 1, heightLimit - 1);
        return center.add(dx, dy, dz);
    }

    private static FluxAnomalyTier pickTier(int stage, Random rand) {
        double r = rand.nextDouble();
        if (stage == 1) {
            return FluxAnomalyTier.SURFACE;
        }
        if (stage == 2) {
            return r < 0.70 ? FluxAnomalyTier.SURFACE : FluxAnomalyTier.SHALLOW;
        }
        if (r < SURFACE_CHANCE) return FluxAnomalyTier.SURFACE;
        if (r < SURFACE_CHANCE + 0.35) return FluxAnomalyTier.SHALLOW;
        return FluxAnomalyTier.DEEP;
    }

    @Nullable
    private static BlockPos findSpawnPosition(World world, BlockPos column, FluxAnomalyTier tier, Random rand) {
        switch (tier) {
            case SURFACE:
                return findSurface(world, column);
            case SHALLOW:
                return findShallow(world, column, rand);
            case DEEP:
                return findDeep(world, column, rand);
            default:
                return null;
        }
    }

    @Nullable
    private static BlockPos findSurface(World world, BlockPos column) {
        BlockPos top = world.getTopSolidOrLiquidBlock(column);
        if (top.getY() <= 1) return null;

        Block block = world.getBlockState(top).getBlock();
        Material mat = world.getBlockState(top).getMaterial();
        if (mat.isLiquid()) return null;
        BlockPos above = top.up();
        if (!world.isAirBlock(above)) return null;

        return above;
    }

    @Nullable
    private static BlockPos findShallow(World world, BlockPos column, Random rand) {
        for (int i = 0; i < 20; i++) {
            int y = 20 + rand.nextInt(36);
            BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
            if (!world.isBlockLoaded(pos)) continue;
            if (world.getBlockState(pos).getBlock() != Blocks.STONE) continue;

            for (net.minecraft.util.EnumFacing face : net.minecraft.util.EnumFacing.values()) {
                BlockPos air = pos.offset(face);
                if (world.isAirBlock(air)) {
                    return air;
                }
            }
        }
        return null;
    }

    @Nullable
    private static BlockPos findDeep(World world, BlockPos column, Random rand) {
        for (int i = 0; i < 30; i++) {
            int y = 1 + rand.nextInt(12);
            BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
            if (!world.isBlockLoaded(pos)) continue;

            Material mat = world.getBlockState(pos).getMaterial();
            if (mat.isLiquid() || mat.isReplaceable() || mat == Material.AIR) continue;

            for (net.minecraft.util.EnumFacing face : net.minecraft.util.EnumFacing.values()) {
                BlockPos air = pos.offset(face);
                if (world.isAirBlock(air)) {
                    return air;
                }
            }
        }
        return null;
    }

    private static long pickCooldownTicks(int stage, Random rand) {
        int days;
        switch (stage) {
            case 1:
                days = 3 + rand.nextInt(4); // 3..6
                break;
            case 2:
                days = 2 + rand.nextInt(3); // 2..4
                break;
            case 3:
                days = 1 + rand.nextInt(3); // 1..3
                break;
            default:
                days = 3;
        }
        return days * 24000L;
    }
}