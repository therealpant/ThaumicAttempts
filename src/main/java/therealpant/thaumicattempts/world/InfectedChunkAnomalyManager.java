package therealpant.thaumicattempts.world;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.items.CapabilityItemHandler;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.api.FluxAnomalyApi;
import therealpant.thaumicattempts.api.FluxAnomalyPresets;
import therealpant.thaumicattempts.api.FluxAnomalySettings;
import therealpant.thaumicattempts.api.FluxAnomalyTier;
import therealpant.thaumicattempts.world.data.TAInfectedChunksData;
import therealpant.thaumicattempts.world.data.TAWorldFluxData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = ThaumicAttempts.MODID)
public final class InfectedChunkAnomalyManager {

    public static final int CHECK_PERIOD = 200;
    public static final int SAFE_PLAYER_RADIUS = 64;
    public static final int MIN_INHABITED_TICKS = 144000;
    private static final int MAX_TRIES = 20;

    private static final double STAGE1_INFECTION_CHANCE = 0.003;
    private static final double STAGE2_INFECTION_CHANCE = 0.008;
    private static final double STAGE3_INFECTION_CHANCE = 0.015;

    private InfectedChunkAnomalyManager() {}

    @SubscribeEvent
    public static void onChunkPopulate(PopulateChunkEvent.Post event) {
        World world = event.getWorld();
        if (world == null || world.isRemote) return;
        if (world.provider == null || world.provider.getDimension() != 0) return;

        TAWorldFluxData fluxData = TAWorldFluxData.get(world);
        if (fluxData.stage < 1) return;

        long chunkKey = TAInfectedChunksData.pack(event.getChunkX(), event.getChunkZ());
        TAInfectedChunksData data = TAInfectedChunksData.get(world);
        if (data.isInfected(chunkKey)) return;

        double chance = infectionChance(fluxData.stage);
        if (chance <= 0) return;

        if (event.getRand().nextDouble() < chance) {
            data.addInfectedChunk(chunkKey);
        }
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        World world = event.world;
        if (!(world instanceof WorldServer)) return;
        if (world.isRemote) return;
        if (event.phase != TickEvent.Phase.END) return;
        if (world.provider == null || world.provider.getDimension() != 0) return;

        WorldServer serverWorld = (WorldServer) world;
        TAInfectedChunksData infectedData = TAInfectedChunksData.get(world);
        long now = world.getTotalWorldTime();
        infectedData.lastManagerTickTime = now;

        TAWorldFluxData fluxData = TAWorldFluxData.get(world);
        if ((now % CHECK_PERIOD) != 0) {
            return;
        }

        infectedData.markDirty();

        infectedData.lastActivationAttemptTime = now;

        if (fluxData.stage < 1) {
            recordAttempt(infectedData, "STAGE_TOO_LOW", 0);
            return;
        }

        if (world.playerEntities.isEmpty()) {
            recordAttempt(infectedData, "NO_PLAYERS", 0);
            return;
        }

        Collection<Chunk> loadedChunks = getLoadedChunks(serverWorld);
        if (loadedChunks.isEmpty()) {
            recordAttempt(infectedData, "NO_LOADED_CHUNKS", 0);
            return;
        }

        List<Chunk> infectedLoaded = new ArrayList<>();
        for (Chunk chunk : loadedChunks) {
            if (chunk == null) continue;
            long key = TAInfectedChunksData.pack(chunk.x, chunk.z);
            if (infectedData.isInfected(key)) {
                infectedLoaded.add(chunk);
            }
        }

        if (infectedLoaded.isEmpty()) {
            recordAttempt(infectedData, "NO_INFECTED_LOADED", 0);
            return;
        }

        int checked = 0;
        String failReason = "NO_CANDIDATE_PASSED_FILTERS";
        Random rand = world.rand;

        while (!infectedLoaded.isEmpty() && checked < MAX_TRIES) {
            int idx = rand.nextInt(infectedLoaded.size());
            Chunk chunk = infectedLoaded.remove(idx);
            checked++;

            if (!passesActivationFilters(world, chunk, infectedData)) {
                continue;
            }

            if (spawnInChunk(serverWorld, chunk, fluxData, infectedData)) {
                recordAttempt(infectedData, "SUCCESS", checked);
                return;
            } else {
                failReason = "SPAWN_FAIL";
                break;
            }
        }

        recordAttempt(infectedData, failReason, checked);
    }

    public static void onSeedSpawned(World world, UUID seedId, long chunkKey) {
        if (world == null || world.isRemote) return;
        if (chunkKey == Long.MIN_VALUE) return;
        TAInfectedChunksData data = TAInfectedChunksData.get(world);
        data.trackSeed(seedId, chunkKey);
    }

    public static void onAnomalyEnded(World world, @Nullable UUID seedId, @Nullable UUID anomalyId, long chunkKey) {
        if (!(world instanceof WorldServer)) return;
        WorldServer serverWorld = (WorldServer) world;
        TAInfectedChunksData data = TAInfectedChunksData.get(world);

        Long knownChunk = resolveChunkKey(data, seedId, anomalyId, chunkKey);
        if (knownChunk == null) return;

        if (seedId != null) data.removeSeed(seedId);
        if (anomalyId != null) data.removeAnomaly(anomalyId);

        data.unmarkChunkActive(knownChunk);
        data.removeInfectedChunk(knownChunk);

        migrateInfection(serverWorld, data);
    }

