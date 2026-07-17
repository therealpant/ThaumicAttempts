package therealpant.thaumicattempts.world.tile;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import therealpant.thaumicattempts.integration.thaumicaugmentation.ImpetusCompat;
import therealpant.thaumicattempts.data.research.TAAspects;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.common.lib.events.EssentiaHandler;

import javax.annotation.Nullable;

/** One of four physical impetus connection points belonging to a Riftonomicon. */
public class TileRiftonomiconImpetusPort extends TileEntity implements ITickable, IEssentiaTransport {
    public static final long BUFFER_CAPACITY = 200000L;
    private Object impetusNode;
    private int essentiaDrawDelay;

    @Override
    public void update() {
        if (world == null || world.isRemote || ++essentiaDrawDelay % 5 != 0) return;
        TileRiftonomicon core = getRiftonomicon();
        int slot = core == null ? -1 : core.getCreationSlotForPort(pos);
        Aspect aspect = requiredAspect();
        if (core == null || aspect == null || !core.canAcceptCreationParadoxum(slot)) return;
        if (EssentiaHandler.drainEssentia(this, aspect, null, 12, 0)) {
            core.acceptCreationParadoxum(slot, 1);
        }
    }

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

    private Aspect requiredAspect() {
        return TAAspects.PARADOXUM != null ? TAAspects.PARADOXUM : Aspect.getAspect("paradoxum");
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

    @Override public boolean isConnectable(EnumFacing face) { return true; }
    @Override public boolean canInputFrom(EnumFacing face) { return true; }
    @Override public boolean canOutputTo(EnumFacing face) { return false; }
    @Override public void setSuction(Aspect aspect, int amount) {}
    @Override public Aspect getSuctionType(EnumFacing face) {
        TileRiftonomicon core = getRiftonomicon();
        int slot = core == null ? -1 : core.getCreationSlotForPort(pos);
        return core != null && core.canAcceptCreationParadoxum(slot) ? requiredAspect() : null;
    }
    @Override public int getSuctionAmount(EnumFacing face) { return getSuctionType(face) == null ? 0 : 128; }
    @Override public int addEssentia(Aspect aspect, int amount, EnumFacing face) {
        TileRiftonomicon core = getRiftonomicon();
        int slot = core == null ? -1 : core.getCreationSlotForPort(pos);
        return core == null || aspect != requiredAspect() ? 0 : core.acceptCreationParadoxum(slot, amount);
    }
    @Override public int takeEssentia(Aspect aspect, int amount, EnumFacing face) { return 0; }
    @Override public Aspect getEssentiaType(EnumFacing face) { return null; }
    @Override public int getEssentiaAmount(EnumFacing face) { return 0; }
    @Override public int getMinimumSuction() { return 0; }
}
