package therealpant.thaumicattempts.items;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.world.EntityFluxAnomalyBurst;
import therealpant.thaumicattempts.world.data.TAInfectedChunksData;

import javax.annotation.Nullable;

public class ItemAnomalyCompass extends Item implements IAnimatable {

    private static final String TAG_HAS_TARGET = "HasAnomalyTarget";
    private static final String TAG_TARGET_X = "AnomalyTargetX";
    private static final String TAG_TARGET_Y = "AnomalyTargetY";
    private static final String TAG_TARGET_Z = "AnomalyTargetZ";
    private static final String TAG_TARGET_DIM = "AnomalyTargetDimension";
    private static final int TARGET_UPDATE_INTERVAL = 10;

    private final AnimationFactory factory = new AnimationFactory(this);

    public ItemAnomalyCompass() {
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
        setTranslationKey(ThaumicAttempts.MODID + ".anomaly_compass");
        setRegistryName(ThaumicAttempts.MODID, "anomaly_compass");
        setMaxStackSize(1);
    }

    @Override
    public void registerControllers(AnimationData data) {
        // The pointer is driven directly by the model from the target stored in the stack.
    }

    @Override
    public AnimationFactory getFactory() {
        return factory;
    }

    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int itemSlot, boolean isSelected) {
        super.onUpdate(stack, world, entity, itemSlot, isSelected);
        if (world.isRemote || !(entity instanceof EntityPlayer)) return;
        if (entity.ticksExisted % TARGET_UPDATE_INTERVAL != 0) return;

        BlockPos target = findNearestAnomaly(world, entity.getPosition());
        if (target == null) {
            clearTarget(stack);
        } else {
            setTarget(stack, target, world.provider.getDimension());
        }
    }

    @Nullable
    private static BlockPos findNearestAnomaly(World world, BlockPos origin) {
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        TAInfectedChunksData data = TAInfectedChunksData.get(world);
        for (long chunkKey : data.getActiveInfectedChunks()) {
            BlockPos candidate = data.getSeedPositionForChunk(chunkKey);
            if (candidate == null) continue;
            double distance = candidate.distanceSq(origin);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = candidate;
            }
        }

        for (Entity loadedEntity : world.loadedEntityList) {
            if (!(loadedEntity instanceof EntityFluxAnomalyBurst) || loadedEntity.isDead) continue;
            BlockPos candidate = ((EntityFluxAnomalyBurst) loadedEntity).getAnomalyPosition();
            double distance = candidate.distanceSq(origin);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = candidate;
            }
        }

        return nearest == null ? null : nearest.toImmutable();
    }

    private static void setTarget(ItemStack stack, BlockPos target, int dimension) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }

        if (tag.getBoolean(TAG_HAS_TARGET)
                && tag.getInteger(TAG_TARGET_X) == target.getX()
                && tag.getInteger(TAG_TARGET_Y) == target.getY()
                && tag.getInteger(TAG_TARGET_Z) == target.getZ()
                && tag.getInteger(TAG_TARGET_DIM) == dimension) {
            return;
        }

        tag.setBoolean(TAG_HAS_TARGET, true);
        tag.setInteger(TAG_TARGET_X, target.getX());
        tag.setInteger(TAG_TARGET_Y, target.getY());
        tag.setInteger(TAG_TARGET_Z, target.getZ());
        tag.setInteger(TAG_TARGET_DIM, dimension);
    }

    private static void clearTarget(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.getBoolean(TAG_HAS_TARGET)) return;
        tag.setBoolean(TAG_HAS_TARGET, false);
        tag.removeTag(TAG_TARGET_X);
        tag.removeTag(TAG_TARGET_Y);
        tag.removeTag(TAG_TARGET_Z);
        tag.removeTag(TAG_TARGET_DIM);
    }

    @Nullable
    public static BlockPos getTarget(ItemStack stack, int currentDimension) {
        if (stack == null || stack.isEmpty()) return null;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.getBoolean(TAG_HAS_TARGET)) return null;
        if (tag.getInteger(TAG_TARGET_DIM) != currentDimension) return null;
        return new BlockPos(
                tag.getInteger(TAG_TARGET_X),
                tag.getInteger(TAG_TARGET_Y),
                tag.getInteger(TAG_TARGET_Z)
        );
    }
}
