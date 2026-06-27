package therealpant.thaumicattempts.data;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.crafting.IDustTrigger;
import thaumcraft.api.crafting.Part;
import thaumcraft.common.lib.crafting.DustTriggerMultiblock;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.init.TABlocks;
import therealpant.thaumicattempts.world.block.BlockRiftonomicon;
import therealpant.thaumicattempts.world.block.BlockRiftStoneFurnace;

public final class TAMultiblockTriggers {
    private TAMultiblockTriggers() {}

    /** Этот ID ты потом укажешь в research json в "recipes": "thaumicattempts:mirror_manager_multiblock" */
    public static final ResourceLocation MIRROR_MANAGER_MB_ID =
            new ResourceLocation(ThaumicAttempts.MODID, "mirror_manager_multiblock");

    public static final ResourceLocation AURA_BOOSTER_MB_ID =
            new ResourceLocation(ThaumicAttempts.MODID, "aura_booster_multiblock");

    public static final ResourceLocation RIFT_EXTRACTOR_MB_ID =
            new ResourceLocation(ThaumicAttempts.MODID, "rift_extractor_multiblock");

    public static final ResourceLocation RIFT_PORTAL_MB_ID =
            new ResourceLocation(ThaumicAttempts.MODID, "rift_portal_multiblock");

    public static final ResourceLocation RIFT_STONE_FURNACE_MB_ID =
            new ResourceLocation(ThaumicAttempts.MODID, "rift_stone_furnace_multiblock");

    public static final ResourceLocation RIFTONOMICON_MB_ID =
            new ResourceLocation(ThaumicAttempts.MODID, "riftonomicon_multiblock");

    public static void register() {
            // 1) Триггер мультиблока (Salis Mundus)
            DustTriggerMultiblock mirrorTrigger = new DustTriggerMultiblock(
                    "TA_GOLEM_MIRRORS",
                    buildTriggerStructure()
            );
            IDustTrigger.registerDustTrigger(mirrorTrigger);

            DustTriggerMultiblock auraBoosterTrigger = new DustTriggerMultiblock(
                    "TA_AURA_BOOSTER",
                    buildAuraBoosterTriggerStructure()
            );
            IDustTrigger.registerDustTrigger(auraBoosterTrigger);

            DustTriggerMultiblock riftExtractorTrigger = new DustTriggerMultiblock(
                "TA_RIFT_EXTRACTOR",
                buildRiftExtractorTriggerStructure()
            );
            IDustTrigger.registerDustTrigger(riftExtractorTrigger);

            DustTriggerMultiblock riftPortalTrigger = new DustTriggerMultiblock(
                "TA_RIFT_EXTRACTOR",
                buildRiftPortalTriggerStructure()
            );
            IDustTrigger.registerDustTrigger(riftPortalTrigger);

            DustTriggerMultiblock riftStoneFurnaceTrigger = new DustTriggerMultiblock(
                "TA_RIFT_EXTRACTOR",
                buildRiftStoneFurnaceTriggerStructure()
            );
            IDustTrigger.registerDustTrigger(riftStoneFurnaceTrigger);

            DustTriggerMultiblock riftonomiconTrigger = new DustTriggerMultiblock(
                "TA_RIFT_EXTRACTOR",
                buildRiftonomiconTriggerStructure()
            );
            IDustTrigger.registerDustTrigger(riftonomiconTrigger);

            // 2) Blueprint для Таумономикона (схема постройки)
            registerBlueprint();
            registerAuraBoosterBlueprint();
            registerRiftExtractorBlueprint();
            registerRiftPortalBlueprint();
            registerRiftStoneFurnaceBlueprint();
            registerRiftonomiconBlueprint();
    }


    private static Part[][][] buildTriggerStructure() {
        // верх -> AIR
        Part coreToAir  = new Part(TABlocks.MIRROR_MANAGER_CORE, "AIR");
        // низ (куда кликаем пылью) -> MIRROR_MANAGER
        Part baseToMgr  = new Part(TABlocks.RIFT_STONE_BASE, TABlocks.MIRROR_MANAGER);

        return new Part[][][] {
                // Y = 0 (ВЕРХ)
                { { coreToAir } },
                // Y = 1 (НИЗ, СЮДА КЛИКАЕМ ПЫЛЬЮ)
                { { baseToMgr } }
        };
    }

