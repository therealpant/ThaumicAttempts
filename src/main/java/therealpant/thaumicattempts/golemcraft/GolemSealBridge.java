// src/main/java/therealpant/thaumicattemts/golemcraft/GolemSealBridge.java
package therealpant.thaumicattempts.golemcraft;

import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Мост для обращения к печатям Thaumcraft 6 без жёсткой зависимости.
 * Идея: сформировать "запрос" недостающих стаков и отдать его в менеджер печатей,
 * если соответствующие классы/методы есть. В противном случае — просто no-op.
 */
public final class GolemSealBridge {
    private GolemSealBridge(){}

    /** Элемент запроса: что и сколько притащить в конкретный индекс входа 3×3 */
    public static class MissingEntry {
        public final ItemStack stack; public final int slot;
        public MissingEntry(ItemStack s, int slot){ this.stack = s.copy(); this.slot = slot; }
    }

    /**
     * Попытаться создать задачу "принести вещи" через печати возле TE.
     * Возвращает true, если удалось «пнуть» систему задач (по рефлексии).
     */
    @SuppressWarnings("unchecked")
    public static boolean requestViaSeals(World w, BlockPos here, TileEntity te, EnumDyeColor color, List<MissingEntry> missing){
        try {
            // Примерная цель: thaumcraft.common.golems.seals.SealHandler.requestProvision(...)
            // или похожий помощник. Делаем рефлексию с запасом: ищем класс по нескольким вариантам.
            Class<?> handler =
                    tryClass("thaumcraft.common.golems.seals.SealHandler",
                            tryClass("thaumcraft.common.golems.seals.SealUtilities", null));
            if (handler == null) return false;

            // Готовим простые структуры (позиция, цвет, список ItemStack)
            List<ItemStack> stacks = new ArrayList<>();
            for (MissingEntry m : missing) stacks.add(m.stack);

            // Ищем любой публичный статический метод, который принимает (World, BlockPos, TileEntity, EnumDyeColor, List)
            Method target = null;
            for (Method m : handler.getMethods()){
                Class<?>[] p = m.getParameterTypes();
                if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
                if (m.getReturnType() != boolean.class) continue;
                if (p.length == 5 &&
                        p[0] == World.class &&
                        p[1] == BlockPos.class &&
                        TileEntity.class.isAssignableFrom(p[2]) &&
                        (p[3].getName().endsWith("EnumDyeColor") || p[3] == EnumDyeColor.class) &&
                        List.class.isAssignableFrom(p[4])) {
                    target = m; break;
                }
            }
            if (target == null) return false;

            Object ok = target.invoke(null, w, here, te, color, stacks);
            return ok instanceof Boolean && (Boolean) ok;
        } catch (Throwable t) {
            // тихий лог — без зависимости на сторонний логгер
            System.out.println("[ThaumicAttempts] Seal bridge failed: " + t.getClass().getSimpleName());
            return false;
        }
    }

    private static Class<?> tryClass(String name, Class<?> fallback){
        try { return Class.forName(name); } catch (Throwable ignored) { return fallback; }
    }
}
