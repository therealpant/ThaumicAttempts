package therealpant.thaumicattempts.world.tile;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemTool;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemBow;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.block.state.IBlockState;
import net.minecraftforge.common.util.Constants;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;
import therealpant.thaumicattempts.init.ModBlocksItems;
import therealpant.thaumicattempts.init.TABlocks;
import therealpant.thaumicattempts.world.block.BlockRiftonomicon;
import therealpant.thaumicattempts.integration.thaumicaugmentation.ImpetusCompat;
import therealpant.thaumicattempts.items.ItemPortalRune;
import therealpant.thaumicattempts.items.ItemRiftonomiconProtocol;
import therealpant.thaumicattempts.world.block.RiftonomiconStructureHelper;

import javax.annotation.Nullable;
import java.util.List;
import java.util.ArrayList;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.items.casters.ItemFocus;

public class TileRiftonomicon extends TileEntity implements ITickable, IAnimatable {
    private static final String MAIN_ANIMATION = "animation";
    private static final String CORE_ANIMATION = "portal";
    private static final String NAVIGATION_STONE_ANIMATION = "animation";
    private static final int MAIN_ANIMATION_TICKS = 300;
    private static final int CORE_IDLE_END_TICKS = 60;
    private static final int CORE_NAVIGATION_HOLD_START_TICKS = 120;
    private static final int CORE_NAVIGATION_HOLD_END_TICKS = 180;
    private static final int CORE_NAVIGATION_CLOSE_END_TICKS = 240;
    private static final int CORE_ALCHEMY_START_TICKS = 240;
    private static final int CORE_ALCHEMY_HOLD_START_TICKS = 300;
    private static final int CORE_ALCHEMY_HOLD_END_TICKS = 360;
    private static final int CORE_ALCHEMY_CLOSE_END_TICKS = 420;
    private static final int CORE_TRANSMUTATION_START_TICKS = 420;
    private static final int CORE_TRANSMUTATION_HOLD_START_TICKS = 480;
    private static final int CORE_TRANSMUTATION_HOLD_END_TICKS = 540;
    private static final int CORE_TRANSMUTATION_CLOSE_END_TICKS = 600;
    public static final int PROTOCOL_SLOT_COUNT = 4;
    public static final int BASE_NAVIGATION_RUNE_SLOT_COUNT = 4;
    public static final int MAX_NAVIGATION_RUNE_SLOT_COUNT = 24;
    public static final int NAVIGATION_RUNE_SLOT_COUNT = MAX_NAVIGATION_RUNE_SLOT_COUNT;
    public static final int ALCHEMY_PEARL_SLOT_COUNT = 4;
    private static final String TAG_PROTOCOLS = "Protocols";
    private static final String TAG_NAVIGATION_RUNES = "NavigationRunes";
    private static final String TAG_SLOT = "Slot";
    private static final String TAG_CORE_PORTAL_LATCHED = "CorePortalLatched";
    private static final String TAG_CORE_ANIMATION_TICKS = "CoreAnimationTicks";
    private static final String TAG_CORE_OPEN_CYCLE = "CoreOpenCycle";
    private static final String TAG_CORE_PROTOCOL_META = "CoreProtocolMeta";
    private static final String TAG_ALCHEMY_PEARLS = "AlchemyPearls";
    private static final String TAG_IMPETUS_PEARL_WEAR = "ImpetusPearlWear";
    private static final String TAG_FALLBACK_FURNACE_CYCLES = "FallbackFurnaceCycles";
    private static final String TAG_TRANSMUTATION_ITEM = "TransmutationItem";
    private static final String TAG_TRANSMUTATION_ACTIVE = "TransmutationActive";
    private static final String TAG_TRANSMUTATION_ESSENTIA = "TransmutationEssentia";
    private static final String TAG_TRANSMUTATION_IMPETUS_FAILURES = "TransmutationImpetusFailures";
    private static final String TAG_TRANSMUTATION_IMPETUS_PULSE = "TransmutationImpetusPulse";
    private static final int TRANSMUTATION_ESSENTIA_REQUIRED = 150;
    private static final long TRANSMUTATION_IMPETUS_PER_SECOND = 50L;
    private static final long IMPETUS_PER_PEARL_WEAR_EVENT = 800L;
    private static final float PEARL_DAMAGE_CHANCE = 0.05F;
    private static final float FALLBACK_PEARL_DAMAGE_CHANCE = 0.08F;
    private static final int FALLBACK_CYCLES_PER_WEAR_EVENT = 4;
    private static final net.minecraft.util.EnumFacing[] IMPETUS_PORT_FACINGS = {
            net.minecraft.util.EnumFacing.NORTH,
            net.minecraft.util.EnumFacing.EAST,
            net.minecraft.util.EnumFacing.SOUTH,
            net.minecraft.util.EnumFacing.WEST
    };

    private final AnimationFactory factory = new AnimationFactory(this);
    private final AnimationFactory coreAnimationFactory = new AnimationFactory(this);
    private final AnimationFactory navigationStoneAnimationFactory = new AnimationFactory(this);
    private transient boolean coreAnimationPass;
    private transient boolean navigationStoneAnimationPass;
    private int coreAnimationTicks;
    private boolean coreProtocolRequested;
    private boolean coreOpenCycle;
    private int coreAnimationProtocolMeta = -1;
    private transient boolean coreAnimationApplied;
    private transient String appliedCoreAnimation;
    private transient boolean syncCoreAnimationTick;
    private final NonNullList<ItemStack> protocolSlots = NonNullList.withSize(PROTOCOL_SLOT_COUNT, ItemStack.EMPTY);
    private final NonNullList<ItemStack> navigationRunes = NonNullList.withSize(NAVIGATION_RUNE_SLOT_COUNT, ItemStack.EMPTY);
    private final NonNullList<ItemStack> alchemyPearls = NonNullList.withSize(ALCHEMY_PEARL_SLOT_COUNT, ItemStack.EMPTY);
    private long impetusPearlWear;
    private int fallbackFurnaceCycles;
    private ItemStack transmutationItem = ItemStack.EMPTY;
    private boolean transmutationActive;
    private int transmutationEssentia;
    private int transmutationImpetusFailures;
    private int transmutationImpetusPulse;
    private int transmutationPaymentTicks;

