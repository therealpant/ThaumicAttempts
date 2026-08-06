package therealpant.thaumicattempts.world;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.items.CapabilityItemHandler;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.api.FluxAnomalyApi;
import therealpant.thaumicattempts.api.FluxAnomalyEnvironment;
import therealpant.thaumicattempts.api.FluxAnomalyPresets;
import therealpant.thaumicattempts.api.FluxAnomalySettings;
import therealpant.thaumicattempts.api.FluxAnomalyTier;
import therealpant.thaumicattempts.world.data.TAInfectedChunksData;
import therealpant.thaumicattempts.world.data.TAPlayerAnomalyData;
import therealpant.thaumicattempts.world.data.TAWorldFluxData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Natural anomaly director.
 *
 * Old infected-chunk and inhabited-time lotteries were deliberately removed.
 * A player owns finite spawn budgets and a point is spent only after an
 * anomaly entity was successfully created.
 */
@Mod.EventBusSubscriber(modid = ThaumicAttempts.MODID)
public final class InfectedChunkAnomalyManager {

    public static final int CHECK_PERIOD = 450;
    private static final int MAX_PLAYERS_PER_PASS = 10;
    public static final int SAFE_PLAYER_RADIUS = 96;
    public static final int SEARCH_MIN_RADIUS = 128;
    public static final int SEARCH_MAX_RADIUS = 256;
    public static final int STORAGE_SAFE_RADIUS = 48;
    public static final int ANOMALY_SAFE_RADIUS = 48;
    private static final int POSITION_ATTEMPTS = 24;

    private InfectedChunkAnomalyManager() {}

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        World world = event.world;
        if (!(world instanceof WorldServer) || world.isRemote) return;
        if (event.phase != TickEvent.Phase.END) return;

        int dimension = world.provider.getDimension();
        if (dimension != 0 && dimension != -1 && dimension != 1) return;

        long now = world.getTotalWorldTime();
        if (now % CHECK_PERIOD != 0L) return;
        if (world.playerEntities.isEmpty()) return;

        WorldServer overworld = world.getMinecraftServer().getWorld(0);
        if (overworld == null) return;
        int stage = TAWorldFluxData.get(overworld).stage;
        if (stage <= 0) return;

