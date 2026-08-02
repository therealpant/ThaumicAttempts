package therealpant.thaumicattempts.net.msg;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import therealpant.thaumicattempts.ThaumicAttempts;

public class S2C_MonolithParadoxumStream implements IMessage {
    private BlockPos source;
    private BlockPos target;

    public S2C_MonolithParadoxumStream() {}

    public S2C_MonolithParadoxumStream(BlockPos source, BlockPos target) {
        this.source = source;
        this.target = target;
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeLong(source.toLong());
        buffer.writeLong(target.toLong());
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        source = BlockPos.fromLong(buffer.readLong());
        target = BlockPos.fromLong(buffer.readLong());
    }

    public static void dispatch(World world, BlockPos source, BlockPos target) {
        if (world == null || world.isRemote || source == null || target == null) return;
        NetworkRegistry.TargetPoint point = new NetworkRegistry.TargetPoint(
                world.provider.getDimension(),
                source.getX() + 0.5D,
                source.getY() + 0.5D,
                source.getZ() + 0.5D,
                64.0D);
        ThaumicAttempts.NET.sendToAllAround(
                new S2C_MonolithParadoxumStream(source, target), point);
    }

    public static class Handler
            implements IMessageHandler<S2C_MonolithParadoxumStream, IMessage> {
        @Override
        public IMessage onMessage(S2C_MonolithParadoxumStream message,
                                  MessageContext context) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                if (Minecraft.getMinecraft().world != null) {
                    ThaumicAttempts.proxy.spawnMonolithParadoxumStream(
                            Minecraft.getMinecraft().world,
                            message.source,
                            message.target);
                }
            });
            return null;
        }
    }
}
