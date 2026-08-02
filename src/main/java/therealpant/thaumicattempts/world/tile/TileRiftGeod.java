package therealpant.thaumicattempts.world.tile;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;

import javax.annotation.Nullable;
import java.util.UUID;
import therealpant.thaumicattempts.world.block.BlockRiftGeod;

public class TileRiftGeod extends TileEntity implements IAnimatable, AnomalyLinkedTile, ITickable {

    private final AnimationFactory factory = new AnimationFactory(this);
    private UUID anomalyId;
    private BlockPos seedPos;
    private int reproduceCooldown;
    private long harvestReadyAt;
    private boolean collected;

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
                new AnimationBuilder().addAnimation("rift_geod.animation", true)
        );
        return PlayState.CONTINUE;
    }

    @Override
    public AnimationFactory getFactory() {
        return factory;
    }

    @Override
    public void setAnomalyLink(@Nullable UUID anomalyId, @Nullable BlockPos seedPos) {
        this.anomalyId = anomalyId;
        this.seedPos = seedPos == null ? null : seedPos.toImmutable();
        markDirty();
    }

    @Nullable
    @Override
    public UUID getAnomalyId() {
        return anomalyId;
    }

    @Nullable
    @Override
    public BlockPos getSeedPos() {
        return seedPos;
    }

    public int getReproduceCooldown() {
        return reproduceCooldown;
    }

    public void setReproduceCooldown(int reproduceCooldown) {
        this.reproduceCooldown = reproduceCooldown;
        markDirty();
    }

    public void startRegrowth(long harvestReadyAt) {
        this.collected = true;
        this.harvestReadyAt = harvestReadyAt;
        syncState();
    }

    public boolean isCollected() {
        return collected;
    }

    @Override
    public void update() {
        if (world == null || world.isRemote) return;

        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockRiftGeod)) return;

        if (!collected) {
            if (harvestReadyAt != 0L) {
                harvestReadyAt = 0L;
                markDirty();
            }
            return;
        }

        if (harvestReadyAt <= 0L) {
            harvestReadyAt = world.getTotalWorldTime() + BlockRiftGeod.REGROW_TICKS;
            markDirty();
            return;
        }

        if (world.getTotalWorldTime() >= harvestReadyAt) {
            harvestReadyAt = 0L;
            collected = false;
            syncState();
        }
    }

    private void syncState() {
        markDirty();
        if (world != null) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        if (compound.hasUniqueId("AnomalyId")) {
            anomalyId = compound.getUniqueId("AnomalyId");
        } else {
            anomalyId = null;
        }
        if (compound.hasKey("SeedX", 3) && compound.hasKey("SeedY", 3) && compound.hasKey("SeedZ", 3)) {
            seedPos = new BlockPos(compound.getInteger("SeedX"), compound.getInteger("SeedY"), compound.getInteger("SeedZ"));
        } else {
            seedPos = null;
        }
        reproduceCooldown = compound.getInteger("ReproduceCooldown");
        harvestReadyAt = compound.getLong("HarvestReadyAt");
        collected = compound.getBoolean("Collected");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        if (anomalyId != null) {
            compound.setUniqueId("AnomalyId", anomalyId);
        }
        if (seedPos != null) {
            compound.setInteger("SeedX", seedPos.getX());
            compound.setInteger("SeedY", seedPos.getY());
            compound.setInteger("SeedZ", seedPos.getZ());
        }
        compound.setInteger("ReproduceCooldown", reproduceCooldown);
        compound.setLong("HarvestReadyAt", harvestReadyAt);
        compound.setBoolean("Collected", collected);
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
}
