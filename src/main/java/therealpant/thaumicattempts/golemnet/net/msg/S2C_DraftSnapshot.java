package therealpant.thaumicattempts.golemnet.net.msg;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import therealpant.thaumicattempts.client.ClientCatalogCache;

import java.util.ArrayList;
import java.util.List;

/** Сервер → клиент: 3×3 слоты (черновик и pending) как пары (иконки, количества). */
public class S2C_DraftSnapshot implements IMessage {
    private boolean craftTab;

    private List<ItemStack> draftStacks = new ArrayList<>(9);
    private List<Integer>   draftCounts = new ArrayList<>(9);

    private List<ItemStack> pendingStacks = new ArrayList<>(9);
    private List<Integer>   pendingCounts = new ArrayList<>(9);

    public S2C_DraftSnapshot() {}

    public S2C_DraftSnapshot(boolean craftTab,
                             List<ItemStack> draftStacks,  List<Integer> draftCounts,
                             List<ItemStack> pendingStacks,List<Integer> pendingCounts) {
        this.craftTab = craftTab;

        this.draftStacks   = (draftStacks == null)   ? new ArrayList<>() : new ArrayList<>(draftStacks);
        this.draftCounts   = (draftCounts == null)   ? new ArrayList<>() : new ArrayList<>(draftCounts);
        this.pendingStacks = (pendingStacks == null) ? new ArrayList<>() : new ArrayList<>(pendingStacks);
        this.pendingCounts = (pendingCounts == null) ? new ArrayList<>() : new ArrayList<>(pendingCounts);
    }

    @Override public void toBytes(ByteBuf buf) {
        buf.writeBoolean(craftTab);

        buf.writeInt(draftStacks.size());
        for (ItemStack s : draftStacks) ByteBufUtils.writeItemStack(buf, s == null ? ItemStack.EMPTY : s);
        buf.writeInt(draftCounts.size());
        for (Integer c : draftCounts)    buf.writeInt(c == null ? 0 : c);

        buf.writeInt(pendingStacks.size());
        for (ItemStack s : pendingStacks) ByteBufUtils.writeItemStack(buf, s == null ? ItemStack.EMPTY : s);
        buf.writeInt(pendingCounts.size());
        for (Integer c : pendingCounts)    buf.writeInt(c == null ? 0 : c);
    }

    @Override public void fromBytes(ByteBuf buf) {
        craftTab = buf.readBoolean();

        int n = buf.readInt();
        draftStacks = new ArrayList<>(n);
        for (int i=0;i<n;i++) draftStacks.add(ByteBufUtils.readItemStack(buf));
        int m = buf.readInt();
        draftCounts = new ArrayList<>(m);
        for (int i=0;i<m;i++) draftCounts.add(buf.readInt());

        int n2 = buf.readInt();
        pendingStacks = new ArrayList<>(n2);
        for (int i=0;i<n2;i++) pendingStacks.add(ByteBufUtils.readItemStack(buf));
        int m2 = buf.readInt();
        pendingCounts = new ArrayList<>(m2);
        for (int i=0;i<m2;i++) pendingCounts.add(buf.readInt());
    }

    public static class Handler implements IMessageHandler<S2C_DraftSnapshot, IMessage> {
        @Override public IMessage onMessage(S2C_DraftSnapshot msg, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                ClientCatalogCache.setDraft(msg.craftTab,   msg.draftStacks,   msg.draftCounts);
                ClientCatalogCache.setPending(msg.craftTab, msg.pendingStacks, msg.pendingCounts);
            });
            return null;
        }
    }
}
