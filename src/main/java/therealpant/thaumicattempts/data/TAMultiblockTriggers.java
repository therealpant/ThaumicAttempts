package therealpant.thaumicattempts.data;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.crafting.IDustTrigger;
import thaumcraft.api.crafting.Part;
import thaumcraft.common.lib.crafting.DustTriggerMultiblock;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.init.TABlocks;

public final class TAMultiblockTriggers {
    private TAMultiblockTriggers() {}

    /** Этот ID ты потом укажешь в research json в "recipes": "thaumicattempts:mirror_manager_multiblock" */
    public static final ResourceLocation MIRROR_MANAGER_MB_ID =
            new ResourceLocation(ThaumicAttempts.MODID, "mirror_manager_multiblock");

    public static final ResourceLocation AURA_BOOSTER_MB_ID =
            new ResourceLocation(ThaumicAttempts.MODID, "aura_booster_multiblock");

    public static final ResourceLocation RIFT_EXTRACTOR_MB_ID =
            new ResourceLocation(ThaumicAttempts.MODID, "aura_booster_multiblock");

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

            // 2) Blueprint для Таумономикона (схема постройки)
            registerBlueprint();
            registerAuraBoosterBlueprint();
            registerRiftExtractorBlueprint();
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

        ThaumcraftApi.addMultiblockRecipeToCatalog(AURA_BOOSTER_MB_ID, bp);
    }
}
