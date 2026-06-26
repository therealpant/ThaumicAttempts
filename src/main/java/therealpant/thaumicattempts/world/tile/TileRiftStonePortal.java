package therealpant.thaumicattempts.world.tile;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ITeleporter;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;
import thaumcraft.api.aura.AuraHelper;
import therealpant.thaumicattempts.init.TABlocks;
import therealpant.thaumicattempts.util.WorldSpawnUtil;

import javax.annotation.Nullable;

public class TileRiftStonePortal extends TileEntity implements ITickable, IAnimatable {
    private static final int OPEN_TICKS = 40;
    private static final int HOLD_TICKS = 60;
    private static final int CLOSE_TICKS = 40;
    private static final int HOLD_ANIMATION_START_TICKS = 45;
    private static final int HOLD_ANIMATION_END_TICKS = 105;
    private static final int HOLD_ANIMATION_REWIND_TICKS = 60;
    private static final int FULL_ANIMATION_TICKS = 160;
    private static final int VIS_DRAIN_INTERVAL = 20;
    private static final float VIS_DRAIN_AMOUNT = 10.0F;
    private static final int TELEPORT_COOLDOWN_TICKS = 40;

    private static final String TAG_TELEPORT_TIME = "TA_RiftStonePortalTeleportTime";

    public static final String ANIM_FULL = "portal.full";

    private final AnimationFactory factory = new AnimationFactory(this);

    private PortalVisualState visualState = PortalVisualState.CLOSED;
    private boolean fullAnimationApplied;
    private boolean open;
    private boolean closeQueued;
    private int phaseTicks;
    private int closeDurationTicks = CLOSE_TICKS;
    private int targetDimension;
    private BlockPos targetPos;
    private int modelRotationSteps;

    public void openPortal(int dimension, BlockPos destination) {
        if (destination == null) {
            return;
        }
        targetDimension = dimension;
        targetPos = destination;
        open = true;
        closeQueued = false;
        visualState = PortalVisualState.OPENING;
        phaseTicks = 0;
        closeDurationTicks = CLOSE_TICKS;
        fullAnimationApplied = false;
        markDirtyAndSync();
    }

    public boolean isActive() {
        return visualState == PortalVisualState.OPENING || visualState == PortalVisualState.HOLD;
    }

    public void requestClose() {
        if (visualState == PortalVisualState.OPENING || visualState == PortalVisualState.HOLD) {
            open = false;
            closeQueued = true;
            markDirtyAndSync();
        }
    }

    public PortalVisualState getVisualState() {
        return visualState;
    }

    public int getModelRotationSteps() {
        return modelRotationSteps;
    }

    public void rotateModelClockwise() {
        modelRotationSteps = (modelRotationSteps + 1) & 7;
        markDirtyAndSync();
    }

    public float getCultistPortalProgress(float partialTicks) {
        switch (visualState) {
            case OPENING:
                return clamp01((phaseTicks + partialTicks) / (float) OPEN_TICKS);
            case HOLD:
                return 1.0F;
            case CLOSING:
                int closeStartTicks = Math.max(closeDurationTicks - CLOSE_TICKS, 0);
                return 1.0F - clamp01((phaseTicks + partialTicks - closeStartTicks) / (float) CLOSE_TICKS);
            case CLOSED:
            default:
                return 0.0F;
        }
    }

    @Override
    public void update() {
        if (world == null) return;

        switch (visualState) {
            case OPENING:
                phaseTicks++;
                if (phaseTicks >= OPEN_TICKS) {
                    visualState = PortalVisualState.HOLD;
                    phaseTicks = 0;
                    if (!world.isRemote) {
                        markDirtyAndSync();
                    }
                }
                break;
            case HOLD:
                phaseTicks++;
                if (!world.isRemote && phaseTicks % VIS_DRAIN_INTERVAL == 0 && !drainMaintenanceVis()) {
                    beginClosing();
                    break;
                }
                if (phaseTicks >= HOLD_TICKS) {
                    if (closeQueued) {
                        beginClosing();
                    } else {
                        phaseTicks = 0;
                    }
                }
                break;
            case CLOSING:
                phaseTicks++;
                if (phaseTicks >= closeDurationTicks) {
                    visualState = PortalVisualState.CLOSED;
                    phaseTicks = 0;
                    open = false;
                    closeQueued = false;
                    closeDurationTicks = CLOSE_TICKS;
                    if (!world.isRemote) {
                        markDirtyAndSync();
                    }
                }
                break;
            case CLOSED:
            default:
                phaseTicks = 0;
                closeDurationTicks = CLOSE_TICKS;
                closeQueued = false;
                open = false;
                break;
        }
    }

