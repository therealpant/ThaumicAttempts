package therealpant.thaumicattempts.data;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.crafting.IDustTrigger;
import thaumcraft.api.crafting.Part;
import thaumcraft.common.lib.crafting.DustTriggerMultiblock;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.init.TABlocks;

public final class TAMultiblockTriggers {
    private TAMultiblockTriggers() {}

    public static void register() {
        try {
            // В stable_39 конструктор: (String research, Part[][][] blueprint)
            DustTriggerMultiblock trigger = new DustTriggerMultiblock(
                    "TA_GOLEM_MIRRORS",
                    buildStructure()
            );

            IDustTrigger.registerDustTrigger(trigger);

            ThaumicAttempts.LOGGER.info("Registered mirror manager multiblock dust trigger.");
        } catch (Throwable t) {
            ThaumicAttempts.LOGGER.warn("Mirror manager dust trigger registration failed.", t);
        }
    }

    /**
     * ВАЖНО:
     * В stable_39 "pos" (куда нанесена пыль) соответствует НИЖНЕМУ слою структуры,
     * то есть последнему индексу по Y.
     *
     * Поэтому БАЗУ (по которой кликаем) кладём в самый нижний слой (последний Y).
     *
     * После срабатывания:
     * - база -> менеджер
     * - остальное -> AIR
     */
    private static Part[][][] buildStructure() {
        Part eldToAir  = new Part(BlocksTC.stoneEldritchTile, "AIR");
        Part coreToAir = new Part(TABlocks.MIRROR_MANAGER_CORE, "AIR");
        Part baseToMgr = new Part(TABlocks.MIRROR_MANAGER_BASE, TABlocks.MIRROR_MANAGER);

        return new Part[][][] {
                // Y = 0 (ВЕРХ)
                { { eldToAir } },
                // Y = 1 (СЕРЕДИНА)
                { { coreToAir } },
                // Y = 2 (НИЗ, СЮДА КЛИКАЕМ ПЫЛЬЮ)
                { { baseToMgr } }
        };
    }
}