    @Override
    public void registerControllers(AnimationData data) {
        data.addAnimationController(new AnimationController<>(this, "controller", 0, this::predicate));
    }

    private <E extends IAnimatable> PlayState predicate(AnimationEvent<E> event) {
        if (navigationStoneAnimationPass) {
            event.getController().setAnimation(new AnimationBuilder().addAnimation(NAVIGATION_STONE_ANIMATION, true));
            return PlayState.CONTINUE;
        }
        if (coreAnimationPass) {
            return coreAnimationPredicate(event);
        }
        AnimationController<?> controller = event.getController();
        controller.setAnimation(new AnimationBuilder().addAnimation(MAIN_ANIMATION, true));
        syncLoopToWorldTime(controller, event.getAnimationTick(), MAIN_ANIMATION_TICKS, 0);
        return PlayState.CONTINUE;
    }

    private <E extends IAnimatable> PlayState coreAnimationPredicate(AnimationEvent<E> event) {
        AnimationController<?> controller = event.getController();
        String animation = getCoreAnimationName();
        if (!coreAnimationApplied || !animation.equals(appliedCoreAnimation)) {
            applyCoreAnimation(controller, animation, true);
            appliedCoreAnimation = animation;
            coreAnimationApplied = true;
            syncCoreAnimationTick = true;
            return PlayState.CONTINUE;
        }

        if (syncCoreAnimationTick) {
            syncControllerToStageTick(controller, event.getAnimationTick());
            syncCoreAnimationTick = false;
        }
        tickCoreAnimationController(controller, event.getAnimationTick());
        return PlayState.CONTINUE;
    }

    private void tickCoreAnimationController(AnimationController<?> controller, double eventTick) {
        double animationTick = getCoreControllerTick(controller, eventTick);

        if (!coreOpenCycle && animationTick >= CORE_IDLE_END_TICKS) {
            if (coreProtocolRequested) {
                coreOpenCycle = true;
            } else {
                subtractCoreAnimationTime(controller, CORE_IDLE_END_TICKS);
                animationTick -= CORE_IDLE_END_TICKS;
            }
        }

        if (coreOpenCycle && coreProtocolRequested
                && animationTick >= getCoreHoldEndTicks()) {
            subtractCoreAnimationTime(controller, CORE_IDLE_END_TICKS);
        } else if (coreOpenCycle && !coreProtocolRequested
                && animationTick >= getCoreCloseEndTicks()) {
            subtractCoreAnimationTime(controller, getCoreCloseEndTicks());
            coreOpenCycle = false;
            coreAnimationProtocolMeta = -1;
        }
    }

    private double getCoreControllerTick(AnimationController<?> controller, double eventTick) {
        double speed = controller.getAnimationSpeed();
        if (speed == 0.0D) speed = 1.0D;
        return speed * Math.max(eventTick - controller.tickOffset, 0.0D);
    }

    private void subtractCoreAnimationTime(AnimationController<?> controller, int ticks) {
        double speed = controller.getAnimationSpeed();
        if (speed == 0.0D) speed = 1.0D;
        controller.tickOffset += ticks / speed;
    }

    private String getCoreAnimationName() {
        return CORE_ANIMATION;
    }

    private void syncControllerToStageTick(AnimationController<?> controller, double eventTick) {
        double speed = controller.getAnimationSpeed();
        if (speed == 0.0D) speed = 1.0D;
        controller.tickOffset = eventTick - coreAnimationTicks / speed;
    }

    private void applyCoreAnimation(AnimationController<?> controller, String animation, boolean loop) {
        controller.markNeedsReload();
        controller.setAnimation(new AnimationBuilder().addAnimation(animation, loop));
    }

    private void syncLoopToWorldTime(AnimationController<?> controller, double eventTick,
                                     int cycleTicks, int animationOffset) {
        if (world == null) return;
        double speed = controller.getAnimationSpeed();
        if (speed == 0.0D) speed = 1.0D;
        double desiredTick = animationOffset + Math.floorMod(world.getTotalWorldTime(), cycleTicks);
        controller.tickOffset = eventTick - desiredTick / speed;
    }

    @Override
    public AnimationFactory getFactory() {
        if (navigationStoneAnimationPass) return navigationStoneAnimationFactory;
        return coreAnimationPass ? coreAnimationFactory : factory;
    }

    public void setCoreAnimationPass(boolean coreAnimationPass) {
        this.coreAnimationPass = coreAnimationPass;
    }

    public void setNavigationStoneAnimationPass(boolean navigationStoneAnimationPass) {
        this.navigationStoneAnimationPass = navigationStoneAnimationPass;
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return new AxisAlignedBB(pos).grow(4.0D, 5.0D, 4.0D);
    }

    @Override
    public void update() {
        if (world == null || world.isRemote) return;
        tickCoreAnimationState();
        if (world.getTotalWorldTime() % 20L == 0L) {
            ensureImpetusProxyPorts();
        }
        tickTransmutation();
        if (world.getTotalWorldTime() % 5L == 0L) {
            absorbNavigationRunesFromBaseVoid();
            absorbTransmutationItemFromBaseVoid();
        }
    }

    private void ensureImpetusProxyPorts() {
        for (net.minecraft.util.EnumFacing facing : net.minecraft.util.EnumFacing.HORIZONTALS) {
            BlockPos portPos = pos.offset(facing, 2);
            if (!world.isAirBlock(portPos)) continue;
            world.setBlockState(portPos, TABlocks.RIFTONOMICON.getDefaultState()
                    .withProperty(BlockRiftonomicon.PART, BlockRiftonomicon.Part.IMPETUS_PORT), 3);
        }
        ensureCentralTransmutationPort(pos.down(1), BlockRiftonomicon.Part.TRANSMUTATION_ESSENTIA_INPUT);
        ensureCentralTransmutationPort(pos.down(3), BlockRiftonomicon.Part.TRANSMUTATION_IMPETUS_INPUT);
    }

