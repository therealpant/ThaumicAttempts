package therealpant.thaumicattempts.world.tile;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.common.util.Constants;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;
import therealpant.thaumicattempts.data.AnomalyWellRecipe;
import therealpant.thaumicattempts.data.TAAnomalyWellRecipes;
import therealpant.thaumicattempts.world.EntityAnomalyWellResult;
import therealpant.thaumicattempts.world.EntityFluxAnomalyBurst;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TileAnomalyWell extends TileEntity implements ITickable, IAnimatable {
    public static final int SLOT_COUNT = 3;
    public static final int MAX_CHARGES = 10;
    public static final float MAX_LEVEL_PIXELS = 10.0F;
    private static final int COLLECTION_INTERVAL = 5;
    private static final int RECHARGE_CHECK_INTERVAL = 20;
    private static final long MIN_RECHARGE_TICKS = 7L * 60L * 20L;
    private static final long MAX_RECHARGE_TICKS = 10L * 60L * 20L;
    private static final float ANOMALY_CLOSE_CHANCE = 0.02F;

    private final AnimationFactory factory = new AnimationFactory(this);
    private final NonNullList<ItemStack> input =
            NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private int maxCharges = MAX_CHARGES;
    private int charges = MAX_CHARGES;
    private int ticks;
    private long nextRechargeAt;
    private UUID anomalyId;

    @Override
    public void update() {
        if (world == null || world.isRemote) {
            return;
        }
        ticks++;
        if (ticks % RECHARGE_CHECK_INTERVAL == 0) {
            tickLaboratoryRecharge();
        }
        if (ticks % COLLECTION_INTERVAL == 0) {
            collectItems();
        }
    }

    private void collectItems() {
        AxisAlignedBB intake = new AxisAlignedBB(
                pos.getX() - 0.4375D, pos.getY(), pos.getZ() - 0.4375D,
                pos.getX() + 1.4375D, pos.getY() + 1.5D, pos.getZ() + 1.4375D);
        List<EntityItem> entities = world.getEntitiesWithinAABB(EntityItem.class, intake);

        for (EntityItem entity : entities) {
            if (getStoredItemCount() >= SLOT_COUNT) {
                break;
            }
            if (entity instanceof EntityAnomalyWellResult) {
                continue;
            }
            if (entity == null || entity.isDead || entity.getItem().isEmpty()) {
                continue;
            }
            storeOne(entity.getItem());
            ItemStack remaining = entity.getItem();
            remaining.shrink(1);
            if (remaining.isEmpty()) {
                entity.setDead();
            } else {
                entity.setItem(remaining);
            }
        }

        if (getStoredItemCount() < SLOT_COUNT) {
            return;
        }

        // Once the three inputs are captured, everything else inside the well is excess.
        for (EntityItem entity : entities) {
            if (entity != null && !entity.isDead
                    && !(entity instanceof EntityAnomalyWellResult)) {
                entity.setDead();
            }
        }
        processInputs();
    }

    private void storeOne(ItemStack source) {
        ItemStack one = source.copy();
        one.setCount(1);
        for (int slot = 0; slot < input.size(); slot++) {
            if (input.get(slot).isEmpty()) {
                input.set(slot, one);
                markDirtyAndSync();
                return;
            }
        }
    }

    private void processInputs() {
        List<ItemStack> stacks = new ArrayList<>(input.size());
        for (ItemStack stack : input) {
            stacks.add(stack.copy());
        }

        AnomalyWellRecipe recipe = TAAnomalyWellRecipes.findMatch(stacks);
        clearInputs();
        if (recipe != null && charges > 0) {
            charges--;
            scheduleRechargeIfNeeded();
            eject(recipe.getResult());
        }
        markDirtyAndSync();
    }

    private void eject(ItemStack result) {
        EntityAnomalyWellResult entity = new EntityAnomalyWellResult(
                world,
                pos.getX() + 0.5D,
                pos.getY() + 1.15D,
                pos.getZ() + 0.5D,
                result);
        entity.motionX = 0.0D;
        entity.motionY = 0.0D;
        entity.motionZ = 0.0D;
        entity.setPickupDelay(20);
        world.spawnEntity(entity);
    }

    private void clearInputs() {
        for (int slot = 0; slot < input.size(); slot++) {
            input.set(slot, ItemStack.EMPTY);
        }
    }

    private int getStoredItemCount() {
        int count = 0;
        for (ItemStack stack : input) {
            if (!stack.isEmpty()) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public int getCharges() {
        return charges;
    }

    public int getMaxCharges() {
        return maxCharges;
    }

    public float getFluidLevelPixels() {
        return Math.min(MAX_LEVEL_PIXELS, charges);
    }

    public void initializeLaboratory(UUID id, int chargeLimit) {
        if (world == null || world.isRemote || id == null) return;
        anomalyId = id;
        maxCharges = Math.max(6, Math.min(MAX_CHARGES, chargeLimit));
        charges = maxCharges;
        nextRechargeAt = 0L;
        markDirtyAndSync();
    }

    public boolean isLinkedTo(UUID id) {
        return id != null && id.equals(anomalyId);
    }

    public void disableRegeneration(UUID id) {
        if (world == null || world.isRemote || !isLinkedTo(id)) return;
        anomalyId = null;
        nextRechargeAt = 0L;
        markDirtyAndSync();
    }

    private void scheduleRechargeIfNeeded() {
        if (anomalyId == null || charges >= maxCharges || nextRechargeAt > 0L) return;
        nextRechargeAt = world.getTotalWorldTime() + randomRechargeDelay();
    }

    private long randomRechargeDelay() {
        int range = (int) (MAX_RECHARGE_TICKS - MIN_RECHARGE_TICKS + 1L);
        return MIN_RECHARGE_TICKS + world.rand.nextInt(range);
    }

    private void tickLaboratoryRecharge() {
        if (anomalyId == null || charges >= maxCharges) {
            nextRechargeAt = 0L;
            return;
        }
        if (nextRechargeAt <= 0L) {
            nextRechargeAt = world.getTotalWorldTime() + randomRechargeDelay();
            markDirty();
            return;
        }
        if (world.getTotalWorldTime() < nextRechargeAt) return;

        charges++;
        nextRechargeAt = charges < maxCharges
                ? world.getTotalWorldTime() + randomRechargeDelay() : 0L;
        EntityFluxAnomalyBurst anomaly = findLinkedAnomaly();
        if (anomaly != null && world.rand.nextFloat() < ANOMALY_CLOSE_CHANCE) {
            anomaly.finishFromLaboratoryWellRecharge(anomalyId);
        }
        markDirtyAndSync();
    }

    @Nullable
    private EntityFluxAnomalyBurst findLinkedAnomaly() {
        if (anomalyId == null) return null;
        for (net.minecraft.entity.Entity entity : world.loadedEntityList) {
            if (!(entity instanceof EntityFluxAnomalyBurst)) continue;
            EntityFluxAnomalyBurst anomaly = (EntityFluxAnomalyBurst) entity;
            if (anomalyId.equals(anomaly.getAnomalyId()) && !anomaly.isDead) {
                return anomaly;
            }
        }
        return null;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setInteger("MaxCharges", maxCharges);
        compound.setInteger("Charges", charges);
        if (nextRechargeAt > 0L) {
            compound.setLong("NextRechargeAt", nextRechargeAt);
        }
        if (anomalyId != null) {
            compound.setUniqueId("LaboratoryAnomaly", anomalyId);
        }
        NBTTagList list = new NBTTagList();
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            NBTTagCompound stackTag = new NBTTagCompound();
            stackTag.setByte("Slot", (byte) slot);
            stack.writeToNBT(stackTag);
            list.appendTag(stackTag);
        }
        compound.setTag("Inputs", list);
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        maxCharges = compound.hasKey("MaxCharges")
                ? Math.max(6, Math.min(MAX_CHARGES, compound.getInteger("MaxCharges")))
                : MAX_CHARGES;
        charges = compound.hasKey("Charges")
                ? Math.max(0, Math.min(maxCharges, compound.getInteger("Charges")))
                : maxCharges;
        nextRechargeAt = compound.getLong("NextRechargeAt");
        anomalyId = compound.hasUniqueId("LaboratoryAnomaly")
                ? compound.getUniqueId("LaboratoryAnomaly") : null;
        clearInputs();
        NBTTagList list = compound.getTagList("Inputs", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound stackTag = list.getCompoundTagAt(i);
            int slot = stackTag.getByte("Slot") & 255;
            if (slot >= 0 && slot < input.size()) {
                ItemStack stack = new ItemStack(stackTag);
                if (!stack.isEmpty()) {
                    stack.setCount(1);
                    input.set(slot, stack);
                }
            }
        }
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

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
    public void registerControllers(AnimationData data) {
        data.addAnimationController(new AnimationController<>(
                this, "anomaly_well_controller", 0, this::animationPredicate));
    }

    private <E extends IAnimatable> PlayState animationPredicate(AnimationEvent<E> event) {
        event.getController().setAnimation(new AnimationBuilder().addAnimation("well", true));
        return PlayState.CONTINUE;
    }

    @Override
    public AnimationFactory getFactory() {
        return factory;
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return new AxisAlignedBB(pos).grow(2.0D, 5.0D, 2.0D);
    }

    @Override
    public double getMaxRenderDistanceSquared() {
        return 65536.0D;
    }
}
