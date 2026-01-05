package therealpant.thaumicattempts.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.util.text.TextComponentString;
import therealpant.thaumicattempts.api.FluxAnomalySpawnMethod;
import therealpant.thaumicattempts.world.EntityFluxAnomalyBurst;

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
        return "/ta_anomaly_find";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2; // OP
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 0) {
            throw new WrongUsageException(getUsage(sender));
        }

        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        if (player == null) throw new CommandException("Command must be executed by a player.");

        World world = player.world;
        List<EntityFluxAnomalyBurst> anomalies = world.getEntities(EntityFluxAnomalyBurst.class, anomaly ->
                anomaly != null
                        && !anomaly.isDead
                        && anomaly.getSpawnMethod() == FluxAnomalySpawnMethod.WORLD_GEN);

        if (anomalies.isEmpty()) {
            throw new CommandException("No generated flux anomalies are currently active.");
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

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("tafind");
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        // никаких подсказок, чтобы было проще
        return Collections.emptyList();
    }
}