package therealpant.thaumicattempts.world.tile;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

public class TileAnomalyCrop extends TileEntity {
    private static final String NBT_MIN_VIS = "MinVisDuringGrowth";

    private float minVisDuringGrowth = Float.MAX_VALUE;

    public float getMinVisDuringGrowth() {
        return minVisDuringGrowth;
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
    }
}