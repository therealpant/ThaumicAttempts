package therealpant.thaumicattempts.net.msg;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import therealpant.thaumicattempts.world.tile.TileRiftonomicon;

public class C2S_RiftonomiconWispClick implements IMessage {
    private BlockPos pos;
    private int slot;
    private boolean extract;

    public C2S_RiftonomiconWispClick() {
    }

    public C2S_RiftonomiconWispClick(BlockPos pos, int slot, boolean extract) {
        this.pos = pos;
        this.slot = slot;
        this.extract = extract;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        buf.writeByte(slot);
        buf.writeBoolean(extract);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        slot = buf.readByte();
        extract = buf.readBoolean();
    }

    public static class Handler implements IMessageHandler<C2S_RiftonomiconWispClick, IMessage> {
        @Override
        public IMessage onMessage(C2S_RiftonomiconWispClick msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (msg.pos == null || player.getDistanceSqToCenter(msg.pos) > 100.0D) return;
                TileEntity tile = player.world.getTileEntity(msg.pos);
                if (tile instanceof TileRiftonomicon) {
                    TileRiftonomicon riftonomicon = (TileRiftonomicon) tile;
                    if (msg.extract && player.isSneaking() && player.getHeldItemMainhand().isEmpty()) {
                        ItemStack rune = riftonomicon.removeNavigationRune(msg.slot);
                        if (!rune.isEmpty()) {
                            player.setHeldItem(EnumHand.MAIN_HAND, rune);
                            player.inventoryContainer.detectAndSendChanges();
                        }
                    } else if (!msg.extract) {
                        riftonomicon.openNearestPortalForNavigationRune(msg.slot, player);
                    }
                }
            });
            return null;
        }
    }
}
