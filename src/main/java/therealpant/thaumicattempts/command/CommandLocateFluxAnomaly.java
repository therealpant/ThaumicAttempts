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
import therealpant.thaumicattempts.world.data.TAWorldFluxData;

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
        List<EntityFluxAnomalyBurst> anomalies = world.getEntities(EntityFluxAnomalyBurst.class, anomaly ->
                anomaly != null
                        && !anomaly.isDead
                        && anomaly.getSpawnMethod() == FluxAnomalySpawnMethod.WORLD_GEN
                        && (filter == null || anomaly.getTier() == filter));

        if (anomalies.isEmpty()) {
            teleportFromSavedData(sender, player, filter);
            return;
        }

        EntityFluxAnomalyBurst nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (EntityFluxAnomalyBurst anomaly : anomalies) {
            double dist = player.getDistanceSq(anomaly);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = anomaly;
            }
        }

        if (nearest == null) {
            throw new CommandException("No generated flux anomalies are currently active.");
        }

        double targetX = nearest.posX;
        double targetY = nearest.posY;
        double targetZ = nearest.posZ;

        player.connection.setPlayerLocation(targetX, targetY, targetZ, player.rotationYaw, player.rotationPitch);

        BlockPos targetPos = new BlockPos(targetX, targetY, targetZ);
        sender.sendMessage(new TextComponentString(
                "Teleported to generated flux anomaly at " + targetPos +
                        " (distance=" + String.format("%.1f", Math.sqrt(nearestDist)) + ")"
        ));
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
        TAWorldFluxData data = TAWorldFluxData.get(player.world);
        if (data.activeAnomalies.isEmpty()) {
            throw new CommandException("No generated flux anomalies are currently active.");
        }

        BlockPos playerPos = player.getPosition();
        UUID bestId = null;
        double bestDist = Double.MAX_VALUE;
        BlockPos bestPos = null;

        for (Map.Entry<UUID, TAWorldFluxData.ActiveAnomalyEntry> entry : data.activeAnomalies.entrySet()) {
            if (entry.getValue() == null) continue;
            if (filter != null && entry.getValue().tier != filter) continue;
            BlockPos pos = entry.getValue().seedPos;
            double dist = pos.distanceSq(playerPos);
            if (dist < bestDist) {
                bestDist = dist;
                bestId = entry.getKey();
                bestPos = pos;
            }
        }

        if (bestId == null || bestPos == null) {
            throw new CommandException("No generated flux anomalies are currently active.");
        }

        player.world.getChunk(bestPos);
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
    }