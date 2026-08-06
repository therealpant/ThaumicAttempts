// src/main/java/therealpant/thaumicattempts/client/ClientModels.java
package therealpant.thaumicattempts.client;

import net.minecraft.block.state.IBlockState;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.model.ModelBakery;
import net.minecraft.client.renderer.block.statemap.StateMap;
import net.minecraft.client.renderer.block.statemap.StateMapperBase;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;

import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.client.render.*;
import therealpant.thaumicattempts.init.ModBlocksItems;
import therealpant.thaumicattempts.items.ItemPrism;
import therealpant.thaumicattempts.items.ItemRiftonomiconProtocol;
import therealpant.thaumicattempts.items.ItemAlchemicalCore;
import therealpant.thaumicattempts.items.ItemCalibratedExtract;
import therealpant.thaumicattempts.golemnet.block.BlockMathCore;
import therealpant.thaumicattempts.golemnet.block.BlockMirrorStabilizer;
import therealpant.thaumicattempts.golemnet.tile.*;

import therealpant.thaumicattempts.init.TABlocks;
import therealpant.thaumicattempts.proxy.CommonProxy;
import therealpant.thaumicattempts.tile.TilePillar;
import therealpant.thaumicattempts.world.tile.TileAnomalyStone;
import therealpant.thaumicattempts.world.tile.TileAuraBooster;
import therealpant.thaumicattempts.world.tile.TileRiftExtractor;
import therealpant.thaumicattempts.world.tile.TileRiftGeod;
import therealpant.thaumicattempts.world.tile.TileAnomalyArchive;
import therealpant.thaumicattempts.world.tile.TileAncientColumn;
import therealpant.thaumicattempts.world.tile.TileAncientFire;
import therealpant.thaumicattempts.world.tile.TileAnomalyWell;
import therealpant.thaumicattempts.world.tile.TileAnomalousMonolith;
import therealpant.thaumicattempts.world.tile.TileStrangePedestal;
import therealpant.thaumicattempts.world.tile.TileRiftPortalPlatform;
import therealpant.thaumicattempts.world.tile.TileRiftBush;
import therealpant.thaumicattempts.world.tile.TileRiftonomicon;
import therealpant.thaumicattempts.world.tile.TileRiftonomiconCorePlaceholder;
import therealpant.thaumicattempts.world.tile.TileRiftStoneAltar;
import therealpant.thaumicattempts.world.tile.TileRiftStoneFurnace;
import therealpant.thaumicattempts.world.tile.TileRiftStonePortal;
import therealpant.thaumicattempts.world.tile.TileEldritchConstruction;


import java.util.function.Supplier;

@Mod.EventBusSubscriber(value = Side.CLIENT, modid = ThaumicAttempts.MODID)
public final class ClientModels extends CommonProxy {

