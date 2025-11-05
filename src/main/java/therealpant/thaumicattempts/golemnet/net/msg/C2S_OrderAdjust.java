package therealpant.thaumicattempts.golemnet.net.msg;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import therealpant.thaumicattempts.golemnet.tile.TileOrderTerminal;
import therealpant.thaumicattempts.util.CraftYieldHelper;
import therealpant.thaumicattempts.util.ItemKey;

import java.util.Map;

public class C2S_OrderAdjust implements IMessage {
    private BlockPos pos;
    private ItemStack key;
    private int delta;
    private boolean craftTab;
    private ItemStack keyOne;

    public C2S_OrderAdjust() {}
    public C2S_OrderAdjust(BlockPos pos, ItemStack key, int delta, boolean craftTab) {
        this.pos = pos;
        this.key = key.copy(); this.key.setCount(1);
        this.delta = delta;
        this.craftTab = craftTab;
    }

    @Override public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        ByteBufUtils.writeItemStack(buf, key);
        buf.writeInt(delta);
        buf.writeBoolean(craftTab);
    }
    @Override public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        key = ByteBufUtils.readItemStack(buf);
        delta = buf.readInt();
        craftTab = buf.readBoolean();
    }

    public static class Handler implements IMessageHandler<C2S_OrderAdjust, IMessage> {
        @Override
        public IMessage onMessage(C2S_OrderAdjust msg, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                net.minecraft.world.World w = player.world;
                TileEntity te = w.getTileEntity(msg.pos);
                if (!(te instanceof TileOrderTerminal)) return;

                TileOrderTerminal term = (TileOrderTerminal) te;

                // БЫЛО: term.adjustDraft(msg.key, msg.delta, msg.craftTab);
                int delta = msg.delta;

                // В крафтовой вкладке «+1» означает «1 крафт».
                // Преобразуем в количество РЕЗУЛЬТАТА (умножаем на выход рецепта).
                if (msg.craftTab && delta != 0) {
                    int yield = CraftYieldHelper.getCraftYield(w, msg.key);
                    if (yield < 1) yield = 1;
                    // знак delta сохраняем (ЛКМ/ПКМ), домножаем модуль.
                    if (delta > 0) delta = delta * yield;
                    else delta = -(Math.abs(delta) * yield);
                }

                ItemStack key = msg.key;
                if (!msg.craftTab && key != null && !key.isEmpty()
                        && key.getItem() == thaumcraft.api.items.ItemsTC.crystalEssence) {
                    thaumcraft.api.aspects.AspectList al =
                            ((thaumcraft.common.items.ItemTCEssentiaContainer) thaumcraft.api.items.ItemsTC.crystalEssence).getAspects(key);
                    if (al != null && al.size() == 1) {
                        thaumcraft.api.aspects.Aspect a = al.getAspects()[0];
                        key = thaumcraft.api.ThaumcraftApiHelper.makeCrystal(a, 1);
                    }
                }

                term.adjustDraft(key, delta, msg.craftTab);

                // --- Снимок ЧЕРНОВИКА/ПЭНДИНГА
                Map<ItemKey, Integer> draftMap = term.getDraftSnapshot(msg.craftTab);

                java.util.List<ItemStack> draft9 = new java.util.ArrayList<>(9);
                java.util.List<Integer> draftCnt = new java.util.ArrayList<>(9);

                for (Map.Entry<ItemKey, Integer> e : draftMap.entrySet()) {
                    draft9.add(e.getKey().toStack(1));
                    draftCnt.add(Math.max(1, e.getValue()));
                    if (draft9.size() == 9) break;
                }
                while (draft9.size() < 9) {
                    draft9.add(ItemStack.EMPTY);
                    draftCnt.add(0);
                }

                java.util.List<ItemStack> pending9 = term.getPendingSnapshot(msg.craftTab);
                java.util.List<Integer> pendingCnt = new java.util.ArrayList<>(9);
                for (int i = 0; i < 9; i++) {
                    ItemStack s = (pending9 != null && i < pending9.size()) ? pending9.get(i) : ItemStack.EMPTY;
                    pendingCnt.add((s != null && !s.isEmpty()) ? s.getCount() : 0);
                }

                therealpant.thaumicattempts.ThaumicAttempts.NET.sendTo(
                        new therealpant.thaumicattempts.golemnet.net.msg.S2C_DraftSnapshot(
                                msg.craftTab, draft9, draftCnt, pending9, pendingCnt),
                        player
                );
            });
            return null;
        }
    }
}


