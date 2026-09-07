package therealpant.thaumicattempts.data.research;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.research.IScanThing;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.api.research.ScanBlock;
import thaumcraft.api.research.ScanItem;
import thaumcraft.api.research.ScanningManager;
import thaumcraft.api.items.ItemsTC;
import net.minecraftforge.oredict.OreDictionary;
import therealpant.thaumicattempts.init.TABlocks;
import therealpant.thaumicattempts.init.ModBlocksItems;
import therealpant.thaumicattempts.world.tile.TileAnomalyArchive;
import therealpant.thaumicattempts.world.tile.TileAnomalyWell;
import therealpant.thaumicattempts.world.block.BlockAnomalyWell;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Registers the blocks that reveal the anomaly field notes when scanned. */
public final class TAResearchScans {
    public static final String ARCHIVE_INFO = "!TA_ANOMALY_ARCHIVE_INFO";
    public static final String LABORATORY_INFO = "!TA_ANOMALY_LABORATORY_INFO";
    public static final String MONOLITH_INFO = "!TA_ANOMALY_MONOLITH_INFO";

    public static final String ARCHIVE_NAVIGATION = "f_ta_archive_navigation";
    public static final String ARCHIVE_ALCHEMY = "f_ta_archive_alchemy";
    public static final String ARCHIVE_TRANSMUTATION = "f_ta_archive_transmutation";
    public static final String ARCHIVE_CREATION = "f_ta_archive_creation";
    public static final String SCANNED_NAVIGATION_PEARL = "!TA_NAVIGATION_PEARL_SCAN";
    public static final String SCANNED_NAVIGATION_STAR = "!TA_NAVIGATION_STAR_SCAN";
    public static final String SCANNED_CRIMSON_RITES = "!TA_CRIMSON_RITES_SCAN";
    public static final String SCANNED_TWISTED_CURIOSITY = "!TA_TWISTED_CURIOSITY_SCAN";
    public static final String SCANNED_ELDRITCH_CURIOSITY = "!TA_ELDRITCH_CURIOSITY_SCAN";
    public static final String SCANNED_ANCIENT_CURIOSITY = "!TA_ANCIENT_CURIOSITY_SCAN";
    public static final String SCANNED_STORAGE_BELL = "!TA_STORAGE_BELL_SCAN";
    public static final String SCANNED_GOLEM_SEAL = "!TA_GOLEM_SEAL_SCAN";

    private static final String[] ARCHIVE_FACTS = {
            ARCHIVE_NAVIGATION,
            ARCHIVE_ALCHEMY,
            ARCHIVE_TRANSMUTATION,
            ARCHIVE_CREATION
    };

    private TAResearchScans() {}

    public static void register() {
        ScanningManager.addScannableThing(new ArchiveScan());
        ScanningManager.addScannableThing(new WellScan());
        ScanningManager.addScannableThing(new ScanBlock(MONOLITH_INFO, TABlocks.ANOMALOUS_MONOLITH));
        ScanningManager.addScannableThing(new ScanBlock(MONOLITH_INFO, TABlocks.STRANGE_PEDESTAL));
        ScanningManager.addScannableThing(new ScanItem(
                SCANNED_NAVIGATION_PEARL,
                new ItemStack(ItemsTC.primordialPearl, 1, OreDictionary.WILDCARD_VALUE)
        ));
        ScanningManager.addScannableThing(new ScanItem(
                SCANNED_NAVIGATION_STAR,
                new ItemStack(Items.NETHER_STAR)
        ));
        ScanningManager.addScannableThing(new ScanItem(
                SCANNED_CRIMSON_RITES,
                new ItemStack(ItemsTC.curio, 1, 6)
        ));
        ScanningManager.addScannableThing(new ScanItem(
                SCANNED_TWISTED_CURIOSITY,
                new ItemStack(ItemsTC.curio, 1, 5)
        ));
        ScanningManager.addScannableThing(new ScanItem(
                SCANNED_ELDRITCH_CURIOSITY,
                new ItemStack(ItemsTC.curio, 1, 2)
        ));
        ScanningManager.addScannableThing(new ScanItem(
                SCANNED_ANCIENT_CURIOSITY,
                new ItemStack(ItemsTC.curio, 1, 3)
        ));
        ScanningManager.addScannableThing(new ScanItem(
                SCANNED_STORAGE_BELL,
                new ItemStack(ModBlocksItems.STORAGE_BELL)
        ));
        ScanningManager.addScannableThing(new ScanItem(
                SCANNED_GOLEM_SEAL,
                new ItemStack(ItemsTC.seals, 1, OreDictionary.WILDCARD_VALUE)
        ));
    }

