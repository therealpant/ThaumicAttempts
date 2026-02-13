package therealpant.thaumicattempts.world.tile;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.common.lib.events.EssentiaHandler;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.config.TAConfig;

import therealpant.thaumicattempts.integration.thaumicaugmentation.ImpetusCompat;

import javax.annotation.Nullable;



public class TileAuraBooster extends TileEntity implements ITickable, IEssentiaTransport, IAnimatable {

    private final AnimationFactory factory = new AnimationFactory(this);

    private static final String TAUG_MODID = "thaumicaugmentation";
    private static final int TICK_INTERVAL = 60;
    private static final int ESSENTIA_CAP = 40;
    private static final int ESSENTIA_SUCTION = 1024;
    private static final int ESSENTIA_DRAIN_RANGE = 8;
    private static final int ESSENTIA_BASE_COST = 6;
    private static final int IMPETUS_BASE_COST = 6;
    private static final int HEAT_RESET_TICKS = 240;
    private static final int PEARL_DAMAGE_INTERVAL = 10;
    private static final float VIS_ADD_MULTIPLIER = 0.10F;
    private static final float VIS_MAX_MULTIPLIER = 0.75F;
    private static final Aspect REQUIRED_ASPECT = Aspect.AURA;
    private static final String PEARL_ID = "thaumcraft:primordial_pearl";

    private final ItemStackHandler inventory = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            markDirtyAndSync();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return isPearl(stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    };

    @Nullable
    private Object impetusNode;

    private int impetusPulseTicks;
    private int tickCounter;
    private int drawDelay;
    private int suctionPingCooldown;
    private int essentiaAmount;
    private int pearlUseCounter;
    private int essentiaCostCurrent = ESSENTIA_BASE_COST;
    private int impetusCostCurrent = IMPETUS_BASE_COST;

    private long lastWorkTick;

    @Override
    public void update() {
        if (world == null) return;

        if (world.isRemote) {
            if (impetusPulseTicks > 0) impetusPulseTicks--;
            return;
        }

        pullEssentiaFromNeighbors();
        if (suctionPingCooldown-- <= 0) {
            if (essentiaAmount < ESSENTIA_CAP) {
                notifyEssentiaChange();
            }
            suctionPingCooldown = 20;
        }
        if (++tickCounter % TICK_INTERVAL != 0) return;
        attemptBoost();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!ImpetusCompat.isLoaded() || world == null) return;

        if (impetusNode == null) {
            impetusNode = ImpetusCompat.createBufferedConsumerNode(this, 4, 4, 200000L);
        }

        ImpetusCompat.updateNodeLocation(impetusNode, world, pos);

