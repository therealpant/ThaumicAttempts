package therealpant.thaumicattempts.golemnet.tile;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import therealpant.thaumicattempts.golemnet.cloud.CloudEndpointRef;
import therealpant.thaumicattempts.golemnet.cloud.CloudOrder;
import therealpant.thaumicattempts.golemnet.cloud.CloudOrderKind;
import therealpant.thaumicattempts.golemnet.cloud.MirrorLogisticsCloud;
import therealpant.thaumicattempts.util.ItemKey;

import javax.annotation.Nullable;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class CloudOrderSubmitHelper {

    private CloudOrderSubmitHelper() {}

    static int submitDelivery(World world, BlockPos managerPos, BlockPos requesterPos, int destSide, ItemKey key, int amount) {
        LinkedHashMap<ItemKey, Integer> accepted = submitBatchDelivery(
                world,
                managerPos,
                requesterPos,
                destSide,
                Collections.singletonList(new AbstractMap.SimpleEntry<>(key, amount))
        );
        return Math.max(0, accepted.getOrDefault(key, 0));
    }

    static int submitCraft(World world, BlockPos managerPos, BlockPos requesterPos, int destSide, ItemKey key, int amount) {
        LinkedHashMap<ItemKey, Integer> accepted = submitBatchCraft(
                world,
                managerPos,
                requesterPos,
                destSide,
                Collections.singletonList(new AbstractMap.SimpleEntry<>(key, amount))
        );
        return Math.max(0, accepted.getOrDefault(key, 0));
    }

    static LinkedHashMap<ItemKey, Integer> submitBatchDelivery(World world,
                                                               BlockPos managerPos,
                                                               BlockPos requesterPos,
                                                               int destSide,
                                                               List<Map.Entry<ItemKey, Integer>> requests) {
        LinkedHashMap<ItemKey, Integer> accepted = new LinkedHashMap<>();
        TileMirrorManager mgr = getManager(world, managerPos);
        if (mgr == null || requesterPos == null || requests == null || requests.isEmpty()) return accepted;

        MirrorLogisticsCloud cloud = mgr.getCloud();
        List<Map.Entry<ItemKey, Integer>> fallback = new ArrayList<>();
        long now = world.getTotalWorldTime();
        CloudEndpointRef destination = new CloudEndpointRef(requesterPos, destSide);

        for (Map.Entry<ItemKey, Integer> entry : requests) {
            if (entry == null || entry.getKey() == null || entry.getKey() == ItemKey.EMPTY) continue;
            int amount = Math.max(1, entry.getValue());
            ItemKey key = entry.getKey();

            if (submitCloudOrder(cloud, CloudOrderKind.DELIVERY, requesterPos, destination, key, amount, now)) {
                accepted.merge(key, amount, Integer::sum);
            } else {
                fallback.add(new AbstractMap.SimpleEntry<>(key, amount));
            }
        }

        if (!fallback.isEmpty()) {
            mgr.enqueueBatchDelivery(requesterPos, destSide, 0, fallback);
            for (Map.Entry<ItemKey, Integer> entry : fallback) {
                accepted.merge(entry.getKey(), Math.max(1, entry.getValue()), Integer::sum);
            }
        }

        return accepted;
    }

    static LinkedHashMap<ItemKey, Integer> submitBatchCraft(World world,
                                                            BlockPos managerPos,
                                                            BlockPos requesterPos,
                                                            int destSide,
                                                            List<Map.Entry<ItemKey, Integer>> requests) {
        LinkedHashMap<ItemKey, Integer> accepted = new LinkedHashMap<>();
        TileMirrorManager mgr = getManager(world, managerPos);
        if (mgr == null || requesterPos == null || requests == null || requests.isEmpty()) return accepted;

        MirrorLogisticsCloud cloud = mgr.getCloud();
        List<TerminalStyleCraftSubmitHelper.CraftOrder> fallbackOrders = new ArrayList<>();
        long now = world.getTotalWorldTime();
        CloudEndpointRef destination = new CloudEndpointRef(requesterPos, destSide);

        for (Map.Entry<ItemKey, Integer> entry : requests) {
            if (entry == null || entry.getKey() == null || entry.getKey() == ItemKey.EMPTY) continue;
            int amount = Math.max(1, entry.getValue());
            ItemKey key = entry.getKey();

            if (submitCloudOrder(cloud, CloudOrderKind.CRAFT, requesterPos, destination, key, amount, now)) {
                accepted.merge(key, amount, Integer::sum);
            } else {
                fallbackOrders.add(new TerminalStyleCraftSubmitHelper.CraftOrder(key.toStack(1), amount));
            }
        }

        if (!fallbackOrders.isEmpty()) {
            LinkedHashMap<ItemKey, Integer> fallbackAccepted = TerminalStyleCraftSubmitHelper.submitTerminalStyleCraftOrders(
                    world,
                    managerPos,
                    requesterPos,
                    destSide,
                    fallbackOrders,
                    false
            );
            for (Map.Entry<ItemKey, Integer> entry : fallbackAccepted.entrySet()) {
                accepted.merge(entry.getKey(), Math.max(1, entry.getValue()), Integer::sum);
            }
        }

        return accepted;
    }

    @Nullable
    private static TileMirrorManager getManager(@Nullable World world, @Nullable BlockPos managerPos) {
        if (world == null || world.isRemote || managerPos == null) return null;
        TileEntity te = world.getTileEntity(managerPos);
        if (!(te instanceof TileMirrorManager)) return null;
        return (TileMirrorManager) te;
    }

    private static boolean submitCloudOrder(@Nullable MirrorLogisticsCloud cloud,
                                            CloudOrderKind kind,
                                            BlockPos requesterPos,
                                            CloudEndpointRef destination,
                                            ItemKey key,
                                            int amount,
                                            long now) {
        if (cloud == null) return false;
        CloudOrder order = new CloudOrder(
                UUID.randomUUID(),
                kind,
                requesterPos,
                destination,
                key,
                amount,
                now
        );
        return cloud.submitOrder(order) != null;
    }
}