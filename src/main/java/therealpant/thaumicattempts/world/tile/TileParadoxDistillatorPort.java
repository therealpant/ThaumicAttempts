package therealpant.thaumicattempts.world.tile;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IEssentiaTransport;

import javax.annotation.Nullable;

public class TileParadoxDistillatorPort extends TileEntity implements IEssentiaTransport {
    private BlockPos controllerPos;

    public void setControllerPos(BlockPos controllerPos) {
        if (controllerPos != null && !controllerPos.equals(this.controllerPos)) {
            this.controllerPos = controllerPos.toImmutable();
            markDirty();
        }
    }

    @Nullable
    private TileParadoxDistillator getController() {
        if (world == null) return null;
        BlockPos controller = controllerPos == null ? pos.down() : controllerPos;
        if (!world.isBlockLoaded(controller)) return null;
        TileEntity tile = world.getTileEntity(controller);
        return tile instanceof TileParadoxDistillator
                ? (TileParadoxDistillator) tile : null;
    }

    @Override public boolean isConnectable(EnumFacing face) { return face == EnumFacing.UP; }
    @Override public boolean canInputFrom(EnumFacing face) { return false; }
    @Override public boolean canOutputTo(EnumFacing face) { return face == EnumFacing.UP; }
    @Override public void setSuction(Aspect aspect, int amount) {}
    @Override public Aspect getSuctionType(EnumFacing face) { return null; }
    @Override public int getSuctionAmount(EnumFacing face) { return 0; }
    @Override public int getMinimumSuction() { return 0; }
    @Override public int addEssentia(Aspect aspect, int amount, EnumFacing face) { return 0; }

    @Override
    public int takeEssentia(Aspect aspect, int amount, EnumFacing face) {
        TileParadoxDistillator controller = getController();
        return face == EnumFacing.UP && controller != null
                ? controller.takeEssentia(aspect, amount) : 0;
    }

    @Override
    public Aspect getEssentiaType(EnumFacing face) {
        TileParadoxDistillator controller = getController();
        return face == EnumFacing.UP && controller != null
                ? controller.getEssentiaType() : null;
    }

    @Override
    public int getEssentiaAmount(EnumFacing face) {
        TileParadoxDistillator controller = getController();
        return face == EnumFacing.UP && controller != null
                ? controller.getEssentiaAmount() : 0;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (controllerPos != null) tag.setLong("Controller", controllerPos.toLong());
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        controllerPos = tag.hasKey("Controller", 4)
                ? BlockPos.fromLong(tag.getLong("Controller")) : null;
    }
}
