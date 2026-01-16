package therealpant.thaumicattempts.world;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
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
import therealpant.thaumicattempts.config.TAConfig;
import therealpant.thaumicattempts.world.data.TAInfectedChunksData;
import therealpant.thaumicattempts.world.data.TAWorldFluxData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = ThaumicAttempts.MODID)
public final class InfectedChunkAnomalyManager {

    public static final int CHECK_PERIOD = 150;
    public static final int SAFE_PLAYER_RADIUS = 96;
    public static final int MIN_INHABITED_TICKS = 144000;
    public static final int MIN_SPACING_CHUNKS = 8;
    public static final int SEARCH_RADIUS_BLOCKS = 256;
    public static final int CLEANED_RADIUS_CHUNKS = 8;
    public static final int CLEANED_COOLDOWN_TICKS = 40000;
    public static final int CLEANSE_AFTER_SPAWN_RADIUS_CHUNKS = 0;
    public static final double MIGRATION_EXTRA_INFECT_CHANCE = 0.10;
    private static final int MOUNTAIN_ELEVATION = 18;
    private static final int SHALLOW_MIN_DEPTH_BELOW_SURFACE = 10;
    private static final int SHALLOW_MAX_DEPTH_BELOW_SURFACE = 40;
    private static final int SHALLOW_MIN_Y_FLOOR = 18;
    private static final int SHALLOW_MAX_Y_CEIL = 90;

    private static final double ACTIVATION_CHANCE = 0.75;
    private static final double STAGE1_INFECTION_CHANCE = 0.03;
    private static final double STAGE2_INFECTION_CHANCE = 0.06;
    private static final double STAGE3_INFECTION_CHANCE = 0.09;

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
            ThaumicAttempts.LOGGER.info(
                    "[AnomSpawn] chunk infected cx={} cz={} stage={}",
                    event.getChunkX(), event.getChunkZ(), fluxData.stage
            );
            data.addInfectedChunk(chunkKey);
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        World world = event.getWorld();
        if (!(world instanceof WorldServer)) return;
        if (world.isRemote) return;
        if (world.provider == null || world.provider.getDimension() != 0) return;

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

        if ((now % CHECK_PERIOD) != 0) {
            return;
        }

        infectedData.purgeExpired(now);
        infectedData.lastActivationAttemptTime = now;
        TAWorldFluxData fluxData = null;

        fluxData = TAWorldFluxData.get(world);
        ThaumicAttempts.LOGGER.info(
                "[AnomSpawn] attempt tick={} stage={} players={} infectedTotal={} active={}",
                now,
                fluxData.stage,
                world.playerEntities.size(),
                infectedData.getInfectedChunkKeys().size(),
                infectedData.getActiveChunkKeys().size()
        );

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
        int searchRadiusChunks = (int) Math.ceil(SEARCH_RADIUS_BLOCKS / 16.0);
        double searchRadiusSq = (double) SEARCH_RADIUS_BLOCKS * SEARCH_RADIUS_BLOCKS;
        double minDistanceSq = (double) SAFE_PLAYER_RADIUS * SAFE_PLAYER_RADIUS;

        List<Chunk> candidates = new ArrayList<>();
        int infectedNearPlayer = 0;
        int blockedByCleaned = 0;
        int tooClose = 0;

        ChunkProviderServer chunkProvider = serverWorld.getChunkProvider();
        for (int cx = player.chunkCoordX - searchRadiusChunks; cx <= player.chunkCoordX + searchRadiusChunks; cx++) {
            for (int cz = player.chunkCoordZ - searchRadiusChunks; cz <= player.chunkCoordZ + searchRadiusChunks; cz++) {
                double centerX = cx * 16 + 8;
                double centerZ = cz * 16 + 8;
                double dx = centerX - player.posX;
                double dz = centerZ - player.posZ;
                double distSq = dx * dx + dz * dz;
                if (distSq > searchRadiusSq) continue;

                long key = chunkKey(cx, cz);
                if (!infectedData.isInfected(key)) continue;

                infectedNearPlayer++;
                if (infectedData.isActive(key)) {
                    continue;
                }
                if (distSq < minDistanceSq) {
                    tooClose++;
                    continue;
                }
                if (isInCleanedCooldown(world, infectedData, cx, cz)) {
                    blockedByCleaned++;
                    continue;
                }
                if (hasAnyWithinRadiusChunks(infectedData.getActiveChunkKeys(), cx, cz, MIN_SPACING_CHUNKS)) {
                    continue;
                }

                Chunk chunk = chunkProvider.getLoadedChunk(cx, cz);
                if (chunk == null) {
                    continue;
                }
                if (!passesActivationFilters(world, chunk, infectedData)) {
                    continue;
                }
                candidates.add(chunk);
            }
        }