    private static Part[][][] buildAuraBoosterTriggerStructure() {
        Part topToAir = new Part(TABlocks.RIFT_CRISTAL_BLOCK, "AIR");
        Part middleToAir = new Part(TABlocks.AURA_BOOSTER_CORE, "AIR");
        Part baseToBooster = new Part(TABlocks.RIFT_STONE_BASE, TABlocks.AURA_BOOSTER);

        return new Part[][][] {
                // Y = 0 (ВЕРХ)
                { { topToAir } },
                // Y = 1 (СЕРЕДИНА)
                { { middleToAir } },
                // Y = 2 (НИЗ, СЮДА КЛИКАЕМ ПЫЛЬЮ)
                { { baseToBooster } }
        };
    }

    private static Part[][][] buildRiftExtractorTriggerStructure() {
        Part topToAir = new Part(TABlocks.ELDRITCH_CONSTRUCTION, "AIR");
        Part middleToAir = new Part(TABlocks.ELDRITCH_CONSTRUCTION, "AIR");
        Part baseToBooster = new Part(TABlocks.RIFT_STONE_BASE, TABlocks.RIFT_EXTRACTOR);

        return new Part[][][] {
                // Y = 0 (ВЕРХ)
                { { topToAir } },
                // Y = 1 (СЕРЕДИНА)
                { { middleToAir } },
                // Y = 2 (НИЗ, СЮДА КЛИКАЕМ ПЫЛЬЮ)
                { { baseToBooster } }
        };
    }

    private static Part[][][] buildBlueprintStructure() {
        // Blueprint показывает исходную постройку (ничего не заменяем)
        Part top    = new Part(TABlocks.MIRROR_MANAGER_CORE, TABlocks.MIRROR_MANAGER_CORE);
        Part bottom = new Part(TABlocks.RIFT_STONE_BASE, TABlocks.RIFT_STONE_BASE);

        return new Part[][][] {
                // Y = 0 (ВЕРХ)
                { { top } },
                // Y = 1 (НИЗ)
                { { bottom } }
        };
    }

    private static void registerBlueprint() {
        Part[][][] parts = buildBlueprintStructure();

        // Конструктор BluePrint в stable_39 есть такой:
        // new ThaumcraftApi.BluePrint(String research, ItemStack displayStack, Part[][][] parts, ItemStack... ingredients)
        ThaumcraftApi.BluePrint bp = new ThaumcraftApi.BluePrint(
                "TA_GOLEM_MIRRORS",
                new ItemStack(TABlocks.MIRROR_MANAGER),
                parts
        );

        ThaumcraftApi.addMultiblockRecipeToCatalog(MIRROR_MANAGER_MB_ID, bp);
    }

    private static Part[][][] buildAuraBoosterBlueprintStructure() {
        Part top = new Part(TABlocks.RIFT_CRISTAL_BLOCK, TABlocks.RIFT_CRISTAL_BLOCK);
        Part middle = new Part(TABlocks.AURA_BOOSTER_CORE, TABlocks.AURA_BOOSTER_CORE);
        Part bottom = new Part(TABlocks.RIFT_STONE_BASE, TABlocks.RIFT_STONE_BASE);

        return new Part[][][] {
                // Y = 0 (ВЕРХ)
                { { top } },
                // Y = 1 (СЕРЕДИНА)
                { { middle } },
                // Y = 2 (НИЗ)
                { { bottom } }
        };
    }

    private static void registerAuraBoosterBlueprint() {
        Part[][][] parts = buildAuraBoosterBlueprintStructure();

        ThaumcraftApi.BluePrint bp = new ThaumcraftApi.BluePrint(
                "TA_AURA_BOOSTER",
                new ItemStack(TABlocks.AURA_BOOSTER),
                parts
        );

        ThaumcraftApi.addMultiblockRecipeToCatalog(AURA_BOOSTER_MB_ID, bp);
    }

    private static Part[][][] buildRiftExtractorBlueprintStructure() {
        Part top = new Part(TABlocks.ELDRITCH_CONSTRUCTION, TABlocks.ELDRITCH_CONSTRUCTION);
        Part middle = new Part(TABlocks.ELDRITCH_CONSTRUCTION, TABlocks.ELDRITCH_CONSTRUCTION);
        Part bottom = new Part(TABlocks.RIFT_STONE_BASE, TABlocks.RIFT_STONE_BASE);

        return new Part[][][] {
                // Y = 0 (ВЕРХ)
                { { top } },
                // Y = 1 (СЕРЕДИНА)
                { { middle } },
                // Y = 2 (НИЗ)
                { { bottom } }
        };
    }

