package therealpant.thaumicattempts.world.tile;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import therealpant.thaumicattempts.world.block.BlockAnomalyBed;

import javax.annotation.Nullable;

public class TileAnomalyCrop extends TileEntity {
    private static final String NBT_MIN_VIS = "MinVisDuringGrowth";
    private static final String NBT_BED_STATE = "BedState";

    private float minVisDuringGrowth = Float.MAX_VALUE;
    @Nullable
    private BlockAnomalyBed.BedState bedState;

    public float getMinVisDuringGrowth() {
        return minVisDuringGrowth;
    }

    @Nullable
    public BlockAnomalyBed.BedState getBedState() {
        return bedState;
    }

    public void setBedState(BlockAnomalyBed.BedState bedState) {
        this.bedState = bedState;
        markDirty();
    }

    public void setMinVisDuringGrowth(float minVisDuringGrowth) {
        this.minVisDuringGrowth = minVisDuringGrowth;
        markDirty();
    }

    public void resetMinVis() {
        setMinVisDuringGrowth(Float.MAX_VALUE);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setFloat(NBT_MIN_VIS, minVisDuringGrowth);
        if (bedState != null) {
            compound.setInteger(NBT_BED_STATE, bedState.ordinal());
        }
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        if (compound.hasKey(NBT_MIN_VIS)) {
            minVisDuringGrowth = compound.getFloat(NBT_MIN_VIS);
        } else {
            minVisDuringGrowth = Float.MAX_VALUE;
        }
        if (compound.hasKey(NBT_BED_STATE)) {
            int ordinal = compound.getInteger(NBT_BED_STATE);
            BlockAnomalyBed.BedState[] values = BlockAnomalyBed.BedState.values();
            bedState = (ordinal >= 0 && ordinal < values.length) ? values[ordinal] : null;
        } else {
            bedState = null;
        }
    }
}