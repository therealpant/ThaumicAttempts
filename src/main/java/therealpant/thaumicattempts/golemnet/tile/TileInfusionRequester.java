package therealpant.thaumicattempts.golemnet.tile;

import com.mojang.authlib.GameProfile;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.items.*;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.items.wrapper.RangedWrapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.casters.IInteractWithCaster;
import thaumcraft.api.golems.GolemHelper;
import thaumcraft.common.golems.EntityThaumcraftGolem;
import thaumcraft.common.tiles.crafting.TileInfusionMatrix;
import therealpant.thaumicattempts.api.*;
import therealpant.thaumicattempts.golemcraft.item.ItemBasePattern;
import therealpant.thaumicattempts.golemcraft.item.ItemInfusionPattern;
import therealpant.thaumicattempts.util.ItemKey;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Consumer;

/**
 * Infusion Requester:
 * - 15 patterns (ItemInfusionPattern)
 * - 1 special slot (gauntlet / caster)
 * - 9 result slots
 * - internal input buffer for delivered resources
 * - bound storages (pedestals) where resources are distributed before starting matrix
 *
 * PICKUP LOGIC:
 * - After click: wait until TileInfusionMatrix.crafting becomes TRUE (craft started)
 * - Then wait until it becomes FALSE (craft finished)
 * - Then pull EVERYTHING from ALL bound storages into results (leftovers -> drop)
 */
