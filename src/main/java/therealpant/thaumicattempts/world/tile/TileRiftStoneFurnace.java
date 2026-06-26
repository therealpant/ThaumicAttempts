package therealpant.thaumicattempts.world.tile;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.items.ItemStackHandler;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectContainer;
import thaumcraft.common.lib.crafting.ThaumcraftCraftingManager;
import therealpant.thaumicattempts.data.research.TAAspects;

import javax.annotation.Nullable;
import java.util.List;

public class TileRiftStoneFurnace extends TileEntity implements ITickable, IAnimatable, IAspectContainer {
    public static final int INPUT_SLOTS = 3;
    public static final int ESSENTIA_CAP = 1000;
    public static final int PORT_CAP = 250;
    private static final float VOID_EFFICIENCY = 0.95F;

    private final AnimationFactory factory = new AnimationFactory(this);
    private final ItemStackHandler input = new ItemStackHandler(INPUT_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            markDirtyAndSync();
        }
    };

    private AspectList aspects = new AspectList();
    private int essentiaAmount;
    private int cookTime;
    private int smeltTime = 100;
    private int ticks;

    @Override
    public void update() {
        if (world == null || world.isRemote) {
            return;
        }

        ticks++;
        if (ticks % 5 == 0) {
            collectItems();
        }

        if (canSmelt()) {
            cookTime++;
            if (cookTime >= smeltTime) {
                cookTime = 0;
                smeltItem();
            }
        } else {
            cookTime = 0;
        }
    }

    private void collectItems() {
        if (isInputFull()) {
            return;
        }

        AxisAlignedBB intake = new AxisAlignedBB(
                pos.getX() + 0.0625D, pos.getY() + 0.5D, pos.getZ() + 0.0625D,
                pos.getX() + 0.9375D, pos.getY() + 1.25D, pos.getZ() + 0.9375D
        );
        List<EntityItem> items = world.getEntitiesWithinAABB(EntityItem.class, intake);
        for (EntityItem entity : items) {
            if (entity == null || entity.isDead) {
                continue;
            }
            ItemStack remaining = insertOne(entity.getItem());
            entity.setItem(remaining);
            if (remaining.isEmpty()) {
                entity.setDead();
            }
            if (isInputFull()) {
                break;
            }
        }
    }

    private ItemStack insertOne(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (!isSmeltable(stack)) {
            return stack;
        }

        ItemStack one = stack.copy();
        one.setCount(1);
        ItemStack remainder = one;
        for (int i = 0; i < input.getSlots(); i++) {
            remainder = input.insertItem(i, remainder, false);
            if (remainder.isEmpty()) {
                ItemStack result = stack.copy();
                result.shrink(1);
                markDirtyAndSync();
                return result;
            }
        }
        return stack;
    }

    private boolean isInputFull() {
        for (int i = 0; i < input.getSlots(); i++) {
            if (input.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean canSmelt() {
        if (essentiaAmount >= ESSENTIA_CAP) {
            return false;
        }

        ItemStack stack = getNextInput();
        if (stack.isEmpty()) {
            return false;
        }

        AspectList tags = getSmeltAspects(stack);
        if (tags == null || tags.size() <= 0) {
            return false;
        }

        int needed = tags.visSize();
        if (needed > ESSENTIA_CAP - essentiaAmount) {
            return false;
        }

        smeltTime = Math.max(1, needed * 2);
        return true;
    }

    private ItemStack getNextInput() {
        for (int i = 0; i < input.getSlots(); i++) {
            ItemStack stack = input.getStackInSlot(i);
            if (!stack.isEmpty()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private int getNextInputSlot() {
        for (int i = 0; i < input.getSlots(); i++) {
            if (!input.getStackInSlot(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private boolean isSmeltable(ItemStack stack) {
        AspectList tags = getSmeltAspects(stack);
        return tags != null && tags.size() > 0 && tags.visSize() > 0;
    }

    @Nullable
    private AspectList getSmeltAspects(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return ThaumcraftCraftingManager.getObjectTags(stack);
    }

    private void smeltItem() {
        int slot = getNextInputSlot();
        if (slot < 0) {
            return;
        }

        ItemStack stack = input.getStackInSlot(slot);
        AspectList produced = getSmeltAspects(stack);
        if (produced == null || produced.size() <= 0) {
            input.extractItem(slot, 1, false);
            markDirtyAndSync();
            return;
        }

        produced = produced.copy();
        int waste = applyVoidEfficiency(produced);
        for (Aspect aspect : produced.getAspects()) {
            int amount = produced.getAmount(aspect);
            if (amount > 0) {
                aspects.add(aspect, amount);
            }
        }
        if (waste > 0) {
            Aspect wasteAspect = getWasteAspect();
            if (wasteAspect != null) {
                aspects.add(wasteAspect, waste);
            }
        }

        input.extractItem(slot, 1, false);
        recalcEssentiaAmount();
        markDirtyAndSync();
    }

    private int applyVoidEfficiency(AspectList produced) {
        int waste = 0;
        for (Aspect aspect : produced.getAspects()) {
            int amount = produced.getAmount(aspect);
            for (int i = 0; i < amount; i++) {
                float efficiency = aspect == Aspect.FLUX ? VOID_EFFICIENCY * 0.66F : VOID_EFFICIENCY;
                if (world.rand.nextFloat() > efficiency && produced.reduce(aspect, 1)) {
                    waste++;
                }
            }
        }
        return waste;
    }

    @Nullable
    private Aspect getWasteAspect() {
        if (TAAspects.PARADOXUM != null) {
            return TAAspects.PARADOXUM;
        }
        return Aspect.getAspect("paradoxum");
    }

    private void recalcEssentiaAmount() {
        essentiaAmount = aspects == null ? 0 : aspects.visSize();
    }

    public int getEssentiaAmountTotal() {
        return essentiaAmount;
    }

    public float getFluidLevelPixels() {
        if (essentiaAmount <= 0) {
            return 0.0F;
        }
        return 1.0F + (essentiaAmount / 100.0F);
    }

    @Nullable
    public Aspect getAspectForPort(int portIndex) {
        Aspect[] present = getPresentAspects();
        if (present.length == 0) {
            return null;
        }
        if (present.length == 1) {
            return present[0];
        }
        if (present.length == 2) {
            return present[portIndex < 2 ? 0 : 1];
        }
        if (present.length == 3) {
            return present[portIndex == 3 ? 0 : portIndex];
        }
        return present[portIndex % present.length];
    }

    public int getAmountForPort(int portIndex) {
        Aspect aspect = getAspectForPort(portIndex);
        if (aspect == null) {
            return 0;
        }
        int assigned = getPortCountForAspect(aspect);
        int total = aspects.getAmount(aspect);
        if (total <= 0) {
            return 0;
        }
        int visibleShare = (total + Math.max(1, assigned) - 1) / Math.max(1, assigned);
        return Math.min(PORT_CAP, visibleShare);
    }

    private int getPortCountForAspect(Aspect aspect) {
        int count = 0;
        for (int i = 0; i < 4; i++) {
            if (getAspectForPort(i) == aspect) {
                count++;
            }
        }
        return count;
    }

    private Aspect[] getPresentAspects() {
        if (aspects == null || aspects.size() <= 0) {
            return new Aspect[0];
        }
        return aspects.getAspects();
    }

    public int takeFromPort(int portIndex, Aspect aspect, int amount) {
        Aspect portAspect = getAspectForPort(portIndex);
        if (aspect == null || portAspect == null || aspect != portAspect || amount <= 0) {
            return 0;
        }
        int available = Math.min(amount, getAmountForPort(portIndex));
        if (available <= 0 || !takeFromContainer(aspect, available)) {
            return 0;
        }
        markDirtyAndSync();
        return available;
    }

    @Override
    public AspectList getAspects() {
        return aspects.copy();
    }

    @Override
    public void setAspects(AspectList aspects) {
        this.aspects = aspects == null ? new AspectList() : aspects;
        recalcEssentiaAmount();
        markDirtyAndSync();
    }

    @Override
    public boolean doesContainerAccept(Aspect aspect) {
        return essentiaAmount < ESSENTIA_CAP;
    }

    @Override
    public int addToContainer(Aspect aspect, int amount) {
        int accepted = Math.min(amount, ESSENTIA_CAP - essentiaAmount);
        if (aspect != null && accepted > 0) {
            aspects.add(aspect, accepted);
            recalcEssentiaAmount();
            markDirtyAndSync();
        }
        return amount - accepted;
    }

    @Override
    public boolean takeFromContainer(Aspect aspect, int amount) {
        if (aspect == null || amount <= 0 || aspects.getAmount(aspect) < amount) {
            return false;
        }
        aspects.remove(aspect, amount);
        recalcEssentiaAmount();
        markDirtyAndSync();
        return true;
    }

    @Override
    public boolean takeFromContainer(AspectList request) {
        if (!doesContainerContain(request)) {
            return false;
        }
        for (Aspect aspect : request.getAspects()) {
            aspects.remove(aspect, request.getAmount(aspect));
        }
        recalcEssentiaAmount();
        markDirtyAndSync();
        return true;
    }

    @Override
    public boolean doesContainerContainAmount(Aspect aspect, int amount) {
        return aspects.getAmount(aspect) >= amount;
    }

    @Override
    public boolean doesContainerContain(AspectList request) {
        if (request == null) {
            return true;
        }
        for (Aspect aspect : request.getAspects()) {
            if (aspects.getAmount(aspect) < request.getAmount(aspect)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int containerContains(Aspect aspect) {
        return aspects.getAmount(aspect);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setTag("Input", input.serializeNBT());
        aspects.writeToNBT(compound, "Essentia");
        compound.setInteger("EssentiaAmount", essentiaAmount);
        compound.setInteger("CookTime", cookTime);
        compound.setInteger("SmeltTime", smeltTime);
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        input.deserializeNBT(compound.getCompoundTag("Input"));
        aspects = new AspectList();
        aspects.readFromNBT(compound, "Essentia");
        recalcEssentiaAmount();
        cookTime = compound.getInteger("CookTime");
        smeltTime = compound.hasKey("SmeltTime") ? compound.getInteger("SmeltTime") : 100;
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
    }

    private void markDirtyAndSync() {
        markDirty();
        if (world != null) {
            IBlockStateUpdate.notify(world, pos);
        }
    }

    @Override
    public void registerControllers(AnimationData data) {
        data.addAnimationController(new AnimationController<>(this, "controller", 0, this::predicate));
    }

    private <E extends IAnimatable> PlayState predicate(AnimationEvent<E> event) {
        event.getController().setAnimation(new AnimationBuilder().addAnimation("animation", true));
        return PlayState.CONTINUE;
    }

    @Override
    public AnimationFactory getFactory() {
        return factory;
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return new AxisAlignedBB(pos).grow(2.0D, 1.0D, 2.0D);
    }

    private static final class IBlockStateUpdate {
        private static void notify(net.minecraft.world.World world, BlockPos pos) {
            net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
        }
    }
}