        ThaumicAttempts.LOGGER.info(
                "[AnomSpawn] scan result: infectedNearPlayer={} candidates={} blockedByCleaned={} tooClose={} activeNearby={}",
                infectedNearPlayer,
                candidates.size(),
                blockedByCleaned,
                tooClose,
                infectedData.getActiveChunkKeys().size()
        );
        if (candidates.isEmpty()) {
            String reason;
            if (infectedNearPlayer == 0) {
                reason = "NO_INFECTED_NEAR_PLAYER";
            } else if (blockedByCleaned == infectedNearPlayer) {
                reason = "ALL_INFECTED_BLOCKED_BY_CLEANED";
            } else if (tooClose == infectedNearPlayer) {
                reason = "TOO_CLOSE_TO_PLAYER";
            } else {
                reason = "NO_CANDIDATE_PASSED_FILTERS";
            }
            recordAttempt(infectedData, reason, infectedNearPlayer);
            return;
        }

        Chunk target = candidates.get(rand.nextInt(candidates.size()));
        ThaumicAttempts.LOGGER.info(
                "[AnomSpawn] selected chunk cx={} cz={} dist={} blocks",
                target.x,
                target.z,
                (int)Math.sqrt(
                        Math.pow(target.x * 16 + 8 - player.posX, 2) +
                                Math.pow(target.z * 16 + 8 - player.posZ, 2)
                )
        );
        ActivationResult result = spawnInChunk(serverWorld, target, fluxData, infectedData);
        if (!result.success) {
            recordAttempt(infectedData, result.reason, infectedNearPlayer);
            return;
        }
        cleanseAfterSpawn(infectedData, target.x, target.z);
        recordAttempt(infectedData, "SUCCESS", infectedNearPlayer);

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

        ThaumicAttempts.LOGGER.info(
                "[AnomSpawn] anomaly ended seed={} anomaly={} chunkKey={}",
                seedId, anomalyId, chunkKey
        );

        Long knownChunk = resolveChunkKey(data, seedId, anomalyId, chunkKey);
        if (knownChunk == null) return;

        if (seedId != null) data.removeSeed(seedId);
        if (anomalyId != null) data.removeAnomaly(anomalyId);
        data.removeSeedsForChunk(knownChunk);

        data.unmarkChunkActive(knownChunk);
        data.removeInfectedChunk(knownChunk);
        data.clearChunkTracking(knownChunk);
        long now = world.getTotalWorldTime();
        data.markCleaned(knownChunk, now + CLEANED_COOLDOWN_TICKS);

