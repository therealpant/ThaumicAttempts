package therealpant.thaumicattempts.golemnet.net.msg;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import therealpant.thaumicattempts.golemnet.tile.TileOrderTerminal;

public class C2S_OrderSubmit implements IMessage {
    private BlockPos pos;
    private boolean craftTab;

    public C2S_OrderSubmit() {}
    public C2S_OrderSubmit(BlockPos pos, boolean craftTab) {
        this.pos = pos; this.craftTab = craftTab;
    }

    @Override public void toBytes(io.netty.buffer.ByteBuf buf) {
        buf.writeLong(pos.toLong()); buf.writeBoolean(craftTab);
    }
    @Override public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong()); craftTab = buf.readBoolean();
    }

    public static class Handler implements IMessageHandler<C2S_OrderSubmit, IMessage> {
        @Override public IMessage onMessage(C2S_OrderSubmit msg, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().player;
            p.getServerWorld().addScheduledTask(() -> {
                World w = p.world;
                TileEntity te = w.getTileEntity(msg.pos);
                if (te instanceof TileOrderTerminal) {
                    ((TileOrderTerminal) te).submitDraft(msg.craftTab);
                }
            });
            return null;
        }
    }
}
