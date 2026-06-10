// src/main/java/therealpant/thaumicattempts/golemnet/net/msg/S2CFlyAnim.java
package therealpant.thaumicattempts.golemnet.net.msg;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.Side;
import therealpant.thaumicattempts.ThaumicAttempts;

public class S2CFlyAnim implements IMessage {
    public static final int MODE_MANAGER_TO_MIRROR = 0;
    public static final int MODE_MIRROR_TO_MIRROR = 1;
    public BlockPos managerPos;
    public ItemStack stack = ItemStack.EMPTY;
    public int ring, slot, duration;
    public int srcRing = -1, srcSlot = -1, mode = MODE_MANAGER_TO_MIRROR;
    public long seed;
    public int delay;

    public S2CFlyAnim() {}
    public S2CFlyAnim(BlockPos p, ItemStack s, int r, int sl, int dur, long seed) {
        this(p, s, r, sl, -1, -1, MODE_MANAGER_TO_MIRROR, dur, seed, 0);
    }

    public S2CFlyAnim(BlockPos p, ItemStack s, int r, int sl, int srcR, int srcS, int mode, int dur, long seed) {
        this(p, s, r, sl, srcR, srcS, mode, dur, seed, 0);
    }

    public S2CFlyAnim(BlockPos p, ItemStack s, int r, int sl, int srcR, int srcS, int mode, int dur, long seed, int delay) {
        this.managerPos = p;
        this.stack = s == null ? ItemStack.EMPTY : s.copy();
        if (!this.stack.isEmpty()) this.stack.setCount(1);
        this.ring = r;
        this.slot = sl;
        this.srcRing = srcR;
        this.srcSlot = srcS;
        this.mode = mode;
        this.duration = dur;
        this.seed = seed;
        this.delay = Math.max(0, delay);
    }

    @Override public void toBytes(ByteBuf buf) {
        buf.writeLong(managerPos.toLong());
        ByteBufUtils.writeItemStack(buf, stack);
        buf.writeInt(ring); buf.writeInt(slot);
        buf.writeInt(duration);
        buf.writeInt(srcRing); buf.writeInt(srcSlot);
        buf.writeInt(mode);
        buf.writeLong(seed);
        buf.writeInt(delay);
    }
    @Override public void fromBytes(ByteBuf buf) {
        managerPos = BlockPos.fromLong(buf.readLong());
        stack = ByteBufUtils.readItemStack(buf);
        ring = buf.readInt(); slot = buf.readInt();
        duration = buf.readInt();
        srcRing = buf.readInt(); srcSlot = buf.readInt();
        mode = buf.readInt();
        seed = buf.readLong();
        delay = buf.readInt();
    }

    /**
     * Универсальный способ отослать анимацию «предмет летит в зеркало»
     * на всех клиентов вокруг менеджера.
     */
    public static void dispatchManagerToMirror(World world, BlockPos pos,
                                               ItemStack stack, int ring, int slot,
                                               int duration, long seed) {
        dispatch(world, pos, stack, ring, slot, -1, -1, MODE_MANAGER_TO_MIRROR, duration, seed, 0);
    }

    public static void dispatchManagerToMirror(World world, BlockPos pos,
                                               ItemStack stack, int ring, int slot,
                                               int duration, long seed, int delay) {
        dispatch(world, pos, stack, ring, slot, -1, -1, MODE_MANAGER_TO_MIRROR, duration, seed, delay);
    }

    public static void dispatchMirrorToMirror(World world, BlockPos pos,
                                              ItemStack stack, int srcRing, int srcSlot, int dstRing, int dstSlot,
                                              int duration, long seed) {
        dispatch(world, pos, stack, dstRing, dstSlot, srcRing, srcSlot, MODE_MIRROR_TO_MIRROR, duration, seed, 0);
    }

    public static void dispatchMirrorToMirror(World world, BlockPos pos,
                                              ItemStack stack, int srcRing, int srcSlot, int dstRing, int dstSlot,
                                              int duration, long seed, int delay) {
        dispatch(world, pos, stack, dstRing, dstSlot, srcRing, srcSlot, MODE_MIRROR_TO_MIRROR, duration, seed, delay);
    }

    public static void dispatch(World world, BlockPos pos,
                                ItemStack stack, int ring, int slot,
                                int srcRing, int srcSlot, int mode,
                                int duration, long seed) {
        dispatch(world, pos, stack, ring, slot, srcRing, srcSlot, mode, duration, seed, 0);
    }

    public static void dispatch(World world, BlockPos pos,
                                ItemStack stack, int ring, int slot,
                                int srcRing, int srcSlot, int mode,
                                int duration, long seed, int delay) {
        if (world == null || world.isRemote) return;

        S2CFlyAnim msg = new S2CFlyAnim(pos, stack, ring, slot, srcRing, srcSlot, mode, duration, seed, delay);
        NetworkRegistry.TargetPoint tp = new NetworkRegistry.TargetPoint(
                world.provider.getDimension(),
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5,
                32.0
        );
        ThaumicAttempts.NET.sendToAllAround(msg, tp);
    }

    public static class Handler implements IMessageHandler<S2CFlyAnim, IMessage> {
        @Override public IMessage onMessage(S2CFlyAnim msg, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                TileEntity te = Minecraft.getMinecraft().world.getTileEntity(msg.managerPos);
                if (te instanceof therealpant.thaumicattempts.golemnet.tile.TileMirrorManager) {
                    ((therealpant.thaumicattempts.golemnet.tile.TileMirrorManager) te)
                            .clientAddFlying(msg.stack, msg.ring, msg.slot, msg.srcRing, msg.srcSlot, msg.mode, msg.duration, msg.seed, msg.delay);
                }
            });
            return null;
        }
    }
}
