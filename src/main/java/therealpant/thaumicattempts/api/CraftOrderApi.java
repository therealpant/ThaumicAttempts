package therealpant.thaumicattempts.api;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Set;

/**
 * Общий API для цепочек заказа крафта: постановка, ожидание завершения и
 * доставка результата обратно заказчику (OrderTerminal или другой адресат).
 * <p>
 * Его используют менеджер зеркал и реквестеры, чтобы говорить на одном языке
 * о том, что является «крафтером»: в такой блок нужно завести ресурсы, а потом
 * забрать готовый результат.
 */
public final class CraftOrderApi {
    private CraftOrderApi() {}

    /** Метка «обычного крафтера», ресурсы → результат. */
    public static final String TAG_CRAFTER = "crafter";

    /**
     * Контракт для точек крафта, которые хотят объявить собственные теги.
     * Используется MirrorManager и OrderTerminal для фильтрации подходящих
     * исполнителей заказа.
     */
    public interface TagProvider {
        /** Набор тегов, описывающих возможности исполнителя. */
        Set<String> getCraftOrderTags();
    }

    /** Проверяет, что endpoint объявляет указанный тег. */
    public static boolean hasTag(ICraftEndpoint endpoint, String tag) {
        if (endpoint instanceof TagProvider) {
            Set<String> tags = ((TagProvider) endpoint).getCraftOrderTags();
            return tags != null && tags.contains(tag);
        }
        return false;
    }

    /** Удобный помощник для одиночного тега crafter. */
    public static boolean isCrafter(ICraftEndpoint endpoint) {
        return hasTag(endpoint, TAG_CRAFTER);
    }

    /**
     * Пытается найти item-обработчик у адресата (терминал/крафтер/любой TE) с
     * учётом предпочитаемой стороны.
     */
    @Nullable
    public static IItemHandler findDestinationHandler(World world, BlockPos dest, int destSide) {
        if (world == null || dest == null) return null;
        TileEntity te = world.getTileEntity(dest);
        if (te == null) return null;

        EnumFacing side = (destSide >= 0 && destSide < 6) ? EnumFacing.byIndex(destSide) : null;

        IItemHandler handler = te.getCapability(net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side);
        if (handler != null) return handler;

        handler = te.getCapability(net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (handler != null) return handler;

        handler = te.getCapability(net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.UP);
        if (handler != null) return handler;

        for (EnumFacing f : EnumFacing.values()) {
            handler = te.getCapability(net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, f);
            if (handler != null) return handler;
        }
        return null;
    }

    /**
     * Аккуратно кладёт предмет в адресата. Возвращает, сколько было принято.
     */
    public static int insertIntoDestination(World world, BlockPos dest, int destSide, ItemStack stack) {
        if (world == null || dest == null || stack == null || stack.isEmpty()) return 0;
        IItemHandler handler = findDestinationHandler(world, dest, destSide);
        if (handler == null) return 0;

        ItemStack remaining = stack.copy();
        int moved = 0;
        for (int i = 0; i < handler.getSlots() && !remaining.isEmpty(); i++) {
            ItemStack rest = handler.insertItem(i, remaining, false);
            moved += remaining.getCount() - (rest.isEmpty() ? 0 : rest.getCount());
            remaining = (rest == null) ? ItemStack.EMPTY : rest;
        }
        return moved;
    }

    /** Утилита для простых реализаций TagProvider. */
    public static Set<String> singletonTag(String tag) {
        return Collections.singleton(tag);
    }
}