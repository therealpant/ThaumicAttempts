package therealpant.thaumicattempts.world.tile;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;
import therealpant.thaumicattempts.net.msg.S2C_MonolithParadoxumStream;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TileAnomalousMonolith extends TileEntity implements IAnimatable, ITickable {
    public static final int MIN_PARTICLES_PER_SECOND = 2;
    public static final int MAX_PARTICLES_PER_SECOND = 10;
    public static final int MAX_EFFICIENCY = 3;
    public static final int MAX_INTERCEPTED_PARTICLES = 1000;

    private static final int PARTICLE_FLIGHT_TICKS = 80;
    private static final int PARTICLES_PER_DISTRIBUTION = 40;
    private static final int DISTRIBUTION_DELAY_TICKS = 20;
    private static final double PEDESTAL_RANGE_SQ = 16.0D * 16.0D;

    private final AnimationFactory factory = new AnimationFactory(this);
    private final ArrayDeque<Long> pendingAbsorptions = new ArrayDeque<>();
    private final ArrayDeque<BlockPos> pedestalDispatchQueue = new ArrayDeque<>();

    private int efficiency;
    private double particleAccumulator;
    private int absorbedParticles;
    private long nextPedestalDispatchTick;
    private boolean interceptorStoneInstalled;
    private int interceptedParticles;

    @Override
    public void update() {
        if (world == null || world.isRemote) return;

        long now = world.getTotalWorldTime();
        scheduleAbsorptionParticles(now);
        finishArrivedParticles(now);
        queueCompletedDistributions();
        dispatchNextPedestal(now);
        if (now % 20L == 0L) {
            markDirty();
        }
    }

    private void scheduleAbsorptionParticles(long now) {
        particleAccumulator += getParticlesPerSecond();
        while (particleAccumulator >= 20.0D) {
            particleAccumulator -= 20.0D;
            pendingAbsorptions.addLast(now + PARTICLE_FLIGHT_TICKS);
        }
    }

    private void finishArrivedParticles(long now) {
        while (!pendingAbsorptions.isEmpty() && pendingAbsorptions.peekFirst() <= now) {
            pendingAbsorptions.removeFirst();
            if (interceptorStoneInstalled) {
                interceptedParticles = Math.min(MAX_INTERCEPTED_PARTICLES,
                        interceptedParticles + 1);
                continue;
            }
            absorbedParticles++;
        }
    }

    public boolean hasInterceptorStone() {
        return interceptorStoneInstalled;
    }

    public void installInterceptorStone() {
        if (interceptorStoneInstalled) return;
        interceptorStoneInstalled = true;
        markDirtyAndSync();
    }

    public boolean removeInterceptorStone() {
        if (!interceptorStoneInstalled) return false;
        interceptorStoneInstalled = false;
        absorbedParticles += interceptedParticles;
        interceptedParticles = 0;
        markDirtyAndSync();
        return true;
    }

    public int drainInterceptedParticles(int maximum) {
        if (maximum <= 0 || interceptedParticles <= 0) return 0;
        int drained = Math.min(maximum, interceptedParticles);
        interceptedParticles -= drained;
        markDirty();
        return drained;
    }

    private void queueCompletedDistributions() {
        while (absorbedParticles >= PARTICLES_PER_DISTRIBUTION) {
            List<BlockPos> pedestals = findPedestals();
            absorbedParticles -= PARTICLES_PER_DISTRIBUTION;
            if (!pedestals.isEmpty()) {
                pedestalDispatchQueue.addAll(pedestals);
            }
            markDirty();
        }
    }

    private List<BlockPos> findPedestals() {
        List<BlockPos> result = new ArrayList<>();
        for (TileEntity tile : world.loadedTileEntityList) {
            if (!(tile instanceof TileStrangePedestal)
                    || tile.isInvalid()
                    || tile.getPos().distanceSq(pos) > PEDESTAL_RANGE_SQ
                    || !((TileStrangePedestal) tile).canReceiveParadoxum()) {
                continue;
            }
            result.add(tile.getPos().toImmutable());
        }
        result.sort(Comparator.comparingDouble(pos::distanceSq));
        return result;
    }

    private void dispatchNextPedestal(long now) {
        if (pedestalDispatchQueue.isEmpty() || now < nextPedestalDispatchTick) return;

        while (!pedestalDispatchQueue.isEmpty()) {
            BlockPos targetPos = pedestalDispatchQueue.removeFirst();
            TileEntity target = world.getTileEntity(targetPos);
            if (!(target instanceof TileStrangePedestal)
                    || targetPos.distanceSq(pos) > PEDESTAL_RANGE_SQ) {
                continue;
            }

            int accepted =
                    ((TileStrangePedestal) target).receiveParadoxumFromMonolith(this, 1);
            if (accepted <= 0) continue;
            S2C_MonolithParadoxumStream.dispatch(world, pos, targetPos);
            nextPedestalDispatchTick = now + DISTRIBUTION_DELAY_TICKS;
            markDirty();
            return;
        }
        markDirty();
    }

    public int getEfficiency() {
        return efficiency;
    }

    public void setEfficiency(int efficiency) {
        int clamped = Math.max(0, Math.min(MAX_EFFICIENCY, efficiency));
        if (this.efficiency == clamped) return;
        this.efficiency = clamped;
        markDirtyAndSync();
    }

    public int getParticlesPerSecond() {
        if (efficiency <= 0) return MIN_PARTICLES_PER_SECOND;
        return Math.min(MAX_PARTICLES_PER_SECOND, 4 + efficiency * 2);
    }

    public int getAbsorbedParticles() {
        return absorbedParticles;
    }

    @Override
    public void registerControllers(AnimationData data) {
        data.addAnimationController(new AnimationController<>(
                this, "monolith_controller", 0, this::animationPredicate));
    }

    private <E extends IAnimatable> PlayState animationPredicate(AnimationEvent<E> event) {
        event.getController().setAnimation(
                new AnimationBuilder().addAnimation("condensator", true));
        return PlayState.CONTINUE;
    }

    @Override
    public AnimationFactory getFactory() {
        return factory;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        efficiency = Math.max(0, Math.min(MAX_EFFICIENCY,
                compound.getInteger("Efficiency")));
        particleAccumulator = Math.max(0.0D,
                Math.min(19.999D, compound.getDouble("ParticleAccumulator")));
        absorbedParticles = Math.max(0, compound.getInteger("AbsorbedParticles"));
        nextPedestalDispatchTick = compound.getLong("NextPedestalDispatch");
        interceptorStoneInstalled = compound.getBoolean("InterceptorStoneInstalled");
        interceptedParticles = Math.max(0, Math.min(MAX_INTERCEPTED_PARTICLES,
                compound.getInteger("InterceptedParticles")));

        pendingAbsorptions.clear();
        int pendingCount = Math.max(0, Math.min(256,
                compound.getInteger("PendingAbsorptionCount")));
        for (int index = 0; index < pendingCount; index++) {
            pendingAbsorptions.addLast(compound.getLong("PendingAbsorption" + index));
        }

        pedestalDispatchQueue.clear();
        int dispatchCount = Math.max(0, Math.min(4096,
                compound.getInteger("PedestalDispatchCount")));
        for (int index = 0; index < dispatchCount; index++) {
            pedestalDispatchQueue.addLast(BlockPos.fromLong(
                    compound.getLong("PedestalDispatch" + index)));
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setInteger("Efficiency", efficiency);
        compound.setDouble("ParticleAccumulator", particleAccumulator);
        compound.setInteger("AbsorbedParticles", absorbedParticles);
        compound.setLong("NextPedestalDispatch", nextPedestalDispatchTick);
        compound.setBoolean("InterceptorStoneInstalled", interceptorStoneInstalled);
        compound.setInteger("InterceptedParticles", interceptedParticles);

        compound.setInteger("PendingAbsorptionCount", pendingAbsorptions.size());
        int index = 0;
        for (Long arrival : pendingAbsorptions) {
            compound.setLong("PendingAbsorption" + index++, arrival);
        }

        compound.setInteger("PedestalDispatchCount", pedestalDispatchQueue.size());
        index = 0;
        for (BlockPos target : pedestalDispatchQueue) {
            compound.setLong("PedestalDispatch" + index++, target.toLong());
        }
        return compound;
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Nullable
    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        readFromNBT(packet.getNbtCompound());
    }

    private void markDirtyAndSync() {
        markDirty();
        if (world != null) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
        }
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return new AxisAlignedBB(pos).grow(3.0D, 4.0D, 3.0D);
    }

    @Override
    public double getMaxRenderDistanceSquared() {
        return 65536.0D;
    }
}
