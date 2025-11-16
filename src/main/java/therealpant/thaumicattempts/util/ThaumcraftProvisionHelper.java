package therealpant.thaumicattempts.util;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import thaumcraft.api.golems.GolemHelper;
import thaumcraft.api.golems.tasks.Task;
import thaumcraft.common.golems.tasks.TaskHandler;
import therealpant.thaumicattempts.core.TAHooks;
import therealpant.thaumicattempts.golemnet.tile.TileMirrorManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import thaumcraft.api.golems.seals.SealPos;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ThaumcraftProvisionHelper {

    // ==== Безопасный доступ к TaskHandler.tasks =============================

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static ConcurrentHashMap<Integer, Task> getTasksSafe() {
        // 1) Пытаемся через публичное/статическое поле
        try {
            Object o = TaskHandler.tasks;
            ConcurrentHashMap<Integer, Task> flat = flattenTasksMap(o);
            if (flat != null) {
                return flat;
            }
        } catch (Throwable ignored) {
            // пойдём к reflect
        }

        // 2) Fallback через reflect (если вдруг поле не public / другое имя в маппинге)
        try {
            Field f = TaskHandler.class.getDeclaredField("tasks");
            f.setAccessible(true);
            Object o = f.get(null);
            ConcurrentHashMap<Integer, Task> flat = flattenTasksMap(o);
            if (flat != null) {
                return flat;
            }
        } catch (Throwable ignored) {
            // no-op
        }

        return null;
    }

    /**
     * Приводим layout TaskHandler.tasks к виду (taskId -> Task).
     * Поддерживаем 2 варианта:
     *  - Map<Integer, Task>
     *  - Map<?, Map<Integer, Task>> (per-dimension и т.п.)
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ConcurrentHashMap<Integer, Task> flattenTasksMap(Object o) {
        if (!(o instanceof ConcurrentHashMap)) {
            return null;
        }

        ConcurrentHashMap raw = (ConcurrentHashMap) o;

        // Попробуем понять по первому нормальному value, что внутри
        Object sample = null;
        for (Object v : raw.values()) {
            if (v != null) {
                sample = v;
                break;
            }
        }

        if (sample == null) {
            // пусто — нормально, просто вернуть как есть
            return (ConcurrentHashMap<Integer, Task>) raw;
        }

        // Вариант 1: сразу Task
        if (sample instanceof Task) {
            return (ConcurrentHashMap<Integer, Task>) raw;
        }

        // Вариант 2: значения — вложенные карты: dimId -> (taskId -> Task)
        if (sample instanceof ConcurrentHashMap || sample instanceof java.util.Map) {
            ConcurrentHashMap<Integer, Task> flat = new ConcurrentHashMap<>();

            for (Object innerObj : raw.values()) {
                if (!(innerObj instanceof java.util.Map)) continue;

                java.util.Map inner = (java.util.Map) innerObj;
                for (Object eObj : inner.entrySet()) {
                    java.util.Map.Entry e = (java.util.Map.Entry) eObj;
                    Object k = e.getKey();
                    Object v = e.getValue();
                    if (k instanceof Integer && v instanceof Task) {
                        flat.put((Integer) k, (Task) v);
                    }
                }
            }

            if (!flat.isEmpty()) {
                return flat;
            }
        }

        // Непонятный формат — не трогаем
        return null;
    }


    // ==== Provision от имени MirrorManager ==================================

    /**
     * Создать provisioning-запрос от имени менеджера.
     *
     * Если у менеджера есть связанные TileGolemDispatcher с живыми курьерами —
     * выбираем свободного голема и через TAHooks помечаем созданный ProvisionRequest,
     * а затем Task, как закреплённый за этим големом.
     *
     * Если нет — падаем обратно на обычный Thaumcraft'овский provisioning.
     */
    public static boolean requestProvisioningForManager(TileMirrorManager manager, ItemStack stack) {
        if (manager == null || stack == null || stack.isEmpty()) return false;
        if (manager.getWorld() == null) return false;

        // Берём текущие таски — нужно, чтобы findFreeDispatcherGolemUUID
        // понимал, какие големы уже заняты.
        ConcurrentHashMap<Integer, Task> tasks = getTasksSafe();

        // Ищем СВОБОДНОГО голема среди привязанных к диспетчерам менеджера.
        UUID golemId = manager.findFreeDispatcherGolemUUID(tasks);

        // Цвет канала менеджера (для ProvisionRequest / печати)
        int color = manager.getDispatcherSealColor();

        if (golemId == null) {
            // Нет свободного линкованного курьера:
            // НИЧЕГО НЕ СОЗДАЁМ.
            // Менеджерская очередь подождёт следующий тик.
            return false;
        }

        // Сообщаем патчу, какого голема зашить в создаваемый ProvisionRequest/Task.
        // onProvisionConstruct в TAHooks достанет этот UUID и пропишет в Task.golemUUID.
        TAHooks.pushDispatchGolem(golemId);
        try {
            GolemHelper.requestProvisioning(
                    manager.getWorld(),
                    manager.getPos(),
                    EnumFacing.UP,
                    stack,
                    color
            );
        } finally {
            TAHooks.popDispatchGolem();
        }

        return true;
    }

    public static boolean requestProvisioningForManagerWithGolem(TileMirrorManager manager,
                                                                 ItemStack stack,
                                                                 UUID golemId) {
        if (manager == null || stack == null || stack.isEmpty()) return false;
        if (manager.getWorld() == null) return false;
        if (golemId == null) return false;

        // Тут НЕ ищем свободных, не фоллбэкаем — считаем, что зовущий уже решил.
        TAHooks.pushDispatchGolem(golemId);
        try {
            GolemHelper.requestProvisioning(
                    manager.getWorld(),
                    manager.getPos(),
                    EnumFacing.UP,
                    stack,
                    manager.getDispatcherSealColor()
            );
        } finally {
            TAHooks.popDispatchGolem();
        }

        return true;
    }

}
