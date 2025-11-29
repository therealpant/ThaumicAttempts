package therealpant.thaumicattempts.golemcraft.tile;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import therealpant.thaumicattempts.golemcraft.item.ItemDeliveryPattern;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class TileDeliveryStation extends TileEntity {

    private static final int PATTERN_SLOT_COUNT = 15;

    private static final String TAG_PATTERN = "Pattern";
    private static final String TAG_PAYLOAD = "Payload";
    private static final String TAG_TARGETS = "Targets";
    private static final String TAG_CLICK   = "ClickPos";

    private final ItemStackHandler patternSlots = new ItemStackHandler(PATTERN_SLOT_COUNT) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack != null && stack.getItem() instanceof ItemDeliveryPattern;
        }

        @Override
        protected void onContentsChanged(int slot) { markDirty(); }
    };

    private final ItemStackHandler payloadSlot = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) { return stack != null && !stack.isEmpty(); }

        @Override
        protected void onContentsChanged(int slot) { markDirty(); }
    };

    private final IItemHandler combined = new CombinedInvWrapper(patternSlots, payloadSlot);


    private final List<BlockPos> targetChests = new ArrayList<>();
    @Nullable private BlockPos clickTarget = null;

    public ItemStackHandler getPatternHandler() { return patternSlots; }
    public ItemStackHandler getPayloadHandler() { return payloadSlot; }

    public boolean tryInsertPattern(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof ItemDeliveryPattern)) return false;
        for (int i = 0; i < patternSlots.getSlots(); i++) {
            if (patternSlots.getStackInSlot(i).isEmpty()) {
                ItemStack copy = stack.copy();
                copy.setCount(1);
                patternSlots.setStackInSlot(i, copy);
                return true;
            }
        }
        return false;
    }

    public ItemStack tryExtractPattern() {
        for (int i = patternSlots.getSlots() - 1; i >= 0; i--) {
            ItemStack stack = patternSlots.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            patternSlots.setStackInSlot(i, ItemStack.EMPTY);
            return stack;
        }
        return ItemStack.EMPTY;
    }

    public boolean tryInsertPayload(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!payloadSlot.getStackInSlot(0).isEmpty()) return false;
        ItemStack copy = stack.copy();
        copy.setCount(1);
        payloadSlot.setStackInSlot(0, copy);
        return true;
    }

    public ItemStack tryExtractPayload() {
        ItemStack stack = payloadSlot.getStackInSlot(0);
        if (stack.isEmpty()) return ItemStack.EMPTY;
        payloadSlot.setStackInSlot(0, ItemStack.EMPTY);
        return stack;
    }

    public void applyLinks(List<BlockPos> orderedTargets, @Nullable BlockPos click) {
        targetChests.clear();
        if (orderedTargets != null) {
            targetChests.addAll(orderedTargets);
        }
        clickTarget = click == null ? null : click.toImmutable();
        markDirty();
    }

    public List<BlockPos> getTargetChests() { return new ArrayList<>(targetChests); }
    @Nullable public BlockPos getClickTarget() { return clickTarget; }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return true;
        return super.hasCapability(capability, facing);
    }

    @Nullable
    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(combined);
        }
        return super.getCapability(capability, facing);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setTag(TAG_PATTERN, patternSlots.serializeNBT());
        compound.setTag(TAG_PAYLOAD, payloadSlot.serializeNBT());

        NBTTagList list = new NBTTagList();
        for (BlockPos p : targetChests) {
            NBTTagCompound posTag = new NBTTagCompound();
            posTag.setInteger("x", p.getX());
            posTag.setInteger("y", p.getY());
            posTag.setInteger("z", p.getZ());
            list.appendTag(posTag);
        }
        compound.setTag(TAG_TARGETS, list);

        if (clickTarget != null) {
            NBTTagCompound pos = new NBTTagCompound();
            pos.setInteger("x", clickTarget.getX());
            pos.setInteger("y", clickTarget.getY());
            pos.setInteger("z", clickTarget.getZ());
            compound.setTag(TAG_CLICK, pos);
        }
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        if (compound.hasKey(TAG_PATTERN, Constants.NBT.TAG_COMPOUND)) {
            patternSlots.deserializeNBT(compound.getCompoundTag(TAG_PATTERN));
        }
        if (compound.hasKey(TAG_PAYLOAD, Constants.NBT.TAG_COMPOUND)) {
            payloadSlot.deserializeNBT(compound.getCompoundTag(TAG_PAYLOAD));
        }
        targetChests.clear();
        if (compound.hasKey(TAG_TARGETS, Constants.NBT.TAG_LIST)) {
            NBTTagList list = compound.getTagList(TAG_TARGETS, Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound posTag = list.getCompoundTagAt(i);
                BlockPos p = new BlockPos(posTag.getInteger("x"), posTag.getInteger("y"), posTag.getInteger("z"));
                targetChests.add(p);
            }
        }
        clickTarget = null;
        if (compound.hasKey(TAG_CLICK, Constants.NBT.TAG_COMPOUND)) {
            NBTTagCompound pos = compound.getCompoundTag(TAG_CLICK);
            clickTarget = new BlockPos(pos.getInteger("x"), pos.getInteger("y"), pos.getInteger("z"));
        }
    }

    public void dropContents() {
        if (world == null || world.isRemote) return;
        for (ItemStackHandler handler : new ItemStackHandler[]{patternSlots, payloadSlot}) {
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    EntityItem ent = new EntityItem(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
                    world.spawnEntity(ent);
                    handler.setStackInSlot(i, ItemStack.EMPTY);
                }
            }
        }
    }
}