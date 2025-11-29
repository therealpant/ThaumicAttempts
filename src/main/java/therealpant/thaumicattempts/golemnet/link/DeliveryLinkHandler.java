package therealpant.thaumicattempts.golemnet.link;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import thaumcraft.api.items.ItemsTC;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.golemcraft.tile.TileDeliveryStation;

import java.util.*;

@Mod.EventBusSubscriber(modid = ThaumicAttempts.MODID)
public final class DeliveryLinkHandler {

    private static final class Session {
        final int dimension;
        final BlockPos origin;
        final List<BlockPos> storages = new ArrayList<>();
        BlockPos clickTarget = null;
        Session(int dim, BlockPos origin) {
            this.dimension = dim;
            this.origin = origin.toImmutable();
        }
    }

    private static final Map<UUID, Session> sessions = new HashMap<>();

    private DeliveryLinkHandler() { }

    public static void startLinking(EntityPlayer player, int dimension, BlockPos origin) {
        sessions.put(player.getUniqueID(), new Session(dimension, origin));
    }

    private static Session getSession(EntityPlayer player) {
        return sessions.get(player.getUniqueID());
    }

    private static void finish(EntityPlayer player) {
        sessions.remove(player.getUniqueID());
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        EntityPlayer player = event.getEntityPlayer();
        Session s = getSession(player);
        if (s == null) return;
        if (player.world.isRemote) return;
        if (player.getHeldItem(event.getHand()).getItem() != ItemsTC.golemBell) return;
        if (player.world.provider.getDimension() != s.dimension) return;

        BlockPos pos = event.getPos();
        if (pos.equals(s.origin)) return; // ignore clicks on self

        if (player.isSneaking()) {
            s.clickTarget = pos.toImmutable();
            player.sendStatusMessage(new TextComponentTranslation("thaumicattempts.link.click_target", pos.getX(), pos.getY(), pos.getZ()), true);
        } else {
            if (!s.storages.contains(pos)) {
                s.storages.add(pos.toImmutable());
                player.sendStatusMessage(new TextComponentTranslation("thaumicattempts.link.storage_added", s.storages.size(), pos.getX(), pos.getY(), pos.getZ()), true);
            }
        }
        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.util.EnumActionResult.SUCCESS);
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        EntityPlayer player = event.getEntityPlayer();
        Session s = getSession(player);
        if (s == null) return;
        if (!player.isSneaking()) return;
        if (player.world.isRemote) return;
        if (player.world.provider.getDimension() != s.dimension) return;
        if (player.getHeldItem(event.getHand()).getItem() != ItemsTC.golemBell) return;

        TileEntity te = player.world.getTileEntity(s.origin);
        if (te instanceof TileDeliveryStation) {
            ((TileDeliveryStation) te).applyLinks(s.storages, s.clickTarget);
            player.sendStatusMessage(new TextComponentTranslation("thaumicattempts.link.saved", s.storages.size()), true);
        } else {
            player.sendStatusMessage(new TextComponentTranslation("thaumicattempts.link.missing"), true);
        }
        finish(player);
        event.setCanceled(true);
    }
}