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

public final class TAMultiblockTriggers {
    private TAMultiblockTriggers() {}

    /** Этот ID ты потом укажешь в research json в "recipes": "thaumicattempts:mirror_manager_multiblock" */
    public static final ResourceLocation MIRROR_MANAGER_MB_ID =
            new ResourceLocation(ThaumicAttempts.MODID, "mirror_manager_multiblock");

    public static void register() {
        try {
            // 1) Триггер мультиблока (Salis Mundus)
            DustTriggerMultiblock trigger = new DustTriggerMultiblock(
                    "TA_GOLEM_MIRRORS",
                    buildTriggerStructure()
            );
            IDustTrigger.registerDustTrigger(trigger);

            // 2) Blueprint для Таумономикона (схема постройки)
            registerBlueprint();

            ThaumicAttempts.LOGGER.info("Registered mirror manager multiblock dust trigger + blueprint.");
        } catch (Throwable t) {
            ThaumicAttempts.LOGGER.warn("Mirror manager multiblock registration failed.", t);
        }
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


}
