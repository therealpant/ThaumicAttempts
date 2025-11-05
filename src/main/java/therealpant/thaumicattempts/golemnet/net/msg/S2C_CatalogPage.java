// src/main/java/therealpant/thaumicattempts/golemnet/net/msg/S2C_CatalogPage.java
package therealpant.thaumicattempts.golemnet.net.msg;

import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraft.client.Minecraft;
import therealpant.thaumicattempts.client.ClientCatalogCache;

import java.util.ArrayList;
import java.util.List;

public class S2C_CatalogPage implements IMessage {
    private boolean craftTab;
    private long snapshotId;
    private int pageNo1;
    private int totalPages;     // 0 если неизвестно
    private boolean hasMore;
    private List<ItemStack> items35;
    private List<Integer> counts;
    private List<Integer> makeCounts;
    private List<Boolean> makePossible;

    public S2C_CatalogPage() {}

    public S2C_CatalogPage(boolean craftTab, long snapshotId, int pageNo1, int totalPages, boolean hasMore,
                           List<ItemStack> items35, List<Integer> counts, List<Integer> makeCounts, List<Boolean> makePossible) {
        this.craftTab = craftTab;
        this.snapshotId = snapshotId;
        this.pageNo1 = pageNo1;
        this.totalPages = totalPages;
        this.hasMore = hasMore;
        this.items35 = items35;
        this.counts = counts;
        this.makeCounts = makeCounts;
        this.makePossible = makePossible;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(craftTab);
        buf.writeLong(snapshotId);
        buf.writeInt(pageNo1);
        buf.writeInt(totalPages);
        buf.writeBoolean(hasMore);

        // ItemStack[] → NBTTagList of compounds
        NBTTagList list = new NBTTagList();
        if (items35 != null) {
            for (ItemStack s : items35) {
                NBTTagCompound t = new NBTTagCompound();
                if (s != null && !s.isEmpty()) s.writeToNBT(t);
                list.appendTag(t);
            }
        }
        NBTTagCompound itemsTag = new NBTTagCompound();
        itemsTag.setTag("items", list);
        ByteBufUtils.writeTag(buf, itemsTag);

        writeIntList(buf, counts);
        writeIntList(buf, makeCounts);
        writeBoolList(buf, makePossible);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        craftTab   = buf.readBoolean();
        snapshotId = buf.readLong();
        pageNo1    = buf.readInt();
        totalPages = buf.readInt();
        hasMore    = buf.readBoolean();

        NBTTagCompound itemsTag = ByteBufUtils.readTag(buf);
        items35 = new ArrayList<>(35);
        if (itemsTag != null && itemsTag.hasKey("items", 9)) {
            NBTTagList list = itemsTag.getTagList("items", 10);
            int n = Math.min(35, list.tagCount());
            for (int i = 0; i < n; i++) {
                NBTTagCompound t = list.getCompoundTagAt(i);
                ItemStack s = ItemStack.EMPTY;
                if (!t.isEmpty()) s = new ItemStack(t);
                items35.add(s);
            }
        }
        counts       = readIntList(buf);
        makeCounts   = readIntList(buf);
        makePossible = readBoolList(buf);
    }

    private static void writeIntList(ByteBuf buf, List<Integer> list) {
        int n = (list == null) ? 0 : list.size();
        buf.writeInt(n);
        for (int i = 0; i < n; i++) buf.writeInt(list.get(i) == null ? 0 : list.get(i));
    }
    private static List<Integer> readIntList(ByteBuf buf) {
        int n = buf.readInt();
        List<Integer> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(buf.readInt());
        return out;
    }
    private static void writeBoolList(ByteBuf buf, List<Boolean> list) {
        int n = (list == null) ? 0 : list.size();
        buf.writeInt(n);
        for (int i = 0; i < n; i++) buf.writeBoolean(Boolean.TRUE.equals(list.get(i)));
    }
    private static List<Boolean> readBoolList(ByteBuf buf) {
        int n = buf.readInt();
        List<Boolean> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(buf.readBoolean());
        return out;
    }

    public static class Handler implements IMessageHandler<S2C_CatalogPage, IMessage> {
        // S2C_CatalogPage.Handler
        @Override
        public IMessage onMessage(S2C_CatalogPage msg, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                int nonEmpty = 0;
                if (msg.items35 != null) {
                    for (int i = 0; i < Math.min(35, msg.items35.size()); i++) {
                        if (msg.items35.get(i) != null && !msg.items35.get(i).isEmpty()) nonEmpty++;
                    }
                }
                // Порядок аргументов: craft, sid, pageNo1, totalPages, hasMore,
                // items, counts, makeCounts, makePossible, itemCountOnThisPage, partial=false
                ClientCatalogCache.applyPagePacket(
                        msg.craftTab,
                        msg.snapshotId,
                        Math.max(1, msg.pageNo1),
                        Math.max(0, msg.totalPages),
                        msg.hasMore,
                        msg.items35, msg.counts, msg.makeCounts, msg.makePossible,
                        nonEmpty,
                        false
                );
            });
            return null;
        }
    }
}
