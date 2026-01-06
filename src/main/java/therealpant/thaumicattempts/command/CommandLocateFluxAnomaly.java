package therealpant.thaumicattempts.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import therealpant.thaumicattempts.api.FluxAnomalySpawnMethod;
import therealpant.thaumicattempts.api.FluxAnomalyTier;
import therealpant.thaumicattempts.world.EntityFluxAnomalyBurst;
import therealpant.thaumicattempts.world.data.TAInfectedChunksData;


import javax.annotation.Nullable;
import java.util.*;

/**
 * Находит ближайшую сгенерированную аномалию и переносит игрока к ней.
 */
public class CommandLocateFluxAnomaly extends CommandBase {

    @Override
    public String getName() {
        return "ta_anomaly_find";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/ta_anomaly_find [any|surface|shallow|deep]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2; // OP
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        FluxAnomalyTier filter = parseFilter(args, sender);

        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        if (player == null) throw new CommandException("Command must be executed by a player.");

        World world = player.world;
        EntityFluxAnomalyBurst nearest = findNearestLoaded(world, player, filter);
        if (nearest != null) {
            double targetX = nearest.posX;
            double targetY = nearest.posY;
            double targetZ = nearest.posZ;
            double nearestDist = player.getDistance(nearest);

            player.connection.setPlayerLocation(targetX, targetY, targetZ, player.rotationYaw, player.rotationPitch);
            BlockPos targetPos = new BlockPos(targetX, targetY, targetZ);
            sender.sendMessage(new TextComponentString(
                    "Teleported to generated flux anomaly at " + targetPos +
                            " (distance=" + String.format("%.1f", nearestDist) + ")"
            ));
            return;
        }
        teleportFromSavedData(sender, player, filter);
    }

    private FluxAnomalyTier parseFilter(String[] args, ICommandSender sender) throws CommandException {
        if (args.length == 0) return null;
        if (args.length > 1) throw new WrongUsageException(getUsage(sender));
        switch (args[0].toLowerCase()) {
            case "any":
                return null;
            case "surface":
                return FluxAnomalyTier.SURFACE;
            case "shallow":
                return FluxAnomalyTier.SHALLOW;
            case "deep":
                return FluxAnomalyTier.DEEP;
            default:
                throw new WrongUsageException(getUsage(sender));
        }
    }

    private void teleportFromSavedData(ICommandSender sender, EntityPlayerMP player, @Nullable FluxAnomalyTier filter) throws CommandException {
        TAInfectedChunksData data = TAInfectedChunksData.get(player.world);
        if (data.getActiveInfectedChunks().isEmpty()) {
            throw new CommandException("No generated flux anomalies are currently active.");
        }

        BlockPos playerPos = player.getPosition();
        BlockPos bestPos = null;
        double bestDist = Double.MAX_VALUE;

        for (long chunkKey : data.getActiveInfectedChunks()) {
            FluxAnomalyTier tier = data.getTierForChunk(chunkKey);
            if (filter != null && tier != null && tier != filter) continue;

            int cx = TAInfectedChunksData.unpackX(chunkKey);
            int cz = TAInfectedChunksData.unpackZ(chunkKey);
            BlockPos center = new BlockPos(cx * 16 + 8, 64, cz * 16 + 8);
            BlockPos seedPos = data.getSeedPositionForChunk(chunkKey);

            BlockPos target = seedPos != null ? seedPos : player.world.getTopSolidOrLiquidBlock(center);
            player.world.getChunk(target);

            double dist = target.distanceSq(playerPos);
            if (dist < bestDist) {
                bestDist = dist;
                bestPos = target;
            }
        }

        if (bestPos == null) {
            throw new CommandException("No generated flux anomalies are currently active.");
        }

        player.connection.setPlayerLocation(bestPos.getX() + 0.5, bestPos.getY() + 0.5, bestPos.getZ() + 0.5, player.rotationYaw, player.rotationPitch);
        sender.sendMessage(new TextComponentString(
                "Teleported to saved active flux anomaly at " + bestPos +
                        " (distance=" + String.format("%.1f", Math.sqrt(bestDist)) + ")"
        ));
    }

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("tafind");
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "any", "surface", "shallow", "deep");
        }
        return Collections.emptyList();
    }

    @Nullable
    private EntityFluxAnomalyBurst findNearestLoaded(World world, EntityPlayerMP player, @Nullable FluxAnomalyTier filter) {
        List<EntityFluxAnomalyBurst> anomalies = world.getEntities(EntityFluxAnomalyBurst.class, anomaly ->
                anomaly != null
                        && !anomaly.isDead
                        && anomaly.getSpawnMethod() == FluxAnomalySpawnMethod.WORLD_GEN
                        && (filter == null || anomaly.getTier() == filter));

        EntityFluxAnomalyBurst nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (EntityFluxAnomalyBurst anomaly : anomalies) {
            double dist = player.getDistanceSq(anomaly);
            if (dist < nearestDistSq) {
                nearestDistSq = dist;
                nearest = anomaly;
            }
        }

        return nearest;
    }
}