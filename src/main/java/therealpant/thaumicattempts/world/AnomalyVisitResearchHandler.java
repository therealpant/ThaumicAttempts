package therealpant.thaumicattempts.world;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.init.TABlocks;

/** Grants reusable research facts when a player physically visits an anomaly. */
@Mod.EventBusSubscriber(modid = ThaumicAttempts.MODID)
public final class AnomalyVisitResearchHandler {
    public static final String VISITED_ARCHIVE = "m_ta_visited_anomaly_archive";
    public static final String VISITED_LABORATORY = "m_ta_visited_anomaly_laboratory";
    public static final String VISITED_MONOLITH = "m_ta_visited_anomaly_monolith";

    private static final String LEGACY_VISITED_ARCHIVE = "f_ta_visited_anomaly_archive";
    private static final String LEGACY_VISITED_LABORATORY = "f_ta_visited_anomaly_laboratory";
    private static final String LEGACY_VISITED_MONOLITH = "f_ta_visited_anomaly_monolith";

    private static final int CHECK_INTERVAL = 20;
    private static final int VISIT_RADIUS = 4;
    private static final double VISIT_RADIUS_SQ = VISIT_RADIUS * VISIT_RADIUS;

    private AnomalyVisitResearchHandler() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP)) return;
        if (event.player.world == null || event.player.world.isRemote
                || event.player.ticksExisted % CHECK_INTERVAL != 0) return;

        EntityPlayerMP player = (EntityPlayerMP) event.player;
        IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(player);
        if (knowledge == null) return;

        // Facts used the f_ prefix in older builds, which made Thaumcraft
        // render them as flask requirements. Preserve completed visits while
        // migrating them to the vanilla milestone-card m_ format.
        boolean changed = migrateLegacyVisit(knowledge, LEGACY_VISITED_ARCHIVE, VISITED_ARCHIVE);
        changed |= migrateLegacyVisit(knowledge, LEGACY_VISITED_LABORATORY, VISITED_LABORATORY);
        changed |= migrateLegacyVisit(knowledge, LEGACY_VISITED_MONOLITH, VISITED_MONOLITH);

        boolean needsArchive = !knowledge.isResearchKnown(VISITED_ARCHIVE);
        boolean needsLaboratory = !knowledge.isResearchKnown(VISITED_LABORATORY);
        boolean needsMonolith = !knowledge.isResearchKnown(VISITED_MONOLITH);
        if (!needsArchive && !needsLaboratory && !needsMonolith) {
            if (changed) knowledge.sync(player);
            return;
        }
        BlockPos center = player.getPosition();
        BlockPos min = center.add(-VISIT_RADIUS, -VISIT_RADIUS, -VISIT_RADIUS);
        BlockPos max = center.add(VISIT_RADIUS, VISIT_RADIUS, VISIT_RADIUS);

        for (BlockPos candidate : BlockPos.getAllInBoxMutable(min, max)) {
            if (player.getDistanceSqToCenter(candidate) > VISIT_RADIUS_SQ) continue;
            Block block = player.world.getBlockState(candidate).getBlock();

            if (needsArchive && block == TABlocks.ANOMALY_ARCHIVE) {
                changed |= knowledge.addResearch(VISITED_ARCHIVE);
                needsArchive = false;
            } else if (needsLaboratory && block == TABlocks.ANOMALY_WELL) {
                changed |= knowledge.addResearch(VISITED_LABORATORY);
                needsLaboratory = false;
            } else if (needsMonolith && block == TABlocks.ANOMALOUS_MONOLITH) {
                changed |= knowledge.addResearch(VISITED_MONOLITH);
                needsMonolith = false;
            }

            if (!needsArchive && !needsLaboratory && !needsMonolith) break;
        }

        if (changed) knowledge.sync(player);
    }

    private static boolean migrateLegacyVisit(IPlayerKnowledge knowledge, String legacyKey, String milestoneKey) {
        return !knowledge.isResearchKnown(milestoneKey)
                && knowledge.isResearchKnown(legacyKey)
                && knowledge.addResearch(milestoneKey);
    }
}
