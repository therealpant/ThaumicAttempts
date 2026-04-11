package therealpant.thaumicattempts.api;

import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;

/**
 * Единая точка входа для automation-заказов.
 * Терминал и редстоун должны вызывать один и тот же метод.
 */
public interface IAutomationOrderAcceptor {
    /**
     * @param slot       индекс паттерна/иконки
     * @param items      желаемое количество итогового результата (не число крафтов)
     * @param managerPos менеджер, через которого ведётся снабжение (может быть null)
     * @param dest       точка доставки результата (для terminal-flow), null для локального redstone-flow
     * @param destSide   сторона доставки, обычно -1
     * @return количество принятых итоговых предметов (items), либо 0 если заказ не принят
     */
    int submitAutomationOrder(int slot, int items,
                              @Nullable BlockPos managerPos,
                              @Nullable BlockPos dest,
                              int destSide);
}