    private static void migrateInfection(WorldServer world, TAInfectedChunksData data) {
        TAWorldFluxData fluxData = TAWorldFluxData.get(world);
        if (fluxData.stage < 1) return;

        Collection<Chunk> loadedChunks = getLoadedChunks(world);
        if (loadedChunks.isEmpty()) return;

        List<Chunk> candidates = new ArrayList<>();
        for (Chunk chunk : loadedChunks) {
            long key = TAInfectedChunksData.pack(chunk.x, chunk.z);
            if (data.isInfected(key)) continue;
            if (passesActivationFilters(world, chunk, data)) {
                candidates.add(chunk);
            }
        }

        if (candidates.isEmpty()) return;

        Chunk target = candidates.get(world.rand.nextInt(candidates.size()));
        long targetKey = TAInfectedChunksData.pack(target.x, target.z);
        data.addInfectedChunk(targetKey);
    }

    private static boolean spawnInChunk(WorldServer world, Chunk chunk, TAWorldFluxData fluxData, TAInfectedChunksData data) {
        long chunkKey = TAInfectedChunksData.pack(chunk.x, chunk.z);
        BlockPos column = randomColumnInChunk(chunk, world.rand);

        FluxAnomalyTier tier = pickTier(fluxData.stage, world.rand);
        BlockPos spawnPos = findSpawnPosition(world, column, tier, world.rand);
        if (spawnPos == null) {
            return false;
        }

        FluxAnomalySettings settings = FluxAnomalyPresets.createSettings(tier);
        try {
            EntityFluxAnomalyBurst anomaly = FluxAnomalyApi.spawn(world, spawnPos, settings);
            anomaly.setHostingChunkKey(chunkKey);
            data.addInfectedChunk(chunkKey);
            data.markChunkActive(chunkKey);
            data.trackAnomaly(anomaly.getUniqueID(), chunkKey);
            return true;
        } catch (Exception ex) {
            ThaumicAttempts.LOGGER.error("Failed to spawn flux anomaly in chunk ({}, {})", chunk.x, chunk.z, ex);
            return false;
        }
    }

    private static boolean passesActivationFilters(World world, Chunk chunk, TAInfectedChunksData data) {
        long chunkKey = TAInfectedChunksData.pack(chunk.x, chunk.z);
        if (data.isActive(chunkKey)) return false;
        if (chunk.getInhabitedTime() > MIN_INHABITED_TICKS) return false;

        double cx = chunk.x * 16 + 8;
        double cz = chunk.z * 16 + 8;
        EntityPlayer nearest = world.getClosestPlayer(cx, 64, cz, SAFE_PLAYER_RADIUS, false);
        if (nearest != null) return false;

        return !hasInventories(chunk);
    }

    private static boolean hasInventories(Chunk chunk) {
        if (chunk == null) return false;
        for (TileEntity te : chunk.getTileEntityMap().values()) {
            if (te == null) continue;
            if (te instanceof net.minecraft.inventory.IInventory) return true;
            if (te.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null)) return true;
        }
        return false;
    }

    private static BlockPos randomColumnInChunk(Chunk chunk, Random rand) {
        int x = chunk.x * 16 + rand.nextInt(16);
        int z = chunk.z * 16 + rand.nextInt(16);
        return new BlockPos(x, 0, z);
    }

    private static FluxAnomalyTier pickTier(int stage, Random rand) {
        double r = rand.nextDouble();
        if (stage == 1) {
            return FluxAnomalyTier.SURFACE;
        }
        if (stage == 2) {
            return r < 0.70 ? FluxAnomalyTier.SURFACE : FluxAnomalyTier.SHALLOW;
        }
        if (r < 0.55) return FluxAnomalyTier.SURFACE;
        if (r < 0.90) return FluxAnomalyTier.SHALLOW;
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

        if (world.getBlockState(top).getMaterial().isLiquid()) return null;
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
            if (world.getBlockState(pos).getBlock() != net.minecraft.init.Blocks.STONE) continue;

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

            net.minecraft.block.material.Material mat = world.getBlockState(pos).getMaterial();
            if (mat.isLiquid() || mat.isReplaceable() || mat == net.minecraft.block.material.Material.AIR) continue;

            for (net.minecraft.util.EnumFacing face : net.minecraft.util.EnumFacing.values()) {
                BlockPos air = pos.offset(face);
                if (world.isAirBlock(air)) {
                    return air;
                }
            }
        }
        return null;
    }

    private static Collection<Chunk> getLoadedChunks(WorldServer world) {
        ChunkProviderServer provider = world.getChunkProvider();
        Collection<Chunk> loaded = provider.getLoadedChunks();
        return loaded == null ? Collections.emptyList() : loaded;
    }

    private static double infectionChance(int stage) {
        switch (stage) {
            case 1:
                return STAGE1_INFECTION_CHANCE;
            case 2:
                return STAGE2_INFECTION_CHANCE;
            case 3:
            default:
                return STAGE3_INFECTION_CHANCE;
        }
    }

    private static void recordAttempt(TAInfectedChunksData data, String reason, int candidates) {
        data.lastActivationFailReason = reason;
        data.lastCandidatesChecked = candidates;
        data.markDirty();
    }

    @Nullable
    private static Long resolveChunkKey(TAInfectedChunksData data, @Nullable UUID seedId, @Nullable UUID anomalyId, long chunkKey) {
        if (chunkKey != Long.MIN_VALUE) return chunkKey;
        if (seedId != null) {
            Long found = data.findChunkForSeed(seedId);
            if (found != null) return found;
        }
        if (anomalyId != null) {
            Long found = data.findChunkForAnomaly(anomalyId);
            if (found != null) return found;
        }
        return null;
    }
}