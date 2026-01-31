package therealpant.thaumicattempts.net.msg;

import io.netty.buffer.ByteBuf;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import therealpant.thaumicattempts.util.TAGemCountCache;

public class S2C_AmberCountUpdate implements IMessage {
    private UUID playerId;
    private int count;

    public S2C_AmberCountUpdate() {}

    public S2C_AmberCountUpdate(UUID playerId, int count) {
        this.playerId = playerId;
        this.count = count;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(playerId.getMostSignificantBits());
        buf.writeLong(playerId.getLeastSignificantBits());
        buf.writeInt(count);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        playerId = new UUID(buf.readLong(), buf.readLong());
        count = buf.readInt();
    }

    public static class Handler implements IMessageHandler<S2C_AmberCountUpdate, IMessage> {
        @Override
        public IMessage onMessage(S2C_AmberCountUpdate msg, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                TAGemCountCache.setClientAmberCount(msg.playerId, msg.count);
            });
            return null;
        }
    }
}
