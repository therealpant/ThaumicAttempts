package therealpant.thaumicattempts.init;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.golemcraft.block.BlockArcaneEarBand;
import therealpant.thaumicattempts.golemcraft.block.BlockGolemCrafter;
import therealpant.thaumicattempts.golemcraft.block.BlockArcaneCrafter;
import therealpant.thaumicattempts.golemcraft.item.ItemCraftPattern;
import therealpant.thaumicattempts.golemcraft.item.ItemInfusionPattern;
import therealpant.thaumicattempts.golemcraft.item.ItemArcanePattern;
import therealpant.thaumicattempts.golemcraft.item.ItemResourceList;
import therealpant.thaumicattempts.golemnet.block.*;
import therealpant.thaumicattempts.world.block.*;


import therealpant.thaumicattempts.golemnet.tile.*;
import therealpant.thaumicattempts.world.tile.TileRiftBush;
import therealpant.thaumicattempts.world.tile.TileRiftGeod;
import therealpant.thaumicattempts.world.tile.TileAnomalyArchive;
import therealpant.thaumicattempts.world.tile.TileAncientColumn;
import therealpant.thaumicattempts.world.tile.TileAncientFire;
import therealpant.thaumicattempts.world.tile.TileAnomalyWell;
import therealpant.thaumicattempts.world.tile.TileAnomalousMonolith;
import therealpant.thaumicattempts.world.tile.TileStrangePedestal;

import therealpant.thaumicattempts.items.ItemStorageBell;
import therealpant.thaumicattempts.items.ItemTAGem;
import therealpant.thaumicattempts.items.ItemPortalRune;
import therealpant.thaumicattempts.items.ItemPrism;
import therealpant.thaumicattempts.items.ItemRiftonomiconProtocol;
import therealpant.thaumicattempts.items.ItemCalibratedExtract;
import therealpant.thaumicattempts.items.ItemAnomalyCompass;
import therealpant.thaumicattempts.items.ItemBlockEldritchConstruction;

import therealpant.thaumicattempts.world.tile.TileAnomalyStone;
import therealpant.thaumicattempts.world.tile.TileAnomalyCrop;
import therealpant.thaumicattempts.world.tile.TileEldritchConstruction;
import therealpant.thaumicattempts.world.tile.TileAuraBooster;
import therealpant.thaumicattempts.world.tile.TileRiftPortalPlatform;
import therealpant.thaumicattempts.world.tile.TileRiftonomicon;
import therealpant.thaumicattempts.world.tile.TileRiftonomiconCorePlaceholder;
import therealpant.thaumicattempts.world.tile.TileRiftStoneAltar;
import therealpant.thaumicattempts.world.tile.TileRiftStoneFurnace;
import therealpant.thaumicattempts.world.tile.TileRiftStoneFurnacePort;
import therealpant.thaumicattempts.world.tile.TileRiftStonePortal;
import therealpant.thaumicattempts.world.item.ItemAnomalySeeds;
import therealpant.thaumicattempts.world.item.ItemMindFruitFood;
import therealpant.thaumicattempts.world.item.ItemMindPotion;
import therealpant.thaumicattempts.world.item.ItemRiftAmethyst;
import therealpant.thaumicattempts.world.item.ItemTaintedMindFruitFood;

@Mod.EventBusSubscriber(modid = ThaumicAttempts.MODID)
public final class ModBlocksItems {
    // Хэндлы инстансов
    public static Block GOLEM_CRAFTER;
    public static Block ARCANE_CRAFTER;
    public static Block EAR_BAND;
    public static Block MIRROR_STABILIZER;
    public static Block MATH_CORE;


