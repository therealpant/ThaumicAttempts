package therealpant.thaumicattempts.world.tile;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;
import software.bernie.geckolib3.core.*;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.*;
import thaumcraft.api.items.IGogglesDisplayExtended;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.entities.EntityFluxRift;
import therealpant.thaumicattempts.integration.rift.*;
import therealpant.thaumicattempts.items.ItemInterceptorStone;
import therealpant.thaumicattempts.init.ModBlocksItems;
import therealpant.thaumicattempts.world.item.ParadoxumContainer;
import therealpant.thaumicattempts.world.block.RiftWellLiquidSurface;
import javax.annotation.Nullable;
import java.util.*;

public class TileRiftWell extends TileEntity implements ITickable, IAnimatable, IGogglesDisplayExtended {
    public static final double LIQUID_SURFACE_Y = RiftWellLiquidSurface.HEIGHT;
    public static final double RIFT_HORIZONTAL_RADIUS = RiftWellGeometry.CYLINDER_RADIUS;
    public static final double RIFT_VERTICAL_RADIUS = RiftWellGeometry.CYLINDER_HALF_HEIGHT;
    public static final double RIFT_CENTER_Y_OFFSET = 1.0D + RIFT_VERTICAL_RADIUS;
    public static final int MAX_INTERCEPTOR_STONES = 8;
    public static final int AWAKENING_START_SIZE = 5;
    public static final int AWAKENING_TARGET_SIZE = 50;
    public static final int AWAKENING_GROWTH_INTERVAL = 5;
    private final AnimationFactory factory = new AnimationFactory(this);
    private final RiftReactor reactor = new RiftReactor();
    private final List<BlockPos> linkedMonoliths = new ArrayList<>();
    private UUID riftId;
    private transient EntityFluxRift cachedRift;
    private int ticks, absentTicks, awakeningTicks, particleFlowTicks;
    private boolean awakeningRift, contaminated, cleansingPaused;
    private boolean emergencyCollapse;
    private int cleanupTicks;
    private double corruptedCharge;
    private ItemStack cleansingPearl = ItemStack.EMPTY;
    private int legacyEssentia;
    private int collectorCount, damperCount;
    private int activeDrainCount;
    private final List<BlockPos> lastEmissionTargets = new ArrayList<>();
    private double capacity, incoming;
    private boolean initialized;

    @Override public void onLoad() {
        super.onLoad();
        initialized = false;
    }

    @Override public void update() {
        if (world == null) return;
        ticks++;
        if (particleFlowTicks > 0) particleFlowTicks--;
        if (world.isRemote) {
            EntityFluxRift rift = resolveClientRift();
            if (rift != null && !rift.getCollapse()) RiftWellShapeHooks.bind(this, rift);
            return;
        }
        if (!initialized) {
            RiftWellWorldData saved = RiftWellWorldData.get(world);
            if (saved.isContaminated(pos)) {
                contaminated = true;
                if (riftId == null) riftId = saved.rift(pos);
            } else if (contaminated) saved.contaminate(pos, riftId);
            initialized = true;
        }
        if (ticks % 20 == 0) migrateLegacyContents();
        EntityFluxRift rift = resolveRift();
        if (rift == null) absentTicks++; else absentTicks = 0;

        if (contaminated) {
            reactor.tension = 100;
            if (rift != null) {
                int previousSize = (int)Math.round(reactor.size);
                reactor.size = rift.getRiftSize();
                if (emergencyCollapse && previousSize > rift.getRiftSize())
                    corruptedCharge = Math.min(RiftReactor.MAX_PARTICLE_GROWN_SIZE,
                            corruptedCharge + previousSize - rift.getRiftSize());
            } else emergencyCollapse = false;
            if (rift != null && !rift.getCollapse()) {
                emergencyCollapse = true;
                RiftWellLifecycle.shutdownIfNeeded(rift, pos);
                RiftWellShapeHooks.bind(this, rift);
            }
            if (ticks % 5 == 0) tickCleansing();
            if (ticks % 20 == 0) markDirtyAndSync();
            return;
        }
        if (rift == null) {
            if (absentTicks > 40) {
                riftId = null; awakeningRift = false;
                reactor.size = 0;
                reactor.input = reactor.emission = reactor.absorbed = reactor.load = 0;
                reactor.growthBuffer = reactor.pulseClock = 0;
                reactor.excitation = 0;
                capacity = 0; collectorCount = damperCount = activeDrainCount = 0;
                reactor.tension = Math.max(0, reactor.tension - 0.2D);
                reactor.criticalSeconds = reactor.tension;
                reactor.critical = false;
                if (ticks % 5 == 0) tryAwakenRift();
            }
            if (ticks % 20 == 0) markDirtyAndSync();
            return;
        }
        if (rift.getCollapse()) return; // An external TC/TA closing operation has priority.
        RiftWellLifecycle.manage(rift, pos);
        RiftWellShapeHooks.bind(this, rift);
        if (awakeningRift && ++awakeningTicks >= AWAKENING_GROWTH_INTERVAL) {
            awakeningTicks = 0;
            reactor.size = Math.min(AWAKENING_TARGET_SIZE, rift.getRiftSize() + 1);
            rift.setRiftSize((int) reactor.size);
            if (reactor.size >= AWAKENING_TARGET_SIZE) awakeningRift = false;
        }
        if (ticks % 20 == 0) tickReactor(rift);
    }

