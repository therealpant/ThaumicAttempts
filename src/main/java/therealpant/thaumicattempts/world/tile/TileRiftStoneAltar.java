package therealpant.thaumicattempts.world.tile;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;

import javax.annotation.Nullable;

public class TileRiftStoneAltar extends TileEntity implements IAnimatable {
    private static final String ANIM_ALTAR = "animation";
    private static final int LOOP_END_TICKS = 170;
    private static final int LOOP_REWIND_TICKS = 120;
    private static final int FULL_ANIMATION_TICKS = 240;

    private final AnimationFactory factory = new AnimationFactory(this);

    private boolean working;
    private boolean animationApplied;
    private boolean animationRunning;
    private double previousAnimationTick = -1.0D;

    public void toggleWorking() {
        setWorking(!working);
    }

    public boolean isWorking() {
        return working;
    }

    private void setWorking(boolean working) {
        if (this.working == working) {
            return;
        }
        this.working = working;
        if (working) {
            animationApplied = false;
            animationRunning = true;
        }
        markDirtyAndSync();
    }

    @Override
    public void registerControllers(AnimationData data) {
        data.addAnimationController(new AnimationController<>(this, "controller", 0, this::predicate));
    }

    private <E extends IAnimatable> PlayState predicate(AnimationEvent<E> event) {
        AnimationController<?> controller = event.getController();

        if (!working && !animationRunning) {
            animationApplied = false;
            return PlayState.STOP;
        }

        if (!animationApplied) {
            animationApplied = true;
            animationRunning = true;
            previousAnimationTick = -1.0D;
            controller.markNeedsReload();
            controller.setAnimation(new AnimationBuilder().addAnimation(ANIM_ALTAR, false));
            return PlayState.CONTINUE;
        }

        double animationTick = getAnimationTick(controller, event.getAnimationTick());
        if (working && animationTick >= LOOP_END_TICKS) {
            rewindAnimation(controller);
            previousAnimationTick = LOOP_END_TICKS - LOOP_REWIND_TICKS;
            return PlayState.CONTINUE;
        }

        if (!working && animationTick >= FULL_ANIMATION_TICKS) {
            animationRunning = false;
            animationApplied = false;
            previousAnimationTick = -1.0D;
            return PlayState.STOP;
        }

        previousAnimationTick = animationTick;
        return PlayState.CONTINUE;
    }

    private double getAnimationTick(AnimationController<?> controller, double eventTick) {
        double animationSpeed = controller.getAnimationSpeed();
        if (animationSpeed == 0.0D) {
            animationSpeed = 1.0D;
        }
        return animationSpeed * Math.max(eventTick - controller.tickOffset, 0.0D);
    }

    private void rewindAnimation(AnimationController<?> controller) {
        double animationSpeed = controller.getAnimationSpeed();
        if (animationSpeed == 0.0D) {
            animationSpeed = 1.0D;
        }
        controller.tickOffset += LOOP_REWIND_TICKS / animationSpeed;
    }

    @Override
    public AnimationFactory getFactory() {
        return factory;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        boolean previousWorking = working;
        working = compound.getBoolean("Working");
        if (working && !previousWorking) {
            animationApplied = false;
            animationRunning = true;
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setBoolean("Working", working);
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
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return new AxisAlignedBB(pos).grow(1.0D, 1.0D, 1.0D);
    }

    private void markDirtyAndSync() {
        markDirty();
        if (world != null) {
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        }
    }
}
