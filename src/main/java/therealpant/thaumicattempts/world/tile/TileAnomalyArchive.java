package therealpant.thaumicattempts.world.tile;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;
import therealpant.thaumicattempts.world.EntityFluxAnomalyBurst;

import javax.annotation.Nullable;
import java.util.UUID;

public class TileAnomalyArchive extends TileEntity implements IAnimatable, ITickable {

    private static final int[] PANEL_BONES = {
            17, 18, 19, 20, 16, 21, 22, 23, 27, 28, 31, 32
    };
    private static final int BONE_COUNT = PANEL_BONES.length;
    private static final long MIN_RESTORE_TICKS = 7L * 60L * 20L;
    private static final long MAX_RESTORE_TICKS = 10L * 60L * 20L;
    private static final int RESTORE_CHECK_INTERVAL = 20;
    private static final float ANOMALY_CLOSE_CHANCE = 0.02F;

    private final AnimationFactory factory = new AnimationFactory(this);
    private final long[] restoreAt = new long[BONE_COUNT];
    private int hiddenBones;
    private int restoreCheckTicker;
    private UUID anomalyId;

    public void linkToAnomaly(UUID id) {
        if (world == null || world.isRemote || id == null) return;
        anomalyId = id;
        markDirtyAndSync();
    }

    public boolean isLinkedTo(UUID id) {
        return id != null && id.equals(anomalyId);
    }

    public boolean hasAnyVisibleBone() {
        for (int index = 0; index < BONE_COUNT; index++) {
            if (!isBoneHiddenAt(index)) return true;
        }
        return false;
    }

    public void disableRegeneration(UUID id) {
        if (world == null || world.isRemote || !isLinkedTo(id)) return;
        anomalyId = null;
        markDirtyAndSync();
    }

    public boolean hideRandomVisibleBone() {
        if (world == null || world.isRemote) return false;
        int visibleCount = 0;
        for (int index = 0; index < BONE_COUNT; index++) {
            if (!isBoneHiddenAt(index)) visibleCount++;
        }
        if (visibleCount == 0) return false;

        int selected = world.rand.nextInt(visibleCount);
        for (int index = 0; index < BONE_COUNT; index++) {
            if (isBoneHiddenAt(index)) continue;
            if (selected-- == 0) {
                hiddenBones |= 1 << index;
                restoreAt[index] = world.getTotalWorldTime() + randomRestoreDelay();
                markDirtyAndSync();
                return true;
            }
        }
        return false;
    }

    public boolean isBoneHidden(int bone) {
        int index = panelBoneIndex(bone);
        return index >= 0 && isBoneHiddenAt(index);
    }

    private boolean isBoneHiddenAt(int index) {
        return (hiddenBones & (1 << index)) != 0;
    }

    private static int panelBoneIndex(int bone) {
        for (int index = 0; index < BONE_COUNT; index++) {
            if (PANEL_BONES[index] == bone) return index;
        }
        return -1;
    }

    public static int getPanelBoneCount() {
        return BONE_COUNT;
    }

    public static int getPanelBone(int index) {
        return PANEL_BONES[index];
    }

    private long randomRestoreDelay() {
        int range = (int) (MAX_RESTORE_TICKS - MIN_RESTORE_TICKS + 1L);
        return MIN_RESTORE_TICKS + world.rand.nextInt(range);
    }

    @Override
    public void update() {
        if (world == null || world.isRemote || anomalyId == null
                || ++restoreCheckTicker < RESTORE_CHECK_INTERVAL) {
            return;
        }
        restoreCheckTicker = 0;

        EntityFluxAnomalyBurst anomaly = findLinkedAnomaly();
        if (anomaly == null) return;

        long now = world.getTotalWorldTime();
        boolean changed = false;
        for (int index = 0; index < BONE_COUNT; index++) {
            int mask = 1 << index;
            if ((hiddenBones & mask) == 0) continue;
            if (restoreAt[index] <= 0L) {
                restoreAt[index] = now + randomRestoreDelay();
                changed = true;
                continue;
            }
            if (now < restoreAt[index]) continue;

            hiddenBones &= ~mask;
            restoreAt[index] = 0L;
            changed = true;

            if (world.rand.nextFloat() < ANOMALY_CLOSE_CHANCE
                    && anomaly.finishFromArchiveBoneRestore(anomalyId)) {
                break;
            }
        }
        if (changed) markDirtyAndSync();
    }

    @Nullable
    private EntityFluxAnomalyBurst findLinkedAnomaly() {
        for (net.minecraft.entity.Entity entity : world.loadedEntityList) {
            if (!(entity instanceof EntityFluxAnomalyBurst)) continue;
            EntityFluxAnomalyBurst anomaly = (EntityFluxAnomalyBurst) entity;
            if (anomalyId.equals(anomaly.getAnomalyId()) && !anomaly.isDead) {
                return anomaly;
            }
        }
        return null;
    }

    private void markDirtyAndSync() {
        markDirty();
        if (world != null) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        hiddenBones = compound.getInteger("HiddenArchiveBones");
        for (int index = 0; index < BONE_COUNT; index++) {
            restoreAt[index] = compound.getLong(
                    "ArchiveBone" + PANEL_BONES[index] + "RestoreAt");
        }
        anomalyId = compound.hasUniqueId("ArchiveAnomaly")
                ? compound.getUniqueId("ArchiveAnomaly") : null;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setInteger("HiddenArchiveBones", hiddenBones);
        for (int index = 0; index < BONE_COUNT; index++) {
            if (restoreAt[index] > 0L) {
                compound.setLong(
                        "ArchiveBone" + PANEL_BONES[index] + "RestoreAt",
                        restoreAt[index]);
            }
        }
        if (anomalyId != null) compound.setUniqueId("ArchiveAnomaly", anomalyId);
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

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return new AxisAlignedBB(pos).grow(2.0D, 3.0D, 2.0D);
    }

    @Override
    public double getMaxRenderDistanceSquared() {
        return 65536.0D;
    }

    @Override
    public void registerControllers(AnimationData data) {
        data.addAnimationController(new AnimationController<>(
                this, "archive_controller", 0, this::animationPredicate));
    }

    private <E extends IAnimatable> PlayState animationPredicate(AnimationEvent<E> event) {
        event.getController().setAnimation(new AnimationBuilder().addAnimation("wait", true));
        return PlayState.CONTINUE;
    }

    @Override
    public AnimationFactory getFactory() {
        return factory;
    }

}