    private void tickReactor(EntityFluxRift rift) {
        if (Math.round(reactor.size) != rift.getRiftSize()) reactor.size = rift.getRiftSize();
        List<TileDrainageNode> drains = new ArrayList<>();
        for (TileDrainageNode node : RiftWellArea.nearby(world, pos, 16, TileDrainageNode.class)) {
            if ((node.hasCollectorInstalled() || node.hasStabilizerInstalled()) && node.belongsTo(pos)) {
                drains.add(node);
                if (drains.size() == RiftReactor.MAX_DRAINS) break;
            }
        }
        final List<TileDrainageNode> collectors = new ArrayList<>();
        double pearlEfficiency = 0.0D;
        for (TileDrainageNode node : drains) {
            if (node.hasCollectorInstalled()) collectors.add(node);
            if (node.hasStabilizerInstalled()) pearlEfficiency += node.getPearlEfficiency();
        }
        incoming = collectLinks();
        final Set<TileDrainageNode> usedCollectors = new HashSet<>();
        final int[] fallbackIndex = {0};
        reactor.step((int)incoming,collectors.size(),pearlEfficiency,1.0D,awakeningRift,
                new RiftReactor.CycleHandler() {
            @Override public void beginCycle(int arcCount,double burstStrength) {
                lastEmissionTargets.clear();
                usedCollectors.clear();
                for (TileDrainageNode node:drains)
                    if (node.hasStabilizerInstalled()) node.beginGlobalDamping();
            }
            @Override public double collect(double burstStrength,int arcIndex,int arcCount) {
                TileDrainageNode node=oldestUnusedCollector(collectors,usedCollectors);
                if (node==null) {
                    TileDrainageNode fallback=drains.isEmpty() ? null
                            : drains.get(fallbackIndex[0]++%drains.size());
                    lastEmissionTargets.add(fallback == null ? pos.toImmutable()
                            : fallback.getPos().toImmutable());
                    return 0;
                }
                usedCollectors.add(node);
                lastEmissionTargets.add(node.getPos().toImmutable());
                return node.beginGlobalCollecting(burstStrength);
            }
        });
        refreshDrainCounts(drains);
        capacity = collectorCount * RiftReactor.COLLECTOR_PULSE_CAPACITY;
        if (reactor.input > 0) particleFlowTicks = 25;
        for (TileDrainageNode node : drains) {
            double recent=node.getLastImpactTick()==world.getTotalWorldTime() ? node.getLastAccepted() : 0;
            node.service(reactor.coherence(),recent,reactor.tension);
        }
        if (reactor.pulses > 0 && reactor.tension >= 75 && reactor.pulseSerial % 4 == 0)
            damagePearl(drains);
        if (reactor.size < 1) { rift.setDead(); riftId = null; cachedRift = null; awakeningRift = false; }
        else if (rift.getRiftSize() != (int) Math.round(reactor.size)) rift.setRiftSize((int) Math.round(reactor.size));
        if (reactor.breached() && !rift.isDead) beginEmergencyCollapse(rift);
        markDirtyAndSync();
    }

