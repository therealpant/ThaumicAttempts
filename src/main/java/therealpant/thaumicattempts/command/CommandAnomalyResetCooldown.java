package therealpant.thaumicattempts.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import therealpant.thaumicattempts.world.data.TAWorldFluxData;

public class CommandAnomalyResetCooldown extends CommandBase {

    @Override
    public String getName() {
        return "ta_anomaly_reset_cd";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/ta_anomaly_reset_cd";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        World world = sender.getEntityWorld();
        if (world == null || world.isRemote) return;

        TAWorldFluxData data = TAWorldFluxData.get(world);
        data.nextAnomalySpawnTime = 0;
        data.markDirty();

        sender.sendMessage(new TextComponentString(TextFormatting.GREEN +
                "Cooldown reset. nextAnomalySpawnTime=0"));
    }
}
