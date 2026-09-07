// src/main/java/therealpant/thaumicattempts/golemnet/net/msg/S2C_SnapshotCreated.java
package therealpant.thaumicattempts.golemnet.net.msg;

import net.minecraftforge.fml.common.network.simpleimpl.*;
import therealpant.thaumicattempts.ThaumicAttempts;

// package therealpant.thaumicattempts.golemnet.net.msg;

public class S2C_SnapshotCreated implements net.minecraftforge.fml.common.network.simpleimpl.IMessage {
    private boolean craftTab;
    private long snapshotId;

    public S2C_SnapshotCreated() {}
    public S2C_SnapshotCreated(boolean craftTab, long snapshotId) {
        this.craftTab = craftTab; this.snapshotId = snapshotId;
    }

    @Override public void toBytes(io.netty.buffer.ByteBuf buf) {
        buf.writeBoolean(craftTab);
        buf.writeLong(snapshotId);
    }
    @Override public void fromBytes(io.netty.buffer.ByteBuf buf) {
        craftTab = buf.readBoolean();
        snapshotId = buf.readLong();
    }

    public boolean isCraftTab() { return craftTab; }
    public long getSnapshotId() { return snapshotId; }

    public static class Handler implements net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler<S2C_SnapshotCreated, net.minecraftforge.fml.common.network.simpleimpl.IMessage> {
        @Override public IMessage onMessage(S2C_SnapshotCreated msg, MessageContext ctx) {
            ThaumicAttempts.proxy.handleClientPacket(msg);
            return null;
        }
    }
}

