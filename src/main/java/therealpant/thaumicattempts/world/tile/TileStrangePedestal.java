package therealpant.thaumicattempts.world.tile;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IEssentiaContainerItem;
import therealpant.thaumicattempts.data.research.TAAspects;
import therealpant.thaumicattempts.init.ModBlocksItems;

import javax.annotation.Nullable;

public class TileStrangePedestal extends TileEntity
        implements IAnimatable, ISidedInventory {
    public static final int MAX_PARADOXUM = 250;
    private static final String ITEM_TAG = "HeldItem";

    private final AnimationFactory factory = new AnimationFactory(this);
    private final IItemHandler unsidedItemHandler = new InvWrapper(this);
    private final IItemHandler[] sidedItemHandlers = new IItemHandler[6];
    private ItemStack heldItem = ItemStack.EMPTY;

    /**
     * Closed input used only by an explicitly selected monolith.
     * This tile deliberately exposes no essentia transport capability.
     */
    public int receiveParadoxumFromMonolith(TileAnomalousMonolith source, int amount) {
        if (world == null || world.isRemote || source == null || amount <= 0
                || source.getWorld() != world || !canReceiveParadoxum()) {
            return 0;
        }
        int accepted = Math.min(amount, MAX_PARADOXUM - getParadoxum());
        if (accepted <= 0) return 0;

        IEssentiaContainerItem container = (IEssentiaContainerItem) heldItem.getItem();
        AspectList aspects = container.getAspects(heldItem);
        if (aspects == null) aspects = new AspectList();
        aspects.add(TAAspects.PARADOXUM, accepted);
        container.setAspects(heldItem, aspects);
        markDirtyAndSync();
        return accepted;
    }

    public int getParadoxum() {
        if (!hasRiftAmethyst() || TAAspects.PARADOXUM == null) return 0;
        AspectList aspects =
                ((IEssentiaContainerItem) heldItem.getItem()).getAspects(heldItem);
        return aspects == null ? 0 : aspects.getAmount(TAAspects.PARADOXUM);
    }

    public int getRedstonePower() {
        if (!hasRiftAmethyst()) return 0;
        int paradoxum = MathHelper.clamp(getParadoxum(), 0, MAX_PARADOXUM);
        return MathHelper.clamp(
                15 - MathHelper.floor(paradoxum * 15.0F / MAX_PARADOXUM),
                0, 15);
    }

    public boolean canReceiveParadoxum() {
        return hasRiftAmethyst() && TAAspects.PARADOXUM != null
                && getParadoxum() < MAX_PARADOXUM;
    }

    public boolean hasRiftAmethyst() {
        return !heldItem.isEmpty()
                && heldItem.getItem() == ModBlocksItems.RIFT_AMETIST
                && heldItem.getItem() instanceof IEssentiaContainerItem;
    }

    public ItemStack getHeldItem() {
        return heldItem;
    }

    public boolean insertAmethyst(ItemStack stack) {
        if (!heldItem.isEmpty() || stack.isEmpty()
                || stack.getItem() != ModBlocksItems.RIFT_AMETIST) {
            return false;
        }
        setInventorySlotContents(0, stack);
        return true;
    }

    public ItemStack removeAmethyst() {
        return removeStackFromSlot(0);
    }

    @Override
    public int getSizeInventory() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return heldItem.isEmpty();
    }

    @Override
    public ItemStack getStackInSlot(int index) {
        return index == 0 ? heldItem : ItemStack.EMPTY;
    }

    @Override
    public ItemStack decrStackSize(int index, int count) {
        if (index != 0 || count <= 0 || heldItem.isEmpty()) return ItemStack.EMPTY;
        ItemStack result = heldItem.splitStack(Math.min(count, heldItem.getCount()));
        if (heldItem.isEmpty()) heldItem = ItemStack.EMPTY;
        markDirtyAndSync();
        return result;
    }

    @Override
    public ItemStack removeStackFromSlot(int index) {
        if (index != 0 || heldItem.isEmpty()) return ItemStack.EMPTY;
        ItemStack result = heldItem;
        heldItem = ItemStack.EMPTY;
        markDirtyAndSync();
        return result;
    }

    @Override
    public void setInventorySlotContents(int index, ItemStack stack) {
        if (index != 0) return;
        if (stack.isEmpty()) {
            if (heldItem.isEmpty()) return;
            heldItem = ItemStack.EMPTY;
            markDirtyAndSync();
            return;
        }
        if (!isItemValidForSlot(index, stack)) return;
        heldItem = stack.copy();
        heldItem.setCount(1);
        markDirtyAndSync();
    }

    @Override
    public int getInventoryStackLimit() {
        return 1;
    }

    @Override
    public boolean isUsableByPlayer(EntityPlayer player) {
        return world != null && world.getTileEntity(pos) == this
                && player.getDistanceSq(pos.getX() + 0.5D,
                pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public void openInventory(EntityPlayer player) {}

    @Override
    public void closeInventory(EntityPlayer player) {}

    @Override
    public boolean isItemValidForSlot(int index, ItemStack stack) {
        return index == 0 && heldItem.isEmpty() && !stack.isEmpty()
                && stack.getItem() == ModBlocksItems.RIFT_AMETIST;
    }

    @Override
    public int getField(int id) {
        return 0;
    }

    @Override
    public void setField(int id, int value) {}

    @Override
    public int getFieldCount() {
        return 0;
    }

    @Override
    public void clear() {
        if (heldItem.isEmpty()) return;
        heldItem = ItemStack.EMPTY;
        markDirtyAndSync();
    }

    @Override
    public String getName() {
        return "container.thaumicattempts.strange_pedestal";
    }

    @Override
    public boolean hasCustomName() {
        return false;
    }

    @Override
    public int[] getSlotsForFace(EnumFacing side) {
        return new int[]{0};
    }

    @Override
    public boolean canInsertItem(int index, ItemStack itemStackIn,
                                 EnumFacing direction) {
        return isItemValidForSlot(index, itemStackIn);
    }

    @Override
    public boolean canExtractItem(int index, ItemStack stack,
                                  EnumFacing direction) {
        return index == 0 && !heldItem.isEmpty();
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return true;
        return super.hasCapability(capability, facing);
    }

    @Nullable
    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            IItemHandler handler = unsidedItemHandler;
            if (facing != null) {
                int index = facing.getIndex();
                if (sidedItemHandlers[index] == null) {
                    sidedItemHandlers[index] = new SidedInvWrapper(this, facing);
                }
                handler = sidedItemHandlers[index];
            }
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(handler);
        }
        return super.getCapability(capability, facing);
    }

    @Override
    public void registerControllers(AnimationData data) {
        data.addAnimationController(new AnimationController<>(
                this, "pedestal_controller", 0, this::animationPredicate));
    }

    private <E extends IAnimatable> PlayState animationPredicate(AnimationEvent<E> event) {
        event.getController().setAnimation(
                new AnimationBuilder().addAnimation("animation", true));
        return PlayState.CONTINUE;
    }

    @Override
    public AnimationFactory getFactory() {
        return factory;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        heldItem = compound.hasKey(ITEM_TAG, 10)
                ? new ItemStack(compound.getCompoundTag(ITEM_TAG))
                : ItemStack.EMPTY;
        if (!heldItem.isEmpty()) heldItem.setCount(1);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        if (!heldItem.isEmpty()) {
            compound.setTag(ITEM_TAG, heldItem.writeToNBT(new NBTTagCompound()));
        }
        return compound;
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Nullable
    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        readFromNBT(packet.getNbtCompound());
    }

    private void markDirtyAndSync() {
        markDirty();
        if (world != null) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
            if (!world.isRemote) {
                world.notifyNeighborsOfStateChange(pos, state.getBlock(), false);
            }
        }
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return new AxisAlignedBB(pos).grow(1.0D, 2.0D, 1.0D);
    }

    @Override
    public double getMaxRenderDistanceSquared() {
        return 65536.0D;
    }
}
