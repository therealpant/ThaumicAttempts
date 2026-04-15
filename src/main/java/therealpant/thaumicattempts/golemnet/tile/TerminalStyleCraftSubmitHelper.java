package therealpant.thaumicattempts.golemnet.tile;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import therealpant.thaumicattempts.api.AutomationOrderSubmitHelper;
import therealpant.thaumicattempts.api.CraftOrderApi;
import therealpant.thaumicattempts.api.ICraftEndpoint;
import therealpant.thaumicattempts.api.ITerminalOrderAcceptor;
import therealpant.thaumicattempts.api.TerminalOrderApi;
import therealpant.thaumicattempts.util.ItemKey;
import therealpant.thaumicattempts.util.ResourceIdentity;

import javax.annotation.Nullable;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class TerminalStyleCraftSubmitHelper {

    static final class CraftOrder {
        final ItemStack requestStack;
        final int count;

        CraftOrder(ItemStack requestStack, int count) {
            this.requestStack = requestStack == null ? ItemStack.EMPTY : requestStack.copy();
            this.count = Math.max(1, count);
        }
    }

    private TerminalStyleCraftSubmitHelper() {}

    static LinkedHashMap<ItemKey, Integer> submitTerminalStyleCraftOrders(@Nullable World world,
                                                                          @Nullable BlockPos managerPos,
                                                                          @Nullable BlockPos requesterPos,
                                                                          int destSide,
                                                                          List<CraftOrder> orders,
                                                                          boolean allowDirectTerminalFallback) {
        LinkedHashMap<ItemKey, Integer> acceptedByOutput = new LinkedHashMap<>();
        if (world == null || world.isRemote || managerPos == null || requesterPos == null || orders == null || orders.isEmpty()) {
            return acceptedByOutput;
        }

        TileEntity mte = world.getTileEntity(managerPos);
        if (!(mte instanceof TileMirrorManager)) return acceptedByOutput;
        TileMirrorManager mgr = (TileMirrorManager) mte;

        List<Map.Entry<ItemKey, Integer>> toManager = new ArrayList<>();

        for (CraftOrder order : orders) {
            if (order == null || order.requestStack.isEmpty() || order.count <= 0) continue;

            ItemStack one = order.requestStack.copy();
            one.setCount(1);

            if (TerminalOrderApi.isOrderIcon(one)) {

                ItemStack resultLike = TerminalOrderApi.stripOrderIconData(one);
                ItemKey outputKey = (resultLike == null || resultLike.isEmpty()) ? ItemKey.EMPTY : ItemKey.of(resultLike);

                // Для non-terminal клиентов (например, пьедестала) НЕ идём в direct automation path,
                // чтобы заказы проходили через manager queue/order memory.
                if (!allowDirectTerminalFallback) {
                    if (outputKey != ItemKey.EMPTY && hasCraftEndpointFor(world, mgr, outputKey.toStack(1))) {
                        toManager.add(new AbstractMap.SimpleEntry<>(outputKey, order.count));
                    }
                    continue;
                }

                int acceptedItems = AutomationOrderSubmitHelper.submitAutomationOrderByIcon(
                        world, one, order.count, managerPos, requesterPos, destSide
                );

                if (acceptedItems > 0 && outputKey != ItemKey.EMPTY) {
                    acceptedByOutput.merge(outputKey, acceptedItems, Integer::sum);
                    continue;
                }

                BlockPos target = TerminalOrderApi.getOrderIconPos(one);
                int slot = TerminalOrderApi.getOrderIconSlot(one);
                if (target == null || slot < 0) continue;

                TileEntity te = world.getTileEntity(target);
                if (te instanceof ITerminalOrderAcceptor) {
                    ((ITerminalOrderAcceptor) te).triggerFromTerminal(slot, order.count);
                }
                continue;
            }

            ItemKey key = ItemKey.of(one);
            if (key == ItemKey.EMPTY) continue;

            // Добавляем в manager-style только те позиции,
            // для которых реально найден craft endpoint.
            if (!hasCraftEndpointFor(world, mgr, key.toStack(1))) {
                continue;
            }

            toManager.add(new AbstractMap.SimpleEntry<>(key, order.count));
        }

        if (!toManager.isEmpty()) {
            mgr.enqueueBatchCraft(
                    requesterPos,
                    destSide,
                    1,
                    toManager,
                    key -> findCraftEndpointFor(world, mgr, key.toStack(1))
            );

            for (Map.Entry<ItemKey, Integer> e : toManager) {
                acceptedByOutput.merge(e.getKey(), Math.max(1, e.getValue()), Integer::sum);
            }
        }

        return acceptedByOutput;
    }

    private static boolean hasCraftEndpointFor(World world, TileMirrorManager mgr, ItemStack result) {
        return findCraftEndpointFor(world, mgr, result) != null;
    }

    @Nullable
    private static BlockPos findCraftEndpointFor(World world, TileMirrorManager mgr, ItemStack result) {
        if (result == null || result.isEmpty()) return null;
        Set<BlockPos> reqs = mgr.getRequestersSnapshot();
        if (reqs == null || reqs.isEmpty()) return null;

        for (BlockPos rp : reqs) {
            TileEntity te = world.getTileEntity(rp);
            if (!(te instanceof ICraftEndpoint)) continue;

            ICraftEndpoint ep = (ICraftEndpoint) te;
            if (!CraftOrderApi.isCrafter(ep)) continue;
            List<ItemStack> outs = ep.listCraftableResults();
            if (outs == null || outs.isEmpty()) continue;

            for (ItemStack out : outs) {
                if (out == null || out.isEmpty()) continue;
                if (ResourceIdentity.sameResource(out, result)) return rp;
            }
        }
        return null;
    }
}