package therealpant.thaumicattempts.world;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.world.data.TAWorldFluxData;

@Mod.EventBusSubscriber(modid = ThaumicAttempts.MODID)
public final class FluxResearchUnlockHandler {

    public static final String SURFACE_ANOMALIES = "f_ta_flux_10000";
    public static final String SHALLOW_ANOMALIES = "f_ta_flux_20000";
    public static final String DEEP_ANOMALIES = "f_ta_flux_30000";

    private FluxResearchUnlockHandler() {}

    public static void grantForWorld(World world, TAWorldFluxData data) {
        if (!(world instanceof WorldServer) || data == null || world.isRemote) return;

        WorldServer serverWorld = (WorldServer) world;
        for (EntityPlayerMP player : serverWorld.getMinecraftServer().getPlayerList().getPlayers()) {
            grantForPlayer(player, data.fluxGeneratedTotal);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        if (event.player.world == null || event.player.world.isRemote) return;

        WorldServer overworld = event.player.getServer().getWorld(0);
        if (overworld == null) return;
        grantForPlayer((EntityPlayerMP) event.player, TAWorldFluxData.get(overworld).fluxGeneratedTotal);
    }

    private static void grantForPlayer(EntityPlayerMP player, double totalFlux) {
        IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(player);
        if (knowledge == null) return;

        boolean changed = false;
        if (totalFlux >= TAWorldFluxData.SURFACE_ANOMALY_FLUX) {
            changed |= grantFact(knowledge, SURFACE_ANOMALIES);
        }
        if (totalFlux >= TAWorldFluxData.SHALLOW_ANOMALY_FLUX) {
            changed |= grantFact(knowledge, SHALLOW_ANOMALIES);
        }
        if (totalFlux >= TAWorldFluxData.DEEP_ANOMALY_FLUX) {
            changed |= grantFact(knowledge, DEEP_ANOMALIES);
        }

        if (changed) {
            knowledge.sync(player);
        }
    }

    private static boolean grantFact(IPlayerKnowledge knowledge, String key) {
        return !knowledge.isResearchKnown(key) && knowledge.addResearch(key);
    }
}