    @Nullable private TileDrainageNode oldestUnusedCollector(List<TileDrainageNode> nodes,
                                                              Set<TileDrainageNode> used) {
        TileDrainageNode result=null;
        for (TileDrainageNode node:nodes) {
            if (used.contains(node) || !node.hasCollectorInstalled()) continue;
            if (result==null || node.getLastActivationTick()<result.getLastActivationTick()
                    || node.getLastActivationTick()==result.getLastActivationTick()
                    && node.getPos().toLong()<result.getPos().toLong()) result=node;
        }
        return result;
    }

    private void refreshDrainCounts(List<TileDrainageNode> drains) {
        activeDrainCount=collectorCount=damperCount=0;
        for (TileDrainageNode node:drains) {
            if (node.isBarrierNode()) activeDrainCount++;
            if (node.isCollector()) collectorCount++;
            if (node.isDamper()) damperCount++;
        }
    }

    private void damagePearl(List<TileDrainageNode> nodes) {
        double sum = 0;
        for (TileDrainageNode node : nodes) if (node.hasStabilizerInstalled()) sum += 1.0D / node.getPearlEfficiency();
        if (sum == 0) return;
        double draw = world.rand.nextDouble() * sum;
        for (TileDrainageNode node : nodes) {
            if (!node.hasStabilizerInstalled()) continue;
            draw -= 1.0D / node.getPearlEfficiency();
            if (draw <= 0) { node.damagePearl(); break; }
        }
    }

    private int collectLinks() {
        int result = 0;
        Iterator<BlockPos> it = linkedMonoliths.iterator();
        while (it.hasNext()) {
            BlockPos source = it.next();
            if (!world.isBlockLoaded(source)) continue;
            TileEntity raw = world.getTileEntity(source);
            if (!(raw instanceof TileAnomalousMonolith) || !((TileAnomalousMonolith) raw).hasInterceptorStone()) {
                it.remove();
                continue;
            }
            TileAnomalousMonolith monolith = (TileAnomalousMonolith) raw;
            monolith.drainInterceptedParticles(Integer.MAX_VALUE);
            result++;
        }
        return result;
    }

    private void beginEmergencyCollapse(EntityFluxRift rift) {
        contaminated = true; awakeningRift = false; reactor.tension = 100;
        reactor.size = rift.getRiftSize();
        emergencyCollapse = true;
        corruptedCharge = 0;
        RiftWellLifecycle.shutdown(rift, pos);
        RiftWellWorldData.get(world).contaminate(pos, riftId);
        world.playSound(null, pos, SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.BLOCKS, 1, 0.5F);
        markDirtyAndSync();
    }

    public void onControllerBroken() {
        if (world == null || world.isRemote) return;
        EntityFluxRift rift = resolveRift();
        if (rift != null && !rift.getCollapse()) RiftWellLifecycle.shutdown(rift,pos);
        cleansingPearl = ItemStack.EMPTY;
    }

    private AxisAlignedBB liquidBounds() {
        return new AxisAlignedBB(pos.getX()-3.25, pos.getY()+0.15, pos.getZ()-3.25,
                pos.getX()+4.25, pos.getY()+LIQUID_SURFACE_Y+0.475, pos.getZ()+4.25);
    }
    private boolean inLiquid(EntityItem item) {
        return !item.isDead && RiftWellLiquidSurface.contains(item.posX-pos.getX(), item.posZ-pos.getZ());
    }

    private void tryAwakenRift() {
        if (contaminated || !world.getEntitiesWithinAABB(EntityFluxRift.class, new AxisAlignedBB(pos).grow(16)).isEmpty()) return;
        for (EntityItem entity : world.getEntitiesWithinAABB(EntityItem.class, liquidBounds())) {
            ItemStack stack = entity.getItem();
            if (!inLiquid(entity) || stack.getItem() != ModBlocksItems.PARADOX_CORE || !ParadoxumContainer.isFull(stack)) continue;
            EntityFluxRift rift = new EntityFluxRift(world);
            Vec3d centre = getRiftCentre();
            rift.setRiftSeed(world.rand.nextInt()); rift.setRiftSize(AWAKENING_START_SIZE);
            rift.setPosition(centre.x, centre.y, centre.z); rift.setRiftStability(40);
            riftId = rift.getUniqueID(); cachedRift = rift;
            RiftWellLifecycle.manage(rift, pos);
            if (!world.spawnEntity(rift)) { riftId = null; cachedRift = null; return; }
            reactor.reset(); reactor.size = AWAKENING_START_SIZE; awakeningRift = true; awakeningTicks = 0;
            consume(entity);
            world.playSound(null, pos, SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.BLOCKS, 1, 0.65F);
            markDirtyAndSync(); return;
        }
    }

