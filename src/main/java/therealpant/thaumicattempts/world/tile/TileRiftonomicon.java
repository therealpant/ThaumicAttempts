package therealpant.thaumicattempts.world.tile;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemTool;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemBow;
import net.minecraft.item.Item;
import net.minecraft.item.crafting.IRecipe;
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
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;
import therealpant.thaumicattempts.data.TAMultiblockTriggers;
import therealpant.thaumicattempts.golemnet.block.BlockEldritchConstarction;
import therealpant.thaumicattempts.init.ModBlocksItems;
import therealpant.thaumicattempts.init.TABlocks;
import therealpant.thaumicattempts.world.block.BlockRiftonomicon;
import therealpant.thaumicattempts.integration.thaumicaugmentation.ImpetusCompat;
import therealpant.thaumicattempts.items.ItemPortalRune;
import therealpant.thaumicattempts.items.ItemRiftonomiconProtocol;
import therealpant.thaumicattempts.items.ItemPrism;
import therealpant.thaumicattempts.items.ItemCalibratedExtract;
import therealpant.thaumicattempts.util.WeaponTransmutation;
import therealpant.thaumicattempts.util.ArmorTransmutation;
import therealpant.thaumicattempts.util.FocusTransmutation;
import therealpant.thaumicattempts.world.block.BlockRiftonomicon;

import javax.annotation.Nullable;
import java.util.List;
import java.util.ArrayList;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.items.casters.ItemFocus;
import thaumcraft.common.lib.crafting.ThaumcraftCraftingManager;

public class TileRiftonomicon extends TileEntity implements ITickable, IAnimatable {
    private static final String MAIN_ANIMATION = "animation";
    private static final String CORE_ANIMATION = "animation";
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
    private static final int CORE_CREATION_START_TICKS = 600;
    private static final int CORE_CREATION_HOLD_START_TICKS = 660;
    private static final int CORE_CREATION_HOLD_END_TICKS = 720;
    private static final int CORE_CREATION_CLOSE_END_TICKS = 780;
    public static final int PROTOCOL_SLOT_COUNT = 4;
    public static final int BASE_NAVIGATION_RUNE_SLOT_COUNT = 4;
    public static final int MAX_NAVIGATION_RUNE_SLOT_COUNT = 24;
    public static final int NAVIGATION_RUNE_SLOT_COUNT = MAX_NAVIGATION_RUNE_SLOT_COUNT;
    public static final int ALCHEMY_PEARL_SLOT_COUNT = 4;
    public static final int TRANSMUTATION_PRISM_SLOT_COUNT = 4;
    public static final int CREATION_SAMPLE_SLOT_COUNT = 4;
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
    private static final String TAG_TRANSMUTATION_PRISMS = "TransmutationPrisms";
    private static final String TAG_TRANSMUTATION_RETURN_TICKS = "TransmutationReturnTicks";
    private static final String TAG_TRANSMUTATION_ANIMATION_TICKS = "TransmutationAnimationTicks";
    private static final String TAG_CREATION_SAMPLES = "CreationSamples";
    private static final String TAG_CREATION_CORE = "CreationCore";
    private static final String TAG_CREATION_TARGET = "CreationTarget";
    private static final String TAG_CREATION_ACTIVE_MASK = "CreationActiveMask";
    private static final String TAG_CREATION_SIGNATURES = "CreationSignatures";
    private static final String TAG_CREATION_REQUIRED = "CreationRequired";
    private static final String TAG_CREATION_PARADOXUM = "CreationParadoxum";
    private static final String TAG_CREATION_FAILURES = "CreationFailures";
    private static final String TAG_CREATION_INSTABILITY = "CreationInstability";
    private static final String TAG_CREATION_CHECKPOINT = "CreationCheckpoint";
    private static final String TAG_CREATION_COMPLETION_TICKS = "CreationCompletionTicks";
    private static final String TAG_CREATION_EXTRACTOR_FUEL = "CreationExtractorFuel";
    private static final String TAG_CREATION_EXTRACTOR_PARADOXUM = "CreationExtractorParadoxum";
    private static final String TAG_CARTRIDGE_RECOVERY_POS = "CartridgeRecoveryPos";
    private static final String TAG_CARTRIDGE_RECOVERY_PROGRESS = "CartridgeRecoveryProgress";
    private static final String TAG_CARTRIDGE_RECOVERY_PARADOXUM = "CartridgeRecoveryParadoxum";
    private static final String TAG_CARTRIDGE_RECOVERY_TICKS = "CartridgeRecoveryTicks";
    private static final int TRANSMUTATION_ESSENTIA_BASE = 150;
    private static final long TRANSMUTATION_IMPETUS_PER_SECOND = 50L;
    private static final long IMPETUS_PER_PEARL_WEAR_EVENT = 800L;
    private static final float PEARL_DAMAGE_CHANCE = 0.05F;
    private static final float FALLBACK_PEARL_DAMAGE_CHANCE = 0.08F;
    private static final int FALLBACK_CYCLES_PER_WEAR_EVENT = 4;
    private static final int CREATION_SIGNATURES_PER_PULSE = 5;
    private static final int CREATION_CHECKPOINT_SIGNATURES = 80;
    private static final int CREATION_IMPETUS_PER_PULSE = 50;
    private static final int CREATION_PARADOXUM_CAPACITY = 64;
    public static final int CREATION_COMPLETION_TOTAL_TICKS = 60;
    public static final int CREATION_COMPLETION_SWAP_TICK = CREATION_COMPLETION_TOTAL_TICKS / 2;
    private static final long CREATION_EXTRACTOR_FUEL_CAPACITY = 200000L;
    private static final int CREATION_EXTRACTOR_PARADOXUM_CAPACITY = 4096;
    private static final long IMPETUS_PER_PARADOXUM = 100L;
    private static final int CARTRIDGE_RECOVERY_ESSENTIA_PER_FATIGUE = 75;
    private static final long CARTRIDGE_RECOVERY_IMPETUS_PER_SECOND = 50L;
    private static final double CREATION_EXTRACTOR_LINK_RANGE_SQ = 16.0D * 16.0D;
    private static final net.minecraft.util.EnumFacing[] IMPETUS_PORT_FACINGS = {
            net.minecraft.util.EnumFacing.NORTH,
            net.minecraft.util.EnumFacing.EAST,
            net.minecraft.util.EnumFacing.SOUTH,
            net.minecraft.util.EnumFacing.WEST
    };
    private static final net.minecraft.util.EnumFacing[] CREATION_IMPETUS_PORT_FACINGS = {
            net.minecraft.util.EnumFacing.EAST,
            net.minecraft.util.EnumFacing.NORTH,
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
    private transient int formationStage;
    private final NonNullList<ItemStack> protocolSlots = NonNullList.withSize(PROTOCOL_SLOT_COUNT, ItemStack.EMPTY);
    private final NonNullList<ItemStack> navigationRunes = NonNullList.withSize(NAVIGATION_RUNE_SLOT_COUNT, ItemStack.EMPTY);
    private final NonNullList<ItemStack> alchemyPearls = NonNullList.withSize(ALCHEMY_PEARL_SLOT_COUNT, ItemStack.EMPTY);
    private final NonNullList<ItemStack> transmutationPrisms = NonNullList.withSize(TRANSMUTATION_PRISM_SLOT_COUNT, ItemStack.EMPTY);
    private final NonNullList<ItemStack> creationSamples = NonNullList.withSize(CREATION_SAMPLE_SLOT_COUNT, ItemStack.EMPTY);
    private final boolean[] creationActive = new boolean[CREATION_SAMPLE_SLOT_COUNT];
    private final int[] creationInstability = new int[CREATION_SAMPLE_SLOT_COUNT];
    private final int[] creationCheckpointProgress = new int[CREATION_SAMPLE_SLOT_COUNT];
    private long impetusPearlWear;
    private int fallbackFurnaceCycles;
    private ItemStack transmutationItem = ItemStack.EMPTY;
    private boolean transmutationActive;
    private int transmutationEssentia;
    private int transmutationImpetusFailures;
    private int transmutationImpetusPulse;
    private int transmutationPaymentTicks;
    private int transmutationReturnTicks;
    private int transmutationAnimationTicks;
    private transient float displayedTransmutationProgress;
    private ItemStack creationCoreItem = ItemStack.EMPTY;
    private ItemStack creationTarget = ItemStack.EMPTY;
    private int creationSignatures;
    private int creationRequired;
    private final int[] creationParadoxum = new int[CREATION_SAMPLE_SLOT_COUNT];
    private int creationFailures;
    private int creationPulseTicks;
    private int creationCompletionTicks;
    private long creationExtractorFuel;
    private int creationExtractorParadoxum;
    @Nullable private BlockPos cartridgeRecoveryOrigin;
    private int cartridgeRecoveryProgress;
    private int cartridgeRecoveryParadoxum;
    private int cartridgeRecoveryTicks;

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
        return PlayState.CONTINUE;
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
        if (world == null) return;
        updateFormationStage();
        if (formationStage < 3) return;
        if (world.isRemote) {
            tickCoreAnimationState();
            if (creationCompletionTicks > 0
                    && creationCompletionTicks < CREATION_COMPLETION_TOTAL_TICKS) creationCompletionTicks++;
            if (transmutationActive) {
                transmutationAnimationTicks++;
                displayedTransmutationProgress += (getRawTransmutationProgress() - displayedTransmutationProgress) * 0.12F;
            } else {
                displayedTransmutationProgress = 0.0F;
                if (transmutationReturnTicks > 0) transmutationReturnTicks--;
            }
            return;
        }
        tickCoreAnimationState();
        if (world.getTotalWorldTime() % 20L == 0L) {
            ensureImpetusProxyPorts();
        }
        tickTransmutation();
        tickCreationCompletion();
        tickCreationResearch();
        tickCartridgeRecovery();
        if (transmutationReturnTicks > 0 && --transmutationReturnTicks % 4 == 0) sync();
        if (world.getTotalWorldTime() % 5L == 0L) {
            absorbNavigationRunesFromBaseVoid();
            absorbTransmutationItemFromBaseVoid();
        }
    }

