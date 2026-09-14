package therealpant.thaumicattempts.world.tile;

import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;
import therealpant.thaumicattempts.world.item.ParadoxumContainer;
import therealpant.thaumicattempts.world.tile.automation.ParadoxPedestalAutomation;
import therealpant.thaumicattempts.world.tile.automation.ParadoxPedestalAutomationHost;

import javax.annotation.Nullable;
import thaumcraft.api.items.ItemsTC;
import therealpant.thaumicattempts.integration.rift.RiftReactor;
import therealpant.thaumicattempts.integration.rift.RiftWellArea;
import therealpant.thaumicattempts.world.block.DrainageNodeStructure;

public class TileDrainageNode extends TileEntity implements ITickable, IAnimatable,
        ParadoxPedestalAutomationHost, thaumcraft.api.items.IGogglesDisplayExtended {
    public static final double WELL_RANGE_SQ = 16.0D * 16.0D;
    private static final int WELL_SEARCH_INTERVAL = 40;
    private static final String ITEM_TAG = "HeldItem";

    private final AnimationFactory factory = new AnimationFactory(this);
    private final ParadoxPedestalAutomation pedestalAutomation =
            new ParadoxPedestalAutomation(this, this);
    private ItemStack heldItem = ItemStack.EMPTY;
    private BlockPos cachedWellPos;
    private int ticks;
    private boolean automatePearls;
    private long servicedAt = -100;
    private double efficiency;
    private double outputRate;
    private double tension;
    private long lastImpactTick = Long.MIN_VALUE;
    private long activatedAt = Long.MIN_VALUE;
    private long workingUntil = Long.MIN_VALUE;
    private long readyAt = Long.MIN_VALUE;
    private int impactPulse;
    private int activitySerial;
    private int activityType;
    private double runeLockPhase;
    private double lastAccepted;
    private double condensedTension;

    @Override
    public void update() {
        if (world == null || world.isRemote) return;
        ticks++;
        // Upgrade old worlds opportunistically without overwriting blocks built above the node.
        if (ticks == 1 && DrainageNodeStructure.canForm(world,pos))
            DrainageNodeStructure.form(world,pos);
        if (ticks == 1 || ticks % WELL_SEARCH_INTERVAL == 0) cachedWellPos = findNearestWell();
        pedestalAutomation.tick();
    }

    @Nullable
    private BlockPos findNearestWell() {
        java.util.List<TileRiftWell> wells = RiftWellArea.nearby(world, pos, 16, TileRiftWell.class);
        return wells.isEmpty() ? null : wells.get(0).getPos().toImmutable();
    }

    private boolean isUsableWell(BlockPos wellPos) {
        return wellPos.distanceSq(pos) <= WELL_RANGE_SQ
                && world.isBlockLoaded(wellPos)
                && world.getTileEntity(wellPos) instanceof TileRiftWell;
    }

    public ItemStack getHeldItem() { return heldItem; }

    public static boolean isPearl(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == ItemsTC.primordialPearl && stack.getItemDamage() < 8;
    }
    public static boolean accepts(ItemStack stack) { return isPearl(stack) || ParadoxumContainer.isContainer(stack); }
    public int getPearlDurability() { return isPearl(heldItem) ? Math.max(0, 8 - heldItem.getItemDamage()) : 0; }
    public double getPearlEfficiency() { return RiftReactor.pearlEfficiency(getPearlDurability()); }
    public boolean hasCollectorInstalled() {
        return ParadoxumContainer.isContainer(heldItem) && !ParadoxumContainer.isFull(heldItem)
                && !pedestalAutomation.isSlotBusy(0);
    }
    public boolean hasStabilizerInstalled() {
        return getPearlDurability() > 0 && !pedestalAutomation.isSlotBusy(0);
    }
    private long now() { return world == null ? 0 : world.getTotalWorldTime(); }
    public boolean isWorking() { return now() < workingUntil; }
    public boolean isRecovering() { return now() >= workingUntil && now() < readyAt; }
    public boolean isReady() { return now() >= readyAt; }
    /** A global cycle uses the whole twelve-slot contour; animation recovery does not remove a slot. */
    public boolean isCollector() { return hasCollectorInstalled(); }
    public boolean isDamper() { return hasStabilizerInstalled(); }
    public boolean isBarrierNode() {
        return hasCollectorInstalled() || hasStabilizerInstalled();
    }
    public boolean belongsTo(BlockPos well) {
        if (cachedWellPos == null || !isUsableWell(cachedWellPos)) cachedWellPos = findNearestWell();
        return well.equals(cachedWellPos);
    }
    public void beginGlobalDamping() {
        if (hasStabilizerInstalled()) activate(2, 40);
    }
    public double beginGlobalCollecting(double burstStrength) {
        if (!hasCollectorInstalled() || burstStrength <= 0) return 0;
        double space=Math.max(0,ParadoxumContainer.getCapacity(heldItem)
                -ParadoxumContainer.getStored(heldItem)-condensedTension);
        double accepted=Math.min(Math.min(burstStrength,
                RiftReactor.COLLECTOR_PULSE_CAPACITY),space);
        if (accepted<=0) return 0;
        condensedTension+=accepted;
        int requested=Math.min(64,(int)Math.floor(condensedTension+1.0E-9D));
        if (requested>0) {
            int added=ParadoxumContainer.add(heldItem,requested);
            condensedTension=Math.max(0,condensedTension-added);
        }
        lastAccepted=accepted;
        lastImpactTick=now();
        impactPulse++;
        activate(1,40);
        return accepted;
    }
    private void activate(int type,int cooldownTicks) {
        long tick=now();
        activityType=type;
        activatedAt=tick;
        workingUntil=tick+15;
        readyAt=tick+Math.max(15,cooldownTicks);
        activitySerial++;
        long mixed=pos.toLong()^activitySerial*0x9E3779B97F4A7C15L;
        runeLockPhase=(Math.floorMod(mixed,10000L))/10000.0D;
        markDirtyAndSync();
    }
    public void service(double coherence, double rate, double currentTension) {
        servicedAt = world.getTotalWorldTime();
        efficiency = coherence * (hasStabilizerInstalled() ? getPearlEfficiency() : 1.0D);
        outputRate = rate; tension = currentTension; markDirtyAndSync();
    }
    public void damagePearl() {
        if (!isPearl(heldItem)) return;
        int remaining = getPearlDurability();
        if (remaining <= 1) heldItem = ItemStack.EMPTY;
        else heldItem.setItemDamage(heldItem.getItemDamage() + 1);
        world.playSound(null, pos, net.minecraft.init.SoundEvents.ENTITY_ITEM_BREAK,
                net.minecraft.util.SoundCategory.BLOCKS, 0.8F, 0.7F);
        if (world instanceof net.minecraft.world.WorldServer)
            ((net.minecraft.world.WorldServer) world).spawnParticle(net.minecraft.util.EnumParticleTypes.ITEM_CRACK,
                    pos.getX()+0.5D, pos.getY()+31.0D/16.0D, pos.getZ()+0.5D,
                    12, 0.15D, 0.15D, 0.15D, 0.05D, net.minecraft.item.Item.getIdFromItem(ItemsTC.primordialPearl), 8-remaining);
        markDirtyAndSync();
    }
    public boolean isServiced() { return world != null && world.getTotalWorldTime() - servicedAt <= 40
            && (hasCollectorInstalled() || hasStabilizerInstalled()); }
    public double getEfficiency() { return isServiced() ? efficiency : 0; }
    public double getOutputRate() { return isServiced() ? outputRate : 0; }
    public double getTension() { return isServiced() ? tension : 0; }
    public long getLastImpactTick() { return lastImpactTick; }
    public int getImpactPulse() { return impactPulse; }
    public int getActivitySerial() { return activitySerial; }
    public int getActivityType() { return activityType; }
    public double getRuneLockPhase() { return runeLockPhase; }
    public double getLastAccepted() { return lastAccepted; }
    public long getLastActivationTick() { return activatedAt; }
    public int getRemainingRecoveryTicks() { return (int)Math.max(0,readyAt-now()); }
    public BlockPos getWellPosition() { return cachedWellPos; }
    public String getModeKey() { return "thaumicattempts.drainage.mode." + (isWorking() ? "working"
            : isRecovering() ? "recovering" : isDamper() ? "damping"
            : isCollector() ? "collecting" : isBarrierNode() ? "barrier" : "inactive"); }
    public net.minecraft.util.text.TextComponentTranslation statusText() {
        return new net.minecraft.util.text.TextComponentTranslation("thaumicattempts.drainage.status",
                new net.minecraft.util.text.TextComponentTranslation(getModeKey()),
                String.format(java.util.Locale.ROOT,"%.1f",getEfficiency()*100),
                String.format(java.util.Locale.ROOT,"%.2f",lastAccepted),getPearlDurability(),
                String.format(java.util.Locale.ROOT,"%.1f",getRemainingRecoveryTicks()/20.0D));
    }
    @Override public String[] getIGogglesText() { return new String[]{statusText().getFormattedText()}; }

    public boolean insertContainer(ItemStack stack) {
        if (!heldItem.isEmpty() || pedestalAutomation.isSlotBusy(0)
                || !accepts(stack)) return false;
        heldItem = stack.copy();
        heldItem.setCount(1);
        ParadoxumContainer.normalize(heldItem);
        automatePearls = isPearl(heldItem);
        condensedTension = 0;
        pedestalAutomation.clearOrigin(0);
        cachedWellPos = null;
        markDirtyAndSync();
        return true;
    }

    public ItemStack removeContainer() {
        if (heldItem.isEmpty() || pedestalAutomation.isSlotBusy(0)) return ItemStack.EMPTY;
        ItemStack result = heldItem;
        heldItem = ItemStack.EMPTY;
        condensedTension = 0;
        pedestalAutomation.clearOrigin(0);
        markDirtyAndSync();
        return result;
    }

    @Override public void registerControllers(AnimationData data) {
        data.addAnimationController(new AnimationController<>(
                this, "drainage_controller", 0, this::animationPredicate));
    }
    private <E extends IAnimatable> PlayState animationPredicate(AnimationEvent<E> event) {
        event.getController().setAnimation(new AnimationBuilder().addAnimation("animation", true));
        return PlayState.CONTINUE;
    }
    @Override public AnimationFactory getFactory() { return factory; }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (!heldItem.isEmpty()) tag.setTag(ITEM_TAG,
                heldItem.writeToNBT(new NBTTagCompound()));
        if (cachedWellPos != null) tag.setLong("WellPos", cachedWellPos.toLong());
        tag.setBoolean("AutomatePearls", automatePearls);
        tag.setLong("ServicedAt", servicedAt);
        tag.setDouble("Efficiency", efficiency);
        tag.setDouble("OutputRate", outputRate);
        tag.setDouble("Tension", tension);
        tag.setLong("LastImpactTick",lastImpactTick);
        tag.setLong("ActivatedAt",activatedAt); tag.setLong("WorkingUntil",workingUntil);
        tag.setLong("ReadyAt",readyAt); tag.setInteger("ActivityType",activityType);
        tag.setInteger("ActivitySerial",activitySerial); tag.setDouble("RuneLockPhase",runeLockPhase);
        tag.setDouble("LastAccepted",lastAccepted);
        tag.setInteger("ImpactPulse",impactPulse);
        tag.setDouble("CondensedTension",condensedTension);
        pedestalAutomation.writeToNBT(tag);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        heldItem = tag.hasKey(ITEM_TAG, 10)
                ? new ItemStack(tag.getCompoundTag(ITEM_TAG)) : ItemStack.EMPTY;
        if (!heldItem.isEmpty()) heldItem.setCount(1);
        automatePearls = tag.getBoolean("AutomatePearls") || isPearl(heldItem);
        servicedAt = tag.hasKey("ServicedAt") ? tag.getLong("ServicedAt") : -100;
        efficiency = tag.getDouble("Efficiency"); outputRate = tag.getDouble("OutputRate"); tension = tag.getDouble("Tension");
        lastImpactTick = tag.hasKey("LastImpactTick") ? tag.getLong("LastImpactTick") : Long.MIN_VALUE;
        activatedAt=tag.hasKey("ActivatedAt") ? tag.getLong("ActivatedAt") : Long.MIN_VALUE;
        workingUntil=tag.hasKey("WorkingUntil") ? tag.getLong("WorkingUntil") : Long.MIN_VALUE;
        readyAt=tag.hasKey("ReadyAt") ? tag.getLong("ReadyAt") : Long.MIN_VALUE;
        activityType=tag.getInteger("ActivityType"); activitySerial=tag.getInteger("ActivitySerial");
        runeLockPhase=RiftReactor.clamp(tag.getDouble("RuneLockPhase"),0,1);
        lastAccepted=Math.max(0,tag.getDouble("LastAccepted"));
        impactPulse = tag.getInteger("ImpactPulse");
        condensedTension = RiftReactor.clamp(tag.getDouble("CondensedTension"),0,0.999999999D);
        cachedWellPos = tag.hasKey("WellPos", 4)
                ? BlockPos.fromLong(tag.getLong("WellPos")) : null;
        pedestalAutomation.readFromNBT(tag);
    }

    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Nullable @Override public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        readFromNBT(packet.getNbtCompound());
    }

    private void markDirtyAndSync() {
        markDirty();
        if (world != null) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
            world.updateComparatorOutputLevel(pos, state.getBlock());
        }
    }

    public ParadoxPedestalAutomation getPedestalAutomation() { return pedestalAutomation; }

    @Override public int getAutomationSlotCount() { return 1; }
    @Override public ItemStack getAutomationStack(int slot) {
        return slot == 0 ? heldItem : ItemStack.EMPTY;
    }
    @Override public boolean canAcceptAutomatedStack(ItemStack stack) {
        return automatePearls ? isPearl(stack) : ParadoxumContainer.isContainer(stack) && !ParadoxumContainer.isFull(stack);
    }
    @Override public boolean isAutomatedStackComplete(ItemStack stack) {
        return ParadoxumContainer.isContainer(stack) && ParadoxumContainer.isFull(stack);
    }
    @Override public boolean insertAutomatedStack(int slot, ItemStack stack) {
        if (slot != 0 || !heldItem.isEmpty() || !accepts(stack)) return false;
        heldItem = stack.copy();
        heldItem.setCount(1);
        ParadoxumContainer.normalize(heldItem);
        automatePearls = isPearl(heldItem);
        condensedTension = 0;
        cachedWellPos = null;
        markDirtyAndSync();
        return true;
    }
    @Override public ItemStack extractAutomatedStack(int slot) {
        if (slot != 0 || heldItem.isEmpty()) return ItemStack.EMPTY;
        ItemStack result = heldItem;
        heldItem = ItemStack.EMPTY;
        condensedTension = 0;
        markDirtyAndSync();
        return result;
    }
    @Override public Vec3d getAutomationSlotPosition(int slot) {
        return new Vec3d(pos.getX() + 0.5D, pos.getY() + 31.0D / 16.0D,
                pos.getZ() + 0.5D);
    }
    @Override public void onAutomationChanged() { markDirtyAndSync(); }
    @Override public boolean hasPedestalReservation(BlockPos pedestalPos) {
        return pedestalAutomation.isDestination(pedestalPos);
    }

    @Override public AxisAlignedBB getRenderBoundingBox() {
        return new AxisAlignedBB(pos).grow(17.0D);
    }
    @Override public double getMaxRenderDistanceSquared() { return 65536.0D; }
}
