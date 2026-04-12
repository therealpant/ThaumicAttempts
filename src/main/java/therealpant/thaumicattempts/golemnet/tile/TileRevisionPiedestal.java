package therealpant.thaumicattempts.golemnet.tile;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import thaumcraft.api.blocks.BlocksTC;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;
import therealpant.thaumicattempts.api.IAutomationOrderAcceptor;
import therealpant.thaumicattempts.api.ITerminalOrderIconProvider;
import therealpant.thaumicattempts.api.TerminalOrderApi;
import therealpant.thaumicattempts.util.ResourceIdentity;
import therealpant.thaumicattempts.golemnet.tile.TileMirrorManager;

import javax.annotation.Nullable;
import java.util.List;

public class TileRevisionPiedestal extends TileEntity implements IAnimatable, ITickable {

    private static final String TAG_ACTIVE = "Active";
    private static final String TAG_COUNTER = "Counter";
    private static final String TAG_ITEM = "Item";
    private static final String TAG_MANAGER_POS = "ManagerPos";
    private static final String TAG_PENDING_ITEMS = "PendingItems";
    private static final String TAG_LAST_SEEN_AVAILABLE = "LastSeenAvailable";
    private static final String TAG_NEXT_RETRY_TICK = "NextRetryTick";


    private final AnimationFactory factory = new AnimationFactory(this);
    private boolean active = true;
    private ItemStack pedestalItem = ItemStack.EMPTY;
    private int counter = 1;
    private int outSignal = 0;
    private int tickAccumulator = 0;
    private int pendingItems = 0;
    private int lastSeenAvailable = 0;
    private long nextRetryTick = 0L;
    @Nullable
    private BlockPos managerPos = null;

    private static final class ResolvedAutomationTarget {
        final IAutomationOrderAcceptor acceptor;
        final int slot;

        private ResolvedAutomationTarget(IAutomationOrderAcceptor acceptor, int slot) {
            this.acceptor = acceptor;
            this.slot = slot;
        }
    }

    public boolean isActive() {
        return active;
    }

    public void toggleActive() {
        setActive(!active);
    }

    public void setActive(boolean active) {
        this.active = active;
        recalcSignalAndNotify(true);
        markDirtyAndSync();
    }

    public ItemStack getPedestalItem() {
        return pedestalItem;
    }

    public ItemStack getRequestedItem() {
        if (pedestalItem == null || pedestalItem.isEmpty()) return ItemStack.EMPTY;
        ItemStack requested = TerminalOrderApi.stripOrderIconData(pedestalItem);
        if (requested == null || requested.isEmpty()) return ItemStack.EMPTY;
        ItemStack one = requested.copy();
        one.setCount(1);
        return one;
    }

    public int getCounter() {
        return counter;
    }

    public int getMultiplier() {
        if (world == null) return 1;
        IBlockState below = world.getBlockState(pos.down());
        Block b = below.getBlock();
        if (b == BlocksTC.stoneArcane) return 10;
        if (b == BlocksTC.stoneEldritchTile) return 64;
        return 1;
    }

    public int getThreshold() {
        return Math.max(1, counter) * Math.max(1, getMultiplier());
    }

    public int getOutSignal() {
        return outSignal;
    }

    @Nullable
    public BlockPos getManagerPos() {
        return managerPos;
    }

    public void setManagerPos(@Nullable BlockPos managerPos) {
        this.managerPos = managerPos == null ? null : managerPos.toImmutable();
        recalcSignalAndNotify(true);
        markDirtyAndSync();
    }

    public void clearManagerPosFromManager(BlockPos managerPos) {
        if (this.managerPos != null && this.managerPos.equals(managerPos)) {
            this.managerPos = null;
            recalcSignalAndNotify(true);
            markDirtyAndSync();
        }
    }

    public void cycleCounter() {
        counter++;
        if (counter > 16) {
            counter = 1;
        }
        recalcSignalAndNotify(true);
        markDirtyAndSync();
    }

    public boolean tryInsertFromHand(EntityPlayer player, EnumHand hand) {
        if (player == null || hand == null) return false;
        ItemStack held = player.getHeldItem(hand);
        if (held.isEmpty() || !pedestalItem.isEmpty()) return false;

        pedestalItem = held.copy();
        pedestalItem.setCount(1);
        resetOrderTracking();
        if (!player.capabilities.isCreativeMode) {
            held.shrink(1);
        }
        recalcSignalAndNotify(true);
        markDirtyAndSync();
        return true;
    }

