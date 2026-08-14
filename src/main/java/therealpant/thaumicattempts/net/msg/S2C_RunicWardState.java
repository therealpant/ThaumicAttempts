package therealpant.thaumicattempts.net.msg;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import therealpant.thaumicattempts.client.RunicWardClientState;

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

    public static final class Handler implements IMessageHandler<S2C_RunicWardState, IMessage> {
        @Override
        public IMessage onMessage(S2C_RunicWardState message, MessageContext context) {
            Minecraft.getMinecraft().addScheduledTask(() -> RunicWardClientState.update(
                    message.active, message.current, message.max, message.fortification));
            return null;
        }
    }
}
