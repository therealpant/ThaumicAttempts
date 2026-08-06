package therealpant.thaumicattempts.net.msg;

import io.netty.buffer.ByteBuf;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.research.ScanningManager;
import therealpant.thaumicattempts.world.block.BlockAnomalyArchive;

public class C2S_AnomalyArchiveInteract implements IMessage {
    private static final double SERVER_REACH = 6.0D;
    private BlockPos pos;
    private boolean scan;

    public C2S_AnomalyArchiveInteract() {
    }

    public C2S_AnomalyArchiveInteract(BlockPos pos) {
        this(pos, false);
    }

    public C2S_AnomalyArchiveInteract(BlockPos pos, boolean scan) {
        this.pos = pos;
        this.scan = scan;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        buf.writeBoolean(scan);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        scan = buf.readBoolean();
    }

    public static class Handler
            implements IMessageHandler<C2S_AnomalyArchiveInteract, IMessage> {

        @Override
        public IMessage onMessage(C2S_AnomalyArchiveInteract message,
                                  MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(
                    () -> handle(player, message.pos, message.scan));
            return null;
        }

        private static void handle(EntityPlayerMP player, BlockPos pos, boolean scan) {
            if (pos == null || !player.world.isBlockLoaded(pos)) {
                return;
            }

            if (scan) {
                if (player.getHeldItem(EnumHand.MAIN_HAND).isEmpty()
                        || player.getHeldItem(EnumHand.MAIN_HAND).getItem()
                        != ItemsTC.thaumometer) return;
            } else if (!BlockAnomalyArchive.isPickaxe(
                    player.getHeldItem(EnumHand.MAIN_HAND))) return;

            IBlockState state = player.world.getBlockState(pos);
            if (!(state.getBlock() instanceof BlockAnomalyArchive)) return;

            Vec3d eye = player.getPositionEyes(1.0F);
            Vec3d end = eye.add(player.getLookVec().normalize().scale(SERVER_REACH));
            RayTraceResult archiveHit =
                    BlockAnomalyArchive.rayTraceInteraction(pos, eye, end);
            if (archiveHit == null) return;

            RayTraceResult obstruction = player.world.rayTraceBlocks(
                    eye, archiveHit.hitVec, false, true, false);
            if (obstruction != null
                    && obstruction.typeOfHit == RayTraceResult.Type.BLOCK
                    && !pos.equals(obstruction.getBlockPos())
                    && eye.squareDistanceTo(obstruction.hitVec) + 1.0E-5D
                    < eye.squareDistanceTo(archiveHit.hitVec)) {
                return;
            }

            if (scan) {
                ScanningManager.scanTheThing(player, pos);
            } else {
                ((BlockAnomalyArchive) state.getBlock()).harvestWithPickaxe(
                        player.world, pos, player, EnumHand.MAIN_HAND);
            }
        }
    }
}
