package therealpant.thaumicattempts.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import therealpant.thaumicattempts.api.FluxAnomalyTier;
import therealpant.thaumicattempts.world.data.TAInfectedChunksData;


import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

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

        BlockPos targetPos = findNearestSaved(player, filter);
        if (targetPos == null) {
            throw new CommandException("No generated flux anomalies are currently active.");
        }
        player.connection.setPlayerLocation(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5, player.rotationYaw, player.rotationPitch);
        sender.sendMessage(new TextComponentString(
                "Teleported to active flux anomaly at " + targetPos +
                        " (distance=" + String.format("%.1f", Math.sqrt(player.getDistanceSq(targetPos))) + ")"
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
    private BlockPos findNearestSaved(EntityPlayerMP player, @Nullable FluxAnomalyTier filter) {
        TAInfectedChunksData data = TAInfectedChunksData.get(player.world);
        if (data.getActiveInfectedChunks().isEmpty()) return null;

        BlockPos playerPos = player.getPosition();
        BlockPos bestPos = null;
        double bestDist = Double.MAX_VALUE;

        for (long chunkKey : data.getActiveInfectedChunks()) {
            FluxAnomalyTier tier = data.getTierForChunk(chunkKey);
            if (filter != null && tier != null && tier != filter) continue;
            BlockPos seedPos = data.getSeedPositionForChunk(chunkKey);
            if (seedPos == null) continue;
            player.world.getChunk(seedPos);

            double dist = seedPos.distanceSq(playerPos);
            if (dist < bestDist) {
                bestDist = dist;
                bestPos = seedPos;
            }
        }

        return bestPos;
    }
}