    private boolean canCleanse() {
        // Never interpret an unloaded neighbouring chunk as proof that its rift is gone.
        if (!world.isAreaLoaded(pos.add(-16,-1,-16), pos.add(16,16,16))) return false;
        if (resolveRift() != null || absentTicks < 40) return false;
        for (EntityFluxRift nearby : world.getEntitiesWithinAABB(EntityFluxRift.class, new AxisAlignedBB(pos).grow(16))) {
            if (!nearby.isDead) return false;
        }
        return true;
    }

    private void tickCleansing() {
        boolean paused = !canCleanse();
        if (cleansingPaused != paused) { cleansingPaused = paused; markDirtyAndSync(); }
        if (paused) return;
        if (cleansingPearl.isEmpty()) {
            for (EntityItem entity : world.getEntitiesWithinAABB(EntityItem.class, liquidBounds())) {
                ItemStack stack = entity.getItem();
                if (!inLiquid(entity) || !TileDrainageNode.isPearl(stack) || stack.getItemDamage() != 0) continue;
                cleansingPearl = stack.copy(); cleansingPearl.setCount(1); cleanupTicks = 0;
                consume(entity); markDirtyAndSync(); return;
            }
            return;
        }
        cleanupTicks = Math.min(RiftReactor.CLEANSE_TICKS, cleanupTicks + 5);
        if (cleanupTicks >= RiftReactor.CLEANSE_TICKS) {
            RiftWellWorldData.get(world).clean(pos);
            contaminated = false; riftId = null; cachedRift = null;
            cleansingPearl = ItemStack.EMPTY; cleanupTicks = 0;
            emergencyCollapse = false; corruptedCharge = 0;
            reactor.reset();
            ((WorldServer) world).spawnParticle(EnumParticleTypes.END_ROD, pos.getX()+0.5, pos.getY()+2, pos.getZ()+0.5,
                    40, 0.7, 0.7, 0.7, 0.03);
            world.playSound(null, pos, SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.BLOCKS, 1, 1.2F);
        }
        markDirtyAndSync();
    }
    private void consume(EntityItem item) {
        item.getItem().shrink(1);
        if (item.getItem().isEmpty()) item.setDead();
        else item.setItem(item.getItem());
    }

    private void migrateLegacyContents() {
        if (legacyEssentia <= 0) return;
        ItemStack core = new ItemStack(ModBlocksItems.PARADOX_CORE);
        int restored = ParadoxumContainer.add(core, Math.min(2500, legacyEssentia));
        if (restored <= 0) return;
        legacyEssentia -= restored;
        InventoryHelper.spawnItemStack(world, pos.getX()+4.75, pos.getY()+1.25, pos.getZ()+0.5, core);
        markDirtyAndSync();
    }

