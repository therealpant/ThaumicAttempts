package therealpant.thaumicattempts.api;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public interface ICraftEndpoint {
    /**
     * Все результаты, которые это рабочее место умеет крафтить.
     * Каждый ItemStack — это результат РОВНО ОДНОГО цикла крафта,
     * с корректным count.
     */
    List<ItemStack> listCraftableResults();

    /**
     * Сколько штук результата даёт ровно один цикл крафта
     * для результата "как этот".
     */
    int getPerCraftOutputCountFor(ItemStack like);

    /**
     * Полный список входов для результата "как этот" на заданное число ЦИКЛОВ крафта.
     *
     * ВАЖНО:
     * - параметр times = именно количество craft cycles,
     *   а НЕ желаемое количество выходных предметов;
     * - если рецепт даёт 4 предмета за цикл, то:
     *      getPerCraftOutputCountFor(...) == 4
     *      getRecipeInputsFor(result, 1) -> входы на 1 цикл
     *      getRecipeInputsFor(result, 4) -> входы на 4 цикла
     */
    List<ItemStack> getRecipeInputsFor(ItemStack resultLike, int times);

    /**
     * Поставить в очередь крафт результата "как этот"
     * на указанное количество циклов.
     * Реализация сама объединяет с существующей очередью.
     */
    void enqueueCraft(ItemStack resultLike, int crafts);

    /**
     * Единая точка входа для менеджера:
     * - managerPos: позиция менеджера-инициатора (может быть null)
     * - returnDest/returnSide: куда вернуть готовый результат (терминал/другой буфер)
     * - amount: требуемое количество результата в штуках (не в циклах)
     *
     * Возвращает принятое количество результата (в штуках). 0 = заказ не принят.
     */
    default int enqueueCraftOrder(BlockPos managerPos, BlockPos returnDest, int returnSide, ItemStack resultLike, int amount) {
        if (resultLike == null || resultLike.isEmpty() || amount <= 0) return 0;
        int perCraft = Math.max(1, getPerCraftOutputCountFor(resultLike));
        int crafts = (amount + perCraft - 1) / perCraft;
        if (crafts <= 0) return 0;
        enqueueCraft(resultLike, crafts);
        return crafts * perCraft;
    }

    /**
     * Прямой запуск назначенного сетевого execution task-а.
     *
     * По умолчанию откатывается к enqueueCraftOrder(...) ради совместимости,
     * но manager-mode endpoint-ы могут переопределить и запустить локальный
     * цикл без requester/redstone orchestration.
     */
    default int startAssignedCraftTask(BlockPos managerPos, BlockPos returnDest, int returnSide, ItemStack resultLike, int amount) {
        return enqueueCraftOrder(managerPos, returnDest, returnSide, resultLike, amount);
    }

    /**
     * Есть ли активные или поставленные в очередь циклы.
     */
    boolean hasActiveOrQueued();

    default BlockPos getCraftTaskOutputPos(BlockPos endpointPos) {
        return endpointPos;
    }

}