// src/main/java/therealpant/thaumicattempts/command/CommandSpawnFluxAnomaly.java
package therealpant.thaumicattempts.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import therealpant.thaumicattempts.world.EntityFluxAnomalyBurst;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * Команда для теста:
 *   /ta_anomaly
 *   /ta_anomaly <radius> <totalSpreads> <budgetPerTick>
 *
 * Пример:
 *   /ta_anomaly 40 6500 220
 *
 * Спавнит аномалию в позиции игрока (на сервере).
 */
public class CommandSpawnFluxAnomaly extends CommandBase {

    @Override
    public String getName() {
        return "ta_anomaly";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/ta_anomaly [radius] [totalSpreads] [budgetPerTick]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2; // OP
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        if (player == null) throw new CommandException("Command must be executed by a player.");

        int radius = 40;
        int total = 6500;
        int budget = 220;

        if (args.length != 0 && args.length != 3) {
            throw new WrongUsageException(getUsage(sender));
        }

        if (args.length == 3) {
            radius = parseInt(args[0], 8, 96);
            total  = parseInt(args[1], 500, 40000);
            budget = parseInt(args[2], 20, 2000);
        }

        BlockPos center = player.getPosition();

        // Спавним с кастомными параметрами
        EntityFluxAnomalyBurst e = new EntityFluxAnomalyBurst(player.world, center, radius, total, budget);
        player.world.spawnEntity(e);

        sender.sendMessage(new net.minecraft.util.text.TextComponentString(
                "Spawned flux anomaly at " + center +
                        " (radius=" + radius +
                        ", totalSpreads=" + total +
                        ", budgetPerTick=" + budget + ")"
        ));

    }

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("taflux");
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        // никаких подсказок, чтобы было проще
        return Collections.emptyList();
    }
}
