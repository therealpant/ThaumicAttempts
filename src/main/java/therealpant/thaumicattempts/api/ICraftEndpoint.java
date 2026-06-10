package therealpant.thaumicattempts.api;

import net.minecraft.item.ItemStack;
import therealpant.thaumicattempts.util.ItemKey;

import java.util.List;
import java.util.Map;

public interface ICraftEndpoint {
    /**
     * Все результаты, которые это рабочее место умеет крафтить.
     * Каждый ItemStack – "как выглядит результат за один цикл", с правильным count.
     */
    List<ItemStack> listCraftableResults();

    /**
     * Сколько штук даёт ровно один цикл крафта для результата "как этот".
     */
    int getPerCraftOutputCountFor(ItemStack like);

    /**
     * Поставить в очередь крафт результата, "как этот", на указанное количество циклов.
     * Реализация сама объединяет с существующей очередью.
     */
    void enqueueCraft(ItemStack resultLike, int crafts);

    Map<ItemKey, Integer> getInputsPerCycle(ItemStack resultLike);

    int getOutputCount(ItemKey key);

    /**
     * Есть ли активные или поставленные в очередь циклы.
     */
    boolean hasActiveOrQueued();
}