        migrateInfection(serverWorld, data, knownChunk);
    }

    private static void migrateInfection(WorldServer world, TAInfectedChunksData data, long originChunkKey) {
        TAWorldFluxData fluxData = TAWorldFluxData.get(world);
        if (fluxData.stage < 1) return;

        ThaumicAttempts.LOGGER.info(
                "[AnomSpawn] migration from chunkKey={} cleanedRadius={}",
                originChunkKey, CLEANED_RADIUS_CHUNKS
        );


        int originX = chunkX(originChunkKey);
        int originZ = chunkZ(originChunkKey);
        List<MigrateCandidate> candidates = new ArrayList<>();
        ChunkProviderServer provider = world.getChunkProvider();
        for (int dx = -CLEANED_RADIUS_CHUNKS; dx <= CLEANED_RADIUS_CHUNKS; dx++) {
            for (int dz = -CLEANED_RADIUS_CHUNKS; dz <= CLEANED_RADIUS_CHUNKS; dz++) {
                int cx = originX + dx;
                int cz = originZ + dz;
                long key = chunkKey(cx, cz);
                if (data.isInfected(key)) continue;
                if (data.isActive(key)) continue;
                if (isInCleanedCooldown(world, data, cx, cz)) continue;

                Chunk chunk = provider.getLoadedChunk(cx, cz);
                if (chunk == null) continue;
                if (!passesActivationFilters(world, chunk, data)) continue;

                int dist2 = dx * dx + dz * dz;
                candidates.add(new MigrateCandidate(key, dist2));
            }
        }

        if (candidates.isEmpty()) return;

        MigrateCandidate primary = pickFarthestCandidate(world.rand, candidates, null);
        if (primary != null) {
            data.addInfectedChunk(primary.chunkKey);
        }
        if (primary != null && world.rand.nextDouble() < MIGRATION_EXTRA_INFECT_CHANCE) {
            MigrateCandidate secondary = pickFarthestCandidate(world.rand, candidates, primary.chunkKey);
            if (secondary != null) {
                data.addInfectedChunk(secondary.chunkKey);
            }
        }
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
            ThaumicAttempts.LOGGER.info(
                    "[AnomSpawn] spawning anomaly in chunk {} {} tier={}",
                    chunk.x, chunk.z, tier
            );
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
        EntityPlayer nearest = world.getClosestPlayer(cx, world.getSeaLevel(), cz, SAFE_PLAYER_RADIUS, false);
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

    private static boolean isInCleanedCooldown(World world, TAInfectedChunksData data, int cx, int cz) {
        long now = world.getTotalWorldTime();
        for (int dx = -CLEANED_RADIUS_CHUNKS; dx <= CLEANED_RADIUS_CHUNKS; dx++) {
            for (int dz = -CLEANED_RADIUS_CHUNKS; dz <= CLEANED_RADIUS_CHUNKS; dz++) {
                long key = chunkKey(cx + dx, cz + dz);
                long expiry = data.getCleanedExpiry(key);
                if (expiry > now) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void cleanseAfterSpawn(TAInfectedChunksData data, int hostCx, int hostCz) {
        long hostingKey = chunkKey(hostCx, hostCz);
        for (int dx = -CLEANSE_AFTER_SPAWN_RADIUS_CHUNKS; dx <= CLEANSE_AFTER_SPAWN_RADIUS_CHUNKS; dx++) {
            for (int dz = -CLEANSE_AFTER_SPAWN_RADIUS_CHUNKS; dz <= CLEANSE_AFTER_SPAWN_RADIUS_CHUNKS; dz++) {
                long key = chunkKey(hostCx + dx, hostCz + dz);
                if (key == hostingKey) continue;
                if (data.isActive(key)) continue;
                if (data.isInfected(key)) {
                    data.removeInfectedChunk(key);
                }
            }
        }
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

    private static int surfaceY(World world, int x, int z) {
        int y = world.getHeight(new BlockPos(x, 0, z)).getY();
        int max = world.getHeight() - 2;
        if (y < 1) y = 1;
        if (y > max) y = max;
        return y;
    }

    private static boolean isMountainous(World world, int x, int z) {
        int sea = world.getSeaLevel();
        int sy = surfaceY(world, x, z);
        return (sy - sea) >= MOUNTAIN_ELEVATION;
    }

    private static int clampY(World world, int y) {
        int max = world.getHeight() - 2;
        if (y < 1) return 1;
        if (y > max) return max;
        return y;
    }

    @Nullable
    private static SpawnLocation findShallow(World world, BlockPos column, Random rand) {
        int x = column.getX();
        int z = column.getZ();
        int sy = surfaceY(world, x, z);
        int maxY = Math.min(SHALLOW_MAX_Y_CEIL, sy - SHALLOW_MIN_DEPTH_BELOW_SURFACE);
        int minY = Math.max(SHALLOW_MIN_Y_FLOOR, sy - SHALLOW_MAX_DEPTH_BELOW_SURFACE);

        if (TAConfig.ENABLE_FLUX_ANOMALY_DEBUG_LOGS) {
            ThaumicAttempts.LOGGER.debug(
                    "[AnomSpawn] shallow terrain x={} z={} surfaceY={} range=[{}..{}] mountainous={}",
                    x, z, sy, minY, maxY, isMountainous(world, x, z)
            );
        }

        if (maxY <= minY) return SpawnLocation.invalid("SPAWN_POS_INVALID");

        for (int i = 0; i < 24; i++) {
            int y = minY + rand.nextInt(maxY - minY + 1);
            y = clampY(world, y);
            BlockPos pos = new BlockPos(x, y, z);
            if (!world.isBlockLoaded(pos)) continue;

            SpawnLocation location = tryFindCaveAir(world, pos);
            if (location.pos != null) {
                if (world.canBlockSeeSky(location.pos)) continue;
                return location;
            }
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
        ThaumicAttempts.LOGGER.info("[AnomSpawn] reason={} infectedNear={} time={}",
                reason, candidates, data.lastActivationAttemptTime);
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

    private static final class MigrateCandidate {
        final long chunkKey;
        final int dist2;

        private MigrateCandidate(long chunkKey, int dist2) {
            this.chunkKey = chunkKey;
            this.dist2 = dist2;
        }
    }

    @Nullable
    private static MigrateCandidate pickFarthestCandidate(Random rand, List<MigrateCandidate> candidates, @Nullable Long excludeKey) {
        int maxDist = -1;
        List<MigrateCandidate> farthest = new ArrayList<>();
        for (MigrateCandidate candidate : candidates) {
            if (excludeKey != null && candidate.chunkKey == excludeKey) continue;
            if (candidate.dist2 > maxDist) {
                maxDist = candidate.dist2;
                farthest.clear();
                farthest.add(candidate);
            } else if (candidate.dist2 == maxDist) {
                farthest.add(candidate);
            }
        }
        if (farthest.isEmpty()) return null;
        return farthest.get(rand.nextInt(farthest.size()));
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