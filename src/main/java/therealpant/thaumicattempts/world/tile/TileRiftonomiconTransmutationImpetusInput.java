package therealpant.thaumicattempts.world.tile;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import therealpant.thaumicattempts.integration.thaumicaugmentation.ImpetusCompat;

import javax.annotation.Nullable;

public class TileRiftonomiconTransmutationImpetusInput extends TileEntity {
    private Object node;

    private TileRiftonomicon getCore() {
        TileEntity tile = world == null ? null : world.getTileEntity(pos.up(3));
        return tile instanceof TileRiftonomicon ? (TileRiftonomicon) tile : null;
    }

    @Override public void onLoad() { super.onLoad(); ensureNode(true); }
    @Override public void invalidate() { cleanup(true); super.invalidate(); }
    @Override public void onChunkUnload() { cleanup(false); super.onChunkUnload(); }

    private void cleanup(boolean destroy) {
        ImpetusCompat.deregisterRenderableNode(node, world);
        if (destroy) ImpetusCompat.syncDestroyedNode(node, world);
        ImpetusCompat.unloadNode(node);
        if (destroy) ImpetusCompat.destroyNode(node);
        node = null;
    }

    private void ensureNode(boolean sync) {
        if (!ImpetusCompat.isLoaded() || world == null) return;
        if (node == null) node = ImpetusCompat.createBufferedConsumerNode(this, 1, 0, 200000L);
        ImpetusCompat.updateNodeLocation(node, world, pos);
        ImpetusCompat.initNode(node, world);
        ImpetusCompat.registerRenderableNode(node, world);
        if (sync) ImpetusCompat.syncNodeFully(node, world);
    }

    public long consume(long amount, boolean simulate) {
        ensureNode(false);
        ImpetusCompat.validateNode(node, world);
        return ImpetusCompat.consumeFromNode(node, world, amount, simulate);
    }

    @Override public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        Capability<?> cap = ImpetusCompat.getImpetusNodeCapability();
        return cap != null && capability == cap && getCore() != null || super.hasCapability(capability, facing);
    }

    @Nullable @Override public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        Capability<?> cap = ImpetusCompat.getImpetusNodeCapability();
        if (cap != null && capability == cap && getCore() != null) {
            ensureNode(false);
            @SuppressWarnings("unchecked") T cast = (T) node;
            return cast;
        }
        return super.getCapability(capability, facing);
    }
}
