package therealpant.thaumicattempts.world.tile;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraftforge.common.capabilities.Capability;
import therealpant.thaumicattempts.integration.thaumicaugmentation.ImpetusCompat;
import therealpant.thaumicattempts.data.research.TAAspects;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.common.lib.events.EssentiaHandler;

import javax.annotation.Nullable;

public class TileRiftonomiconTransmutationImpetusInput extends TileEntity implements ITickable, IEssentiaTransport {
    private Object node;
    private int drawDelay;

    private TileRiftonomicon getCore() {
        TileEntity tile = world == null ? null : world.getTileEntity(pos.down());
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

    @Override
    public void update() {
        if (world == null || world.isRemote || ++drawDelay % 5 != 0) return;
        TileRiftonomicon core = getCore();
        Aspect aspect = requiredAspect();
        if (core != null && aspect != null && canAcceptParadoxum(core)
                && EssentiaHandler.drainEssentia(this, aspect, null, 12, 0)) {
            acceptParadoxum(core, 1);
        }
    }

    private boolean canAcceptParadoxum(TileRiftonomicon core) {
        return core != null && (core.canAcceptCartridgeRecoveryParadoxum()
                || core.canAcceptCreationExtractorProductionParadoxum()
                || !ImpetusCompat.isLoaded() && core.canAcceptCreationExtractorParadoxum());
    }

    private int acceptParadoxum(TileRiftonomicon core, int amount) {
        if (core == null || amount <= 0) return 0;
        boolean fallbackFuelTurn = !ImpetusCompat.isLoaded() && drawDelay % 20 == 0
                && core.canAcceptCreationExtractorParadoxum();
        if (fallbackFuelTurn) return core.acceptCreationExtractorParadoxum(amount);
        if (core.canAcceptCartridgeRecoveryParadoxum()) {
            return core.acceptCartridgeRecoveryParadoxum(amount);
        }
        if (core.canAcceptCreationExtractorProductionParadoxum()) {
            return core.acceptCreationExtractorProductionParadoxum(amount);
        }
        return !ImpetusCompat.isLoaded() ? core.acceptCreationExtractorParadoxum(amount) : 0;
    }

    private Aspect requiredAspect() {
        return TAAspects.PARADOXUM != null ? TAAspects.PARADOXUM : Aspect.getAspect("paradoxum");
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

    @Override public boolean isConnectable(EnumFacing face) { return true; }
    @Override public boolean canInputFrom(EnumFacing face) { return true; }
    @Override public boolean canOutputTo(EnumFacing face) { return false; }
    @Override public void setSuction(Aspect aspect, int amount) {}
    @Override public Aspect getSuctionType(EnumFacing face) {
        TileRiftonomicon core = getCore();
        return canAcceptParadoxum(core) ? requiredAspect() : null;
    }
    @Override public int getSuctionAmount(EnumFacing face) { return getSuctionType(face) == null ? 0 : 128; }
    @Override public int addEssentia(Aspect aspect, int amount, EnumFacing face) {
        TileRiftonomicon core = getCore();
        return core == null || aspect != requiredAspect() ? 0 : acceptParadoxum(core, amount);
    }
    @Override public int takeEssentia(Aspect aspect, int amount, EnumFacing face) { return 0; }
    @Override public Aspect getEssentiaType(EnumFacing face) { return null; }
    @Override public int getEssentiaAmount(EnumFacing face) { return 0; }
    @Override public int getMinimumSuction() { return 0; }
}