    public boolean tryTeleport(EntityPlayer player) {
        if (world == null || world.isRemote || visualState != PortalVisualState.HOLD || targetPos == null) {
            return false;
        }
        if (!(player instanceof EntityPlayerMP)) {
            return false;
        }
        if (isTeleportCoolingDown(player)) {
            return false;
        }

        EntityPlayerMP mp = (EntityPlayerMP) player;

        if (mp.dimension == targetDimension) {
            BlockPos destination = findSafeTeleportPos(world, targetPos);
            if (destination == null) {
                beginClosing();
                return false;
            }
            setTeleportCooldown(mp);
            mp.setPositionAndUpdate(destination.getX() + 0.5D, destination.getY(), destination.getZ() + 0.5D);
            return true;
        }

        if (!(world instanceof WorldServer)) {
            return false;
        }
        MinecraftServer server = ((WorldServer) world).getMinecraftServer();
        WorldServer targetWorld = server.getWorld(targetDimension);
        if (targetWorld == null) {
            beginClosing();
            return false;
        }
        BlockPos destination = findSafeTeleportPos(targetWorld, targetPos);
        if (destination == null) {
            beginClosing();
            return false;
        }

        setTeleportCooldown(mp);
        mp.changeDimension(targetDimension, new FixedTeleporter(destination));
        return true;
    }

    @Nullable
    private static BlockPos findSafeTeleportPos(World world, BlockPos portalPos) {
        for (EnumFacing facing : EnumFacing.HORIZONTALS) {
            BlockPos candidate = portalPos.offset(facing);
            if (isSafePortalExit(world, candidate)) {
                return candidate.toImmutable();
            }
        }
        for (EnumFacing facing : EnumFacing.HORIZONTALS) {
            BlockPos candidate = portalPos.offset(facing).up();
            if (isSafePortalExit(world, candidate)) {
                return candidate.toImmutable();
            }
        }
        for (EnumFacing facing : EnumFacing.HORIZONTALS) {
            BlockPos candidate = portalPos.offset(facing).down();
            if (isSafePortalExit(world, candidate)) {
                return candidate.toImmutable();
            }
        }
        return null;
    }

    private static boolean isSafePortalExit(World world, BlockPos pos) {
        if (WorldSpawnUtil.isSafeSeedPos(world, pos)) {
            return true;
        }
        if (world == null || pos == null) {
            return false;
        }
        if (pos.getY() < 1 || pos.getY() >= world.getHeight() - 1) {
            return false;
        }
        if (!world.isBlockLoaded(pos)) {
            world.getChunk(pos);
        }
        if (!world.isAirBlock(pos) || !world.isAirBlock(pos.up())) {
            return false;
        }

        BlockPos below = pos.down();
        return world.getBlockState(below).getBlock() == TABlocks.RIFT_PORTAL_PLATFORM;
    }

    private void beginClosing() {
        closeQueued = false;
        open = false;
        if (visualState != PortalVisualState.CLOSED && visualState != PortalVisualState.CLOSING) {
            closeDurationTicks = getRemainingFullAnimationTicks();
            visualState = PortalVisualState.CLOSING;
            phaseTicks = 0;
            markDirtyAndSync();
        }
    }

    private boolean drainMaintenanceVis() {
        return AuraHelper.drainVis(world, pos, VIS_DRAIN_AMOUNT, false) >= VIS_DRAIN_AMOUNT;
    }

    private boolean isTeleportCoolingDown(EntityPlayer player) {
        long until = player.getEntityData().getLong(TAG_TELEPORT_TIME);
        return world.getTotalWorldTime() < until;
    }

    private void setTeleportCooldown(EntityPlayer player) {
        player.getEntityData().setLong(TAG_TELEPORT_TIME, world.getTotalWorldTime() + TELEPORT_COOLDOWN_TICKS);
    }

    private static float clamp01(float value) {
        if (value < 0.0F) return 0.0F;
        if (value > 1.0F) return 1.0F;
        return value;
    }

    @Override
    public void registerControllers(AnimationData data) {
        data.addAnimationController(new AnimationController<>(this, "controller", 0, this::predicate));
    }

