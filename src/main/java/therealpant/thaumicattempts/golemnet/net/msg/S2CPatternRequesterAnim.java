package therealpant.thaumicattempts.golemnet.net.msg;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.golemnet.tile.TilePatternRequester;

public class S2CPatternRequesterAnim implements IMessage {
    public static final int MODE_MIRROR_TO_BLOCK = 0;
    public static final int MODE_BLOCK_TO_MIRROR = 1;
    public static final int MODE_BOUNCE = 2;

    public BlockPos requesterPos;
    public ItemStack stack = ItemStack.EMPTY;
    public int mode;
    public int duration;
    public int delay;
    public long seed;

    public S2CPatternRequesterAnim() {}

    public S2CPatternRequesterAnim(BlockPos pos, ItemStack stack, int mode, int duration, long seed, int delay) {
        this.requesterPos = pos;
        this.stack = stack == null ? ItemStack.EMPTY : stack.copy();
        if (!this.stack.isEmpty()) this.stack.setCount(1);
        this.mode = mode;
        this.duration = Math.max(5, duration);
        this.seed = seed;
        this.delay = Math.max(0, delay);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(requesterPos.toLong());
        ByteBufUtils.writeItemStack(buf, stack);
        buf.writeInt(mode);
        buf.writeInt(duration);
        buf.writeInt(delay);
        buf.writeLong(seed);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        requesterPos = BlockPos.fromLong(buf.readLong());
        stack = ByteBufUtils.readItemStack(buf);
        mode = buf.readInt();
        duration = buf.readInt();
        delay = buf.readInt();
        seed = buf.readLong();
    }

    public static void dispatch(World world, BlockPos pos, ItemStack stack, int mode, int duration, long seed, int delay) {
        if (world == null || world.isRemote || pos == null || stack == null || stack.isEmpty()) return;

        S2CPatternRequesterAnim msg = new S2CPatternRequesterAnim(pos, stack, mode, duration, seed, delay);
        NetworkRegistry.TargetPoint tp = new NetworkRegistry.TargetPoint(
                world.provider.getDimension(),
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5,
                32.0
        );
        ThaumicAttempts.NET.sendToAllAround(msg, tp);
    }

    public static class Handler implements IMessageHandler<S2CPatternRequesterAnim, IMessage> {
        @Override
        public IMessage onMessage(S2CPatternRequesterAnim msg, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                TileEntity te = Minecraft.getMinecraft().world.getTileEntity(msg.requesterPos);
                if (te instanceof TilePatternRequester) {
                    ((TilePatternRequester) te).clientAddFlying(msg.stack, msg.mode, msg.duration, msg.seed, msg.delay);
                }
            });
            return null;
        }
    }
}