    private void ensureCentralTransmutationPort(BlockPos portPos, BlockRiftonomicon.Part part) {
        IBlockState state = world.getBlockState(portPos);
        if (state.getBlock() != TABlocks.RIFTONOMICON || state.getValue(BlockRiftonomicon.PART) == part) return;
        world.setBlockState(portPos, TABlocks.RIFTONOMICON.getDefaultState()
                .withProperty(BlockRiftonomicon.PART, part), 3);
    }

    private void tickTransmutation() {
        if (!transmutationActive) return;
        if (!hasTransmutationProtocol() || transmutationItem.isEmpty() || getActivePrismAltars().isEmpty()) {
            abortTransmutation();
            return;
        }
        if (++transmutationPaymentTicks >= 20) {
            transmutationPaymentTicks = 0;
            if (!payTransmutationImpetus()) {
                if (++transmutationImpetusFailures > 3) {
                    abortTransmutation();
                    return;
                }
            } else {
                ++transmutationImpetusPulse;
                sync();
            }
            markDirty();
        }
        if (transmutationEssentia >= TRANSMUTATION_ESSENTIA_REQUIRED) completeTransmutation();
    }

    private boolean payTransmutationImpetus() {
        TileEntity tile = world.getTileEntity(pos.down(3));
        if (!(tile instanceof TileRiftonomiconTransmutationImpetusInput)) return false;
        TileRiftonomiconTransmutationImpetusInput input = (TileRiftonomiconTransmutationImpetusInput) tile;
        if (input.consume(TRANSMUTATION_IMPETUS_PER_SECOND, true) < TRANSMUTATION_IMPETUS_PER_SECOND) return false;
        return input.consume(TRANSMUTATION_IMPETUS_PER_SECOND, false) >= TRANSMUTATION_IMPETUS_PER_SECOND;
    }

    public boolean tryStartTransmutation() {
        if (world == null || world.isRemote || transmutationActive || !hasTransmutationProtocol()
                || transmutationItem.isEmpty() || getActivePrismAltars().isEmpty()) return false;
        transmutationActive = true;
        transmutationEssentia = 0;
        transmutationImpetusFailures = 0;
        transmutationPaymentTicks = 20;
        sendTransmutationAltarAnimationSignal();
        sync();
        return true;
    }

    public boolean isTransmutationActive() { return transmutationActive; }
    public int getTransmutationImpetusPulse() { return transmutationImpetusPulse; }

    public boolean canAcceptTransmutationParadoxum() {
        return transmutationActive && transmutationEssentia < TRANSMUTATION_ESSENTIA_REQUIRED;
    }

    public int acceptTransmutationParadoxum(int amount) {
        if (!canAcceptTransmutationParadoxum() || amount <= 0) return 0;
        int accepted = Math.min(amount, TRANSMUTATION_ESSENTIA_REQUIRED - transmutationEssentia);
        transmutationEssentia += accepted;
        sync();
        return accepted;
    }

    private List<TileRiftStoneAltar> getActivePrismAltars() {
        List<TileRiftStoneAltar> result = new ArrayList<>();
        for (BlockPos altarPos : getLinkedTransmutationAltars()) {
            if (!isTransmutationAltarConnectionActive(altarPos)) continue;
            TileEntity tile = world.getTileEntity(altarPos);
            if (tile instanceof TileRiftStoneAltar && !((TileRiftStoneAltar) tile).getPrism().isEmpty()) {
                result.add((TileRiftStoneAltar) tile);
            }
        }
        return result;
    }

    private void completeTransmutation() {
        for (TileRiftStoneAltar altar : getActivePrismAltars()) altar.consumePrism();
        stopTransmutation();
    }

    private void abortTransmutation() { stopTransmutation(); }

    private void stopTransmutation() {
        transmutationActive = false;
        transmutationPaymentTicks = 0;
        sendTransmutationAltarAnimationSignal();
        sync();
    }

    private void tickCoreAnimationState() {
        coreAnimationTicks++;
        if (!coreOpenCycle && coreAnimationTicks >= CORE_IDLE_END_TICKS) {
            if (coreProtocolRequested) {
                coreOpenCycle = true;
                int start = getCoreStartTicks();
                if (start > 0) coreAnimationTicks = start;
            } else {
                coreAnimationTicks = 0;
            }
        }

        if (coreOpenCycle && coreProtocolRequested
                && coreAnimationTicks >= getCoreHoldEndTicks()) {
            coreAnimationTicks = getCoreHoldStartTicks();
        } else if (coreOpenCycle && !coreProtocolRequested
                && coreAnimationTicks >= getCoreCloseEndTicks()) {
            coreAnimationTicks = 0;
            coreOpenCycle = false;
            coreAnimationProtocolMeta = -1;
        }

        // Persist the exact cursor without sending a block update at loop boundaries;
        // the client performs the same relative tickOffset adjustment locally.
        markDirty();
    }

    private int getCoreStartTicks() {
        if (coreAnimationProtocolMeta == ItemRiftonomiconProtocol.META_ALCHEMICAL_PROTOCOL) return CORE_ALCHEMY_START_TICKS;
        if (coreAnimationProtocolMeta == ItemRiftonomiconProtocol.META_TRANSMUTATION_PROTOCOL) return CORE_TRANSMUTATION_START_TICKS;
        return 0;
    }

    private int getCoreHoldStartTicks() {
        if (coreAnimationProtocolMeta == ItemRiftonomiconProtocol.META_ALCHEMICAL_PROTOCOL) return CORE_ALCHEMY_HOLD_START_TICKS;
        if (coreAnimationProtocolMeta == ItemRiftonomiconProtocol.META_TRANSMUTATION_PROTOCOL) return CORE_TRANSMUTATION_HOLD_START_TICKS;
        return CORE_NAVIGATION_HOLD_START_TICKS;
    }

    private int getCoreHoldEndTicks() { return getCoreHoldStartTicks() + CORE_IDLE_END_TICKS; }

