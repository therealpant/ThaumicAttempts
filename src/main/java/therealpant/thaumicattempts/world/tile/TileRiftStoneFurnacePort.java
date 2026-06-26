package therealpant.thaumicattempts.world.tile;

import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IEssentiaTransport;
import therealpant.thaumicattempts.init.TABlocks;
import therealpant.thaumicattempts.world.block.BlockRiftStoneFurnace;

import javax.annotation.Nullable;

public class TileRiftStoneFurnacePort extends TileEntity implements IEssentiaTransport {
    public static final int PORT_CAP = TileRiftStoneFurnace.PORT_CAP;

    @Nullable
    private TileRiftStoneFurnace getFurnace() {
        if (world == null || pos == null) {
            return null;
        }

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos candidate = pos.add(dx, -1, dz);
                IBlockState state = world.getBlockState(candidate);
                if (state.getBlock() == TABlocks.RIFT_STONE_FURNACE
                        && state.getValue(BlockRiftStoneFurnace.PART) == BlockRiftStoneFurnace.Part.CENTER_LOW) {
                    TileEntity tile = world.getTileEntity(candidate);
                    if (tile instanceof TileRiftStoneFurnace) {
                        return (TileRiftStoneFurnace) tile;
                    }
                }
            }
        }
        return null;
    }

    private int getPortIndex() {
        TileRiftStoneFurnace furnace = getFurnace();
        if (furnace == null) {
            return 0;
        }
        int dx = pos.getX() - furnace.getPos().getX();
        int dz = pos.getZ() - furnace.getPos().getZ();
        if (dx < 0 && dz < 0) return 0;
        if (dx > 0 && dz < 0) return 1;
        if (dx < 0 && dz > 0) return 2;
        return 3;
    }

    @Nullable
    public Aspect getPortAspect() {
        TileRiftStoneFurnace furnace = getFurnace();
        return furnace == null ? null : furnace.getAspectForPort(getPortIndex());
    }

    public int getPortAmount() {
        TileRiftStoneFurnace furnace = getFurnace();
        return furnace == null ? 0 : furnace.getAmountForPort(getPortIndex());
    }

    @Override
    public boolean isConnectable(EnumFacing face) {
        return face == EnumFacing.UP;
    }

    @Override
    public boolean canInputFrom(EnumFacing face) {
        return false;
    }

    @Override
    public boolean canOutputTo(EnumFacing face) {
        return face == EnumFacing.UP;
    }

    @Override
    public void setSuction(Aspect aspect, int amount) {
    }

    @Override
    public Aspect getSuctionType(EnumFacing face) {
        return null;
    }

    @Override
    public int getSuctionAmount(EnumFacing face) {
        return 0;
    }

    @Override
    public int takeEssentia(Aspect aspect, int amount, EnumFacing face) {
        if (!canOutputTo(face)) {
            return 0;
        }
        TileRiftStoneFurnace furnace = getFurnace();
        return furnace == null ? 0 : furnace.takeFromPort(getPortIndex(), aspect, amount);
    }

    @Override
    public int addEssentia(Aspect aspect, int amount, EnumFacing face) {
        return 0;
    }

    @Override
    public Aspect getEssentiaType(EnumFacing face) {
        return canOutputTo(face) ? getPortAspect() : null;
    }

    @Override
    public int getEssentiaAmount(EnumFacing face) {
        return canOutputTo(face) ? getPortAmount() : 0;
    }

    @Override
    public int getMinimumSuction() {
        return 0;
    }
}