        List<EntityPlayerMP> players = new ArrayList<>();
        for (EntityPlayer player : world.playerEntities) {
            if (player instanceof EntityPlayerMP && !player.isSpectator()) {
                players.add((EntityPlayerMP) player);
            }
        }
        Collections.shuffle(players, world.rand);
        int checkedPlayers = Math.min(MAX_PLAYERS_PER_PASS, players.size());
        for (int index = 0; index < checkedPlayers; index++) {
            trySpawnForPlayer((WorldServer) world, players.get(index),
                    stage, TAWorldFluxData.get(overworld).fluxGeneratedTotal, now);
        }
    }

    private static void trySpawnForPlayer(WorldServer world, EntityPlayerMP player,
                                          int stage, double totalFlux, long now) {
        TAPlayerAnomalyData data = TAPlayerAnomalyData.get(world);
        TAPlayerAnomalyData.Pool pool = poolForDimension(world.provider.getDimension());
        if (pool == null) return;

        List<SpawnChoice> choices = eligibleChoices(world, player, data, pool, stage, now);
        if (choices.isEmpty()) return;

        Random rand = world.rand;
        SpawnChoice choice = choices.get(rand.nextInt(choices.size()));
        choice = maybeReplaceWithTierFourAnomaly(world, player, choice, totalFlux, rand);
        BlockPos position = findPosition(world, player, choice, rand);
        if (position == null) return;

        long chunkKey = TAInfectedChunksData.pack(position.getX() >> 4, position.getZ() >> 4);
        FluxAnomalySettings settings = FluxAnomalyPresets.createSettings(choice.tier)
                .environment(choice.environment)
                .sourcePlayer(player.getUniqueID())
                .sourceChunk(chunkKey);

        EntityFluxAnomalyBurst anomaly = FluxAnomalyApi.trySpawnEntity(world, position, settings);
        if (anomaly == null) return;

        if (!isHighTier(choice.tier)) {
            data.spend(player.getUniqueID(), pool, choice.budgetTier, now);
        }

        TAInfectedChunksData active = TAInfectedChunksData.get(world);
        active.markChunkActive(chunkKey);
        active.setActiveChunkTier(chunkKey, choice.tier);
        active.trackAnomaly(anomaly.getAnomalyId(), chunkKey);
        active.setAnomalyPosition(chunkKey, position);
    }

    private static SpawnChoice maybeReplaceWithTierFourAnomaly(WorldServer world,
                                                               EntityPlayerMP player,
                                                               SpawnChoice choice,
                                                               double totalFlux,
                                                               Random rand) {
        if (totalFlux < TAWorldFluxData.ARCHIVE_ANOMALY_FLUX) return choice;
        boolean overworldReplacement = world.provider.getDimension() == 0
                && choice.tier == FluxAnomalyTier.DEEP
                && choice.environment == FluxAnomalyEnvironment.STANDARD
                && rand.nextFloat() < 0.10F;
        boolean netherReplacement = world.provider.getDimension() == -1
                && (choice.environment == FluxAnomalyEnvironment.NETHER_FLOOR
                || choice.environment == FluxAnomalyEnvironment.NETHER_CEILING)
                && rand.nextFloat() < 0.10F;
        boolean endReplacement = world.provider.getDimension() == 1
                && choice.tier == FluxAnomalyTier.DEEP
                && choice.environment == FluxAnomalyEnvironment.END_ISLANDS
                && rand.nextFloat() < 0.10F;
        if (!overworldReplacement && !netherReplacement && !endReplacement) return choice;
        FluxAnomalyTier replacementTier = endReplacement
                ? FluxAnomalyTier.MONOLITH
                : netherReplacement ? FluxAnomalyTier.LABORATORY : FluxAnomalyTier.ARCHIVE;
        if (hasSimilarLoadedAnomaly(world, player,
                replacementTier, choice.environment)) {
            return choice;
        }
        return new SpawnChoice(replacementTier,
                choice.environment, choice.tier);
    }

    private static List<SpawnChoice> eligibleChoices(WorldServer world,
                                                     EntityPlayerMP player,
                                                     TAPlayerAnomalyData data,
                                                     TAPlayerAnomalyData.Pool pool,
                                                     int stage,
                                                     long now) {
        List<SpawnChoice> result = new ArrayList<>();
        UUID playerId = player.getUniqueID();

        if (pool == TAPlayerAnomalyData.Pool.OVERWORLD) {
            addIfEligible(result, world, player, data, playerId, pool,
                    FluxAnomalyTier.SURFACE, FluxAnomalyEnvironment.STANDARD, stage >= 1, now);
            addIfEligible(result, world, player, data, playerId, pool,
                    FluxAnomalyTier.SHALLOW, FluxAnomalyEnvironment.STANDARD, stage >= 2, now);
            addIfEligible(result, world, player, data, playerId, pool,
                    FluxAnomalyTier.DEEP, FluxAnomalyEnvironment.STANDARD, stage >= 3, now);
        } else if (pool == TAPlayerAnomalyData.Pool.NETHER && stage >= 2) {
            FluxAnomalyEnvironment environment = world.rand.nextBoolean()
                    ? FluxAnomalyEnvironment.NETHER_FLOOR
                    : FluxAnomalyEnvironment.NETHER_CEILING;
            addIfEligible(result, world, player, data, playerId, pool,
                    FluxAnomalyTier.SHALLOW, environment, true, now);
        } else if (pool == TAPlayerAnomalyData.Pool.END && stage >= 3) {
            addIfEligible(result, world, player, data, playerId, pool,
                    FluxAnomalyTier.DEEP, FluxAnomalyEnvironment.END_ISLANDS, true, now);
        }
        return result;
    }

    private static void addIfEligible(List<SpawnChoice> target,
                                      WorldServer world,
                                      EntityPlayerMP player,
                                      TAPlayerAnomalyData data,
                                      UUID playerId,
                                      TAPlayerAnomalyData.Pool pool,
                                      FluxAnomalyTier tier,
                                      FluxAnomalyEnvironment environment,
                                      boolean unlocked,
                                      long now) {
        if (!unlocked || !data.canSpend(playerId, pool, tier, now)) return;
        if (hasSimilarLoadedAnomaly(world, player, tier, environment)) return;
        target.add(new SpawnChoice(tier, environment));
    }

    private static boolean hasSimilarLoadedAnomaly(WorldServer world,
                                                   EntityPlayerMP player,
                                                   FluxAnomalyTier tier,
                                                   FluxAnomalyEnvironment environment) {
        double maxDistanceSq = (SEARCH_MAX_RADIUS + 64.0D) * (SEARCH_MAX_RADIUS + 64.0D);
        for (Entity entity : world.loadedEntityList) {
            if (!(entity instanceof EntityFluxAnomalyBurst) || entity.isDead) continue;
            if (entity.getDistanceSq(player) > maxDistanceSq) continue;
            EntityFluxAnomalyBurst anomaly = (EntityFluxAnomalyBurst) entity;
            if (isHighTier(anomaly.getTier())) continue;
            if (environment == FluxAnomalyEnvironment.END_ISLANDS) {
                if (anomaly.getEnvironment() == FluxAnomalyEnvironment.END_ISLANDS) return true;
            } else if (environment == FluxAnomalyEnvironment.NETHER_FLOOR
                    || environment == FluxAnomalyEnvironment.NETHER_CEILING) {
                if (anomaly.getEnvironment() == FluxAnomalyEnvironment.NETHER_FLOOR
                        || anomaly.getEnvironment() == FluxAnomalyEnvironment.NETHER_CEILING) return true;
            } else if (anomaly.getEnvironment() == FluxAnomalyEnvironment.STANDARD
                    && anomaly.getTier() == tier) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static BlockPos findPosition(WorldServer world,
                                         EntityPlayerMP source,
                                         SpawnChoice choice,
                                         Random rand) {
        for (int attempt = 0; attempt < POSITION_ATTEMPTS; attempt++) {
            double angle = rand.nextDouble() * Math.PI * 2.0D;
            int distance = SEARCH_MIN_RADIUS + rand.nextInt(SEARCH_MAX_RADIUS - SEARCH_MIN_RADIUS + 1);
            int x = MathHelper.floor(source.posX + Math.cos(angle) * distance);
            int z = MathHelper.floor(source.posZ + Math.sin(angle) * distance);
            int y = chooseY(world, x, z, choice, rand);
            BlockPos candidate = new BlockPos(x, y, z);

            if (!world.isAreaLoaded(candidate, 16)) continue;
            if (hasPlayerTooClose(world, candidate)) continue;
            if (hasLoadedStorageNear(world, candidate)) continue;

            if (choice.environment == FluxAnomalyEnvironment.STANDARD
                    && choice.tier == FluxAnomalyTier.SURFACE) {
                BlockPos top = world.getTopSolidOrLiquidBlock(new BlockPos(x, 0, z));
                if (top.getY() <= 1 || world.getBlockState(top).getMaterial().isLiquid()) continue;
                candidate = top.up();
                if (!world.isAirBlock(candidate)) continue;
            }
            if (hasActiveAnomalyNear(world, candidate)) continue;
            return candidate;
        }
        return null;
    }

    private static int chooseY(WorldServer world, int x, int z, SpawnChoice choice, Random rand) {
        switch (choice.environment) {
            case NETHER_FLOOR:
                return findNetherFloorAnchor(world, x, z);
            case NETHER_CEILING:
                return findNetherCeilingAnchor(world, x, z);
            case END_ISLANDS:
                return rand.nextBoolean() ? 20 + rand.nextInt(9) : 218 + rand.nextInt(15);
            case STANDARD:
            default:
                if (choice.tier == FluxAnomalyTier.DEEP
                        || choice.tier == FluxAnomalyTier.ARCHIVE
                        || choice.tier == FluxAnomalyTier.LABORATORY
                        || choice.tier == FluxAnomalyTier.MONOLITH) {
                    return 6 + rand.nextInt(18);
                }
                if (choice.tier == FluxAnomalyTier.SHALLOW) {
                    int surface = world.getHeight(new BlockPos(x, 0, z)).getY();
                    return MathHelper.clamp(surface - 18 - rand.nextInt(23), 18, 90);
                }
                return world.getHeight(new BlockPos(x, 0, z)).getY();
        }
    }

    private static int findNetherFloorAnchor(World world, int x, int z) {
        for (int y = 16; y >= 0; y--) {
            if (world.getBlockState(new BlockPos(x, y, z)).getBlock()
                    == net.minecraft.init.Blocks.BEDROCK) {
                return Math.min(24, y + 2);
            }
        }
        return 6;
    }

    private static int findNetherCeilingAnchor(World world, int x, int z) {
        for (int y = 96; y <= 127; y++) {
            if (world.getBlockState(new BlockPos(x, y, z)).getBlock()
                    == net.minecraft.init.Blocks.BEDROCK) {
                return Math.max(96, y - 7);
            }
        }
        return 120;
    }

    private static boolean hasPlayerTooClose(World world, BlockPos pos) {
        double radiusSq = SAFE_PLAYER_RADIUS * SAFE_PLAYER_RADIUS;
        for (EntityPlayer player : world.playerEntities) {
            double dx = player.posX - (pos.getX() + 0.5D);
            double dy = player.posY - (pos.getY() + 0.5D);
            double dz = player.posZ - (pos.getZ() + 0.5D);
            if (dx * dx + dy * dy + dz * dz < radiusSq) return true;
        }
        return false;
    }

    private static boolean hasLoadedStorageNear(World world, BlockPos pos) {
        double radiusSq = STORAGE_SAFE_RADIUS * STORAGE_SAFE_RADIUS;
        for (TileEntity tile : world.loadedTileEntityList) {
            if (tile == null || tile.isInvalid()) continue;
            if (!isStorage(tile)) continue;
            double dx = tile.getPos().getX() - pos.getX();
            double dz = tile.getPos().getZ() - pos.getZ();
            if (dx * dx + dz * dz <= radiusSq) return true;
        }
        return false;
    }

    public static boolean hasActiveAnomalyNear(World world, BlockPos pos) {
        if (world == null || pos == null) return false;
        double radiusSq = ANOMALY_SAFE_RADIUS * ANOMALY_SAFE_RADIUS;

        for (Entity entity : world.loadedEntityList) {
            if (!(entity instanceof EntityFluxAnomalyBurst) || entity.isDead) continue;
            if (isHighTier(((EntityFluxAnomalyBurst) entity).getTier())) continue;
            double dx = entity.posX - (pos.getX() + 0.5D);
            double dy = entity.posY - (pos.getY() + 0.5D);
            double dz = entity.posZ - (pos.getZ() + 0.5D);
            if (dx * dx + dy * dy + dz * dz < radiusSq) return true;
        }

        TAInfectedChunksData active = TAInfectedChunksData.get(world);
        for (long chunkKey : active.getActiveInfectedChunks()) {
            if (isHighTier(active.getTierForChunk(chunkKey))) continue;
            BlockPos known = active.getSeedPositionForChunk(chunkKey);
            if (known != null && known.distanceSq(pos) < radiusSq) return true;
        }
        return false;
    }

    private static boolean isStorage(TileEntity tile) {
        if (tile instanceof IInventory) return true;
        Capability<?> capability = CapabilityItemHandler.ITEM_HANDLER_CAPABILITY;
        return tile.hasCapability(capability, null);
    }

    public static void onSeedSpawned(World world, UUID seedId, long chunkKey, BlockPos seedPos) {
        if (world == null || world.isRemote || chunkKey == Long.MIN_VALUE) return;
        TAInfectedChunksData.get(world).trackSeed(seedId, chunkKey, seedPos);
    }

    public static void onAnomalyEnded(World world,
                                      @Nullable UUID seedId,
                                      @Nullable UUID anomalyId,
                                      long chunkKey,
                                      @Nullable UUID sourcePlayerId,
                                      FluxAnomalyTier tier,
                                      FluxAnomalyEnvironment environment) {
        if (!(world instanceof WorldServer)) return;

        TAInfectedChunksData active = TAInfectedChunksData.get(world);
        Long knownChunk = resolveChunkKey(active, seedId, anomalyId, chunkKey);
        if (knownChunk != null) {
            if (seedId != null) active.removeSeed(seedId);
            if (anomalyId != null) active.removeAnomaly(anomalyId);
            active.removeSeedsForChunk(knownChunk);
            active.unmarkChunkActive(knownChunk);
            active.removeInfectedChunk(knownChunk);
            active.clearChunkTracking(knownChunk);
        }

        if (sourcePlayerId != null && !isHighTier(tier)) {
            TAPlayerAnomalyData.Pool pool = poolForDimension(world.provider.getDimension());
            if (pool != null) {
                TAPlayerAnomalyData.get(world).recordClosed(
                        sourcePlayerId, pool, world.getTotalWorldTime());
            }
        }
    }

    @Nullable
    private static Long resolveChunkKey(TAInfectedChunksData data,
                                        @Nullable UUID seedId,
                                        @Nullable UUID anomalyId,
                                        long chunkKey) {
        if (chunkKey != Long.MIN_VALUE) return chunkKey;
        if (seedId != null) {
            Long found = data.findChunkForSeed(seedId);
            if (found != null) return found;
        }
        return anomalyId == null ? null : data.findChunkForAnomaly(anomalyId);
    }

    @Nullable
    private static TAPlayerAnomalyData.Pool poolForDimension(int dimension) {
        if (dimension == 0) return TAPlayerAnomalyData.Pool.OVERWORLD;
        if (dimension == -1) return TAPlayerAnomalyData.Pool.NETHER;
        if (dimension == 1) return TAPlayerAnomalyData.Pool.END;
        return null;
    }

    private static boolean isHighTier(@Nullable FluxAnomalyTier tier) {
        return tier == FluxAnomalyTier.ARCHIVE
                || tier == FluxAnomalyTier.LABORATORY
                || tier == FluxAnomalyTier.MONOLITH;
    }

    private static final class SpawnChoice {
        final FluxAnomalyTier tier;
        final FluxAnomalyEnvironment environment;
        final FluxAnomalyTier budgetTier;

        private SpawnChoice(FluxAnomalyTier tier, FluxAnomalyEnvironment environment) {
            this(tier, environment, tier);
        }

        private SpawnChoice(FluxAnomalyTier tier,
                            FluxAnomalyEnvironment environment,
                            FluxAnomalyTier budgetTier) {
            this.tier = tier;
            this.environment = environment;
            this.budgetTier = budgetTier;
        }
    }
}