    private static void registerRiftExtractorBlueprint() {
        Part[][][] parts = buildRiftExtractorBlueprintStructure();

        ThaumcraftApi.BluePrint bp = new ThaumcraftApi.BluePrint(
                "TA_RIFT_EXTRACTOR",
                new ItemStack(TABlocks.RIFT_EXTRACTOR),
                parts
        );

        ThaumcraftApi.addMultiblockRecipeToCatalog(RIFT_EXTRACTOR_MB_ID, bp);
    }

    private static Part[][][] buildRiftPortalTriggerStructure() {
        Part darkToAir = new Part(TABlocks.DARK_JASPER_BRICKS, "AIR");
        Part darkToPortal = new Part(TABlocks.DARK_JASPER_BRICKS, TABlocks.RIFT_STONE_PORTAL);
        Part darkToPlatform = new Part(TABlocks.DARK_JASPER_BRICKS, TABlocks.RIFT_PORTAL_PLATFORM);
        Part polishedToPlatform = new Part(TABlocks.POLISHED_DARK_JASPER, TABlocks.RIFT_PORTAL_PLATFORM);

        return new Part[][][] {
                {
                        { null, null, null },
                        { null, darkToAir, null },
                        { null, null, null }
                },
                {
                        { null, null, null },
                        { null, darkToPortal, null },
                        { null, null, null }
                },
                {
                        { darkToPlatform, polishedToPlatform, darkToPlatform },
                        { polishedToPlatform, polishedToPlatform, polishedToPlatform },
                        { darkToPlatform, polishedToPlatform, darkToPlatform }
                }
        };
    }

    private static Part[][][] buildRiftPortalBlueprintStructure() {
        Part dark = new Part(TABlocks.DARK_JASPER_BRICKS, TABlocks.DARK_JASPER_BRICKS);
        Part polished = new Part(TABlocks.POLISHED_DARK_JASPER, TABlocks.POLISHED_DARK_JASPER);

        return new Part[][][] {
                {
                        { null, null, null },
                        { null, dark, null },
                        { null, null, null }
                },
                {
                        { null, null, null },
                        { null, dark, null },
                        { null, null, null }
                },
                {
                        { dark, polished, dark },
                        { polished, polished, polished },
                        { dark, polished, dark }
                }
        };
    }

    private static void registerRiftPortalBlueprint() {
        Part[][][] parts = buildRiftPortalBlueprintStructure();

        ThaumcraftApi.BluePrint bp = new ThaumcraftApi.BluePrint(
                "TA_RIFT_EXTRACTOR",
                new ItemStack(TABlocks.RIFT_STONE_PORTAL),
                parts
        );

        ThaumcraftApi.addMultiblockRecipeToCatalog(RIFT_PORTAL_MB_ID, bp);
    }

    private static Part[][][] buildRiftStoneFurnaceTriggerStructure() {
        return new Part[][][] {
                {
                        {
                                furnacePart(BlocksTC.alembic, BlockRiftStoneFurnace.Part.TOP_CORNER),
                                furnacePart(TABlocks.DARK_JASPER_BRICKS, BlockRiftStoneFurnace.Part.TOP_NORTH),
                                furnacePart(BlocksTC.alembic, BlockRiftStoneFurnace.Part.TOP_CORNER)
                        },
                        {
                                furnacePart(TABlocks.DARK_JASPER_BRICKS, BlockRiftStoneFurnace.Part.TOP_WEST),
                                null,
                                furnacePart(TABlocks.DARK_JASPER_BRICKS, BlockRiftStoneFurnace.Part.TOP_EAST)
                        },
                        {
                                furnacePart(BlocksTC.alembic, BlockRiftStoneFurnace.Part.TOP_CORNER),
                                furnacePart(TABlocks.DARK_JASPER_BRICKS, BlockRiftStoneFurnace.Part.TOP_SOUTH),
                                furnacePart(BlocksTC.alembic, BlockRiftStoneFurnace.Part.TOP_CORNER)
                        }
                },
                {
                        {
                                furnacePart(BlocksTC.metalAlchemicalAdvanced, BlockRiftStoneFurnace.Part.LOWER_FULL),
                                furnacePart(TABlocks.ELDRITCH_CONSTRUCTION, BlockRiftStoneFurnace.Part.LOWER_FULL),
                                furnacePart(BlocksTC.metalAlchemicalAdvanced, BlockRiftStoneFurnace.Part.LOWER_FULL)
                        },
                        {
                                furnacePart(TABlocks.ELDRITCH_CONSTRUCTION, BlockRiftStoneFurnace.Part.LOWER_FULL),
                                furnacePart(BlocksTC.smelterVoid, BlockRiftStoneFurnace.Part.CENTER_LOW),
                                furnacePart(TABlocks.ELDRITCH_CONSTRUCTION, BlockRiftStoneFurnace.Part.LOWER_FULL)
                        },
                        {
                                furnacePart(BlocksTC.metalAlchemicalAdvanced, BlockRiftStoneFurnace.Part.LOWER_FULL),
                                furnacePart(TABlocks.ELDRITCH_CONSTRUCTION, BlockRiftStoneFurnace.Part.LOWER_FULL),
                                furnacePart(BlocksTC.metalAlchemicalAdvanced, BlockRiftStoneFurnace.Part.LOWER_FULL)
                        }
                }
        };
    }

