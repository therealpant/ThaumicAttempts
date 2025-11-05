// src/main/java/therealpant/thaumicattempts/golemnet/util/CraftYieldHelper.java
package therealpant.thaumicattempts.util;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;

/** Определяет «сколько предметов даёт один крафт» для целевого результата. */
public final class CraftYieldHelper {
    private CraftYieldHelper() {}

    public static int getCraftYield(World world, ItemStack resultLike) {
        if (resultLike == null || resultLike.isEmpty()) return 1;

        // 1) Пытаемся найти ванильный рецепт с таким же Item/метаданными
        int n = findVanillaRecipeYield(resultLike);
        if (n > 0) return n;

        // 2) (опционально) сюда можно добавить опрос аркан/собственных рецептов

        // 3) Фоллбэк — 1
        return 1;
    }

    /** Проход по CraftingManager.REGISTRY (1.12.x). */
    private static int findVanillaRecipeYield(ItemStack target) {
        for (IRecipe r : CraftingManager.REGISTRY) {
            ItemStack out = r.getRecipeOutput();
            if (out == null || out.isEmpty()) continue;

            // Сравнение по item и метаданным; NBT учитывай при необходимости
            boolean sameItem = out.getItem() == target.getItem()
                    && out.getMetadata() == target.getMetadata();

            if (sameItem) {
                int c = out.getCount();
                if (c > 0) return c;
            }
        }
        return 0;
    }
}