    private int getCoreCloseEndTicks() {
        if (coreAnimationProtocolMeta == ItemRiftonomiconProtocol.META_ALCHEMICAL_PROTOCOL) return CORE_ALCHEMY_CLOSE_END_TICKS;
        if (coreAnimationProtocolMeta == ItemRiftonomiconProtocol.META_TRANSMUTATION_PROTOCOL) return CORE_TRANSMUTATION_CLOSE_END_TICKS;
        return CORE_NAVIGATION_CLOSE_END_TICKS;
    }

    public ItemStack getProtocolInSlot(int slot) {
        return isValidProtocolSlot(slot) ? protocolSlots.get(slot) : ItemStack.EMPTY;
    }

    public boolean isProtocolSlotEmpty(int slot) {
        return getProtocolInSlot(slot).isEmpty();
    }

    public boolean setProtocolInSlot(int slot, ItemStack stack) {
        if (!isValidProtocolSlot(slot)) return false;
        boolean hadProtocol = coreProtocolRequested;
        ItemStack stored = stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        if (!stored.isEmpty()) stored.setCount(1);
        protocolSlots.set(slot, stored);
        int activeProtocol = getProtocolMeta();
        coreProtocolRequested = activeProtocol >= 0;
        if (activeProtocol >= 0) {
            coreAnimationProtocolMeta = activeProtocol;
            if (!hadProtocol && (activeProtocol == ItemRiftonomiconProtocol.META_ALCHEMICAL_PROTOCOL
                    || activeProtocol == ItemRiftonomiconProtocol.META_TRANSMUTATION_PROTOCOL)) {
                coreAnimationTicks = activeProtocol == ItemRiftonomiconProtocol.META_ALCHEMICAL_PROTOCOL
                        ? CORE_ALCHEMY_START_TICKS : CORE_TRANSMUTATION_START_TICKS;
                coreOpenCycle = true;
            }
        }
        sendTransmutationAltarAnimationSignal();
        sync();
        return true;
    }

    public ItemStack removeProtocolFromSlot(int slot) {
        if (!isValidProtocolSlot(slot)) return ItemStack.EMPTY;
        ItemStack stored = protocolSlots.get(slot);
        if (stored.isEmpty()) return ItemStack.EMPTY;
        protocolSlots.set(slot, ItemStack.EMPTY);
        int activeProtocol = getProtocolMeta();
        coreProtocolRequested = activeProtocol >= 0;
        if (activeProtocol >= 0) coreAnimationProtocolMeta = activeProtocol;
        sendTransmutationAltarAnimationSignal();
        sync();
        return stored;
    }

    public boolean canInsertProtocolItem(ItemStack stack) {
        if (!isRiftonomiconProtocol(stack)) return false;
        int meta = stack.getMetadata();
        if (isProtocolMeta(meta)) {
            return getProtocolMeta() < 0 && allSubsystemsMatchProtocol(meta);
        }
        int expectedProtocol = meta - 1;
        return getProtocolMeta() == expectedProtocol;
    }

    public boolean hasNavigationProtocol() {
        return getProtocolMeta() == ItemRiftonomiconProtocol.META_NAVIGATION_PROTOCOL;
    }

    public boolean hasAlchemicalProtocol() {
        return getProtocolMeta() == ItemRiftonomiconProtocol.META_ALCHEMICAL_PROTOCOL;
    }

    public boolean hasTransmutationProtocol() {
        return getProtocolMeta() == ItemRiftonomiconProtocol.META_TRANSMUTATION_PROTOCOL;
    }

    public boolean isTransmutationAltarAnimationRequested() {
        return transmutationActive;
    }

    private void sendTransmutationAltarAnimationSignal() {
        if (world == null || world.isRemote) return;
        for (TileEntity tile : world.loadedTileEntityList) {
            if (tile instanceof TileRiftStoneAltar) {
                ((TileRiftStoneAltar) tile).receiveRiftonomiconAnimationSignal(this);
            }
        }
    }

    public int getTransmutationAltarLimit() {
        return hasTransmutationProtocol()
                ? Math.min(4, 1 + getSubsystemCount(ItemRiftonomiconProtocol.META_PRISMATIC_SUBSYSTEM)) : 0;
    }

    public boolean hasFreeTransmutationAltarConnection(BlockPos altarPos) {
        if (world == null || world.isRemote || altarPos == null || !hasTransmutationProtocol()) return false;
        if (isTransmutationAltarConnectionActive(altarPos)) return true;
        return getLinkedTransmutationAltars().size() < getTransmutationAltarLimit();
    }

    public boolean isTransmutationAltarConnectionActive(BlockPos altarPos) {
        if (altarPos == null || !hasTransmutationProtocol()) return false;
        List<BlockPos> linked = getLinkedTransmutationAltars();
        int limit = Math.min(getTransmutationAltarLimit(), linked.size());
        return linked.subList(0, limit).contains(altarPos);
    }

    private List<BlockPos> getLinkedTransmutationAltars() {
        List<BlockPos> linked = new ArrayList<>();
        if (world == null) return linked;
        for (TileEntity tile : world.loadedTileEntityList) {
            if (!(tile instanceof TileRiftStoneAltar)) continue;
            TileRiftStoneAltar altar = (TileRiftStoneAltar) tile;
            if (pos.equals(altar.getLinkedRiftonomiconPos())
                    && altar.getPos().distanceSq(pos) <= TileRiftStoneAltar.LINK_RADIUS_SQ) linked.add(altar.getPos());
        }
        linked.sort((a, b) -> Long.compare(a.toLong(), b.toLong()));
        return linked;
    }

    public ItemStack getAlchemyPearl(int slot) {
        return slot >= 0 && slot < ALCHEMY_PEARL_SLOT_COUNT ? alchemyPearls.get(slot) : ItemStack.EMPTY;
    }

    public int getAlchemyEfficiency() {
        int pearls = 0;
        for (ItemStack pearl : alchemyPearls) {
            if (!pearl.isEmpty()) pearls++;
        }
        int reservedFuelPearls = ImpetusCompat.isLoaded() ? 0 : 1;
        return Math.min(ALCHEMY_PEARL_SLOT_COUNT, Math.max(0, pearls - reservedFuelPearls));
    }