    private static Part[][][] buildRiftStoneFurnaceBlueprintStructure() {
        Part advanced = new Part(BlocksTC.metalAlchemicalAdvanced, BlocksTC.metalAlchemicalAdvanced);
        Part voidSmelter = new Part(BlocksTC.smelterVoid, BlocksTC.smelterVoid);
        Part alembic = new Part(BlocksTC.alembic, BlocksTC.alembic);
        Part jasper = new Part(TABlocks.ELDRITCH_CONSTRUCTION, TABlocks.ELDRITCH_CONSTRUCTION);
        Part brick = new Part(TABlocks.DARK_JASPER_BRICKS,TABlocks.DARK_JASPER_BRICKS);

        return new Part[][][] {
                {
                        { alembic, brick, alembic },
                        { brick,   null, brick },
                        { alembic, brick, alembic }
                },
                {
                        { advanced, jasper, advanced },
                        { jasper, voidSmelter, jasper },
                        { advanced, jasper, advanced }
                }
        };
    }

    private static Part furnacePart(Object source, BlockRiftStoneFurnace.Part targetPart) {
        return new Part(
                source,
                new ItemStack(
                        TABlocks.RIFT_STONE_FURNACE,
                        1,
                        TABlocks.RIFT_STONE_FURNACE.getMetaFromState(
                                TABlocks.RIFT_STONE_FURNACE.getDefaultState()
                                        .withProperty(BlockRiftStoneFurnace.PART, targetPart)
                        )
                )
        );
    }

    private static void registerRiftStoneFurnaceBlueprint() {
        Part[][][] parts = buildRiftStoneFurnaceBlueprintStructure();

        ThaumcraftApi.BluePrint bp = new ThaumcraftApi.BluePrint(
                "TA_RIFT_EXTRACTOR",
                new ItemStack(TABlocks.RIFT_STONE_FURNACE),
                parts
        );

        ThaumcraftApi.addMultiblockRecipeToCatalog(RIFT_STONE_FURNACE_MB_ID, bp);
    }

    private static Part[][][] buildRiftonomiconTriggerStructure() {
        return buildRiftonomiconStructure(true);
    }

    private static Part[][][] buildRiftonomiconBlueprintStructure() {
        return buildRiftonomiconStructure(false);
    }