    public static Item CRAFT_PATTERN;
    public static Item INFUSION_PATTERN;
    public static Item ARCANE_PATTERN;
    public static Item RESOURCE_LIST;
    public static Item RIFT_FLOWER;
    public static Item RIFT_STONE;
    public static Item RIFT_CRISTAL;
    public static Item RIFT_EMBER;
    public static Item RIFT_AMETIST;
    public static Item RIFT_BRILIANT;
    public static Item ANOMALY_SEEDS;
    public static Item MIND_FRUIT;
    public static Item MATURE_MIND_FRUIT;
    public static Item TAINTED_MIND_FRUIT;
    public static Item MIND_POTION;
    public static Item TA_GEM;
    public static Item PRISM;
    public static Item RIFTONOMICON_PROTOCOL;
    public static Item STORAGE_BELL;
    public static Item PORTAL_RUNE;
    public static Item CALIBRATED_EXTRACT;
    public static Item ANOMALY_COMPASS;
    public static Item ARCHIVE_FRAGMENTS;
    public static Item TRANSMUTATION_CORE;
    public static Item CREATION_CORE;
    public static Item NAVIGATION_CORE;
    public static Item ALCHEMICAL_CORE;

    // ---- РЕЕСТР БЛОКОВ ----
    @SubscribeEvent
    public static void onRegisterBlocks(RegistryEvent.Register<Block> e) {
        GOLEM_CRAFTER = new BlockGolemCrafter()
                .setRegistryName(ThaumicAttempts.MODID, "golem_crafter");
        e.getRegistry().register(GOLEM_CRAFTER);

        ARCANE_CRAFTER = new BlockArcaneCrafter()
                .setRegistryName(ThaumicAttempts.MODID, "arcane_crafter");
        e.getRegistry().register(ARCANE_CRAFTER);

        EAR_BAND = new BlockArcaneEarBand();
        e.getRegistry().register(EAR_BAND);

        MIRROR_STABILIZER = new therealpant.thaumicattempts.golemnet.block.BlockMirrorStabilizer();
        MATH_CORE         = new therealpant.thaumicattempts.golemnet.block.BlockMathCore();
        e.getRegistry().registerAll(MIRROR_STABILIZER, MATH_CORE);


        // Сетап блоков сети (если у них НЕТ setRegistryName в конструкторе — задай тут)
        TABlocks.MIRROR_MANAGER    = new BlockMirrorManager();    // .setRegistryName(MODID, "mirror_manager");
        TABlocks.ORDER_TERMINAL    = new BlockOrderTerminal();    // .setRegistryName(MODID, "order_terminal");
        TABlocks.PATTERN_REQUESTER = new BlockPatternRequester(); // .setRegistryName(MODID, "pattern_requester");
        TABlocks.RESOURCE_REQUESTER = new BlockResourceRequester();
        TABlocks.CRAFT_PLANNER = new BlockCraftPlanner();
        TABlocks.GOLEM_DISPATCHER = new BlockGolemDispatcher();
        TABlocks.INFUSION_REQUESTER = new BlockInfusionRequester();
        TABlocks.ANOMALY_STONE = new BlockAnomalyStone();
        TABlocks.RIFT_BUSH = new BlockRiftBush();
        TABlocks.RIFT_GEOD = new BlockRiftGeod();
        TABlocks.ANOMALY_ARCHIVE = new BlockAnomalyArchive();
        TABlocks.ANCIENT_COLUMN = new BlockAncientColumn();
        TABlocks.ANCIENT_FIRE = new BlockAncientFire();
        TABlocks.ANOMALY_WELL = new BlockAnomalyWell();
        TABlocks.ANOMALOUS_MONOLITH = new BlockAnomalousMonolith();
        TABlocks.STRANGE_PEDESTAL = new BlockStrangePedestal();
        TABlocks.ELDRITCH_CONSTRUCTION = new BlockEldritchConstarction();
        TABlocks.ANOMALY_BED = new BlockAnomalyBed();
        TABlocks.ANOMALY_CROP = new BlockAnomalyCrop();
        TABlocks.AURA_BOOSTER = new BlockAuraBooster();
        TABlocks.RIFT_CRISTAL_BLOCK = new BlockRistCristalBlock();
        TABlocks.RIFT_EXTRACTOR = new BlockRiftExtractor();
        TABlocks.REVISION_PIEDESTAL = new BlockRevisionPiedestal();
        TABlocks.RIFT_STONE_PORTAL = new BlockRiftStonePortal();
        TABlocks.RIFT_PORTAL_PLATFORM = new BlockRiftPortalPlatform();
        TABlocks.RIFT_STONE_FURNACE = new BlockRiftStoneFurnace();
        TABlocks.RIFT_STONE_ALTAR = new BlockRiftStoneAltar();
        TABlocks.RIFTONOMICON = new BlockRiftonomicon();
        TABlocks.RIFTONOMICON_CORE_PLACEHOLDER = new BlockRiftonomiconCorePlaceholder();
        TABlocks.POLISHED_DARK_JASPER = new BlockDarkJasperFull("polished_dark_jasper");

        // Formed multiblock states are obtained only by assembling their structures.
        // Keep their construction components available, but hide the completed blocks.
        TABlocks.MIRROR_MANAGER.setCreativeTab(null);
        TABlocks.AURA_BOOSTER.setCreativeTab(null);
        TABlocks.RIFT_EXTRACTOR.setCreativeTab(null);
        TABlocks.RIFT_STONE_PORTAL.setCreativeTab(null);
        TABlocks.RIFT_PORTAL_PLATFORM.setCreativeTab(null);
        TABlocks.RIFT_STONE_FURNACE.setCreativeTab(null);
        TABlocks.RIFT_STONE_ALTAR.setCreativeTab(null);
        TABlocks.RIFTONOMICON.setCreativeTab(null);
        TABlocks.RIFTONOMICON_CORE_PLACEHOLDER.setCreativeTab(null);

        e.getRegistry().registerAll(
                TABlocks.MIRROR_MANAGER,
                TABlocks.ORDER_TERMINAL,
                TABlocks.PATTERN_REQUESTER,
                TABlocks.RESOURCE_REQUESTER,
                TABlocks.GOLEM_DISPATCHER,
                TABlocks.CRAFT_PLANNER,
                TABlocks.INFUSION_REQUESTER,
                TABlocks.ANOMALY_STONE,
                TABlocks.RIFT_BUSH,
                TABlocks.RIFT_GEOD,
                TABlocks.ANOMALY_ARCHIVE,
                TABlocks.ANCIENT_COLUMN,
                TABlocks.ANCIENT_FIRE,
                TABlocks.ANOMALY_WELL,
                TABlocks.ANOMALOUS_MONOLITH,
                TABlocks.STRANGE_PEDESTAL,
                TABlocks.ELDRITCH_CONSTRUCTION,
                TABlocks.ANOMALY_BED,
                TABlocks.ANOMALY_CROP,
                TABlocks.AURA_BOOSTER,
                TABlocks.RIFT_CRISTAL_BLOCK,
                TABlocks.RIFT_EXTRACTOR,
                TABlocks.REVISION_PIEDESTAL,
                TABlocks.RIFT_STONE_PORTAL,
                TABlocks.RIFT_PORTAL_PLATFORM,
                TABlocks.RIFT_STONE_FURNACE,
                TABlocks.RIFT_STONE_ALTAR,
                TABlocks.RIFTONOMICON,
                TABlocks.RIFTONOMICON_CORE_PLACEHOLDER,
                TABlocks.POLISHED_DARK_JASPER
        );

        // TileEntities сети
        GameRegistry.registerTileEntity(TileMirrorManager.class,
                new ResourceLocation(ThaumicAttempts.MODID, "mirror_manager"));
        GameRegistry.registerTileEntity(TileOrderTerminal.class,
                new ResourceLocation(ThaumicAttempts.MODID, "order_terminal"));
        GameRegistry.registerTileEntity(TilePatternRequester.class,
                new ResourceLocation(ThaumicAttempts.MODID, "pattern_requester"));
        GameRegistry.registerTileEntity(TileResourceRequester.class,
                new ResourceLocation(ThaumicAttempts.MODID, "resource_requester"));
        GameRegistry.registerTileEntity(TileGolemDispatcher.class,
                new ResourceLocation(ThaumicAttempts.MODID, "golem_dispatcher"));
        GameRegistry.registerTileEntity(TileCraftPlanner.class,
                new ResourceLocation(ThaumicAttempts.MODID, "craft_planner"));
        GameRegistry.registerTileEntity(TileInfusionRequester.class,
                new ResourceLocation(ThaumicAttempts.MODID, "infusion_requester"));
        GameRegistry.registerTileEntity(TileAnomalyStone.class,
                new ResourceLocation(ThaumicAttempts.MODID, "anomaly_stone"));
        GameRegistry.registerTileEntity(TileRiftBush.class,
                new ResourceLocation(ThaumicAttempts.MODID, "rift_bush"));
        GameRegistry.registerTileEntity(TileRiftGeod.class,
                new ResourceLocation(ThaumicAttempts.MODID, "rift_geod"));
        GameRegistry.registerTileEntity(TileAnomalyArchive.class,
                new ResourceLocation(ThaumicAttempts.MODID, "anomaly_archive"));
        GameRegistry.registerTileEntity(TileAncientColumn.class,
                new ResourceLocation(ThaumicAttempts.MODID, "ancient_column"));
        GameRegistry.registerTileEntity(TileAncientFire.class,
                new ResourceLocation(ThaumicAttempts.MODID, "ancient_fire"));
        GameRegistry.registerTileEntity(TileAnomalyWell.class,
                new ResourceLocation(ThaumicAttempts.MODID, "anomaly_well"));
        GameRegistry.registerTileEntity(TileAnomalousMonolith.class,
                new ResourceLocation(ThaumicAttempts.MODID, "anomalous_monolith"));
        GameRegistry.registerTileEntity(TileStrangePedestal.class,
                new ResourceLocation(ThaumicAttempts.MODID, "strange_pedestal"));
        GameRegistry.registerTileEntity(TileEldritchConstruction.class,
                new ResourceLocation(ThaumicAttempts.MODID, "eldritch_construction"));
        GameRegistry.registerTileEntity(TileAnomalyCrop.class,
                new ResourceLocation(ThaumicAttempts.MODID, "ta_anomaly_crop"));
        GameRegistry.registerTileEntity(TileAuraBooster.class,
                new ResourceLocation(ThaumicAttempts.MODID, "ta_aura_booster"));
        GameRegistry.registerTileEntity(therealpant.thaumicattempts.world.tile.TileRiftExtractor.class,
                new ResourceLocation(ThaumicAttempts.MODID, "rift_extractor"));
        GameRegistry.registerTileEntity(TileRevisionPiedestal.class,
                new ResourceLocation(ThaumicAttempts.MODID, "revision_piedestal"));
        GameRegistry.registerTileEntity(TileRiftStonePortal.class,
                new ResourceLocation(ThaumicAttempts.MODID, "rift_stone_portal"));
        GameRegistry.registerTileEntity(TileRiftPortalPlatform.class,
                new ResourceLocation(ThaumicAttempts.MODID, "rift_portal_platform"));
        GameRegistry.registerTileEntity(TileRiftStoneFurnace.class,
                new ResourceLocation(ThaumicAttempts.MODID, "rift_stone_furnace"));
        GameRegistry.registerTileEntity(TileRiftStoneFurnacePort.class,
                new ResourceLocation(ThaumicAttempts.MODID, "rift_stone_furnace_port"));
        GameRegistry.registerTileEntity(TileRiftStoneAltar.class,
                new ResourceLocation(ThaumicAttempts.MODID, "rift_stone_altar"));
        GameRegistry.registerTileEntity(TileRiftonomicon.class,
                new ResourceLocation(ThaumicAttempts.MODID, "riftonomicon"));
        GameRegistry.registerTileEntity(TileRiftonomiconCorePlaceholder.class,
                new ResourceLocation(ThaumicAttempts.MODID, "riftonomicon_core_placeholder"));
        GameRegistry.registerTileEntity(therealpant.thaumicattempts.world.tile.TileRiftonomiconImpetusPort.class,
                new ResourceLocation(ThaumicAttempts.MODID, "riftonomicon_impetus_port"));
        GameRegistry.registerTileEntity(therealpant.thaumicattempts.world.tile.TileRiftonomiconTransmutationEssentiaInput.class,
                new ResourceLocation(ThaumicAttempts.MODID, "riftonomicon_transmutation_essentia_input"));
        GameRegistry.registerTileEntity(therealpant.thaumicattempts.world.tile.TileRiftonomiconTransmutationImpetusInput.class,
                new ResourceLocation(ThaumicAttempts.MODID, "riftonomicon_transmutation_impetus_input"));
        // TE для ARCANE_CRAFTER регистрируем в ThaumicAttempts#preInit (см. ниже).
    }