    @Nullable private EntityFluxRift resolveRift() {
        if (cachedRift != null && !cachedRift.isDead && cachedRift.world == world) return cachedRift;
        cachedRift = null;
        if (riftId != null && world instanceof WorldServer) {
            Entity entity = ((WorldServer) world).getEntityFromUuid(riftId);
            if (entity instanceof EntityFluxRift && !entity.isDead) cachedRift = (EntityFluxRift) entity;
        }
        return cachedRift;
    }
    @Nullable private EntityFluxRift resolveClientRift() {
        if (world == null || riftId == null) return null;
        if (cachedRift != null && !cachedRift.isDead && cachedRift.world == world
                && cachedRift.getUniqueID().equals(riftId)) return cachedRift;
        cachedRift = null;
        for (EntityFluxRift rift : world.getEntitiesWithinAABB(EntityFluxRift.class, new AxisAlignedBB(pos).grow(24))) {
            if (rift.getUniqueID().equals(riftId)) { cachedRift = rift; break; }
        }
        return cachedRift;
    }
    public boolean controlsRift(EntityFluxRift rift) { return rift != null && rift.getUniqueID().equals(riftId); }
    private Vec3d getRiftCentre() { return new Vec3d(pos.getX()+0.5, pos.getY()+RIFT_CENTER_Y_OFFSET, pos.getZ()+0.5); }
    public boolean addMonolithLink(BlockPos source) {
        if (contaminated || source == null || linkedMonoliths.contains(source) || linkedMonoliths.size() >= MAX_INTERCEPTOR_STONES) return false;
        linkedMonoliths.add(source.toImmutable()); markDirtyAndSync(); return true;
    }
    public List<ItemStack> takeInstalledStones() {
        List<ItemStack> result = new ArrayList<>();
        if (!contaminated && world != null) for (BlockPos source : linkedMonoliths)
            result.add(ItemInterceptorStone.createLinkedStack(world, source));
        linkedMonoliths.clear(); markDirtyAndSync(); return result;
    }
    public int getInstalledStoneCount() { return linkedMonoliths.size(); }
    public boolean hasParticleFlow() { return !contaminated && particleFlowTicks > 0; }
    public int getOverloadChancePercent() { return (int) Math.max(0, reactor.tension - 50); }
    public float getOverloadRadius() { return 0; }
    public int getRiftSize() { return (int) Math.round(reactor.size); }
    public double getTension() { return reactor.tension; }
    public double getReactionExcitation() { return reactor.excitation; }
    public int getActiveDrainCount() { return activeDrainCount; }
    public double getEmissionPerSecond() { return reactor.emission; }
    public double getUnquenchedRatio() { return reactor.unrelieved; }
    public int getEmissionSerial() { return reactor.pulseSerial; }
    public double getLastPulseStrength() { return reactor.lastPulseStrength; }
    public int getLastArcCount() { return reactor.lastArcCount; }
    public double getGrowthProgress() {
        return RiftReactor.clamp(reactor.growthBuffer,-1,1);
    }
    public double getEquilibriumSize() { return reactor.targetSize; }
    public double getEmissionProgress() {
        return reactor.eventProgress();
    }
    public boolean isContaminated() { return contaminated; }
    public boolean isCleansing() { return !cleansingPearl.isEmpty(); }
    public boolean isCleansingPaused() { return cleansingPaused; }
    public ItemStack getCleansingPearl() { return cleansingPearl; }
    public float getCleansingProgress() { return cleanupTicks / (float) RiftReactor.CLEANSE_TICKS; }
    public float getLiquidDarkness() {
        if (contaminated) {
            float p = getCleansingProgress(); p = p*p*(3-2*p);
            double total=corruptedCharge+(emergencyCollapse ? Math.max(0,reactor.size) : 0);
            float charged=emergencyCollapse && total>0
                    ? (float)(corruptedCharge/total) : 1.0F;
            return 0.85F*charged*(1-p);
        }
        return (float) (0.75D*Math.pow(reactor.tension/100.0D, 1.3D));
    }
    @Nullable public Vec3d getRandomVisualTarget(Random random) {
        if (cachedRift == null || cachedRift.isDead || cachedRift.points.isEmpty()) return null;
        Vec3d point = cachedRift.points.get(random.nextInt(cachedRift.points.size()));
        return point.add(cachedRift.posX-pos.getX(), cachedRift.posY-pos.getY(), cachedRift.posZ-pos.getZ());
    }
    public List<Vec3d> getVisualRiftSection(Random random, int wanted) {
        List<Vec3d> result = new ArrayList<>();
        // A tile update can arrive between the client tick and the render pass.
        if (world != null && world.isRemote) resolveClientRift();
        if (cachedRift == null || cachedRift.isDead || cachedRift.points.isEmpty()) return result;
        int size = cachedRift.points.size();
        int count = Math.max(1,Math.min(wanted,size));
        // The last point becomes the discharge origin. Keep it in the middle
        // third of the rift instead of allowing either tip to fire.
        int minEnd = Math.max(count-1,(int)Math.ceil((size-1)*0.35D));
        int maxEnd = Math.max(minEnd,(int)Math.floor((size-1)*0.65D));
        maxEnd = Math.min(size-1,maxEnd);
        int end = minEnd + (maxEnd == minEnd ? 0 : random.nextInt(maxEnd-minEnd+1));
        int start = Math.max(0,end-count+1);
        Vec3d offset = new Vec3d(cachedRift.posX-pos.getX(),cachedRift.posY-pos.getY(),cachedRift.posZ-pos.getZ());
        for (int i=0;i<count;i++) result.add(cachedRift.points.get(start+i).add(offset));
        return result;
    }
    @Nullable public Vec3d getEmissionVisualTarget(int arcIndex,Random random) {
        if (world == null) return null;
        if (arcIndex>=0 && arcIndex<lastEmissionTargets.size()) {
            BlockPos drain=lastEmissionTargets.get(arcIndex);
            if (drain.equals(pos)) {
                double angle=random.nextDouble()*Math.PI*2.0D;
                return new Vec3d(0.5D+Math.cos(angle)*RiftBarrierGeometry.RADIUS,
                        0.8D+random.nextDouble()*(RiftBarrierGeometry.HEIGHT-1.6D),
                        0.5D+Math.sin(angle)*RiftBarrierGeometry.RADIUS);
            }
            if (!world.isBlockLoaded(drain)) return null;
            return new Vec3d(drain.getX()-pos.getX()+0.5D,
                    drain.getY()-pos.getY()+31.0D/16.0D,
                    drain.getZ()-pos.getZ()+0.5D);
        }
        List<TileDrainageNode> candidates = new ArrayList<>();
        for (TileDrainageNode node : RiftWellArea.nearby(world,pos,16,TileDrainageNode.class)) {
            BlockPos well = node.getWellPosition();
            if (node.isBarrierNode() && pos.equals(well)) candidates.add(node);
        }
        if (candidates.isEmpty()) return null;
        BlockPos drain = candidates.get(random.nextInt(candidates.size())).getPos();
        return new Vec3d(drain.getX()-pos.getX()+0.5D,
                drain.getY()-pos.getY()+31.0D/16.0D,
                drain.getZ()-pos.getZ()+0.5D);
    }
    public Vec3d getRandomLiquidPoint(Random random) { return RiftWellLiquidSurface.randomPoint(random); }
    public String getWorkingModeKey() {
        String mode = contaminated ? isCleansing() ? cleansingPaused ? "cleansing_paused" : "cleansing"
                : cachedRift != null ? "shutdown" : "contaminated"
                : reactor.critical ? "critical" : awakeningRift ? "awakening"
                : getRiftSize() == 0 ? "waiting"
                : reactor.load > 1.0D || reactor.tension >= 50 ? "overloaded"
                : reactor.load >= 0.85D ? "tense" : "running";
        return "thaumicattempts.rift_well.mode." + mode;
    }
    public TextComponentTranslation statusTextPrimary() {
        return new TextComponentTranslation("thaumicattempts.rift_well.reactor_status.1",
                new TextComponentTranslation(getWorkingModeKey()), getRiftSize(),
                String.format(Locale.ROOT,"%.1f",getEquilibriumSize()),
                String.format(Locale.ROOT,"%.1f",reactor.sizeLimit),
                String.format(Locale.ROOT,"%.1f",reactor.tension));
    }
    public TextComponentTranslation statusTextSecondary() {
        return new TextComponentTranslation("thaumicattempts.rift_well.reactor_status.2",
                linkedMonoliths.size(),collectorCount,damperCount,reactor.lastArcCount,
                String.format(Locale.ROOT,"%.2f",reactor.lastPulseStrength),
                String.format(Locale.ROOT,"%.0f",reactor.load*100),
                String.format(Locale.ROOT,"%.0f",getEmissionProgress()*100),
                String.format(Locale.ROOT,"%.1f",reactor.absorbed));
    }
    @Override public String[] getIGogglesText() { return new String[]{
            statusTextPrimary().getFormattedText(),statusTextSecondary().getFormattedText()}; }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("ReactorVersion",6);
        tag.setDouble("ReactorSize",reactor.size); tag.setDouble("Tension",reactor.tension);
        tag.setDouble("Excitation",reactor.excitation); tag.setDouble("Wear",reactor.wear);
        tag.setDouble("CriticalSeconds",reactor.criticalSeconds);
        tag.setBoolean("Critical",reactor.critical); tag.setDouble("Input",reactor.input);
        tag.setDouble("Emission",reactor.emission); tag.setDouble("Capacity",capacity);
        tag.setDouble("Absorbed",reactor.absorbed); tag.setDouble("GrowthBuffer",reactor.growthBuffer);
        tag.setDouble("PulseClock",reactor.pulseClock); tag.setDouble("DecayBuffer",reactor.decayBuffer);
        tag.setDouble("Unrelieved",reactor.unrelieved); tag.setDouble("LastPulseStrength",reactor.lastPulseStrength);
        tag.setInteger("LastArcCount",reactor.lastArcCount); tag.setInteger("PulseSerial",reactor.pulseSerial);
        tag.setDouble("TargetSize",reactor.targetSize); tag.setDouble("SizeLimit",reactor.sizeLimit);
        tag.setInteger("RawBurstCount",reactor.rawBurstCount); tag.setDouble("Load",reactor.load);
        tag.setInteger("ActiveDrains",activeDrainCount);
        tag.setInteger("Collectors",collectorCount); tag.setInteger("Dampers",damperCount);
        NBTTagList emissionTargets = new NBTTagList();
        for (BlockPos target:lastEmissionTargets) {
            NBTTagCompound entry=new NBTTagCompound(); entry.setLong("Position",target.toLong());
            emissionTargets.appendTag(entry);
        }
        tag.setTag("LastEmissionTargets",emissionTargets);
        tag.setInteger("LegacyEssentia",legacyEssentia);
        tag.setBoolean("Contaminated",contaminated);
        tag.setBoolean("EmergencyCollapse",emergencyCollapse);
        tag.setDouble("CorruptedCharge",corruptedCharge);
        tag.setInteger("CleanupTicks",cleanupTicks);
        tag.setBoolean("CleansingPaused",cleansingPaused);
        if (!cleansingPearl.isEmpty()) tag.setTag("CleansingPearl",cleansingPearl.writeToNBT(new NBTTagCompound()));
        tag.setBoolean("AwakeningRift",awakeningRift); tag.setInteger("AwakeningTicks",awakeningTicks);
        tag.setInteger("ParticleFlowTicks",particleFlowTicks);
        if (riftId != null) tag.setUniqueId("Rift",riftId);
        NBTTagList list = new NBTTagList();
        for (BlockPos source : linkedMonoliths) { NBTTagCompound e = new NBTTagCompound(); e.setLong("Position",source.toLong()); list.appendTag(e); }
        tag.setTag("LinkedMonoliths",list);
        return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        int reactorVersion=tag.getInteger("ReactorVersion");
        reactor.size = RiftReactor.clamp(tag.getDouble("ReactorSize"),0,100000);
        reactor.tension = RiftReactor.clamp(tag.getDouble("Tension"),0,100);
        reactor.excitation = reactorVersion>=6
                ? RiftReactor.clamp(tag.getDouble("Excitation"),0,1) : 0;
        reactor.wear = 0;
        reactor.criticalSeconds = reactor.tension;
        reactor.critical = reactorVersion>=6 && tag.getBoolean("Critical");
        reactor.input = tag.getDouble("Input");
        reactor.emission = Math.max(0,tag.getDouble("Emission"));
        reactor.absorbed=Math.max(0,tag.getDouble("Absorbed"));
        reactor.growthBuffer=reactorVersion>=6
                ? RiftReactor.clamp(tag.getDouble("GrowthBuffer"),-1,1) : 0;
        reactor.pulseClock=reactorVersion>=6
                ? RiftReactor.clamp(tag.getDouble("PulseClock"),0,20) : 0;
        reactor.decayBuffer=reactor.growthBuffer<0 ? -reactor.growthBuffer : 0;
        reactor.unrelieved=RiftReactor.clamp(tag.getDouble("Unrelieved"),0,1);
        reactor.lastPulseStrength=RiftReactor.clamp(tag.getDouble("LastPulseStrength"),0,100);
        reactor.lastArcCount=Math.max(0,Math.min(RiftReactor.MAX_DRAINS,tag.getInteger("LastArcCount")));
        reactor.pulseSerial=Math.max(0,tag.getInteger("PulseSerial"));
        reactor.targetSize=reactorVersion>=6 ? RiftReactor.clamp(tag.getDouble("TargetSize"),0,140) : reactor.size;
        reactor.sizeLimit=reactorVersion>=6 ? RiftReactor.clamp(tag.getDouble("SizeLimit"),0,140) : reactor.size;
        reactor.rawBurstCount=reactorVersion>=6 ? Math.max(0,tag.getInteger("RawBurstCount")) : 0;
        reactor.load=reactorVersion>=6 ? Math.max(0,tag.getDouble("Load")) : 0;
        capacity = Math.max(0,tag.getDouble("Capacity"));
        activeDrainCount=Math.max(0,Math.min(RiftReactor.MAX_DRAINS,tag.getInteger("ActiveDrains")));
        collectorCount = tag.getInteger("Collectors"); damperCount = tag.getInteger("Dampers");
        lastEmissionTargets.clear();
        NBTTagList targetList=tag.getTagList("LastEmissionTargets",10);
        for (int i=0;i<targetList.tagCount() && i<RiftReactor.MAX_DRAINS;i++)
            lastEmissionTargets.add(BlockPos.fromLong(targetList.getCompoundTagAt(i).getLong("Position")));
        if (lastEmissionTargets.isEmpty() && tag.hasKey("LastEmissionTarget",4))
            lastEmissionTargets.add(BlockPos.fromLong(tag.getLong("LastEmissionTarget")));
        legacyEssentia = Math.max(0,tag.getInteger(tag.hasKey("ReactorVersion") ? "LegacyEssentia" : "ParadoxumEssentia"));
        contaminated = tag.getBoolean("Contaminated");
        emergencyCollapse = tag.getBoolean("EmergencyCollapse");
        corruptedCharge = RiftReactor.clamp(tag.getDouble("CorruptedCharge"),0,
                RiftReactor.MAX_PARTICLE_GROWN_SIZE);
        cleanupTicks = (int) RiftReactor.clamp(tag.getInteger("CleanupTicks"),0,RiftReactor.CLEANSE_TICKS);
        cleansingPaused = tag.getBoolean("CleansingPaused");
        cleansingPearl = tag.hasKey("CleansingPearl",10) ? new ItemStack(tag.getCompoundTag("CleansingPearl")) : ItemStack.EMPTY;
        if (!cleansingPearl.isEmpty()) cleansingPearl.setCount(1);
        awakeningRift = tag.getBoolean("AwakeningRift"); awakeningTicks = Math.max(0,tag.getInteger("AwakeningTicks"));
        particleFlowTicks = (int) RiftReactor.clamp(tag.getInteger("ParticleFlowTicks"),0,25);
        riftId = tag.hasUniqueId("Rift") ? tag.getUniqueId("Rift") : null;
        if (cachedRift != null && (cachedRift.isDead || cachedRift.world != world
                || !cachedRift.getUniqueID().equals(riftId))) cachedRift = null;
        linkedMonoliths.clear();
        NBTTagList list = tag.getTagList("LinkedMonoliths",10);
        for (int i=0;i<list.tagCount() && linkedMonoliths.size()<MAX_INTERCEPTOR_STONES;i++) {
            BlockPos source = BlockPos.fromLong(list.getCompoundTagAt(i).getLong("Position"));
            if (!linkedMonoliths.contains(source)) linkedMonoliths.add(source);
        }
        reactor.input=linkedMonoliths.size();
        reactor.excitation=linkedMonoliths.size()/(double)RiftReactor.MAX_LINKS;
        if (reactorVersion<6) {
            reactor.tension=contaminated ? 100 : 0;
            reactor.critical=false;
            reactor.criticalSeconds=reactor.tension;
            emergencyCollapse=contaminated && riftId!=null;
        }
    }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Nullable @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos,0,getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) { readFromNBT(packet.getNbtCompound()); }
    private void markDirtyAndSync() {
        markDirty();
        if (world != null) { IBlockState state=world.getBlockState(pos); world.notifyBlockUpdate(pos,state,state,3); world.updateComparatorOutputLevel(pos,state.getBlock()); }
    }
    @Override public void registerControllers(AnimationData data) {
        data.addAnimationController(new AnimationController<>(this,"rift_well_controller",0,this::animationPredicate));
    }
    private <E extends IAnimatable> PlayState animationPredicate(AnimationEvent<E> event) {
        event.getController().setAnimation(new AnimationBuilder().addAnimation("animation",true)); return PlayState.CONTINUE;
    }
    @Override public AnimationFactory getFactory() { return factory; }
    @Override public AxisAlignedBB getRenderBoundingBox() { return new AxisAlignedBB(pos).grow(16); }
    @Override public double getMaxRenderDistanceSquared() { return 65536; }
}
