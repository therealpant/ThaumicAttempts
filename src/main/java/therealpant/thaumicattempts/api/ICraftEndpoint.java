package therealpant.thaumicattempts.api;

import net.minecraft.item.ItemStack;

import java.util.List;

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

    /**
     * Есть ли активные или поставленные в очередь циклы.
     */
    boolean hasActiveOrQueued();
}
