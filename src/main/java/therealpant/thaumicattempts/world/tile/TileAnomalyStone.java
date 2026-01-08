package therealpant.thaumicattempts.world.tile;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
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

public class TileAnomalyStone extends TileEntity implements IAnimatable, AnomalyLinkedTile {

    private final AnimationFactory factory = new AnimationFactory(this);
    private UUID anomalyId;
    private BlockPos seedPos;
    private int reproduceCooldown;

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
                new AnimationBuilder().addAnimation("rift_stone.animation", true)
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
        return compound;
    }
}