    public boolean hasAlchemyPearls() {
        for (ItemStack pearl : alchemyPearls) {
            if (!pearl.isEmpty()) return true;
        }
        return false;
    }

    public boolean tryInsertAlchemyPearl(int slot, EntityPlayer player) {
        if (!hasAlchemicalProtocol() || player == null || slot < 0 || slot >= ALCHEMY_PEARL_SLOT_COUNT
                || !alchemyPearls.get(slot).isEmpty()) return false;
        ItemStack held = player.getHeldItemMainhand();
        if (held.isEmpty() || held.getItem() != ItemsTC.primordialPearl) return false;
        ItemStack pearl = held.copy();
        pearl.setCount(1);
        alchemyPearls.set(slot, pearl);
        if (!player.capabilities.isCreativeMode) held.shrink(1);
        sync();
        return true;
    }

    public ItemStack removeAlchemyPearl(int slot) {
        if (!hasAlchemicalProtocol() || slot < 0 || slot >= ALCHEMY_PEARL_SLOT_COUNT) return ItemStack.EMPTY;
        ItemStack pearl = alchemyPearls.get(slot);
        if (pearl.isEmpty()) return ItemStack.EMPTY;
        alchemyPearls.set(slot, ItemStack.EMPTY);
        sync();
        return pearl;
    }

    public boolean hasNavigationSubsystem() {
        return getSubsystemCount(ItemRiftonomiconProtocol.META_SPATIAL_SUBSYSTEM) > 0;
    }

    public int getNavigationSubsystemCount() {
        return hasNavigationProtocol() ? getSubsystemCount(ItemRiftonomiconProtocol.META_SPATIAL_SUBSYSTEM) : 0;
    }

    public int getNavigationRuneSlotLimit() {
        if (!hasNavigationProtocol()) return 0;
        int subsystemCount = Math.min(3, getNavigationSubsystemCount());
        return Math.min(MAX_NAVIGATION_RUNE_SLOT_COUNT, BASE_NAVIGATION_RUNE_SLOT_COUNT << subsystemCount);
    }

    public int getProtocolMeta() {
        for (ItemStack stack : protocolSlots) {
            if (isRiftonomiconProtocol(stack) && isProtocolMeta(stack.getMetadata())) {
                return stack.getMetadata();
            }
        }
        return -1;
    }

    public int getSubsystemMeta() {
        for (ItemStack stack : protocolSlots) {
            if (isRiftonomiconProtocol(stack) && !isProtocolMeta(stack.getMetadata())) {
                return stack.getMetadata();
            }
        }
        return -1;
    }

    public int getFurnaceConnectionLimit() {
        int installed = 0;
        for (ItemStack stack : protocolSlots) {
            if (isRiftonomiconProtocol(stack)) installed++;
        }
        return Math.max(1, Math.min(PROTOCOL_SLOT_COUNT, installed));
    }

    /** Each connected furnace consumes exclusively through its assigned proxy port. */
    public long consumeImpetusForFurnace(BlockPos furnacePos, long requested, boolean simulate) {
        if (world == null || furnacePos == null || requested <= 0L) return 0L;
        List<BlockPos> linked = getLinkedFurnaces();
        int port = linked.indexOf(furnacePos);
        if (port < 0 || port >= getFurnaceConnectionLimit() || port >= IMPETUS_PORT_FACINGS.length) return 0L;
        TileEntity tile = world.getTileEntity(pos.offset(IMPETUS_PORT_FACINGS[port], 2));
        if (!(tile instanceof TileRiftonomiconImpetusPort)) return 0L;
        long consumed = ((TileRiftonomiconImpetusPort) tile).consume(requested, simulate);
        if (!simulate && consumed > 0L) recordImpetusConsumption(consumed);
        return consumed;
    }

    public int getImpetusPortForFurnace(BlockPos furnacePos) {
        if (furnacePos == null) return -1;
        int port = getLinkedFurnaces().indexOf(furnacePos);
        return port >= 0 && port < getFurnaceConnectionLimit() ? port : -1;
    }

    private void recordImpetusConsumption(long consumed) {
        impetusPearlWear += consumed;
        boolean changed = false;
        while (impetusPearlWear >= IMPETUS_PER_PEARL_WEAR_EVENT) {
            impetusPearlWear -= IMPETUS_PER_PEARL_WEAR_EVENT;
            changed |= runPearlWearEvent(PEARL_DAMAGE_CHANCE);
        }
        if (changed) sync();
        else markDirty();
    }

    private boolean runPearlWearEvent(float damageChance) {
        if (world == null) return false;
        int leastDamage = Integer.MAX_VALUE;
        List<Integer> candidates = new ArrayList<>();
        for (int slot = 0; slot < ALCHEMY_PEARL_SLOT_COUNT; slot++) {
            ItemStack pearl = alchemyPearls.get(slot);
            if (pearl.isEmpty()) continue;
            int damage = pearl.getItemDamage();
            if (damage < leastDamage) {
                leastDamage = damage;
                candidates.clear();
                candidates.add(slot);
            } else if (damage == leastDamage) {
                candidates.add(slot);
            }
        }
        if (candidates.isEmpty()) return false;

        int slot = candidates.get(world.rand.nextInt(candidates.size()));
        if (world.rand.nextFloat() >= damageChance) return false;
        ItemStack pearl = alchemyPearls.get(slot);
        if (pearl.attemptDamageItem(1, world.rand, null)) {
            alchemyPearls.set(slot, ItemStack.EMPTY);
        }
        return true;
    }

    public void onFurnaceSmeltingCycleCompleted(BlockPos furnacePos) {
        if (world == null || world.isRemote || ImpetusCompat.isLoaded()
                || !isFurnaceConnectionActive(furnacePos)) return;
        fallbackFurnaceCycles++;
        boolean damaged = false;
        while (fallbackFurnaceCycles >= FALLBACK_CYCLES_PER_WEAR_EVENT) {
            fallbackFurnaceCycles -= FALLBACK_CYCLES_PER_WEAR_EVENT;
            damaged |= runPearlWearEvent(FALLBACK_PEARL_DAMAGE_CHANCE);
        }
        if (damaged) sync();
        else markDirty();
    }