        // ВАЖНО: init надо на ОБЕИХ сторонах
        ImpetusCompat.initNode(impetusNode, world);
    }


    @Override
    public void validate() {
        super.validate();

    }

    @Override
    public void invalidate() {
        if (ImpetusCompat.isLoaded()) {
            ImpetusCompat.unloadNode(impetusNode);
        }
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        if (ImpetusCompat.isLoaded()) {
            ImpetusCompat.unloadNode(impetusNode);
        }
        super.onChunkUnload();
    }

    public ItemStack getPearlStack() {
        return inventory.getStackInSlot(0);
    }

    public boolean tryInsertPearl(net.minecraft.entity.player.EntityPlayer player, EnumHand hand) {
        if (player == null || hand == null) return false;
        ItemStack held = player.getHeldItem(hand);
        if (!isPearl(held)) return false;
        if (!inventory.getStackInSlot(0).isEmpty()) return false;
        ItemStack toInsert = held.copy();
        toInsert.setCount(1);
        ItemStack remainder = inventory.insertItem(0, toInsert, false);
        if (!remainder.isEmpty()) return false;
        if (!player.capabilities.isCreativeMode) {
            held.shrink(1);
        }
        markDirtyAndSync();
        return true;
    }

    public boolean tryExtractPearl(net.minecraft.entity.player.EntityPlayer player) {
        if (player == null) return false;
        if (inventory.getStackInSlot(0).isEmpty()) return false;
        ItemStack extracted = inventory.extractItem(0, 1, false);
        if (extracted.isEmpty()) return false;
        boolean added = player.inventory.addItemStackToInventory(extracted);
        if (!added && world != null) {
            net.minecraft.inventory.InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), extracted);
        }
        markDirtyAndSync();
        return true;
    }

    private boolean hasPearl() {
        return isPearl(getPearlStack());
    }

    private void attemptBoost() {
        long now = world.getTotalWorldTime();
        if (now - lastWorkTick >= HEAT_RESET_TICKS) {
            resetHeatCosts();
        }
        int pearlBonus = hasPearl() ? 1 : 0;
        int impetusEfficiency = Math.min(3, 2 + pearlBonus);
        long requestedImpetus = 0L;
        long consumedImpetus = 0L;
        int requestedEssentia = 0;
        int consumedEssentia = 0;
        float payRatio = 0.0F;

        if (canBoostAny(impetusEfficiency)) {
            requestedImpetus = impetusCostCurrent;
            consumedImpetus = consumeImpetus(requestedImpetus, false);
            payRatio = clamp01(requestedImpetus <= 0 ? 0.0F : (float) consumedImpetus / (float) requestedImpetus);
            if (payRatio > 0.0F) {
                debugPowerAttempt(requestedImpetus, consumedImpetus, 0, 0, payRatio);
                if (boostChunks(impetusEfficiency, payRatio)) {
                    applyHeatIncrease();
                    lastWorkTick = now;
                    handlePearlWear();
                    return;
                }
            }
        }

        int efficiency = Math.min(3, 1 + pearlBonus);
        if (!canBoostAny(efficiency)) return;
        requestedEssentia = essentiaCostCurrent;
        consumedEssentia = consumeEssentiaPartial(requestedEssentia);
        payRatio = clamp01(requestedEssentia <= 0 ? 0.0F : (float) consumedEssentia / (float) requestedEssentia);
        if (payRatio <= 0.0F) return;
        debugPowerAttempt(0L, 0L, requestedEssentia, consumedEssentia, payRatio);
        if (boostChunks(efficiency, payRatio)) {
            consumeEssentia(consumedEssentia);
            applyHeatIncrease();
            lastWorkTick = now;
            handlePearlWear();
        }
    }

    private void handlePearlWear() {
        if (!hasPearl()) return;
        pearlUseCounter++;
        if (pearlUseCounter < PEARL_DAMAGE_INTERVAL) return;
        pearlUseCounter = 0;
        ItemStack stack = inventory.getStackInSlot(0);
        if (stack.isEmpty()) return;
        if (stack.isItemStackDamageable()) {
            stack.setItemDamage(stack.getItemDamage() + 1);
            if (stack.getItemDamage() >= stack.getMaxDamage()) {
                stack.shrink(1);
            }
        } else {
            stack.shrink(1);
        }
        markDirtyAndSync();
    }

    private boolean canBoostAny(int efficiency) {
        if (efficiency <= 0) return false;
        BlockPos origin = pos;
        int chunkX = origin.getX() >> 4;
        int chunkZ = origin.getZ() >> 4;
        if (efficiency == 3) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (needsBoost(chunkX + dx, chunkZ + dz, efficiency)) {
                        return true;
                    }
                }
            }
            return false;
        }
        return needsBoost(chunkX, chunkZ, efficiency);
    }

    private boolean boostChunks(int efficiency, float payRatio) {
        if (efficiency <= 0 || payRatio <= 0.0F) return false;
        BlockPos origin = pos;
        int chunkX = origin.getX() >> 4;
        int chunkZ = origin.getZ() >> 4;
        boolean boosted = false;
        if (efficiency == 3) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    boosted |= boostChunk(chunkX + dx, chunkZ + dz, efficiency, payRatio);
                }
            }
            return boosted;
        }
        return boostChunk(chunkX, chunkZ, efficiency, payRatio);
    }

    private boolean needsBoost(int chunkX, int chunkZ, int efficiency) {
        float base = getChunkBase(chunkX, chunkZ);
        if (base <= 0.0F) return false;
        float max = base * (1.0F + VIS_MAX_MULTIPLIER * efficiency);
        float current = AuraHelper.getVis(world, getChunkCenter(chunkX, chunkZ));
        return current < 0.8F * max;
    }

    private boolean boostChunk(int chunkX, int chunkZ, int efficiency, float payRatio) {
        float base = getChunkBase(chunkX, chunkZ);
        if (base <= 0.0F) return false;
        float max = base * (1.0F + VIS_MAX_MULTIPLIER * efficiency);
        BlockPos chunkPos = getChunkCenter(chunkX, chunkZ);
        float current = AuraHelper.getVis(world, chunkPos);
        if (current >= 0.8F * max) return false;
        float add = base * VIS_ADD_MULTIPLIER * efficiency;
        float allowed = max - current;
        if (allowed <= 0.0F) return false;
        float toAdd = Math.min(add, allowed);
        if (toAdd <= 0.0F) return false;
        float scaled = toAdd * payRatio;
        if (scaled <= 0.0F) return false;
        AuraHelper.addVis(world, chunkPos, scaled);
        return true;
    }

    private float getChunkBase(int chunkX, int chunkZ) {
        BlockPos chunkCenter = getChunkCenter(chunkX, chunkZ);
        float base = AuraHelper.getAuraBase(world, chunkCenter);
        return base > 0.0F ? base : 0.0F;
    }

    private BlockPos getChunkCenter(int chunkX, int chunkZ) {
        return new BlockPos((chunkX << 4) + 8, pos.getY(), (chunkZ << 4) + 8);
    }

    private void consumeEssentia(int amount) {
        if (amount <= 0 || essentiaAmount <= 0) return;
        essentiaAmount = Math.max(0, essentiaAmount - amount);
        markDirtyAndSync();
        notifyEssentiaChange();
    }

    private int consumeEssentiaPartial(int requested) {
        if (requested <= 0 || essentiaAmount <= 0) return 0;
        return Math.min(requested, essentiaAmount);
    }

    private boolean pullEssentiaFromNeighbors() {
        if (++drawDelay % 5 != 0 || world == null || world.isRemote) return false;
        if (essentiaAmount >= ESSENTIA_CAP) return false;
        int canTake = ESSENTIA_CAP - essentiaAmount;
        int pulled = 0;
        for (int i = 0; i < canTake; i++) {
            boolean ok = EssentiaHandler.drainEssentia(this, REQUIRED_ASPECT, EnumFacing.UP, ESSENTIA_DRAIN_RANGE, 0);
            if (!ok) break;
            pulled++;
            essentiaAmount++;
        }
        if (pulled > 0) {
            markDirtyAndSync();
            notifyEssentiaChange();
            return true;
        }
        return false;
    }

    private void resetHeatCosts() {
        essentiaCostCurrent = ESSENTIA_BASE_COST;
        impetusCostCurrent = IMPETUS_BASE_COST;
    }

    private void applyHeatIncrease() {
        essentiaCostCurrent += (int) Math.ceil(ESSENTIA_BASE_COST / 3.0);
        impetusCostCurrent += (int) Math.ceil(IMPETUS_BASE_COST / 3.0);
    }

    private void debugPowerAttempt(long requestedImpetus, long consumedImpetus, int requestedEssentia,
                                   int consumedEssentia, float payRatio) {
        if (!TAConfig.ENABLE_AURA_BOOSTER_DEBUG_LOGS) return;
        ThaumicAttempts.LOGGER.debug(
                "[AuraBooster] requestedImpetus={}, consumed={}, requestedEssentia={}, consumed={}, ratio={}",
                requestedImpetus, consumedImpetus, requestedEssentia, consumedEssentia, payRatio
        );
    }

    private float clamp01(float value) {
        if (value < 0.0F) return 0.0F;
        if (value > 1.0F) return 1.0F;
        return value;
    }

    private void notifyEssentiaChange() {
        if (world != null) {
            world.notifyNeighborsOfStateChange(pos, getBlockType(), true);
        }
    }

    private static boolean isPearl(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item pearl = Item.getByNameOrId(PEARL_ID);
        return pearl != null && stack.getItem() == pearl;
    }

    private void markDirtyAndSync() {
        markDirty();
        if (world != null) {
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        if (compound.hasKey("Inventory")) {
            inventory.deserializeNBT(compound.getCompoundTag("Inventory"));
        }
        impetusPulseTicks = compound.getInteger("ImpetusPulse");
        essentiaAmount = compound.getInteger("Essentia");
        pearlUseCounter = compound.getInteger("PearlUse");
        essentiaCostCurrent = compound.getInteger("HeatEssCost");
        impetusCostCurrent = compound.getInteger("HeatImpCost");
        lastWorkTick = compound.getLong("HeatLastWork");
        if (essentiaCostCurrent <= 0) essentiaCostCurrent = ESSENTIA_BASE_COST;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setTag("Inventory", inventory.serializeNBT());
        compound.setInteger("ImpetusPulse", impetusPulseTicks);
        compound.setInteger("Essentia", essentiaAmount);
        compound.setInteger("PearlUse", pearlUseCounter);
        compound.setInteger("HeatEssCost", essentiaCostCurrent);
        compound.setInteger("HeatImpCost", impetusCostCurrent);
        compound.setLong("HeatLastWork", lastWorkTick);
        return compound;
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
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        Capability<?> impCap = ImpetusCompat.getImpetusNodeCapability();
        if (impCap != null && capability == impCap) return true;
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return true;
        return super.hasCapability(capability, facing);
    }

    @Nullable
    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        Capability<?> impCap = ImpetusCompat.getImpetusNodeCapability();
        if (impCap != null && capability == impCap) {
            if (impetusNode == null && ImpetusCompat.isLoaded()) {
                impetusNode = ImpetusCompat.createBufferedConsumerNode(this, 4, 4, 200000L);
                ImpetusCompat.updateNodeLocation(impetusNode, world, pos);
                ImpetusCompat.initNode(impetusNode, world);
            }
            @SuppressWarnings("unchecked")
            T cast = (T) impetusNode;
            return cast;
        }
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(inventory);
        }
        return super.getCapability(capability, facing);
    }

    // ========================= IEssentiaTransport =========================

    @Override
    public boolean isConnectable(EnumFacing face) {
        return true;
    }

    @Override
    public boolean canInputFrom(EnumFacing face) {
        return true;
    }

    @Override
    public boolean canOutputTo(EnumFacing face) {
        return false;
    }

    @Override
    public void setSuction(Aspect aspect, int amount) {}

    @Override
    public Aspect getSuctionType(EnumFacing face) {
        return essentiaAmount < ESSENTIA_CAP ? REQUIRED_ASPECT : null;
    }

    @Override
    public int getSuctionAmount(EnumFacing face) {
        return essentiaAmount < ESSENTIA_CAP ? ESSENTIA_SUCTION : 0;
    }

    @Override
    public int addEssentia(Aspect aspect, int amount, EnumFacing face) {
        if (aspect != REQUIRED_ASPECT || amount <= 0) return 0;
        int can = Math.min(amount, ESSENTIA_CAP - essentiaAmount);
        if (can <= 0) return 0;
        essentiaAmount += can;
        markDirtyAndSync();
        notifyEssentiaChange();
        return can;
    }

    @Override
    public int takeEssentia(Aspect aspect, int amount, EnumFacing face) {
        return 0;
    }

    @Override
    public Aspect getEssentiaType(EnumFacing face) {
        return essentiaAmount > 0 ? REQUIRED_ASPECT : null;
    }

    @Override
    public int getEssentiaAmount(EnumFacing face) {
        return essentiaAmount;
    }

    @Override
    public int getMinimumSuction() {
        return 8;
    }

    private long consumeImpetus(long requested, boolean simulate) {
        if (!ImpetusCompat.isLoaded() || requested <= 0) return 0L;
        if (impetusNode == null) return 0L;

        // validate ВСЕГДА (и на simulate тоже), чтобы сеть/входы были актуальны
        if (world != null) {
            ImpetusCompat.validateNode(impetusNode, world);
        }

        long consumed = ImpetusCompat.consumeFromNode(impetusNode, world, requested, simulate);

        if (!simulate && consumed > 0 && world != null && !world.isRemote) {
            impetusPulseTicks = 10;
            markDirtyAndSync();
        }

        return consumed;
    }

    @Override
    public void registerControllers(AnimationData data) {
        data.addAnimationController(new AnimationController<>(this, "controller", 0, this::predicate));
    }

    private <E extends IAnimatable> PlayState predicate(AnimationEvent<E> event) {
        event.getController().setAnimation(
                new AnimationBuilder().addAnimation("animation.aura_booster.idle", true)
        );
        return PlayState.CONTINUE;
    }

    @Override
    public AnimationFactory getFactory() {
        return factory;
    }
}
