package therealpant.thaumicattempts.golemnet.tile;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;

public class TileRevisionPiedestal extends TileEntity implements IAnimatable {

    private static final String TAG_ACTIVE = "Active";
    private static final String TAG_COUNTER = "Counter";
    private static final String TAG_ITEM = "Item";


    private final AnimationFactory factory = new AnimationFactory(this);
    private boolean active = true;
    private ItemStack pedestalItem = ItemStack.EMPTY;
    private int counter = 1;

    public boolean isActive() {
        return active;
    }

    public void toggleActive() {
        setActive(!active);
    }

    public void setActive(boolean active) {
        this.active = active;
        markDirtyAndSync();
    }

    public ItemStack getPedestalItem() {
        return pedestalItem;
    }

    public int getCounter() {
        return counter;
    }

    public void cycleCounter() {
        counter++;
        if (counter > 16) {
            counter = 1;
        }
        markDirtyAndSync();
    }

    public boolean tryInsertFromHand(EntityPlayer player, EnumHand hand) {
        if (player == null || hand == null) return false;
        ItemStack held = player.getHeldItem(hand);
        if (held.isEmpty() || !pedestalItem.isEmpty()) return false;

        pedestalItem = held.copy();
        pedestalItem.setCount(1);
        if (!player.capabilities.isCreativeMode) {
            held.shrink(1);
        }
        markDirtyAndSync();
        return true;
    }

    public boolean tryExtractToPlayer(EntityPlayer player) {
        if (player == null || pedestalItem.isEmpty()) return false;

        ItemStack out = pedestalItem.copy();
        pedestalItem = ItemStack.EMPTY;
        if (!player.inventory.addItemStackToInventory(out) && world != null) {
            InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), out);
        }
        markDirtyAndSync();
        return true;
    }

    public void dropContents() {
        if (world == null || world.isRemote || pedestalItem.isEmpty()) return;
        InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), pedestalItem.copy());
        pedestalItem = ItemStack.EMPTY;
        markDirty();
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
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setBoolean(TAG_ACTIVE, active);
        compound.setInteger(TAG_COUNTER, counter);
        if (!pedestalItem.isEmpty()) {
            compound.setTag(TAG_ITEM, pedestalItem.writeToNBT(new NBTTagCompound()));
        }
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
