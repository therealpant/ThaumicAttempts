package therealpant.thaumicattempts.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import therealpant.thaumicattempts.world.data.TAWorldFluxData;
import therealpant.thaumicattempts.world.data.TAInfectedChunksData;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * Выводит отладочную информацию о спавне флакс-аномалий.
 */
public class CommandAnomalyDebug extends CommandBase {

    @Override
    public String getName() {
        return "ta_anomaly_debug";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/ta_anomaly_debug";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 0) {
            throw new WrongUsageException(getUsage(sender));
        }

        World overworld = server.getWorld(0);
        if (overworld == null || overworld.isRemote) {
            throw new CommandException("Overworld is not loaded.");
        }

        TAWorldFluxData data = TAWorldFluxData.get(overworld);
        TAInfectedChunksData infected = TAInfectedChunksData.get(overworld);

        sender.sendMessage(new TextComponentString("WorldTime: " + overworld.getTotalWorldTime()));
        sender.sendMessage(new TextComponentString(
                "stage=" + data.stage +
                        ", fluxGeneratedTotal=" + String.format("%.1f", data.fluxGeneratedTotal) +
                        ", nextAnomalySpawnTime=" + data.nextAnomalySpawnTime +
                        (data.lastNextAnomalySpawnTimeSet > 0 ? " (lastSet=" + data.lastNextAnomalySpawnTimeSet + ")" : "")
        ));
        sender.sendMessage(new TextComponentString(
                "lastSpawnAttemptTime=" + data.lastSpawnAttemptTime +
                        ", lastSpawnFailReason=" + (data.lastSpawnFailReason == null ? "" : data.lastSpawnFailReason) +
                        ", lastSpawnCheckedCandidates=" + data.lastSpawnCheckedCandidates
        ));
        sender.sendMessage(new TextComponentString("infected=" + infected.getInfectedChunks().size() +
                ", active=" + infected.getActiveInfectedChunks().size() +
                ", tracked seeds=" + infected.getTrackedSeeds() +
                ", tracked anomalies=" + infected.getTrackedAnomalies()));
        sender.sendMessage(new TextComponentString(
                "lastActivationAttempt=" + infected.lastActivationAttemptTime +
                        ", lastActivationReason=" + (infected.lastActivationFailReason == null ? "" : infected.lastActivationFailReason) +
                        ", lastCandidatesChecked=" + infected.lastCandidatesChecked +
                        (infected.lastManagerTickTime > 0 ? ", lastManagerTickTime=" + infected.lastManagerTickTime : "")
        ));
        if (infected.getLastActivatedSeedPos() != null || infected.getLastActivatedTier() != null) {
            sender.sendMessage(new TextComponentString(
                    "lastActiveTier=" + (infected.getLastActivatedTier() == null ? "none" : infected.getLastActivatedTier()) +
                            ", lastSeedPos=" + infected.getLastActivatedSeedPos()
            ));
        }
        sender.sendMessage(new TextComponentString(
                "lastResourcePlacement=" + infected.getLastResourcePlacement() +
                        " (attempts=" + infected.getLastResourcePlacementAttempts() +
                        ", placed=" + infected.getLastResourcePlacementSuccess() + ")"
        ));
        }

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("ta_anom_dbg");
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        return Collections.emptyList();
    }
}