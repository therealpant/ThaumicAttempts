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

    /**
     * Структура ДЛЯ ТРИГГЕРА (то, что будет заменяться/удаляться при активации).
     *
     * В stable_39 "pos" (куда нанесена пыль) соответствует НИЖНЕМУ слою структуры,
     * то есть последнему индексу по Y. Поэтому базу кладём в последний Y.
     *
     * После срабатывания:
     * - база -> менеджер
     * - остальное -> AIR
     */
    private static Part[][][] buildTriggerStructure() {
        Part eldToAir  = new Part(BlocksTC.stoneEldritchTile, "AIR");
        Part coreToAir = new Part(TABlocks.MIRROR_MANAGER_CORE, "AIR");
        Part baseToMgr = new Part(BlocksTC.stoneEldritchTile, TABlocks.MIRROR_MANAGER);

        return new Part[][][] {
                // Y = 0 (ВЕРХ)
                { { eldToAir } },
                // Y = 1 (СЕРЕДИНА)
                { { coreToAir } },
                // Y = 2 (НИЗ, СЮДА КЛИКАЕМ ПЫЛЬЮ)
                { { baseToMgr } }
        };
    }

    /**
     * Blueprint ДЛЯ КНИГИ (показывает “как построить ДО активации”).
     *
     * ВНИМАНИЕ: blueprint не должен содержать AIR и “base->manager”.
     * Он должен содержать исходные блоки, которые игрок ставит руками.
     */
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

    /**
     * Структура для КНИГИ:
     * верх: Eldritch Tile
     * середина: CORE
     * низ: BASE (по ней потом активируем)
     *
     * Почему new Part(block, block)?
     * Потому что Part в stable_39 НЕ имеет конструктора с одним аргументом.
     * Для blueprint мы хотим “ничего не заменять”, поэтому source=target.
     */
    private static Part[][][] buildBlueprintStructure() {
        Part top    = new Part(BlocksTC.stoneEldritchTile, BlocksTC.stoneEldritchTile);
        Part middle = new Part(TABlocks.MIRROR_MANAGER_CORE, TABlocks.MIRROR_MANAGER_CORE);
        Part bottom = new Part(BlocksTC.stoneEldritchTile, BlocksTC.stoneEldritchTile);

        return new Part[][][] {
                // Y = 0 (ВЕРХ)
                { { top } },
                // Y = 1 (СЕРЕДИНА)
                { { middle } },
                // Y = 2 (НИЗ)
                { { bottom } }
        };
    }
}
