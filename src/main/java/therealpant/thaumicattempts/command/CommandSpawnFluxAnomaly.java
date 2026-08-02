// src/main/java/therealpant/thaumicattempts/command/CommandSpawnFluxAnomaly.java
package therealpant.thaumicattempts.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import therealpant.thaumicattempts.api.FluxAnomalyApi;
import therealpant.thaumicattempts.api.FluxAnomalySettings;
import therealpant.thaumicattempts.api.FluxAnomalySpawnMethod;
import therealpant.thaumicattempts.api.FluxAnomalyTier;
import therealpant.thaumicattempts.api.FluxAnomalyEnvironment;
import therealpant.thaumicattempts.world.EntityFluxAnomalyBurst;
import therealpant.thaumicattempts.world.InfectedChunkAnomalyManager;
import therealpant.thaumicattempts.world.data.TAInfectedChunksData;

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
        return "/ta_anomaly [surface|shallow|deep|archive|laboratory|monolith] [radius totalSpreads budgetPerTick]";
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
        FluxAnomalyTier tier = FluxAnomalyTier.SURFACE;

        if (args.length != 0 && args.length != 1 && args.length != 3 && args.length != 4) {
            throw new WrongUsageException(getUsage(sender));
        }

        int numberOffset = 0;
        if (args.length == 1 || args.length == 4) {
            tier = parseTier(args[0], sender);
            numberOffset = 1;
        }

        if (args.length - numberOffset == 3) {
            radius = parseInt(args[numberOffset], 8, 96);
            total  = parseInt(args[numberOffset + 1], 500, 40000);
            budget = parseInt(args[numberOffset + 2], 20, 2000);
        }

        BlockPos center = player.getPosition();
        FluxAnomalyEnvironment environment = FluxAnomalyEnvironment.STANDARD;
        if (tier == FluxAnomalyTier.ARCHIVE) {
            int dimension = player.world.provider.getDimension();
            if (dimension == 0) {
                center = new BlockPos(center.getX(), 12, center.getZ());
            } else if (dimension == -1) {
                tier = FluxAnomalyTier.LABORATORY;
                center = new BlockPos(center.getX(), 6, center.getZ());
                environment = FluxAnomalyEnvironment.NETHER_FLOOR;
            } else {
                throw new CommandException(
                        "Archive anomalies can only spawn in the Overworld or Nether.");
            }
            total = 0;
            budget = 1;
        } else if (tier == FluxAnomalyTier.LABORATORY) {
            if (player.world.provider.getDimension() != -1) {
                throw new CommandException(
                        "Laboratory anomalies can only spawn in the Nether.");
            }
            center = new BlockPos(center.getX(), 6, center.getZ());
            environment = FluxAnomalyEnvironment.NETHER_FLOOR;
            total = 0;
            budget = 1;
        } else if (tier == FluxAnomalyTier.MONOLITH) {
            if (player.world.provider.getDimension() != 1) {
                throw new CommandException(
                        "Monolith anomalies can only spawn in the End.");
            }
            center = new BlockPos(center.getX(), center.getY(), center.getZ());
            environment = FluxAnomalyEnvironment.END_ISLANDS;
            total = 0;
            budget = 1;
        }
        long chunkKey = TAInfectedChunksData.pack(center.getX() >> 4, center.getZ() >> 4);
        if (InfectedChunkAnomalyManager.hasActiveAnomalyNear(player.world, center)) {
            throw new CommandException(
                    "Another active anomaly is less than "
                            + InfectedChunkAnomalyManager.ANOMALY_SAFE_RADIUS
                            + " blocks away.");
        }

        // Спавним с кастомными параметрами
        FluxAnomalySettings settings = new FluxAnomalySettings()
                .radiusBlocks(radius)
                .totalSpreads(total)
                .budgetPerTick(budget)
                .tier(tier)
                .environment(environment)
                .sourceChunk(chunkKey)
                .spawnMethod(FluxAnomalySpawnMethod.COMMAND);
        EntityFluxAnomalyBurst anomaly = FluxAnomalyApi.spawn(player.world, center, settings);
        TAInfectedChunksData active = TAInfectedChunksData.get(player.world);
        active.markChunkActive(chunkKey);
        active.setActiveChunkTier(chunkKey, tier);
        active.trackAnomaly(anomaly.getAnomalyId(), chunkKey);
        active.setAnomalyPosition(chunkKey, center);

        sender.sendMessage(new net.minecraft.util.text.TextComponentString(
                "Spawned flux anomaly at " + center +
                        " (tier=" + tier.name().toLowerCase() +
                        ", radius=" + radius +
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
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(
                    args, "surface", "shallow", "deep", "archive", "laboratory", "monolith");
        }
        return Collections.emptyList();
    }

    private FluxAnomalyTier parseTier(String value, ICommandSender sender) throws CommandException {
        switch (value.toLowerCase()) {
            case "surface":
                return FluxAnomalyTier.SURFACE;
            case "shallow":
                return FluxAnomalyTier.SHALLOW;
            case "deep":
                return FluxAnomalyTier.DEEP;
            case "archive":
                return FluxAnomalyTier.ARCHIVE;
            case "laboratory":
                return FluxAnomalyTier.LABORATORY;
            case "monolith":
                return FluxAnomalyTier.MONOLITH;
            default:
                throw new WrongUsageException(getUsage(sender));
        }
    }
}