    private boolean canPowerFurnaces() {
        return hasAlchemicalProtocol() && (ImpetusCompat.isLoaded() || hasAlchemyPearls());
    }

    public boolean hasFreeFurnaceConnection(BlockPos furnacePos) {
        if (world == null || world.isRemote || furnacePos == null || !canPowerFurnaces()) return false;
        if (isFurnaceConnectionActive(furnacePos)) return true;
        return getLinkedFurnaces().size() < getFurnaceConnectionLimit();
    }

    public boolean isFurnaceConnectionActive(BlockPos furnacePos) {
        if (furnacePos == null || !canPowerFurnaces()) return false;
        List<BlockPos> linked = getLinkedFurnaces();
        int limit = Math.min(getFurnaceConnectionLimit(), linked.size());
        for (int i = 0; i < limit; i++) {
            if (linked.get(i).equals(furnacePos)) return true;
        }
        return false;
    }

    private List<BlockPos> getLinkedFurnaces() {
        List<BlockPos> linked = new ArrayList<>();
        if (world == null) return linked;
        for (TileEntity tile : world.loadedTileEntityList) {
            if (!(tile instanceof TileRiftStoneFurnace)) continue;
            TileRiftStoneFurnace furnace = (TileRiftStoneFurnace) tile;
            if (!pos.equals(furnace.getLinkedRiftonomiconPos())) continue;
            if (furnace.getPos().distanceSq(pos) > TileRiftStoneFurnace.LINK_RADIUS_SQ) continue;
            linked.add(furnace.getPos());
        }
        linked.sort((a, b) -> Long.compare(a.toLong(), b.toLong()));
        return linked;
    }

    public NonNullList<ItemStack> removeAllProtocols() {
        NonNullList<ItemStack> removed = NonNullList.withSize(PROTOCOL_SLOT_COUNT, ItemStack.EMPTY);
        boolean changed = false;
        for (int i = 0; i < PROTOCOL_SLOT_COUNT; i++) {
            ItemStack stack = protocolSlots.get(i);
            if (!stack.isEmpty()) {
                removed.set(i, stack);
                protocolSlots.set(i, ItemStack.EMPTY);
                changed = true;
            }
        }
        if (changed) {
            coreProtocolRequested = false;
            sync();
        }
        return removed;
    }

    public ItemStack getNavigationRuneInSlot(int slot) {
        return isValidNavigationRuneSlot(slot) ? navigationRunes.get(slot) : ItemStack.EMPTY;
    }

    public boolean canInsertNavigationRune(ItemStack stack) {
        return hasNavigationProtocol() && isPortalRuneWithTarget(stack) && getFirstEmptyNavigationRuneSlot() >= 0;
    }

    public boolean hasNavigationRunes() {
        for (ItemStack stack : navigationRunes) {
            if (!stack.isEmpty()) return true;
        }
        return false;
    }

    public boolean insertNavigationRune(ItemStack stack) {
        int slot = getRandomEmptyNavigationRuneSlot();
        if (slot < 0 || !canInsertNavigationRune(stack)) return false;
        ItemStack stored = stack.copy();
        stored.setCount(1);
        navigationRunes.set(slot, stored);
        sync();
        return true;
    }

    private void absorbNavigationRunesFromBaseVoid() {
        if (!hasNavigationProtocol() || getFirstEmptyNavigationRuneSlot() < 0) return;

        BlockPos base = pos.down(RiftonomiconStructureHelper.CORE_Y_OFFSET);
        AxisAlignedBB area = new AxisAlignedBB(
                base.getX() - 1.0D, base.getY() + 0.05D, base.getZ() - 1.0D,
                base.getX() + 2.0D, base.getY() + 2.0D, base.getZ() + 2.0D
        );
        List<EntityItem> items = world.getEntitiesWithinAABB(EntityItem.class, area);
        for (EntityItem entity : items) {
            if (entity == null || entity.isDead) continue;
            ItemStack stack = entity.getItem();
            if (!canInsertNavigationRune(stack)) continue;

            ItemStack stored = stack.copy();
            stored.setCount(1);
            if (!insertNavigationRune(stored)) continue;

            stack.shrink(1);
            if (stack.isEmpty()) {
                entity.setDead();
            } else {
                entity.setItem(stack);
            }
            break;
        }
    }

    private void absorbTransmutationItemFromBaseVoid() {
        if (!hasTransmutationProtocol() || !transmutationItem.isEmpty()) return;
        BlockPos base = pos.down(RiftonomiconStructureHelper.CORE_Y_OFFSET);
        AxisAlignedBB area = new AxisAlignedBB(
                base.getX() - 1.0D, base.getY() + 0.05D, base.getZ() - 1.0D,
                base.getX() + 2.0D, base.getY() + 2.0D, base.getZ() + 2.0D);
        for (EntityItem entity : world.getEntitiesWithinAABB(EntityItem.class, area)) {
            if (entity == null || entity.isDead || !isValidTransmutationItem(entity.getItem())) continue;
            transmutationItem = entity.getItem().copy();
            transmutationItem.setCount(1);
            ItemStack remaining = entity.getItem();
            remaining.shrink(1);
            if (remaining.isEmpty()) entity.setDead(); else entity.setItem(remaining);
            sync();
            return;
        }
    }

