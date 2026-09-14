package therealpant.thaumicattempts.world.tile;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.common.util.Constants;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;
import thaumcraft.api.aspects.Aspect;
import therealpant.thaumicattempts.data.research.TAAspects;
import therealpant.thaumicattempts.world.block.BlockParadoxDistillator;
import therealpant.thaumicattempts.world.item.ParadoxumContainer;
import therealpant.thaumicattempts.world.tile.automation.ParadoxPedestalAutomation;
import therealpant.thaumicattempts.world.tile.automation.ParadoxPedestalAutomationHost;

import javax.annotation.Nullable;

public class TileParadoxDistillator extends TileEntity implements ITickable, IAnimatable,
        ParadoxPedestalAutomationHost {
    public static final int SLOT_COUNT = 4;
    public static final int PROCESS_INTERVAL = 20;
    public static final double CONVERSION_RATE = 0.9D;

    private final AnimationFactory factory = new AnimationFactory(this);
    private final ParadoxPedestalAutomation pedestalAutomation =
            new ParadoxPedestalAutomation(this, this);
    private final NonNullList<ItemStack> inputs =
            NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private int essentia;
    private double conversionRemainder;
    private int ticks;

    @Override
    public void update() {
        if (world == null || world.isRemote) return;
        ticks++;
        if (ticks == 1 || ticks % 20 == 0) {
            BlockParadoxDistillator.ensureOutputPort(world, pos);
        }
        if (ticks % PROCESS_INTERVAL == 0) {
            int consumed = 0;
            for (ItemStack input : inputs) {
                consumed += ParadoxumContainer.take(input, 1);
            }
            if (consumed > 0) {
                conversionRemainder += consumed * CONVERSION_RATE;
                int produced = (int) Math.floor(conversionRemainder + 1.0E-9D);
                if (produced > 0) {
                    essentia = Math.min(Integer.MAX_VALUE - 4, essentia + produced);
                    conversionRemainder -= produced;
                }
                markDirtyAndSync();
            }
        }
        pedestalAutomation.tick();
    }

    public boolean insertContainer(ItemStack source) {
        if (!ParadoxumContainer.isContainer(source)) return false;
        for (int slot = 0; slot < inputs.size(); slot++) {
            if (!inputs.get(slot).isEmpty() || pedestalAutomation.isSlotBusy(slot)) continue;
            ItemStack inserted = source.copy();
            inserted.setCount(1);
            ParadoxumContainer.normalize(inserted);
            inputs.set(slot, inserted);
            pedestalAutomation.clearOrigin(slot);
            markDirtyAndSync();
            return true;
        }
        return false;
    }

    public ItemStack removeLastContainer() {
        for (int slot = inputs.size() - 1; slot >= 0; slot--) {
            if (inputs.get(slot).isEmpty() || pedestalAutomation.isSlotBusy(slot)) continue;
            ItemStack result = inputs.get(slot);
            inputs.set(slot, ItemStack.EMPTY);
            pedestalAutomation.clearOrigin(slot);
            markDirtyAndSync();
            return result;
        }
        return ItemStack.EMPTY;
    }

    public ItemStack getInput(int slot) {
        return slot >= 0 && slot < inputs.size() ? inputs.get(slot) : ItemStack.EMPTY;
    }

    public int getEssentiaAmount() { return essentia; }

    @Nullable
    public Aspect getEssentiaType() {
        return essentia > 0 ? TAAspects.PARADOXUM : null;
    }

    public int takeEssentia(Aspect aspect, int amount) {
        if (amount <= 0 || aspect == null || aspect != TAAspects.PARADOXUM) return 0;
        int taken = Math.min(amount, essentia);
        if (taken > 0) {
            essentia -= taken;
            markDirtyAndSync();
        }
        return taken;
    }

    public void giveInputsTo(EntityPlayer player) {
        for (int slot = 0; slot < inputs.size(); slot++) {
            ItemStack stack = inputs.get(slot);
            if (stack.isEmpty()) continue;
            if (!player.inventory.addItemStackToInventory(stack)) player.dropItem(stack, false);
            inputs.set(slot, ItemStack.EMPTY);
        }
        markDirtyAndSync();
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("ParadoxumEssentia", essentia);
        tag.setDouble("ConversionRemainder", conversionRemainder);
        NBTTagList list = new NBTTagList();
        for (int slot = 0; slot < inputs.size(); slot++) {
            if (inputs.get(slot).isEmpty()) continue;
            NBTTagCompound itemTag = new NBTTagCompound();
            itemTag.setByte("Slot", (byte) slot);
            inputs.get(slot).writeToNBT(itemTag);
            list.appendTag(itemTag);
        }
        tag.setTag("Inputs", list);
        pedestalAutomation.writeToNBT(tag);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        essentia = Math.max(0, tag.getInteger("ParadoxumEssentia"));
        conversionRemainder = Math.max(0.0D,
                Math.min(0.999999D, tag.getDouble("ConversionRemainder")));
        for (int slot = 0; slot < inputs.size(); slot++) inputs.set(slot, ItemStack.EMPTY);
        NBTTagList list = tag.getTagList("Inputs", Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < list.tagCount(); index++) {
            NBTTagCompound itemTag = list.getCompoundTagAt(index);
            int slot = itemTag.getByte("Slot") & 255;
            if (slot < inputs.size()) {
                ItemStack stack = new ItemStack(itemTag);
                if (!stack.isEmpty() && ParadoxumContainer.isContainer(stack)) {
                    stack.setCount(1);
                    inputs.set(slot, stack);
                }
            }
        }
        pedestalAutomation.readFromNBT(tag);
    }

    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Nullable @Override public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        readFromNBT(packet.getNbtCompound());
    }

    private void markDirtyAndSync() {
        markDirty();
        if (world != null) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
        }
    }

    public ParadoxPedestalAutomation getPedestalAutomation() { return pedestalAutomation; }

    @Override public int getAutomationSlotCount() { return SLOT_COUNT; }
    @Override public ItemStack getAutomationStack(int slot) { return getInput(slot); }
    @Override public boolean canAcceptAutomatedStack(ItemStack stack) {
        return ParadoxumContainer.isContainer(stack) && ParadoxumContainer.getStored(stack) > 0;
    }
    @Override public boolean isAutomatedStackComplete(ItemStack stack) {
        return ParadoxumContainer.isContainer(stack) && ParadoxumContainer.getStored(stack) <= 0;
    }
    @Override public boolean insertAutomatedStack(int slot, ItemStack stack) {
        if (slot < 0 || slot >= inputs.size() || !inputs.get(slot).isEmpty()
                || !ParadoxumContainer.isContainer(stack)) return false;
        ItemStack inserted = stack.copy();
        inserted.setCount(1);
        ParadoxumContainer.normalize(inserted);
        inputs.set(slot, inserted);
        markDirtyAndSync();
        return true;
    }
    @Override public ItemStack extractAutomatedStack(int slot) {
        if (slot < 0 || slot >= inputs.size() || inputs.get(slot).isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack result = inputs.get(slot);
        inputs.set(slot, ItemStack.EMPTY);
        markDirtyAndSync();
        return result;
    }
    @Override public Vec3d getAutomationSlotPosition(int slot) {
        double[][] anchors = {
                {17.0D / 16.0D, 17.0D / 16.0D},
                {-17.0D / 16.0D, 17.0D / 16.0D},
                {-17.0D / 16.0D, -17.0D / 16.0D},
                {17.0D / 16.0D, -17.0D / 16.0D}
        };
        int safeSlot = Math.max(0, Math.min(anchors.length - 1, slot));
        return new Vec3d(pos.getX() + 0.5D + anchors[safeSlot][0],
                pos.getY() + 18.0D / 16.0D,
                pos.getZ() + 0.5D + anchors[safeSlot][1]);
    }
    @Override public void onAutomationChanged() { markDirtyAndSync(); }
    @Override public boolean hasPedestalReservation(BlockPos pedestalPos) {
        return pedestalAutomation.isDestination(pedestalPos);
    }

    @Override public void registerControllers(AnimationData data) {
        data.addAnimationController(new AnimationController<>(
                this, "paradox_distillator_controller", 0, this::animationPredicate));
    }
    private <E extends IAnimatable> PlayState animationPredicate(AnimationEvent<E> event) {
        event.getController().setAnimation(new AnimationBuilder().addAnimation("well", true));
        return PlayState.CONTINUE;
    }
    @Override public AnimationFactory getFactory() { return factory; }
    @Override public AxisAlignedBB getRenderBoundingBox() {
        return new AxisAlignedBB(pos).grow(7.0D, 3.0D, 7.0D);
    }
    @Override public double getMaxRenderDistanceSquared() { return 65536.0D; }
}
