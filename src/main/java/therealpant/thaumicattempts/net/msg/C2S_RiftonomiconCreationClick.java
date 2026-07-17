package therealpant.thaumicattempts.net.msg;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import thaumcraft.api.casters.ICaster;
import therealpant.thaumicattempts.world.tile.TileRiftonomicon;

public class C2S_RiftonomiconCreationClick implements IMessage {
    private BlockPos pos;
    private int slot;

    public C2S_RiftonomiconCreationClick() {}

    public C2S_RiftonomiconCreationClick(BlockPos pos, int slot) {
        this.pos = pos;
        this.slot = slot;
    }

    @Override public void toBytes(ByteBuf buf) { buf.writeLong(pos.toLong()); buf.writeByte(slot); }
    @Override public void fromBytes(ByteBuf buf) { pos = BlockPos.fromLong(buf.readLong()); slot = buf.readByte(); }

    public static class Handler implements IMessageHandler<C2S_RiftonomiconCreationClick, IMessage> {
        @Override
        public IMessage onMessage(C2S_RiftonomiconCreationClick msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (msg.pos == null || msg.slot < -1 || msg.slot >= TileRiftonomicon.CREATION_SAMPLE_SLOT_COUNT
                        || player.getDistanceSqToCenter(msg.pos) > 100.0D) return;
                TileEntity raw = player.world.getTileEntity(msg.pos);
                if (!(raw instanceof TileRiftonomicon)) return;
                TileRiftonomicon tile = (TileRiftonomicon) raw;
                ItemStack held = player.getHeldItemMainhand();
                if (msg.slot < 0) {
                    if (held.isEmpty()) giveOrDrop(player, tile.removeCreationCore());
                    else tile.insertCreationCore(player);
                } else if (!held.isEmpty() && held.getItem() instanceof ICaster) {
                    if (player.isSneaking()) tile.stopCreationResearchAndCheck(msg.slot);
                    else tile.startCreationResearch(msg.slot);
                } else if (held.isEmpty()) {
                    giveOrDrop(player, tile.removeCreationSample(msg.slot));
                } else {
                    tile.insertCreationSample(msg.slot, player);
                }
                player.inventoryContainer.detectAndSendChanges();
            });
            return null;
        }

        private static void giveOrDrop(EntityPlayerMP player, ItemStack stack) {
            if (stack.isEmpty()) return;
            if (player.getHeldItemMainhand().isEmpty()) player.setHeldItem(EnumHand.MAIN_HAND, stack);
            else if (!player.inventory.addItemStackToInventory(stack)) player.dropItem(stack, false);
        }
    }
}