    // ---- РЕЕСТР ПРЕДМЕТОВ ----
    @SubscribeEvent
    public static void onRegisterItems(RegistryEvent.Register<Item> e) {
        // Паттерны
        CRAFT_PATTERN  = new ItemCraftPattern()
                .setRegistryName(ThaumicAttempts.MODID, "craft_pattern");
        ARCANE_PATTERN = new ItemArcanePattern()
                .setRegistryName(ThaumicAttempts.MODID, "arcane_pattern");
        INFUSION_PATTERN = new ItemInfusionPattern()
                .setRegistryName(ThaumicAttempts.MODID, "infusion_pattern");
        RESOURCE_LIST  = new ItemResourceList()
                .setRegistryName(ThaumicAttempts.MODID, "resource_list");
        RIFT_FLOWER = new Item()
                .setCreativeTab(ThaumicAttempts.CREATIVE_TAB)
                .setTranslationKey(ThaumicAttempts.MODID + ".rift_flover")
                .setRegistryName(ThaumicAttempts.MODID, "rift_flover");
        RIFT_STONE = new Item()
                .setCreativeTab(ThaumicAttempts.CREATIVE_TAB)
                .setTranslationKey(ThaumicAttempts.MODID + ".rift_stone")
                .setRegistryName(ThaumicAttempts.MODID, "rift_stone");
        RIFT_CRISTAL = new Item()
                .setCreativeTab(ThaumicAttempts.CREATIVE_TAB)
                .setTranslationKey(ThaumicAttempts.MODID + ".rift_cristal")
                .setRegistryName(ThaumicAttempts.MODID, "rift_cristal");
        RIFT_EMBER = new Item()
                .setCreativeTab(ThaumicAttempts.CREATIVE_TAB)
                .setTranslationKey(ThaumicAttempts.MODID + ".rift_ember")
                .setRegistryName(ThaumicAttempts.MODID, "rift_ember");
        RIFT_AMETIST = new ItemRiftAmethyst()
                .setCreativeTab(ThaumicAttempts.CREATIVE_TAB)
                .setTranslationKey(ThaumicAttempts.MODID + ".rift_ametist")
                .setRegistryName(ThaumicAttempts.MODID, "rift_ametist");
        RIFT_BRILIANT = new Item()
                .setCreativeTab(ThaumicAttempts.CREATIVE_TAB)
                .setTranslationKey(ThaumicAttempts.MODID + ".rift_briliant")
                .setRegistryName(ThaumicAttempts.MODID, "rift_briliant");
        ANOMALY_SEEDS = new ItemAnomalySeeds(TABlocks.ANOMALY_CROP, TABlocks.ANOMALY_BED);
        MIND_FRUIT = new ItemMindFruitFood(5, 12.0F, 30 * 20)
                .setCreativeTab(ThaumicAttempts.CREATIVE_TAB)
                .setTranslationKey(ThaumicAttempts.MODID + ".mind_fruit")
                .setRegistryName(ThaumicAttempts.MODID, "ta_mind_fruit");
        MATURE_MIND_FRUIT = new ItemMindFruitFood(7, 16.0F, 60 * 20)
                .setCreativeTab(ThaumicAttempts.CREATIVE_TAB)
                .setTranslationKey(ThaumicAttempts.MODID + ".mature_mind_fruit")
                .setRegistryName(ThaumicAttempts.MODID, "ta_mature_mind_fruit");
        TAINTED_MIND_FRUIT = new ItemTaintedMindFruitFood(7, 16.0F)
                .setCreativeTab(ThaumicAttempts.CREATIVE_TAB)
                .setTranslationKey(ThaumicAttempts.MODID + ".tainted_mind_fruit")
                .setRegistryName(ThaumicAttempts.MODID, "ta_tainted_mind_fruit");
        MIND_POTION = new ItemMindPotion();
        TA_GEM = new ItemTAGem()
                .setRegistryName(ThaumicAttempts.MODID, "ta_gem");
        PRISM = new ItemPrism();
        RIFTONOMICON_PROTOCOL = new ItemRiftonomiconProtocol();
        STORAGE_BELL = new ItemStorageBell();
        PORTAL_RUNE = new ItemPortalRune();
        CALIBRATED_EXTRACT = new ItemCalibratedExtract();
        ANOMALY_COMPASS = new ItemAnomalyCompass();
        ARCHIVE_FRAGMENTS = new Item()
                .setCreativeTab(ThaumicAttempts.CREATIVE_TAB)
                .setTranslationKey(ThaumicAttempts.MODID + ".archive_fragments")
                .setRegistryName(ThaumicAttempts.MODID, "archive_fragments");
        TRANSMUTATION_CORE = createCoreItem("transmutation_core");
        CREATION_CORE = createCoreItem("creation_core");
        NAVIGATION_CORE = createCoreItem("navigation_core");
        ALCHEMICAL_CORE = new therealpant.thaumicattempts.items.ItemAlchemicalCore();

        e.getRegistry().registerAll(
                CRAFT_PATTERN,
                INFUSION_PATTERN,
                ARCANE_PATTERN,
                RESOURCE_LIST,
                RIFT_FLOWER,
                RIFT_STONE,
                RIFT_CRISTAL,
                RIFT_EMBER,
                RIFT_AMETIST,
                RIFT_BRILIANT,
                ANOMALY_SEEDS,
                MIND_FRUIT,
                MATURE_MIND_FRUIT,
                TAINTED_MIND_FRUIT,
                MIND_POTION,
                TA_GEM,
                PRISM,
                RIFTONOMICON_PROTOCOL,
                STORAGE_BELL,
                PORTAL_RUNE,
                CALIBRATED_EXTRACT,
                ANOMALY_COMPASS,
                ARCHIVE_FRAGMENTS,
                TRANSMUTATION_CORE,
                CREATION_CORE,
                NAVIGATION_CORE,
                ALCHEMICAL_CORE
        );

        // ItemBlock'и
        e.getRegistry().register(new ItemBlock(GOLEM_CRAFTER)
                .setRegistryName(GOLEM_CRAFTER.getRegistryName()));
        e.getRegistry().register(new ItemBlock(ARCANE_CRAFTER)
                .setRegistryName(ARCANE_CRAFTER.getRegistryName()));
        e.getRegistry().register(new ItemBlock(EAR_BAND)
                .setRegistryName(EAR_BAND.getRegistryName()));
        e.getRegistry().register(new ItemBlock(MIRROR_STABILIZER)
                .setRegistryName(MIRROR_STABILIZER.getRegistryName()));
        e.getRegistry().register(new ItemBlock(MATH_CORE)
                .setRegistryName(MATH_CORE.getRegistryName()));

        // ItemBlock'и сети
        TABlocks.MIRROR_MANAGER_ITEM = new ItemBlock(TABlocks.MIRROR_MANAGER)
                .setRegistryName(TABlocks.MIRROR_MANAGER.getRegistryName());
        TABlocks.ORDER_TERMINAL_ITEM = new ItemBlock(TABlocks.ORDER_TERMINAL)
                .setRegistryName(TABlocks.ORDER_TERMINAL.getRegistryName());
        TABlocks.PATTERN_REQUESTER_ITEM = new ItemBlock(TABlocks.PATTERN_REQUESTER)
                .setRegistryName(TABlocks.PATTERN_REQUESTER.getRegistryName());
        TABlocks.RESOURCE_REQUESTER_ITEM = new ItemBlock(TABlocks.RESOURCE_REQUESTER)
                .setRegistryName(TABlocks.RESOURCE_REQUESTER.getRegistryName());
        TABlocks.GOLEM_DISPATCHER_ITEM = new ItemBlock(TABlocks.GOLEM_DISPATCHER)
                .setRegistryName(TABlocks.GOLEM_DISPATCHER.getRegistryName());
        TABlocks.CRAFT_PLANNER_ITEM = new ItemBlock(TABlocks.CRAFT_PLANNER)
                .setRegistryName(TABlocks.CRAFT_PLANNER.getRegistryName());
        TABlocks.INFUSION_REQUESTER_ITEM = new ItemBlock(TABlocks.INFUSION_REQUESTER)
                .setRegistryName(TABlocks.INFUSION_REQUESTER.getRegistryName());
        TABlocks.ANOMALY_STONE_ITEM = new ItemBlock(TABlocks.ANOMALY_STONE)
                .setRegistryName(TABlocks.ANOMALY_STONE.getRegistryName());
        TABlocks.RIFT_BUSH_ITEM = new ItemBlock(TABlocks.RIFT_BUSH)
                .setRegistryName(TABlocks.RIFT_BUSH.getRegistryName());
        TABlocks.RIFT_GEOD_ITEM = new ItemBlock(TABlocks.RIFT_GEOD)
                .setRegistryName(TABlocks.RIFT_GEOD.getRegistryName());
        TABlocks.ANOMALY_ARCHIVE_ITEM = new ItemBlock(TABlocks.ANOMALY_ARCHIVE)
                .setRegistryName(TABlocks.ANOMALY_ARCHIVE.getRegistryName());
        TABlocks.ANCIENT_COLUMN_ITEM = new ItemBlock(TABlocks.ANCIENT_COLUMN)
                .setRegistryName(TABlocks.ANCIENT_COLUMN.getRegistryName());
        TABlocks.ANCIENT_FIRE_ITEM = new ItemBlock(TABlocks.ANCIENT_FIRE)
                .setRegistryName(TABlocks.ANCIENT_FIRE.getRegistryName());
        TABlocks.ANOMALY_WELL_ITEM = new ItemBlock(TABlocks.ANOMALY_WELL)
                .setRegistryName(TABlocks.ANOMALY_WELL.getRegistryName());
        TABlocks.ANOMALOUS_MONOLITH_ITEM = new ItemBlock(TABlocks.ANOMALOUS_MONOLITH)
                .setRegistryName(TABlocks.ANOMALOUS_MONOLITH.getRegistryName());
        TABlocks.STRANGE_PEDESTAL_ITEM = new ItemBlock(TABlocks.STRANGE_PEDESTAL)
                .setRegistryName(TABlocks.STRANGE_PEDESTAL.getRegistryName());
        TABlocks.ELDRITCH_CONSTRUCTION_ITEM = new ItemBlockEldritchConstruction(TABlocks.ELDRITCH_CONSTRUCTION)
                .setRegistryName(TABlocks.ELDRITCH_CONSTRUCTION.getRegistryName());
        TABlocks.ANOMALY_BED_ITEM = new ItemBlock(TABlocks.ANOMALY_BED)
                .setRegistryName(TABlocks.ANOMALY_BED.getRegistryName());
        TABlocks.AURA_BOOSTER_ITEM = new ItemBlock(TABlocks.AURA_BOOSTER)
                .setRegistryName(TABlocks.AURA_BOOSTER.getRegistryName());
        TABlocks.RIFT_CRISTAL_BLOCK_ITEM = new ItemBlock(TABlocks.RIFT_CRISTAL_BLOCK)
                .setRegistryName(TABlocks.RIFT_CRISTAL_BLOCK.getRegistryName());
        TABlocks.RIFT_EXTRACTOR_ITEM = new ItemBlock(TABlocks.RIFT_EXTRACTOR)
                .setRegistryName(TABlocks.RIFT_EXTRACTOR.getRegistryName());
        TABlocks.REVISION_PIEDESTAL_ITEM = new ItemBlock(TABlocks.REVISION_PIEDESTAL)
                .setRegistryName(TABlocks.REVISION_PIEDESTAL.getRegistryName());
        TABlocks.RIFT_STONE_PORTAL_ITEM = new ItemBlock(TABlocks.RIFT_STONE_PORTAL)
                .setRegistryName(TABlocks.RIFT_STONE_PORTAL.getRegistryName());
        TABlocks.RIFT_PORTAL_PLATFORM_ITEM = new ItemBlock(TABlocks.RIFT_PORTAL_PLATFORM)
                .setRegistryName(TABlocks.RIFT_PORTAL_PLATFORM.getRegistryName());
        TABlocks.RIFT_STONE_FURNACE_ITEM = new ItemBlock(TABlocks.RIFT_STONE_FURNACE)
                .setRegistryName(TABlocks.RIFT_STONE_FURNACE.getRegistryName());
        TABlocks.RIFT_STONE_ALTAR_ITEM = new ItemBlock(TABlocks.RIFT_STONE_ALTAR)
                .setRegistryName(TABlocks.RIFT_STONE_ALTAR.getRegistryName());
        TABlocks.RIFTONOMICON_ITEM = new ItemBlock(TABlocks.RIFTONOMICON)
                .setRegistryName(TABlocks.RIFTONOMICON.getRegistryName());
        TABlocks.RIFTONOMICON_CORE_PLACEHOLDER_ITEM = new ItemBlock(TABlocks.RIFTONOMICON_CORE_PLACEHOLDER)
                .setRegistryName(TABlocks.RIFTONOMICON_CORE_PLACEHOLDER.getRegistryName());
        TABlocks.POLISHED_DARK_JASPER_ITEM = new ItemBlock(TABlocks.POLISHED_DARK_JASPER)
                .setRegistryName(TABlocks.POLISHED_DARK_JASPER.getRegistryName());

        e.getRegistry().registerAll(
                TABlocks.MIRROR_MANAGER_ITEM,
                TABlocks.ORDER_TERMINAL_ITEM,
                TABlocks.PATTERN_REQUESTER_ITEM,
                TABlocks.RESOURCE_REQUESTER_ITEM,
                TABlocks.GOLEM_DISPATCHER_ITEM,
                TABlocks.CRAFT_PLANNER_ITEM,
                TABlocks.INFUSION_REQUESTER_ITEM,
                TABlocks.ANOMALY_STONE_ITEM,
                TABlocks.RIFT_BUSH_ITEM,
                TABlocks.RIFT_GEOD_ITEM,
                TABlocks.ANOMALY_ARCHIVE_ITEM,
                TABlocks.ANCIENT_COLUMN_ITEM,
                TABlocks.ANCIENT_FIRE_ITEM,
                TABlocks.ANOMALY_WELL_ITEM,
                TABlocks.ANOMALOUS_MONOLITH_ITEM,
                TABlocks.STRANGE_PEDESTAL_ITEM,
                TABlocks.ELDRITCH_CONSTRUCTION_ITEM,
                TABlocks.ANOMALY_BED_ITEM,
                TABlocks.AURA_BOOSTER_ITEM,
                TABlocks.RIFT_CRISTAL_BLOCK_ITEM,
                TABlocks.RIFT_EXTRACTOR_ITEM,
                TABlocks.REVISION_PIEDESTAL_ITEM,
                TABlocks.RIFT_STONE_PORTAL_ITEM,
                TABlocks.RIFT_PORTAL_PLATFORM_ITEM,
                TABlocks.RIFT_STONE_FURNACE_ITEM,
                TABlocks.RIFT_STONE_ALTAR_ITEM,
                TABlocks.RIFTONOMICON_ITEM,
                TABlocks.RIFTONOMICON_CORE_PLACEHOLDER_ITEM,
                TABlocks.POLISHED_DARK_JASPER_ITEM
        );
    }

    private static Item createCoreItem(String name) {
        return new Item()
                .setCreativeTab(ThaumicAttempts.CREATIVE_TAB)
                .setTranslationKey(ThaumicAttempts.MODID + "." + name)
                .setRegistryName(ThaumicAttempts.MODID, name);
    }
}
