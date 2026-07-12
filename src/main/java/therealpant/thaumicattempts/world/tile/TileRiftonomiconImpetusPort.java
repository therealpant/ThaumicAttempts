package therealpant.thaumicattempts.world.tile;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import therealpant.thaumicattempts.integration.thaumicaugmentation.ImpetusCompat;

import javax.annotation.Nullable;

/** One of four physical impetus connection points belonging to a Riftonomicon. */
public class TileRiftonomiconImpetusPort extends TileEntity {
    public static final long BUFFER_CAPACITY = 200000L;
    private Object impetusNode;

    @Override
    public void onLoad() {
        super.onLoad();
        ensureImpetusNode(true);
    }

    @Override
    public void invalidate() {
        if (ImpetusCompat.isLoaded()) {
            ImpetusCompat.syncDestroyedNode(impetusNode, world);
            ImpetusCompat.deregisterRenderableNode(impetusNode, world);
            ImpetusCompat.unloadNode(impetusNode);
            ImpetusCompat.destroyNode(impetusNode);
        }
        impetusNode = null;
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        if (ImpetusCompat.isLoaded()) {
            ImpetusCompat.deregisterRenderableNode(impetusNode, world);
            ImpetusCompat.unloadNode(impetusNode);
        }
        impetusNode = null;
        super.onChunkUnload();
    }

    @Nullable
    public TileRiftonomicon getRiftonomicon() {
        if (world == null) return null;
        for (EnumFacing facing : EnumFacing.HORIZONTALS) {
            BlockPos corePos = pos.offset(facing, 2);
            TileEntity tile = world.getTileEntity(corePos);
            if (tile instanceof TileRiftonomicon) return (TileRiftonomicon) tile;
        }
        return null;
    }

    public long consume(long requested, boolean simulate) {
        if (getRiftonomicon() == null || requested <= 0L) return 0L;
        ensureImpetusNode(false);
        ImpetusCompat.validateNode(impetusNode, world);
        return ImpetusCompat.consumeFromNode(impetusNode, world, requested, simulate);
    }

    private void ensureImpetusNode(boolean syncFully) {
        if (!ImpetusCompat.isLoaded() || world == null) return;
        if (impetusNode == null) {
            impetusNode = ImpetusCompat.createBufferedConsumerNode(this, 1, 0, BUFFER_CAPACITY);
        }
        ImpetusCompat.updateNodeLocation(impetusNode, world, pos);
        ImpetusCompat.initNode(impetusNode, world);
        ImpetusCompat.registerRenderableNode(impetusNode, world);
        if (syncFully) ImpetusCompat.syncNodeFully(impetusNode, world);
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        Capability<?> impetus = ImpetusCompat.getImpetusNodeCapability();
        if (impetus != null && capability == impetus && getRiftonomicon() != null) return true;
        return super.hasCapability(capability, facing);
    }

    @Nullable
    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        Capability<?> impetus = ImpetusCompat.getImpetusNodeCapability();
        if (impetus != null && capability == impetus && getRiftonomicon() != null) {
            ensureImpetusNode(false);
            @SuppressWarnings("unchecked") T cast = (T) impetusNode;
            return cast;
        }
        return super.getCapability(capability, facing);
    }
}
