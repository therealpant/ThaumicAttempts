package therealpant.thaumicattempts.golemcraft;

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
import therealpant.thaumicattempts.world.block.BlockAnomalyBed;
import therealpant.thaumicattempts.world.block.BlockAnomalyCrop;
import therealpant.thaumicattempts.world.block.BlockAnomalyStone;
import therealpant.thaumicattempts.world.block.BlockRiftBush;
import therealpant.thaumicattempts.world.block.BlockRiftGeod;


import therealpant.thaumicattempts.golemnet.tile.*;
import therealpant.thaumicattempts.world.tile.TileRiftBush;
import therealpant.thaumicattempts.world.tile.TileRiftGeod;


import therealpant.thaumicattempts.init.TABlocks;
import therealpant.thaumicattempts.world.tile.TileAnomalyStone;
import therealpant.thaumicattempts.world.tile.TileAnomalyCrop;
import therealpant.thaumicattempts.world.item.ItemAnomalySeeds;
import therealpant.thaumicattempts.world.item.ItemMindFruitFood;
import therealpant.thaumicattempts.world.item.ItemMindPotion;
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
    public static Item ANOMALY_SEEDS;
    public static Item MIND_FRUIT;
    public static Item MATURE_MIND_FRUIT;
    public static Item TAINTED_MIND_FRUIT;
    public static Item MIND_POTION;

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
        TABlocks.MIRROR_MANAGER_CORE = new BlockMirrorManagerCore();
        TABlocks.ORDER_TERMINAL    = new BlockOrderTerminal();    // .setRegistryName(MODID, "order_terminal");
        TABlocks.PATTERN_REQUESTER = new BlockPatternRequester(); // .setRegistryName(MODID, "pattern_requester");
        TABlocks.RESOURCE_REQUESTER = new BlockResourceRequester();
        TABlocks.GOLEM_DISPATCHER = new BlockGolemDispatcher();
        TABlocks.INFUSION_REQUESTER = new BlockInfusionRequester();
        TABlocks.ANOMALY_STONE = new BlockAnomalyStone();
        TABlocks.RIFT_BUSH = new BlockRiftBush();
        TABlocks.RIFT_GEOD = new BlockRiftGeod();
        TABlocks.RIFT_STONE_BASE = new BlockRiftStoneBase();
        TABlocks.ELDRITCH_CONSTRUCTION = new BlockEldritchConstarction();
        TABlocks.ANOMALY_BED = new BlockAnomalyBed();
        TABlocks.ANOMALY_CROP = new BlockAnomalyCrop();

        e.getRegistry().registerAll(
                TABlocks.MIRROR_MANAGER,
                TABlocks.MIRROR_MANAGER_CORE,
                TABlocks.ORDER_TERMINAL,
                TABlocks.PATTERN_REQUESTER,
                TABlocks.RESOURCE_REQUESTER,
                TABlocks.GOLEM_DISPATCHER,
                TABlocks.INFUSION_REQUESTER,
                TABlocks.ANOMALY_STONE,
                TABlocks.RIFT_BUSH,
                TABlocks.RIFT_GEOD,
                TABlocks.RIFT_STONE_BASE,
                TABlocks.ELDRITCH_CONSTRUCTION,
                TABlocks.ANOMALY_BED,
                TABlocks.ANOMALY_CROP
        );

        // TileEntities сети
        GameRegistry.registerTileEntity(TileMirrorManager.class,
                new ResourceLocation(ThaumicAttempts.MODID, "mirror_manager"));
        GameRegistry.registerTileEntity(TileMirrorManagerCore.class,
                new ResourceLocation(ThaumicAttempts.MODID, "mirror_manager_core"));
        GameRegistry.registerTileEntity(TileRiftStoneBase.class,
                new ResourceLocation(ThaumicAttempts.MODID, "rift_stone_base"));
        GameRegistry.registerTileEntity(TileOrderTerminal.class,
                new ResourceLocation(ThaumicAttempts.MODID, "order_terminal"));
        GameRegistry.registerTileEntity(TilePatternRequester.class,
                new ResourceLocation(ThaumicAttempts.MODID, "pattern_requester"));
        GameRegistry.registerTileEntity(TileResourceRequester.class,
                new ResourceLocation(ThaumicAttempts.MODID, "resource_requester"));
        GameRegistry.registerTileEntity(TileGolemDispatcher.class,
                new ResourceLocation(ThaumicAttempts.MODID, "golem_dispatcher"));
        GameRegistry.registerTileEntity(TileInfusionRequester.class,
                new ResourceLocation(ThaumicAttempts.MODID, "infusion_requester"));
        GameRegistry.registerTileEntity(TileAnomalyStone.class,
                new ResourceLocation(ThaumicAttempts.MODID, "anomaly_stone"));
        GameRegistry.registerTileEntity(TileRiftBush.class,
                new ResourceLocation(ThaumicAttempts.MODID, "rift_bush"));
        GameRegistry.registerTileEntity(TileRiftGeod.class,
                new ResourceLocation(ThaumicAttempts.MODID, "rift_geod"));
        GameRegistry.registerTileEntity(TileAnomalyCrop.class,
                new ResourceLocation(ThaumicAttempts.MODID, "ta_anomaly_crop"));
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

        e.getRegistry().registerAll(
                CRAFT_PATTERN,
                INFUSION_PATTERN,
                ARCANE_PATTERN,
                RESOURCE_LIST,
                RIFT_FLOWER,
                RIFT_STONE,
                RIFT_CRISTAL,
                ANOMALY_SEEDS,
                MIND_FRUIT,
                MATURE_MIND_FRUIT,
                TAINTED_MIND_FRUIT,
                MIND_POTION
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
        TABlocks.MIRROR_MANAGER_CORE_ITEM = new ItemBlock(TABlocks.MIRROR_MANAGER_CORE)
                .setRegistryName(TABlocks.MIRROR_MANAGER_CORE.getRegistryName());
        TABlocks.ORDER_TERMINAL_ITEM = new ItemBlock(TABlocks.ORDER_TERMINAL)
                .setRegistryName(TABlocks.ORDER_TERMINAL.getRegistryName());
        TABlocks.PATTERN_REQUESTER_ITEM = new ItemBlock(TABlocks.PATTERN_REQUESTER)
                .setRegistryName(TABlocks.PATTERN_REQUESTER.getRegistryName());
        TABlocks.RESOURCE_REQUESTER_ITEM = new ItemBlock(TABlocks.RESOURCE_REQUESTER)
                .setRegistryName(TABlocks.RESOURCE_REQUESTER.getRegistryName());
        TABlocks.GOLEM_DISPATCHER_ITEM = new ItemBlock(TABlocks.GOLEM_DISPATCHER)
                .setRegistryName(TABlocks.GOLEM_DISPATCHER.getRegistryName());
        TABlocks.INFUSION_REQUESTER_ITEM = new ItemBlock(TABlocks.INFUSION_REQUESTER)
                .setRegistryName(TABlocks.INFUSION_REQUESTER.getRegistryName());
        TABlocks.ANOMALY_STONE_ITEM = new ItemBlock(TABlocks.ANOMALY_STONE)
                .setRegistryName(TABlocks.ANOMALY_STONE.getRegistryName());
        TABlocks.RIFT_BUSH_ITEM = new ItemBlock(TABlocks.RIFT_BUSH)
                .setRegistryName(TABlocks.RIFT_BUSH.getRegistryName());
        TABlocks.RIFT_GEOD_ITEM = new ItemBlock(TABlocks.RIFT_GEOD)
                .setRegistryName(TABlocks.RIFT_GEOD.getRegistryName());
        TABlocks.RIFT_STONE_BASE_ITEM = new ItemBlock(TABlocks.RIFT_STONE_BASE)
                .setRegistryName(TABlocks.RIFT_STONE_BASE.getRegistryName());
        TABlocks.ELDRITCH_CONSTRUCTION_ITEM = new ItemBlock(TABlocks.ELDRITCH_CONSTRUCTION)
                .setRegistryName(TABlocks.ELDRITCH_CONSTRUCTION.getRegistryName());
        TABlocks.ANOMALY_BED_ITEM = new ItemBlock(TABlocks.ANOMALY_BED)
                .setRegistryName(TABlocks.ANOMALY_BED.getRegistryName());


        e.getRegistry().registerAll(
                TABlocks.MIRROR_MANAGER_ITEM,
                TABlocks.MIRROR_MANAGER_CORE_ITEM,
                TABlocks.ORDER_TERMINAL_ITEM,
                TABlocks.PATTERN_REQUESTER_ITEM,
                TABlocks.RESOURCE_REQUESTER_ITEM,
                TABlocks.GOLEM_DISPATCHER_ITEM,
                TABlocks.INFUSION_REQUESTER_ITEM,
                TABlocks.ANOMALY_STONE_ITEM,
                TABlocks.RIFT_BUSH_ITEM,
                TABlocks.RIFT_GEOD_ITEM,
                TABlocks.RIFT_STONE_BASE_ITEM,
                TABlocks.ELDRITCH_CONSTRUCTION_ITEM,
                TABlocks.ANOMALY_BED_ITEM
        );
    }
}