    /**
     * The well's visible model spans more than one block, while the actual
     * block and tile entity live only at its centre. ScanBlock therefore only
     * recognises the small central part. This scan checks the same full model
     * bounds that are used for selection and collision.
     */
    private static final class WellScan implements IScanThing {
        private static final double SCAN_DISTANCE = 16.0D;
        private final ScanBlock centreBlock = new ScanBlock(LABORATORY_INFO, TABlocks.ANOMALY_WELL);

        @Override
        public boolean checkThing(EntityPlayer player, Object thing) {
            if (centreBlock.checkThing(player, thing)) return true;
            if (!(thing instanceof RayTraceResult) && !(thing instanceof BlockPos)) return false;
            if (player == null || player.world == null) return false;

            Vec3d start = player.getPositionEyes(1.0F);
            Vec3d end = start.add(player.getLookVec().scale(SCAN_DISTANCE));
            double nearestSolidHitSq = getSolidHitDistanceSq(start, thing);

            for (TileEntity tile : player.world.loadedTileEntityList) {
                if (!(tile instanceof TileAnomalyWell)
                        || tile.getPos().distanceSq(player.getPosition()) > SCAN_DISTANCE * SCAN_DISTANCE) {
                    continue;
                }
                RayTraceResult modelHit = BlockAnomalyWell.getModelBounds(tile.getPos())
                        .calculateIntercept(start, end);
                if (modelHit != null && modelHit.hitVec != null
                        && start.squareDistanceTo(modelHit.hitVec) <= nearestSolidHitSq + 0.01D) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String getResearchKey(EntityPlayer player, Object thing) {
            return LABORATORY_INFO;
        }

        @Override
        public void onSuccess(EntityPlayer player, Object thing) {
        }

        private static double getSolidHitDistanceSq(Vec3d start, Object thing) {
            if (thing instanceof RayTraceResult) {
                RayTraceResult hit = (RayTraceResult) thing;
                if (hit.typeOfHit != RayTraceResult.Type.MISS && hit.hitVec != null) {
                    return start.squareDistanceTo(hit.hitVec);
                }
            }
            return SCAN_DISTANCE * SCAN_DISTANCE;
        }
    }

    /**
     * Keeps the archive's ordinary field-note scan, then turns a completely
     * cleared archive into a repeatable (but cooldown-limited) source of lore.
     */
    private static final class ArchiveScan implements IScanThing {
        private static final long COOLDOWN_TICKS = 6L * 60L * 20L;
        private static final String[] THEORY_CATEGORIES = {
                "INFUSION", "AUROMANCY", "ELDRITCH"
        };

        private final ScanBlock archiveBlock = new ScanBlock(TABlocks.ANOMALY_ARCHIVE);
        private final Map<UUID, String> pendingResearch = new HashMap<>();
        private final Map<UUID, Long> pendingResearchTick = new HashMap<>();

        @Override
        public boolean checkThing(EntityPlayer player, Object thing) {
            if (!archiveBlock.checkThing(player, thing)) return false;

            IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(player);
            if (knowledge == null) return false;

            // The first scan still opens the archive's informational entry.
            if (!knowledge.isResearchKnown(ARCHIVE_INFO)) return true;

            TileAnomalyArchive archive = getArchive(player, thing);
            return archive != null && !archive.hasAnyVisibleBone()
                    && !getMissingFacts(knowledge).isEmpty();
        }

        @Override
        public String getResearchKey(EntityPlayer player, Object thing) {
            IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(player);

            // ScanningManager asks for the key several times and grants it
            // between those calls. Keep the server-side answer stable until
            // onSuccess, otherwise the introductory scan can turn into a lore
            // scan halfway through the same operation.
            if (!player.world.isRemote) {
                String pending = pendingResearch.get(player.getUniqueID());
                Long pendingTick = pendingResearchTick.get(player.getUniqueID());
                if (pending != null && pendingTick != null
                        && pendingTick == player.world.getTotalWorldTime()) {
                    return pending;
                }
                pendingResearch.remove(player.getUniqueID());
                pendingResearchTick.remove(player.getUniqueID());
            }

            if (knowledge == null || !knowledge.isResearchKnown(ARCHIVE_INFO)) {
                if (!player.world.isRemote) {
                    pendingResearch.put(player.getUniqueID(), ARCHIVE_INFO);
                    pendingResearchTick.put(player.getUniqueID(), player.world.getTotalWorldTime());
                }
                return ARCHIVE_INFO;
            }

            TileAnomalyArchive archive = getArchive(player, thing);

            // A null key is TC6's supported "successful scan without a new
            // research key" path. It lets onSuccess show our cooldown result
            // without ScanningManager replacing it with "unknown object".
            if (archive != null && archive.isDecipheringOnCooldown()) {
                return null;
            }

            List<String> missing = getMissingFacts(knowledge);
            if (missing.isEmpty()) return ARCHIVE_FACTS[0];

            String selected = player.world.isRemote
                    ? missing.get(0)
                    : missing.get(player.world.rand.nextInt(missing.size()));
            if (!player.world.isRemote) {
                pendingResearch.put(player.getUniqueID(), selected);
                pendingResearchTick.put(player.getUniqueID(), player.world.getTotalWorldTime());
            }
            return selected;
        }

        @Override
        public void onSuccess(EntityPlayer player, Object thing) {
            String awarded = pendingResearch.remove(player.getUniqueID());
            pendingResearchTick.remove(player.getUniqueID());
            if (awarded == null) {
                player.sendStatusMessage(
                        new TextComponentTranslation("thaumicattempts.archive_scan.cooldown"), true);
                return;
            }
            if (ARCHIVE_INFO.equals(awarded)) return;

            TileAnomalyArchive archive = getArchive(player, thing);
            if (archive != null) archive.startDecipheringCooldown(COOLDOWN_TICKS);

            IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(player);
            if (knowledge == null) return;

            String categoryKey = THEORY_CATEGORIES[player.world.rand.nextInt(THEORY_CATEGORIES.length)];
            ResearchCategory category = ResearchCategories.getResearchCategory(categoryKey);
            if (category != null) {
                knowledge.addKnowledge(IPlayerKnowledge.EnumKnowledgeType.THEORY, category, 1);
            }
            if (player instanceof EntityPlayerMP) knowledge.sync((EntityPlayerMP) player);

        }

        private static List<String> getMissingFacts(IPlayerKnowledge knowledge) {
            List<String> missing = new ArrayList<>();
            for (String fact : ARCHIVE_FACTS) {
                if (!knowledge.isResearchKnown(fact)) missing.add(fact);
            }
            return missing;
        }

        private static TileAnomalyArchive getArchive(EntityPlayer player, Object thing) {
            BlockPos pos = null;
            if (thing instanceof RayTraceResult) {
                pos = ((RayTraceResult) thing).getBlockPos();
            } else if (thing instanceof BlockPos) {
                pos = (BlockPos) thing;
            }
            if (pos == null) {
                RayTraceResult hit = player.rayTrace(16.0D, 1.0F);
                if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK) {
                    pos = hit.getBlockPos();
                }
            }
            if (pos == null) return null;
            TileEntity tile = player.world.getTileEntity(pos);
            return tile instanceof TileAnomalyArchive ? (TileAnomalyArchive) tile : null;
        }

    }
}