    @SubscribeEvent
    public static void onModelRegistry(ModelRegistryEvent e) {
        /* ---------- ITEM-модели (иконки в инвентаре) ---------- */

        // простые предметы
        ModelLoader.setCustomModelResourceLocation(
                ModBlocksItems.CRAFT_PATTERN, 0,
                new ModelResourceLocation(ThaumicAttempts.MODID + ":craft_pattern", "inventory")
        );
        ModelLoader.setCustomModelResourceLocation(
                ModBlocksItems.INFUSION_PATTERN, 0,
                new ModelResourceLocation(ThaumicAttempts.MODID + ":infusion_pattern", "inventory")
        );
        ModelLoader.setCustomModelResourceLocation(
                ModBlocksItems.ARCANE_PATTERN, 0,
                new ModelResourceLocation(ThaumicAttempts.MODID + ":arcane_pattern", "inventory")
        );
        ModelLoader.setCustomModelResourceLocation(
                ModBlocksItems.RESOURCE_LIST, 0,
                new ModelResourceLocation(ThaumicAttempts.MODID + ":resource_list", "inventory")
        );
        ModelLoader.setCustomModelResourceLocation(
                ModBlocksItems.RIFT_FLOWER, 0,
                new ModelResourceLocation(ThaumicAttempts.MODID + ":rift_flover", "inventory")
        );
        ModelLoader.setCustomModelResourceLocation(
                ModBlocksItems.RIFT_STONE, 0,
                new ModelResourceLocation(ThaumicAttempts.MODID + ":rift_stone", "inventory")
        );
        ModelLoader.setCustomModelResourceLocation(
                ModBlocksItems.RIFT_CRISTAL, 0,
                new ModelResourceLocation(ThaumicAttempts.MODID + ":rift_cristal", "inventory")
        );
        ModelLoader.setCustomModelResourceLocation(
                ModBlocksItems.RIFT_EMBER, 0,
                new ModelResourceLocation(ThaumicAttempts.MODID + ":rift_ember", "inventory")
        );
        ModelLoader.setCustomModelResourceLocation(
                ModBlocksItems.RIFT_AMETIST, 0,
                new ModelResourceLocation(ThaumicAttempts.MODID + ":rift_ametist", "inventory")
        );
        ModelLoader.setCustomModelResourceLocation(
                ModBlocksItems.RIFT_BRILIANT, 0,
                new ModelResourceLocation(ThaumicAttempts.MODID + ":rift_briliant", "inventory")
        );
        ModelLoader.setCustomModelResourceLocation(
                ModBlocksItems.ANOMALY_SEEDS, 0,
                new ModelResourceLocation(ThaumicAttempts.MODID + ":ta_anomaly_seeds", "inventory")
        );
        ModelLoader.setCustomModelResourceLocation(
                ModBlocksItems.MIND_FRUIT, 0,
                new ModelResourceLocation(ThaumicAttempts.MODID + ":ta_mind_fruit", "inventory")
        );
        ModelLoader.setCustomModelResourceLocation(
                ModBlocksItems.MATURE_MIND_FRUIT, 0,
                new ModelResourceLocation(ThaumicAttempts.MODID + ":ta_mature_mind_fruit", "inventory")
        );
        ModelLoader.setCustomModelResourceLocation(
                ModBlocksItems.TAINTED_MIND_FRUIT, 0,
                new ModelResourceLocation(ThaumicAttempts.MODID + ":ta_tainted_mind_fruit", "inventory")
        );
        ModelLoader.setCustomModelResourceLocation(
                ModBlocksItems.MIND_POTION, 0,
                new ModelResourceLocation(ThaumicAttempts.MODID + ":mind_potion", "inventory")
        );
        ModelLoader.setCustomModelResourceLocation(
                ModBlocksItems.TA_GEM, 0,
                new ModelResourceLocation(ThaumicAttempts.MODID + ":gem_amber_1", "inventory")
        );
        ModelLoader.setCustomModelResourceLocation(
                ModBlocksItems.TA_GEM, 1,
                new ModelResourceLocation(ThaumicAttempts.MODID + ":gem_amber_2", "inventory")
        );
        ModelLoader.setCustomModelResourceLocation(
                ModBlocksItems.TA_GEM, 2,
                new ModelResourceLocation(ThaumicAttempts.MODID + ":gem_amber_3", "inventory")
        );
        ModelLoader.setCustomModelResourceLocation(
                ModBlocksItems.TA_GEM, 3,
                new ModelResourceLocation(ThaumicAttempts.MODID + ":gem_amethyst_1", "inventory")
        );
        ModelLoader.setCustomModelResourceLocation(
                ModBlocksItems.TA_GEM, 4,
                new ModelResourceLocation(ThaumicAttempts.MODID + ":gem_amethyst_2", "inventory")
        );
        ModelLoader.setCustomModelResourceLocation(
                ModBlocksItems.TA_GEM, 5,
                new ModelResourceLocation(ThaumicAttempts.MODID + ":gem_amethyst_3", "inventory")
        );
        ModelLoader.setCustomModelResourceLocation(
                ModBlocksItems.TA_GEM, 6,
                new ModelResourceLocation(ThaumicAttempts.MODID + ":gem_diamond_1", "inventory")
        );
        ModelLoader.setCustomModelResourceLocation(
                ModBlocksItems.TA_GEM, 7,
                new ModelResourceLocation(ThaumicAttempts.MODID + ":gem_diamond_2", "inventory")
        );
        ModelLoader.setCustomModelResourceLocation(
                ModBlocksItems.TA_GEM, 8,
                new ModelResourceLocation(ThaumicAttempts.MODID + ":gem_diamond_3", "inventory")
        );
        ModelLoader.setCustomModelResourceLocation(
                ModBlocksItems.STORAGE_BELL, 0,
                new ModelResourceLocation(ThaumicAttempts.MODID + ":storage_bell", "inventory")
        );
        ModelLoader.setCustomModelResourceLocation(
                ModBlocksItems.PORTAL_RUNE, 0,
                new ModelResourceLocation(ThaumicAttempts.MODID + ":portal_rune", "inventory")
        );
        ModelResourceLocation sigilModel = new ModelResourceLocation(
                ThaumicAttempts.MODID + ":calibrated_extract", "inventory");
        ModelResourceLocation sigilStageOneModel = new ModelResourceLocation(
                ThaumicAttempts.MODID + ":calibrated_extract_stage_1", "inventory");
        ModelResourceLocation sigilStageTwoModel = new ModelResourceLocation(
                ThaumicAttempts.MODID + ":calibrated_extract_stage_2", "inventory");
        ModelBakery.registerItemVariants(ModBlocksItems.CALIBRATED_EXTRACT,
                sigilModel, sigilStageOneModel, sigilStageTwoModel);
        ModelLoader.setCustomMeshDefinition(ModBlocksItems.CALIBRATED_EXTRACT, stack -> {
            int stage = ItemCalibratedExtract.getCraftingStage(stack);
            if (stage == ItemCalibratedExtract.CRAFTING_STAGE_CORE_STONE) return sigilStageOneModel;
            if (stage == ItemCalibratedExtract.CRAFTING_STAGE_PARTIAL_SIGIL) return sigilStageTwoModel;
            return sigilModel;
        });
        ModelLoader.setCustomModelResourceLocation(
                ModBlocksItems.ANOMALY_COMPASS, 0,
                new ModelResourceLocation(ThaumicAttempts.MODID + ":anomaly_compass", "inventory")
        );
        ModelLoader.setCustomModelResourceLocation(
                ModBlocksItems.ARCHIVE_FRAGMENTS, 0,
                new ModelResourceLocation(ThaumicAttempts.MODID + ":archive_fragments", "inventory")
        );
        registerItemModel(ModBlocksItems.TRANSMUTATION_CORE);
        registerItemModel(ModBlocksItems.CREATION_CORE);
        registerItemModel(ModBlocksItems.NAVIGATION_CORE);
        for (int wear = 0; wear < ItemAlchemicalCore.USES; wear++) {
            String model = wear >= ItemAlchemicalCore.CRITICAL_STAGE_START
                    ? "alchemical_core_critical"
                    : wear >= ItemAlchemicalCore.DAMAGED_STAGE_START
                    ? "alchemical_core_damaged" : "alchemical_core";
            ModelLoader.setCustomModelResourceLocation(
                    ModBlocksItems.ALCHEMICAL_CORE, wear,
                    new ModelResourceLocation(ThaumicAttempts.MODID + ":" + model, "inventory")
            );
        }
        ModBlocksItems.ANOMALY_COMPASS.setTileEntityItemStackRenderer(new RenderAnomalyCompass());
        registerPrismModel(ItemPrism.META_AIR, "prism_air");
        registerPrismModel(ItemPrism.META_AQUA, "prism_aqua");
        registerPrismModel(ItemPrism.META_IGNIS, "prism_ignis");
        registerPrismModel(ItemPrism.META_TERRA, "prism");
        registerProtocolModel(ItemRiftonomiconProtocol.META_NAVIGATION_PROTOCOL);
        registerProtocolModel(ItemRiftonomiconProtocol.META_SPATIAL_SUBSYSTEM);
        registerProtocolModel(ItemRiftonomiconProtocol.META_ALCHEMICAL_PROTOCOL);
        registerProtocolModel(ItemRiftonomiconProtocol.META_CATALYZING_SUBSYSTEM);
        registerProtocolModel(ItemRiftonomiconProtocol.META_TRANSMUTATION_PROTOCOL);
        registerProtocolModel(ItemRiftonomiconProtocol.META_PRISMATIC_SUBSYSTEM);
        registerProtocolModel(ItemRiftonomiconProtocol.META_EXTRACTION_PROTOCOL);
        registerProtocolModel(ItemRiftonomiconProtocol.META_SCALING_SUBSYSTEM);

        // ItemBlock'и наших блоков (иконки!)
        registerItemBlockModel(ModBlocksItems.GOLEM_CRAFTER, ThaumicAttempts.MODID + ":golem_crafter");
        registerItemBlockModel(ModBlocksItems.ARCANE_CRAFTER, ThaumicAttempts.MODID + ":arcane_crafter");
        registerItemBlockModel(ModBlocksItems.MATH_CORE, ThaumicAttempts.MODID + ":math_core");
        registerItemBlockModel(ModBlocksItems.MIRROR_STABILIZER, ThaumicAttempts.MODID + ":mirror_stabilizer");
        registerItemBlockModel(TABlocks.RIFT_BUSH, ThaumicAttempts.MODID + ":rift_bush");
        ModelLoader.setCustomModelResourceLocation(
                TABlocks.ELDRITCH_CONSTRUCTION_ITEM, 0,
                new ModelResourceLocation(ThaumicAttempts.MODID + ":eldritch_constarction", "inventory"));
        for (int meta = 1; meta <= 3; meta++) {
            String variant = meta == 1 ? "amber" : meta == 2 ? "amethyst" : "diamond";
            ModelLoader.setCustomModelResourceLocation(
                    TABlocks.ELDRITCH_CONSTRUCTION_ITEM, meta,
                    new ModelResourceLocation(
                            ThaumicAttempts.MODID + ":eldritch_constarction_" + variant,
                            "inventory"));
        }
        registerItemBlockModel(TABlocks.ANOMALY_BED, ThaumicAttempts.MODID + ":ta_anomaly_bed");
        registerItemBlockModel(TABlocks.RIFT_CRISTAL_BLOCK, ThaumicAttempts.MODID + ":rist_cristal_block");
        registerItemBlockModel(TABlocks.POLISHED_DARK_JASPER, ThaumicAttempts.MODID + ":polished_dark_jasper");

        // ухо — используем таумовскую иконку предмета
        ModelLoader.setCustomModelResourceLocation(
                Item.getItemFromBlock(ModBlocksItems.EAR_BAND), 0,
                new ModelResourceLocation("thaumcraft:arcane_ear", "inventory")
        );
        ModelLoader.setCustomModelResourceLocation(
                Item.getItemFromBlock(TABlocks.RESOURCE_REQUESTER), 0,
                new ModelResourceLocation("thaumicattempts:resource_requester", "inventory")
        );
        ModelLoader.setCustomModelResourceLocation(
                Item.getItemFromBlock(TABlocks.INFUSION_REQUESTER), 0,
                new ModelResourceLocation("thaumicattempts:infusion_requester", "inventory")
        );

        // предметы-«тайлы»
        registerItemModel(TABlocks.MIRROR_MANAGER_ITEM);
        attachTileRenderer(TABlocks.MIRROR_MANAGER_ITEM, TileMirrorManager::new);
        registerItemModel(TABlocks.ORDER_TERMINAL_ITEM);
        registerItemModel(TABlocks.PATTERN_REQUESTER_ITEM);
        attachTileRenderer(TABlocks.PATTERN_REQUESTER_ITEM, TilePatternRequester::new);
        registerItemModel(TABlocks.RESOURCE_REQUESTER_ITEM);
        attachTileRenderer(TABlocks.RESOURCE_REQUESTER_ITEM, TileResourceRequester::new);
        registerItemModel(TABlocks.INFUSION_REQUESTER_ITEM);
        attachTileRenderer(TABlocks.INFUSION_REQUESTER_ITEM, TileInfusionRequester::new);
        registerItemModel(TABlocks.GOLEM_DISPATCHER_ITEM);
        registerItemModel(TABlocks.CRAFT_PLANNER_ITEM);
        attachTileRenderer(TABlocks.GOLEM_DISPATCHER_ITEM, TileGolemDispatcher::new);
        attachTileRenderer(TABlocks.CRAFT_PLANNER_ITEM, TileCraftPlanner::new);
        registerItemModel(TABlocks.ANOMALY_STONE_ITEM);
        attachTileRenderer(TABlocks.ANOMALY_STONE_ITEM, therealpant.thaumicattempts.world.tile.TileAnomalyStone::new);
        registerItemModel(TABlocks.RIFT_GEOD_ITEM);
        attachTileRenderer(TABlocks.RIFT_GEOD_ITEM, TileRiftGeod::new);
        registerItemModel(TABlocks.ANOMALY_ARCHIVE_ITEM);
        attachTileRenderer(TABlocks.ANOMALY_ARCHIVE_ITEM, TileAnomalyArchive::new);
        registerItemModel(TABlocks.ANCIENT_COLUMN_ITEM);
        attachTileRenderer(TABlocks.ANCIENT_COLUMN_ITEM, TileAncientColumn::new);
        registerItemModel(TABlocks.ANCIENT_FIRE_ITEM);
        attachTileRenderer(TABlocks.ANCIENT_FIRE_ITEM, TileAncientFire::new);
        registerItemModel(TABlocks.ANOMALY_WELL_ITEM);
        attachTileRenderer(TABlocks.ANOMALY_WELL_ITEM, TileAnomalyWell::new);
        registerItemModel(TABlocks.ANOMALOUS_MONOLITH_ITEM);
        attachTileRenderer(TABlocks.ANOMALOUS_MONOLITH_ITEM, TileAnomalousMonolith::new);
        registerItemModel(TABlocks.STRANGE_PEDESTAL_ITEM);
        attachTileRenderer(TABlocks.STRANGE_PEDESTAL_ITEM, TileStrangePedestal::new);
        registerItemModel(TABlocks.AURA_BOOSTER_ITEM);
        attachTileRenderer(TABlocks.AURA_BOOSTER_ITEM, TileAuraBooster::new);
        registerItemModel(TABlocks.RIFT_EXTRACTOR_ITEM);
        attachTileRenderer(TABlocks.RIFT_EXTRACTOR_ITEM, TileRiftExtractor::new);
        registerItemModel(TABlocks.REVISION_PIEDESTAL_ITEM);
        attachTileRenderer(TABlocks.REVISION_PIEDESTAL_ITEM, TileRevisionPiedestal::new);
        registerItemModel(TABlocks.RIFT_STONE_PORTAL_ITEM);
        attachTileRenderer(TABlocks.RIFT_STONE_PORTAL_ITEM, TileRiftStonePortal::new);
        registerItemModel(TABlocks.RIFT_PORTAL_PLATFORM_ITEM);
        attachTileRenderer(TABlocks.RIFT_PORTAL_PLATFORM_ITEM, TileRiftPortalPlatform::new);
        registerItemModel(TABlocks.RIFT_STONE_FURNACE_ITEM);
        attachTileRenderer(TABlocks.RIFT_STONE_FURNACE_ITEM, TileRiftStoneFurnace::new);
        registerItemModel(TABlocks.RIFT_STONE_ALTAR_ITEM);
        attachTileRenderer(TABlocks.RIFT_STONE_ALTAR_ITEM, TileRiftStoneAltar::new);
        registerItemModel(TABlocks.RIFTONOMICON_ITEM);
        attachTileRenderer(TABlocks.RIFTONOMICON_ITEM, TileRiftonomicon::new);
        registerItemModel(TABlocks.RIFTONOMICON_CORE_PLACEHOLDER_ITEM);
        attachTileRenderer(TABlocks.RIFTONOMICON_CORE_PLACEHOLDER_ITEM, TileRiftonomiconCorePlaceholder::new);
        /* ---------- StateMappers (рендер БЛОКА в мире) ---------- */

        // наши крафтеры: игнорируем таумовский ENABLED (если присутствует)
        ModelLoader.setCustomStateMapper(
                ModBlocksItems.GOLEM_CRAFTER,
                new StateMap.Builder()
                        .ignore(thaumcraft.common.blocks.IBlockEnabled.ENABLED)
                        .build()
        );
        ModelLoader.setCustomStateMapper(
                ModBlocksItems.ARCANE_CRAFTER,
                new StateMap.Builder()
                        .ignore(thaumcraft.common.blocks.IBlockEnabled.ENABLED)
                        .build()
        );

        // ВАЖНО: для ядра и стабилизатора игнорируем ТОЛЬКО наше свойство ACTIVATOR.
        // Свойство active остаётся — оно выбирает модель (active=true/false).
        // СТАБИЛИЗАТОР ЗЕРКАЛ
        ModelLoader.setCustomStateMapper(
                ModBlocksItems.MIRROR_STABILIZER,
                (new StateMap.Builder()).ignore(BlockMirrorStabilizer.SIG).build()
        );

// МАТ. ЯДРО
        ModelLoader.setCustomStateMapper(
                ModBlocksItems.MATH_CORE,
                (new StateMap.Builder()).ignore(BlockMathCore.SIG).build()
        );

        // Таумовское «ухо»: у блока нет ENABLED, мапим все состояния на готовый ресурс.
        ModelLoader.setCustomStateMapper(
                ModBlocksItems.EAR_BAND,
                new StateMapperBase() {
                    @Override
                    protected ModelResourceLocation getModelResourceLocation(IBlockState state) {
                        // Thaumcraft сам разрулит варианты через свой blockstate
                        return new ModelResourceLocation("thaumcraft:arcane_ear");
                    }
                }
        );

        /* ---------- TESR ---------- */
        ClientRegistry.bindTileEntitySpecialRenderer(
                TileMirrorManager.class,
                new RenderMirrorManagerGeo());
        ClientRegistry.bindTileEntitySpecialRenderer(
                TilePatternRequester.class,
                new RenderPatternRequesterGeo()
        );
        ClientRegistry.bindTileEntitySpecialRenderer(
                TileResourceRequester.class,
                new RenderResourceRequester()
        );
        ClientRegistry.bindTileEntitySpecialRenderer(
                    TileGolemDispatcher.class,
                    new DispatcherRenderer()
        );
        ClientRegistry.bindTileEntitySpecialRenderer(
                TileCraftPlanner.class,
                new CraftPlannerRenderer()
        );
        ClientRegistry.bindTileEntitySpecialRenderer(
                TileInfusionRequester.class,
                new RenderInfusionRequester()
        );
        ClientRegistry.bindTileEntitySpecialRenderer(
                TilePillar.class,
                new RenderPillar()
        );
        ClientRegistry.bindTileEntitySpecialRenderer(
                TileAnomalyStone.class,
                new RenderAnomalyStone()
        );
        ClientRegistry.bindTileEntitySpecialRenderer(
                TileRiftBush.class,
                new RenderRiftBush()
        );
        ClientRegistry.bindTileEntitySpecialRenderer(
                TileRiftGeod.class,
                new RenderRiftGeod()
        );
        ClientRegistry.bindTileEntitySpecialRenderer(
                TileAnomalyArchive.class,
                new RenderAnomalyArchive()
        );
        ClientRegistry.bindTileEntitySpecialRenderer(
                TileAncientColumn.class,
                new RenderAncientColumn()
        );
        ClientRegistry.bindTileEntitySpecialRenderer(
                TileAncientFire.class,
                new RenderAncientFire()
        );
        ClientRegistry.bindTileEntitySpecialRenderer(
                TileAnomalyWell.class,
                new RenderAnomalyWell()
        );
        ClientRegistry.bindTileEntitySpecialRenderer(
                TileAnomalousMonolith.class,
                new RenderAnomalousMonolith()
        );
        ClientRegistry.bindTileEntitySpecialRenderer(
                TileStrangePedestal.class,
                new RenderStrangePedestal()
        );
        ClientRegistry.bindTileEntitySpecialRenderer(
                TileEldritchConstruction.class,
                new RenderEldritchConstruction()
        );
        ClientRegistry.bindTileEntitySpecialRenderer(
                TileAuraBooster.class,
                new RenderAuraBoosterGeo()
        );
        ClientRegistry.bindTileEntitySpecialRenderer(
                TileRiftExtractor.class,
                new RenderRiftExtractor()
        );
        ClientRegistry.bindTileEntitySpecialRenderer(
                TileRevisionPiedestal.class,
                new RenderRevisionPiedestalGeo()
        );
        ClientRegistry.bindTileEntitySpecialRenderer(
                TileRiftStonePortal.class,
                new RenderRiftStonePortal()
        );
        ClientRegistry.bindTileEntitySpecialRenderer(
                TileRiftPortalPlatform.class,
                new RenderRiftPortalPlatform()
        );
        ClientRegistry.bindTileEntitySpecialRenderer(
                TileRiftStoneFurnace.class,
                new RenderRiftStoneFurnace()
        );
        ClientRegistry.bindTileEntitySpecialRenderer(
                TileRiftStoneAltar.class,
                new RenderRiftStoneAltar()
        );
        ClientRegistry.bindTileEntitySpecialRenderer(
                TileRiftonomicon.class,
                new RenderRiftonomicon()
        );
        ClientRegistry.bindTileEntitySpecialRenderer(
                TileRiftonomiconCorePlaceholder.class,
                new RenderRiftonomiconCorePlaceholder()
        );
    }

    private static void registerPrismModel(int meta, String model) {
        ModelLoader.setCustomModelResourceLocation(
                ModBlocksItems.PRISM, meta,
                new ModelResourceLocation(ThaumicAttempts.MODID + ":" + model, "inventory")
        );
    }

    private static void registerProtocolModel(int meta) {
        ModelLoader.setCustomModelResourceLocation(
                ModBlocksItems.RIFTONOMICON_PROTOCOL, meta,
                new ModelResourceLocation(ThaumicAttempts.MODID + ":protocol_" + meta, "inventory")
        );
    }

    private static void registerItemBlockModel(net.minecraft.block.Block block, String path) {
        ModelLoader.setCustomModelResourceLocation(
                Item.getItemFromBlock(block), 0,
                new ModelResourceLocation(path, "inventory")
        );
    }

    private static void registerItemModel(Item item) {
        ModelLoader.setCustomModelResourceLocation(
                item, 0, new ModelResourceLocation(item.getRegistryName(), "inventory")
        );
    }

    private static void attachTileRenderer(Item item, Supplier<? extends net.minecraft.tileentity.TileEntity> factory) {
        item.setTileEntityItemStackRenderer(new TileItemStackRenderer<>(factory));
    }
}
