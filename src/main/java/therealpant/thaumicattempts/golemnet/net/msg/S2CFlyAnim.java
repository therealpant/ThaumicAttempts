// src/main/java/therealpant/thaumicattempts/golemnet/net/S2CFlyAnim.java
package therealpant.thaumicattempts.golemnet.net.msg;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.*;

public class S2CFlyAnim implements IMessage {
    public BlockPos managerPos;
    public ItemStack stack = ItemStack.EMPTY;
    public int ring, slot, duration;
    public long seed;

    public S2CFlyAnim() {}
    public S2CFlyAnim(BlockPos p, ItemStack s, int r, int sl, int dur, long seed) {
        this.managerPos = p;
        this.stack = s.copy(); this.stack.setCount(1);
        this.ring = r; this.slot = sl; this.duration = dur; this.seed = seed;
    }

    @Override public void toBytes(ByteBuf buf) {
        buf.writeLong(managerPos.toLong());
        ByteBufUtils.writeItemStack(buf, stack);
        buf.writeInt(ring); buf.writeInt(slot);
        buf.writeInt(duration);
        buf.writeLong(seed);
    }
    @Override public void fromBytes(ByteBuf buf) {
        managerPos = BlockPos.fromLong(buf.readLong());
        stack = ByteBufUtils.readItemStack(buf);
        ring = buf.readInt(); slot = buf.readInt();
        duration = buf.readInt();
        seed = buf.readLong();
    }

    public static class Handler implements IMessageHandler<S2CFlyAnim, IMessage> {
        @Override public IMessage onMessage(S2CFlyAnim msg, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                TileEntity te = Minecraft.getMinecraft().world.getTileEntity(msg.managerPos);
                if (te instanceof therealpant.thaumicattempts.golemnet.tile.TileMirrorManager) {
                    ((therealpant.thaumicattempts.golemnet.tile.TileMirrorManager) te)
                            .clientAddFlying(msg.stack, msg.ring, msg.slot, msg.duration, msg.seed);
                }
            });
            return null;
        }
    }
}
