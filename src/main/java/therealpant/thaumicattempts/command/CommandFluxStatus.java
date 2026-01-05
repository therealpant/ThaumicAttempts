package therealpant.thaumicattempts.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import therealpant.thaumicattempts.world.FluxChunkSampler;
import therealpant.thaumicattempts.world.data.TAWorldFluxData;

public class CommandFluxStatus extends CommandBase {

    @Override
    public String getName() {
        return "ta_flux_status";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/ta_flux_status";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2; // как у большинства админ-команд
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        World world = sender.getEntityWorld();
        if (world == null || world.isRemote) return;

        TAWorldFluxData data = TAWorldFluxData.get(world);

        long now = world.getTotalWorldTime();
        long next = data.nextAnomalySpawnTime;
        long left = Math.max(0, next - now);
        boolean can = data.canTrySpawn(world);

        sender.sendMessage(new TextComponentString(TextFormatting.GOLD + "=== ThaumicAttempts: Flux status ==="));
        sender.sendMessage(new TextComponentString("WorldTime: " + now));
        sender.sendMessage(new TextComponentString("fluxGeneratedTotal: " + TextFormatting.AQUA + (long)data.fluxGeneratedTotal));
        sender.sendMessage(new TextComponentString("stage: " + TextFormatting.AQUA + data.stage));
        sender.sendMessage(new TextComponentString("nextAnomalySpawnTime: " + next + " (in " + left + " ticks)"));
        sender.sendMessage(new TextComponentString("canTrySpawn NOW: " + (can ? TextFormatting.GREEN + "YES" : TextFormatting.RED + "NO")));

        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "--- Sampler ---"));
        sender.sendMessage(new TextComponentString("lastSampleWorldTime: " + FluxChunkSampler.lastSampleWorldTime));
        sender.sendMessage(new TextComponentString("lastBatchProcessed: " + FluxChunkSampler.lastBatchProcessed));
        sender.sendMessage(new TextComponentString("loadedChunks(current): " + FluxChunkSampler.lastKnownLoadedChunks));
        sender.sendMessage(new TextComponentString("snapshotChunks(last): " + FluxChunkSampler.lastLoadedChunksSnapshot));
        sender.sendMessage(new TextComponentString("trackedFluxChunks(map size): " + FluxChunkSampler.lastTrackedFluxChunks));
    }
}
