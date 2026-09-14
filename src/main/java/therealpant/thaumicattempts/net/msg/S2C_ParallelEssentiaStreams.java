package therealpant.thaumicattempts.net.msg;

import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import therealpant.thaumicattempts.ThaumicAttempts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class S2C_ParallelEssentiaStreams implements IMessage {
    private BlockPos matrix;
    private List<Stream> streams = Collections.emptyList();

    public S2C_ParallelEssentiaStreams() {}

    public S2C_ParallelEssentiaStreams(BlockPos matrix, List<Stream> streams) {
        this.matrix = matrix;
        this.streams = new ArrayList<>(streams);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeLong(matrix.toLong());
        buffer.writeByte(Math.min(255, streams.size()));
        for (int i = 0; i < streams.size() && i < 255; i++) {
            Stream stream = streams.get(i);
            buffer.writeLong(stream.source.toLong());
            buffer.writeInt(stream.color);
            buffer.writeShort(stream.length);
        }
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        matrix = BlockPos.fromLong(buffer.readLong());
        int count = buffer.readUnsignedByte();
        streams = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            streams.add(new Stream(
                    BlockPos.fromLong(buffer.readLong()),
                    buffer.readInt(),
                    buffer.readUnsignedShort()));
        }
    }

    public BlockPos getMatrix() {
        return matrix;
    }

    public List<Stream> getStreams() {
        return streams;
    }

    public static void dispatch(World world, BlockPos matrix, List<Stream> streams) {
        if (world == null || world.isRemote || matrix == null || streams == null || streams.isEmpty()) return;
        ThaumicAttempts.NET.sendToAllAround(
                new S2C_ParallelEssentiaStreams(matrix, streams),
                new NetworkRegistry.TargetPoint(
                        world.provider.getDimension(),
                        matrix.getX() + 0.5D,
                        matrix.getY() + 0.5D,
                        matrix.getZ() + 0.5D,
                        32.0D));
    }

    public static final class Stream {
        private final BlockPos source;
        private final int color;
        private final int length;

        public Stream(BlockPos source, int color, int length) {
            this.source = source.toImmutable();
            this.color = color;
            this.length = Math.max(0, length);
        }

        public BlockPos getSource() {
            return source;
        }

        public int getColor() {
            return color;
        }

        public int getLength() {
            return length;
        }
    }

    public static class Handler implements IMessageHandler<S2C_ParallelEssentiaStreams, IMessage> {
        @Override
        public IMessage onMessage(S2C_ParallelEssentiaStreams message, MessageContext context) {
            ThaumicAttempts.proxy.handleClientPacket(message);
            return null;
        }
    }
}
