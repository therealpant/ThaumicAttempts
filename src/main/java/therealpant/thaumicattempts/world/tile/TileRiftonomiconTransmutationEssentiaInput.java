package therealpant.thaumicattempts.world.tile;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.common.lib.events.EssentiaHandler;
import therealpant.thaumicattempts.data.research.TAAspects;

public class TileRiftonomiconTransmutationEssentiaInput extends TileEntity implements IEssentiaTransport, ITickable {
    private int drawDelay;

    @Override
    public void update() {
        if (world == null || world.isRemote || ++drawDelay % 5 != 0) return;
        TileRiftonomicon core = getCore();
        Aspect aspect = required();
        if (core == null || aspect == null || !core.canAcceptTransmutationParadoxum()) return;
        // Infusion Matrix uses the global essentia source handler rather than
        // manually pulling only from an immediately adjacent transport.
        if (EssentiaHandler.drainEssentia(this, aspect, null, 12, 0)) {
            core.acceptTransmutationParadoxum(1);
        }
    }
    private TileRiftonomicon getCore() {
        TileEntity tile = world == null ? null : world.getTileEntity(pos.up());
        return tile instanceof TileRiftonomicon ? (TileRiftonomicon) tile : null;
    }

    private Aspect required() {
        return TAAspects.PARADOXUM != null ? TAAspects.PARADOXUM : Aspect.getAspect("paradoxum");
    }

    @Override public boolean isConnectable(EnumFacing face) { return true; }
    @Override public boolean canInputFrom(EnumFacing face) { return true; }
    @Override public boolean canOutputTo(EnumFacing face) { return false; }
    @Override public void setSuction(Aspect aspect, int amount) {}
    @Override public Aspect getSuctionType(EnumFacing face) {
        TileRiftonomicon core = getCore();
        return core != null && core.canAcceptTransmutationParadoxum() ? required() : null;
    }
    @Override public int getSuctionAmount(EnumFacing face) { return getSuctionType(face) == null ? 0 : 128; }
    @Override public int addEssentia(Aspect aspect, int amount, EnumFacing face) {
        TileRiftonomicon core = getCore();
        return core == null || aspect != required() ? 0 : core.acceptTransmutationParadoxum(amount);
    }
    @Override public int takeEssentia(Aspect aspect, int amount, EnumFacing face) { return 0; }
    @Override public Aspect getEssentiaType(EnumFacing face) { return null; }
    @Override public int getEssentiaAmount(EnumFacing face) { return 0; }
    @Override public int getMinimumSuction() { return 0; }
}
