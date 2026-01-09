package therealpant.thaumicattempts.world;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.common.BiomeDictionary.Type;
import net.minecraftforge.event.world.ChunkEvent;
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

    public static final int CHECK_PERIOD = 800;
    public static final int SAFE_PLAYER_RADIUS = 164;
    public static final int MIN_INHABITED_TICKS = 144000;
    public static final int MIN_SPACING_CHUNKS = 10;
    public static final int SCHEDULE_MIN_VIEW_OFFSET = 2;
    public static final int SCHEDULE_MAX_EXTRA = 24;
    public static final int SCHEDULE_ATTEMPTS = 24;
    public static final int SCHEDULE_CLEANUP_PERIOD = 1200;
    public static final int SCHEDULE_TTL_TICKS = 36000;
    public static final int SCHEDULE_HARD_CAP = 256;

    private static final double ACTIVATION_CHANCE = 0.25;
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
    public static void onChunkLoad(ChunkEvent.Load event) {
        World world = event.getWorld();
        if (!(world instanceof WorldServer)) return;
        if (world.isRemote) return;
        if (world.provider == null || world.provider.getDimension() != 0) return;

        WorldServer serverWorld = (WorldServer) world;
        Chunk chunk = event.getChunk();
        int cx = chunk.x;
        int cz = chunk.z;
        long chunkKey = chunkKey(cx, cz);

        TAInfectedChunksData data = TAInfectedChunksData.get(world);
        if (!data.isScheduled(chunkKey)) return;

        if (hasAnyWithinRadiusChunks(data.getInfectedChunkKeys(), cx, cz, MIN_SPACING_CHUNKS)) return;
        if (hasAnyWithinRadiusChunks(data.getActiveChunkKeys(), cx, cz, MIN_SPACING_CHUNKS)) return;
        if (isPlayerWithinSafeRadius(world, cx, cz)) return;
        if (!passesActivationFilters(world, chunk, data)) return;

        if (!data.consumeSchedule(chunkKey)) return;

        TAWorldFluxData fluxData = TAWorldFluxData.get(world);
        spawnInChunk(serverWorld, chunk, fluxData, data);
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
        if ((now % SCHEDULE_CLEANUP_PERIOD) == 0) {
            infectedData.cleanupSchedules(now, SCHEDULE_TTL_TICKS, SCHEDULE_HARD_CAP);
        }
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

        if (world.rand.nextDouble() >= ACTIVATION_CHANCE) {
            recordAttempt(infectedData, "CHANCE_FAILED", 0);
            return;
        }

        Random rand = world.rand;
        EntityPlayer player = world.playerEntities.get(rand.nextInt(world.playerEntities.size()));
        int viewDistance = serverWorld.getMinecraftServer().getPlayerList().getViewDistance();
        int minDist = viewDistance + SCHEDULE_MIN_VIEW_OFFSET;
        int maxDist = minDist + SCHEDULE_MAX_EXTRA;

        int checked = 0;
        String failReason = "NO_CANDIDATE_PASSED_FILTERS";


        while (checked < SCHEDULE_ATTEMPTS) {
            checked++;

            double angle = rand.nextDouble() * Math.PI * 2.0;
            int distance = minDist + rand.nextInt(maxDist - minDist + 1);
            int dx = MathHelper.floor(Math.cos(angle) * distance);
            int dz = MathHelper.floor(Math.sin(angle) * distance);
            int cx = player.chunkCoordX + dx;
            int cz = player.chunkCoordZ + dz;
            long key = chunkKey(cx, cz);

            if (infectedData.isInfected(key) || infectedData.isActive(key) || infectedData.isScheduled(key)) {
                continue;
            }

            if (hasAnyWithinRadiusChunks(infectedData.getInfectedChunkKeys(), cx, cz, MIN_SPACING_CHUNKS)) {
                continue;
            }
            if (hasAnyWithinRadiusChunks(infectedData.getActiveChunkKeys(), cx, cz, MIN_SPACING_CHUNKS)) {
                continue;
            }
            if (isPlayerWithinSafeRadius(world, cx, cz)) {
                continue;
            }

            infectedData.scheduleChunk(key, now);
            recordAttempt(infectedData, "SCHEDULED", checked);
            return;
        }

        recordAttempt(infectedData, failReason, checked);
    }

    public static void onSeedSpawned(World world, UUID seedId, long chunkKey, BlockPos seedPos) {
        if (world == null || world.isRemote) return;
        if (chunkKey == Long.MIN_VALUE) return;
        TAInfectedChunksData data = TAInfectedChunksData.get(world);
        data.trackSeed(seedId, chunkKey, seedPos);
    }

    public static void onAnomalyEnded(World world, @Nullable UUID seedId, @Nullable UUID anomalyId, long chunkKey) {
        if (!(world instanceof WorldServer)) return;
        WorldServer serverWorld = (WorldServer) world;
        TAInfectedChunksData data = TAInfectedChunksData.get(world);

        Long knownChunk = resolveChunkKey(data, seedId, anomalyId, chunkKey);
        if (knownChunk == null) return;

        if (seedId != null) data.removeSeed(seedId);
        if (anomalyId != null) data.removeAnomaly(anomalyId);
        data.removeSeedsForChunk(knownChunk);

        data.unmarkChunkActive(knownChunk);
        data.removeInfectedChunk(knownChunk);
        data.clearChunkTracking(knownChunk);

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

    private static ActivationResult spawnInChunk(WorldServer world, Chunk chunk, TAWorldFluxData fluxData, TAInfectedChunksData data) {
        long chunkKey = TAInfectedChunksData.pack(chunk.x, chunk.z);
        BlockPos column = randomColumnInChunk(chunk, world.rand);

        FluxAnomalyTier tier = pickTier(fluxData.stage, world.rand);
        SpawnLocation location = findSpawnPosition(world, column, tier, world.rand);
        if (location.pos == null) {
            return ActivationResult.fail(location.reason);
        }

        FluxAnomalySettings settings = FluxAnomalyPresets.createSettings(tier);
        try {
            EntityFluxAnomalyBurst anomaly = FluxAnomalyApi.spawn(world, location.pos, settings);
            anomaly.setHostingChunkKey(chunkKey);
            data.addInfectedChunk(chunkKey);
            data.markChunkActive(chunkKey);
            data.setActiveChunkTier(chunkKey, tier);
            data.trackAnomaly(anomaly.getUniqueID(), chunkKey);
            return ActivationResult.success();
        } catch (Exception ex) {
            ThaumicAttempts.LOGGER.error("Failed to spawn flux anomaly in chunk ({}, {})", chunk.x, chunk.z, ex);
            return ActivationResult.fail("SPAWN_FAIL");
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

    private static long chunkKey(int cx, int cz) {
        return TAInfectedChunksData.pack(cx, cz);
    }

    private static int chunkX(long key) {
        return (int) (key >> 32);
    }

    private static int chunkZ(long key) {
        return (int) key;
    }

    private static boolean hasAnyWithinRadiusChunks(Iterable<Long> keys, int cx, int cz, int rChunks) {
        int r2 = rChunks * rChunks;
        for (Long k : keys) {
            int ox = chunkX(k);
            int oz = chunkZ(k);
            int dx = ox - cx;
            int dz = oz - cz;
            if (dx*dx + dz*dz <= r2) return true;
        }
        return false;
    }

    private static boolean isPlayerWithinSafeRadius(World world, int cx, int cz) {
        double x = cx * 16 + 8;
        double z = cz * 16 + 8;
        EntityPlayer nearest = world.getClosestPlayer(x, 64, z, SAFE_PLAYER_RADIUS, false);
        return nearest != null;
    }


    @Nullable
    private static SpawnLocation findSpawnPosition(World world, BlockPos column, FluxAnomalyTier tier, Random rand) {
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
    private static SpawnLocation findSurface(World world, BlockPos column) {
        BlockPos top = world.getTopSolidOrLiquidBlock(column);
        if (top.getY() <= 1) return SpawnLocation.invalid("SPAWN_POS_INVALID");

        Biome biome = world.getBiome(top);
        if (biome == null || !BiomeDictionary.hasType(biome, Type.FOREST)) {
            return SpawnLocation.invalid("BIOME_NOT_FOREST");
        }

        if (world.getBlockState(top).getMaterial().isLiquid()) return SpawnLocation.invalid("SPAWN_POS_INVALID");
        BlockPos above = top.up();
        if (!world.isAirBlock(above)) return SpawnLocation.invalid("SPAWN_POS_INVALID");

        return SpawnLocation.valid(above);
    }

    @Nullable
    private static SpawnLocation findShallow(World world, BlockPos column, Random rand) {
        for (int i = 0; i < 20; i++) {
            int y = 20 + rand.nextInt(36);
            BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
            if (!world.isBlockLoaded(pos)) continue;
            if (y > Math.max(1, world.getSeaLevel() - 5)) continue;
            SpawnLocation location = tryFindCaveAir(world, pos);
            if (location.pos != null) return location;
        }
        return SpawnLocation.invalid("SPAWN_POS_INVALID");
    }

    @Nullable
    private static SpawnLocation findDeep(World world, BlockPos column, Random rand) {
        for (int i = 0; i < 30; i++) {
            int y = 1 + rand.nextInt(24);
            BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
            if (!world.isBlockLoaded(pos)) continue;

            SpawnLocation location = tryFindCaveAir(world, pos);
            if (location.pos != null) return location;
        }
        return SpawnLocation.invalid("SPAWN_POS_INVALID");
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

    private static SpawnLocation tryFindCaveAir(World world, BlockPos stonePos) {
        if (world.getBlockState(stonePos).getBlock() != net.minecraft.init.Blocks.STONE) {
            return SpawnLocation.invalid("SPAWN_POS_INVALID");
        }

        for (net.minecraft.util.EnumFacing face : net.minecraft.util.EnumFacing.values()) {
            BlockPos air = stonePos.offset(face);
            if (!world.isAirBlock(air)) continue;
            if (air.getY() < 1 || air.getY() >= world.getHeight()) continue;
            net.minecraft.block.state.IBlockState airState = world.getBlockState(air);
            if (airState.getMaterial().isLiquid()) continue;
            net.minecraft.block.state.IBlockState neighbor = world.getBlockState(stonePos);
            if (!neighbor.getMaterial().isSolid()) continue;
            return SpawnLocation.valid(air);
        }
        return SpawnLocation.invalid("SPAWN_POS_INVALID");
    }

    private static final class ActivationResult {
        final boolean success;
        final String reason;

        private ActivationResult(boolean success, String reason) {
            this.success = success;
            this.reason = reason == null ? "" : reason;
        }

        static ActivationResult success() {
            return new ActivationResult(true, "SUCCESS");
        }

        static ActivationResult fail(String reason) {
            return new ActivationResult(false, reason == null ? "SPAWN_FAIL" : reason);
        }
    }

    private static final class SpawnLocation {
        final BlockPos pos;
        final String reason;

        private SpawnLocation(@Nullable BlockPos pos, String reason) {
            this.pos = pos;
            this.reason = reason == null ? "SPAWN_POS_INVALID" : reason;
        }

        static SpawnLocation valid(BlockPos pos) {
            return new SpawnLocation(pos, "SUCCESS");
        }

        static SpawnLocation invalid(String reason) {
            return new SpawnLocation(null, reason);
        }
    }
}