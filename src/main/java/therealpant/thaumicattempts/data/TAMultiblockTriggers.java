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
import therealpant.thaumicattempts.golemnet.block.BlockEldritchConstarction;
import therealpant.thaumicattempts.world.block.BlockRiftExtractor;
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

    public static final ResourceLocation TRANSMUTATION_ALTAR_MB_ID =
            new ResourceLocation(ThaumicAttempts.MODID, "transmutation_altar_multiblock");

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
                "TA_RIFTONOMICON_CREATION",
                buildRiftExtractorTriggerStructure()
            );
            IDustTrigger.registerDustTrigger(riftExtractorTrigger);

            DustTriggerMultiblock riftPortalTrigger = new DustTriggerMultiblock(
                "TA_RIFTONOMICON_NAVIGATION",
                buildRiftPortalTriggerStructure()
            );
            IDustTrigger.registerDustTrigger(riftPortalTrigger);

            DustTriggerMultiblock transmutationAltarTrigger = new DustTriggerMultiblock(
                "TA_RIFTONOMICON",
                buildTransmutationAltarTriggerStructure()
            );
            IDustTrigger.registerDustTrigger(transmutationAltarTrigger);

            DustTriggerMultiblock riftStoneFurnaceTrigger = new DustTriggerMultiblock(
                "TA_RIFTONOMICON_ALCHEMY",
                buildRiftStoneFurnaceTriggerStructure()
            );
            IDustTrigger.registerDustTrigger(riftStoneFurnaceTrigger);

            DustTriggerMultiblock riftonomiconTrigger = new DustTriggerMultiblock(
                "TA_RIFTONOMICON",
                buildRiftonomiconTriggerStructure()
            );
            IDustTrigger.registerDustTrigger(riftonomiconTrigger);

            // 2) Blueprint для Таумономикона (схема постройки)
            registerBlueprint();
            registerAuraBoosterBlueprint();
            registerRiftExtractorBlueprint();
            registerRiftPortalBlueprint();
            registerTransmutationAltarBlueprint();
            registerRiftStoneFurnaceBlueprint();
            registerRiftonomiconBlueprint();
    }


    private static Part[][][] buildTriggerStructure() {
        ItemStack amber = eldritchConstruction(BlockEldritchConstarction.Variant.AMBER);
        Part topToAir = new Part(amber, "AIR");
        Part bottomToManager = new Part(amber.copy(), TABlocks.MIRROR_MANAGER);

        return new Part[][][] {
                { { topToAir } },
                { { bottomToManager } }
        };
    }

    private static Part[][][] buildAuraBoosterTriggerStructure() {
        Part topToAir = new Part(TABlocks.RIFT_CRISTAL_BLOCK, "AIR");
        Part middleToAir = new Part(TABlocks.RIFT_CRISTAL_BLOCK, "AIR");
        Part baseToBooster = new Part(
                eldritchConstruction(BlockEldritchConstarction.Variant.DIAMOND),
                TABlocks.AURA_BOOSTER
        );
        Part baseToAir = new Part(eldritchConstruction(BlockEldritchConstarction.Variant.DIAMOND), "AIR");
        return new Part[][][] {
                { { topToAir } },
                { { baseToAir } },
                { { middleToAir } },
                { { baseToBooster } }
        };
    }

    private static Part[][][] buildRiftExtractorTriggerStructure() {
        Part topToAir = new Part(eldritchConstruction(BlockEldritchConstarction.Variant.AMETHYST), "AIR");
        Part middleToAir = new Part(eldritchConstruction(BlockEldritchConstarction.Variant.AMETHYST), "AIR");
        Part baseToBooster = new Part(eldritchConstruction(BlockEldritchConstarction.Variant.AMETHYST), TABlocks.RIFT_EXTRACTOR);
        Part darkToPlatform = new Part(eldritchConstruction(BlockEldritchConstarction.Variant.BASE), TABlocks.RIFT_PORTAL_PLATFORM);
        Part polishedToPlatform = new Part(TABlocks.POLISHED_DARK_JASPER, TABlocks.RIFT_PORTAL_PLATFORM);

        return new Part[][][] {
                // Y = 3 (ВЕРХ КОЛОННЫ)
                {
                        { null, null, null },
                        { null, topToAir, null },
                        { null, null, null }
                },
                // Y = 2 (СЕРЕДИНА КОЛОННЫ)
                {
                        { null, null, null },
                        { null, middleToAir, null },
                        { null, null, null }
                },
                // Y = 1 (ОСНОВАНИЕ КОЛОННЫ, СЮДА КЛИКАЕМ ПЫЛЬЮ)
                {
                        { null, null, null },
                        { null, baseToBooster, null },
                        { null, null, null }
                },
                // Y = 0 (ПЛАТФОРМА)
                {
                        { darkToPlatform, polishedToPlatform, darkToPlatform },
                        { polishedToPlatform, polishedToPlatform, polishedToPlatform },
                        { darkToPlatform, polishedToPlatform, darkToPlatform }
                }
        };
    }

    private static Part[][][] buildBlueprintStructure() {
        ItemStack amber = eldritchConstruction(BlockEldritchConstarction.Variant.AMBER);
        Part top = new Part(amber, "AIR");
        Part bottom = new Part(amber.copy(), TABlocks.MIRROR_MANAGER);

        return new Part[][][] {
                { { top } },
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
        Part top = new Part(TABlocks.RIFT_CRISTAL_BLOCK, "AIR");
        Part middle = new Part(TABlocks.RIFT_CRISTAL_BLOCK, "AIR");
        ItemStack diamondConstruction = eldritchConstruction(BlockEldritchConstarction.Variant.DIAMOND);
        Part bottom = new Part(diamondConstruction, TABlocks.AURA_BOOSTER);
        Part middle2 = new Part(diamondConstruction, "AIR");
        return new Part[][][] {
                { { top } },
                { { middle2 } },
                { { middle } },
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
        ItemStack baseConstruction = eldritchConstruction(BlockEldritchConstarction.Variant.AMETHYST);
        Part top = new Part(baseConstruction, extractorPart(BlockRiftExtractor.Part.TOP));
        Part middle = new Part(baseConstruction.copy(), extractorPart(BlockRiftExtractor.Part.MID));
        Part base = new Part(baseConstruction, extractorPart(BlockRiftExtractor.Part.LOW));
        Part dark = new Part(eldritchConstruction(BlockEldritchConstarction.Variant.BASE), TABlocks.RIFT_PORTAL_PLATFORM);
        Part polished = new Part(TABlocks.POLISHED_DARK_JASPER, TABlocks.RIFT_PORTAL_PLATFORM);
        return new Part[][][] {
                {
                        { null, null, null },
                        { null, top, null },
                        { null, null, null }
                },
                {
                        { null, null, null },
                        { null, middle, null },
                        { null, null, null }
                },
                {
                        { null, null, null },
                        { null, base, null },
                        { null, null, null }
                },
                {
                        { dark, polished, dark },
                        { polished, polished, polished },
                        { dark, polished, dark }
                }
        };
    }

    private static ItemStack extractorPart(BlockRiftExtractor.Part part) {
        return new ItemStack(
                TABlocks.RIFT_EXTRACTOR,
                1,
                TABlocks.RIFT_EXTRACTOR.getMetaFromState(
                        TABlocks.RIFT_EXTRACTOR.getDefaultState().withProperty(BlockRiftExtractor.PART, part)
                )
        );
    }

    private static void registerRiftExtractorBlueprint() {
        Part[][][] parts = buildRiftExtractorBlueprintStructure();

        ThaumcraftApi.BluePrint bp = new ThaumcraftApi.BluePrint(
                "TA_RIFTONOMICON_CREATION",
                new ItemStack(TABlocks.RIFT_EXTRACTOR),
                parts
        );

        ThaumcraftApi.addMultiblockRecipeToCatalog(RIFT_EXTRACTOR_MB_ID, bp);
    }

    private static Part[][][] buildRiftPortalTriggerStructure() {
        Part darkToAir = new Part(eldritchConstruction(BlockEldritchConstarction.Variant.BASE), "AIR");
        Part darkToPortal = new Part(eldritchConstruction(BlockEldritchConstarction.Variant.BASE), TABlocks.RIFT_STONE_PORTAL);
        Part darkToPlatform = new Part(eldritchConstruction(BlockEldritchConstarction.Variant.BASE), TABlocks.RIFT_PORTAL_PLATFORM);
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
        Part darkToAir = new Part(eldritchConstruction(BlockEldritchConstarction.Variant.BASE), "AIR");
        Part darkToPortal = new Part(eldritchConstruction(BlockEldritchConstarction.Variant.BASE), TABlocks.RIFT_STONE_PORTAL);
        Part dark = new Part(eldritchConstruction(BlockEldritchConstarction.Variant.BASE), TABlocks.RIFT_PORTAL_PLATFORM);
        Part polished = new Part(TABlocks.POLISHED_DARK_JASPER, TABlocks.RIFT_PORTAL_PLATFORM);

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
                        { dark, polished, dark },
                        { polished, polished, polished },
                        { dark, polished, dark }
                }
        };
    }

    private static void registerRiftPortalBlueprint() {
        Part[][][] parts = buildRiftPortalBlueprintStructure();

        ThaumcraftApi.BluePrint bp = new ThaumcraftApi.BluePrint(
                "TA_RIFTONOMICON",
                new ItemStack(TABlocks.RIFT_STONE_PORTAL),
                parts
        );

        ThaumcraftApi.addMultiblockRecipeToCatalog(RIFT_PORTAL_MB_ID, bp);
    }

    private static Part[][][] buildTransmutationAltarTriggerStructure() {
        Part darkToAltar = new Part(eldritchConstruction(BlockEldritchConstarction.Variant.BASE), TABlocks.RIFT_STONE_ALTAR);
        Part darkToPlatform = new Part(eldritchConstruction(BlockEldritchConstarction.Variant.BASE), TABlocks.RIFT_PORTAL_PLATFORM);
        Part polishedToPlatform = new Part(TABlocks.POLISHED_DARK_JASPER, TABlocks.RIFT_PORTAL_PLATFORM);
        return new Part[][][] {
                {
                        { null, null, null },
                        { null, darkToAltar, null },
                        { null, null, null }
                },
                {
                        { darkToPlatform, polishedToPlatform, darkToPlatform },
                        { polishedToPlatform, polishedToPlatform, polishedToPlatform },
                        { darkToPlatform, polishedToPlatform, darkToPlatform }
                }
        };
    }

    private static Part[][][] buildTransmutationAltarBlueprintStructure() {
        Part darkToAltar = new Part(eldritchConstruction(BlockEldritchConstarction.Variant.BASE), TABlocks.RIFT_STONE_ALTAR);
        Part dark = new Part(eldritchConstruction(BlockEldritchConstarction.Variant.BASE), TABlocks.RIFT_PORTAL_PLATFORM);
        Part polished = new Part(TABlocks.POLISHED_DARK_JASPER, TABlocks.RIFT_PORTAL_PLATFORM);
        return new Part[][][] {
                {
                        { null, null, null },
                        { null, darkToAltar, null },
                        { null, null, null }
                },
                {
                        { dark, polished, dark },
                        { polished, polished, polished },
                        { dark, polished, dark }
                }
        };
    }

    private static void registerTransmutationAltarBlueprint() {
        ThaumcraftApi.BluePrint bp = new ThaumcraftApi.BluePrint(
                "TA_RIFTONOMICON",
                new ItemStack(TABlocks.RIFT_STONE_ALTAR),
                buildTransmutationAltarBlueprintStructure()
        );
        ThaumcraftApi.addMultiblockRecipeToCatalog(TRANSMUTATION_ALTAR_MB_ID, bp);
    }

    private static Part[][][] buildRiftStoneFurnaceTriggerStructure() {
        return new Part[][][] {
                {
                        {
                                furnacePart(BlocksTC.alembic, BlockRiftStoneFurnace.Part.TOP_CORNER),
                                furnacePart(eldritchConstruction(BlockEldritchConstarction.Variant.BASE), BlockRiftStoneFurnace.Part.TOP_NORTH),
                                furnacePart(BlocksTC.alembic, BlockRiftStoneFurnace.Part.TOP_CORNER)
                        },
                        {
                                furnacePart(eldritchConstruction(BlockEldritchConstarction.Variant.BASE), BlockRiftStoneFurnace.Part.TOP_WEST),
                                null,
                                furnacePart(eldritchConstruction(BlockEldritchConstarction.Variant.BASE), BlockRiftStoneFurnace.Part.TOP_EAST)
                        },
                        {
                                furnacePart(BlocksTC.alembic, BlockRiftStoneFurnace.Part.TOP_CORNER),
                                furnacePart(eldritchConstruction(BlockEldritchConstarction.Variant.BASE), BlockRiftStoneFurnace.Part.TOP_SOUTH),
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
        return buildRiftStoneFurnaceTriggerStructure();
    }

    private static Part furnacePart(Object source, BlockRiftStoneFurnace.Part targetPart) {
        if (source == TABlocks.ELDRITCH_CONSTRUCTION) {
            source = eldritchConstruction(BlockEldritchConstarction.Variant.BASE);
        }
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

    /** Metadata-aware source used so every inserted gem is a distinct multiblock part. */
    public static ItemStack eldritchConstruction(BlockEldritchConstarction.Variant variant) {
        return new ItemStack(TABlocks.ELDRITCH_CONSTRUCTION, 1, variant.getMeta());
    }

    private static void registerRiftStoneFurnaceBlueprint() {
        Part[][][] parts = buildRiftStoneFurnaceBlueprintStructure();

        ThaumcraftApi.BluePrint bp = new ThaumcraftApi.BluePrint(
                "TA_RIFTONOMICON_ALCHEMY",
                new ItemStack(TABlocks.RIFT_STONE_FURNACE),
                parts
        );

        ThaumcraftApi.addMultiblockRecipeToCatalog(RIFT_STONE_FURNACE_MB_ID, bp);
    }

    private static Part[][][] buildRiftonomiconTriggerStructure() {
        return buildRiftonomiconStructure(true);
    }

    private static Part[][][] buildRiftonomiconBlueprintStructure() {
        return buildRiftonomiconStructure(true);
    }

    private static Part[][][] buildRiftonomiconStructure(boolean transform) {
        Object n = null;
        Object brick = eldritchConstruction(BlockEldritchConstarction.Variant.BASE);
        Object polished = TABlocks.POLISHED_DARK_JASPER;
        Object core = TABlocks.RIFT_CRISTAL_BLOCK;

        Object[][][] sources = new Object[][][] {
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
                        { n, brick, brick, polished, brick, brick, n },
                        { brick, brick, n, n, n, brick, brick },
                        { polished, polished, n, n, n, polished, polished },
                        { brick, brick, n, n, n, brick, brick },
                        { n, brick, brick, polished, brick, brick, n },
                        { n, n, brick, polished, brick, n, n }
                }
        };

        // The visual center is empty in the blueprint, but after transformation it must
        // become an invisible walkable floor. "AIR" keeps the original structure recipe
        // unchanged and lets the break helper restore these positions back to air.
        for (int z = 2; z <= 4; z++) {
            for (int x = 2; x <= 4; x++) {
                if (sources[4][z][x] == null) {
                    sources[4][z][x] = "AIR";
                }
            }
        }

        // Four invisible fourth-layer blocks host the physical impetus nodes.
        sources[1][1][3] = "AIR";
        sources[1][5][3] = "AIR";
        sources[1][3][1] = "AIR";
        sources[1][3][5] = "AIR";

        return buildRiftonomiconParts(sources, transform);
    }

    private static Part[][][] buildRiftonomiconParts(Object[][][] sources, boolean transform) {
        Part[][][] parts = new Part[sources.length][][];
        for (int y = 0; y < sources.length; y++) {
            parts[y] = new Part[sources[y].length][];
            for (int z = 0; z < sources[y].length; z++) {
                parts[y][z] = new Part[sources[y][z].length];
            }
        }

        // Three atomic visual steps: base, columns, then all upper layers. The
        // controller core joins the first group so partial model stages can render.
        final int basePriority = 20;
        final int columnsPriority = 40;
        final int upperPriority = 60;
        for (int y = sources.length - 1; y >= 0; y--) {
            int dy = sources.length - 1 - y;
            for (int z = 0; z < sources[y].length; z++) {
                int dz = z - 3;
                for (int x = 0; x < sources[y][z].length; x++) {
                    Object source = sources[y][z][x];
                    if (source == null) {
                        continue;
                    }

                    int dx = x - 3;
                    boolean controllerCore = dx == 0 && dy == BlockRiftonomicon.CORE_Y_OFFSET && dz == 0;
                    int priority = controllerCore || dy == 0
                            ? basePriority
                            : dy == 1 ? columnsPriority : upperPriority;
                    parts[y][z][x] = riftonomiconStructurePart(source, dx, dy, dz, transform, priority);
                }
            }
        }
        return parts;
    }

    private static Part riftonomiconStructurePart(Object source, int dx, int dy, int dz,
                                                  boolean transform, int priority) {
        return transform
                ? riftonomiconPart(source, getRiftonomiconPartFor(dx, dy, dz), priority)
                : new Part(source, source, false, priority);
    }

    public static net.minecraft.block.state.IBlockState getRiftonomiconSourceState(int dx, int dy, int dz) {
        Object source = getRiftonomiconSource(dx, dy, dz);
        if (source instanceof net.minecraft.block.Block) {
            return ((net.minecraft.block.Block) source).getDefaultState();
        }
        if (source instanceof ItemStack) {
            ItemStack stack = (ItemStack) source;
            net.minecraft.block.Block block = net.minecraft.block.Block.getBlockFromItem(stack.getItem());
            return block == net.minecraft.init.Blocks.AIR ? null : block.getStateFromMeta(stack.getMetadata());
        }
        return null;
    }

    private static Object getRiftonomiconSource(int dx, int dy, int dz) {
        int ax = Math.abs(dx);
        int az = Math.abs(dz);

        if (dy == 0) {
            if (dx == 0 && dz == 0) {
                return BlockRiftonomicon.Part.TRANSMUTATION_ESSENTIA_INPUT;
            }
            if (dx == 0 && dz == 0) return BlockRiftonomicon.Part.TRANSMUTATION_IMPETUS_INPUT;
            if ((az == 3 && ax <= 1) || (az == 2 && ax <= 2) || (ax >= 2 && ax <= 3)) {
                return (dx == 0 || dz == 0)
                        ? TABlocks.POLISHED_DARK_JASPER
                        : eldritchConstruction(BlockEldritchConstarction.Variant.BASE);
            }
            return null;
        }

        if (dy == 1) {
            return ax == 2 && az == 2
                    ? eldritchConstruction(BlockEldritchConstarction.Variant.BASE)
                    : null;
        }

        if (dy == 2 || dy == 4) {
            return dx == 0 && dz == 0
                    ? eldritchConstruction(BlockEldritchConstarction.Variant.BASE)
                    : null;
        }

        if (dy == 3) {
            if (dx == 0 && dz == 0) return TABlocks.RIFT_CRISTAL_BLOCK;
            if ((Math.abs(dx) == 2 && dz == 0) || (Math.abs(dz) == 2 && dx == 0)) return "AIR";
            return null;
        }

        return null;
    }

    public static BlockRiftonomicon.Part getRiftonomiconPartFor(int dx, int dy, int dz) {
        int ax = Math.abs(dx);
        int az = Math.abs(dz);

        if (dy == 0) {
            if (dx == 0 && dz == 0) {
                return BlockRiftonomicon.Part.TRANSMUTATION_ESSENTIA_INPUT;
            }
            if (ax == 2 && az == 2) {
                return BlockRiftonomicon.Part.CORNER;
            }
            if (ax == 3 || az == 3) {
                return BlockRiftonomicon.Part.EDGE;
            }
            return BlockRiftonomicon.Part.BASE;
        }

        if (dy == 1) {
            return BlockRiftonomicon.Part.COLUMN;
        }

        if (dy == 3 && dx == 0 && dz == 0) {
            return BlockRiftonomicon.Part.CORE;
        }

        if (dy == 4 && dx == 0 && dz == 0) {
            return BlockRiftonomicon.Part.TRANSMUTATION_IMPETUS_INPUT;
        }

        if (dy == 3 && ((ax == 2 && dz == 0) || (az == 2 && dx == 0))) {
            return BlockRiftonomicon.Part.IMPETUS_PORT;
        }

        return BlockRiftonomicon.Part.TOP;
    }

    private static Part riftonomiconPart(Object source, BlockRiftonomicon.Part targetPart, int priority) {
        return new Part(
                source,
                new ItemStack(
                        TABlocks.RIFTONOMICON,
                        1,
                        TABlocks.RIFTONOMICON.getMetaFromState(
                                TABlocks.RIFTONOMICON.getDefaultState()
                                        .withProperty(BlockRiftonomicon.PART, targetPart)
                        )
                ),
                false,
                priority
        );
    }

    public static boolean hasRiftonomiconPartAt(int dx, int dy, int dz) {
        int ax = Math.abs(dx);
        int az = Math.abs(dz);
        if (ax > 3 || az > 3 || dy < 0 || dy > 4) return false;
        if (dy == 0) {
            if (ax <= 1 && az <= 1) return true;
            return (az == 3 && ax <= 1)
                    || (az == 2 && ax <= 2)
                    || (az <= 1 && ax >= 2);
        }
        if (dy == 1) return ax == 2 && az == 2;
        if (dy == 2 || dy == 4) return dx == 0 && dz == 0;
        return dy == 3 && ((dx == 0 && dz == 0)
                || (ax == 2 && dz == 0)
                || (az == 2 && dx == 0));
    }

    private static void registerRiftonomiconBlueprint() {
        Part[][][] parts = buildRiftonomiconBlueprintStructure();

        ThaumcraftApi.BluePrint bp = new ThaumcraftApi.BluePrint(
                "TA_RIFTONOMICON",
                new ItemStack(TABlocks.RIFTONOMICON),
                parts
        );

        ThaumcraftApi.addMultiblockRecipeToCatalog(RIFTONOMICON_MB_ID, bp);
    }
}