public class TileInfusionRequester extends TileEntity implements ITickable, IPatternedWorksite,
        ITerminalOrderAcceptor, ITerminalOrderIconProvider, ICraftEndpoint, CraftOrderApi.TagProvider, IAnimatable {

    private static final Logger LOG = LogManager.getLogger("ThaumicAttempts/InfusionRequester");

    // выключи если логов слишком много
    private static final boolean DEBUG = true;

    private void logDebug(String msg, Object... args) {
        if (!DEBUG) return;
        LOG.info("[{} @ {}] " + msg,
                world == null ? "no-world" : (world.isRemote ? "C" : "S"),
                pos == null ? "nullpos" : pos.toString(),
                args);
    }

    private void logWarn(String msg, Object... args) {
        LOG.warn("[{} @ {}] " + msg,
                world == null ? "no-world" : (world.isRemote ? "C" : "S"),
                pos == null ? "nullpos" : pos.toString(),
                args);
    }

    // -------------------- constants --------------------

    public static final int PATTERN_SLOT_COUNT = 15;
    private final AnimationFactory factory = new AnimationFactory(this);

    private static final String TAG_PATTERNS = "patterns";
    private static final String TAG_SPECIAL  = "special";
    private static final String TAG_RESULTS  = "results";
    private static final String TAG_INPUT    = "input";
    private static final String TAG_STORAGES = "storages";
    private static final String TAG_TARGET   = "target";
    private static final String TAG_GOLEMS   = "golems";
    private static final String TAG_GOLEM_ID = "id";
    private static final String TAG_MANAGER  = "manager";
    private static final String TAG_OWNER_ID   = "OwnerId";
    private static final String TAG_OWNER_NAME = "OwnerName";

    private static final String TAG_JOB_ACTIVE         = "JobActive";
    private static final String TAG_JOB_STAGE          = "JobStage";
    private static final String TAG_JOB_SLOT           = "JobSlot";
    private static final String TAG_JOB_QUEUE          = "JobQueue";
    private static final String TAG_JOB_INTERACT       = "JobInteractDelay";
    private static final String TAG_JOB_MATRIX_POS     = "JobMatrixPos";
    private static final String TAG_JOB_MATRIX_STARTED = "JobMatrixStarted";
    private static final String TAG_JOB_MATRIX_TICKS   = "JobMatrixTicks";
    private static final String TAG_JOB_CRAFTS_TOTAL   = "JobCraftsTotal";
    private static final String TAG_JOB_CRAFTS_LEFT    = "JobCraftsLeft";
    private static final String TAG_CRAFT_DELIVERIES   = "CraftDeliveries";

    private static final int MAX_QUEUED_ORDERS = 8;

    private static final int INTERACT_DELAY_TICKS = 40;
    private static final int PEDESTAL_DELAY_TICKS = 20;

    private static final int WAIT_MATRIX_TIMEOUT_TICKS = 20 * 180; // 3 minutes

    private int interactDelayCounter = 0;

    // -------------------- inventories --------------------

    /** Internal buffer where golems/hoppers insert resources. */
    private final ItemStackHandler input = new ItemStackHandler(27) {
        @Override protected void onContentsChanged(int slot) { markDirtyAndSync(); }
    };

    private final ItemStackHandler patterns = new ItemStackHandler(PATTERN_SLOT_COUNT) {
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return !stack.isEmpty() && stack.getItem() instanceof ItemInfusionPattern;
        }
        @Override protected void onContentsChanged(int slot) { markDirtyAndSync(); }
    };

    private final ItemStackHandler specialSlot = new ItemStackHandler(1) {
        @Override protected void onContentsChanged(int slot) { markDirtyAndSync(); }
    };

    /** 9 result slots */
    private final ItemStackHandler results = new ItemStackHandler(9) {
        @Override protected void onContentsChanged(int slot) { markDirtyAndSync(); }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return true; }
    };

    /** combined: IMPORTANT input first */
    private final IItemHandler combined = new CombinedInvWrapper(input, patterns, specialSlot, results);
    /** expose only results to DOWN (so hoppers pull only craft output) */
    private final IItemHandler resultsOnly = new RangedWrapper(results, 0, results.getSlots());

    // -------------------- links --------------------

    private final List<BlockPos> storages = new ArrayList<>();
    @Nullable private BlockPos targetPos = null;
    private final LinkedHashSet<UUID> boundGolems = new LinkedHashSet<>();

    private final ArrayDeque<QueuedJob> queuedTriggers = new ArrayDeque<>();
    private int lastSignal = 0;

    // -------------------- job state --------------------

    private final ArrayDeque<PendingCrafterDelivery> pendingCraftDeliveries = new ArrayDeque<>();
    private boolean jobActive = false;
    private int activeSlot = -1;
    private int jobCraftsTotal = 1;
    private int jobCraftsLeft = 0;
    private int tickCounter = 0;

    private enum Stage { NONE, WAIT_RESOURCES, INTERACT_TARGET, WAIT_PICKUP }
    private Stage stage = Stage.NONE;

    // Phase 1: deliver resources to requester (input)
    private final Map<ItemKey, Integer> pendingToRequester = new LinkedHashMap<>();
    private final Map<ItemKey, Integer> baselineToRequester = new HashMap<>();

    // Phase 2: distribute from requester(input) to storages
    private final Map<Integer, Map<ItemKey, Integer>> pendingByStorage = new LinkedHashMap<>();
    private int distributeCurrentStorage = -1;
    private int distributeDelayTicks = 0;
    private boolean distributeFxPlayed = false;

    // provisioning
    private boolean needsEnsure = false;
    private int lastEnsureTick = -9999;
    private int nextProvisionTick = 0;
    private static final int PROVISION_INTERVAL = 10;
    private static final int ENSURE_INTERVAL = 20;

    @Nullable private BlockPos managerPos = null;
    private boolean managerFromPattern = false;

    @Nullable private UUID ownerId = null;
    @Nullable private String ownerName = null;

    // matrix tracking
    @Nullable private BlockPos jobMatrixPos = null;
    private boolean jobMatrixStarted = false;
    private int waitMatrixTicks = 0;

    // -------------------- GeckoLib --------------------

    @Override
    public void registerControllers(AnimationData data) {
        data.addAnimationController(new AnimationController<>(
                this, "infusion_requester_controller", 0, this::animationPredicate));
    }

    @Override
    public AnimationFactory getFactory() { return factory; }

    private <E extends IAnimatable> PlayState animationPredicate(AnimationEvent<E> event) {
        event.getController().setAnimation(
                new AnimationBuilder().addAnimation("animation.infusion_requester.new", true));
        return PlayState.CONTINUE;
    }

    // -------------------- Internal classes --------------------

    private static final class PendingCrafterDelivery {
        final BlockPos dest;
        final int destSide;
        @Nullable final BlockPos manager;
        final ItemStack like1;
        int remaining;

        PendingCrafterDelivery(BlockPos dest, int destSide, @Nullable BlockPos manager, ItemStack like1, int remaining) {
            this.dest = dest.toImmutable();
            this.destSide = destSide;
            this.manager = (manager == null) ? null : manager.toImmutable();
            this.like1 = like1.copy();
            this.like1.setCount(1);
            this.remaining = Math.max(0, remaining);
        }

        NBTTagCompound serialize() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setLong("Dest", dest.toLong());
            tag.setInteger("Side", destSide);
            if (manager != null) tag.setLong("Mgr", manager.toLong());
            tag.setTag("Like", like1.serializeNBT());
            tag.setInteger("Remain", remaining);
            return tag;
        }

        static PendingCrafterDelivery deserialize(NBTTagCompound tag) {
            BlockPos dest = BlockPos.fromLong(tag.getLong("Dest"));
            int side = tag.getInteger("Side");
            BlockPos manager = tag.hasKey("Mgr") ? BlockPos.fromLong(tag.getLong("Mgr")) : null;
            ItemStack like = new ItemStack(tag.getCompoundTag("Like"));
            int remain = tag.getInteger("Remain");
            return new PendingCrafterDelivery(dest, side, manager, like, remain);
        }
    }

    private static final class QueuedJob {
        final int slot;
        final int crafts;

        QueuedJob(int slot, int crafts) {
            this.slot = slot;
            this.crafts = Math.max(1, crafts);
        }

        NBTTagCompound serialize() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("s", slot);
            tag.setInteger("c", crafts);
            return tag;
        }

        static QueuedJob deserialize(NBTTagCompound tag) {
            int slot = tag.getInteger("s");
            int crafts = tag.hasKey("c") ? tag.getInteger("c") : 1;
            return new QueuedJob(slot, crafts);
        }
    }

    // -------------------- IPatternedWorksite --------------------

    @Override
    public PatternRedstoneMode getRedstoneMode() {
        return PatternRedstoneMode.RISING_EDGE_SELECTS_SLOT;
    }

    @Override
    public PatternProvisioningSpec getProvisioningSpec() {
        return new PatternProvisioningSpec(EnumFacing.UP, true, true, PROVISION_INTERVAL);
    }

    @Override public ItemStackHandler getPatternHandler() { return patterns; }
    public ItemStackHandler getInputHandler() { return input; }
    public ItemStackHandler getSpecialHandler() { return specialSlot; }
    public ItemStackHandler getResultHandler() { return results; }
    public Integer getActivePatternIndex() { return activeSlot; }
    public @Nullable BlockPos getManagerPos() { return managerPos; }

    @Override public void setManagerPosFromPattern(@Nullable BlockPos managerPos) { setManagerPos(managerPos); }
    @Override public @Nullable BlockPos getManagerPosForPattern() { return managerPos; }

    public void setManagerPos(@Nullable BlockPos pos) { setManagerPos(pos, false); }

    private boolean hasPatternRequesterAbove() {
        if (world == null) return false;
        TileEntity te = world.getTileEntity(pos.up());
        return te instanceof TilePatternRequester;
    }

    private boolean useManagerForProvision() {
        return hasPatternRequesterAbove() && managerPos != null;
    }

    private void syncManagerFromPattern() {
        if (world == null || world.isRemote) return;

        TileEntity above = world.getTileEntity(pos.up());
        if (above instanceof TilePatternRequester) {
            TilePatternRequester requester = (TilePatternRequester) above;
            BlockPos patternManager = requester.getManagerPos();
            if (patternManager != null) setManagerPos(patternManager, true);
            else if (managerFromPattern) setManagerPos(null, false);
        } else if (managerFromPattern) {
            setManagerPos(null, false);
        }
    }

    private void setManagerPos(@Nullable BlockPos pos, boolean fromPattern) {
        boolean previousManager = useManagerForProvision();
        BlockPos newPos = pos == null ? null : pos.toImmutable();
        boolean newFlag = fromPattern && newPos != null;

        if (!Objects.equals(this.managerPos, newPos) || this.managerFromPattern != newFlag) {
            this.managerPos = newPos;
            this.managerFromPattern = newFlag;
            markDirtyAndSync();

            boolean nowManager = useManagerForProvision();
            if (nowManager) {
                needsEnsure = needsEnsure || !pendingToRequester.isEmpty();
                nextProvisionTick = tickCounter;
            } else if (previousManager && !pendingToRequester.isEmpty()) {
                lastEnsureTick = tickCounter;
                nextProvisionTick = tickCounter;
            }

            logDebug("Manager set to {} fromPattern={}", this.managerPos, this.managerFromPattern);
        }
    }

    public void setOwner(@Nullable EntityPlayer player) {
        if (player == null) {
            this.ownerId = null;
            this.ownerName = null;
        } else {
            this.ownerId = player.getUniqueID();
            this.ownerName = player.getName();
        }
        markDirtyAndSync();
        logDebug("Owner set id={} name={}", ownerId, ownerName);
    }

    public boolean tryInsertPattern(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemInfusionPattern)) return false;
        for (int i = 0; i < patterns.getSlots(); i++) {
            if (patterns.getStackInSlot(i).isEmpty()) {
                ItemStack copy = stack.copy();
                copy.setCount(1);
                patterns.setStackInSlot(i, copy);
                markDirtyAndSync();
                logDebug("Inserted pattern into slot {}", i);
                return true;
            }
        }
        return false;
    }

    @Override
    public int enqueueFromPatternRequester(int patternSlot, int times) {
        int before = queuedTriggers.size();
        enqueueTrigger(patternSlot, Math.max(1, times), false);
        int after = queuedTriggers.size();
        int added = Math.max(0, after - before);
        logDebug("enqueueFromPatternRequester slot={} times={} added={} queueNow={}", patternSlot, times, added, queuedTriggers.size());
        return added;
    }

    @Override
    public int enqueueFromPatternRequester(ItemStack resultLike, int times) {
        if (resultLike == null || resultLike.isEmpty()) return 0;
        int slot = findPatternSlotFor(resultLike);
        if (slot < 0) return 0;
        return enqueueFromPatternRequester(slot, times);
    }

    public int enqueueCrafterOrder(@Nullable BlockPos managerPos, BlockPos dest, int destSide, ItemStack resultLike, int items) {
        if (dest == null || resultLike == null || resultLike.isEmpty() || items <= 0) return 0;

        int perCraft = Math.max(1, getPerCraftOutputCountFor(resultLike));
        int crafts = (items + perCraft - 1) / perCraft;
        int accepted = enqueueFromPatternRequester(resultLike, crafts);
        if (accepted <= 0) return 0;

        int totalOut = Math.max(1, accepted * perCraft);
        pendingCraftDeliveries.add(new PendingCrafterDelivery(dest, destSide, managerPos, resultLike, totalOut));

        if (managerPos != null && this.managerPos == null) setManagerPos(managerPos, true);
        markDirtyAndSync();

        logDebug("enqueueCrafterOrder dest={} side={} like={} items={} crafts={} accepted={} totalOut={}",
                dest, destSide, resultLike, items, crafts, accepted, totalOut);

        return accepted;
    }

    public ItemStack tryExtractPattern() {
        for (int i = patterns.getSlots() - 1; i >= 0; i--) {
            ItemStack st = patterns.getStackInSlot(i);
            if (!st.isEmpty()) {
                patterns.setStackInSlot(i, ItemStack.EMPTY);
                markDirtyAndSync();
                logDebug("Extracted pattern from slot {}", i);
                return st;
            }
        }
        return ItemStack.EMPTY;
    }

    // ========================= UPDATE =========================

    @Override
    public void onLoad() {
        super.onLoad();
        if (world != null && !world.isRemote) {
            this.lastSignal = readSignal();
            syncManagerFromPattern();
            logDebug("onLoad signal={} storages={} target={} manager={}", lastSignal, storages.size(), targetPos, managerPos);
        }
    }

    @Override
    public void update() {
        if (world == null) return;

        if (!world.isRemote) {
            syncManagerFromPattern();
            if (managerPos != null) {
                TileEntity te = world.getTileEntity(managerPos);
                if (te instanceof TileMirrorManager) {
                    ((TileMirrorManager) te).registerRequester(this.pos);
                }
            }
        }

        if (world.isRemote) return;

        tickCounter++;

        int signal = readSignal();
        if (signal != lastSignal) {
            logDebug("Redstone changed {} -> {}", lastSignal, signal);
            if (signal > 0) {
                int slot = patternIndexFromSignal(signal);
                if (slot >= 0) {
                    enqueueTrigger(slot, 1, true);
                    tryStartNextJob();
                }
            }
            lastSignal = signal;
        }

        if (!jobActive) {
            tryStartNextJob();
            deliverStoredResultsToDestinations();
            return;
        }

        if (stage == Stage.WAIT_RESOURCES) {
            boolean changed = reconcilePendingToRequester();
            ensurePendingToRequester(changed ? 0 : ENSURE_INTERVAL);

            if (pendingToRequester.isEmpty()) {
                stage = Stage.INTERACT_TARGET;
                interactDelayCounter = 0;
                markDirtyAndSync();
                logDebug("Stage -> INTERACT_TARGET");
            }
        }
        else if (stage == Stage.INTERACT_TARGET) {

            boolean distributed = distributeFromInputToStorages();
            if (!distributed) {
                deliverStoredResultsToDestinations();
                return;
            }

            if (interactDelayCounter < INTERACT_DELAY_TICKS) {
                interactDelayCounter++;
                deliverStoredResultsToDestinations();
                return;
            }

            boolean triggered = performTargetInteraction();
            if (!triggered) {
                interactDelayCounter = 0;
                markDirtyAndSync();
                logDebug("performTargetInteraction returned FALSE -> stay INTERACT_TARGET (will retry)");
                deliverStoredResultsToDestinations();
                return;
            }

            stage = Stage.WAIT_PICKUP;
            jobMatrixStarted = false;
            waitMatrixTicks = 0;

            jobMatrixPos = targetPos;
            TileInfusionMatrix mx = resolveMatrix();

            logDebug("Stage -> WAIT_PICKUP, matrix={} matrixPos={}", mx == null ? "null" : "found", jobMatrixPos);

            interactDelayCounter = 0;
            markDirtyAndSync();

            deliverStoredResultsToDestinations();
        }
        else if (stage == Stage.WAIT_PICKUP) {

            waitMatrixTicks++;

            TileInfusionMatrix matrix = resolveMatrix();
            if (matrix == null) {
                if (waitMatrixTicks >= WAIT_MATRIX_TIMEOUT_TICKS) {
                    logWarn("WAIT_PICKUP timeout: matrix not found for {} ticks. Pulling storages and clearing job.", waitMatrixTicks);
                    pullAllStoragesIntoResults();
                    clearJob();
                }
                deliverStoredResultsToDestinations();
                return;
            }

            boolean crafting = matrix.crafting;
            boolean active = matrix.active;

            if (DEBUG && (waitMatrixTicks % 20 == 0)) {
                logDebug("WAIT_PICKUP status: matrixPos={} active={} crafting={} started={} waitTicks={}",
                        jobMatrixPos, active, crafting, jobMatrixStarted, waitMatrixTicks);
            }

            if (!jobMatrixStarted) {
                if (crafting) {
                    jobMatrixStarted = true;
                    markDirtyAndSync();
                }
                deliverStoredResultsToDestinations();
                return;
            }

            if (!crafting) {
                pullAllStoragesIntoResults();
                if (jobCraftsLeft > 1) {
                    jobCraftsLeft--;
                    prepareNextCraft();
                } else {
                    clearJob();
                }
                deliverStoredResultsToDestinations();
                return;
            }

            deliverStoredResultsToDestinations();
        }

        deliverStoredResultsToDestinations();
    }

    // ========================= TERMINAL / CRAFT API =========================

    public void triggerExternalRequest(int slot, int count) {
        if (world == null || world.isRemote) return;
        enqueueTrigger(slot, count, false);
        tryStartNextJob();
    }

    @Override
    public void triggerFromTerminal(int slot, int count) {
        triggerExternalRequest(slot, count);
    }

    @Override
    public List<ItemStack> listTerminalOrderIcons() {
        if (world == null) return Collections.emptyList();

        ArrayList<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < patterns.getSlots(); i++) {
            ItemStack pat = patterns.getStackInSlot(i);
            if (pat.isEmpty() || !(pat.getItem() instanceof ItemInfusionPattern)) continue;

            ItemStack preview = TerminalOrderApi.stripOrderIconData(ItemInfusionPattern.calcResultPreview(pat, world));
            if (preview.isEmpty()) continue;

            ItemStack icon = TerminalOrderApi.makeOrderIcon(preview, world.getBlockState(pos).getBlock(), pos, i);
            if (!icon.isEmpty()) out.add(icon);
        }
        return out;
    }

    @Override
    public List<ItemStack> listCraftableResults() {
        if (world == null) return Collections.emptyList();

        ArrayList<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < patterns.getSlots(); i++) {
            ItemStack pat = patterns.getStackInSlot(i);
            if (pat.isEmpty() || !(pat.getItem() instanceof ItemInfusionPattern)) continue;

            ItemStack preview = TerminalOrderApi.stripOrderIconData(ItemInfusionPattern.calcResultPreview(pat, world));
            if (preview.isEmpty()) continue;

            ItemStack one = preview.copy();
            if (one.getCount() <= 0) one.setCount(1);
            out.add(one);
        }
        return out;
    }

    @Override
    public int getPerCraftOutputCountFor(ItemStack like) {
        if (world == null || like == null || like.isEmpty()) return 0;

        int slot = findPatternSlotFor(like);
        if (slot < 0) return 0;

        ItemStack pat = patterns.getStackInSlot(slot);
        if (pat.isEmpty() || !(pat.getItem() instanceof ItemInfusionPattern)) return 0;

        ItemStack preview = ItemInfusionPattern.calcResultPreview(pat, world);
        if (preview.isEmpty()) return 0;

        return Math.max(1, preview.getCount());
    }

    @Override
    public void enqueueCraft(ItemStack resultLike, int crafts) {
        enqueueFromPatternRequester(resultLike, crafts);
    }

    @Override
    public boolean hasActiveOrQueued() {
        return jobActive || !queuedTriggers.isEmpty();
    }

    @Override
    public Set<String> getCraftOrderTags() {
        return CraftOrderApi.singletonTag(CraftOrderApi.TAG_CRAFTER);
    }

    // ========================= PATTERN LOOKUP / MATCH =========================

    public int findPatternSlotFor(ItemStack like) {
        if (world == null || like == null || like.isEmpty()) return -1;
        for (int i = 0; i < patterns.getSlots(); i++) {
            ItemStack pat = patterns.getStackInSlot(i);
            if (pat.isEmpty() || !(pat.getItem() instanceof ItemInfusionPattern)) continue;

            ItemStack preview = TerminalOrderApi.stripOrderIconData(ItemInfusionPattern.calcResultPreview(pat, world));
            if (preview.isEmpty()) continue;

            boolean match = (preview.getMaxStackSize() == 1)
                    ? (preview.getItem() == like.getItem() && (!preview.getHasSubtypes() || preview.getMetadata() == like.getMetadata()))
                    : ItemHandlerHelper.canItemStacksStackRelaxed(preview, like);
            if (match) return i;
        }
        return -1;
    }

    private boolean matchesOrder(ItemStack preview, ItemStack like) {
        if (preview == null || like == null || preview.isEmpty() || like.isEmpty()) return false;
        if (preview.getMaxStackSize() == 1 || like.getMaxStackSize() == 1) {
            if (preview.getItem() != like.getItem()) return false;
            return !preview.getHasSubtypes() || preview.getMetadata() == like.getMetadata();
        }
        return ItemHandlerHelper.canItemStacksStackRelaxed(preview, like);
    }

    // ========================= DELIVERY OUT (results -> destinations) =========================

    private int deliverPendingCraftResult(ItemStack stack) {
        if (stack == null || stack.isEmpty() || world == null || pendingCraftDeliveries.isEmpty()) return 0;
        int before = stack.getCount();

        for (Iterator<PendingCrafterDelivery> it = pendingCraftDeliveries.iterator(); it.hasNext() && !stack.isEmpty(); ) {
            PendingCrafterDelivery order = it.next();
            if (!matchesOrder(order.like1, stack)) continue;

            int toSend = Math.min(stack.getCount(), Math.max(1, order.remaining));
            ItemStack attempt = stack.copy();
            attempt.setCount(toSend);

            TileMirrorManager manager = null;
            if (order.manager != null && world.getTileEntity(order.manager) instanceof TileMirrorManager) {
                manager = (TileMirrorManager) world.getTileEntity(order.manager);
            }

            int moved;
            if (manager != null) {
                ItemStack remain = manager.acceptProvisionResult(attempt);
                moved = toSend - (remain.isEmpty() ? 0 : remain.getCount());
                if (moved > 0) {
                    Map<ItemKey, Integer> need = new LinkedHashMap<>();
                    need.put(ItemKey.of(order.like1), moved);
                    manager.ensureDeliveryForExact(order.dest, need, 0);
                }
            } else {
                moved = CraftOrderApi.insertIntoDestination(world, order.dest, order.destSide, attempt);
                if (moved > 0) {
                    ItemStack delivered = attempt.copy();
                    delivered.setCount(moved);
                    TileEntity te = world.getTileEntity(order.dest);
                    if (te instanceof TileOrderTerminal) {
                        ((TileOrderTerminal) te).onDelivered(delivered, moved);
                    }
                }
            }

            if (moved > 0) {
                stack.shrink(moved);
                order.remaining = Math.max(0, order.remaining - moved);
                if (order.remaining <= 0) it.remove();
            }
        }

        return before - (stack.isEmpty() ? 0 : stack.getCount());
    }

    private void deliverStoredResultsToDestinations() {
        if (pendingCraftDeliveries.isEmpty()) return;

        for (int i = 0; i < results.getSlots(); i++) {
            ItemStack slot = results.getStackInSlot(i);
            if (slot.isEmpty()) continue;

            ItemStack copy = slot.copy();
            int moved = deliverPendingCraftResult(copy);
            if (moved > 0) {
                int left = Math.max(0, slot.getCount() - moved);
                if (left <= 0) results.setStackInSlot(i, ItemStack.EMPTY);
                else {
                    copy.setCount(left);
                    results.setStackInSlot(i, copy);
                }
                markDirtyAndSync();
            }
        }
    }

    // ========================= JOB QUEUE =========================

    private void enqueueTrigger(int slot, int count, boolean applyPatternRepeat) {
        if (slot < 0 || slot >= patterns.getSlots()) return;
        if (count <= 0) return;

        int repeat = applyPatternRepeat ? getPatternRepeatCount(slot) : 1;
        int total = Math.max(1, count) * Math.max(1, repeat);
        int room = MAX_QUEUED_ORDERS - getQueuedCraftsCount();
        if (room <= 0) return;

        int toAdd = Math.min(total, room);
        if (toAdd <= 0) return;

        queuedTriggers.add(new QueuedJob(slot, toAdd));
        markDirtyAndSync();
    }

    private void tryStartNextJob() {
        if (jobActive) return;

        QueuedJob  next = queuedTriggers.poll();
        if (next == null) return;

        if (!hasPatternInSlot(next.slot) || storages.isEmpty()) {
            markDirtyAndSync();
            return;
        }

        jobActive = true;
        activeSlot = next.slot;
        jobCraftsTotal = Math.max(1, next.crafts);
        jobCraftsLeft = jobCraftsTotal;
        markDirtyAndSync();
        onJobTriggered(next.slot, jobCraftsLeft);
    }

    private void resetDistributionState() {
        distributeCurrentStorage = -1;
        distributeDelayTicks = 0;
        distributeFxPlayed = false;
    }

    private void onJobTriggered(int slot, int crafts) {
        if (!hasPatternInSlot(slot) || storages.isEmpty()) {
            clearJob();
            return;
        }

        List<PatternResourceList.Entry> resources = getResourcesForSlot(slot);
        if (resources.isEmpty()) {
            clearJob();
            return;
        }

        pendingToRequester.clear();
        baselineToRequester.clear();
        pendingByStorage.clear();
        resetDistributionState();

        stage = Stage.WAIT_RESOURCES;
        jobActive = true;
        activeSlot = slot;
        jobCraftsTotal = Math.max(1, crafts);
        jobCraftsLeft = jobCraftsTotal;
        interactDelayCounter = 0;

        jobMatrixPos = null;
        jobMatrixStarted = false;
        waitMatrixTicks = 0;

        int storageCount = storages.size();

        for (PatternResourceList.Entry entry : resources) {
            if (entry == null || entry.getKey() == null || entry.getKey() == ItemKey.EMPTY) continue;

            ItemKey key = entry.getKey();
            int count = Math.max(1, entry.getCount());
            int total = Math.max(1, count) * jobCraftsLeft;

            baselineToRequester.putIfAbsent(key, countInInputLike(key));
            pendingToRequester.merge(key, total, Integer::sum);
        }

        buildPendingByStorageForCraft(resources, storageCount);

        ensurePendingToRequester(0);
        markDirtyAndSync();
    }

    private void prepareNextCraft() {
        if (!hasPatternInSlot(activeSlot) || storages.isEmpty()) return;

        List<PatternResourceList.Entry> resources = getResourcesForSlot(activeSlot);
        if (resources.isEmpty()) return;

        pendingToRequester.clear();
        baselineToRequester.clear();
        int storageCount = storages.size();
        buildPendingByStorageForCraft(resources, storageCount);

        for (PatternResourceList.Entry entry : resources) {
            if (entry == null || entry.getKey() == null || entry.getKey() == ItemKey.EMPTY) continue;

            ItemKey key = entry.getKey();
            int count = Math.max(1, entry.getCount());

            int have = countInInputLike(key);
            if (have < count) {
                pendingToRequester.put(key, count - have);
                baselineToRequester.put(key, have);
            }
        }
        if (pendingToRequester.isEmpty()) {
            stage = Stage.INTERACT_TARGET;
        } else {
            stage = Stage.WAIT_RESOURCES;
            ensurePendingToRequester(0);
        }

        interactDelayCounter = 0;
        jobMatrixStarted = false;
        waitMatrixTicks = 0;
        markDirtyAndSync();
    }

    private void clearJob() {

        jobActive = false;
        activeSlot = -1;
        stage = Stage.NONE;
        jobCraftsTotal = 1;
        jobCraftsLeft = 0;

        pendingToRequester.clear();
        baselineToRequester.clear();
        pendingByStorage.clear();

        needsEnsure = false;
        lastEnsureTick = tickCounter;
        nextProvisionTick = tickCounter;

        interactDelayCounter = 0;

        jobMatrixPos = null;
        jobMatrixStarted = false;
        waitMatrixTicks = 0;

        resetDistributionState();
        markDirtyAndSync();
        tryStartNextJob();
    }

    // ========================= RESOURCES PHASE 1 =========================

    private boolean hasPatternInSlot(int slot) {
        if (slot < 0 || slot >= patterns.getSlots()) return false;
        ItemStack stack = patterns.getStackInSlot(slot);
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemInfusionPattern)) return false;
        return !PatternResourceList.build(stack).isEmpty();
    }

    private int getPatternRepeatCount(int slot) {
        if (slot < 0 || slot >= patterns.getSlots()) return 1;
        ItemStack stack = patterns.getStackInSlot(slot);
        if (stack.isEmpty()) return 1;
        if (stack.getItem() instanceof ItemBasePattern) {
            return ItemBasePattern.getRepeatCount(stack);
        }
        return 1;
    }

    private int getQueuedCraftsCount() {
        int total = 0;
        for (QueuedJob job : queuedTriggers) {
            if (job != null) total += Math.max(0, job.crafts);
        }
        return total;
    }

    private void buildPendingByStorageForCraft(List<PatternResourceList.Entry> resources, int storageCount) {
        pendingByStorage.clear();
        resetDistributionState();

        if (storageCount <= 0 || resources == null || resources.isEmpty()) return;

        int idx = 0;
        for (PatternResourceList.Entry entry : resources) {
            if (entry == null || entry.getKey() == null || entry.getKey() == ItemKey.EMPTY) continue;

            ItemKey key = entry.getKey();
            int count = Math.max(1, entry.getCount());

            for (int i = 0; i < count; i++) {
                int target = idx % storageCount;
                Map<ItemKey, Integer> need = pendingByStorage.computeIfAbsent(target, k -> new LinkedHashMap<>());
                need.merge(key, 1, Integer::sum);
                idx++;
            }
        }
    }

    public List<PatternResourceList.Entry> getResourcesForSlot(int slot) {
        if (!hasPatternInSlot(slot)) return Collections.emptyList();
        return PatternResourceList.build(patterns.getStackInSlot(slot));
    }

    private int countInInputLike(ItemKey key) {
        if (key == null || key == ItemKey.EMPTY) return 0;

        ItemStack like = key.toStack(1);
        if (like.isEmpty()) return 0;

        int total = 0;
        for (int i = 0; i < input.getSlots(); i++) {
            ItemStack slot = input.getStackInSlot(i);
            if (slot.isEmpty()) continue;
            if (ItemHandlerHelper.canItemStacksStackRelaxed(slot, like)) total += slot.getCount();
        }
        return total;
    }

    private boolean reconcilePendingToRequester() {
        boolean changed = false;

        for (Iterator<Map.Entry<ItemKey, Integer>> it = pendingToRequester.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<ItemKey, Integer> e = it.next();
            ItemKey key = e.getKey();
            int want = Math.max(1, e.getValue());
            int baseline = Math.max(0, baselineToRequester.getOrDefault(key, 0));

            int have = countInInputLike(key);
            int delivered = Math.max(0, have - baseline);
            int left = Math.max(0, want - delivered);

            if (left <= 0) {
                it.remove();
                changed = true;
            } else if (left != e.getValue()) {
                e.setValue(left);
                changed = true;
            }
        }

        if (changed) {
            needsEnsure = true;
            markDirtyAndSync();
            logDebug("reconcilePendingToRequester changed. pendingNow={}", pendingToRequester);
        }

        return changed;
    }

    private static ItemStack normalizeForProvision(ItemStack like, int amount) {
        if (like == null || like.isEmpty()) return ItemStack.EMPTY;
        ItemStack copy = like.copy();
        copy.setCount(Math.max(1, amount));
        return copy;
    }

    private void requestProvisionToRequester(Map<ItemKey, Integer> needs) {
        if (world == null || needs == null || needs.isEmpty()) return;

        BlockPos here = this.pos;
        for (Map.Entry<ItemKey, Integer> entry : needs.entrySet()) {
            ItemKey key = entry.getKey();
            if (key == null || key == ItemKey.EMPTY) continue;

            ItemStack like = key.toStack(1);
            if (like.isEmpty()) continue;

            int remaining = Math.max(1, entry.getValue());
            while (remaining > 0) {
                int chunk = Math.min(remaining, Math.max(1, like.getMaxStackSize()));
                ItemStack req = normalizeForProvision(like, chunk);
                if (!req.isEmpty()) {
                    GolemHelper.requestProvisioning(world, here, EnumFacing.UP, req, 0);
                }
                remaining -= chunk;
            }
        }

        logDebug("requestProvisionToRequester sent needs={}", needs);
    }

    private void ensurePendingToRequester(int interval) {
        if (world == null || pendingToRequester.isEmpty()) return;
        if (!needsEnsure && (tickCounter - lastEnsureTick) < interval) return;

        if (useManagerForProvision() && world.getTileEntity(managerPos) instanceof TileMirrorManager) {
            TileMirrorManager mgr = (TileMirrorManager) world.getTileEntity(managerPos);
            mgr.ensureDeliveryForExact(pos, new LinkedHashMap<>(pendingToRequester), 0);
            needsEnsure = false;
            lastEnsureTick = tickCounter;
            logDebug("ensurePendingToRequester via manager={} needs={}", managerPos, pendingToRequester);
            return;
        }

        if (tickCounter < nextProvisionTick) return;

        if (!useManagerForProvision()) {
            requestProvisionToRequester(pendingToRequester);
        }

        lastEnsureTick = tickCounter;
        nextProvisionTick = tickCounter + PROVISION_INTERVAL;
        needsEnsure = false;
    }

    // ========================= RESOURCES PHASE 2 (DISTRIBUTE) =========================

    private int moveFromInputToStorage(ItemKey key, int maxAmount, IItemHandler storageHandler) {
        if (key == null || key == ItemKey.EMPTY || maxAmount <= 0) return 0;
        ItemStack like = key.toStack(1);
        if (like.isEmpty()) return 0;

        int moved = 0;
        for (int i = 0; i < input.getSlots() && maxAmount > 0; i++) {
            ItemStack slot = input.getStackInSlot(i);
            if (slot.isEmpty()) continue;
            if (!ItemHandlerHelper.canItemStacksStackRelaxed(slot, like)) continue;

            int extract = Math.min(maxAmount, slot.getCount());
            ItemStack toInsert = input.extractItem(i, extract, true);
            if (toInsert.isEmpty()) continue;

            ItemStack remaining = ItemHandlerHelper.insertItem(storageHandler, toInsert, false);
            int inserted = toInsert.getCount() - (remaining.isEmpty() ? 0 : remaining.getCount());
            if (inserted > 0) {
                input.extractItem(i, inserted, false);
                moved += inserted;
                maxAmount -= inserted;
            }
        }
        return moved;
    }

    private void playPedestalFinishFX(BlockPos pedestalPos) {
        if (world == null || world.isRemote || pedestalPos == null) return;

        IBlockState state = world.getBlockState(pedestalPos);
        Block block = state.getBlock();
        if (block == null) return;

        world.addBlockEvent(pedestalPos, block, 12, 0);
        logDebug("playPedestalFinishFX at {}", pedestalPos);
    }

    private int findNextStorageIndex() {
        if (pendingByStorage.isEmpty()) return -1;

        int bestGreater = Integer.MAX_VALUE;
        int bestAny = Integer.MAX_VALUE;

        for (Integer idx : pendingByStorage.keySet()) {
            if (idx == null || idx < 0) continue;
            if (idx > distributeCurrentStorage && idx < bestGreater) bestGreater = idx;
            if (idx < bestAny) bestAny = idx;
        }

        if (bestGreater != Integer.MAX_VALUE) return bestGreater;
        return bestAny == Integer.MAX_VALUE ? -1 : bestAny;
    }

    private boolean distributeFromInputToStorages() {
        if (world == null) return true;

        if (storages.isEmpty() || pendingByStorage.isEmpty()) {
            resetDistributionState();
            return true;
        }

        if (distributeDelayTicks > 0) {
            distributeDelayTicks--;
            return false;
        }

        Map<ItemKey, Integer> needs = pendingByStorage.get(distributeCurrentStorage);
        if (distributeCurrentStorage < 0 || needs == null || needs.isEmpty()) {
            if (needs != null && needs.isEmpty()) pendingByStorage.remove(distributeCurrentStorage);

            if (pendingByStorage.isEmpty()) {
                resetDistributionState();
                return true;
            }

            distributeCurrentStorage = findNextStorageIndex();
            distributeFxPlayed = false;

            if (distributeCurrentStorage < 0) {
                resetDistributionState();
                return true;
            }

            needs = pendingByStorage.get(distributeCurrentStorage);
            if (needs == null || needs.isEmpty()) return false;
        }

        BlockPos storagePos = storages.get(Math.max(0, Math.min(distributeCurrentStorage, storages.size() - 1)));
        IItemHandler handler = getStorageHandler(storagePos);
        if (handler == null) {
            logWarn("distribute: storage handler missing at {} (idx={})", storagePos, distributeCurrentStorage);
            pendingByStorage.remove(distributeCurrentStorage);
            distributeCurrentStorage = -1;
            return pendingByStorage.isEmpty();
        }

        boolean anyMovedHere = false;

        for (Iterator<Map.Entry<ItemKey, Integer>> itNeed = needs.entrySet().iterator(); itNeed.hasNext(); ) {
            Map.Entry<ItemKey, Integer> needEntry = itNeed.next();
            ItemKey key = needEntry.getKey();
            int want = Math.max(1, needEntry.getValue());

            int moved = moveFromInputToStorage(key, want, handler);
            if (moved > 0) {
                anyMovedHere = true;

                if (!distributeFxPlayed) {
                    playPedestalFinishFX(storagePos);
                    distributeFxPlayed = true;
                }

                int left = Math.max(0, want - moved);
                if (left <= 0) itNeed.remove();
                else needEntry.setValue(left);
            }
        }

        if (anyMovedHere) {
            markDirtyAndSync();
            logDebug("distribute: moved into {} pendingHereNow={}", storagePos, needs);
        }

        if (needs.isEmpty()) {
            pendingByStorage.remove(distributeCurrentStorage);
            distributeCurrentStorage = -1;

            if (pendingByStorage.isEmpty()) {
                resetDistributionState();
                logDebug("distribute: finished all storages");
                return true;
            } else {
                distributeDelayTicks = PEDESTAL_DELAY_TICKS;
                distributeFxPlayed = false;
                logDebug("distribute: finished one storage, delay {} ticks", distributeDelayTicks);
                return false;
            }
        }

        return false;
    }

    // ========================= INTERACT (CLICK MATRIX) =========================

    /**
     * FIX: TileInfusionMatrix.onCasterRightClick(...) returns FALSE always.
     * So we treat "triggered" for matrix as: after click -> matrix.active || matrix.crafting.
     */
    private boolean performTargetInteraction() {
        if (world == null || world.isRemote) return false;
        if (targetPos == null) {
            logWarn("performTargetInteraction: targetPos is NULL");
            return true;
        }

        ItemStack stack = specialSlot.getStackInSlot(0);
        if (stack.isEmpty()) {
            logWarn("performTargetInteraction: specialSlot empty");
            return false;
        }

        IBlockState state = world.getBlockState(targetPos);
        Block block = state.getBlock();
        if (block == null || block.isAir(state, world, targetPos)) {
            logWarn("performTargetInteraction: target block is air at {}", targetPos);
            return false;
        }

        if (!(world instanceof WorldServer)) return false;
        WorldServer ws = (WorldServer) world;

        EntityPlayerMP ownerEntity = null;

        if (ownerId != null) {
            for (EntityPlayerMP p : ws.getMinecraftServer().getPlayerList().getPlayers()) {
                if (p.getUniqueID().equals(ownerId)) { ownerEntity = p; break; }
            }
        }
        if (ownerEntity == null && ownerName != null) {
            for (EntityPlayerMP p : ws.getMinecraftServer().getPlayerList().getPlayers()) {
                if (ownerName.equals(p.getName())) { ownerEntity = p; break; }
            }
        }
        if (ownerEntity == null) {
            double cx = targetPos.getX() + 0.5, cy = targetPos.getY() + 0.5, cz = targetPos.getZ() + 0.5;
            double bestDist = Double.MAX_VALUE;
            for (EntityPlayerMP p : ws.getMinecraftServer().getPlayerList().getPlayers()) {
                double d = p.getDistanceSq(cx, cy, cz);
                if (d < bestDist) { bestDist = d; ownerEntity = p; }
            }
        }

        GameProfile profile = (ownerEntity != null)
                ? ownerEntity.getGameProfile()
                : new GameProfile(UUID.randomUUID(), "[TA_Infusion_NoOwner]");

        FakePlayer fake = FakePlayerFactory.get(ws, profile);

        if (ownerEntity != null) {
            IPlayerKnowledge src = ThaumcraftCapabilities.getKnowledge(ownerEntity);
            IPlayerKnowledge dst = ThaumcraftCapabilities.getKnowledge(fake);
            if (src != null && dst != null) {
                Capability<IPlayerKnowledge> cap = ThaumcraftCapabilities.KNOWLEDGE;
                @SuppressWarnings("unchecked")
                NBTBase data = cap.getStorage().writeNBT(cap, src, null);
                if (data != null) cap.getStorage().readNBT(cap, dst, null, data);
            }
        }

        fake.setPosition(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
        fake.rotationYaw = 0;
        fake.rotationPitch = 0;

        ItemStack held = stack.copy();
        fake.setHeldItem(EnumHand.MAIN_HAND, held);

        TileEntity te = world.getTileEntity(targetPos);

        boolean triggered = false;
        boolean calledCaster = false;

        try {
            if (te instanceof IInteractWithCaster) {
                calledCaster = true;
                boolean res = ((IInteractWithCaster) te).onCasterRightClick(world, held, fake, targetPos, EnumFacing.UP, EnumHand.MAIN_HAND);

                // IMPORTANT FIX for infusion matrix
                if (te instanceof TileInfusionMatrix) {
                    TileInfusionMatrix m = (TileInfusionMatrix) te;
                    triggered = m.active || m.crafting;
                    logDebug("performTargetInteraction: Matrix click res={} -> active={} crafting={} => triggered={}",
                            res, m.active, m.crafting, triggered);
                } else {
                    triggered = res;
                    logDebug("performTargetInteraction: IInteractWithCaster res={} te={}", res, te.getClass().getName());
                }
            } else {
                EnumActionResult itemRes = held.onItemUse(fake, world, targetPos, EnumHand.MAIN_HAND, EnumFacing.UP, 0.5F, 0.5F, 0.5F);
                if (itemRes == EnumActionResult.SUCCESS) {
                    triggered = true;
                    logDebug("performTargetInteraction: onItemUse SUCCESS");
                } else {
                    boolean act = block.onBlockActivated(world, targetPos, state, fake, EnumHand.MAIN_HAND, EnumFacing.UP, 0.5F, 0.5F, 0.5F);
                    triggered = act;
                    logDebug("performTargetInteraction: onBlockActivated={}", act);
                }
            }
        } catch (Throwable t) {
            logWarn("performTargetInteraction exception: {}", t.toString());
            triggered = false;
        }

        ItemStack after = fake.getHeldItemMainhand();
        specialSlot.setStackInSlot(0, after);
        markDirtyAndSync();

        // дополнительный fallback: если матрица уже активна/крафтит, но triggered=false
        if (!triggered && te instanceof TileInfusionMatrix) {
            TileInfusionMatrix m = (TileInfusionMatrix) te;
            if (m.active || m.crafting) {
                triggered = true;
                logDebug("performTargetInteraction fallback: matrix active/crafting -> treat as triggered");
            }
        }

        if (!triggered) {
            logDebug("performTargetInteraction FAILED (calledCaster={} te={})", calledCaster, te == null ? "null" : te.getClass().getName());
        }

        return triggered;
    }

    // ========================= MATRIX RESOLVE =========================

    @Nullable
    private TileInfusionMatrix resolveMatrix() {
        if (world == null) return null;

        BlockPos base = (jobMatrixPos != null) ? jobMatrixPos : targetPos;
        if (base == null) return null;

        BlockPos[] scan = new BlockPos[] {
                base,
                base.up(), base.up(2), base.up(3),
                base.down(), base.down(2), base.down(3)
        };

        for (BlockPos p : scan) {
            TileEntity te = world.getTileEntity(p);
            if (te instanceof TileInfusionMatrix) {
                if (jobMatrixPos == null || !jobMatrixPos.equals(p)) {
                    logDebug("resolveMatrix: found matrix at {} (base={})", p, base);
                }
                jobMatrixPos = p.toImmutable();
                return (TileInfusionMatrix) te;
            }
        }

        // редкий лог
        if (DEBUG && (tickCounter % 40 == 0)) {
            logWarn("resolveMatrix: NOT FOUND. base={} target={} jobMatrixPos={}", base, targetPos, jobMatrixPos);
        }

        return null;
    }

    // ========================= PULL ALL FROM STORAGES =========================

    private void pullAllStoragesIntoResults() {
        if (world == null || storages.isEmpty()) {
            logWarn("pullAllStoragesIntoResults: nothing (storages={})", storages.size());
            return;
        }

        int movedTotal = 0;

        for (BlockPos storagePos : storages) {
            if (storagePos == null) continue;
            IItemHandler handler = getStorageHandler(storagePos);
            if (handler == null) {
                logWarn("pullAllStoragesIntoResults: no handler at {}", storagePos);
                continue;
            }
            movedTotal += drainHandlerIntoResults(handler, storagePos);
        }

        markDirtyAndSync();
        logDebug("pullAllStoragesIntoResults DONE movedTotal={} storages={}", movedTotal, storages.size());
    }

    private int drainHandlerIntoResults(IItemHandler handler, BlockPos storagePos) {
        if (handler == null) return 0;

        final int[] moved = new int[]{0};

        Consumer<ItemStack> inserter = stack -> {
            if (stack == null || stack.isEmpty()) return;

            ItemStack before = stack.copy();
            ItemStack remaining = ItemHandlerHelper.insertItem(results, before, false);

            int inserted = before.getCount() - (remaining.isEmpty() ? 0 : remaining.getCount());
            if (inserted > 0) moved[0] += inserted;

            if (!remaining.isEmpty()) dropStack(remaining);
        };

        for (int i = 0; i < handler.getSlots(); i++) {
            int limit = Math.max(1, handler.getSlotLimit(i));
            ItemStack canExtract = handler.extractItem(i, limit, true);
            if (canExtract.isEmpty()) continue;

            ItemStack extracted = handler.extractItem(i, canExtract.getCount(), false);
            if (!extracted.isEmpty()) {
                inserter.accept(extracted);
                logDebug("drain {} slot={} extracted={} (handler={})",
                        storagePos, i, extracted, handler.getClass().getName());
            }
        }

        return moved[0];
    }


    private void dropStack(ItemStack stack) {
        if (world == null || stack == null || stack.isEmpty()) return;
        EntityItem entity = new EntityItem(world, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, stack);
        entity.setDefaultPickupDelay();
        world.spawnEntity(entity);
    }

    // ========================= HELPERS =========================

    private int readSignal() {
        if (world == null) return 0;
        int signal = 0;
        for (EnumFacing f : EnumFacing.values()) signal = Math.max(signal, world.getRedstonePower(pos, f));
        signal = Math.max(signal, world.getStrongPower(pos));
        return Math.max(0, Math.min(15, signal));
    }

    private int patternIndexFromSignal(int signal) {
        if (signal <= 0) return -1;
        return Math.min(signal - 1, patterns.getSlots() - 1);
    }

    public int bindStorage(BlockPos pos) {
        if (pos == null) return -1;
        BlockPos immutable = pos.toImmutable();
        int existing = storages.indexOf(immutable);
        if (existing >= 0) return existing + 1;
        storages.add(immutable);
        markDirtyAndSync();
        logDebug("bindStorage added {} total={}", immutable, storages.size());
        return storages.size();
    }

    public List<BlockPos> getStorages() { return new ArrayList<>(storages); }

    public boolean setTargetPos(BlockPos pos) {
        BlockPos immutable = pos == null ? null : pos.toImmutable();
        if ((targetPos == null && immutable == null) || (targetPos != null && targetPos.equals(immutable))) return false;
        targetPos = immutable;
        markDirtyAndSync();
        logDebug("setTargetPos -> {}", targetPos);
        return true;
    }

    @Nullable public BlockPos getTargetPos() { return targetPos; }

    public boolean tryBindGolem(EntityThaumcraftGolem golem) {
        if (world == null || world.isRemote) return false;
        if (golem == null || golem.isDead) return false;

        UUID id = golem.getUniqueID();
        if (id == null) return false;
        if (boundGolems.contains(id)) return true;
        boundGolems.add(id);
        markDirtyAndSync();
        logDebug("Bound golem {}", id);
        return true;
    }

    public Set<UUID> getBoundGolemsSnapshot() { return new LinkedHashSet<>(boundGolems); }

    public void dropContents() {
        if (world == null || world.isRemote) return;
        dropHandler(input);
        dropHandler(patterns);
        dropHandler(specialSlot);
        dropHandler(results);
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return true;
        return super.hasCapability(capability, facing);
    }

    @Nullable
    private IItemHandler getStorageHandler(BlockPos storagePos) {
        if (world == null || storagePos == null) return null;
        TileEntity te = world.getTileEntity(storagePos);
        if (te == null) return null;

        for (EnumFacing f : EnumFacing.values()) {
            IItemHandler h = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, f);
            if (h != null) return h;
        }
        IItemHandler h = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (h != null) return h;

        if (te instanceof IInventory) {
            return new InvWrapper((IInventory) te);
        }

        return null;
    }

    @Nullable
    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            if (facing == EnumFacing.DOWN) {
                return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(resultsOnly);
            }
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(combined);
        }
        return super.getCapability(capability, facing);
    }

    // ========================= NBT =========================

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);

        compound.setTag(TAG_INPUT,   input.serializeNBT());
        compound.setTag(TAG_PATTERNS, patterns.serializeNBT());
        compound.setTag(TAG_SPECIAL,  specialSlot.serializeNBT());
        compound.setTag(TAG_RESULTS,  results.serializeNBT());

        NBTTagList storeList = new NBTTagList();
        for (BlockPos p : storages) {
            if (p == null) continue;
            NBTTagCompound el = new NBTTagCompound();
            el.setLong("p", p.toLong());
            storeList.appendTag(el);
        }
        compound.setTag(TAG_STORAGES, storeList);

        if (targetPos != null) compound.setLong(TAG_TARGET, targetPos.toLong());
        if (managerPos != null) compound.setLong(TAG_MANAGER, managerPos.toLong());
        compound.setBoolean("ManagerPattern", managerFromPattern && managerPos != null);

        if (ownerId != null) compound.setUniqueId(TAG_OWNER_ID, ownerId);
        if (ownerName != null) compound.setString(TAG_OWNER_NAME, ownerName);

        NBTTagList golemList = new NBTTagList();
        for (UUID id : boundGolems) {
            if (id == null) continue;
            NBTTagCompound el = new NBTTagCompound();
            el.setUniqueId(TAG_GOLEM_ID, id);
            golemList.appendTag(el);
        }
        compound.setTag(TAG_GOLEMS, golemList);

        NBTTagList craftList = new NBTTagList();
        for (PendingCrafterDelivery delivery : pendingCraftDeliveries) {
            if (delivery == null) continue;
            craftList.appendTag(delivery.serialize());
        }
        compound.setTag(TAG_CRAFT_DELIVERIES, craftList);

        compound.setBoolean(TAG_JOB_ACTIVE, jobActive);
        compound.setInteger(TAG_JOB_STAGE, stage.ordinal());
        compound.setInteger(TAG_JOB_SLOT, activeSlot);
        compound.setInteger(TAG_JOB_INTERACT, interactDelayCounter);
        compound.setInteger(TAG_JOB_CRAFTS_TOTAL, jobCraftsTotal);
        compound.setInteger(TAG_JOB_CRAFTS_LEFT, jobCraftsLeft);

        if (jobMatrixPos != null) compound.setLong(TAG_JOB_MATRIX_POS, jobMatrixPos.toLong());
        compound.setBoolean(TAG_JOB_MATRIX_STARTED, jobMatrixStarted);
        compound.setInteger(TAG_JOB_MATRIX_TICKS, waitMatrixTicks);

        NBTTagList qList = new NBTTagList();
        for (QueuedJob job : queuedTriggers) {
            if (job == null) continue;
            qList.appendTag(job.serialize());
        }
        compound.setTag(TAG_JOB_QUEUE, qList);

        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);

        if (compound.hasKey(TAG_INPUT))    input.deserializeNBT(compound.getCompoundTag(TAG_INPUT));
        if (compound.hasKey(TAG_PATTERNS)) patterns.deserializeNBT(compound.getCompoundTag(TAG_PATTERNS));
        if (compound.hasKey(TAG_SPECIAL))  specialSlot.deserializeNBT(compound.getCompoundTag(TAG_SPECIAL));
        if (compound.hasKey(TAG_RESULTS))  results.deserializeNBT(compound.getCompoundTag(TAG_RESULTS));

        storages.clear();
        if (compound.hasKey(TAG_STORAGES, Constants.NBT.TAG_LIST)) {
            NBTTagList list = compound.getTagList(TAG_STORAGES, Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound el = list.getCompoundTagAt(i);
                if (el.hasKey("p")) storages.add(BlockPos.fromLong(el.getLong("p")));
            }
        }

        targetPos = compound.hasKey(TAG_TARGET) ? BlockPos.fromLong(compound.getLong(TAG_TARGET)) : null;

        managerPos = compound.hasKey(TAG_MANAGER) ? BlockPos.fromLong(compound.getLong(TAG_MANAGER)) : null;
        managerFromPattern = compound.getBoolean("ManagerPattern") && managerPos != null;

        ownerId = compound.hasUniqueId(TAG_OWNER_ID) ? compound.getUniqueId(TAG_OWNER_ID) : null;
        ownerName = compound.hasKey(TAG_OWNER_NAME) ? compound.getString(TAG_OWNER_NAME) : null;

        boundGolems.clear();
        if (compound.hasKey(TAG_GOLEMS, Constants.NBT.TAG_LIST)) {
            NBTTagList list = compound.getTagList(TAG_GOLEMS, Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound el = list.getCompoundTagAt(i);
                if (el.hasUniqueId(TAG_GOLEM_ID)) boundGolems.add(el.getUniqueId(TAG_GOLEM_ID));
            }
        }

        pendingCraftDeliveries.clear();
        if (compound.hasKey(TAG_CRAFT_DELIVERIES, Constants.NBT.TAG_LIST)) {
            NBTTagList list = compound.getTagList(TAG_CRAFT_DELIVERIES, Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound el = list.getCompoundTagAt(i);
                pendingCraftDeliveries.add(PendingCrafterDelivery.deserialize(el));
            }
        }

        jobActive = compound.getBoolean(TAG_JOB_ACTIVE);

        int stOrd = compound.getInteger(TAG_JOB_STAGE);
        if (stOrd < 0 || stOrd >= Stage.values().length) stage = Stage.NONE;
        else stage = Stage.values()[stOrd];

        activeSlot = compound.getInteger(TAG_JOB_SLOT);
        interactDelayCounter = compound.getInteger(TAG_JOB_INTERACT);
        jobCraftsTotal = compound.hasKey(TAG_JOB_CRAFTS_TOTAL) ? compound.getInteger(TAG_JOB_CRAFTS_TOTAL) : 1;
        jobCraftsLeft = compound.hasKey(TAG_JOB_CRAFTS_LEFT) ? compound.getInteger(TAG_JOB_CRAFTS_LEFT) : 0;
        if (jobActive && jobCraftsLeft <= 0) {
            jobCraftsLeft = Math.max(1, jobCraftsTotal);
        }

        jobMatrixPos = compound.hasKey(TAG_JOB_MATRIX_POS) ? BlockPos.fromLong(compound.getLong(TAG_JOB_MATRIX_POS)) : null;
        jobMatrixStarted = compound.getBoolean(TAG_JOB_MATRIX_STARTED);
        waitMatrixTicks = compound.hasKey(TAG_JOB_MATRIX_TICKS) ? compound.getInteger(TAG_JOB_MATRIX_TICKS) : 0;

        queuedTriggers.clear();
        if (compound.hasKey(TAG_JOB_QUEUE, Constants.NBT.TAG_LIST)) {
            NBTTagList qList = compound.getTagList(TAG_JOB_QUEUE, Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < qList.tagCount(); i++) {
                NBTTagCompound el = qList.getCompoundTagAt(i);
                if (el.hasKey("s")) queuedTriggers.add(QueuedJob.deserialize(el));
            }
        }

        resetDistributionState();

    }

    private void dropHandler(ItemStackHandler handler) {
        if (handler == null || world == null) return;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                handler.setStackInSlot(i, ItemStack.EMPTY);
                EntityItem ei = new EntityItem(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack.copy());
                world.spawnEntity(ei);
            }
        }
    }

    private void markDirtyAndSync() {
        markDirty();
        if (world != null && !world.isRemote) {
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        }
    }
}
