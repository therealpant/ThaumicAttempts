// src/main/java/therealpant/thaumicattempts/golemnet/net/msg/C2S_RequestCatalogPage.java
package therealpant.thaumicattempts.golemnet.net.msg;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import therealpant.thaumicattempts.golemnet.tile.TileOrderTerminal;

public class C2S_RequestCatalogPage implements IMessage {

    public BlockPos terminalPos;
    public long snapshotId;     // <0 => создать новый
    public int pageIndex0;      // 0-базовый индекс страницы
    public String search;       // фильтр
    public boolean craftTab;    // вкладка

    public C2S_RequestCatalogPage() {}

    public C2S_RequestCatalogPage(BlockPos pos, long snapshotId, int pageIndex0, String search, boolean craftTab) {
        this.terminalPos = pos;
        this.snapshotId  = snapshotId;
        this.pageIndex0  = pageIndex0;
        this.search      = (search == null ? "" : search);
        this.craftTab    = craftTab;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(terminalPos == null ? 0L : terminalPos.toLong());
        buf.writeLong(snapshotId);
        buf.writeInt(pageIndex0);
        ByteBufUtils.writeUTF8String(buf, search == null ? "" : search);
        buf.writeBoolean(craftTab);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        long packed = buf.readLong();
        this.terminalPos = BlockPos.fromLong(packed);
        this.snapshotId  = buf.readLong();
        this.pageIndex0  = buf.readInt();
        this.search      = ByteBufUtils.readUTF8String(buf);
        this.craftTab    = buf.readBoolean();
    }

    // ===== SERVER handler =====
    public static final class Handler implements IMessageHandler<C2S_RequestCatalogPage, IMessage> {
        @Override
        public IMessage onMessage(final C2S_RequestCatalogPage msg, final MessageContext ctx) {
            if (ctx.side != Side.SERVER) return null;
            final EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (player == null || player.world == null) return;
                if (msg.terminalPos == null) return;

                net.minecraft.tileentity.TileEntity te = player.world.getTileEntity(msg.terminalPos);
                if (te instanceof TileOrderTerminal) {
                    ((TileOrderTerminal) te).handleCatalogPageRequest(
                            player,
                            msg.craftTab,
                            msg.snapshotId,
                            msg.pageIndex0,
                            msg.search
                    );
                }
            });
            return null; // ответ — через S2C_* пакеты
        }
    }
}
