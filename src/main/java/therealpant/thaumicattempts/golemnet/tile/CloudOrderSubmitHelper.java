package therealpant.thaumicattempts.golemnet.tile;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.golemnet.cloud.CloudEndpointRef;
import therealpant.thaumicattempts.golemnet.cloud.CloudOrder;
import therealpant.thaumicattempts.golemnet.cloud.CloudOrderKind;
import therealpant.thaumicattempts.golemnet.cloud.MirrorLogisticsCloud;
import therealpant.thaumicattempts.api.ICloudCraftConsumer;
import therealpant.thaumicattempts.util.ResourceIdentity;
import net.minecraft.item.ItemStack;
import therealpant.thaumicattempts.util.ItemKey;

import javax.annotation.Nullable;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CloudOrderSubmitHelper {

    private CloudOrderSubmitHelper() {}

    public static int submitDelivery(World world, BlockPos managerPos, BlockPos requesterPos, int destSide, ItemKey key, int amount) {
        LinkedHashMap<ItemKey, Integer> accepted = submitBatchDelivery(
                world,
                managerPos,
                requesterPos,
                destSide,
                Collections.singletonList(new AbstractMap.SimpleEntry<>(key, amount))
        );
        return Math.max(0, accepted.getOrDefault(key, 0));
    }

    public static int submitCraft(World world, BlockPos managerPos, BlockPos requesterPos, int destSide, ItemKey key, int amount) {
        LinkedHashMap<ItemKey, Integer> accepted = submitBatchCraft(
                world,
                managerPos,
                requesterPos,
                destSide,
                Collections.singletonList(new AbstractMap.SimpleEntry<>(key, amount))
        );
        return Math.max(0, accepted.getOrDefault(key, 0));
    }

    public static LinkedHashMap<ItemKey, Integer> submitBatchDelivery(World world,
                                                               BlockPos managerPos,
                                                               BlockPos requesterPos,
                                                               int destSide,
                                                               List<Map.Entry<ItemKey, Integer>> requests) {
        LinkedHashMap<ItemKey, Integer> accepted = new LinkedHashMap<>();
        TileMirrorManager mgr = getManager(world, managerPos);
        if (mgr == null || requesterPos == null || requests == null || requests.isEmpty()) return accepted;

        MirrorLogisticsCloud cloud = mgr.getCloud();
        long now = world.getTotalWorldTime();
        CloudEndpointRef destination = new CloudEndpointRef(requesterPos, destSide);

        for (Map.Entry<ItemKey, Integer> entry : requests) {
            if (entry == null || entry.getKey() == null || entry.getKey() == ItemKey.EMPTY) continue;
            int amount = Math.max(1, entry.getValue());
            ItemKey key = entry.getKey();

            if (submitCloudOrder(cloud, CloudOrderKind.DELIVERY, requesterPos, destination, key, amount, now)) {
                accepted.merge(key, amount, Integer::sum);
            }
        }

        return accepted;
    }

    public static LinkedHashMap<ItemKey, Integer> submitBatchCraft(World world,
                                                            BlockPos managerPos,
                                                            BlockPos requesterPos,
                                                            int destSide,
                                                            List<Map.Entry<ItemKey, Integer>> requests) {
        LinkedHashMap<ItemKey, Integer> accepted = new LinkedHashMap<>();
        TileMirrorManager mgr = getManager(world, managerPos);
        if (mgr == null || requesterPos == null || requests == null || requests.isEmpty()) return accepted;

        MirrorLogisticsCloud cloud = mgr.getCloud();
        long now = world.getTotalWorldTime();
        CloudEndpointRef destination = new CloudEndpointRef(requesterPos, destSide);

        for (Map.Entry<ItemKey, Integer> entry : requests) {
            if (entry == null || entry.getKey() == null || entry.getKey() == ItemKey.EMPTY) continue;
            int amount = Math.max(1, entry.getValue());
            ItemKey key = entry.getKey();

            if (canUseCloudCraft(mgr, key)
                    && submitCloudOrder(cloud, CloudOrderKind.CRAFT, requesterPos, destination, key, amount, now)) {
                accepted.merge(key, amount, Integer::sum);
            }
        }

        return accepted;
    }

    private static boolean canUseCloudCraft(TileMirrorManager mgr, ItemKey key) {
        if (mgr == null || mgr.getWorld() == null || key == null || key == ItemKey.EMPTY) return false;
        ItemStack like = key.toStack(1);
        if (like.isEmpty()) return false;

        for (BlockPos pos : mgr.getRequestersSnapshot()) {
            TileEntity te = mgr.getWorld().getTileEntity(pos);
            if (!(te instanceof ICloudCraftConsumer)) continue;
            List<ItemStack> craftable = ((ICloudCraftConsumer) te).listCraftableResults();
            if (craftable == null) continue;
            for (ItemStack out : craftable) {
                if (out != null && !out.isEmpty() && ResourceIdentity.sameResource(out, like)) {
                    return true;
                }
            }
        }
        return false;
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
        UUID submitted = cloud.submitOrder(order);
        if (submitted != null) {
            ThaumicAttempts.LOGGER.info("[Cloud] submit CloudOrder id=%s kind=%s item=%s amount=%d source=%s destination=%s",
                    submitted, kind, key, amount, requesterPos, destination);
            return true;
        }
        ThaumicAttempts.LOGGER.warn("[Cloud] submit CloudOrder failed kind=%s item=%s amount=%d source=%s destination=%s",
                kind, key, amount, requesterPos, destination);
        return false;
    }
}