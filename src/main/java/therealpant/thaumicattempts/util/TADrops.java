// src/main/java/therealpant/thaumicattempts/util/TADrops.java
package therealpant.thaumicattempts.util;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;
import java.util.Random;

public final class TADrops {
    private TADrops() {}

    /**
     * 5%  - 0 шт
     * 85% - 1 шт
     * 10% - 2 шт
     */
    public static int rollCount(Random rand) {
        int roll = rand.nextInt(100); // 0..99
        if (roll < 5)  return 0;      // 5%  (0..4)
        if (roll < 90) return 1;      // 85% (5..89)
        return 2;                      // 10% (90..99)
    }

    /**
     * Добавляет дроп по шансам:
     * 5% ничего, 85% 1, 10% 2.
     *
     * pos не обязателен для логики (оставлен для удобства/будущих расширений),
     * но можно передавать null.
     */
    public static void addRiftDrop(List<ItemStack> drops, World world, BlockPos pos, Item item) {
        if (drops == null || world == null || item == null) return;

        // Используем rand мира (на сервере это уже норм), но оставим твой "на всякий"
        Random rand = world.rand;
        if (world instanceof WorldServer) {
            rand = ((WorldServer) world).rand;
        }

        int count = rollCount(rand);
        if (count > 0) {
            drops.add(new ItemStack(item, count));
        }
    }

    /**
     * Упрощённая версия без pos.
     */
    public static void addRiftDropFixed(List<ItemStack> drops, World world, Item item) {
        addRiftDrop(drops, world, null, item);
    }
}