    private static boolean isValidTransmutationItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem() instanceof ItemArmor
                || stack.getItem() instanceof ItemTool
                || stack.getItem() instanceof ItemSword
                || stack.getItem() instanceof ItemBow
                || stack.getItem() instanceof ItemFocus;
    }

    public ItemStack getTransmutationItem() { return transmutationItem; }

    public ItemStack removeTransmutationItem() {
        if (transmutationItem.isEmpty()) return ItemStack.EMPTY;
        ItemStack result = transmutationItem;
        transmutationItem = ItemStack.EMPTY;
        sync();
        return result;
    }

    public ItemStack removeLastNavigationRune() {
        for (int i = getNavigationRuneSlotLimit() - 1; i >= 0; i--) {
            ItemStack stack = navigationRunes.get(i);
            if (!stack.isEmpty()) {
                navigationRunes.set(i, ItemStack.EMPTY);
                sync();
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public ItemStack removeNavigationRune(int slot) {
        if (!isActiveNavigationRuneSlot(slot)) return ItemStack.EMPTY;
        ItemStack stack = navigationRunes.get(slot);
        if (stack.isEmpty()) return ItemStack.EMPTY;
        navigationRunes.set(slot, ItemStack.EMPTY);
        sync();
        return stack;
    }

    public NonNullList<ItemStack> removeAllNavigationRunes() {
        NonNullList<ItemStack> removed = NonNullList.withSize(NAVIGATION_RUNE_SLOT_COUNT, ItemStack.EMPTY);
        boolean changed = false;
        for (int i = 0; i < NAVIGATION_RUNE_SLOT_COUNT; i++) {
            ItemStack stack = navigationRunes.get(i);
            if (!stack.isEmpty()) {
                removed.set(i, stack);
                navigationRunes.set(i, ItemStack.EMPTY);
                changed = true;
            }
        }
        if (changed) sync();
        return removed;
    }

    public NonNullList<ItemStack> removeNavigationRunesBeyondLimit() {
        NonNullList<ItemStack> removed = NonNullList.withSize(NAVIGATION_RUNE_SLOT_COUNT, ItemStack.EMPTY);
        int limit = getNavigationRuneSlotLimit();
        boolean changed = false;
        for (int i = Math.max(0, limit); i < NAVIGATION_RUNE_SLOT_COUNT; i++) {
            ItemStack stack = navigationRunes.get(i);
            if (!stack.isEmpty()) {
                removed.set(i, stack);
                navigationRunes.set(i, ItemStack.EMPTY);
                changed = true;
            }
        }
        if (changed) sync();
        return removed;
    }

    public boolean openNearestPortalForNavigationRune(int slot, EntityPlayer player) {
        if (world == null || world.isRemote || player == null || !hasNavigationProtocol()) return false;
        if (!isActiveNavigationRuneSlot(slot)) return false;

        ItemPortalRune.PortalPoint target = getNavigationRuneTarget(slot);
        if (target == null) return false;

        TileRiftStonePortal portal = findNearestClosedPortal();
        if (portal == null) return false;
        portal.openPortalFromRiftonomicon(target.dimension, target.pos);
        return true;
    }

    @Nullable
    public ItemPortalRune.PortalPoint getNavigationRuneTarget(int slot) {
        ItemStack rune = getNavigationRuneInSlot(slot);
        if (!isPortalRuneWithTarget(rune)) return null;
        ItemPortalRune.PortalPoint primary = ItemPortalRune.getPrimary(rune);
        return primary != null ? primary : ItemPortalRune.getSecondary(rune);
    }

    @Nullable
    private TileRiftStonePortal findNearestClosedPortal() {
        TileRiftStonePortal nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (TileEntity tile : world.loadedTileEntityList) {
            if (!(tile instanceof TileRiftStonePortal)) continue;
            TileRiftStonePortal portal = (TileRiftStonePortal) tile;
            if (portal.isActive()) continue;

            BlockPos portalPos = portal.getPos();
            double distance = portalPos.distanceSq(pos);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = portal;
            }
        }
        return nearest;
    }

    private boolean isValidProtocolSlot(int slot) {
        return slot >= 0 && slot < PROTOCOL_SLOT_COUNT;
    }

    private boolean isValidNavigationRuneSlot(int slot) {
        return slot >= 0 && slot < NAVIGATION_RUNE_SLOT_COUNT;
    }

    private boolean isActiveNavigationRuneSlot(int slot) {
        return slot >= 0 && slot < getNavigationRuneSlotLimit();
    }

    private int getFirstEmptyNavigationRuneSlot() {
        int limit = getNavigationRuneSlotLimit();
        for (int i = 0; i < limit; i++) {
            if (navigationRunes.get(i).isEmpty()) return i;
        }
        return -1;
    }

    private int getRandomEmptyNavigationRuneSlot() {
        int limit = getNavigationRuneSlotLimit();
        int emptyCount = 0;
        for (int i = 0; i < limit; i++) {
            if (navigationRunes.get(i).isEmpty()) emptyCount++;
        }
        if (emptyCount == 0) return -1;

        int selected = world == null ? 0 : world.rand.nextInt(emptyCount);
        for (int i = 0; i < limit; i++) {
            if (!navigationRunes.get(i).isEmpty()) continue;
            if (selected-- == 0) return i;
        }
        return -1;
    }

    private int getSubsystemCount(int subsystemMeta) {
        int count = 0;
        for (ItemStack stack : protocolSlots) {
            if (isRiftonomiconProtocol(stack) && stack.getMetadata() == subsystemMeta) {
                count++;
            }
        }
        return count;
    }

    private boolean allSubsystemsMatchProtocol(int protocolMeta) {
        int expectedSubsystem = protocolMeta + 1;
        for (ItemStack stack : protocolSlots) {
            if (isRiftonomiconProtocol(stack)
                    && !isProtocolMeta(stack.getMetadata())
                    && stack.getMetadata() != expectedSubsystem) {
                return false;
            }
        }
        return true;
    }

    private static boolean isRiftonomiconProtocol(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() == ModBlocksItems.RIFTONOMICON_PROTOCOL;
    }

    private static boolean isPortalRuneWithTarget(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && stack.getItem() == ModBlocksItems.PORTAL_RUNE
                && (ItemPortalRune.hasPrimary(stack) || ItemPortalRune.hasSecondary(stack));
    }

    private static boolean isProtocolMeta(int meta) {
        return (meta & 1) == 0;
    }

    private void sync() {
        markDirty();
        if (world != null && !world.isRemote) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        writeStackList(compound, TAG_PROTOCOLS, protocolSlots);
        writeStackList(compound, TAG_NAVIGATION_RUNES, navigationRunes);
        writeStackList(compound, TAG_ALCHEMY_PEARLS, alchemyPearls);
        compound.setLong(TAG_IMPETUS_PEARL_WEAR, impetusPearlWear);
        compound.setInteger(TAG_FALLBACK_FURNACE_CYCLES, fallbackFurnaceCycles);
        compound.setBoolean(TAG_TRANSMUTATION_ACTIVE, transmutationActive);
        compound.setInteger(TAG_TRANSMUTATION_ESSENTIA, transmutationEssentia);
        compound.setInteger(TAG_TRANSMUTATION_IMPETUS_FAILURES, transmutationImpetusFailures);
        compound.setInteger(TAG_TRANSMUTATION_IMPETUS_PULSE, transmutationImpetusPulse);
        if (!transmutationItem.isEmpty()) {
            compound.setTag(TAG_TRANSMUTATION_ITEM, transmutationItem.writeToNBT(new NBTTagCompound()));
        }
        compound.setBoolean(TAG_CORE_PORTAL_LATCHED, coreProtocolRequested);
        compound.setInteger(TAG_CORE_ANIMATION_TICKS, coreAnimationTicks);
        compound.setBoolean(TAG_CORE_OPEN_CYCLE, coreOpenCycle);
        compound.setInteger(TAG_CORE_PROTOCOL_META, coreAnimationProtocolMeta);
        return compound;
    }

    private static void writeStackList(NBTTagCompound compound, String key, NonNullList<ItemStack> stacks) {
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (!stack.isEmpty()) {
                NBTTagCompound stackTag = new NBTTagCompound();
                stackTag.setByte(TAG_SLOT, (byte) i);
                stack.writeToNBT(stackTag);
                list.appendTag(stackTag);
            }
        }
        compound.setTag(key, list);
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        int previousAnimationProtocol = coreAnimationProtocolMeta;
        readStackList(compound, TAG_PROTOCOLS, protocolSlots);
        readStackList(compound, TAG_NAVIGATION_RUNES, navigationRunes);
        readStackList(compound, TAG_ALCHEMY_PEARLS, alchemyPearls);
        impetusPearlWear = Math.max(0L, compound.getLong(TAG_IMPETUS_PEARL_WEAR));
        fallbackFurnaceCycles = Math.max(0, compound.getInteger(TAG_FALLBACK_FURNACE_CYCLES));
        transmutationActive = compound.getBoolean(TAG_TRANSMUTATION_ACTIVE);
        transmutationEssentia = Math.max(0, Math.min(TRANSMUTATION_ESSENTIA_REQUIRED,
                compound.getInteger(TAG_TRANSMUTATION_ESSENTIA)));
        transmutationImpetusFailures = Math.max(0, compound.getInteger(TAG_TRANSMUTATION_IMPETUS_FAILURES));
        transmutationImpetusPulse = Math.max(0, compound.getInteger(TAG_TRANSMUTATION_IMPETUS_PULSE));
        transmutationItem = compound.hasKey(TAG_TRANSMUTATION_ITEM)
                ? new ItemStack(compound.getCompoundTag(TAG_TRANSMUTATION_ITEM)) : ItemStack.EMPTY;
        coreProtocolRequested = compound.hasKey(TAG_CORE_PORTAL_LATCHED)
                ? compound.getBoolean(TAG_CORE_PORTAL_LATCHED)
                : getProtocolMeta() >= 0;
        boolean hasNewCycleState = compound.hasKey(TAG_CORE_OPEN_CYCLE);
        coreOpenCycle = hasNewCycleState
                ? compound.getBoolean(TAG_CORE_OPEN_CYCLE)
                : coreProtocolRequested;
        coreAnimationProtocolMeta = compound.hasKey(TAG_CORE_PROTOCOL_META)
                ? compound.getInteger(TAG_CORE_PROTOCOL_META)
                : (coreProtocolRequested ? getProtocolMeta() : -1);
        int savedTicks = compound.hasKey(TAG_CORE_ANIMATION_TICKS)
                ? Math.floorMod(compound.getInteger(TAG_CORE_ANIMATION_TICKS), CORE_ALCHEMY_CLOSE_END_TICKS)
                : 0;
        if (!hasNewCycleState && coreOpenCycle && savedTicks < CORE_NAVIGATION_HOLD_START_TICKS) {
            savedTicks += CORE_NAVIGATION_HOLD_START_TICKS;
        }
        if (coreOpenCycle
                && coreAnimationProtocolMeta == ItemRiftonomiconProtocol.META_ALCHEMICAL_PROTOCOL
                && savedTicks < CORE_ALCHEMY_START_TICKS) {
            savedTicks = coreProtocolRequested
                    ? CORE_ALCHEMY_HOLD_START_TICKS + Math.floorMod(savedTicks, 60)
                    : CORE_ALCHEMY_HOLD_END_TICKS;
        }
        coreAnimationTicks = savedTicks;
        // Do not hard-sync on routine inventory/tile packets: doing so rewinds a
        // client that has already advanced during network transit. Only alchemy
        // insertion intentionally jumps to the absolute 12-second mark.
        if (previousAnimationProtocol != coreAnimationProtocolMeta
                && coreAnimationProtocolMeta == ItemRiftonomiconProtocol.META_ALCHEMICAL_PROTOCOL) {
            syncCoreAnimationTick = true;
        }
    }

    private static void readStackList(NBTTagCompound compound, String key, NonNullList<ItemStack> stacks) {
        for (int i = 0; i < stacks.size(); i++) {
            stacks.set(i, ItemStack.EMPTY);
        }
        NBTTagList list = compound.getTagList(key, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound stackTag = list.getCompoundTagAt(i);
            int slot = stackTag.getByte(TAG_SLOT) & 255;
            if (slot >= 0 && slot < stacks.size()) {
                ItemStack stack = new ItemStack(stackTag);
                if (!stack.isEmpty()) {
                    stack.setCount(1);
                    stacks.set(slot, stack);
                }
            }
        }
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        readFromNBT(tag);
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
}
