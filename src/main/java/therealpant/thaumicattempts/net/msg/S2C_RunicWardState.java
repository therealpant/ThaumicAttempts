package therealpant.thaumicattempts.net.msg;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import therealpant.thaumicattempts.ThaumicAttempts;

public final class S2C_RunicWardState implements IMessage {
    private boolean active;
    private float current;
    private int max;
    private float fortification;

    public S2C_RunicWardState() {}

    public S2C_RunicWardState(boolean active, float current, int max, float fortification) {
        this.active = active;
        this.current = current;
        this.max = max;
        this.fortification = fortification;
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeBoolean(active);
        buffer.writeFloat(current);
        buffer.writeInt(max);
        buffer.writeFloat(fortification);
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        active = buffer.readBoolean();
        current = buffer.readFloat();
        max = buffer.readInt();
        fortification = buffer.readFloat();
    }

    public boolean isActive() { return active; }
    public float getCurrent() { return current; }
    public int getMax() { return max; }
    public float getFortification() { return fortification; }

    public static final class Handler implements IMessageHandler<S2C_RunicWardState, IMessage> {
        @Override
        public IMessage onMessage(S2C_RunicWardState message, MessageContext context) {
            ThaumicAttempts.proxy.handleClientPacket(message);
            return null;
        }
    }
}
