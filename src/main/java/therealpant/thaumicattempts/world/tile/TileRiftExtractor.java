package therealpant.thaumicattempts.world.tile;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import software.bernie.geckolib3.core.AnimationState;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.init.ModBlocksItems;
import therealpant.thaumicattempts.items.ItemCalibratedExtract;

import javax.annotation.Nullable;

/** Extracts the item encoded in a Creation-protocol calibrated extract. */
public class TileRiftExtractor extends TileEntity implements ITickable, IAnimatable {
    private static final int SLOT_CROWN = 0;
    private static final int SLOT_CORE = 1;
    private static final int TICKS_PER_STAGE = 40;
    private static final int SIGNATURES_PER_STAGE = 200;
    private static final long IMPETUS_PER_STAGE = 250L;
    private static final long MIN_DRAW_PER_TICK = 8L;

    private final AnimationFactory factory = new AnimationFactory(this);
    private final ItemStackHandler inventory = new ItemStackHandler(2) {
        @Override protected void onContentsChanged(int slot) { markDirtyAndSync(); }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return slot == SLOT_CROWN && getSpec(stack) != null;
        }
        @Override public int getSlotLimit(int slot) { return 1; }
    };
    private final IItemHandler coreHandler = new CoreHandler();

    private int stage;
    private int stageMax;
    private int ticksToNextStage;
    private long stageFuelNeed;
    private long stageFuelPaid;
    private int fuelPulseTicks;
    private ItemStack cachedCoreRenderStack = ItemStack.EMPTY;

    private AnimVisualState animState = AnimVisualState.SLIP;
    @Nullable private AnimVisualState lastSentAnim;

    private enum AnimVisualState { SLIP, ACTIV_PLUS, WORK, ACTIV_MINUS }

    private static final class ExtractionSpec {
        private final ItemStack result;
        private final int stages;
        private ExtractionSpec(ItemStack result, int stages) {
            this.result = result;
            this.stages = stages;
        }
    }

    @Nullable
    private ExtractionSpec getSpec(ItemStack crown) {
        if (crown == null || crown.isEmpty() || crown.getItem() != ModBlocksItems.CALIBRATED_EXTRACT) return null;
        ItemStack result = ItemCalibratedExtract.getTarget(crown);
        if (result.isEmpty()) return null;
        int cost = ItemCalibratedExtract.getSignatureCost(crown);
        int stages = Math.max(1, (cost + SIGNATURES_PER_STAGE - 1) / SIGNATURES_PER_STAGE);
        return new ExtractionSpec(result, stages);
    }

    @Nullable
    private TileRiftonomicon findLinkedRiftonomicon() {
        if (world == null) return null;
        TileRiftonomicon best = null;
        double bestDistance = Double.MAX_VALUE;
        for (TileEntity tile : world.loadedTileEntityList) {
            if (!(tile instanceof TileRiftonomicon)) continue;
            TileRiftonomicon core = (TileRiftonomicon) tile;
            double distance = core.getPos().distanceSq(pos);
            if (!core.isCreationExtractorLinked(pos)) continue;
            if (distance < bestDistance || distance == bestDistance
                    && (best == null || core.getPos().toLong() < best.getPos().toLong())) {
                best = core;
                bestDistance = distance;
            }
        }
        return best;
    }

    @Override
    public void update() {
        if (world == null) return;
        if (world.isRemote) {
            if (fuelPulseTicks > 0) fuelPulseTicks--;
            return;
        }
        if (world.isBlockPowered(pos)) return;

        ExtractionSpec spec = getSpec(inventory.getStackInSlot(SLOT_CROWN));
        if (spec == null) {
            resetProgress();
            return;
        }
        cachedCoreRenderStack = spec.result.copy();
        if (stageMax != spec.stages) {
            if (stage > 0 || ticksToNextStage > 0) resetProgress();
            stageMax = spec.stages;
        }
        if (!inventory.getStackInSlot(SLOT_CORE).isEmpty()) return;
        if (stage >= stageMax) {
            inventory.setStackInSlot(SLOT_CORE, spec.result.copy());
            markDirtyAndSync();
            return;
        }

        TileRiftonomicon core = findLinkedRiftonomicon();
        if (core == null) return;
        if (stageFuelNeed <= 0L) {
            stageFuelNeed = IMPETUS_PER_STAGE;
            stageFuelPaid = 0L;
            ticksToNextStage = 0;
        }
        long remaining = stageFuelNeed - stageFuelPaid;
        if (remaining > 0L) {
            int ticksLeft = Math.max(1, TICKS_PER_STAGE - ticksToNextStage);
            long wanted = Math.max(MIN_DRAW_PER_TICK, (long) Math.ceil(remaining / (double) ticksLeft));
            wanted = Math.min(wanted, remaining);
            long received = core.consumeCreationExtractorFuel(wanted, false);
            if (received <= 0L) return;
            stageFuelPaid += received;
            fuelPulseTicks = 10;
        }
        if (ticksToNextStage < TICKS_PER_STAGE) ticksToNextStage++;
        if (ticksToNextStage < TICKS_PER_STAGE || stageFuelPaid < stageFuelNeed) {
            markDirtyAndSync();
            return;
        }

        stage++;
        ticksToNextStage = 0;
        stageFuelNeed = 0L;
        stageFuelPaid = 0L;
        if (stage >= stageMax) inventory.setStackInSlot(SLOT_CORE, spec.result.copy());
        markDirtyAndSync();
    }

    public ItemStack getCrownStack() { return inventory.getStackInSlot(SLOT_CROWN); }
    public ItemStack getCoreStack() { return inventory.getStackInSlot(SLOT_CORE); }
    public int getStage() { return stage; }

    public float getCoreAlpha() {
        int max = resolveStageMax();
        return MathHelper.clamp(stage / (float) max, 0.0F, 1.0F);
    }

    @SideOnly(Side.CLIENT)
    public float getCoreAlphaSmooth(float partialTicks) {
        if (stage <= 0 && ticksToNextStage <= 0) return 0.0F;
        float progress = MathHelper.clamp((ticksToNextStage + partialTicks) / TICKS_PER_STAGE, 0.0F, 1.0F);
        float value = MathHelper.clamp((stage + progress) / resolveStageMax(), 0.0F, 1.0F);
        return value * value * (3.0F - 2.0F * value);
    }

    private int resolveStageMax() {
        if (stageMax > 0) return stageMax;
        ExtractionSpec spec = getSpec(inventory.getStackInSlot(SLOT_CROWN));
        return spec == null ? 1 : Math.max(1, spec.stages);
    }

    public ItemStack getCoreRenderStack() {
        ItemStack core = inventory.getStackInSlot(SLOT_CORE);
        if (!core.isEmpty()) return core;
        if (stage > 0 || ticksToNextStage > 0) {
            ExtractionSpec spec = getSpec(inventory.getStackInSlot(SLOT_CROWN));
            if (spec != null) {
                cachedCoreRenderStack = spec.result.copy();
                return spec.result;
            }
            return cachedCoreRenderStack;
        }
        return ItemStack.EMPTY;
    }

    public boolean tryInsertCrown(EntityPlayer player, EnumHand hand) {
        if (player == null || hand == null) return false;
        ItemStack held = player.getHeldItem(hand);
        if (getSpec(held) == null) return false;
        if (!inventory.getStackInSlot(SLOT_CROWN).isEmpty() && !tryExtractCrown(player)) return false;
        ItemStack inserted = held.copy();
        inserted.setCount(1);
        inventory.setStackInSlot(SLOT_CROWN, inserted);
        if (!player.capabilities.isCreativeMode) held.shrink(1);
        markDirtyAndSync();
        return true;
    }

    public boolean tryExtractCrown(EntityPlayer player) {
        if (player == null) return false;
        ItemStack extracted = inventory.extractItem(SLOT_CROWN, 1, false);
        if (extracted.isEmpty()) return false;
        if (stage < stageMax) resetProgress();
        giveToPlayer(player, extracted);
        markDirtyAndSync();
        return true;
    }

    public boolean tryExtractCore(EntityPlayer player) {
        if (player == null || stageMax <= 0 || stage < stageMax) return false;
        ItemStack extracted = inventory.extractItem(SLOT_CORE, 1, false);
        if (extracted.isEmpty()) return false;
        giveToPlayer(player, extracted);
        if (inventory.getStackInSlot(SLOT_CORE).isEmpty()) resetProgress();
        markDirtyAndSync();
        return true;
    }

    private void giveToPlayer(EntityPlayer player, ItemStack stack) {
        if (!player.inventory.addItemStackToInventory(stack) && world != null) {
            net.minecraft.inventory.InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), stack);
        }
    }

    private void resetProgress() {
        stage = 0;
        stageMax = 0;
        ticksToNextStage = 0;
        stageFuelNeed = 0L;
        stageFuelPaid = 0L;
        cachedCoreRenderStack = ItemStack.EMPTY;
    }

    private boolean shouldWorkForAnimation() {
        if (world == null || world.isBlockPowered(pos) || !inventory.getStackInSlot(SLOT_CORE).isEmpty()) return false;
        ExtractionSpec spec = getSpec(inventory.getStackInSlot(SLOT_CROWN));
        return spec != null && stage < spec.stages && findLinkedRiftonomicon() != null;
    }

    private void applyAnimation(AnimationController<?> controller, AnimVisualState state, boolean restart) {
        if (restart) {
            controller.markNeedsReload();
            lastSentAnim = null;
        }
        if (lastSentAnim == state) return;
        lastSentAnim = state;
        switch (state) {
            case ACTIV_PLUS: controller.setAnimation(new AnimationBuilder().addAnimation("activ+", false)); break;
            case WORK: controller.setAnimation(new AnimationBuilder().addAnimation("work", false)); break;
            case ACTIV_MINUS: controller.setAnimation(new AnimationBuilder().addAnimation("activ-", false)); break;
            default: controller.setAnimation(new AnimationBuilder().addAnimation("slip", false));
        }
    }

    private <E extends IAnimatable> PlayState predicate(AnimationEvent<E> event) {
        AnimationController<?> controller = event.getController();
        AnimVisualState desired = shouldWorkForAnimation() ? AnimVisualState.WORK : AnimVisualState.SLIP;
        if (animState == AnimVisualState.ACTIV_PLUS || animState == AnimVisualState.ACTIV_MINUS) {
            applyAnimation(controller, animState, false);
            if (controller.getAnimationState() == AnimationState.Stopped) {
                animState = animState == AnimVisualState.ACTIV_PLUS ? AnimVisualState.WORK : AnimVisualState.SLIP;
                applyAnimation(controller, animState, true);
            }
            return PlayState.CONTINUE;
        }
        applyAnimation(controller, animState, false);
        if (controller.getAnimationState() == AnimationState.Stopped) {
            animState = animState == desired ? desired
                    : desired == AnimVisualState.WORK ? AnimVisualState.ACTIV_PLUS : AnimVisualState.ACTIV_MINUS;
            applyAnimation(controller, animState, true);
        }
        return PlayState.CONTINUE;
    }

    @Override public void registerControllers(AnimationData data) {
        data.addAnimationController(new AnimationController<>(this, "controller", 0, this::predicate));
    }
    @Override public AnimationFactory getFactory() { return factory; }

    @Override public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || super.hasCapability(capability, facing);
    }

    @Nullable @Override public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(coreHandler);
        }
        return super.getCapability(capability, facing);
    }

    private void markDirtyAndSync() {
        markDirty();
        if (world != null) world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        if (tag.hasKey("Inventory")) inventory.deserializeNBT(tag.getCompoundTag("Inventory"));
        stage = tag.getInteger("Stage");
        stageMax = tag.getInteger("StageMax");
        ticksToNextStage = tag.getInteger("TicksToNextStage");
        stageFuelNeed = tag.getLong("StageImpNeed");
        stageFuelPaid = tag.getLong("StageImpPaid");
        fuelPulseTicks = tag.getInteger("ImpetusPulse");
        cachedCoreRenderStack = tag.hasKey("CachedCoreRenderStack")
                ? new ItemStack(tag.getCompoundTag("CachedCoreRenderStack")) : ItemStack.EMPTY;
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setTag("Inventory", inventory.serializeNBT());
        tag.setInteger("Stage", stage);
        tag.setInteger("StageMax", stageMax);
        tag.setInteger("TicksToNextStage", ticksToNextStage);
        tag.setLong("StageImpNeed", stageFuelNeed);
        tag.setLong("StageImpPaid", stageFuelPaid);
        tag.setInteger("ImpetusPulse", fuelPulseTicks);
        if (!cachedCoreRenderStack.isEmpty()) tag.setTag("CachedCoreRenderStack", cachedCoreRenderStack.serializeNBT());
        return tag;
    }

    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        readFromNBT(packet.getNbtCompound());
    }

    private class CoreHandler implements IItemHandler {
        @Override public int getSlots() { return 1; }
        @Override public ItemStack getStackInSlot(int slot) { return inventory.getStackInSlot(SLOT_CORE); }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack == null ? ItemStack.EMPTY : stack;
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (stageMax <= 0 || stage < stageMax || amount <= 0) return ItemStack.EMPTY;
            ItemStack extracted = inventory.extractItem(SLOT_CORE, amount, simulate);
            if (!simulate && !extracted.isEmpty() && inventory.getStackInSlot(SLOT_CORE).isEmpty()) resetProgress();
            return extracted;
        }
        @Override public int getSlotLimit(int slot) { return 1; }
    }
}
