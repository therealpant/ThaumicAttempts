package therealpant.thaumicattempts.integration;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import thaumcraft.api.golems.GolemHelper;

import java.util.*;

/**
 * Компактный helper:
 * - логгер;
 * - ключ предмета (item + meta + nbt);
 * - точки расширения под твою "шину крафта" (определи реальные нужды/остатки).
 *
 * ВАЖНО: здесь больше нет очередей Task/Payload и ручных moveOnce.
 * Только вычисление "что нужно" и "сколько ещё осталось".
 */
public final class ThaumcraftCompat {

    public static final Logger LOG = LogManager.getLogger("ThaumcraftCompat");

    // Радиус, в котором твой крафтер смотрит "склад" — если нужно в логах
    public static final int DEFAULT_RANGE = 64;

    private ThaumcraftCompat() {}

    // ---------- ЛОГИ ----------
    public static void logInfo(String fmt, Object... args) {
        LOG.info(format(fmt, args));
    }
    public static void logDebug(String fmt, Object... args) {
        LOG.debug(format(fmt, args));
    }
    private static String format(String fmt, Object... args) {
        try {
            return String.format(Locale.ROOT, fmt.replace("{}", "%s"), args);
        } catch (Throwable t) {
            return fmt + " " + Arrays.toString(args);
        }
    }

    // ---------- ПРЕДМЕТНЫЙ КЛЮЧ ----------
    public static final class ItemKey {
        public final Item item;
        public final int meta;
        public final NBTTagCompound nbt; // допускается null

        public ItemKey(Item item, int meta, NBTTagCompound nbt) {
            this.item = item;
            this.meta = meta;
            this.nbt = nbt == null ? null : nbt.copy();
        }

        public static ItemKey of(ItemStack stack) {
            return new ItemKey(stack.getItem(), stack.getItemDamage(), stack.hasTagCompound() ? stack.getTagCompound() : null);
        }

        public ItemStack toStack(int count) {
            ItemStack out = new ItemStack(item, Math.max(1, count), meta);
            if (nbt != null) out.setTagCompound(nbt.copy());
            return out;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ItemKey)) return false;
            ItemKey k = (ItemKey) o;
            if (item != k.item || meta != k.meta) return false;
            if (nbt == null && k.nbt == null) return true;
            return Objects.equals(nbt, k.nbt);
        }

        @Override
        public int hashCode() {
            return Objects.hash(item, meta, nbt == null ? 0 : nbt.hashCode());
        }

        @Override
        public String toString() {
            String id = Item.REGISTRY.getNameForObject(item).toString();
            return id + "@" + meta + (nbt != null ? "(nbt)" : "");
        }
    }

    // ---------- ШИНА КРАФТА (ЗАГЛУШКИ ДЛЯ ТВОЕЙ РЕАЛЬНОЙ ЛОГИКИ) ----------

    /**
     * Верни «что сейчас нужно дотащить» для крафтера на этой позиции.
     * Ключ — ItemKey, значение — требуемое количество.
     *
     * TODO: подставь свою реальную логику.
     */
    public static Map<ItemKey, Integer> pollCraftBus(World world, BlockPos crafterPos) {
        // ======= ПРИМЕР-ЗАГЛУШКА: ничего не нужно =======
        return Collections.emptyMap();

        /*
        // ======= Пример, если нужно 9 редстоуна: =======
        Item redstone = Item.getByNameOrId("minecraft:redstone");
        Map<ItemKey, Integer> out = new LinkedHashMap<>();
        out.put(new ItemKey(redstone, 0, null), 9);
        return out;
        */
    }

    /**
     * Посчитать остаток потребности после очередной доставки.
     * Верни 0 или меньше — если всё закрыто; >0 — сколько ещё нужно.
     *
     * TODO: подставь свою реальную логику.
     */
    public static int computeRemainingNeed(World world, BlockPos crafterPos) {
        // ======= ПРИМЕР-ЗАГЛУШКА: ничего не нужно =======
        return 0;

        /*
        // Если продолжаем тот же «редстоун» пример:
        return 7; // значит ещё нужно 7
        */
    }

    // ---------- УТИЛИТЫ ДЛЯ ЛОГОВ ----------

    public static String formatPos(BlockPos p) {
        if (p == null) return "[null]";
        return "[" + p.getX() + "," + p.getY() + "," + p.getZ() + "]";
    }

    public static String prettyNeed(ItemKey k, int amount) {
        String id = net.minecraft.item.Item.REGISTRY.getNameForObject(k.item).toString();
        return "{" + id + "@" + k.meta + " x" + amount + (k.nbt != null ? " nbt" : "") + "}";
    }

    public static void pushCraftNeeds(World world, BlockPos dropPos, Map<ItemStack, Integer> missing) {
        if (world == null || world.isRemote || dropPos == null || missing == null || missing.isEmpty()) return;

        for (Map.Entry<ItemStack, Integer> e : missing.entrySet()) {
            ItemStack key = e.getKey();
            if (key == null || key.isEmpty()) continue;

            int left = Math.max(0, e.getValue());
            if (left == 0) continue;

            // лог для отладки
            try {
                LOG.debug("[Compat] pushCraftNeeds dim={}{} -> {} x{}",
                        world.provider.getDimension(),
                        dropPos, key.getItem().getRegistryName(), left);
            } catch (Throwable ignored) {}

            // отправляем порциями по размеру стака
            while (left > 0) {
                int n = Math.min(left, key.getMaxStackSize());
                ItemStack req = key.copy();
                req.setCount(n);

                // сторона — любая валидная; бери ту, с которой «принимает» твой блок.
                // Если у блока есть собственный facing — подставь его вместо SOUTH.
                GolemHelper.requestProvisioning(world, dropPos, EnumFacing.SOUTH, req);

                left -= n;
            }
        }
    }
}