    public boolean tryExtractToPlayer(EntityPlayer player) {
        if (player == null || pedestalItem.isEmpty()) return false;

        ItemStack out = pedestalItem.copy();
        pedestalItem = ItemStack.EMPTY;
        resetOrderTracking();
        if (!player.inventory.addItemStackToInventory(out) && world != null) {
            InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), out);
        }
        recalcSignalAndNotify(true);
        markDirtyAndSync();
        return true;
    }

    public void dropContents() {
        if (world == null || world.isRemote || pedestalItem.isEmpty()) return;
        InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), pedestalItem.copy());
        pedestalItem = ItemStack.EMPTY;
        resetOrderTracking();
        recalcSignalAndNotify(true);
        markDirty();
    }

    private void resetOrderTracking() {
        pendingItems = 0;
        lastSeenAvailable = 0;
        nextRetryTick = 0L;
    }

    private void markDirtyAndSync() {
        markDirty();
        if (world != null) {
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        }
    }

    @Override
    public void registerControllers(AnimationData data) {
        data.addAnimationController(new AnimationController<>(
                this,
                "main_controller",
                0,
                this::animationPredicate
        ));
    }

    private <E extends IAnimatable> PlayState animationPredicate(AnimationEvent<E> event) {
        event.getController().setAnimation(
                new AnimationBuilder().addAnimation("animation.revision.1", true)
        );
        return PlayState.CONTINUE;
    }

    @Override
    public AnimationFactory getFactory() {
        return factory;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        active = !compound.hasKey(TAG_ACTIVE) || compound.getBoolean(TAG_ACTIVE);
        counter = compound.hasKey(TAG_COUNTER) ? compound.getInteger(TAG_COUNTER) : 1;
        if (counter < 1) counter = 1;
        if (counter > 16) counter = 16;
        pedestalItem = compound.hasKey(TAG_ITEM) ? new ItemStack(compound.getCompoundTag(TAG_ITEM)) : ItemStack.EMPTY;
        outSignal = Math.max(0, Math.min(15, compound.getInteger("OutSignal")));
        managerPos = compound.hasKey(TAG_MANAGER_POS) ? BlockPos.fromLong(compound.getLong(TAG_MANAGER_POS)) : null;
        pendingItems = Math.max(0, compound.getInteger(TAG_PENDING_ITEMS));
        lastSeenAvailable = Math.max(0, compound.getInteger(TAG_LAST_SEEN_AVAILABLE));
        nextRetryTick = compound.getLong(TAG_NEXT_RETRY_TICK);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setBoolean(TAG_ACTIVE, active);
        compound.setInteger(TAG_COUNTER, counter);
        compound.setInteger("OutSignal", outSignal);
        if (!pedestalItem.isEmpty()) {
            compound.setTag(TAG_ITEM, pedestalItem.writeToNBT(new NBTTagCompound()));
        }
        if (managerPos != null) {
            compound.setLong(TAG_MANAGER_POS, managerPos.toLong());
        }
        compound.setInteger(TAG_PENDING_ITEMS, Math.max(0, pendingItems));
        compound.setInteger(TAG_LAST_SEEN_AVAILABLE, Math.max(0, lastSeenAvailable));
        compound.setLong(TAG_NEXT_RETRY_TICK, Math.max(0L, nextRetryTick));
        return compound;
    }

    private void recalcSignalAndNotify(boolean notifyNeighbors) {
        int newSignal = 0;
        ItemStack requested = getRequestedItem();
        if (active && world != null && !world.isRemote && managerPos != null && !requested.isEmpty()) {
            TileEntity te = world.getTileEntity(managerPos);
            if (te instanceof TileMirrorManager) {
                int count = ((TileMirrorManager) te).getAvailableCountFor(requested);
                int threshold = getThreshold();
                if (count > 0 && threshold > 0) {
                    newSignal = Math.max(1, Math.min(15, (count * 15) / threshold));
                }
            }
        }

        if (newSignal != outSignal) {
            outSignal = newSignal;
            markDirtyAndSync();
            if (notifyNeighbors && world != null) {
                world.notifyNeighborsOfStateChange(pos, world.getBlockState(pos).getBlock(), true);
            }
        }
    }

    @Override
    public void update() {
        if (world == null || world.isRemote) return;
        if (++tickAccumulator >= 10) {
            tickAccumulator = 0;
            recalcSignalAndNotify(true);
        }

        if (!active || managerPos == null || pedestalItem.isEmpty()) return;
        TileEntity te = world.getTileEntity(managerPos);
        if (!(te instanceof TileMirrorManager)) return;

        ItemStack requested = getRequestedItem();
        if (requested.isEmpty()) return;

        TileMirrorManager manager = (TileMirrorManager) te;
        int available = Math.max(0, manager.getAvailableCountFor(requested));
        if (available > lastSeenAvailable) {
            pendingItems = Math.max(0, pendingItems - (available - lastSeenAvailable));
        }
        lastSeenAvailable = available;

        int cap = Math.max(1, counter) * Math.max(1, getMultiplier());
        int batch = Math.max(1, counter);
        int missing = Math.max(0, cap - available - pendingItems);
        if (missing <= 0) return;

        long now = world.getTotalWorldTime();
        if (now < nextRetryTick) return;

        int toOrder = Math.min(batch, missing);
        int accepted = trySubmitRestockOrder(manager, toOrder);
        if (accepted > 0) {
            pendingItems += accepted;
            nextRetryTick = now + 40L;
            markDirtyAndSync();
        } else {
            nextRetryTick = now + 100L;
            markDirty();
        }
    }

    private int trySubmitRestockOrder(TileMirrorManager mgr, int items) {
        if (items <= 0 || pedestalItem.isEmpty() || world == null || mgr == null) return 0;

        if (TerminalOrderApi.isOrderIcon(pedestalItem)) {
            BlockPos targetPos = TerminalOrderApi.getOrderIconPos(pedestalItem);
            int slot = TerminalOrderApi.getOrderIconSlot(pedestalItem);
            if (targetPos == null || slot < 0) return 0;
            TileEntity target = world.getTileEntity(targetPos);
            if (target instanceof IAutomationOrderAcceptor) {
                return Math.max(0, ((IAutomationOrderAcceptor) target).submitAutomationOrder(slot, items, managerPos, null, -1));
            }
            return 0;
        }

        ItemStack requested = getRequestedItem();
        if (requested.isEmpty()) return 0;
        ResolvedAutomationTarget resolved = resolveAutomationTargetByItem(mgr, requested);
        if (resolved == null) return 0;
        return Math.max(0, resolved.acceptor.submitAutomationOrder(resolved.slot, items, managerPos, null, -1));
    }

    @Nullable
    private ResolvedAutomationTarget resolveAutomationTargetByItem(TileMirrorManager mgr, ItemStack requested) {
        if (world == null || mgr == null || requested == null || requested.isEmpty()) return null;

        ResolvedAutomationTarget single = null;
        for (BlockPos requesterPos : mgr.getRequestersSnapshot()) {
            if (requesterPos == null) continue;
            TileEntity tile = world.getTileEntity(requesterPos);
            if (!(tile instanceof IAutomationOrderAcceptor) || !(tile instanceof ITerminalOrderIconProvider)) continue;

            List<ItemStack> icons = ((ITerminalOrderIconProvider) tile).listTerminalOrderIcons();
            if (icons == null) continue;
            for (ItemStack icon : icons) {
                if (icon == null || icon.isEmpty()) continue;
                ItemStack stripped = TerminalOrderApi.stripOrderIconData(icon);
                if (stripped == null || stripped.isEmpty()) continue;
                stripped = stripped.copy();
                stripped.setCount(1);
                if (!ResourceIdentity.sameResource(stripped, requested)) continue;

                int slot = TerminalOrderApi.getOrderIconSlot(icon);
                if (slot < 0) continue;
                ResolvedAutomationTarget candidate = new ResolvedAutomationTarget((IAutomationOrderAcceptor) tile, slot);
                if (single != null) return null;
                single = candidate;
            }
        }
        return single;
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        readFromNBT(tag);
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        if (pkt != null && pkt.getNbtCompound() != null) {
            handleUpdateTag(pkt.getNbtCompound());
        }
    }
}
