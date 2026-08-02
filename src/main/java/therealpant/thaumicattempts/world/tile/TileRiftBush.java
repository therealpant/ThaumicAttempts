package therealpant.thaumicattempts.world.tile;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.state.IBlockState;
import therealpant.thaumicattempts.world.block.BlockRiftBush;

import javax.annotation.Nullable;
import java.util.UUID;

public class TileRiftBush extends TileEntity implements AnomalyLinkedTile, ITickable {

    private UUID anomalyId;
    private BlockPos seedPos;
    private int reproduceCooldown;
    private long harvestReadyAt;

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
        this.harvestReadyAt = harvestReadyAt;
        markDirty();
    }

    @Override
    public void update() {
        if (world == null || world.isRemote) return;

        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockRiftBush)
                || state.getValue(BlockRiftBush.HALF) != BlockRiftBush.BlockHalf.LOWER) {
            return;
        }

        BlockRiftBush bush = (BlockRiftBush) state.getBlock();
        if (state.getValue(BlockRiftBush.HARVEST_STATE) == BlockRiftBush.HarvestState.READY) {
            if (harvestReadyAt != 0L) {
                harvestReadyAt = 0L;
                markDirty();
            }
            return;
        }

        if (harvestReadyAt <= 0L) {
            harvestReadyAt = world.getTotalWorldTime() + BlockRiftBush.REGROW_TICKS;
            markDirty();
            return;
        }

        if (world.getTotalWorldTime() >= harvestReadyAt) {
            harvestReadyAt = 0L;
            bush.setHarvestState(world, pos, BlockRiftBush.HarvestState.READY);
            markDirty();
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
        return compound;
    }
}
