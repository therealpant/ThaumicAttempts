package therealpant.thaumicattempts.golemnet.tile;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.Objects;

public class TileCraftPlanner extends TileEntity {
    private static final String TAG_MANAGER = "manager";
    @Nullable
    private BlockPos managerPos;

    @Nullable
    public BlockPos getManagerPos() {
        return managerPos;
    }

    public void setManagerPos(@Nullable BlockPos managerPos) {
        BlockPos next = managerPos == null ? null : managerPos.toImmutable();
        if (!Objects.equals(this.managerPos, next)) {
            this.managerPos = next;
            markDirty();
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        if (managerPos != null) compound.setLong(TAG_MANAGER, managerPos.toLong());
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        managerPos = compound.hasKey(TAG_MANAGER) ? BlockPos.fromLong(compound.getLong(TAG_MANAGER)) : null;
    }
}