    private static Part[][][] buildRiftonomiconStructure(boolean transform) {
        Part n = null;
        Part brick = new Part(TABlocks.DARK_JASPER_BRICKS,TABlocks.DARK_JASPER_BRICKS);
        Part polished = new Part(TABlocks.POLISHED_DARK_JASPER, TABlocks.POLISHED_DARK_JASPER);
        Part core = new Part(TABlocks.RIFTONOMICON_CORE, TABlocks.RIFTONOMICON_CORE);

        return new Part[][][] {
                // 5 слой (верх)
                {
                        { n, n, n, n, n, n, n },
                        { n, n, n, n, n, n, n },
                        { n, n, n, n, n, n, n },
                        { n, n, n, brick, n, n, n },
                        { n, n, n, n, n, n, n },
                        { n, n, n, n, n, n, n },
                        { n, n, n, n, n, n, n }
                },
                // 4 слой
                {
                        { n, n, n, n, n, n, n },
                        { n, n, n, n, n, n, n },
                        { n, n, n, n, n, n, n },
                        { n, n, n, core, n, n, n },
                        { n, n, n, n, n, n, n },
                        { n, n, n, n, n, n, n },
                        { n, n, n, n, n, n, n }
                },
                // 3 слой
                {
                        { n, n, n, n, n, n, n },
                        { n, n, n, n, n, n, n },
                        { n, n, n, n, n, n, n },
                        { n, n, n, brick, n, n, n },
                        { n, n, n, n, n, n, n },
                        { n, n, n, n, n, n, n },
                        { n, n, n, n, n, n, n }
                },
                // 2 слой
                {
                        { n, n, n, n, n, n, n },
                        { n, brick, n, n, n, brick, n },
                        { n, n, n, n, n, n, n },
                        { n, n, n, n, n, n, n },
                        { n, n, n, n, n, n, n },
                        { n, brick, n, n, n, brick, n },
                        { n, n, n, n, n, n, n }
                },
                // 1 слой (низ)
                {
                        { n, n, brick, polished, brick, n, n },
                        { n, brick, polished, polished, polished, brick, n },
                        { polished, brick, n, n, n, brick, polished },
                        { polished, polished, n, n, n, polished, polished },
                        { polished, brick, n, n, n, brick, polished },
                        { n, brick, brick, polished, brick, brick, n },
                        { n, n, polished, polished, polished, n, n }
                }
        };
    }

    private static Part riftonomiconStructurePart(Object source, BlockRiftonomicon.Part targetPart, boolean transform) {
        return transform ? riftonomiconPart(source, targetPart) : new Part(source, source);
    }

    public static net.minecraft.block.state.IBlockState getRiftonomiconSourceState(int dx, int dy, int dz) {
        int ax = Math.abs(dx);
        int az = Math.abs(dz);

        if (dy == 0) {
            if (az == 3) {
                return ax <= 1 ? TABlocks.POLISHED_DARK_JASPER.getDefaultState() : null;
            }
            if (az == 2) {
                return ax <= 2 ? TABlocks.RIFT_STONE_BASE.getDefaultState() : null;
            }
            return ax >= 2 && ax <= 3 ? TABlocks.RIFT_STONE_BASE.getDefaultState() : null;
        }

        if (dy == 1) {
            return ax == 2 && az == 2 ? TABlocks.DARK_JASPER_BRICKS.getDefaultState() : null;
        }

        if (dy >= 2 && dy <= 4) {
            return dx == 0 && dz == 0 ? TABlocks.RIFT_CRISTAL_BLOCK.getDefaultState() : null;
        }

        return null;
    }

    public static BlockRiftonomicon.Part getRiftonomiconPartFor(int dx, int dy, int dz) {
        int ax = Math.abs(dx);
        int az = Math.abs(dz);

        if (dy == 0) {
            if (dx == 0 && dz == -3) {
                return BlockRiftonomicon.Part.CORE;
            }
            if (az == 3 && ax <= 1) {
                return BlockRiftonomicon.Part.EDGE;
            }
            if (az == 2 && ax <= 2) {
                return ax == 2 ? BlockRiftonomicon.Part.CORNER : BlockRiftonomicon.Part.BASE;
            }
            return ax == 3 ? BlockRiftonomicon.Part.EDGE : BlockRiftonomicon.Part.BASE;
        }

        if (dy == 1) {
            return BlockRiftonomicon.Part.COLUMN;
        }
        return BlockRiftonomicon.Part.TOP;
    }

    private static Part riftonomiconPart(Object source, BlockRiftonomicon.Part targetPart) {
        return new Part(
                source,
                new ItemStack(
                        TABlocks.RIFTONOMICON,
                        1,
                        TABlocks.RIFTONOMICON.getMetaFromState(
                                TABlocks.RIFTONOMICON.getDefaultState()
                                        .withProperty(BlockRiftonomicon.PART, targetPart)
                        )
                )
        );
    }

    private static void registerRiftonomiconBlueprint() {
        Part[][][] parts = buildRiftonomiconBlueprintStructure();

        ThaumcraftApi.BluePrint bp = new ThaumcraftApi.BluePrint(
                "TA_RIFT_EXTRACTOR",
                new ItemStack(TABlocks.RIFTONOMICON),
                parts
        );

        ThaumcraftApi.addMultiblockRecipeToCatalog(RIFTONOMICON_MB_ID, bp);
    }
}