    /**
     * 0: hidden controller only; 1: base complete; 2: columns complete;
     * 3: upper structure and core complete.
     */
    public int getFormationStage() {
        if (world == null) return 3;
        if (formationStage < 3) updateFormationStage();
        return formationStage;
    }

    private void updateFormationStage() {
        BlockPos base = pos.down(BlockRiftonomicon.CORE_Y_OFFSET);
        int nextStage;
        if (!isFormationRangeComplete(base, 0, 0)) {
            nextStage = 0;
        } else if (!isFormationRangeComplete(base, 1, 1)) {
            nextStage = 1;
        } else if (!isFormationRangeComplete(base, 2, 4)) {
            nextStage = 2;
        } else {
            nextStage = 3;
        }
        if (nextStage == 3 && formationStage != 3) {
            coreAnimationTicks = 0;
            coreAnimationApplied = false;
            appliedCoreAnimation = null;
            syncCoreAnimationTick = true;
        }
        formationStage = nextStage;
    }

    private boolean isFormationRangeComplete(BlockPos base, int minY, int maxY) {
        for (int dy = minY; dy <= maxY; dy++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dx = -3; dx <= 3; dx++) {
                    if (!TAMultiblockTriggers.hasRiftonomiconPartAt(dx, dy, dz)) continue;
                    IBlockState state = world.getBlockState(base.add(dx, dy, dz));
                    if (state.getBlock() != TABlocks.RIFTONOMICON
                            || state.getValue(BlockRiftonomicon.PART)
                            != TAMultiblockTriggers.getRiftonomiconPartFor(dx, dy, dz)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private void ensureImpetusProxyPorts() {
        for (net.minecraft.util.EnumFacing facing : net.minecraft.util.EnumFacing.HORIZONTALS) {
            BlockPos portPos = pos.offset(facing, 2);
            IBlockState existing = world.getBlockState(portPos);
            if (!world.isAirBlock(portPos) && existing.getBlock() != TABlocks.RIFTONOMICON) continue;
            if (existing.getBlock() == TABlocks.RIFTONOMICON
                    && existing.getValue(BlockRiftonomicon.PART) == BlockRiftonomicon.Part.IMPETUS_PORT) continue;
            world.setBlockState(portPos, TABlocks.RIFTONOMICON.getDefaultState()
                    .withProperty(BlockRiftonomicon.PART, BlockRiftonomicon.Part.IMPETUS_PORT), 3);
        }
        ensureCentralTransmutationPort(pos.down(3), BlockRiftonomicon.Part.TRANSMUTATION_ESSENTIA_INPUT);
        ensureCentralTransmutationPort(pos.up(1), BlockRiftonomicon.Part.TRANSMUTATION_IMPETUS_INPUT);
    }

    private void ensureCentralTransmutationPort(BlockPos portPos, BlockRiftonomicon.Part part) {
        IBlockState state = world.getBlockState(portPos);
        if (state.getBlock() != TABlocks.RIFTONOMICON || state.getValue(BlockRiftonomicon.PART) == part) return;
        world.setBlockState(portPos, TABlocks.RIFTONOMICON.getDefaultState()
                .withProperty(BlockRiftonomicon.PART, part), 3);
    }

    private void tickTransmutation() {
        if (!transmutationActive) return;
        transmutationAnimationTicks++;
        if (!hasTransmutationProtocol() || transmutationItem.isEmpty() || getTransmutationPrismCount() <= 0) {
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
        if (transmutationEssentia >= getTransmutationEssentiaRequired()) completeTransmutation();
    }

    private boolean payTransmutationImpetus() {
        TileEntity tile = world.getTileEntity(pos.up(1));
        if (!(tile instanceof TileRiftonomiconTransmutationImpetusInput)) return false;
        TileRiftonomiconTransmutationImpetusInput input = (TileRiftonomiconTransmutationImpetusInput) tile;
        if (input.consume(TRANSMUTATION_IMPETUS_PER_SECOND, true) < TRANSMUTATION_IMPETUS_PER_SECOND) return false;
        return input.consume(TRANSMUTATION_IMPETUS_PER_SECOND, false) >= TRANSMUTATION_IMPETUS_PER_SECOND;
    }

    public boolean tryStartTransmutation() {
        if (world == null || world.isRemote || transmutationActive || !hasTransmutationProtocol()
                || transmutationItem.isEmpty() || WeaponTransmutation.isCreated(transmutationItem)
                || getTransmutationPrismCount() <= 0 || transmutationReturnTicks > 0) return false;
        transmutationActive = true;
        transmutationEssentia = 0;
        transmutationImpetusFailures = 0;
        transmutationPaymentTicks = 20;
        transmutationAnimationTicks = 0;
        sync();
        return true;
    }

    public boolean isTransmutationActive() { return transmutationActive; }
    public int getTransmutationImpetusPulse() { return transmutationImpetusPulse; }
    public float getTransmutationProgress() {
        return world != null && world.isRemote ? displayedTransmutationProgress : getRawTransmutationProgress();
    }
    private float getRawTransmutationProgress() {
        return Math.min(1.0F, transmutationEssentia / (float) getTransmutationEssentiaRequired());
    }
    public int getTransmutationReturnTicks() { return transmutationReturnTicks; }
    public int getTransmutationAnimationTicks() { return transmutationAnimationTicks; }
    public int getTransmutationMotionTicks() {
        return Math.max(20, (int) Math.ceil(getTransmutationEssentiaRequired() * 0.4D));
    }

    public boolean canAcceptTransmutationParadoxum() {
        return transmutationActive && transmutationEssentia < getTransmutationEssentiaRequired();
    }

    public int acceptTransmutationParadoxum(int amount) {
        if (!canAcceptTransmutationParadoxum() || amount <= 0) return 0;
        int accepted = Math.min(amount, getTransmutationEssentiaRequired() - transmutationEssentia);
        transmutationEssentia += accepted;
        sync();
        return accepted;
    }

    public int getCreationSlotForPort(BlockPos portPos) {
        if (portPos == null) return -1;
        for (int slot = 0; slot < CREATION_IMPETUS_PORT_FACINGS.length; slot++) {
            if (pos.offset(CREATION_IMPETUS_PORT_FACINGS[slot], 2).equals(portPos)) return slot;
        }
        return -1;
    }

    public boolean canAcceptCreationParadoxum(int slot) {
        return slot >= 0 && slot < CREATION_SAMPLE_SLOT_COUNT && creationActive[slot]
                && creationParadoxum[slot] < CREATION_PARADOXUM_CAPACITY;
    }

    public int acceptCreationParadoxum(int slot, int amount) {
        if (!canAcceptCreationParadoxum(slot) || amount <= 0) return 0;
        int accepted = Math.min(amount, CREATION_PARADOXUM_CAPACITY - creationParadoxum[slot]);
        creationParadoxum[slot] += accepted;
        sync();
        return accepted;
    }

    private void tickCreationResearch() {
        if (!hasCreationProtocol() || !hasActiveCreationResearch()) return;
        if (++creationPulseTicks < 20) return;
        creationPulseTicks = 0;

        int successfulStations = 0;
        boolean changed = false;
        for (int slot = 0; slot < CREATION_SAMPLE_SLOT_COUNT; slot++) {
            if (!creationActive[slot]) continue;
            if (!canContinueCreationResearch(slot)) {
                creationActive[slot] = false;
                changed = true;
                continue;
            }
            if (creationParadoxum[slot] <= 0 || !payCreationImpetus(slot, CREATION_IMPETUS_PER_PULSE)) continue;
            creationParadoxum[slot]--;
            creationSignatures += CREATION_SIGNATURES_PER_PULSE;
            creationCheckpointProgress[slot] += CREATION_SIGNATURES_PER_PULSE;
            creationInstability[slot] += getCreationInstabilityPerPulse();
            successfulStations++;
            changed = true;
            if (creationSignatures >= creationRequired) {
                completeCreationResearch();
                sync();
                return;
            }
            if (creationCheckpointProgress[slot] >= CREATION_CHECKPOINT_SIGNATURES) {
                creationCheckpointProgress[slot] -= CREATION_CHECKPOINT_SIGNATURES;
                runCreationStabilityCheck(slot);
            }
        }

        if (successfulStations > 1 && creationRequired > 0) {
            int pairs = successfulStations * (successfulStations - 1) / 2;
            int bonus = successfulStations * CREATION_SIGNATURES_PER_PULSE * pairs * 5 / 100;
            creationSignatures = Math.min(creationRequired, creationSignatures + bonus);
            changed |= bonus > 0;
            if (creationSignatures >= creationRequired) completeCreationResearch();
        }
        if (changed) sync();
    }

    private void tickCartridgeRecovery() {
        if (!hasCreationProtocol()) return;
        if (cartridgeRecoveryOrigin == null) {
            if (creationCoreItem.isEmpty() && !hasActiveCreationResearch() && creationCompletionTicks <= 0
                    && world.getTotalWorldTime() % 20L == 0L) {
                acquireExhaustedCartridge();
            }
            return;
        }
        if (creationCoreItem.isEmpty() || creationCoreItem.getItem() != ModBlocksItems.CALIBRATED_EXTRACT) {
            clearCartridgeRecovery();
            sync();
            return;
        }
        if (ItemCalibratedExtract.getFatigue(creationCoreItem) <= 0) {
            returnRecoveredCartridge();
            return;
        }
        if (++cartridgeRecoveryTicks < 20) return;
        cartridgeRecoveryTicks = 0;
        if (cartridgeRecoveryParadoxum <= 0
                || consumeCreationExtractorFuel(CARTRIDGE_RECOVERY_IMPETUS_PER_SECOND, true)
                < CARTRIDGE_RECOVERY_IMPETUS_PER_SECOND) return;
        if (consumeCreationExtractorFuel(CARTRIDGE_RECOVERY_IMPETUS_PER_SECOND, false)
                < CARTRIDGE_RECOVERY_IMPETUS_PER_SECOND) return;
        cartridgeRecoveryParadoxum--;
        cartridgeRecoveryProgress++;
        if (cartridgeRecoveryProgress >= CARTRIDGE_RECOVERY_ESSENTIA_PER_FATIGUE) {
            cartridgeRecoveryProgress = 0;
            ItemCalibratedExtract.restoreOneFatigue(creationCoreItem);
        }
        sync();
        if (ItemCalibratedExtract.getFatigue(creationCoreItem) <= 0) returnRecoveredCartridge();
    }

    private void acquireExhaustedCartridge() {
        List<TileRiftExtractor> extractors = new ArrayList<>();
        for (TileEntity tile : world.loadedTileEntityList) {
            if (tile instanceof TileRiftExtractor && isCreationExtractorLinked(tile.getPos())) {
                extractors.add((TileRiftExtractor) tile);
            }
        }
        extractors.sort((a, b) -> {
            int distance = Double.compare(pos.distanceSq(a.getPos()), pos.distanceSq(b.getPos()));
            return distance != 0 ? distance : Long.compare(a.getPos().toLong(), b.getPos().toLong());
        });
        for (TileRiftExtractor extractor : extractors) {
            ItemStack cartridge = extractor.takeExhaustedCartridgeForRecovery(this);
            if (cartridge.isEmpty()) continue;
            resetCreationSession();
            creationCoreItem = cartridge;
            cartridgeRecoveryOrigin = extractor.getPos().toImmutable();
            cartridgeRecoveryProgress = 0;
            cartridgeRecoveryParadoxum = 0;
            cartridgeRecoveryTicks = 0;
            sync();
            return;
        }
    }

    private void returnRecoveredCartridge() {
        if (cartridgeRecoveryOrigin == null || ItemCalibratedExtract.getFatigue(creationCoreItem) > 0) return;
        TileEntity tile = world.getTileEntity(cartridgeRecoveryOrigin);
        if (!(tile instanceof TileRiftExtractor)
                || !((TileRiftExtractor) tile).returnRecoveredCartridge(creationCoreItem, this)) return;
        creationCoreItem = ItemStack.EMPTY;
        clearCartridgeRecovery();
        sync();
    }

    private void clearCartridgeRecovery() {
        cartridgeRecoveryOrigin = null;
        cartridgeRecoveryProgress = 0;
        cartridgeRecoveryParadoxum = 0;
        cartridgeRecoveryTicks = 0;
    }

    private boolean isRecoveringCartridge() {
        return cartridgeRecoveryOrigin != null && !creationCoreItem.isEmpty()
                && creationCoreItem.getItem() == ModBlocksItems.CALIBRATED_EXTRACT;
    }

    public boolean canAcceptCartridgeRecoveryParadoxum() {
        return isRecoveringCartridge()
                && cartridgeRecoveryParadoxum < CARTRIDGE_RECOVERY_ESSENTIA_PER_FATIGUE;
    }

    public int acceptCartridgeRecoveryParadoxum(int amount) {
        if (amount <= 0 || !canAcceptCartridgeRecoveryParadoxum()) return 0;
        int accepted = Math.min(amount,
                CARTRIDGE_RECOVERY_ESSENTIA_PER_FATIGUE - cartridgeRecoveryParadoxum);
        cartridgeRecoveryParadoxum += accepted;
        sync();
        return accepted;
    }

    private boolean payCreationImpetus(int slot, long amount) {
        if (slot < 0 || slot >= CREATION_IMPETUS_PORT_FACINGS.length) return false;
        TileEntity tile = world.getTileEntity(pos.offset(CREATION_IMPETUS_PORT_FACINGS[slot], 2));
        if (!(tile instanceof TileRiftonomiconImpetusPort)) return false;
        TileRiftonomiconImpetusPort input = (TileRiftonomiconImpetusPort) tile;
        if (input.consume(amount, true) < amount) return false;
        return input.consume(amount, false) >= amount;
    }

    private int getCreationInstabilityPerPulse() {
        if (creationFailures <= 0) return 50;
        if (creationFailures == 1) return 45;
        if (creationFailures == 2) return 41;
        if (creationFailures == 3) return 38;
        return 35;
    }

    private boolean runCreationStabilityCheck(int slot) {
        if (slot < 0 || slot >= CREATION_SAMPLE_SLOT_COUNT || creationSamples.get(slot).isEmpty()) return false;
        int chanceHundredths = Math.min(9000, Math.max(0, creationInstability[slot]));
        boolean broken = chanceHundredths > 0 && world.rand.nextInt(10000) < chanceHundredths;
        if (broken) {
            creationSamples.set(slot, ItemStack.EMPTY);
            creationActive[slot] = false;
            creationCheckpointProgress[slot] = 0;
            creationInstability[slot] = 0;
            creationFailures++;
        } else {
            creationInstability[slot] /= 2;
        }
        return broken;
    }

    public boolean startCreationResearch(int slot) {
        if (world == null || world.isRemote || !hasCreationProtocol() || !isCreationSlotAvailable(slot)
                || creationActive[slot] || creationSamples.get(slot).isEmpty() || !isCreationBlankInstalled()) return false;
        ItemStack sample = creationSamples.get(slot);
        if (!isValidCreationSample(sample) || !allCreationSamplesMatch(sample)) return false;
        if (creationTarget.isEmpty()) {
            creationTarget = normalizedCreationSample(sample);
            creationRequired = calculateCreationSignatureRequirement(sample);
            creationSignatures = 0;
            creationFailures = 0;
        } else if (!creationSamplesMatch(creationTarget, sample)) {
            return false;
        }
        if (creationRequired <= 0) return false;
        creationActive[slot] = true;
        sync();
        return true;
    }

    public boolean stopCreationResearchAndCheck(int slot) {
        if (world == null || world.isRemote || slot < 0 || slot >= CREATION_SAMPLE_SLOT_COUNT
                || !creationActive[slot]) return false;
        runCreationStabilityCheck(slot);
        creationActive[slot] = false;
        creationCheckpointProgress[slot] = 0;
        sync();
        return true;
    }

    private boolean canContinueCreationResearch(int slot) {
        return isCreationSlotAvailable(slot) && isCreationBlankInstalled()
                && !creationSamples.get(slot).isEmpty()
                && !creationTarget.isEmpty()
                && creationSamplesMatch(creationTarget, creationSamples.get(slot));
    }

    private void completeCreationResearch() {
        creationSignatures = creationRequired;
        for (int slot = 0; slot < CREATION_SAMPLE_SLOT_COUNT; slot++) {
            creationActive[slot] = false;
            creationCheckpointProgress[slot] = 0;
            creationInstability[slot] = 0;
        }
        if (creationCompletionTicks <= 0) creationCompletionTicks = 1;
    }

    private void tickCreationCompletion() {
        if (creationCompletionTicks <= 0) return;
        creationCompletionTicks++;
        if (creationCompletionTicks == CREATION_COMPLETION_SWAP_TICK) {
            creationCoreItem = ItemCalibratedExtract.withTarget(ModBlocksItems.CALIBRATED_EXTRACT,
                    creationTarget, creationRequired);
            sync();
        } else if (creationCompletionTicks >= CREATION_COMPLETION_TOTAL_TICKS) {
            creationCompletionTicks = 0;
            sync();
        } else if (creationCompletionTicks % 10 == 0) {
            sync();
        }
    }

    public int getCreationCompletionTicks() { return creationCompletionTicks; }

    public boolean isCreationVisualActive() {
        return hasActiveCreationResearch() || creationCompletionTicks > 0;
    }

    public boolean insertCreationSample(int slot, EntityPlayer player) {
        if (player == null || !hasCreationProtocol() || !isCreationSlotAvailable(slot)
                || !creationSamples.get(slot).isEmpty()) return false;
        ItemStack held = player.getHeldItemMainhand();
        if (!isValidCreationSample(held) || !allCreationSamplesMatch(held)) return false;
        ItemStack stored = held.copy();
        stored.setCount(1);
        creationSamples.set(slot, stored);
        if (!player.capabilities.isCreativeMode) held.shrink(1);
        sync();
        return true;
    }

    public ItemStack removeCreationSample(int slot) {
        if (!hasCreationProtocol() || slot < 0 || slot >= CREATION_SAMPLE_SLOT_COUNT || creationActive[slot]) {
            return ItemStack.EMPTY;
        }
        ItemStack result = creationSamples.get(slot);
        if (result.isEmpty()) return ItemStack.EMPTY;
        creationSamples.set(slot, ItemStack.EMPTY);
        creationCheckpointProgress[slot] = 0;
        creationInstability[slot] = 0;
        sync();
        return result;
    }

    public ItemStack getCreationSample(int slot) {
        return slot >= 0 && slot < CREATION_SAMPLE_SLOT_COUNT ? creationSamples.get(slot) : ItemStack.EMPTY;
    }

    public boolean isCreationResearchActive(int slot) {
        return slot >= 0 && slot < CREATION_SAMPLE_SLOT_COUNT && creationActive[slot];
    }

    public int getCreationSignatureProgress() { return creationSignatures; }
    public int getCreationSignatureRequired() { return creationRequired; }
    public int getCreationInstability(int slot) {
        return slot >= 0 && slot < CREATION_SAMPLE_SLOT_COUNT ? creationInstability[slot] : 0;
    }

    public int getCreationStationLimit() {
        if (!hasCreationProtocol()) return 0;
        return Math.min(CREATION_SAMPLE_SLOT_COUNT,
                1 + getSubsystemCount(ItemRiftonomiconProtocol.META_SCALING_SUBSYSTEM));
    }

    public int getCreationExtractorLimit() {
        if (!hasCreationProtocol()) return 0;
        return Math.min(8, 2 * (1 + getSubsystemCount(ItemRiftonomiconProtocol.META_SCALING_SUBSYSTEM)));
    }

    public boolean isCreationExtractorLinked(BlockPos extractorPos) {
        if (world == null || extractorPos == null || !hasCreationProtocol()
                || pos.distanceSq(extractorPos) > CREATION_EXTRACTOR_LINK_RANGE_SQ
                || findNearestCreationCore(extractorPos) != this) return false;
        List<BlockPos> candidates = new ArrayList<>();
        for (TileEntity tile : world.loadedTileEntityList) {
            if (tile instanceof TileRiftExtractor && findNearestCreationCore(tile.getPos()) == this) {
                candidates.add(tile.getPos());
            }
        }
        candidates.sort((a, b) -> {
            int distance = Double.compare(pos.distanceSq(a), pos.distanceSq(b));
            return distance != 0 ? distance : Long.compare(a.toLong(), b.toLong());
        });
        int index = candidates.indexOf(extractorPos);
        return index >= 0 && index < getCreationExtractorLimit();
    }

    @Nullable
    private TileRiftonomicon findNearestCreationCore(BlockPos extractorPos) {
        TileRiftonomicon nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (TileEntity tile : world.loadedTileEntityList) {
            if (!(tile instanceof TileRiftonomicon)) continue;
            TileRiftonomicon core = (TileRiftonomicon) tile;
            if (!core.hasCreationProtocol()) continue;
            double distance = core.getPos().distanceSq(extractorPos);
            if (distance > CREATION_EXTRACTOR_LINK_RANGE_SQ) continue;
            if (distance < nearestDistance || distance == nearestDistance
                    && (nearest == null || core.getPos().toLong() < nearest.getPos().toLong())) {
                nearest = core;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    public long consumeCreationExtractorFuel(long amount, boolean simulate) {
        if (amount <= 0L || !hasCreationProtocol()) return 0L;
        if (ImpetusCompat.isLoaded()) {
            TileEntity tile = world.getTileEntity(pos.up(1));
            return tile instanceof TileRiftonomiconTransmutationImpetusInput
                    ? ((TileRiftonomiconTransmutationImpetusInput) tile).consume(amount, simulate) : 0L;
        }
        long consumed = Math.min(amount, creationExtractorFuel);
        if (!simulate && consumed > 0L) {
            creationExtractorFuel -= consumed;
            sync();
        }
        return consumed;
    }

    public int consumeCreationExtractorParadoxum(int amount, boolean simulate) {
        if (amount <= 0 || !hasCreationProtocol()) return 0;
        int consumed = Math.min(amount, creationExtractorParadoxum);
        if (!simulate && consumed > 0) {
            creationExtractorParadoxum -= consumed;
            sync();
        }
        return consumed;
    }

    public boolean canAcceptCreationExtractorProductionParadoxum() {
        return hasCreationProtocol()
                && creationExtractorParadoxum < CREATION_EXTRACTOR_PARADOXUM_CAPACITY;
    }

    public int acceptCreationExtractorProductionParadoxum(int amount) {
        if (amount <= 0 || !canAcceptCreationExtractorProductionParadoxum()) return 0;
        int accepted = Math.min(amount,
                CREATION_EXTRACTOR_PARADOXUM_CAPACITY - creationExtractorParadoxum);
        creationExtractorParadoxum += accepted;
        if (accepted > 0) sync();
        return accepted;
    }

    public boolean canAcceptCreationExtractorParadoxum() {
        return hasCreationProtocol() && !ImpetusCompat.isLoaded()
                && creationExtractorFuel < CREATION_EXTRACTOR_FUEL_CAPACITY;
    }

    public int acceptCreationExtractorParadoxum(int amount) {
        if (amount <= 0 || !canAcceptCreationExtractorParadoxum()) return 0;
        int accepted = (int) Math.min(amount,
                (CREATION_EXTRACTOR_FUEL_CAPACITY - creationExtractorFuel) / IMPETUS_PER_PARADOXUM);
        creationExtractorFuel += accepted * IMPETUS_PER_PARADOXUM;
        if (accepted > 0) sync();
        return accepted;
    }

    private boolean isCreationSlotAvailable(int slot) {
        return slot >= 0 && slot < getCreationStationLimit();
    }

    private boolean hasActiveCreationResearch() {
        for (boolean active : creationActive) if (active) return true;
        return false;
    }

    private int getActiveCreationResearchCount() {
        int count = 0;
        for (boolean active : creationActive) if (active) count++;
        return count;
    }

    public boolean insertCreationCore(EntityPlayer player) {
        if (player == null || !hasCreationProtocol() || !creationCoreItem.isEmpty()
                || hasActiveCreationResearch() || creationCompletionTicks > 0) return false;
        ItemStack held = player.getHeldItemMainhand();
        if (!isBaseEldritchConstruction(held)) return false;
        creationCoreItem = held.copy();
        creationCoreItem.setCount(1);
        if (!player.capabilities.isCreativeMode) held.shrink(1);
        sync();
        return true;
    }

    public ItemStack removeCreationCore() {
        if (creationCoreItem.isEmpty() || hasActiveCreationResearch() || creationCompletionTicks > 0) return ItemStack.EMPTY;
        ItemStack result = creationCoreItem;
        creationCoreItem = ItemStack.EMPTY;
        clearCartridgeRecovery();
        resetCreationSession();
        sync();
        return result;
    }

    public ItemStack getCreationCoreItem() { return creationCoreItem; }

    public NonNullList<ItemStack> removeAllCreationItems() {
        NonNullList<ItemStack> removed = NonNullList.withSize(CREATION_SAMPLE_SLOT_COUNT + 1, ItemStack.EMPTY);
        removed.set(0, creationCoreItem);
        creationCoreItem = ItemStack.EMPTY;
        clearCartridgeRecovery();
        for (int i = 0; i < CREATION_SAMPLE_SLOT_COUNT; i++) {
            if (creationActive[i]) runCreationStabilityCheck(i);
            removed.set(i + 1, creationSamples.get(i));
            creationSamples.set(i, ItemStack.EMPTY);
        }
        resetCreationSession();
        sync();
        return removed;
    }

    public NonNullList<ItemStack> removeCreationSamplesBeyondLimit() {
        NonNullList<ItemStack> removed = NonNullList.withSize(CREATION_SAMPLE_SLOT_COUNT, ItemStack.EMPTY);
        int limit = getCreationStationLimit();
        for (int i = limit; i < CREATION_SAMPLE_SLOT_COUNT; i++) {
            if (creationActive[i]) runCreationStabilityCheck(i);
            removed.set(i, creationSamples.get(i));
            creationSamples.set(i, ItemStack.EMPTY);
            creationActive[i] = false;
            creationInstability[i] = 0;
            creationCheckpointProgress[i] = 0;
        }
        sync();
        return removed;
    }

    private boolean isCreationBlankInstalled() {
        return isBaseEldritchConstruction(creationCoreItem);
    }

    private static boolean isBaseEldritchConstruction(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getItem() == Item.getItemFromBlock(TABlocks.ELDRITCH_CONSTRUCTION)
                && stack.getMetadata() == BlockEldritchConstarction.Variant.BASE.getMeta();
    }

    private void resetCreationSession() {
        creationTarget = ItemStack.EMPTY;
        creationSignatures = 0;
        creationRequired = 0;
        creationFailures = 0;
        creationCompletionTicks = 0;
        for (int i = 0; i < CREATION_SAMPLE_SLOT_COUNT; i++) {
            creationActive[i] = false;
            creationInstability[i] = 0;
            creationCheckpointProgress[i] = 0;
            creationParadoxum[i] = 0;
        }
    }

    private boolean allCreationSamplesMatch(ItemStack candidate) {
        if (!creationTarget.isEmpty() && !creationSamplesMatch(creationTarget, candidate)) return false;
        for (ItemStack sample : creationSamples) {
            if (!sample.isEmpty() && !creationSamplesMatch(sample, candidate)) return false;
        }
        return true;
    }

    private static boolean creationSamplesMatch(ItemStack first, ItemStack second) {
        if (first == null || second == null || first.isEmpty() || second.isEmpty()
                || first.getItem() != second.getItem()) return false;
        if (!first.isItemStackDamageable() && first.getMetadata() != second.getMetadata()) return false;
        return ItemStack.areItemStackTagsEqual(first, second);
    }

    private static ItemStack normalizedCreationSample(ItemStack stack) {
        ItemStack result = stack.copy();
        result.setCount(1);
        if (result.isItemStackDamageable()) result.setItemDamage(0);
        return result;
    }

    private boolean isValidCreationSample(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem() == ModBlocksItems.CALIBRATED_EXTRACT
                || hasCraftingRecipe(stack)) return false;
        AspectList aspects = ThaumcraftCraftingManager.getObjectTags(normalizedCreationSample(stack));
        return aspects != null && aspects.size() > 0 && aspects.visSize() > 0;
    }

    private static boolean hasCraftingRecipe(ItemStack stack) {
        for (IRecipe recipe : ForgeRegistries.RECIPES.getValuesCollection()) {
            ItemStack output = recipe.getRecipeOutput();
            if (sameCreationItemType(output, stack)) return true;
        }
        return false;
    }

    private static boolean sameCreationItemType(ItemStack first, ItemStack second) {
        if (first == null || second == null || first.isEmpty() || second.isEmpty()
                || first.getItem() != second.getItem()) return false;
        return first.isItemStackDamageable() || first.getMetadata() == second.getMetadata();
    }

    private static int calculateCreationSignatureRequirement(ItemStack stack) {
        AspectList aspects = ThaumcraftCraftingManager.getObjectTags(normalizedCreationSample(stack));
        if (aspects == null || aspects.size() <= 0) return 0;
        int mass = aspects.visSize();
        Aspect[] entries = aspects.getAspects();
        int distinct = entries.length;
        double entropy = 0.0D;
        if (distinct > 1 && mass > 0) {
            for (Aspect aspect : entries) {
                double share = aspects.getAmount(aspect) / (double) mass;
                if (share > 0.0D) entropy -= share * Math.log(share);
            }
            entropy /= Math.log(distinct);
        }
        double diversity = 1.0D + 0.04D * (distinct - 1) * (distinct - 1) + 0.20D * entropy;
        double raw = 100.0D + 12.0D * Math.pow(mass, 1.05D) * diversity;
        return Math.max(400, (int) (Math.ceil(raw / 50.0D) * 50.0D));
    }

    private void completeTransmutation() {
        transmuteWeapon(transmutationPrisms);
        for (int i = 0; i < transmutationPrisms.size(); i++) transmutationPrisms.set(i, ItemStack.EMPTY);
        transmutationReturnTicks = getTransmutationMotionTicks();
        stopTransmutation();
    }

    private int getTransmutationEssentiaRequired() {
        int cost = TRANSMUTATION_ESSENTIA_BASE;
        int completed = WeaponTransmutation.getTotal(transmutationItem);
        for (int i = 0; i < completed; i++) {
            cost = (int) Math.ceil(cost * 1.3D);
        }
        return cost;
    }

    private void transmuteWeapon(List<ItemStack> prisms) {
        if (transmutationItem.getItem() instanceof ItemFocus) {
            transmuteFocus(prisms);
            return;
        }
        if (transmutationItem.getItem() instanceof ItemArmor) {
            transmuteArmor(prisms);
            return;
        }
        if (!(transmutationItem.getItem() instanceof ItemTool)
                && !(transmutationItem.getItem() instanceof ItemSword)) return;

        int prismMeta = -1;
        boolean sameElement = true;
        int prismCount = 0;
        for (ItemStack prism : prisms) {
            if (prism.isEmpty()) continue;
            prismCount++;
            if (!(prism.getItem() instanceof ItemPrism)) {
                sameElement = false;
                break;
            }
            if (prismMeta < 0) prismMeta = prism.getMetadata();
            else if (prismMeta != prism.getMetadata()) {
                sameElement = false;
                break;
            }
        }

        int result = world.rand.nextInt(3); // 0 damage, 1 speed, 2 durability
        if (sameElement && prismMeta != ItemPrism.META_AQUA) {
            int preferred = prismMeta == ItemPrism.META_IGNIS ? 0
                    : prismMeta == ItemPrism.META_AIR ? 1 : 2;
            double preferredChance = Math.min(1.0D, prismCount * 0.25D);
            if (world.rand.nextDouble() < preferredChance) result = preferred;
            else {
                int other = world.rand.nextInt(2);
                result = other >= preferred ? other + 1 : other;
            }
        }

        if (result == 0) WeaponTransmutation.addDamage(transmutationItem);
        else if (result == 1) WeaponTransmutation.addSpeed(transmutationItem);
        else WeaponTransmutation.addDurability(transmutationItem);
        int historyMeta = sameElement && prismMeta >= 0 ? prismMeta
                : (result == 0 ? ItemPrism.META_IGNIS
                : result == 1 ? ItemPrism.META_AIR : ItemPrism.META_TERRA);
        int previousTransmutations = WeaponTransmutation.getTotal(transmutationItem);
        WeaponTransmutation.recordElement(transmutationItem, historyMeta);
        if (world.rand.nextDouble() < Math.min(1.0D, 0.05D + 0.02D * previousTransmutations)) {
            WeaponTransmutation.makeCreated(transmutationItem);
        }
        markDirty();
    }

    private void transmuteArmor(List<ItemStack> prisms) {
        List<Integer> aspects = new ArrayList<>();
        for (ItemStack prism : prisms) {
            if (!prism.isEmpty() && prism.getItem() instanceof ItemPrism) {
                aspects.add(prism.getMetadata());
            }
        }
        if (aspects.isEmpty()) return;

        // Equal prisms are deterministic; a mixed set selects one of the inserted prisms.
        int resultMeta = aspects.get(world.rand.nextInt(aspects.size()));
        if (resultMeta == ItemPrism.META_IGNIS) ArmorTransmutation.addArmor(transmutationItem);
        else if (resultMeta == ItemPrism.META_AIR) ArmorTransmutation.addFallProtection(transmutationItem);
        else if (resultMeta == ItemPrism.META_AQUA) ArmorTransmutation.addToughness(transmutationItem);
        else if (resultMeta == ItemPrism.META_TERRA) WeaponTransmutation.addDurability(transmutationItem);
        else return;

        int previousTransmutations = WeaponTransmutation.getTotal(transmutationItem);
        WeaponTransmutation.recordElement(transmutationItem, resultMeta);
        if (world.rand.nextDouble() < Math.min(1.0D, 0.05D + 0.02D * previousTransmutations)) {
            WeaponTransmutation.makeCreated(transmutationItem);
        }
        markDirty();
    }

    private void transmuteFocus(List<ItemStack> prisms) {
        int prismMeta = -1;
        boolean sameElement = true;
        for (ItemStack prism : prisms) {
            if (prism.isEmpty()) continue;
            if (!(prism.getItem() instanceof ItemPrism)) {
                sameElement = false;
                break;
            }
            if (prismMeta < 0) prismMeta = prism.getMetadata();
            else if (prismMeta != prism.getMetadata()) sameElement = false;
        }

        List<Integer> available = new ArrayList<>();
        for (int meta = 0; meta < 4; meta++) {
            if (FocusTransmutation.hasSettingForPrism(transmutationItem, meta)) available.add(meta);
        }
        if (available.isEmpty()) return;

        // A uniform prism set must always improve its own focus-setting group.
        // Mixed/invalid sets retain the old random fallback among applicable groups.
        if (sameElement && prismMeta >= 0 && !available.contains(prismMeta)) return;
        int resultMeta = sameElement && prismMeta >= 0
                ? prismMeta : available.get(world.rand.nextInt(available.size()));
        if (!FocusTransmutation.addForPrism(transmutationItem, resultMeta)) return;

        int previousTransmutations = WeaponTransmutation.getTotal(transmutationItem);
        WeaponTransmutation.recordElement(transmutationItem, resultMeta);
        if (world.rand.nextDouble() < Math.min(1.0D, 0.05D + 0.02D * previousTransmutations)) {
            WeaponTransmutation.makeCreated(transmutationItem);
        }
        markDirty();
    }

    private void abortTransmutation() {
        if (transmutationAnimationTicks > 0) transmutationReturnTicks = getTransmutationMotionTicks();
        stopTransmutation();
    }

    private void stopTransmutation() {
        transmutationActive = false;
        transmutationPaymentTicks = 0;
        sync();
    }

    private void tickCoreAnimationState() {
        coreAnimationTicks++;
        boolean cursorJumped = false;
        if (!coreOpenCycle && coreAnimationTicks >= CORE_IDLE_END_TICKS) {
            if (coreProtocolRequested) {
                coreOpenCycle = true;
                coreAnimationTicks = getCoreStartTicks();
            } else {
                coreAnimationTicks = 0;
            }
            cursorJumped = true;
        }

        if (coreOpenCycle && coreProtocolRequested
                && coreAnimationTicks >= getCoreHoldEndTicks()) {
            coreAnimationTicks = getCoreHoldStartTicks();
            cursorJumped = true;
        } else if (coreOpenCycle && !coreProtocolRequested
                && coreAnimationTicks >= getCoreCloseEndTicks()) {
            coreAnimationTicks = 0;
            coreOpenCycle = false;
            coreAnimationProtocolMeta = -1;
            cursorJumped = true;
        }

        if (cursorJumped && world != null && world.isRemote) syncCoreAnimationTick = true;
        if (world == null || !world.isRemote) markDirty();
    }

    private int getCoreStartTicks() {
        if (coreAnimationProtocolMeta == ItemRiftonomiconProtocol.META_NAVIGATION_PROTOCOL) return CORE_IDLE_END_TICKS;
        if (coreAnimationProtocolMeta == ItemRiftonomiconProtocol.META_ALCHEMICAL_PROTOCOL) return CORE_ALCHEMY_START_TICKS;
        if (coreAnimationProtocolMeta == ItemRiftonomiconProtocol.META_TRANSMUTATION_PROTOCOL) return CORE_TRANSMUTATION_START_TICKS;
        if (coreAnimationProtocolMeta == ItemRiftonomiconProtocol.META_CREATION_PROTOCOL) return CORE_CREATION_START_TICKS;
        return CORE_IDLE_END_TICKS;
    }

    private int getCoreHoldStartTicks() {
        if (coreAnimationProtocolMeta == ItemRiftonomiconProtocol.META_ALCHEMICAL_PROTOCOL) return CORE_ALCHEMY_HOLD_START_TICKS;
        if (coreAnimationProtocolMeta == ItemRiftonomiconProtocol.META_TRANSMUTATION_PROTOCOL) return CORE_TRANSMUTATION_HOLD_START_TICKS;
        if (coreAnimationProtocolMeta == ItemRiftonomiconProtocol.META_CREATION_PROTOCOL) return CORE_CREATION_HOLD_START_TICKS;
        return CORE_NAVIGATION_HOLD_START_TICKS;
    }

    private int getCoreHoldEndTicks() { return getCoreHoldStartTicks() + CORE_IDLE_END_TICKS; }

    private int getCoreCloseEndTicks() {
        if (coreAnimationProtocolMeta == ItemRiftonomiconProtocol.META_ALCHEMICAL_PROTOCOL) return CORE_ALCHEMY_CLOSE_END_TICKS;
        if (coreAnimationProtocolMeta == ItemRiftonomiconProtocol.META_TRANSMUTATION_PROTOCOL) return CORE_TRANSMUTATION_CLOSE_END_TICKS;
        if (coreAnimationProtocolMeta == ItemRiftonomiconProtocol.META_CREATION_PROTOCOL) return CORE_CREATION_CLOSE_END_TICKS;
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
        ItemStack stored = stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        if (!stored.isEmpty()) stored.setCount(1);
        protocolSlots.set(slot, stored);
        int activeProtocol = getProtocolMeta();
        coreProtocolRequested = activeProtocol >= 0;
        if (activeProtocol >= 0) {
            coreAnimationProtocolMeta = activeProtocol;
        }
        sync();
        return true;
    }

    public ItemStack removeProtocolFromSlot(int slot) {
        if (!isValidProtocolSlot(slot)) return ItemStack.EMPTY;
        ItemStack stored = protocolSlots.get(slot);
        if (stored.isEmpty()) return ItemStack.EMPTY;
        if (stored.getItem() == ModBlocksItems.RIFTONOMICON_PROTOCOL
                && stored.getMetadata() == ItemRiftonomiconProtocol.META_TRANSMUTATION_PROTOCOL
                && (transmutationActive || transmutationReturnTicks > 0)) return ItemStack.EMPTY;
        protocolSlots.set(slot, ItemStack.EMPTY);
        int activeProtocol = getProtocolMeta();
        coreProtocolRequested = activeProtocol >= 0;
        if (activeProtocol >= 0) coreAnimationProtocolMeta = activeProtocol;
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

    public boolean hasCreationProtocol() {
        return getProtocolMeta() == ItemRiftonomiconProtocol.META_CREATION_PROTOCOL;
    }

    public boolean isTransmutationAltarAnimationRequested() {
        return false;
    }

    public int getTransmutationAltarLimit() {
        return hasTransmutationProtocol()
                ? Math.min(4, 1 + getSubsystemCount(ItemRiftonomiconProtocol.META_PRISMATIC_SUBSYSTEM)) : 0;
    }

    public boolean hasFreeTransmutationAltarConnection(BlockPos altarPos) {
        return false;
    }

    public boolean isTransmutationAltarConnectionActive(BlockPos altarPos) {
        return false;
    }

    public ItemStack getTransmutationPrism(int slot) {
        return slot >= 0 && slot < getTransmutationAltarLimit() ? transmutationPrisms.get(slot) : ItemStack.EMPTY;
    }

    public int getTransmutationPrismCount() {
        int count = 0;
        for (int i = 0; i < getTransmutationAltarLimit(); i++) if (!transmutationPrisms.get(i).isEmpty()) count++;
        return count;
    }

    public boolean insertTransmutationPrism(ItemStack stack) {
        if (!hasTransmutationProtocol() || transmutationActive || stack == null || stack.isEmpty()
                || !(stack.getItem() instanceof ItemPrism)) return false;
        for (int i = 0; i < getTransmutationAltarLimit(); i++) {
            if (transmutationPrisms.get(i).isEmpty()) {
                ItemStack stored = stack.copy();
                stored.setCount(1);
                transmutationPrisms.set(i, stored);
                sync();
                return true;
            }
        }
        return false;
    }

    public ItemStack removeLastTransmutationPrism() {
        if (transmutationActive) return ItemStack.EMPTY;
        for (int i = transmutationPrisms.size() - 1; i >= 0; i--) {
            ItemStack stack = transmutationPrisms.get(i);
            if (!stack.isEmpty()) {
                transmutationPrisms.set(i, ItemStack.EMPTY);
                sync();
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public NonNullList<ItemStack> removeAllTransmutationPrisms() {
        NonNullList<ItemStack> removed = NonNullList.withSize(TRANSMUTATION_PRISM_SLOT_COUNT, ItemStack.EMPTY);
        for (int i = 0; i < transmutationPrisms.size(); i++) {
            removed.set(i, transmutationPrisms.get(i));
            transmutationPrisms.set(i, ItemStack.EMPTY);
        }
        sync();
        return removed;
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

        BlockPos base = pos.down(BlockRiftonomicon.CORE_Y_OFFSET);
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
        BlockPos base = pos.down(BlockRiftonomicon.CORE_Y_OFFSET);
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
        if (stack == null || stack.isEmpty() || WeaponTransmutation.isCreated(stack)) return false;
        return stack.getItem() instanceof ItemArmor
                || stack.getItem() instanceof ItemTool
                || stack.getItem() instanceof ItemSword
                || stack.getItem() instanceof ItemBow
                || stack.getItem() instanceof ItemFocus;
    }

    public ItemStack getTransmutationItem() { return transmutationItem; }

    public ItemStack removeTransmutationItem() {
        if (transmutationItem.isEmpty() || transmutationActive || transmutationReturnTicks > 0) return ItemStack.EMPTY;
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
        writeStackList(compound, TAG_TRANSMUTATION_PRISMS, transmutationPrisms);
        writeStackList(compound, TAG_CREATION_SAMPLES, creationSamples);
        compound.setLong(TAG_IMPETUS_PEARL_WEAR, impetusPearlWear);
        compound.setInteger(TAG_FALLBACK_FURNACE_CYCLES, fallbackFurnaceCycles);
        compound.setBoolean(TAG_TRANSMUTATION_ACTIVE, transmutationActive);
        compound.setInteger(TAG_TRANSMUTATION_ESSENTIA, transmutationEssentia);
        compound.setInteger(TAG_TRANSMUTATION_IMPETUS_FAILURES, transmutationImpetusFailures);
        compound.setInteger(TAG_TRANSMUTATION_IMPETUS_PULSE, transmutationImpetusPulse);
        compound.setInteger(TAG_TRANSMUTATION_RETURN_TICKS, transmutationReturnTicks);
        compound.setInteger(TAG_TRANSMUTATION_ANIMATION_TICKS, transmutationAnimationTicks);
        if (!transmutationItem.isEmpty()) {
            compound.setTag(TAG_TRANSMUTATION_ITEM, transmutationItem.writeToNBT(new NBTTagCompound()));
        }
        if (!creationCoreItem.isEmpty()) {
            compound.setTag(TAG_CREATION_CORE, creationCoreItem.writeToNBT(new NBTTagCompound()));
        }
        if (!creationTarget.isEmpty()) {
            compound.setTag(TAG_CREATION_TARGET, creationTarget.writeToNBT(new NBTTagCompound()));
        }
        int creationActiveMask = 0;
        for (int i = 0; i < CREATION_SAMPLE_SLOT_COUNT; i++) {
            if (creationActive[i]) creationActiveMask |= 1 << i;
        }
        compound.setInteger(TAG_CREATION_ACTIVE_MASK, creationActiveMask);
        compound.setInteger(TAG_CREATION_SIGNATURES, creationSignatures);
        compound.setInteger(TAG_CREATION_REQUIRED, creationRequired);
        compound.setIntArray(TAG_CREATION_PARADOXUM, creationParadoxum);
        compound.setLong(TAG_CREATION_EXTRACTOR_FUEL, creationExtractorFuel);
        compound.setInteger(TAG_CREATION_EXTRACTOR_PARADOXUM, creationExtractorParadoxum);
        if (cartridgeRecoveryOrigin != null) {
            compound.setLong(TAG_CARTRIDGE_RECOVERY_POS, cartridgeRecoveryOrigin.toLong());
        }
        compound.setInteger(TAG_CARTRIDGE_RECOVERY_PROGRESS, cartridgeRecoveryProgress);
        compound.setInteger(TAG_CARTRIDGE_RECOVERY_PARADOXUM, cartridgeRecoveryParadoxum);
        compound.setInteger(TAG_CARTRIDGE_RECOVERY_TICKS, cartridgeRecoveryTicks);
        compound.setInteger(TAG_CREATION_FAILURES, creationFailures);
        compound.setIntArray(TAG_CREATION_INSTABILITY, creationInstability);
        compound.setIntArray(TAG_CREATION_CHECKPOINT, creationCheckpointProgress);
        compound.setInteger(TAG_CREATION_COMPLETION_TICKS, creationCompletionTicks);
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
        boolean previousProtocolRequested = coreProtocolRequested;
        boolean previousOpenCycle = coreOpenCycle;
        readStackList(compound, TAG_PROTOCOLS, protocolSlots);
        readStackList(compound, TAG_NAVIGATION_RUNES, navigationRunes);
        readStackList(compound, TAG_ALCHEMY_PEARLS, alchemyPearls);
        readStackList(compound, TAG_TRANSMUTATION_PRISMS, transmutationPrisms);
        readStackList(compound, TAG_CREATION_SAMPLES, creationSamples);
        impetusPearlWear = Math.max(0L, compound.getLong(TAG_IMPETUS_PEARL_WEAR));
        fallbackFurnaceCycles = Math.max(0, compound.getInteger(TAG_FALLBACK_FURNACE_CYCLES));
        transmutationActive = compound.getBoolean(TAG_TRANSMUTATION_ACTIVE);
        transmutationEssentia = Math.max(0, compound.getInteger(TAG_TRANSMUTATION_ESSENTIA));
        transmutationImpetusFailures = Math.max(0, compound.getInteger(TAG_TRANSMUTATION_IMPETUS_FAILURES));
        transmutationImpetusPulse = Math.max(0, compound.getInteger(TAG_TRANSMUTATION_IMPETUS_PULSE));
        int receivedReturnTicks = Math.max(0, compound.getInteger(TAG_TRANSMUTATION_RETURN_TICKS));
        int receivedAnimationTicks = Math.max(0, compound.getInteger(TAG_TRANSMUTATION_ANIMATION_TICKS));
        if (world != null && world.isRemote) {
            if (receivedReturnTicks > 0 && transmutationReturnTicks <= 0) transmutationReturnTicks = receivedReturnTicks;
            if (transmutationActive) transmutationAnimationTicks = Math.max(transmutationAnimationTicks, receivedAnimationTicks);
        } else {
            transmutationReturnTicks = receivedReturnTicks;
            transmutationAnimationTicks = receivedAnimationTicks;
        }
        transmutationItem = compound.hasKey(TAG_TRANSMUTATION_ITEM)
                ? new ItemStack(compound.getCompoundTag(TAG_TRANSMUTATION_ITEM)) : ItemStack.EMPTY;
        creationCoreItem = compound.hasKey(TAG_CREATION_CORE)
                ? new ItemStack(compound.getCompoundTag(TAG_CREATION_CORE)) : ItemStack.EMPTY;
        creationTarget = compound.hasKey(TAG_CREATION_TARGET)
                ? new ItemStack(compound.getCompoundTag(TAG_CREATION_TARGET)) : ItemStack.EMPTY;
        int creationActiveMask = compound.getInteger(TAG_CREATION_ACTIVE_MASK);
        int[] savedInstability = compound.getIntArray(TAG_CREATION_INSTABILITY);
        int[] savedCheckpoints = compound.getIntArray(TAG_CREATION_CHECKPOINT);
        for (int i = 0; i < CREATION_SAMPLE_SLOT_COUNT; i++) {
            creationActive[i] = (creationActiveMask & 1 << i) != 0;
            creationInstability[i] = i < savedInstability.length ? Math.max(0, savedInstability[i]) : 0;
            creationCheckpointProgress[i] = i < savedCheckpoints.length ? Math.max(0, savedCheckpoints[i]) : 0;
        }
        creationSignatures = Math.max(0, compound.getInteger(TAG_CREATION_SIGNATURES));
        creationRequired = Math.max(0, compound.getInteger(TAG_CREATION_REQUIRED));
        int[] savedCreationParadoxum = compound.getIntArray(TAG_CREATION_PARADOXUM);
        for (int i = 0; i < CREATION_SAMPLE_SLOT_COUNT; i++) {
            creationParadoxum[i] = i < savedCreationParadoxum.length
                    ? Math.max(0, Math.min(CREATION_PARADOXUM_CAPACITY, savedCreationParadoxum[i])) : 0;
        }
        creationExtractorFuel = Math.max(0L, Math.min(CREATION_EXTRACTOR_FUEL_CAPACITY,
                compound.getLong(TAG_CREATION_EXTRACTOR_FUEL)));
        creationExtractorParadoxum = Math.max(0, Math.min(CREATION_EXTRACTOR_PARADOXUM_CAPACITY,
                compound.getInteger(TAG_CREATION_EXTRACTOR_PARADOXUM)));
        cartridgeRecoveryOrigin = compound.hasKey(TAG_CARTRIDGE_RECOVERY_POS)
                ? BlockPos.fromLong(compound.getLong(TAG_CARTRIDGE_RECOVERY_POS)) : null;
        cartridgeRecoveryProgress = Math.max(0, Math.min(CARTRIDGE_RECOVERY_ESSENTIA_PER_FATIGUE - 1,
                compound.getInteger(TAG_CARTRIDGE_RECOVERY_PROGRESS)));
        cartridgeRecoveryParadoxum = Math.max(0, Math.min(CARTRIDGE_RECOVERY_ESSENTIA_PER_FATIGUE,
                compound.getInteger(TAG_CARTRIDGE_RECOVERY_PARADOXUM)));
        cartridgeRecoveryTicks = Math.max(0, Math.min(19,
                compound.getInteger(TAG_CARTRIDGE_RECOVERY_TICKS)));
        creationFailures = Math.max(0, compound.getInteger(TAG_CREATION_FAILURES));
        creationCompletionTicks = Math.max(0, Math.min(CREATION_COMPLETION_TOTAL_TICKS,
                compound.getInteger(TAG_CREATION_COMPLETION_TICKS)));
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
                ? Math.floorMod(compound.getInteger(TAG_CORE_ANIMATION_TICKS), CORE_CREATION_CLOSE_END_TICKS)
                : 0;
        if (!hasNewCycleState && coreOpenCycle && savedTicks < CORE_NAVIGATION_HOLD_START_TICKS) {
            savedTicks += CORE_NAVIGATION_HOLD_START_TICKS;
        }
        coreAnimationTicks = normalizeCoreAnimationTicks(savedTicks);
        // Routine packets need no hard rewind. State changes do: they switch the
        // controller to another segment of the same 39-second animation.
        if (previousAnimationProtocol != coreAnimationProtocolMeta
                || previousProtocolRequested != coreProtocolRequested
                || previousOpenCycle != coreOpenCycle) {
            syncCoreAnimationTick = true;
        }
    }

    private int normalizeCoreAnimationTicks(int savedTicks) {
        if (!coreOpenCycle) return Math.floorMod(savedTicks, CORE_IDLE_END_TICKS);

        int startTicks = getCoreStartTicks();
        int holdStartTicks = getCoreHoldStartTicks();
        int holdEndTicks = getCoreHoldEndTicks();
        int closeEndTicks = getCoreCloseEndTicks();
        if (coreProtocolRequested) {
            if (savedTicks < startTicks || savedTicks >= holdEndTicks) {
                return holdStartTicks + Math.floorMod(savedTicks, CORE_IDLE_END_TICKS);
            }
            return savedTicks;
        }
        if (savedTicks < startTicks || savedTicks >= closeEndTicks) return holdEndTicks;
        return savedTicks;
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