    private <E extends IAnimatable> PlayState predicate(AnimationEvent<E> event) {
        AnimationController<?> controller = event.getController();

        if (visualState == PortalVisualState.CLOSED) {
            fullAnimationApplied = false;
            return PlayState.STOP;
        }

        if (!fullAnimationApplied) {
            fullAnimationApplied = true;
            controller.markNeedsReload();
            controller.setAnimation(new AnimationBuilder().addAnimation(ANIM_FULL, false));
        } else {
            double fullAnimationTick = getFullAnimationTick(controller, event.getAnimationTick());
            if (!closeQueued && visualState == PortalVisualState.HOLD && fullAnimationTick >= HOLD_ANIMATION_END_TICKS) {
                rewindFullAnimationByHoldCycle(controller);
            }
        }
        return PlayState.CONTINUE;
    }

    private int getRemainingFullAnimationTicks() {
        int currentAnimationTick;
        switch (visualState) {
            case OPENING:
                currentAnimationTick = phaseTicks;
                break;
            case HOLD:
                currentAnimationTick = HOLD_ANIMATION_START_TICKS + phaseTicks;
                break;
            case CLOSING:
                currentAnimationTick = FULL_ANIMATION_TICKS - Math.max(closeDurationTicks - phaseTicks, 0);
                break;
            case CLOSED:
            default:
                currentAnimationTick = FULL_ANIMATION_TICKS;
                break;
        }
        return Math.max(FULL_ANIMATION_TICKS - currentAnimationTick, CLOSE_TICKS);
    }

    private double getFullAnimationTick(AnimationController<?> controller, double eventTick) {
        double animationSpeed = controller.getAnimationSpeed();
        if (animationSpeed == 0.0D) {
            animationSpeed = 1.0D;
        }
        return animationSpeed * Math.max(eventTick - controller.tickOffset, 0.0D);
    }

    private void rewindFullAnimationByHoldCycle(AnimationController<?> controller) {
        double animationSpeed = controller.getAnimationSpeed();
        if (animationSpeed == 0.0D) {
            animationSpeed = 1.0D;
        }
        controller.tickOffset += HOLD_ANIMATION_REWIND_TICKS / animationSpeed;
    }

    @Override
    public AnimationFactory getFactory() {
        return factory;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        PortalVisualState previousVisualState = visualState;
        open = compound.getBoolean("Open");
        closeQueued = compound.getBoolean("CloseQueued");
        phaseTicks = compound.getInteger("PhaseTicks");
        closeDurationTicks = compound.hasKey("CloseDurationTicks")
                ? compound.getInteger("CloseDurationTicks")
                : CLOSE_TICKS;
        visualState = PortalVisualState.fromOrdinal(compound.getInteger("VisualState"));
        if (visualState == PortalVisualState.CLOSED
                || previousVisualState != visualState && visualState == PortalVisualState.OPENING) {
            fullAnimationApplied = false;
        }
        targetDimension = compound.getInteger("TargetDimension");
        targetPos = compound.hasKey("TargetPos") ? BlockPos.fromLong(compound.getLong("TargetPos")) : null;
        modelRotationSteps = compound.getInteger("ModelRotationSteps") & 7;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setBoolean("Open", open);
        compound.setBoolean("CloseQueued", closeQueued);
        compound.setInteger("PhaseTicks", phaseTicks);
        compound.setInteger("CloseDurationTicks", closeDurationTicks);
        compound.setInteger("VisualState", visualState.ordinal());
        compound.setInteger("TargetDimension", targetDimension);
        compound.setInteger("ModelRotationSteps", modelRotationSteps);
        if (targetPos != null) {
            compound.setLong("TargetPos", targetPos.toLong());
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
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
    }

    private void markDirtyAndSync() {
        markDirty();
        if (world != null) {
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        }
    }

    private static final class FixedTeleporter implements ITeleporter {
        private final BlockPos destination;

        private FixedTeleporter(BlockPos destination) {
            this.destination = destination;
        }

        @Override
        public void placeEntity(World world, Entity entity, float yaw) {
            entity.setLocationAndAngles(
                    destination.getX() + 0.5D,
                    destination.getY() + 0.1D,
                    destination.getZ() + 0.5D,
                    entity.rotationYaw,
                    entity.rotationPitch
            );
            entity.motionX = 0.0D;
            entity.motionY = 0.0D;
            entity.motionZ = 0.0D;
        }
    }

    public enum PortalVisualState {
        CLOSED,
        OPENING,
        HOLD,
        CLOSING;

        private static PortalVisualState fromOrdinal(int ordinal) {
            PortalVisualState[] values = values();
            if (ordinal < 0 || ordinal >= values.length) return CLOSED;
            return values[ordinal];
        }
    }
}
