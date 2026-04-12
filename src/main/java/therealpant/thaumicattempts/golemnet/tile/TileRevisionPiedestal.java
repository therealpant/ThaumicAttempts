package therealpant.thaumicattempts.golemnet.tile;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;

public class TileRevisionPiedestal extends TileEntity implements IAnimatable {

    private static final String TAG_ACTIVE = "Active";

    private final AnimationFactory factory = new AnimationFactory(this);
    private boolean active = true;

    public boolean isActive() {
        return active;
    }

    public void toggleActive() {
        setActive(!active);
    }

    public void setActive(boolean active) {
        this.active = active;
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
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setBoolean(TAG_ACTIVE, active);
        return compound;
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
