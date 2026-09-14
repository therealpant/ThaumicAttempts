package therealpant.thaumicattempts.integration.rift;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import thaumcraft.common.entities.EntityFluxRift;

/** Replaces only the natural tick of reactor-owned rifts. */
public final class RiftWellLifecycle {
    public static final String MANAGED = "TAReactorManaged";
    /** Kept so rifts saved by the previous implementation can be migrated safely. */
    public static final String RUNAWAY = "TAReactorRunaway";
    public static final String SHUTDOWN = "TAReactorShutdown";
    private RiftWellLifecycle() {}

    public static void manage(EntityFluxRift rift, BlockPos owner) {
        rift.getEntityData().setBoolean(MANAGED, true);
        rift.getEntityData().setLong("TAReactorOwner", owner.toLong());
    }

    /** Starts a slow, non-damaging SCRAM. Collapsed energy remains in the well as corruption. */
    public static void shutdown(EntityFluxRift rift, BlockPos owner) {
        manage(rift, owner);
        NBTTagCompound data=rift.getEntityData();
        data.setBoolean(SHUTDOWN,true);
        data.setBoolean(RUNAWAY,false);
        rift.setRiftStability(40.0F);
    }

    public static void shutdownIfNeeded(EntityFluxRift rift,BlockPos owner) {
        if (!rift.getEntityData().getBoolean(SHUTDOWN)) shutdown(rift,owner);
    }

    /** Compatibility aliases for callers and entities saved by older builds. */
    public static void breach(EntityFluxRift rift,BlockPos owner) { shutdown(rift,owner); }
    public static void breachIfNeeded(EntityFluxRift rift,BlockPos owner) { shutdownIfNeeded(rift,owner); }

    public static boolean beforeUpdate(EntityFluxRift rift) {
        NBTTagCompound data=rift.getEntityData();
        if (!data.getBoolean(MANAGED) || rift.getCollapse()) return false;
        rift.onEntityUpdate();
        if (rift.world.isRemote) return true;

        BlockPos owner=BlockPos.fromLong(data.getLong("TAReactorOwner"));
        if (data.getBoolean(RUNAWAY)) shutdown(rift,owner);
        if (!data.getBoolean(SHUTDOWN) && rift.world.isBlockLoaded(owner)
                && !(rift.world.getTileEntity(owner)
                instanceof therealpant.thaumicattempts.world.tile.TileRiftWell)) {
            shutdown(rift,owner);
        }

        if (rift.getRiftSeed()==0) rift.setRiftSeed(rift.world.rand.nextInt());
        if (data.getBoolean(SHUTDOWN)) {
            if (rift.ticksExisted%10==0) {
                int next=rift.getRiftSize()-1;
                if (next<=0) rift.setDead();
                else rift.setRiftSize(next);
            }
            return true;
        }
        if (rift.points.isEmpty()) rift.setRiftSize(rift.getRiftSize());
        return true;
    }
}
