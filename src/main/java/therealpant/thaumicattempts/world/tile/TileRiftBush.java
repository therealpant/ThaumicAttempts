package therealpant.thaumicattempts.world.tile;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.UUID;

public class TileRiftBush extends TileEntity implements AnomalyLinkedTile {

    private UUID anomalyId;
    private BlockPos seedPos;

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
        return compound;
    }
}