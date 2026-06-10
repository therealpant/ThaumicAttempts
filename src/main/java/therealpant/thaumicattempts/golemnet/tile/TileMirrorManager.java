package therealpant.thaumicattempts.golemnet.tile;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.api.golems.tasks.Task;
import thaumcraft.common.golems.seals.SealEntity;
import thaumcraft.common.golems.seals.SealHandler;
import thaumcraft.common.golems.seals.SealProvide;
import thaumcraft.common.lib.events.EssentiaHandler;
import therealpant.thaumicattempts.api.ICraftEndpoint;
import therealpant.thaumicattempts.api.CraftOrderApi;
import therealpant.thaumicattempts.api.IPatternedWorksite;
import therealpant.thaumicattempts.api.ITerminalOrderIconProvider;
import therealpant.thaumicattempts.api.TerminalOrderApi;
import therealpant.thaumicattempts.golemcraft.item.ItemResourceList;
import therealpant.thaumicattempts.golemcraft.tile.TileEntityGolemCrafter;
import therealpant.thaumicattempts.golemnet.net.msg.S2CFlyAnim;
import therealpant.thaumicattempts.golemnet.net.msg.S2CPatternRequesterAnim;
import therealpant.thaumicattempts.golemnet.tile.TileRevisionPiedestal;
import therealpant.thaumicattempts.init.ModBlocksItems;
import therealpant.thaumicattempts.integration.TcLogisticsCompat;

import therealpant.thaumicattempts.util.ItemKey;
import therealpant.thaumicattempts.util.ResourceIdentity;
import therealpant.thaumicattempts.util.ThaumcraftProvisionHelper;

import javax.annotation.Nullable;
import java.util.*;


import java.util.UUID;

/**
 * Менеджер: батчи + единая «корона» зеркал (активные/подвешенные), без дюпа.
 */
public class TileMirrorManager extends TileEntity implements ITickable, IAnimatable {
    private final AnimationFactory factory = new AnimationFactory(this);
    /* ===================== Базовые лимиты и апгрейды ===================== */
    private int dispatcherRoundRobinIndex = 0;
    int craftsScheduled = 0;

    private static final int BASE_RANGE = 32;
    private static final int BASE_MIRROR_CAP = 1;
    private static final int BASE_COMPUTE_CAP = 0;

    private static final int STAB_RANGE_INC = 8;
    private static final int STAB_MIRROR_INC = 2;
    private static final int CORE_COMPUTE_INC = 2;

    private int calcRange = BASE_RANGE;
    private int calcMirrorCap = BASE_MIRROR_CAP;
    private int calcComputeCap = BASE_COMPUTE_CAP;

    private final Set<BlockPos> boundTerminals = new HashSet<>();
    private final Set<BlockPos> boundRequesters = new HashSet<>();
    private final Set<BlockPos> boundRevisionPedestals = new HashSet<>();
    private final LinkedHashMap<BlockPos, DispatcherStats> boundDispatchers = new LinkedHashMap<>();
    private static final int DEFAULT_DISPATCHER_COLOR = EnumDyeColor.PURPLE.getMetadata();
    private int dispatcherSealColor = DEFAULT_DISPATCHER_COLOR;
    private final ArrayDeque<BlockPos> dispatcherBusyQueue = new ArrayDeque<>();

    /**
     * Логический ключ зеркала (номер кольца + слот).
     * Используем для жёсткой привязки к потребителям.
     */
    public static final class MirrorKey {
        public final int ring;
        public final int slot;

        public MirrorKey(int ring, int slot) {
            this.ring = ring;
            this.slot = slot;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MirrorKey)) return false;
            MirrorKey other = (MirrorKey) o;
            return ring == other.ring && slot == other.slot;
        }

        @Override
        public int hashCode() {
            return (ring * 31) ^ slot;
        }
    }

    /**
     * Жёсткая связка: ОДИН потребитель → ОДНО зеркало.
     * Ключ – позиция потребителя (терминал/реквестер и т.п.).
     */
    private final Map<BlockPos, MirrorKey> consumerMirrors = new HashMap<>();

    private final List<MirrorSlot> activeMirrors = new ArrayList<>(MAX_SLOTS);
    private long renderSeed = 0L;

    private static final class DispatcherStats {
        int golems;
        int busy;
    }

    private static final class TerminalDeliveryOrder {
        final BlockPos terminalPos;
        final BlockPos destPos;
        final int destSide;
        final ItemStack like;
        final int craftBatchId;
        final int deliveryGroupId;
        final boolean notifyTerminal;
        int amount;
        int delivered;
        int inFlight;
        int attempts;
        int lastRequestTick;
        int lastProgressTick;
        boolean terminalSendVisualPlayed;

        TerminalDeliveryOrder(BlockPos terminalPos, ItemStack like, int amount, int tick) {
            this(terminalPos, terminalPos, -1, like, amount, tick, 0, 0, true);
        }

        TerminalDeliveryOrder(@Nullable BlockPos terminalPos, BlockPos destPos, int destSide, ItemStack like,
                              int amount, int tick, int craftBatchId, boolean notifyTerminal) {
            this(terminalPos, destPos, destSide, like, amount, tick, craftBatchId, 0, notifyTerminal);
        }

        TerminalDeliveryOrder(@Nullable BlockPos terminalPos, BlockPos destPos, int destSide, ItemStack like,
                              int amount, int tick, int craftBatchId, int deliveryGroupId, boolean notifyTerminal) {
            this.terminalPos = terminalPos == null ? null : terminalPos.toImmutable();
            this.destPos = destPos.toImmutable();
            this.destSide = destSide;
            this.like = ResourceIdentity.stackForRequest(like, 1);
            this.craftBatchId = craftBatchId;
            this.deliveryGroupId = Math.max(0, deliveryGroupId);
            this.notifyTerminal = notifyTerminal;
            this.amount = Math.max(0, amount);
            this.lastRequestTick = tick - TERMINAL_ORDER_RETRY_TICKS;
            this.lastProgressTick = tick;
        }

        int remaining() {
            return Math.max(0, amount - delivered);
        }

        boolean matches(BlockPos dest, ItemStack stack) {
            return destPos.equals(dest) && matchesForDelivery(stack, like);
        }
    }

    private static final class TerminalCraftBatch {
        final int id;
        final BlockPos terminalPos;
        final BlockPos endpointPos;
        final BlockPos deliveryPos;
        final BlockPos outputDestPos;
        final int outputDestSide;
        final int parentBatchId;
        final ItemStack resultLike;
        final int patternSlot;
        final int requestedItems;
        final int crafts;
        final int expectedItems;
        boolean started;
        int deliveredItems;
        int lastProgressTick;
        int restartAttempts;
        int resourceStockBaseline = -1;

        TerminalCraftBatch(int id, BlockPos terminalPos, BlockPos endpointPos, BlockPos deliveryPos, int patternSlot,
                           ItemStack resultLike, int requestedItems, int crafts, int expectedItems) {
            this(id, terminalPos, endpointPos, deliveryPos, terminalPos, -1, 0, patternSlot,
                    resultLike, requestedItems, crafts, expectedItems);
        }

        TerminalCraftBatch(int id, BlockPos terminalPos, BlockPos endpointPos, BlockPos deliveryPos,
                           BlockPos outputDestPos, int outputDestSide, int parentBatchId, int patternSlot,
                           ItemStack resultLike, int requestedItems, int crafts, int expectedItems) {
            this.id = id;
            this.terminalPos = terminalPos.toImmutable();
            this.endpointPos = endpointPos.toImmutable();
            this.deliveryPos = deliveryPos.toImmutable();
            this.outputDestPos = outputDestPos.toImmutable();
            this.outputDestSide = outputDestSide;
            this.parentBatchId = Math.max(0, parentBatchId);
            this.resultLike = ResourceIdentity.stackForRequest(resultLike, 1);
            this.patternSlot = patternSlot;
            this.requestedItems = Math.max(1, requestedItems);
            this.crafts = Math.max(1, crafts);
            this.expectedItems = Math.max(1, expectedItems);
        }

        int remainingResult() {
            return Math.max(0, requestedItems - deliveredItems);
        }
    }

    private final List<TerminalDeliveryOrder> terminalDeliveryOrders = new ArrayList<>();
    private final List<TerminalCraftBatch> terminalCraftBatches = new ArrayList<>();
    private final Set<Integer> failedTerminalCraftBatches = new HashSet<>();
    private int nextTerminalCraftBatchId = 1;
    private int nextTerminalDeliveryGroupId = 1;
    private int deliveryVisualQueueTick = -1;
    private int deliveryVisualQueueDelay = 0;

    public int getMirrorUsed() {
        return boundTerminals.size() + boundRequesters.size();
    }

    public int getComputeUsed() {
        int used = boundRequesters.size() + boundRevisionPedestals.size();
        for (DispatcherStats stats : boundDispatchers.values()) {
            used += 1 + stats.golems;
        }
        return used;
    }

    public int getDispatcherSealColor() {
        return dispatcherSealColor & 15;
    }

    public int countBuffered(ItemKey key) {
        if (key == null || key == ItemKey.EMPTY) return 0;
        return countInBufferLike(key.toStack(1));
    }

    private boolean isUnlinking = false;
    private int staleSweepTicker = 0;
    private boolean suppressAcceptAnim = false;

    // ===================== Stability / Flux =====================
    private static final int STABILITY_CHECK_PERIOD = 250;      // каждые 40 тиков
    private static final int ORDO_STAB_PER_UNIT = 10;          // 1 Ordo = +10 стабильности
    private static final float FLUX_PER_10_INSTAB = 2f;      // за каждые 10 отриц. очков -> 0.5 флакса
    private static final int ORDO_DRAIN_RANGE = 16;            // радиус поиска источников эссенции (банки/зеркала)

    // для отладки/GUI (необязательно, но полезно)
    private int lastStabPlus = 0;
    private int lastInstabMinus = 0;
    private int lastOrdoUsed = 0;
    private float lastFluxMade = 0f;

    // ===================== Ordo buffer =====================
    private static final int ORDO_BUFFER_CAP = 100;     // вместимость буфера
    private static final int ORDO_PULL_PER_CHECK = 8;   // сколько максимум пытаемся подкачать за одну проверку (40 тиков)

    private int ordoBuffer = 0; // текущее кол-во Ordo в буфере


    // запускать проверку стабильности только когда была доставка
    private boolean stabilityCheckPending = false;
    private int stabilityCheckTick = -1; // чтобы не вызывать 10 раз за тик


    /* ===================== Голем-логистика ===================== */

    private static final int AUTO_SCAN_PERIOD_TICKS = 100;
    private static final int PROVIDER_SCAN_RADIUS = 16;
    private static final int PROVIDER_RESCAN_TICKS = 200;
    private static final int TERMINAL_ORDER_RETRY_TICKS = 20 * 12;
    private static final int TERMINAL_ORDER_MIN_REQUEST_GAP = 2;
    private static final int TERMINAL_ORDER_FAILED_REQUEST_GAP = 2;
    private static final int TERMINAL_ORDER_REQUEST_CHUNK = 64;

    /* ===================== Владелец / доступ ===================== */

    private final Set<String> allowedOwners = new HashSet<>();
    private @Nullable String ownerUuid;
    private final HashSet<UUID> dispatcherTickReservations = new HashSet<>();
    private int dispatcherReservationTick = -1;

    public @Nullable String getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(@Nullable String uuid) {
        if (!Objects.equals(ownerUuid, uuid)) {
            ownerUuid = uuid;
            markDirty();
        }
    }

    public void allowOwner(UUID uuid) {
        if (uuid != null && allowedOwners.add(uuid.toString())) markDirty();
    }

    private static final String TAG_REQ_REG = "registeredRequesters";

    /* ===================== Каталог (как в TC) ===================== */

    public TcLogisticsCompat.Page buildLogisticsPage(int startRow, String search) {
        return TcLogisticsCompat.build(this.world, this.ownerUuid, this.pos, startRow, search);
    }

    /* ===================== Корона зеркал ===================== */

    // Геометрия (должна совпадать с TESR)
    private static final double RADIUS = 1.6;
    private static final float BASE_Y = 1.35f;
    private static final float Y_STEP = 0.48f;
    private static final float RING_SHIFT_YAW = 30f;

    private static final int RINGS = 4;
    private static final int SLOTS_PER_RING = 6;
    private static final int MAX_SLOTS = RINGS * SLOTS_PER_RING;
    private static final int MIRROR_FOCUS_TICKS = 80;
    private static final int MIRROR_DELIVERY_MAX_TICKS = 150;
    private static final int MANAGER_TO_MIRROR_ANIM_TICKS = 33;
    private static final int MIRROR_TO_MIRROR_ANIM_TICKS = 45;
    private static final int REQUESTER_LOCAL_ANIM_TICKS = 33;
    private static final int CRAFT_BATCH_STALL_TICKS = 20 * 45;
    private static final int CRAFT_BATCH_MAX_RESTARTS = 2;

    private static final String TAG_MIRRORS = "mirrors";
    private static final String TAG_RENDER_SEED = "renderSeed";
    private static final String TAG_PEND_EJECTS = "pendEjects";
    private static final String TAG_DISPATCHERS = "boundDispatchers";
    private static final String TAG_TERMINAL_DELIVERY_ORDERS = "terminalDeliveryOrders";
    private static final String TAG_TERMINAL_CRAFT_BATCHES = "terminalCraftBatches";

    // ЕДИНАЯ занятость слотов
    private final boolean[][] slotBusy = new boolean[RINGS][SLOTS_PER_RING];

    // Активные зеркала (видимые и учитываемые лимитом)
    public static final class MirrorSlot {
        public final int ring, slot;
        public final long phase;

        // пока > worldTime — зеркало считается «в фокусе»
        public long focusUntil;
        public float renderYaw = Float.NaN;
        public float renderFocus = 0f;
        public boolean renderDelivering = false;
        public float idleSpin = 0f;
        public float lastRenderTime = Float.NaN;
        public float lastSpinStep = 0f;
        public long focusStarted = 0L;

        public MirrorSlot(int r, int s, long p) {
            ring = r;
            slot = s;
            phase = p;
            focusUntil = 0L;
        }

        public boolean focus(long now, int durationTicks) {
            if (focusUntil < now) {
                focusStarted = 0L;
            }
            if (focusStarted > 0L && now - focusStarted >= MIRROR_DELIVERY_MAX_TICKS) {
                focusUntil = now;
                focusStarted = 0L;
                return false;
            }

            long target = now + durationTicks;
            long remaining = focusUntil - now;
            if ((focusUntil < now || remaining < (durationTicks / 4)) && target > focusUntil) {
                focusUntil = target;
                if (focusStarted == 0L) {
                    focusStarted = now;
                }
                return true;
            }
            return false;
        }
    }

    private void markDeliveryHappened() {
        // отметим, что в этом тике была доставка
        stabilityCheckPending = true;
    }

    @Nullable
    private MirrorSlot findMirrorSlot(int ring, int slot) {
        for (MirrorSlot m : activeMirrors) {
            if (m.ring == ring && m.slot == slot) {
                return m;
            }
        }
        return null;
    }

    @Nullable
    private MirrorSlot findMirrorSlot(MirrorKey key) {
        if (key == null) return null;
        return findMirrorSlot(key.ring, key.slot);
    }

    private boolean isMirrorTakenByConsumer(int ring, int slot) {
        if (consumerMirrors.isEmpty()) return false;
        for (MirrorKey mk : consumerMirrors.values()) {
            if (mk.ring == ring && mk.slot == slot) {
                return true;
            }
        }
        return false;
    }

    /**
     * Выдать зеркалу конкретного потребителя, если ещё не выдано.
     * Гарантирует: одно зеркало не поделится между двумя потребителями.
     */
    @Nullable
    private MirrorKey getOrAssignMirrorForConsumer(BlockPos consumerPos) {
        if (consumerPos == null) return null;

        // уже есть – проверим, что зеркало ещё существует
        MirrorKey existing = consumerMirrors.get(consumerPos);
        if (existing != null && findMirrorSlot(existing) != null) {
            return existing;
        }

        // ищем свободное зеркало среди активных
        for (MirrorSlot m : activeMirrors) {
            if (!isMirrorTakenByConsumer(m.ring, m.slot)) {
                MirrorKey mk = new MirrorKey(m.ring, m.slot);
                consumerMirrors.put(consumerPos.toImmutable(), mk);
                return mk;
            }
        }

        // свободных нет – на всякий случай привяжем к первому активному
        if (!activeMirrors.isEmpty()) {
            MirrorSlot m = activeMirrors.get(0);
            MirrorKey mk = new MirrorKey(m.ring, m.slot);
            consumerMirrors.put(consumerPos.toImmutable(), mk);
            return mk;
        }

        return null;
    }

    /**
     * При отвязке/потере потребителя — освобождаем зеркало.
     */
    private void releaseMirrorForConsumer(BlockPos consumerPos) {
        if (consumerPos == null) return;
        consumerMirrors.remove(consumerPos);
    }
    /**
     * Когда удаляем само зеркало — сбрасываем всех потребителей, кто был к нему привязан.
     */
    private void releaseConsumersForMirror(int ring, int slot) {
        if (consumerMirrors.isEmpty()) return;
        java.util.Iterator<Map.Entry<BlockPos, MirrorKey>> it = consumerMirrors.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, MirrorKey> e = it.next();
            MirrorKey mk = e.getValue();
            if (mk.ring == ring && mk.slot == slot) {
                it.remove();
            }
        }
    }

    // Подвешенные к выбросу (занимают слот до дропа)
    private static final int EJECT_HOVER_TICKS = 40; // ~2с

    private static final class PendingEject {
        final int ring, slot;
        final long startTick;

        PendingEject(int r, int s, long start) {
            ring = r;
            slot = s;
            startTick = start;
        }
    }

    private final List<PendingEject> pendingEjects = new ArrayList<>();

    public java.util.List<int[]> getPendingEjectVisuals() {
        java.util.ArrayList<int[]> out = new java.util.ArrayList<>(pendingEjects.size());
        long now = (world != null ? world.getTotalWorldTime() : 0L);
        for (PendingEject p : pendingEjects) {
            int age = (int) Math.max(0L, now - p.startTick);
            out.add(new int[]{p.ring, p.slot, age, EJECT_HOVER_TICKS});
        }
        return out;
    }

    public int getEjectHoverTicks() {
        return EJECT_HOVER_TICKS;
    }

    // активные реальные зеркала (не «подвешенные»)
    private int getActiveMirrorCount() {
        return activeMirrors.size();
    }

    private int getFreeMirrorCount() {
        // свободное реальное зеркало = активные минус уже занятые привязками
        return Math.max(0, getActiveMirrorCount() - getMirrorUsed());
    }

    private boolean hasFreeMirror() {
        return getFreeMirrorCount() >= 1;
    }

    private boolean hasFreeComputeCell() {
        return (calcComputeCap - getComputeUsed()) >= 1;
    }

    private long nextPhaseSeed() {
        long base = (renderSeed == 0L ? (renderSeed = world.getTotalWorldTime() ^ pos.toLong()) : renderSeed);
        return base + world.rand.nextInt(10_000);
    }

    @Override
    public void registerControllers(AnimationData data) {
        data.addAnimationController(new AnimationController<>(
                this,
                "main",
                0, // delay
                this::predicate
        ));
    }

    private <E extends IAnimatable> PlayState predicate(AnimationEvent<E> event) {
        // Проигрываем нашу анимацию из JSON:
        event.getController().setAnimation(
                new AnimationBuilder().addAnimation("mirror_manager.animation", true)
        );
        return PlayState.CONTINUE;
    }

    @Override
    public AnimationFactory getFactory() {
        return factory;
    }

    @Nullable
    private int[] pickFreeSlot() {
        for (int r = 0; r < RINGS; r++)
            for (int s = 0; s < SLOTS_PER_RING; s++)
                if (!slotBusy[r][s]) return new int[]{r, s};
        return null;
    }

    private void occupySlot(int r, int s) {
        slotBusy[r][s] = true;
    }

    private void freeSlot(int r, int s) {
        slotBusy[r][s] = false;
    }

    private Vec3d slotWorldPos(int ring, int slot) {
        float base = slot * 60f;
        if (ring == 1 || ring == 3) base += RING_SHIFT_YAW;
        double ang = Math.toRadians(base);
        double px = pos.getX() + 0.5 + Math.cos(ang) * RADIUS;
        double pz = pos.getZ() + 0.5 + Math.sin(ang) * RADIUS;
        double py = pos.getY() + BASE_Y + ring * Y_STEP;
        return new Vec3d(px, py, pz);
    }

    public List<MirrorSlot> getRenderMirrors() {
        return new ArrayList<>(activeMirrors);
    }

    // Курьер принёс предметы для этого менеджера.
    // Возвращает остаток, который не влез.
    public ItemStack acceptProvisionResult(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack before = stack.copy();
        ItemStack rem = silentInsertToBuffer(stack);
        int accepted = before.getCount() - (rem.isEmpty() ? 0 : rem.getCount());
        if (accepted > 0) {
            onArrivedToBuffer(before, accepted);
        }
        return rem;
    }

    /**
     * Добавить активное зеркало, если есть свободный слот (для визуала/лимита).
     */
    public boolean addMirror() {
        if (activeMirrors.size() >= MAX_SLOTS) return false;
        int[] fs = pickFreeSlot();
        if (fs == null) return false;
        occupySlot(fs[0], fs[1]);
        activeMirrors.add(new MirrorSlot(fs[0], fs[1], nextPhaseSeed()));
        markDirtyAndSync();
        return true;
    }

    public ItemStack removeMirror() {
        if (activeMirrors.isEmpty()) return ItemStack.EMPTY;
        MirrorSlot m = activeMirrors.remove(activeMirrors.size() - 1);

        // все потребители, сидевшие на этом зеркале, теряют привязку
        releaseConsumersForMirror(m.ring, m.slot);

        freeSlot(m.ring, m.slot);
        markDirtyAndSync();
        pruneBindingsByRangeAndCapacity();
        return new ItemStack(thaumcraft.api.blocks.BlocksTC.mirror);
    }

    private boolean focusMirrorForConsumer(BlockPos consumerPos, int durationTicks) {
        if (world == null || world.isRemote || consumerPos == null) return false;
        if (!boundTerminals.contains(consumerPos) && !boundRequesters.contains(consumerPos)) return false;

        MirrorKey mk = getOrAssignMirrorForConsumer(consumerPos);
        if (mk == null) return false;

        MirrorSlot ms = findMirrorSlot(mk);
        if (ms != null && ms.focus(world.getTotalWorldTime(), durationTicks)) {
            markDirtyAndSync();
            return true;
        }
        return false;
    }

    private void focusMirrorForConsumer(BlockPos consumerPos) {
        focusMirrorForConsumer(consumerPos, MIRROR_FOCUS_TICKS);
    }

    private void focusMirrorKey(@Nullable MirrorKey key, int durationTicks) {
        if (world == null || world.isRemote || key == null) return;
        MirrorSlot ms = findMirrorSlot(key);
        if (ms != null && ms.focus(world.getTotalWorldTime(), durationTicks)) {
            markDirtyAndSync();
        }
    }

    @Nullable
    private BlockPos resolveConsumerForDestination(BlockPos destPos) {
        if (destPos == null) return null;
        if (boundTerminals.contains(destPos) || boundRequesters.contains(destPos)) {
            return destPos;
        }

        BlockPos up = destPos.up();
        if (boundTerminals.contains(up) || boundRequesters.contains(up)) {
            return up;
        }
        return null;
    }

    @Nullable
    private BlockPos resolveRequesterForAnimation(@Nullable BlockPos pos) {
        if (pos == null || world == null) return null;
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TilePatternRequester) return pos.toImmutable();

        BlockPos up = pos.up();
        TileEntity above = world.getTileEntity(up);
        if (above instanceof TilePatternRequester) return up.toImmutable();

        return null;
    }

    @Nullable
    private MirrorKey mirrorKeyForBoundConsumer(@Nullable BlockPos consumerPos) {
        if (consumerPos == null) return null;
        if (!boundTerminals.contains(consumerPos) && !boundRequesters.contains(consumerPos)) return null;
        return getOrAssignMirrorForConsumer(consumerPos);
    }

    private void playManagerToConsumerVisual(ItemStack stack, @Nullable BlockPos consumerPos, int delayTicks) {
        if (world == null || world.isRemote || stack == null || stack.isEmpty() || consumerPos == null) return;
        MirrorKey dst = mirrorKeyForBoundConsumer(consumerPos);
        if (dst == null) return;

        focusMirrorKey(dst, MIRROR_FOCUS_TICKS);
        S2CFlyAnim.dispatchManagerToMirror(world, pos, stack, dst.ring, dst.slot,
                MANAGER_TO_MIRROR_ANIM_TICKS, nextPhaseSeed(), Math.max(0, delayTicks));
    }

    private void playRequesterLocalVisual(@Nullable BlockPos requesterPos, ItemStack stack, int mode, int delayTicks) {
        if (world == null || world.isRemote || requesterPos == null || stack == null || stack.isEmpty()) return;
        S2CPatternRequesterAnim.dispatch(world, requesterPos, stack, mode,
                mode == S2CPatternRequesterAnim.MODE_BOUNCE
                        ? REQUESTER_LOCAL_ANIM_TICKS * 2
                        : REQUESTER_LOCAL_ANIM_TICKS,
                nextPhaseSeed(), Math.max(0, delayTicks));
    }

    private int reserveDeliveryVisualDelay(int durationTicks) {
        if (deliveryVisualQueueTick != tickCounter) {
            deliveryVisualQueueTick = tickCounter;
            deliveryVisualQueueDelay = 0;
        }
        int delay = deliveryVisualQueueDelay;
        deliveryVisualQueueDelay += Math.max(1, durationTicks);
        return delay;
    }

    private void playTerminalDeliveryVisual(TerminalDeliveryOrder order) {
        if (order == null || !order.notifyTerminal || order.terminalPos == null || order.terminalSendVisualPlayed) return;
        playManagerToConsumerVisual(order.like, order.terminalPos, reserveDeliveryVisualDelay(MANAGER_TO_MIRROR_ANIM_TICKS));
        order.terminalSendVisualPlayed = true;
    }

    private void playCraftInputDeliveryVisual(TerminalDeliveryOrder order, int deliveredNow) {
        if (order == null || deliveredNow <= 0) return;
        BlockPos requesterPos = resolveRequesterForAnimation(order.destPos);
        if (requesterPos == null) {
            playManagerToConsumerVisual(order.like, resolveConsumerForDestination(order.destPos), 0);
            return;
        }

        playManagerToConsumerVisual(order.like, requesterPos, 0);
        playRequesterLocalVisual(requesterPos, order.like,
                S2CPatternRequesterAnim.MODE_MIRROR_TO_BLOCK,
                MANAGER_TO_MIRROR_ANIM_TICKS);
    }

    private void playCraftOutputTransferVisual(TerminalCraftBatch batch, int movedTotal) {
        if (batch == null || movedTotal <= 0) return;

        BlockPos sourceRequester = resolveRequesterForAnimation(batch.endpointPos);
        if (sourceRequester == null) sourceRequester = resolveRequesterForAnimation(batch.deliveryPos);
        BlockPos destRequester = resolveRequesterForAnimation(batch.outputDestPos);
        BlockPos destConsumer = destRequester != null ? destRequester : resolveConsumerForDestination(batch.outputDestPos);

        if (sourceRequester != null && sourceRequester.equals(destRequester)) {
            playRequesterLocalVisual(sourceRequester, batch.resultLike, S2CPatternRequesterAnim.MODE_BOUNCE, 0);
            return;
        }

        if (sourceRequester != null) {
            playRequesterLocalVisual(sourceRequester, batch.resultLike, S2CPatternRequesterAnim.MODE_BLOCK_TO_MIRROR, 0);
        }

        MirrorKey src = mirrorKeyForBoundConsumer(sourceRequester);
        MirrorKey dst = mirrorKeyForBoundConsumer(destConsumer);
        int crownDelay = sourceRequester == null ? 0 : REQUESTER_LOCAL_ANIM_TICKS;

        if (src != null && dst != null) {
            focusMirrorKey(src, MIRROR_FOCUS_TICKS);
            focusMirrorKey(dst, MIRROR_FOCUS_TICKS);
            S2CFlyAnim.dispatchMirrorToMirror(world, pos, batch.resultLike,
                    src.ring, src.slot, dst.ring, dst.slot,
                    MIRROR_TO_MIRROR_ANIM_TICKS, nextPhaseSeed(), crownDelay);
        } else if (dst != null) {
            playManagerToConsumerVisual(batch.resultLike, destConsumer, crownDelay);
        }

        if (destRequester != null) {
            playRequesterLocalVisual(destRequester, batch.resultLike,
                    S2CPatternRequesterAnim.MODE_MIRROR_TO_BLOCK,
                    crownDelay + MIRROR_TO_MIRROR_ANIM_TICKS);
        }
    }

    private boolean isBoundOrderRecipient(BlockPos consumerPos) {
        if (consumerPos == null) return false;
        return boundTerminals.contains(consumerPos) || boundRevisionPedestals.contains(consumerPos);
    }

    private void markDirtyAndSync() {
        markDirty();
        if (!world.isRemote) {
            net.minecraft.block.state.IBlockState st = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, st, st, 3);
        }
    }

    private final java.util.Set<BlockPos> lastActiveStabs = new java.util.HashSet<>();
    private final java.util.Set<BlockPos> lastActiveCores = new java.util.HashSet<>();

    /* === Клиентская эфемерная анимация прилёта === */

    public static final class FlyingItem {
        public ItemStack stack = ItemStack.EMPTY;
        public int ring, slot;
        public int srcRing = -1, srcSlot = -1;
        public int mode = S2CFlyAnim.MODE_MANAGER_TO_MIRROR;
        public long start;
        public int duration;
        public long seed;

        public FlyingItem(ItemStack s, int r, int sl, long st, int dur, long sd) {
            this.stack = (s == null ? ItemStack.EMPTY : s.copy());
            if (!this.stack.isEmpty()) this.stack.setCount(1);
            this.ring = r;
            this.slot = sl;
            this.start = st;
            this.duration = Math.max(5, dur);
            this.seed = sd;
        }
    }

    private final List<FlyingItem> flying = new ArrayList<>();

    public List<FlyingItem> getFlying() {
        return new ArrayList<>(flying);
    }

    public void clientAddFlying(ItemStack stack, int ring, int slot, int duration, long seed) {
        clientAddFlying(stack, ring, slot, -1, -1, S2CFlyAnim.MODE_MANAGER_TO_MIRROR, duration, seed);
    }

    public void clientAddFlying(ItemStack stack, int ring, int slot, int srcRing, int srcSlot, int mode, int duration, long seed) {
        clientAddFlying(stack, ring, slot, srcRing, srcSlot, mode, duration, seed, 0);
    }

    public void clientAddFlying(ItemStack stack, int ring, int slot, int srcRing, int srcSlot, int mode, int duration, long seed, int delay) {
        if (world == null || !world.isRemote) return;
        long now = world.getTotalWorldTime() + Math.max(0, delay);
        FlyingItem item = new FlyingItem(stack, ring, slot, now, duration, seed);
        item.srcRing = srcRing;
        item.srcSlot = srcSlot;
        item.mode = mode;
        flying.add(item);
    }

    public void clientCullFlying() {
        if (world == null || !world.isRemote) return;
        long now = world.getTotalWorldTime();
        flying.removeIf(f -> (now - f.start) > (long) f.duration + 2);
    }

    /* === «Лишние» зеркала: 2с висим и падаем === */

    private static boolean isMirrorItem(ItemStack s) {
        return s != null && !s.isEmpty()
                && s.getItem() == net.minecraft.item.Item.getItemFromBlock(thaumcraft.api.blocks.BlocksTC.mirror);
    }

    /**
     * Из буфера поднимаем зеркала до лимита активных; сверх — подвешиваем и дропаем.
     */
    private void processMirrorItemsInBuffer() {
        if (world == null || world.isRemote) return;

        boolean removedExtras = false;
        while (activeMirrors.size() > calcMirrorCap) {
            MirrorSlot m = activeMirrors.remove(activeMirrors.size() - 1);

            // тоже снимаем всех потребителей с этого зеркала
            releaseConsumersForMirror(m.ring, m.slot);

            pendingEjects.add(new PendingEject(m.ring, m.slot, world.getTotalWorldTime()));
            removedExtras = true;
        }
        if (removedExtras) {
            pruneBindingsByRangeAndCapacity();
            markDirtyAndSync();
        }

    }

    public void rescanAndRebalanceNow() {
        if (world == null || world.isRemote) return;
        rescanUpgradesAndRevalidate();       // пересчёт range/mirrorCap/computeCap + визуалы
        pruneBindingsByRangeAndCapacity();   // и сразу ужать привязки по новым капам/реальным зеркалам
        markDirtyAndSync();
    }

    public static void touchManagerNearUpgrade(net.minecraft.world.World w, BlockPos upgradePos) {
        if (w == null || w.isRemote) return;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dy = 1; dy <= 3; dy++)
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++) {
                    m.setPos(upgradePos.getX() + dx, upgradePos.getY() + dy, upgradePos.getZ() + dz);
                    if (w.getBlockState(m).getBlock() == therealpant.thaumicattempts.init.TABlocks.MIRROR_MANAGER) {
                        TileEntity te = w.getTileEntity(m);
                        if (te instanceof TileMirrorManager) {
                            ((TileMirrorManager) te).rescanAndRebalanceNow();
                        }
                        return;
                    }
                }
    }

    private void unlinkRequesterSideEffects(BlockPos requesterPos) {
        if (world == null || world.isRemote || requesterPos == null) return;
        TileEntity te = world.getTileEntity(requesterPos);
        if (te instanceof TilePatternRequester) {
            ((TilePatternRequester) te).clearManagerPosFromManager(this.pos); // «тихо»
        } else if (te instanceof TileInfusionRequester) {
            ((TileInfusionRequester) te).setManagerPos(null);
        }
        requesters.remove(requesterPos); // важно для каталога крафта
    }

    private void unlinkTerminalSideEffects(BlockPos terminalPos) {
        if (world == null || world.isRemote || terminalPos == null) return;
        TileEntity te = world.getTileEntity(terminalPos);
        if (te instanceof TileOrderTerminal) {
            ((TileOrderTerminal) te).clearManagerPosFromManager(this.pos); // «тихо»
            cancelAllForDestination(terminalPos); // подчистить очереди
        } else if (te instanceof TileCraftPlanner) {
            TileCraftPlanner planner = (TileCraftPlanner) te;
            if (this.pos.equals(planner.getManagerPos())) {
                planner.setManagerPos(null);
            }
        } else if (te instanceof TileResourceRequester) {
            TileResourceRequester requester = (TileResourceRequester) te;
            requester.setManagerPos(null);
            requester.cancelActiveJob();
        }
    }

    private void unlinkDispatcherSideEffects(BlockPos dispatcherPos) {
        if (world == null || world.isRemote || dispatcherPos == null) return;
        TileEntity te = world.getTileEntity(dispatcherPos);
        if (te instanceof TileGolemDispatcher) {
            ((TileGolemDispatcher) te).clearManagerPosFromManager(this.pos);
        }
    }

    private void unlinkRevisionPedestalSideEffects(BlockPos pedestalPos) {
        if (world == null || world.isRemote || pedestalPos == null) return;
        TileEntity te = world.getTileEntity(pedestalPos);
        if (te instanceof TileRevisionPiedestal) {
            ((TileRevisionPiedestal) te).clearManagerPosFromManager(this.pos);
        }
    }

    /**
     * Дропнуть подвешенные, освободив слот.
     */
    private void tickMirrorEjects() {
        if (world == null || world.isRemote) return;
        if (pendingEjects.isEmpty()) return;

        long now = world.getTotalWorldTime();
        for (int i = pendingEjects.size() - 1; i >= 0; i--) {
            PendingEject p = pendingEjects.get(i);
            if (now - p.startTick < EJECT_HOVER_TICKS) continue;

            Vec3d xyz = slotWorldPos(p.ring, p.slot);
            EntityItem drop = new EntityItem(world, xyz.x, xyz.y, xyz.z,
                    new ItemStack(thaumcraft.api.blocks.BlocksTC.mirror));
            drop.setDefaultPickupDelay();
            world.spawnEntity(drop);

            freeSlot(p.ring, p.slot);
            pendingEjects.remove(i);
            markDirtyAndSync();
        }
    }

    /* ===================== Буфер и анимация прилёта ===================== */

    private final ItemStackHandler buffer = new ItemStackHandler(18) {
        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return super.insertItem(slot, stack, simulate);
        }

        @Override
        protected void onContentsChanged(int slot) {
            markDirty();
        }
    };

    private static final EnumFacing[] FACES = new EnumFacing[]{
            EnumFacing.UP, EnumFacing.DOWN, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST
    };

    private static Iterable<BlockPos> faceNeighbors(BlockPos p) {
        java.util.ArrayList<BlockPos> out = new java.util.ArrayList<>(6);
        for (EnumFacing f : FACES) out.add(p.offset(f));
        return out;
    }

    private ItemStack silentInsertToBuffer(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        try {
            suppressAcceptAnim = true;
            return ItemHandlerHelper.insertItem(buffer, stack, false);
        } finally {
            suppressAcceptAnim = false;
        }
    }

    private int simulateAccept(IItemHandler dest, ItemStack like, int upTo) {
        if (dest == null || like == null || like.isEmpty() || upTo <= 0) return 0;

        int left = upTo;
        for (int i = 0; i < dest.getSlots() && left > 0; i++) {
            ItemStack batch = like.copy();
            batch.setCount(Math.min(left, like.getMaxStackSize()));
            ItemStack rem = dest.insertItem(i, batch, true); // simulate
            int accepted = batch.getCount() - (rem.isEmpty() ? 0 : rem.getCount());
            if (accepted > 0) left -= accepted;
        }
        return upTo - left;
    }

    /**
     * Внешняя обёртка — подхватывает приход и продвигает текущий батч.
     */
    private final IItemHandler exposedBuffer = new IItemHandler() {
        @Override
        public int getSlots() {
            return buffer.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return buffer.getStackInSlot(slot);
        }

        @Override
        public int getSlotLimit(int slot) {
            return buffer.getSlotLimit(slot);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return buffer.extractItem(slot, amount, simulate);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) return stack;
            ItemStack before = stack.copy();
            ItemStack rem = buffer.insertItem(slot, stack, simulate);
            if (!simulate) {
                int accepted = before.getCount() - (rem.isEmpty() ? 0 : rem.getCount());
                if (accepted > 0) {
                    onArrivedToBuffer(before, accepted);
                }
            }
            return rem;
        }
    };

    @Override
    public void onLoad() {
        super.onLoad();
        if (world != null && !world.isRemote) {
            rescanUpgradesAndRevalidate();
            pruneBindingsByRangeAndCapacity();

            // NEW: синхронизировать реестр с boundRequesters на случай «битых» записей
            boolean changed = false;
            for (Iterator<BlockPos> it = requesters.iterator(); it.hasNext(); ) {
                BlockPos rp = it.next();
                TileEntity te = world.getTileEntity(rp);
                if (!(te instanceof TilePatternRequester) && !(te instanceof TileInfusionRequester)) {
                    it.remove();
                    changed = true;
                }
            }
            for (BlockPos rp : boundRequesters) {
                TileEntity te = world.getTileEntity(rp);
                if ((te instanceof TilePatternRequester || te instanceof TileInfusionRequester) && !requesters.contains(rp)) {
                    requesters.add(rp.toImmutable());
                    changed = true;
                }
            }
            if (changed) markDirtyAndSync();
            propagateDispatcherColors();
        }
    }

    /* ===================== Прогресс/в пути ===================== */


    private void onArrivedToBuffer(ItemStack stack, int count) {
        if (stack.isEmpty() || count <= 0) return;
        noteTerminalDeliveryArrival(stack, count);
        markDeliveryHappened();
        processTerminalDeliveryOrders();
    }

    private void intakeNearbyItems() {
        if (world == null || world.isRemote) return;

        AxisAlignedBB box = new AxisAlignedBB(
                pos.getX() - 0.001, pos.getY() - 0.001, pos.getZ() - 0.001,
                pos.getX() + 1.001, pos.getY() + 2.001, pos.getZ() + 1.001
        );

        List<EntityItem> ents = world.getEntitiesWithinAABB(EntityItem.class, box);
        if (ents.isEmpty()) return;

        for (EntityItem ei : ents) {
            if (ei == null || ei.isDead) continue;
            ItemStack drop = ei.getItem();
            if (drop == null || drop.isEmpty()) continue;

            boolean haveRoom = !ItemHandlerHelper.insertItem(buffer, drop.copy(), true).equals(drop);
            if (!haveRoom) continue;

            ItemStack before = drop.copy();
            ItemStack rem = ItemHandlerHelper.insertItem(buffer, drop, false);
            int accepted = before.getCount() - (rem.isEmpty() ? 0 : rem.getCount());
            if (accepted > 0) {
                onArrivedToBuffer(before, accepted);
                if (rem.isEmpty()) ei.setDead();
                else ei.setItem(rem);

            }
        }
    }

    public boolean tryBindTerminal(BlockPos pos) {
        if (pos == null) return false;
        if (pos.distanceSq(getPos()) > (double) (calcRange * calcRange)) return false;
        if (!hasFreeMirror()) return false;

        // строгое зеркало для этого терминала
        MirrorKey mk = getOrAssignMirrorForConsumer(pos);
        if (mk == null) return false;

        boundTerminals.add(pos.toImmutable());
        markDirty();
        return true;
    }

    public boolean tryBindRequester(BlockPos pos) {
        if (pos == null) return false;
        if (pos.distanceSq(getPos()) > (double) (calcRange * calcRange)) return false;
        if (!hasFreeMirror()) return false;
        if (!hasFreeComputeCell()) return false;

        MirrorKey mk = getOrAssignMirrorForConsumer(pos);
        if (mk == null) return false;

        boundRequesters.add(pos.toImmutable());
        markDirty();
        return true;
    }

    public boolean tryBindRevisionPedestal(BlockPos pos) {
        if (pos == null) return false;
        if (pos.distanceSq(getPos()) > (double) (calcRange * calcRange)) return false;
        BlockPos key = pos.toImmutable();
        if (boundRevisionPedestals.contains(key)) return true;
        if (!hasFreeComputeCell()) return false;
        boundRevisionPedestals.add(key);
        markDirty();
        return true;
    }

    public boolean tryBindDispatcher(BlockPos pos, int golemCount) {
        if (pos == null) return false;
        if (pos.distanceSq(getPos()) > (double) (calcRange * calcRange)) return false;
        BlockPos key = pos.toImmutable();
        if (boundDispatchers.containsKey(key)) return true;

        int need = 1 + Math.max(0, golemCount);
        if (getComputeUsed() + need > calcComputeCap) return false;

        DispatcherStats stats = new DispatcherStats();
        stats.golems = Math.max(0, golemCount);
        stats.busy = 0;
        boundDispatchers.put(key, stats);
        markDirtyAndSync();
        setDispatcherBusyCount(key, 0);
        propagateDispatcherColor(key);
        return true;
    }

    public void unregisterDispatcher(BlockPos pos) {
        if (pos == null) return;
        BlockPos key = pos.toImmutable();
        DispatcherStats stats = boundDispatchers.remove(key);
        if (stats != null) {
            dispatcherBusyQueue.removeIf(bp -> bp.equals(key));
            setDispatcherBusyCount(key, 0);
            if (world != null && !world.isRemote) {
                TileEntity te = world.getTileEntity(key);
                if (te instanceof TileGolemDispatcher) {
                    ((TileGolemDispatcher) te).clearManagerPosFromManager(this.pos);
                }
            }
            markDirtyAndSync();
        }
    }

    // true, если к менеджеру привязан хотя бы один живой диспетчер с големами
    public boolean hasActiveDispatchers() {
        if (world == null || world.isRemote) return false;
        for (BlockPos pos : boundDispatchers.keySet()) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileGolemDispatcher && !te.isInvalid()) {
                if (((TileGolemDispatcher) te).hasBoundGolems()) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean onDispatcherSetGolemCount(BlockPos pos, int newCount) {
        if (pos == null) return false;
        BlockPos key = pos.toImmutable();
        DispatcherStats stats = boundDispatchers.get(key);
        if (stats == null) return false;

        int normalized = Math.max(0, newCount);
        int delta = normalized - stats.golems;
        int current = getComputeUsed();
        if (delta > 0 && current + delta > calcComputeCap) return false;

        stats.golems = normalized;
        if (stats.busy > stats.golems) {
            trimDispatcherAssignments(key);
        }
        markDirtyAndSync();
        setDispatcherBusyCount(key, stats.busy);
        return true;
    }

    private void trimDispatcherAssignments(BlockPos pos) {
        DispatcherStats stats = boundDispatchers.get(pos);
        if (stats == null) return;
        if (stats.busy <= stats.golems) return;
        int toRemove = stats.busy - stats.golems;
        stats.busy = stats.golems;
        if (toRemove <= 0) return;
        Iterator<BlockPos> it = dispatcherBusyQueue.iterator();
        while (it.hasNext() && toRemove > 0) {
            if (it.next().equals(pos)) {
                it.remove();
                toRemove--;
            }
        }
    }

    public int getProvisionColor() {
        return boundDispatchers.isEmpty() ? 0 : (dispatcherSealColor & 15);
    }

    /**
     * Уже есть в твоём коде, но зафиксирую сигнатуру:
     * true, если указанный голем привязан к любому из связанных GolemDispatcher.
     */
    public boolean isDispatcherLinkedGolem(UUID golemId) {
        if (golemId == null) return false;
        if (world == null || world.isRemote) return false;
        if (boundDispatchers.isEmpty()) return false;

        for (BlockPos dp : boundDispatchers.keySet()) {
            TileEntity te = world.getTileEntity(dp);
            if (te instanceof TileGolemDispatcher) {
                if (((TileGolemDispatcher) te).isGolemBound(golemId)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void propagateDispatcherColor(BlockPos pos) {
        if (world == null || world.isRemote || pos == null) return;
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileGolemDispatcher) {
            TileGolemDispatcher gd = (TileGolemDispatcher) te;
            int want = getDispatcherSealColor();
            if (gd.getSealColor() != want) {
                gd.setSealColor(want);
            }
        }
    }

    private void propagateDispatcherColors() {
        if (world == null || world.isRemote) return;
        for (BlockPos dp : boundDispatchers.keySet()) {
            propagateDispatcherColor(dp);
        }
    }

    private void setDispatcherBusyCount(BlockPos pos, int busy) {
        if (world == null) return;
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileGolemDispatcher) {
            ((TileGolemDispatcher) te).setBusyFromManager(busy);
        }
    }

    /* ===================== Поставщики (автоскан) ===================== */

    public static final class TrackedInv {
        public final BlockPos pos;      // позиция ТАЙЛА с инвентарём
        public final int side;          // грань, на которой висит печать (-1 если не важно)
        public final SealProvide seal;  // сама печать (для matchesFilters)

        public TrackedInv(BlockPos pos, int side, SealProvide seal) {
            this.pos = pos;
            this.side = side;
            this.seal = seal;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof TrackedInv)) return false;
            TrackedInv t = (TrackedInv) o;
            return side == t.side && Objects.equals(pos, t.pos);
        }

        @Override
        public int hashCode() {
            return Objects.hash(pos, side);
        }
    }

    // Только склады под печатями PROVIDE (без соседей)
    private final LinkedHashSet<TrackedInv> provideSet = new LinkedHashSet<>();

    public java.util.Set<TrackedInv> getProvideSet() {
        return java.util.Collections.unmodifiableSet(provideSet);
    }

    private int tickCounter = 0;
    private int lastProviderScan = -9999;

    private void rescanProvidersRadius() {
        rebuildProvideSetFromSeals();
    }

    private void rescanProvidersAuto() {
        rebuildProvideSetFromSeals();
    }

    private void tickProviderScan() {
        // no-op: вся актуализация через rebuildProvideSetFromSeals()
    }

    /**
     * Пересобрать список известных складов:
     *  - только SealProvide
     *  - только владелец ownerUuid (если задан)
     *  - каждый (pos, side) максимум один раз
     *  - без соседних инвентарей
     */
    private void rebuildProvideSetFromSeals() {
        provideSet.clear();
        if (world == null || world.isRemote) return;

        final int range = calcRange;

        for (SealEntity se : SealHandler.getSealsInRange(world, pos, range)) {
            if (!(se.getSeal() instanceof SealProvide)) continue;

            // фильтр по владельцу
            if (ownerUuid != null && !ownerUuid.equals(se.getOwner())) continue;

            BlockPos storagePos = se.getSealPos().pos;
            EnumFacing face = se.getSealPos().face;

            IItemHandler ih = getSealExactInventory(world, storagePos, face);
            if (ih == null) continue;

            int sideIndex = (face != null ? face.getIndex() : -1);
            provideSet.add(new TrackedInv(storagePos.toImmutable(), sideIndex, (SealProvide) se.getSeal()));
        }
    }

    public void dropContents() {
        if (world == null || world.isRemote) return;
        ItemStack mirror = new ItemStack(thaumcraft.api.blocks.BlocksTC.mirror);
        if (!mirror.isEmpty()) {
            for (int i = 0; i < pendingEjects.size(); i++) {
                net.minecraft.inventory.InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), mirror.copy());
            }
            for (int i = 0; i < activeMirrors.size(); i++) {
                net.minecraft.inventory.InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), mirror.copy());
            }
            pendingEjects.clear();
            activeMirrors.clear();
        }
        for (int i = 0; i < buffer.getSlots(); i++) {
            ItemStack stack = buffer.getStackInSlot(i);
            if (!stack.isEmpty()) {
                net.minecraft.inventory.InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), stack.copy());
                buffer.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }

    /* ===================== Жизненный цикл ===================== */

    private int rescanCooldown = 0;

    @Override
    public void update() {
        if (world == null) return;
        if (world.isRemote) return;

        if (--rescanCooldown <= 0) {
            rescanUpgradesAndRevalidate();
            rescanCooldown = 20;
        }

        // ПЕРИОДИЧЕСКИ: выкидываем мертвые привязки (сломанные тайлы/другой менеджер/нет capability)
        if ((staleSweepTicker++ & 31) == 0) { // раз в 32 тика
            pruneStaleBindings(); // <— НОВЫЙ метод ниже
        }

        tickCounter++;

        intakeNearbyItems();
        processTerminalDeliveryOrders();
        processMirrorItemsInBuffer();
        tickMirrorEjects();

        // Периодически актуализируем список складов под SealProvide
        if ((tickCounter % PROVIDER_RESCAN_TICKS) == 0) {
            rebuildProvideSetFromSeals();
        }

        if (stabilityCheckPending && stabilityCheckTick != tickCounter) {
            stabilityCheckTick = tickCounter;
            stabilityCheckPending = false;

            // можно подкачать буфер "впрок" именно во время доставки
            pullOrdoToBuffer(ORDO_PULL_PER_CHECK);

            // и уже затем расчёт стабильности/ordo/флакса
            tickStabilityAndFlux();
        }

        rebuildProvideSetFromSeals();
    }

    private void pruneStaleBindings() {
        if (world == null || world.isRemote) return;

        List<BlockPos> deadReq = new ArrayList<>();
        List<BlockPos> deadTerm = new ArrayList<>();
        List<BlockPos> deadRev = new ArrayList<>();

        // requesters: тайла может не быть, может быть, но уже не наш менеджер
        for (BlockPos rp : boundRequesters) {
            TileEntity te = world.getTileEntity(rp);
            if (te instanceof TilePatternRequester) {
                BlockPos mp = ((TilePatternRequester) te).getManagerPos();
                if (mp == null || !mp.equals(this.pos)) {
                    deadReq.add(rp);
                }
            } else if (te instanceof TileInfusionRequester) {
                BlockPos mp = ((TileInfusionRequester) te).getManagerPos();
                if (mp == null || !mp.equals(this.pos)) {
                    deadReq.add(rp);
                }
            } else {
                deadReq.add(rp);
            }
        }

        // terminals: аналогично
        for (BlockPos tp : boundTerminals) {
            TileEntity te = world.getTileEntity(tp);
            if (te instanceof TileOrderTerminal) {
                BlockPos mp = ((TileOrderTerminal) te).getManagerPos();
                if (mp == null || !mp.equals(this.pos)) {
                    deadTerm.add(tp);
                }
            } else if (te instanceof TileCraftPlanner) {
                BlockPos mp = ((TileCraftPlanner) te).getManagerPos();
                if (mp == null || !mp.equals(this.pos)) {
                    deadTerm.add(tp);
                }
            } else if (te instanceof TileResourceRequester) {
                BlockPos mp = ((TileResourceRequester) te).getManagerPos();
                if (mp == null || !mp.equals(this.pos)) {
                    deadTerm.add(tp);
                }
            } else {
                deadTerm.add(tp);
            }
        }

        for (BlockPos rp : boundRevisionPedestals) {
            TileEntity te = world.getTileEntity(rp);
            if (!(te instanceof TileRevisionPiedestal)) {
                deadRev.add(rp);
                continue;
            }
            BlockPos mp = ((TileRevisionPiedestal) te).getManagerPos();
            if (mp == null || !mp.equals(this.pos)) {
                deadRev.add(rp);
            }
        }

        if (deadReq.isEmpty() && deadTerm.isEmpty() && deadRev.isEmpty()) return;

        // Снимаем из наборов
        boundRequesters.removeAll(deadReq);
        boundTerminals.removeAll(deadTerm);
        boundRevisionPedestals.removeAll(deadRev);

        // Тихо оповещаем живые тайлы (если тайла нет — просто пропустим)
        isUnlinking = true;
        try {
            for (BlockPos rp : deadReq) {
                TileEntity te = world.getTileEntity(rp);
                if (te instanceof TilePatternRequester) {
                    ((TilePatternRequester) te).clearManagerPosFromManager(this.pos);
                }
                // убрать из внутреннего реестра крафтеров
                requesters.remove(rp);
            }
            for (BlockPos tp : deadTerm) {
                TileEntity te = world.getTileEntity(tp);
                if (te instanceof TileOrderTerminal) {
                    ((TileOrderTerminal) te).clearManagerPosFromManager(this.pos);
                    cancelAllForDestination(tp);
                }
            }
            for (BlockPos rp : deadRev) {
                TileEntity te = world.getTileEntity(rp);
                if (te instanceof TileRevisionPiedestal) {
                    ((TileRevisionPiedestal) te).clearManagerPosFromManager(this.pos);
                }
            }
        } finally {
            isUnlinking = false;
        }

        markDirtyAndSync();
    }

    @Nullable
    public UUID findFreeDispatcherGolemUUID(@Nullable java.util.concurrent.ConcurrentHashMap<Integer, Task> tasks) {
        return findFreeDispatcherGolemUUID(tasks, Collections.<UUID>emptySet());
    }

    @Nullable
    public UUID reserveFreeDispatcherGolemUUID(@Nullable java.util.concurrent.ConcurrentHashMap<Integer, Task> tasks) {
        if (dispatcherReservationTick != tickCounter) {
            dispatcherReservationTick = tickCounter;
            dispatcherTickReservations.clear();
        }

        UUID id = findFreeDispatcherGolemUUID(tasks, dispatcherTickReservations);
        if (id != null) dispatcherTickReservations.add(id);
        return id;
    }

    @Nullable
    private UUID findFreeDispatcherGolemUUID(@Nullable java.util.concurrent.ConcurrentHashMap<Integer, Task> tasks,
                                             @Nullable Set<UUID> reservedThisTick) {
        if (world == null || world.isRemote) return null;
        if (boundDispatchers == null || boundDispatchers.isEmpty()) return null;

        // 1) Собираем всех курьеров, привязанных через диспетчеры
        java.util.ArrayList<UUID> all = new java.util.ArrayList<>();
        for (java.util.Map.Entry<BlockPos, DispatcherStats> e : boundDispatchers.entrySet()) {
            TileEntity te = world.getTileEntity(e.getKey());
            if (!(te instanceof TileGolemDispatcher)) continue;
            TileGolemDispatcher gd = (TileGolemDispatcher) te;
            if (!gd.hasBoundGolems()) continue;
            for (UUID id : gd.getBoundGolemsSnapshot()) {
                if (id != null) {
                    all.add(id);
                }
            }
        }
        if (all.isEmpty()) return null;

        // 2) Собираем реально занятых по TaskHandler.tasks
        java.util.Set<UUID> busy = new java.util.HashSet<>();
        if (tasks != null) {
            for (Task t : tasks.values()) {
                if (t == null) continue;
                if (t.isCompleted() || t.isSuspended()) continue;
                UUID gid = t.getGolemUUID();
                if (gid != null) {
                    busy.add(gid);
                }
            }
        }

        // 3) Round-robin по списку курьеров: первый свободный, не в busy
        // Чтобы был стабильный порядок, можно отсортировать, но как правило и так ок.
        // Если хочешь детерминизм - добавь Collections.sort(all) по UUID.
        if (reservedThisTick != null && !reservedThisTick.isEmpty()) {
            busy.addAll(reservedThisTick);
        }

        int size = all.size();
        if (size == 0) return null;

        // Храним указатель, чтобы следующий вызов не начинал с нуля
        if (dispatcherRoundRobinIndex >= size) {
            dispatcherRoundRobinIndex = 0;
        }

        for (int i = 0; i < size; i++) {
            int idx = (dispatcherRoundRobinIndex + i) % size;
            UUID candidate = all.get(idx);
            if (candidate == null) continue;
            if (busy.contains(candidate)) continue;

            // Проверим, что голем вообще живой
            net.minecraft.entity.Entity ent =
                    ((net.minecraft.world.WorldServer) world).getEntityFromUuid(candidate);
            if (!(ent instanceof thaumcraft.common.golems.EntityThaumcraftGolem)) continue;
            if (ent.isDead) continue;

            // Нашли свободного курьера
            dispatcherRoundRobinIndex = (idx + 1) % size;
            return candidate;
        }

        // Все заняты
        return null;
    }


    private void rescanUpgradesAndRevalidate() {
        if (world == null || world.isRemote) return;

        // 5×5×3 область под менеджером
        BlockPos p = getPos();
        int minX = p.getX() - 2, maxX = p.getX() + 2;
        int minZ = p.getZ() - 2, maxZ = p.getZ() + 2;
        int minY = p.getY() - 3, maxY = p.getY() - 1;

        java.util.Set<BlockPos> stabsAll = new java.util.HashSet<>();
        java.util.Set<BlockPos> coresAll = new java.util.HashSet<>();

        for (int y = minY; y <= maxY; y++)
            for (int x = minX; x <= maxX; x++)
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos q = new BlockPos(x, y, z);
                    net.minecraft.block.Block b = world.getBlockState(q).getBlock();
                    if (b == ModBlocksItems.MIRROR_STABILIZER) stabsAll.add(q);
                    else if (b == ModBlocksItems.MATH_CORE) coresAll.add(q);
                }

        // --- Волновая активация от блока прямо под менеджером ---
        java.util.Set<BlockPos> stabsActive = new java.util.HashSet<>();
        java.util.Set<BlockPos> coresActive = new java.util.HashSet<>();

        BlockPos seed = p.down(); // «блок, верхней гранью касающийся менеджера» — ровно под ним
        net.minecraft.block.Block seedBlock = world.getBlockState(seed).getBlock();
        boolean seedIsStab = seedBlock == ModBlocksItems.MIRROR_STABILIZER && stabsAll.contains(seed);
        boolean seedIsCore = seedBlock == ModBlocksItems.MATH_CORE && coresAll.contains(seed);


        if (seedIsStab) stabsActive.add(seed);
        if (seedIsCore) coresActive.add(seed);


        boolean changed = true;
        while (changed) {
            changed = false;

            // от активных стабов пробуем активировать любые КОРЫ, смежные по грани
            for (BlockPos s : new java.util.ArrayList<>(stabsActive)) {
                for (BlockPos nb : faceNeighbors(s)) {
                    if (!coresAll.contains(nb) || coresActive.contains(nb)) continue;
                    coresActive.add(nb);
                    changed = true;
                }
            }

            // от активных коров активируем СТАБЫ, смежные по грани
            for (BlockPos c : new java.util.ArrayList<>(coresActive)) {
                for (BlockPos nb : faceNeighbors(c)) {
                    if (!stabsAll.contains(nb) || stabsActive.contains(nb)) continue;
                    stabsActive.add(nb);
                    changed = true;
                }
            }
        }

        // Применяем визуал активных/неактивных
        applyUpgradeActiveStates(stabsAll, coresAll, stabsActive, coresActive);

        // Пересчёт лимитов из реально активных
        int activeStabs = stabsActive.size();
        int activeCores = coresActive.size();

        calcRange = BASE_RANGE + activeStabs * STAB_RANGE_INC;
        calcMirrorCap = BASE_MIRROR_CAP + activeStabs * STAB_MIRROR_INC;
        calcComputeCap = BASE_COMPUTE_CAP + activeCores * CORE_COMPUTE_INC;

        // Подчистить привязки, если вышли за радиус/кап
        pruneBindingsByRangeAndCapacity();

        // Если активных зеркал больше нового лимита — запланировать выброс
        // (обработка реального выброса — в tickMirrorEjects / processMirrorItemsInBuffer)
        if (getActiveMirrorCount() > calcMirrorCap) {
            // ничего особенного тут не делаем — processMirrorItemsInBuffer() переведёт «лишние» в подвешенные
        }
    }


    private void applyUpgradeActiveStates(
            java.util.Set<BlockPos> stabsAll,
            java.util.Set<BlockPos> coresAll,
            java.util.Set<BlockPos> stabsActive,
            java.util.Set<BlockPos> coresActive) {

        if (world == null || world.isRemote) return;

        // Стабилизаторы
        for (BlockPos p : stabsAll) {
            boolean shouldBeActive = stabsActive.contains(p);
            boolean wasActive = lastActiveStabs.contains(p);
            if (shouldBeActive != wasActive) {
                IBlockState st = world.getBlockState(p);
                if (st.getBlock() == ModBlocksItems.MIRROR_STABILIZER) {
                    st = st.withProperty(
                            therealpant.thaumicattempts.golemnet.block.BlockMirrorStabilizer.ACTIVE,
                            shouldBeActive
                    );
                    // 2 | 16 — обновить блок/клиента, но НЕ уведомлять соседей
                    world.setBlockState(p, st, 2 | 16);
                }
            }
        }

        // Ядра
        for (BlockPos p : coresAll) {
            boolean shouldBeActive = coresActive.contains(p);
            boolean wasActive = lastActiveCores.contains(p);
            if (shouldBeActive != wasActive) {
                IBlockState st = world.getBlockState(p);
                if (st.getBlock() == ModBlocksItems.MATH_CORE) {
                    st = st.withProperty(
                            therealpant.thaumicattempts.golemnet.block.BlockMathCore.ACTIVE,
                            shouldBeActive
                    );
                    world.setBlockState(p, st, 2 | 16);
                }
            }
        }

        // Обновить снапшот
        lastActiveStabs.clear();
        lastActiveStabs.addAll(stabsActive);
        lastActiveCores.clear();
        lastActiveCores.addAll(coresActive);
    }


    public static void deactivateUpgradesAround(net.minecraft.world.World world, BlockPos managerPos) {
        if (world == null || world.isRemote) return;

        // 5×5×3 ПОД менеджером: x/z ±2, y: [pos.y-3 .. pos.y-1]
        final BlockPos p = managerPos;
        final int minX = p.getX() - 2, maxX = p.getX() + 2;
        final int minZ = p.getZ() - 2, maxZ = p.getZ() + 2;
        final int minY = p.getY() - 3, maxY = p.getY() - 1;

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos q = new BlockPos(x, y, z);
                    IBlockState st = world.getBlockState(q);
                    Block b = st.getBlock();

                    if (b == ModBlocksItems.MIRROR_STABILIZER) {
                        if (st.getValue(therealpant.thaumicattempts.golemnet.block.BlockMirrorStabilizer.ACTIVE)) {
                            world.setBlockState(q, st.withProperty(
                                    therealpant.thaumicattempts.golemnet.block.BlockMirrorStabilizer.ACTIVE, false), 3);
                        }
                    } else if (b == ModBlocksItems.MATH_CORE) {
                        if (st.getValue(therealpant.thaumicattempts.golemnet.block.BlockMathCore.ACTIVE)) {
                            world.setBlockState(q, st.withProperty(
                                    therealpant.thaumicattempts.golemnet.block.BlockMathCore.ACTIVE, false), 3);
                        }
                    }
                }
            }
        }
    }


    private void pruneBindingsByRangeAndCapacity() {
        if (world == null || world.isRemote) return;

        java.util.function.Predicate<BlockPos> inRange = bp ->
                bp != null && bp.distanceSq(getPos()) <= (double) (calcRange * calcRange);

        List<BlockPos> dropReq = new ArrayList<>();
        List<BlockPos> dropTerm = new ArrayList<>();
        List<BlockPos> dropRev = new ArrayList<>();
        List<BlockPos> dropDisp = new ArrayList<>();

        // 1) радиус
        for (BlockPos bp : boundRequesters) if (!inRange.test(bp)) dropReq.add(bp);
        for (BlockPos bp : boundTerminals) if (!inRange.test(bp)) dropTerm.add(bp);
        for (BlockPos bp : boundRevisionPedestals) if (!inRange.test(bp)) dropRev.add(bp);
        for (BlockPos bp : boundDispatchers.keySet()) if (!inRange.test(bp)) dropDisp.add(bp);

        // 2) вычисл. кап
        int overCompute = getComputeUsed() - calcComputeCap;
        if (overCompute > 0) {
            Iterator<BlockPos> it = boundRequesters.iterator();
            while (overCompute > 0 && it.hasNext()) {
                dropReq.add(it.next());
                overCompute--;
            }
            if (overCompute > 0) {
                Iterator<BlockPos> itRv = boundRevisionPedestals.iterator();
                while (overCompute > 0 && itRv.hasNext()) {
                    dropRev.add(itRv.next());
                    overCompute--;
                }
            }
            if (overCompute > 0) {
                Iterator<Map.Entry<BlockPos, DispatcherStats>> itD = boundDispatchers.entrySet().iterator();
                while (overCompute > 0 && itD.hasNext()) {
                    Map.Entry<BlockPos, DispatcherStats> entry = itD.next();
                    dropDisp.add(entry.getKey());
                    overCompute -= (1 + entry.getValue().golems);
                }
            }
        }

        // 3) логический кап зеркал
        int overMirrors = getMirrorUsed() - calcMirrorCap;
        if (overMirrors > 0) {
            Iterator<BlockPos> itR = boundRequesters.iterator();
            while (overMirrors > 0 && itR.hasNext()) {
                dropReq.add(itR.next());
                overMirrors--;
            }
            Iterator<BlockPos> itT = boundTerminals.iterator();
            while (overMirrors > 0 && itT.hasNext()) {
                dropTerm.add(itT.next());
                overMirrors--;
            }
        }

        // 4) ФАКТИЧЕСКИЕ активные зеркала (если их меньше, чем занято)
        int freeReal = getActiveMirrorCount();
        int used = getMirrorUsed();
        int overReal = used - freeReal;
        if (overReal > 0) {
            Iterator<BlockPos> itR = boundRequesters.iterator();
            while (overReal > 0 && itR.hasNext()) {
                dropReq.add(itR.next());
                overReal--;
            }
            Iterator<BlockPos> itT = boundTerminals.iterator();
            while (overReal > 0 && itT.hasNext()) {
                dropTerm.add(itT.next());
                overReal--;
            }
        }

        if (dropReq.isEmpty() && dropTerm.isEmpty() && dropRev.isEmpty() && dropDisp.isEmpty()) return;

        // Уникализируем
        java.util.Set<BlockPos> uniqReq = new java.util.LinkedHashSet<>(dropReq);
        java.util.Set<BlockPos> uniqTerm = new java.util.LinkedHashSet<>(dropTerm);
        java.util.Set<BlockPos> uniqRev = new java.util.LinkedHashSet<>(dropRev);
        java.util.Set<BlockPos> uniqDisp = new java.util.LinkedHashSet<>(dropDisp);

        boundRequesters.removeAll(uniqReq);
        boundTerminals.removeAll(uniqTerm);
        boundRevisionPedestals.removeAll(uniqRev);
        boundDispatchers.keySet().removeAll(uniqDisp);
        if (!uniqDisp.isEmpty()) dispatcherBusyQueue.removeIf(uniqDisp::contains);

        // «Тихо» уведомляем
        isUnlinking = true;
        try {
            for (BlockPos rp : uniqReq) unlinkRequesterSideEffects(rp);
            for (BlockPos tp : uniqTerm) unlinkTerminalSideEffects(tp);
            for (BlockPos rv : uniqRev) unlinkRevisionPedestalSideEffects(rv);
            for (BlockPos dp : uniqDisp) unlinkDispatcherSideEffects(dp);
        } finally {
            isUnlinking = false;
        }

        markDirtyAndSync();
    }

    public void forceUnbindAll() {
        if (world == null || world.isRemote) return;
        isUnlinking = true;
        try {
            for (BlockPos rp : new java.util.ArrayList<>(boundRequesters)) {
                unlinkRequesterSideEffects(rp);
            }
            for (BlockPos tp : new java.util.ArrayList<>(boundTerminals)) {
                unlinkTerminalSideEffects(tp);
            }
            for (BlockPos rv : new java.util.ArrayList<>(boundRevisionPedestals)) {
                unlinkRevisionPedestalSideEffects(rv);
            }
            for (BlockPos dp : new java.util.ArrayList<>(boundDispatchers.keySet())) {
                unlinkDispatcherSideEffects(dp);
            }
            boundRequesters.clear();
            boundTerminals.clear();
            boundRevisionPedestals.clear();
            boundDispatchers.clear();
            dispatcherBusyQueue.clear();
        } finally {
            isUnlinking = false;
        }
        markDirtyAndSync();
    }


    public void unbind(@Nullable BlockPos consumerPos) {
        if (world == null || world.isRemote || consumerPos == null) return;

        releaseMirrorForConsumer(consumerPos);

        if (isUnlinking) {
            // Мы уже в процессе отвязки — просто выкинем из наборов и выйдем.
            boundTerminals.remove(consumerPos);
            boundRequesters.remove(consumerPos);
            boundRevisionPedestals.remove(consumerPos);
            markDirty();
            return;
        }

        isUnlinking = true;
        try {
            boolean wasReq = boundRequesters.remove(consumerPos);
            boolean wasTerm = boundTerminals.remove(consumerPos);
            boolean wasRev = boundRevisionPedestals.remove(consumerPos);
            DispatcherStats removedDisp = boundDispatchers.remove(consumerPos);
            boolean wasDisp = removedDisp != null;
            if (wasDisp) {
                dispatcherBusyQueue.removeIf(bp -> bp.equals(consumerPos));
            }

            if (wasReq) unlinkRequesterSideEffects(consumerPos);
            if (wasTerm) unlinkTerminalSideEffects(consumerPos);
            if (wasRev) unlinkRevisionPedestalSideEffects(consumerPos);
            if (wasDisp) unlinkDispatcherSideEffects(consumerPos);

            markDirty();
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        } finally {
            isUnlinking = false;
        }
    }


    public void cancelAllForDestination(BlockPos dst) {
        if (dst == null) return;
        boolean changed = terminalDeliveryOrders.removeIf(o -> dst.equals(o.terminalPos) || dst.equals(o.destPos));
        changed |= terminalCraftBatches.removeIf(b -> dst.equals(b.terminalPos) || dst.equals(b.endpointPos)
                || dst.equals(b.deliveryPos) || dst.equals(b.outputDestPos));
        if (changed) markDirty();
    }

    public int enqueueTerminalDeliveryOrder(BlockPos terminalPos, ItemStack like, int amount) {
        if (world == null || world.isRemote) return 0;
        if (terminalPos == null || like == null || like.isEmpty() || amount <= 0) return 0;
        if (!isBoundOrderRecipient(terminalPos)) return 0;

        TileCraftPlanner planner = findCraftPlanner();
        if (planner != null) {
            return planner.enqueuePlannedDeliveryOrder(terminalPos, like, amount, this);
        }

        return enqueueTerminalDeliveryOrderDirect(terminalPos, like, amount);
    }

    public int enqueueTerminalDeliveryOrderDirect(BlockPos terminalPos, ItemStack like, int amount) {
        if (world == null || world.isRemote) return 0;
        if (terminalPos == null || like == null || like.isEmpty() || amount <= 0) return 0;
        if (!isBoundOrderRecipient(terminalPos)) return 0;

        ItemStack normalized = ResourceIdentity.stackForRequest(like, 1);
        if (normalized.isEmpty()) return 0;

        for (TerminalDeliveryOrder order : terminalDeliveryOrders) {
            if (order.notifyTerminal && order.craftBatchId == 0 && order.matches(terminalPos, normalized)) {
                int add = Math.max(0, amount);
                order.amount += add;
                order.terminalSendVisualPlayed = false;
                order.lastProgressTick = tickCounter;
                markDirty();
                processTerminalDeliveryOrders();
                return add;
            }
        }

        terminalDeliveryOrders.add(new TerminalDeliveryOrder(terminalPos, normalized, amount, tickCounter));
        markDirty();
        processTerminalDeliveryOrders();
        return amount;
    }

    public int enqueueTerminalDeliveryOrders(BlockPos terminalPos, List<Map.Entry<ItemKey, Integer>> requests) {
        if (world == null || world.isRemote) return 0;
        if (terminalPos == null || requests == null || requests.isEmpty()) return 0;
        if (!isBoundOrderRecipient(terminalPos)) return 0;

        return enqueueTerminalDeliveryOrdersDirect(terminalPos, requests);
    }

    public int enqueueTerminalDeliveryOrdersDirect(BlockPos terminalPos, List<Map.Entry<ItemKey, Integer>> requests) {
        if (world == null || world.isRemote) return 0;
        if (terminalPos == null || requests == null || requests.isEmpty()) return 0;
        if (!isBoundOrderRecipient(terminalPos)) return 0;

        int groupId = nextTerminalDeliveryGroupId++;
        if (nextTerminalDeliveryGroupId <= 0) nextTerminalDeliveryGroupId = 1;

        int accepted = 0;
        for (Map.Entry<ItemKey, Integer> e : requests) {
            if (e == null || e.getKey() == null || e.getKey() == ItemKey.EMPTY) continue;
            int amount = Math.max(0, e.getValue());
            if (amount <= 0) continue;
            ItemStack normalized = ResourceIdentity.stackForRequest(e.getKey().toStack(1), 1);
            if (normalized.isEmpty()) continue;
            terminalDeliveryOrders.add(new TerminalDeliveryOrder(
                    terminalPos,
                    terminalPos,
                    -1,
                    normalized,
                    amount,
                    tickCounter,
                    0,
                    groupId,
                    true
            ));
            accepted += amount;
        }

        if (accepted <= 0) return 0;
        markDirty();
        processTerminalDeliveryOrders();
        return accepted;
    }

    public int enqueueTerminalCraftOrder(BlockPos terminalPos, BlockPos endpointPos, int patternSlot, ItemStack resultLike, int items) {
        if (world == null || world.isRemote) return 0;
        if (!isValidTerminalDestination(terminalPos)) return 0;
        if (endpointPos == null || resultLike == null || resultLike.isEmpty() || items <= 0) return 0;

        TileCraftPlanner planner = findCraftPlanner();
        if (planner != null) {
            return planner.enqueuePlannedCraftOrder(terminalPos, endpointPos, patternSlot, resultLike, items, this);
        }

        TileEntity endpointTile = world.getTileEntity(endpointPos);
        if (!(endpointTile instanceof ICraftEndpoint)) return 0;
        ICraftEndpoint endpoint = (ICraftEndpoint) endpointTile;
        int perCraft = Math.max(1, endpoint.getPerCraftOutputCountFor(resultLike));
        int crafts = (items + perCraft - 1) / perCraft;
        if (crafts <= 0) return 0;

        Map<ItemKey, Integer> perCycle = endpoint.getInputsPerCycle(resultLike);
        if (perCycle == null) perCycle = Collections.emptyMap();
        LinkedHashMap<ItemKey, Integer> rawInputs = multiplyInputs(perCycle, crafts);
        if (!canSupplyRawInputs(rawInputs)) return 0;

        return enqueueTerminalCraftOrderDirect(terminalPos, endpointPos, patternSlot, resultLike, items,
                terminalPos, -1, 0, rawInputs);
    }

    public int enqueueTerminalCraftOrderDirect(BlockPos terminalPos, BlockPos endpointPos, int patternSlot,
                                               ItemStack resultLike, int items, BlockPos outputDestPos,
                                               int outputDestSide, int parentBatchId,
                                               Map<ItemKey, Integer> rawInputs) {
        int batchId = enqueuePlannedCraftBatch(terminalPos, endpointPos, patternSlot, resultLike, items,
                outputDestPos, outputDestSide, parentBatchId, rawInputs);
        if (batchId <= 0) return 0;
        processTerminalDeliveryOrders();
        TileEntity endpointTile = world.getTileEntity(endpointPos);
        int perCraft = endpointTile instanceof ICraftEndpoint
                ? Math.max(1, ((ICraftEndpoint) endpointTile).getPerCraftOutputCountFor(resultLike))
                : 1;
        int crafts = (items + perCraft - 1) / perCraft;
        return Math.min(items, crafts * perCraft);
    }

    public int enqueuePlannedCraftBatch(BlockPos terminalPos, BlockPos endpointPos, int patternSlot,
                                        ItemStack resultLike, int items, BlockPos outputDestPos,
                                        int outputDestSide, int parentBatchId,
                                        Map<ItemKey, Integer> rawInputs) {
        if (world == null || world.isRemote) return 0;
        if (!isValidTerminalDestination(terminalPos)) return 0;
        if (endpointPos == null || outputDestPos == null || resultLike == null || resultLike.isEmpty() || items <= 0) return 0;

        TileEntity endpointTile = world.getTileEntity(endpointPos);
        if (!(endpointTile instanceof ICraftEndpoint)) return 0;
        ICraftEndpoint endpoint = (ICraftEndpoint) endpointTile;

        int perCraft = Math.max(1, endpoint.getPerCraftOutputCountFor(resultLike));
        int crafts = (items + perCraft - 1) / perCraft;
        if (crafts <= 0) return 0;

        BlockPos deliveryPos = resolveCraftDeliveryPos(endpointPos);
        if (deliveryPos == null) return 0;

        int batchId = nextTerminalCraftBatchId++;
        if (nextTerminalCraftBatchId <= 0) nextTerminalCraftBatchId = 1;

        TerminalCraftBatch batch = new TerminalCraftBatch(
                batchId,
                terminalPos,
                endpointPos,
                deliveryPos,
                outputDestPos,
                outputDestSide,
                parentBatchId,
                patternSlot,
                resultLike,
                crafts * perCraft,
                crafts,
                crafts * perCraft
        );
        batch.lastProgressTick = tickCounter;
        terminalCraftBatches.add(batch);

        Map<ItemKey, Integer> inputs = rawInputs == null ? Collections.emptyMap() : rawInputs;
        for (Map.Entry<ItemKey, Integer> e : inputs.entrySet()) {
            if (e.getKey() == null || e.getKey() == ItemKey.EMPTY) continue;
            int total = Math.max(0, e.getValue());
            if (total <= 0) continue;
            terminalDeliveryOrders.add(new TerminalDeliveryOrder(
                    null,
                    deliveryPos,
                    EnumFacing.UP.getIndex(),
                    e.getKey().toStack(1),
                    total,
                    tickCounter,
                    batchId,
                    false
            ));
        }

        markDirty();
        return batchId;
    }

    private LinkedHashMap<ItemKey, Integer> multiplyInputs(Map<ItemKey, Integer> perCycle, int crafts) {
        LinkedHashMap<ItemKey, Integer> out = new LinkedHashMap<>();
        if (perCycle == null || perCycle.isEmpty() || crafts <= 0) return out;
        for (Map.Entry<ItemKey, Integer> e : perCycle.entrySet()) {
            if (e.getKey() == null || e.getKey() == ItemKey.EMPTY) continue;
            int total = Math.max(0, e.getValue()) * crafts;
            if (total > 0) out.merge(e.getKey(), total, Integer::sum);
        }
        return out;
    }

    public int getQueuedTerminalDelivery(BlockPos terminalPos, ItemStack like) {
        if (terminalPos == null || like == null || like.isEmpty()) return 0;
        int total = 0;
        for (TerminalDeliveryOrder order : terminalDeliveryOrders) {
            if (order.matches(terminalPos, like)) {
                total += order.remaining();
            }
        }
        return total;
    }

    @Nullable
    private TileCraftPlanner findCraftPlanner() {
        if (world == null || world.isRemote) return null;
        for (BlockPos bp : boundTerminals) {
            TileEntity te = world.getTileEntity(bp);
            if (te instanceof TileCraftPlanner) {
                TileCraftPlanner planner = (TileCraftPlanner) te;
                if (this.pos.equals(planner.getManagerPos())) return planner;
            }
        }
        return null;
    }

    public boolean hasCraftPlanner() {
        return findCraftPlanner() != null;
    }

    public boolean canPlanCraft(ItemStack resultLike, int amount) {
        TileCraftPlanner planner = findCraftPlanner();
        if (planner == null || resultLike == null || resultLike.isEmpty() || amount <= 0) return false;
        ItemKey key = ItemKey.of(resultLike);
        if (key == null || key == ItemKey.EMPTY) return false;
        List<Map.Entry<ItemKey, Integer>> req = new ArrayList<>();
        req.add(new AbstractMap.SimpleEntry<>(key, Math.max(1, amount)));
        return planner.canCraftOrderViaPlanner(req, this);
    }

    public boolean canSupplyRawInputs(Map<ItemKey, Integer> inputs) {
        if (inputs == null || inputs.isEmpty()) return true;
        LinkedHashMap<ItemKey, Integer> pool = getReachableCatalog();
        for (Map.Entry<ItemKey, Integer> e : inputs.entrySet()) {
            if (e.getKey() == null || e.getKey() == ItemKey.EMPTY) continue;
            int want = Math.max(0, e.getValue());
            if (want <= 0) continue;
            ItemStack like = e.getKey().toStack(1);
            int buffered = countInBufferLike(like);
            int fromPool = takeFromPool(pool, e.getKey(), Math.max(0, want - buffered));
            int available = buffered + fromPool;
            if (available < want) return false;
        }
        return true;
    }

    public void processTerminalDeliveryOrders() {
        if (world == null || world.isRemote) return;

        boolean changed = false;
        Set<Integer> readyDeliveryGroups = collectReadyDeliveryGroups();
        Set<Integer> readyCraftDeliveryBatches = collectReadyCraftDeliveryBatches();
        for (Iterator<TerminalDeliveryOrder> it = terminalDeliveryOrders.iterator(); it.hasNext(); ) {
            TerminalDeliveryOrder order = it.next();
            if (!isValidDeliveryDestination(order)) {
                it.remove();
                changed = true;
                continue;
            }

            boolean groupedTerminalDelivery = order.deliveryGroupId > 0;
            boolean groupedCraftDelivery = order.craftBatchId > 0;
            if ((groupedTerminalDelivery && !readyDeliveryGroups.contains(order.deliveryGroupId))
                    || (groupedCraftDelivery && !readyCraftDeliveryBatches.contains(order.craftBatchId))) {
                if (groupedTerminalDelivery && order.notifyTerminal
                        && !order.terminalSendVisualPlayed
                        && countInBufferLike(order.like) >= order.remaining()) {
                    playTerminalDeliveryVisual(order);
                    changed = true;
                }
                if (hasActiveDispatchers() || isSequentialRequestHead(order)) {
                    changed |= requestTerminalOrderRemainder(order);
                }
                continue;
            }

            changed |= deliverOrderFromBuffer(order);
            if (order.remaining() <= 0) {
                it.remove();
                changed = true;
                continue;
            }

            changed |= requestTerminalOrderRemainder(order);
        }

        changed |= processTerminalCraftBatches();

        if (changed) markDirty();
    }

    private boolean isSequentialRequestHead(TerminalDeliveryOrder candidate) {
        if (candidate == null) return false;

        for (TerminalDeliveryOrder order : terminalDeliveryOrders) {
            if (!sameDeliveryCollection(candidate, order)) continue;
            if (order.remaining() <= 0) continue;

            if (order.inFlight > 0) {
                return order == candidate;
            }

            int buffered = countInBufferLike(order.like);
            if (buffered < order.remaining()) {
                return order == candidate;
            }
        }
        return true;
    }

    private boolean sameDeliveryCollection(TerminalDeliveryOrder a, TerminalDeliveryOrder b) {
        if (a == null || b == null) return false;
        if (a.deliveryGroupId > 0 || b.deliveryGroupId > 0) {
            return a.deliveryGroupId > 0 && a.deliveryGroupId == b.deliveryGroupId;
        }
        if (a.craftBatchId > 0 || b.craftBatchId > 0) {
            return a.craftBatchId > 0 && a.craftBatchId == b.craftBatchId;
        }
        return a == b;
    }

    private Set<Integer> collectReadyDeliveryGroups() {
        HashSet<Integer> ids = new HashSet<>();
        HashSet<Integer> checked = new HashSet<>();
        for (TerminalDeliveryOrder order : terminalDeliveryOrders) {
            if (order.deliveryGroupId <= 0) continue;
            int id = order.deliveryGroupId;
            if (!checked.add(id)) continue;
            if (isDeliveryGroupReady(id, 0)) ids.add(id);
        }
        return ids;
    }

    private Set<Integer> collectReadyCraftDeliveryBatches() {
        HashSet<Integer> ids = new HashSet<>();
        HashSet<Integer> checked = new HashSet<>();
        for (TerminalDeliveryOrder order : terminalDeliveryOrders) {
            if (order.craftBatchId <= 0) continue;
            int id = order.craftBatchId;
            if (!checked.add(id)) continue;
            if (isDeliveryGroupReady(0, id)) ids.add(id);
        }
        return ids;
    }

    private boolean isDeliveryGroupReady(int deliveryGroupId, int craftBatchId) {
        LinkedHashMap<ItemKey, Integer> needed = new LinkedHashMap<>();
        for (TerminalDeliveryOrder order : terminalDeliveryOrders) {
            boolean match = deliveryGroupId > 0
                    ? order.deliveryGroupId == deliveryGroupId
                    : order.craftBatchId == craftBatchId;
            if (!match) continue;
            int remaining = order.remaining();
            if (remaining <= 0) continue;
            ItemKey key = ItemKey.of(order.like);
            if (key == null || key == ItemKey.EMPTY) return false;
            needed.merge(key, remaining, Integer::sum);
        }
        if (needed.isEmpty()) return true;

        LinkedHashMap<ItemKey, Integer> pool = getManagerBufferSnapshot();
        for (Map.Entry<ItemKey, Integer> e : needed.entrySet()) {
            int want = Math.max(1, e.getValue());
            int taken = takeFromPool(pool, e.getKey(), want);
            if (taken < want) return false;
        }
        return true;
    }

    private LinkedHashMap<ItemKey, Integer> getManagerBufferSnapshot() {
        LinkedHashMap<ItemKey, Integer> out = new LinkedHashMap<>();
        for (int i = 0; i < buffer.getSlots(); i++) {
            ItemStack stack = buffer.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            ItemKey key = ItemKey.of(stack);
            if (key == null || key == ItemKey.EMPTY) continue;
            out.merge(key, stack.getCount(), Integer::sum);
        }
        return out;
    }

    private int takeFromPool(Map<ItemKey, Integer> pool, ItemKey key, int amount) {
        if (pool == null || pool.isEmpty() || key == null || key == ItemKey.EMPTY || amount <= 0) return 0;
        int left = amount;
        int exact = Math.max(0, pool.getOrDefault(key, 0));
        if (exact > 0) {
            int take = Math.min(exact, left);
            pool.put(key, exact - take);
            left -= take;
        }
        if (left <= 0) return amount;

        ItemStack like = key.toStack(1);
        for (Map.Entry<ItemKey, Integer> e : pool.entrySet()) {
            if (left <= 0) break;
            if (e.getKey() == null || e.getKey() == ItemKey.EMPTY || e.getKey().equals(key)) continue;
            ItemStack candidate = e.getKey().toStack(1);
            if (candidate.isEmpty() || !ResourceIdentity.sameProvisionResource(candidate, like)) continue;
            int have = Math.max(0, e.getValue());
            if (have <= 0) continue;
            int take = Math.min(have, left);
            e.setValue(have - take);
            left -= take;
        }
        return amount - left;
    }

    private boolean isValidDeliveryDestination(TerminalDeliveryOrder order) {
        if (order == null || order.destPos == null) return false;
        if (order.notifyTerminal) {
            return isValidTerminalDestination(order.terminalPos);
        }
        return world.getTileEntity(order.destPos) != null;
    }

    private boolean isValidTerminalDestination(BlockPos terminalPos) {
        if (terminalPos == null || !isBoundOrderRecipient(terminalPos)) return false;
        TileEntity te = world.getTileEntity(terminalPos);
        if (te instanceof TileOrderTerminal) {
            BlockPos manager = ((TileOrderTerminal) te).getManagerPos();
            return manager != null && manager.equals(this.pos);
        }
        if (te instanceof TileRevisionPiedestal) {
            BlockPos manager = ((TileRevisionPiedestal) te).getManagerPos();
            return manager != null && manager.equals(this.pos);
        }
        return false;
    }

    private boolean deliverOrderFromBuffer(TerminalDeliveryOrder order) {
        int remaining = order.remaining();
        if (remaining <= 0) return false;

        IItemHandler dest = order.notifyTerminal
                ? getTerminalBuffer(order.terminalPos)
                : CraftOrderApi.findDestinationHandler(world, order.destPos, order.destSide);
        if (dest == null) return false;

        int room = simulateAccept(dest, order.like, remaining);
        if (room <= 0) return false;

        List<ItemStack> extracted = extractMatchingFromBuffer(order.like, Math.min(remaining, room));
        if (extracted.isEmpty()) return false;

        int deliveredNow = 0;
        for (ItemStack stack : extracted) {
            int accepted = order.notifyTerminal
                    ? insertIntoTerminal(order.terminalPos, stack)
                    : CraftOrderApi.insertIntoDestination(world, order.destPos, order.destSide, stack);
            deliveredNow += accepted;
            int left = stack.getCount() - accepted;
            if (left > 0) {
                ItemStack rem = stack.copy();
                rem.setCount(left);
                silentInsertToBuffer(rem);
            }
        }

        if (deliveredNow <= 0) return false;

        order.delivered += deliveredNow;
        order.lastProgressTick = tickCounter;
        if (!order.notifyTerminal && order.craftBatchId > 0) {
            playCraftInputDeliveryVisual(order, deliveredNow);
        }
        if (order.notifyTerminal && order.terminalPos != null) {
            TileEntity te = world.getTileEntity(order.terminalPos);
            if (te instanceof TileOrderTerminal) {
                ((TileOrderTerminal) te).onDelivered(order.like, deliveredNow);
                playTerminalDeliveryVisual(order);
            } else if (te instanceof TileRevisionPiedestal) {
                ((TileRevisionPiedestal) te).onDelivered(order.like, deliveredNow);
            }
        }
        return true;
    }

    @Nullable
    private IItemHandler getTerminalBuffer(@Nullable BlockPos terminalPos) {
        if (terminalPos == null) return null;
        TileEntity te = world.getTileEntity(terminalPos);
        if (te instanceof TileOrderTerminal) return ((TileOrderTerminal) te).getBufferHandler();
        if (te instanceof TileRevisionPiedestal) {
            return te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        }
        return null;
    }

    private int insertIntoTerminal(@Nullable BlockPos terminalPos, ItemStack stack) {
        if (terminalPos == null || stack == null || stack.isEmpty()) return 0;
        TileEntity te = world.getTileEntity(terminalPos);
        if (te instanceof TileOrderTerminal) return ((TileOrderTerminal) te).tryInsertToBuffer(stack);
        if (te instanceof TileRevisionPiedestal) return CraftOrderApi.insertIntoDestination(world, terminalPos, -1, stack);
        return 0;
    }

    private List<ItemStack> extractMatchingFromBuffer(ItemStack like, int amount) {
        List<ItemStack> out = new ArrayList<>();
        if (like == null || like.isEmpty() || amount <= 0) return out;

        int left = amount;
        for (int i = 0; i < buffer.getSlots() && left > 0; i++) {
            ItemStack stack = buffer.getStackInSlot(i);
            if (stack.isEmpty() || !matchesForDelivery(stack, like)) continue;

            ItemStack got = buffer.extractItem(i, left, false);
            if (!got.isEmpty()) {
                left -= got.getCount();
                out.add(got);
            }
        }
        return out;
    }

    private boolean requestTerminalOrderRemainder(TerminalDeliveryOrder order) {
        int remaining = order.remaining();
        if (remaining <= 0) return false;

        if (order.inFlight > 0 && tickCounter - order.lastRequestTick >= TERMINAL_ORDER_RETRY_TICKS) {
            order.inFlight = 0;
        }

        if (order.inFlight > 0 && !hasActiveDispatchers()) {
            return false;
        }

        int buffered = countInBufferLike(order.like);
        int missing = remaining - order.inFlight - buffered;
        if (missing <= 0) return false;
        if (tickCounter - order.lastRequestTick < TERMINAL_ORDER_MIN_REQUEST_GAP) return false;

        if (hasActiveDispatchers()) {
            java.util.concurrent.ConcurrentHashMap<Integer, Task> tasks =
                    ThaumcraftProvisionHelper.getTasksSafe();
            int requested = 0;
            boolean attempted = false;
            while (missing > 0) {
                UUID golemId = reserveFreeDispatcherGolemUUID(tasks);
                if (golemId == null) break;

                int chunk = Math.min(missing, getProvisionChunkLimit(order.like));
                ItemStack request = normalizeForProvision(order.like, chunk);
                if (request.isEmpty()) break;

                attempted = true;
                if (!ThaumcraftProvisionHelper.requestProvisioningForManagerWithGolem(this, request, golemId)) {
                    break;
                }

                requested += chunk;
                missing -= chunk;
            }

            if (requested <= 0) {
                if (attempted) markProvisionAttemptFailed(order);
                return false;
            }
            order.inFlight += requested;
            order.attempts++;
            order.lastRequestTick = tickCounter;
            return true;
        }

        int chunk = Math.min(missing, getProvisionChunkLimit(order.like));
        ItemStack request = normalizeForProvision(order.like, chunk);
        if (request.isEmpty()) return false;

        boolean ok = ThaumcraftProvisionHelper.requestProvisioningForManager(this, request);
        if (!ok) {
            markProvisionAttemptFailed(order);
            return false;
        }

        order.inFlight += chunk;
        order.attempts++;
        order.lastRequestTick = tickCounter;
        return true;
    }

    private void markProvisionAttemptFailed(TerminalDeliveryOrder order) {
        if (order == null) return;
        order.attempts++;
        int delay = Math.max(1, TERMINAL_ORDER_FAILED_REQUEST_GAP);
        order.lastRequestTick = tickCounter - Math.max(0, TERMINAL_ORDER_MIN_REQUEST_GAP - delay);
    }

    private int getProvisionChunkLimit(ItemStack like) {
        if (like == null || like.isEmpty()) return 1;
        int stackLimit = Math.max(1, like.getMaxStackSize());
        return Math.max(1, Math.min(TERMINAL_ORDER_REQUEST_CHUNK, stackLimit));
    }

    private void noteTerminalDeliveryArrival(ItemStack stack, int count) {
        if (stack == null || stack.isEmpty() || count <= 0 || terminalDeliveryOrders.isEmpty()) return;
        int left = count;
        for (TerminalDeliveryOrder order : terminalDeliveryOrders) {
            if (left <= 0) break;
            if (!matchesForDelivery(stack, order.like)) continue;
            int take = Math.min(left, Math.max(0, order.inFlight));
            if (take <= 0) continue;
            order.inFlight -= take;
            order.lastProgressTick = tickCounter;
            left -= take;
        }
    }

    private boolean processTerminalCraftBatches() {
        if (terminalCraftBatches.isEmpty()) return false;

        boolean changed = false;
        for (Iterator<TerminalCraftBatch> it = terminalCraftBatches.iterator(); it.hasNext(); ) {
            TerminalCraftBatch batch = it.next();
            if (!isValidTerminalDestination(batch.terminalPos) || !(world.getTileEntity(batch.endpointPos) instanceof ICraftEndpoint)) {
                failedTerminalCraftBatches.add(batch.id);
                it.remove();
                changed = true;
                continue;
            }

            if (!batch.started && !hasOpenDeliveryForBatch(batch.id) && !hasOpenChildBatch(batch.id)) {
                if (startCraftBatch(batch)) {
                    batch.started = true;
                    batch.lastProgressTick = tickCounter;
                    changed = true;
                }
            }

            if (batch.started) {
                int moved = drainCraftBatchOutput(batch);
                if (moved > 0) {
                    batch.lastProgressTick = tickCounter;
                    changed = true;
                }
                if (batch.remainingResult() <= 0) {
                    it.remove();
                    changed = true;
                    continue;
                }

                if (!hasOpenDeliveryForBatch(batch.id) && !hasOpenChildBatch(batch.id)
                        && tickCounter - batch.lastProgressTick >= CRAFT_BATCH_STALL_TICKS) {
                    TileEntity endpointTile = world.getTileEntity(batch.endpointPos);
                    boolean endpointBusy = endpointTile instanceof ICraftEndpoint
                            && ((ICraftEndpoint) endpointTile).hasActiveOrQueued();
                    if (endpointBusy) {
                        batch.lastProgressTick = tickCounter;
                        changed = true;
                    } else if (batch.restartAttempts < CRAFT_BATCH_MAX_RESTARTS && startCraftBatch(batch)) {
                        batch.restartAttempts++;
                        batch.lastProgressTick = tickCounter;
                        changed = true;
                    } else if (batch.restartAttempts >= CRAFT_BATCH_MAX_RESTARTS) {
                        failedTerminalCraftBatches.add(batch.id);
                        it.remove();
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    private boolean startCraftBatch(TerminalCraftBatch batch) {
        TileEntity endpointTile = world.getTileEntity(batch.endpointPos);
        if (!(endpointTile instanceof ICraftEndpoint)) return false;

        if (endpointTile instanceof TileResourceRequester) {
            batch.resourceStockBaseline = countAvailableForBatchResult(batch);
        }

        if (batch.patternSlot >= 0) {
            if (endpointTile instanceof TilePatternRequester) {
                BlockPos deliveryPos = resolveCraftDeliveryPos(batch.endpointPos);
                TileEntity worksite = world.getTileEntity(deliveryPos);
                if (worksite instanceof IPatternedWorksite) {
                    return ((IPatternedWorksite) worksite).enqueueFromPatternRequester(batch.patternSlot, batch.crafts) > 0;
                }
            }
            if (endpointTile instanceof IPatternedWorksite) {
                return ((IPatternedWorksite) endpointTile).enqueueFromPatternRequester(batch.patternSlot, batch.crafts) > 0;
            }
        }

        ((ICraftEndpoint) endpointTile).enqueueCraft(batch.resultLike, batch.crafts);
        return true;
    }

    private boolean hasOpenDeliveryForBatch(int batchId) {
        for (TerminalDeliveryOrder order : terminalDeliveryOrders) {
            if (order.craftBatchId == batchId && order.remaining() > 0) return true;
        }
        return false;
    }

    private boolean hasOpenChildBatch(int batchId) {
        for (TerminalCraftBatch batch : terminalCraftBatches) {
            if (batch.parentBatchId == batchId && batch.remainingResult() > 0) return true;
        }
        return false;
    }

    public boolean isTerminalCraftBatchActive(int batchId) {
        if (batchId <= 0) return false;
        for (TerminalCraftBatch batch : terminalCraftBatches) {
            if (batch.id == batchId && batch.remainingResult() > 0) return true;
        }
        return false;
    }

    public boolean wasTerminalCraftBatchFailed(int batchId) {
        return batchId > 0 && failedTerminalCraftBatches.contains(batchId);
    }

    private int drainCraftBatchOutput(TerminalCraftBatch batch) {
        if (isResourceRequesterBatch(batch)) {
            return observeResourceRequesterBatchOutput(batch);
        }

        IItemHandler out = findCraftOutputHandler(batch);
        if (out == null) return 0;

        IItemHandler dest = CraftOrderApi.findDestinationHandler(world, batch.outputDestPos, batch.outputDestSide);
        if (dest == null) return 0;

        int movedTotal = 0;
        int need = batch.remainingResult();
        if (countCraftOutputAvailable(out, batch.resultLike) < need) return 0;

        for (int i = 0; i < out.getSlots() && need > 0; i++) {
            ItemStack can = out.extractItem(i, need, true);
            if (can.isEmpty() || !ResourceIdentity.sameResource(can, batch.resultLike)) continue;

            int room = simulateAccept(dest, batch.resultLike, Math.min(need, can.getCount()));
            if (room <= 0) break;

            ItemStack got = out.extractItem(i, Math.min(need, room), false);
            if (got.isEmpty()) continue;

            int accepted = CraftOrderApi.insertIntoDestination(world, batch.outputDestPos, batch.outputDestSide, got);
            if (accepted > 0) {
                movedTotal += accepted;
                batch.deliveredItems += accepted;
                need -= accepted;
                TileEntity termTe = world.getTileEntity(batch.outputDestPos);
                if (termTe instanceof TileOrderTerminal) {
                    ((TileOrderTerminal) termTe).onDelivered(batch.resultLike, accepted);
                } else if (termTe instanceof TileRevisionPiedestal) {
                    ((TileRevisionPiedestal) termTe).onDelivered(batch.resultLike, accepted);
                }
            }

            int left = got.getCount() - accepted;
            if (left > 0) {
                ItemStack rem = got.copy();
                rem.setCount(left);
                CraftOrderApi.insertIntoDestination(world, resolveCraftOutputPos(batch), EnumFacing.DOWN.getIndex(), rem);
                break;
            }
        }
        if (movedTotal > 0) {
            playCraftOutputTransferVisual(batch, movedTotal);
        }
        return movedTotal;
    }

    private boolean isResourceRequesterBatch(TerminalCraftBatch batch) {
        return batch != null && world != null && world.getTileEntity(batch.endpointPos) instanceof TileResourceRequester;
    }

    private int observeResourceRequesterBatchOutput(TerminalCraftBatch batch) {
        if (batch == null) return 0;
        int need = batch.remainingResult();
        if (need <= 0) return 0;

        if (batch.resourceStockBaseline < 0) {
            batch.resourceStockBaseline = countAvailableForBatchResult(batch);
            return 0;
        }

        int available = countAvailableForBatchResult(batch);
        int produced = Math.max(0, available - batch.resourceStockBaseline);
        if (produced < need) return 0;

        batch.deliveredItems += need;

        TileEntity termTe = world.getTileEntity(batch.outputDestPos);
        if (termTe instanceof TileOrderTerminal) {
            ((TileOrderTerminal) termTe).onDelivered(batch.resultLike, need);
        } else if (termTe instanceof TileRevisionPiedestal) {
            ((TileRevisionPiedestal) termTe).onDelivered(batch.resultLike, need);
        }
        return need;
    }

    private int countAvailableForBatchResult(TerminalCraftBatch batch) {
        if (batch == null || batch.resultLike == null || batch.resultLike.isEmpty()) return 0;
        return getAvailableCountFor(batch.resultLike);
    }

    private int countCraftOutputAvailable(IItemHandler out, ItemStack like) {
        if (out == null || like == null || like.isEmpty()) return 0;
        int total = 0;
        for (int i = 0; i < out.getSlots(); i++) {
            ItemStack can = out.extractItem(i, Integer.MAX_VALUE, true);
            if (!can.isEmpty() && ResourceIdentity.sameResource(can, like)) {
                total += can.getCount();
            }
        }
        return total;
    }

    @Nullable
    private IItemHandler findCraftOutputHandler(TerminalCraftBatch batch) {
        BlockPos outputPos = resolveCraftOutputPos(batch);
        TileEntity te = world.getTileEntity(outputPos);
        if (te == null) return null;
        IItemHandler down = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.DOWN);
        if (down != null) return down;
        return te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
    }

    private BlockPos resolveCraftOutputPos(TerminalCraftBatch batch) {
        return resolveCraftDeliveryPos(batch.endpointPos);
    }

    @Nullable
    private BlockPos resolveCraftDeliveryPos(BlockPos endpointPos) {
        if (endpointPos == null || world == null) return null;
        TileEntity te = world.getTileEntity(endpointPos);
        if (te instanceof TilePatternRequester) {
            TileEntity below = world.getTileEntity(endpointPos.down());
            if (below instanceof IPatternedWorksite || below instanceof ICraftEndpoint) {
                return endpointPos.down().toImmutable();
            }
        }
        return endpointPos.toImmutable();
    }


    private int countInBufferLike(ItemStack like) {
        if (like == null || like.isEmpty()) return 0;
        int total = 0;
        for (int i = 0; i < buffer.getSlots(); i++) {
            ItemStack s = buffer.getStackInSlot(i);
            if (s.isEmpty()) continue;

            boolean match;
            if (like.getMaxStackSize() == 1) {
                match = matchesForDelivery(s, like); // item+meta, без NBT
            } else if (isCrystal(like)) {
                match = isCrystal(s) && crystalSame(s, like);
            } else {
                match = ResourceIdentity.sameResource(s, like);
            }
            if (match) total += s.getCount();
        }
        return total;
    }

    private static ItemStack normalizeForProvision(ItemStack like, int amount) {
        return ResourceIdentity.stackForRequest(like, amount);
    }


    private void tickStabilityAndFlux() {
        if (world == null || world.isRemote) return;

        // раз в проверку подкачиваем Ordo в буфер
        pullOrdoToBuffer(ORDO_PULL_PER_CHECK);

        // 1) считаем "с нуля"
        int mirrorsInstab = getActiveMirrorCount(); // каждое зеркало = +1 нестабильности

        // потребители: терминалы + реквестеры + диспетчеры (если "потребитель" = любая привязка)
        int consumersInstab = boundTerminals.size() + boundRequesters.size() + boundDispatchers.size();

        // математические ядра: +2 нестабильности за каждое АКТИВНОЕ ядро
        int mathCoreInstab = lastActiveCores.size() * 2;

        int instab = mirrorsInstab + consumersInstab + mathCoreInstab;

        // стабилизаторы: +1 стабильности за каждый АКТИВНЫЙ стаб
        int stab = lastActiveStabs.size();

        // итог: (+) - (-)
        int net = 0;

        // 2) если отрицательно — пытаемся "докупиться" Ordo
        int ordoUsed = 0;
        if (net < 0) {
            int needPoints = -net;
            int needOrdo = (needPoints + (ORDO_STAB_PER_UNIT - 1)) / ORDO_STAB_PER_UNIT; // ceil

            // 1) сначала тратим буфер
            int fromBuf = consumeOrdoFromBuffer(needOrdo);
            ordoUsed += fromBuf;
            net += fromBuf * ORDO_STAB_PER_UNIT;

            // 2) если всё ещё не хватает — дотягиваем из мира (по 1) и сразу считаем как использованное
            while (net < 0) {
                boolean ok = EssentiaHandler.drainEssentia(this, Aspect.ORDER, EnumFacing.UP, ORDO_DRAIN_RANGE, 0);
                if (!ok) break;
                ordoUsed++;
                net += ORDO_STAB_PER_UNIT;
            }
        }

        // 3) если всё ещё отрицательно — флакс
        float flux = 0f;
        if (net < 0) {
            int stillBad = -net;
            int chunks10 = (stillBad + 9) / 10; // ceil(stillBad/10)
            flux = FLUX_PER_10_INSTAB * chunks10;
            if (flux < FLUX_PER_10_INSTAB) flux = FLUX_PER_10_INSTAB;

            // загрязняем ауру флаксом (как у TC)
            AuraHelper.polluteAura(world, pos, flux, true);
        }

        // 4) сохраняем для отладки/GUI
        lastStabPlus = stab;
        lastInstabMinus = instab;
        lastOrdoUsed = ordoUsed;
        lastFluxMade = flux;

        // (по желанию) лог раз в 40 тиков можно включать только при проблемах:
        // ThaumicAttempts.LOGGER.debug("[MM Stability] +{} -{} net={} ordoUsed={} flux={}",
        //         stab, instab, (stab - instab), ordoUsed, flux);
    }

    /**
     * Подкачать Ordo в буфер из банок/зеркал, как у матрицы.
     * Возвращает сколько реально закачали.
     */
    private int pullOrdoToBuffer(int maxPull) {
        if (ordoBuffer >= ORDO_BUFFER_CAP) return 0;
        int pulled = 0;

        int canTake = Math.min(maxPull, ORDO_BUFFER_CAP - ordoBuffer);
        for (int i = 0; i < canTake; i++) {
            // EssentiaHandler сам ищет банки/зеркала в радиусе и шлёт FX
            boolean ok = EssentiaHandler.drainEssentia(this, Aspect.ORDER, EnumFacing.UP, ORDO_DRAIN_RANGE, 0);
            if (!ok) break;
            ordoBuffer++;
            pulled++;
        }

        if (pulled > 0) markDirty();
        return pulled;
    }

    /**
     * Потратить Ordo из буфера (сколько есть).
     * Возвращает сколько реально потратили.
     */
    private int consumeOrdoFromBuffer(int amount) {
        if (amount <= 0 || ordoBuffer <= 0) return 0;
        int used = Math.min(amount, ordoBuffer);
        ordoBuffer -= used;
        if (used > 0) markDirty();
        return used;
    }

    /* ===================== Capability ===================== */
    @Override
    public boolean hasCapability(net.minecraftforge.common.capabilities.Capability<?> cap, @Nullable EnumFacing f) {
        if (cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return true;
        return super.hasCapability(cap, f);
    }

    @Override
    public <T> T getCapability(net.minecraftforge.common.capabilities.Capability<T> cap, @Nullable EnumFacing f) {
        if (cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(exposedBuffer);
        }
        return super.getCapability(cap, f);
    }

    /* ===================== NBT / Синхронизация клиента ===================== */
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setInteger("calcRange", calcRange);
        nbt.setInteger("calcMirrorCap", calcMirrorCap);
        nbt.setInteger("calcComputeCap", calcComputeCap);
        nbt.setInteger("dispatcherColor", dispatcherSealColor);
        nbt.setInteger("ordoBuffer", ordoBuffer);

        NBTTagList t = new NBTTagList();
        for (BlockPos bp : boundTerminals) t.appendTag(new NBTTagLong(bp.toLong()));
        nbt.setTag("boundTerminals", t);

        NBTTagList r = new NBTTagList();
        for (BlockPos bp : boundRequesters) r.appendTag(new NBTTagLong(bp.toLong()));
        nbt.setTag("boundRequesters", r);

        NBTTagList rv = new NBTTagList();
        for (BlockPos bp : boundRevisionPedestals) rv.appendTag(new NBTTagLong(bp.toLong()));
        nbt.setTag("boundRevisionPedestals", rv);
        // NEW: зарегистрированные реквестеры (для каталога крафта и поиска)
        NBTTagList rr = new NBTTagList();
        for (BlockPos bp : requesters) rr.appendTag(new NBTTagLong(bp.toLong()));
        nbt.setTag(TAG_REQ_REG, rr);

        NBTTagList dl = new NBTTagList();
        for (Map.Entry<BlockPos, DispatcherStats> entry : boundDispatchers.entrySet()) {
            NBTTagCompound c = new NBTTagCompound();
            c.setLong("pos", entry.getKey().toLong());
            c.setInteger("golems", entry.getValue().golems);
            c.setInteger("busy", entry.getValue().busy);
            dl.appendTag(c);
        }
        nbt.setTag(TAG_DISPATCHERS, dl);

        // зеркала (активные)
        NBTTagList ml = new NBTTagList();
        for (MirrorSlot m : activeMirrors) {
            NBTTagCompound c = new NBTTagCompound();
            c.setInteger("ring", m.ring);
            c.setInteger("slot", m.slot);
            c.setLong("phase", m.phase);
            c.setLong("focus", m.focusUntil); // <-- новая строка
            ml.appendTag(c);
        }
        NBTTagList pe = new NBTTagList();
        for (PendingEject p : pendingEjects) {
            NBTTagCompound c = new NBTTagCompound();
            c.setInteger("ring", p.ring);
            c.setInteger("slot", p.slot);
            c.setLong("start", p.startTick);
            pe.appendTag(c);
        }
        nbt.setTag(TAG_PEND_EJECTS, pe);
        nbt.setTag(TAG_MIRRORS, ml);
        nbt.setLong(TAG_RENDER_SEED, renderSeed);

        NBTTagList orders = new NBTTagList();
        for (TerminalDeliveryOrder order : terminalDeliveryOrders) {
            if (order.destPos == null || order.like == null || order.like.isEmpty()) continue;
            if (order.remaining() <= 0) continue;
            NBTTagCompound c = new NBTTagCompound();
            if (order.terminalPos != null) c.setLong("terminal", order.terminalPos.toLong());
            c.setLong("dest", order.destPos.toLong());
            c.setInteger("destSide", order.destSide);
            c.setInteger("batch", order.craftBatchId);
            c.setInteger("deliveryGroup", order.deliveryGroupId);
            c.setBoolean("notifyTerminal", order.notifyTerminal);
            c.setTag("like", order.like.writeToNBT(new NBTTagCompound()));
            c.setInteger("amount", order.amount);
            c.setInteger("delivered", order.delivered);
            c.setInteger("inFlight", order.inFlight);
            c.setInteger("attempts", order.attempts);
            c.setInteger("lastRequestTick", order.lastRequestTick);
            c.setInteger("lastProgressTick", order.lastProgressTick);
            orders.appendTag(c);
        }
        nbt.setTag(TAG_TERMINAL_DELIVERY_ORDERS, orders);

        NBTTagList batches = new NBTTagList();
        for (TerminalCraftBatch batch : terminalCraftBatches) {
            if (batch.remainingResult() <= 0 || batch.resultLike == null || batch.resultLike.isEmpty()) continue;
            NBTTagCompound c = new NBTTagCompound();
            c.setInteger("id", batch.id);
            c.setLong("terminal", batch.terminalPos.toLong());
            c.setLong("endpoint", batch.endpointPos.toLong());
            c.setLong("delivery", batch.deliveryPos.toLong());
            c.setLong("outputDest", batch.outputDestPos.toLong());
            c.setInteger("outputSide", batch.outputDestSide);
            c.setInteger("parentBatch", batch.parentBatchId);
            c.setInteger("patternSlot", batch.patternSlot);
            c.setTag("result", batch.resultLike.writeToNBT(new NBTTagCompound()));
            c.setInteger("requested", batch.requestedItems);
            c.setInteger("crafts", batch.crafts);
            c.setInteger("expected", batch.expectedItems);
            c.setBoolean("started", batch.started);
            c.setInteger("delivered", batch.deliveredItems);
            c.setInteger("lastProgressTick", batch.lastProgressTick);
            c.setInteger("restartAttempts", batch.restartAttempts);
            c.setInteger("resourceStockBaseline", batch.resourceStockBaseline);
            batches.appendTag(c);
        }
        nbt.setTag(TAG_TERMINAL_CRAFT_BATCHES, batches);
        nbt.setInteger("nextTerminalCraftBatchId", nextTerminalCraftBatchId);
        nbt.setInteger("nextTerminalDeliveryGroupId", nextTerminalDeliveryGroupId);

        return nbt;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        calcRange = nbt.getInteger("calcRange");
        calcMirrorCap = nbt.getInteger("calcMirrorCap");
        calcComputeCap = nbt.getInteger("calcComputeCap");
        dispatcherSealColor = nbt.hasKey("dispatcherColor")
                ? (nbt.getInteger("dispatcherColor") & 15)
                : DEFAULT_DISPATCHER_COLOR;

        boundTerminals.clear();
        boundRequesters.clear();
        boundRevisionPedestals.clear();
        if (nbt.hasKey("boundTerminals", 9)) {
            NBTTagList t = nbt.getTagList("boundTerminals", 4);
            for (int i = 0; i < t.tagCount(); i++)
                boundTerminals.add(BlockPos.fromLong(((NBTTagLong) t.get(i)).getLong()));
        }
        if (nbt.hasKey("boundRequesters", 9)) {
            NBTTagList r = nbt.getTagList("boundRequesters", 4);
            for (int i = 0; i < r.tagCount(); i++)
                boundRequesters.add(BlockPos.fromLong(((NBTTagLong) r.get(i)).getLong()));
        }
        if (nbt.hasKey("boundRevisionPedestals", 9)) {
            NBTTagList rv = nbt.getTagList("boundRevisionPedestals", 4);
            for (int i = 0; i < rv.tagCount(); i++) {
                boundRevisionPedestals.add(BlockPos.fromLong(((NBTTagLong) rv.get(i)).getLong()));
            }
        }

        ordoBuffer = nbt.getInteger("ordoBuffer");
        if (ordoBuffer < 0) ordoBuffer = 0;
        if (ordoBuffer > ORDO_BUFFER_CAP) ordoBuffer = ORDO_BUFFER_CAP;

        boundDispatchers.clear();
        dispatcherBusyQueue.clear();
        if (nbt.hasKey(TAG_DISPATCHERS, 9)) {
            NBTTagList dl = nbt.getTagList(TAG_DISPATCHERS, 10);
            for (int i = 0; i < dl.tagCount(); i++) {
                NBTTagCompound c = dl.getCompoundTagAt(i);
                if (!c.hasKey("pos")) continue;
                BlockPos dp = BlockPos.fromLong(c.getLong("pos"));
                DispatcherStats stats = new DispatcherStats();
                stats.golems = Math.max(0, c.getInteger("golems"));
                stats.busy = 0;
                boundDispatchers.put(dp.toImmutable(), stats);
            }
        }

        requesters.clear();
        if (nbt.hasKey(TAG_REQ_REG, 9)) {
            NBTTagList rr = nbt.getTagList(TAG_REQ_REG, 4);
            for (int i = 0; i < rr.tagCount(); i++)
                requesters.add(BlockPos.fromLong(((NBTTagLong) rr.get(i)).getLong()));
        } else {
            // Back-compat: если старый мир без сохранённого реестра —
            // восстановим его из boundRequesters (фильтруя по типу тайла).
            for (BlockPos rp : boundRequesters) {
                TileEntity te = world != null ? world.getTileEntity(rp) : null;
                if (te instanceof TilePatternRequester || te instanceof TileInfusionRequester) {
                    requesters.add(rp.toImmutable());
                }
            }
        }

        // зеркала
        Map<MirrorKey, MirrorSlot> prevMirrors = new HashMap<>();
        for (MirrorSlot m : activeMirrors) {
            prevMirrors.put(new MirrorKey(m.ring, m.slot), m);
        }
        for (int rr = 0; rr < RINGS; rr++) Arrays.fill(slotBusy[rr], false);
        activeMirrors.clear();
        if (nbt.hasKey(TAG_MIRRORS, 9)) {
            NBTTagList ml = nbt.getTagList(TAG_MIRRORS, 10);
            for (int i = 0; i < ml.tagCount(); i++) {
                NBTTagCompound c = ml.getCompoundTagAt(i);
                int r = Math.max(0, Math.min(RINGS - 1, c.getInteger("ring")));
                int s = Math.max(0, Math.min(SLOTS_PER_RING - 1, c.getInteger("slot")));
                long ph = c.getLong("phase");
                long fu = c.hasKey("focus", 4) ? c.getLong("focus") : 0L;
                if (!slotBusy[r][s]) {
                    slotBusy[r][s] = true;
                    MirrorSlot ms = new MirrorSlot(r, s, ph);
                    ms.focusUntil = fu;
                    MirrorSlot old = prevMirrors.get(new MirrorKey(ms.ring, ms.slot));
                    if (old != null) {
                        ms.renderYaw = old.renderYaw;
                        ms.renderFocus = old.renderFocus;
                        ms.renderDelivering = old.renderDelivering;
                        ms.idleSpin = old.idleSpin;
                        ms.lastRenderTime = old.lastRenderTime;
                        ms.lastSpinStep = old.lastSpinStep;
                    }
                    activeMirrors.add(ms);
                }
            }
        }
        pendingEjects.clear();
        if (nbt.hasKey(TAG_PEND_EJECTS, 9)) {
            NBTTagList pe = nbt.getTagList(TAG_PEND_EJECTS, 10);
            for (int i = 0; i < pe.tagCount(); i++) {
                NBTTagCompound c = pe.getCompoundTagAt(i);
                int r = Math.max(0, Math.min(RINGS - 1, c.getInteger("ring")));
                int s = Math.max(0, Math.min(SLOTS_PER_RING - 1, c.getInteger("slot")));
                long st = c.getLong("start");
                // слот должен считаться занятым, чтобы не залезли другие
                slotBusy[r][s] = true;
                pendingEjects.add(new PendingEject(r, s, st));
            }
            if (world != null && !world.isRemote) {
                propagateDispatcherColors();
            }
        }
        renderSeed = nbt.getLong(TAG_RENDER_SEED);
        terminalDeliveryOrders.clear();
        if (nbt.hasKey(TAG_TERMINAL_DELIVERY_ORDERS, 9)) {
            NBTTagList orders = nbt.getTagList(TAG_TERMINAL_DELIVERY_ORDERS, 10);
            for (int i = 0; i < orders.tagCount(); i++) {
                NBTTagCompound c = orders.getCompoundTagAt(i);
                if (!c.hasKey("like")) continue;
                ItemStack like = new ItemStack(c.getCompoundTag("like"));
                if (like.isEmpty()) continue;
                BlockPos terminal = c.hasKey("terminal") ? BlockPos.fromLong(c.getLong("terminal")) : null;
                BlockPos dest = c.hasKey("dest") ? BlockPos.fromLong(c.getLong("dest")) : terminal;
                if (dest == null) continue;
                TerminalDeliveryOrder order = new TerminalDeliveryOrder(
                        terminal,
                        dest,
                        c.hasKey("destSide") ? c.getInteger("destSide") : -1,
                        like,
                        Math.max(0, c.getInteger("amount")),
                        tickCounter,
                        c.getInteger("batch"),
                        c.getInteger("deliveryGroup"),
                        !c.hasKey("notifyTerminal") || c.getBoolean("notifyTerminal")
                );
                order.delivered = Math.max(0, c.getInteger("delivered"));
                order.inFlight = Math.max(0, c.getInteger("inFlight"));
                order.attempts = Math.max(0, c.getInteger("attempts"));
                order.lastRequestTick = c.getInteger("lastRequestTick");
                order.lastProgressTick = c.getInteger("lastProgressTick");
                if (order.remaining() > 0) terminalDeliveryOrders.add(order);
            }
        }
        terminalCraftBatches.clear();
        nextTerminalCraftBatchId = Math.max(1, nbt.getInteger("nextTerminalCraftBatchId"));
        nextTerminalDeliveryGroupId = Math.max(1, nbt.getInteger("nextTerminalDeliveryGroupId"));
        if (nbt.hasKey(TAG_TERMINAL_CRAFT_BATCHES, 9)) {
            NBTTagList batches = nbt.getTagList(TAG_TERMINAL_CRAFT_BATCHES, 10);
            for (int i = 0; i < batches.tagCount(); i++) {
                NBTTagCompound c = batches.getCompoundTagAt(i);
                if (!c.hasKey("terminal") || !c.hasKey("endpoint") || !c.hasKey("delivery") || !c.hasKey("result")) continue;
                ItemStack result = new ItemStack(c.getCompoundTag("result"));
                if (result.isEmpty()) continue;
                BlockPos outputDest = c.hasKey("outputDest")
                        ? BlockPos.fromLong(c.getLong("outputDest"))
                        : BlockPos.fromLong(c.getLong("terminal"));
                TerminalCraftBatch batch = new TerminalCraftBatch(
                        Math.max(1, c.getInteger("id")),
                        BlockPos.fromLong(c.getLong("terminal")),
                        BlockPos.fromLong(c.getLong("endpoint")),
                        BlockPos.fromLong(c.getLong("delivery")),
                        outputDest,
                        c.hasKey("outputSide") ? c.getInteger("outputSide") : -1,
                        c.getInteger("parentBatch"),
                        c.hasKey("patternSlot") ? c.getInteger("patternSlot") : -1,
                        result,
                        Math.max(1, c.getInteger("requested")),
                        Math.max(1, c.getInteger("crafts")),
                        Math.max(1, c.getInteger("expected"))
                );
                batch.started = c.getBoolean("started");
                batch.deliveredItems = Math.max(0, c.getInteger("delivered"));
                batch.lastProgressTick = c.hasKey("lastProgressTick")
                        ? c.getInteger("lastProgressTick")
                        : tickCounter;
                batch.restartAttempts = Math.max(0, c.getInteger("restartAttempts"));
                batch.resourceStockBaseline = c.hasKey("resourceStockBaseline")
                        ? c.getInteger("resourceStockBaseline")
                        : -1;
                if (batch.remainingResult() > 0) terminalCraftBatches.add(batch);
                nextTerminalCraftBatchId = Math.max(nextTerminalCraftBatchId, batch.id + 1);
            }
        }
        staleSweepTicker = 0;
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket() {
        return new net.minecraft.network.play.server.SPacketUpdateTileEntity(getPos(), 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(net.minecraft.network.NetworkManager net, net.minecraft.network.play.server.SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
    }

    /* ===================== Кристаллы TC6 и прочее ===================== */

    private static boolean isCrystal(ItemStack s) {
        return s != null && !s.isEmpty() && s.getItem() == thaumcraft.api.items.ItemsTC.crystalEssence;
    }

    @Nullable
    private static thaumcraft.api.aspects.Aspect aspectOf(ItemStack s) {
        if (!isCrystal(s)) return null;
        thaumcraft.api.aspects.AspectList al =
                ((thaumcraft.common.items.ItemTCEssentiaContainer) thaumcraft.api.items.ItemsTC.crystalEssence)
                        .getAspects(s);
        return (al != null && al.size() == 1) ? al.getAspects()[0] : null;
    }

    @Override
    public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() {
        return new net.minecraft.util.math.AxisAlignedBB(pos).grow(3.5D, 4.5D, 3.5D);
    }

    @Override
    public double getMaxRenderDistanceSquared() {
        return 65536.0D;
    }

    private static boolean crystalSame(ItemStack a, ItemStack b) {
        if (!isCrystal(a) || !isCrystal(b)) return false;
        thaumcraft.api.aspects.Aspect ax = aspectOf(a);
        thaumcraft.api.aspects.Aspect bx = aspectOf(b);
        return ax != null && bx != null && ax == bx;
    }

    private static ItemKey normKeyForCatalog(ItemStack s) {
        return ResourceIdentity.keyOf(s);
    }


    private static boolean matchesForDelivery(ItemStack a, ItemStack like) {
        return ResourceIdentity.sameProvisionResource(a, like);
    }

    /**
     * Каталог доступных ресурсов:
     *  - только инвентари под SealProvide (provideSet),
     *  - уважаем фильтры печати,
     *  - не лезем к соседям.
     */
    public LinkedHashMap<ItemKey, Integer> getReachableCatalog() {
        LinkedHashMap<ItemKey, Integer> out = new LinkedHashMap<>();
        if (world == null || world.isRemote) return out;

        // актуализируем снапшот
        rebuildProvideSetFromSeals();

        for (TrackedInv ti : provideSet) {
            TileEntity te = world.getTileEntity(ti.pos);
            if (te == null || te.isInvalid()) continue;

            EnumFacing face = (ti.side >= 0 && ti.side < 6) ? EnumFacing.byIndex(ti.side) : null;

            // Берём тот же строгий handler, что и при скане
            IItemHandler ih = getSealExactInventory(world, ti.pos, face);
            if (ih == null) continue;

            SealProvide provide = ti.seal;

            for (int i = 0; i < ih.getSlots(); i++) {
                ItemStack st = ih.getStackInSlot(i);
                if (st.isEmpty()) continue;

                if (provide != null && !provide.matchesFilters(st)) continue;

                ItemKey key = normKeyForCatalog(st);
                out.merge(key, st.getCount(), Integer::sum);
            }
        }

        return out;
    }

    public int getAvailableCountFor(ItemStack like) {
        if (like == null || like.isEmpty()) return 0;
        ItemKey key = ResourceIdentity.keyOf(like);
        if (key == null || key == ItemKey.EMPTY) return 0;
        int inNetwork = getReachableCatalog().getOrDefault(key, 0);
        return Math.max(0, inNetwork + countInBufferLike(like));
    }

    private void appendResourcePreviews(BlockPos resourcePos, Set<BlockPos> seen, List<ItemStack> into) {
        if (world == null || world.isRemote) return;
        if (resourcePos == null) return;

        BlockPos key = resourcePos.toImmutable();
        if (seen != null && seen.contains(key)) return;

        TileEntity te = world.getTileEntity(resourcePos);
        if (!(te instanceof TileResourceRequester)) return;

        if (seen != null) seen.add(key);

        TileEntity above = world.getTileEntity(resourcePos.up());
        if (!(above instanceof TilePatternRequester)) return;

        IItemHandler patt = ((TileResourceRequester) te).getPatternHandler();
        if (patt == null) return;

        for (int i = 0; i < patt.getSlots(); i++) {
            ItemStack pattern = patt.getStackInSlot(i);
            if (pattern.isEmpty() || !(pattern.getItem() instanceof ItemResourceList)) continue;
            ItemStack icon = ItemResourceList.getPreviewOrFirstEntry(pattern);
            if (icon.isEmpty()) continue;
            ItemStack display = TileResourceRequester.makeOrderIcon(icon, resourcePos, i);
            if (!display.isEmpty()) into.add(display);
        }
    }
    private void appendTerminalIcons(BlockPos pos, Set<BlockPos> seen, List<ItemStack> into) {
        if (world == null || world.isRemote || pos == null) return;

        BlockPos key = pos.toImmutable();
        if (seen != null && seen.contains(key)) return;

        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof ITerminalOrderIconProvider)) return;

        if (seen != null) seen.add(key);

        List<ItemStack> icons = ((ITerminalOrderIconProvider) te).listTerminalOrderIcons();
        if (icons != null) into.addAll(icons);
    }
    /**
     * Узкий доступ к инвентарю под печатью:
     * - только тайл по указанному адресу;
     * - без поиска соседей;
     * - без дабл-сундуков и прочей "магии".
     */
    @Nullable
    private static IItemHandler getSealExactInventory(net.minecraft.world.World world,
                                                      BlockPos pos,
                                                      @Nullable EnumFacing face) {
        if (world == null || pos == null) return null;

        TileEntity te = world.getTileEntity(pos);
        if (te == null || te.isInvalid()) return null;

        // 1) Если это обычный IInventory — оборачиваем В РУЧНУЮ.
        //    Так мы гарантируем, что видим только этот тайл, без объединения с соседями.
        if (te instanceof IInventory) {
            return new InvWrapper((IInventory) te);
        }

        // 2) Иначе пробуем capability СТРОГО на грани печати.
        if (face != null &&
                te.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face)) {
            return te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face);
        }

        // 3) Фолбэк: capability без стороны (для нестандартных хендлеров),
        //    но всё равно только на ЭТОМ тайле.
        if (te.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null)) {
            return te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        }

        return null;
    }

    public List<ItemStack> getCraftablesCatalog() {
        List<ItemStack> out = new ArrayList<>();
        Set<BlockPos> reqs = getRequestersSnapshot();
        if (world == null || reqs == null) return out;

        for (BlockPos rp : reqs) {
            TileEntity te = world.getTileEntity(rp);
            TileEntity below = world.getTileEntity(rp.down());
            if (te instanceof TilePatternRequester
                    && (below instanceof TileResourceRequester || below instanceof TileInfusionRequester)) {
                continue;
            }
            if (!(te instanceof ICraftEndpoint)) continue;

            ICraftEndpoint ep = (ICraftEndpoint) te;
            if (te instanceof ITerminalOrderIconProvider) {
                List<ItemStack> icons = ((ITerminalOrderIconProvider) te).listTerminalOrderIcons();
                if (icons != null && !icons.isEmpty()) {
                    for (ItemStack icon : icons) {
                        if (icon == null || icon.isEmpty()) continue;
                        ItemStack resultLike = TerminalOrderApi.stripOrderIconData(icon);
                        int perCraft = Math.max(1, ep.getPerCraftOutputCountFor(resultLike));
                        ItemStack copy = icon.copy();
                        copy.setCount(perCraft);
                        out.add(copy);
                    }
                    continue;
                }
            }

            List<ItemStack> lst = ep.listCraftableResults();
            if (lst == null || lst.isEmpty()) continue;

            for (ItemStack s : lst) {
                if (s == null || s.isEmpty()) continue;
                ItemStack one = s.copy();
                if (one.getCount() <= 0) one.setCount(1);
                out.add(one);
            }
        }
        return out;
    }

    /**
     * Агрегируем результаты: стакаемые — relaxed, нестакуемые — по item+meta,
     * ровно в духе логики крафтера.
     */
    private static void mergeCraftables(java.util.Map<ItemStack,Integer> agg,
                                        java.util.List<ItemStack> list) {
        if (list == null || list.isEmpty()) return;

        outer:
        for (ItemStack res : list) {
            if (res == null || res.isEmpty()) continue;

            // ищем уже существующий ключ по той же логике, что и в крафтере
            for (ItemStack ex : agg.keySet()) {
                if (sameResultForCatalog(ex, res)) {
                    int sum = agg.getOrDefault(ex, 0) + Math.max(1, res.getCount());
                    agg.put(ex, sum);
                    continue outer;
                }
            }
            // нового в agg ещё нет — добавляем
            ItemStack k = res.copy();
            k.setCount(1);
            agg.put(k, Math.max(1, res.getCount()));
        }
    }

    /**
     * Сравнение результатов для каталога:
     *  - для кристаллов — по аспекту
     *  - для нестакуемых — item + meta
     *  - иначе — relaxed stack
     */
    private static boolean sameResultForCatalog(ItemStack a, ItemStack b) {
        return ResourceIdentity.sameResource(a, b);
    }

    /* ===================== Реквестеры (крафтеры) ===================== */
    private final Set<BlockPos> requesters = new HashSet<>();

    public void registerRequester(BlockPos pos) {
        if (pos != null) {
            requesters.add(pos.toImmutable());
            markDirty();
        }
    }

    public void unregisterRequester(BlockPos pos) {
        if (pos != null && requesters.remove(pos)) markDirty();
    }

    boolean isConsumerBound(BlockPos pos) {
        if (pos == null) return false;
        return boundTerminals.contains(pos) || boundRequesters.contains(pos);
    }

    /* ===================== NBT owner allow (оставлено как было) ===================== */
    public boolean isOwnerAllowed(String owner) {
        return allowedOwners.isEmpty() || (owner != null && allowedOwners.contains(owner));
    }

    private static final class PlannerStep {
        final BlockPos requesterPos;
        final ItemStack resultLike;
        final int amount;

        PlannerStep(BlockPos requesterPos, ItemStack resultLike, int amount) {
            this.requesterPos = requesterPos;
            this.resultLike = resultLike;
            this.amount = amount;
        }
    }

    private List<ItemStack> getRecipeInputsForEndpoint(TileEntity te, ItemStack like, int crafts) {
        if (te instanceof TilePatternRequester) {
            return ((TilePatternRequester) te).getRecipeInputsFor(like, crafts);
        }
        if (te instanceof TileInfusionRequester) {
            TileInfusionRequester inf = (TileInfusionRequester) te;
            int slot = inf.findPatternSlotFor(like);
            if (slot < 0) return Collections.emptyList();
            List<therealpant.thaumicattempts.api.PatternResourceList.Entry> entries = inf.getResourcesForSlot(slot);
            if (entries == null || entries.isEmpty()) return Collections.emptyList();
            List<ItemStack> out = new ArrayList<>();
            for (therealpant.thaumicattempts.api.PatternResourceList.Entry e : entries) {
                if (e == null || e.getKey() == null || e.getKey() == ItemKey.EMPTY) continue;
                ItemStack st = e.getKey().toStack(Math.max(1, e.getCount() * Math.max(1, crafts)));
                if (!st.isEmpty()) out.add(st);
            }
            return out;
        }
        return Collections.emptyList();
    }

    private boolean tryBuildCraftPlan(ItemStack targetLike, int amount,
                                      Map<ItemKey, Integer> stock,
                                      Set<ItemKey> visiting,
                                      List<PlannerStep> outSteps) {
        if (targetLike == null || targetLike.isEmpty() || amount <= 0) return true;

        ItemKey key = ItemKey.of(targetLike);
        int have = Math.max(0, stock.getOrDefault(key, 0));
        if (have >= amount) {
            stock.put(key, have - amount);
            return true;
        }

        int missing = amount - have;
        stock.put(key, 0);

        if (!visiting.add(key)) return false;

        BlockPos rp = findRequesterForKey(key);
        if (rp == null) {
            visiting.remove(key);
            return false;
        }

        TileEntity te = world.getTileEntity(rp);
        if (!(te instanceof ICraftEndpoint)) {
            visiting.remove(key);
            return false;
        }

        ICraftEndpoint ep = (ICraftEndpoint) te;
        int perCraft = Math.max(1, ep.getPerCraftOutputCountFor(targetLike));
        int crafts = (missing + perCraft - 1) / perCraft;

        List<ItemStack> needs = getRecipeInputsForEndpoint(te, targetLike, crafts);
        if (needs != null) {
            for (ItemStack need : needs) {
                if (need == null || need.isEmpty()) continue;
                if (!tryBuildCraftPlan(need, Math.max(1, need.getCount()), stock, visiting, outSteps)) {
                    visiting.remove(key);
                    return false;
                }
            }
        }

        int produced = crafts * perCraft;
        int leftover = Math.max(0, produced - missing);
        if (leftover > 0) {
            stock.put(key, stock.getOrDefault(key, 0) + leftover);
        }

        outSteps.add(new PlannerStep(rp, targetLike.copy(), missing));
        visiting.remove(key);
        return true;
    }

    @Nullable
    private BlockPos findRequesterForKey(ItemKey key) {
        if (world == null || key == null || key == ItemKey.EMPTY) return null;

        ItemStack like = key.toStack(1);
        if (like.isEmpty()) return null;
        for (BlockPos rp : requesters) {
            TileEntity te = world.getTileEntity(rp);
            BlockPos endpointPos = rp;
            TileEntity below = world.getTileEntity(rp.down());
            if (te instanceof TilePatternRequester
                    && (below instanceof TileResourceRequester || below instanceof TileInfusionRequester)) {
                endpointPos = rp.down();
                te = world.getTileEntity(endpointPos);
            }
            if (te instanceof ICraftEndpoint) {
                List<ItemStack> outs = ((ICraftEndpoint) te).listCraftableResults();
                if (outs != null) {
                    for (ItemStack out : outs) {
                        if (out == null || out.isEmpty()) continue;
                        if (ResourceIdentity.sameResource(out, like)) return endpointPos;
                    }
                }
            }

            if (te instanceof TileResourceRequester) {
                List<ItemStack> icons = ((TileResourceRequester) te).listTerminalOrderIcons();
                if (icons == null) continue;
                for (ItemStack icon : icons) {
                    ItemStack preview = TerminalOrderApi.stripOrderIconData(icon);
                    if (!preview.isEmpty() && ResourceIdentity.sameResource(preview, like)) {
                        return endpointPos;
                    }
                }
            }
        }
        return null;
    }

    // Строгое сравнение "для стакаемых": item, meta (если есть сабтипы), NBT; кристаллы — по аспекту
    private static boolean stackMatchStrict(ItemStack a, ItemStack b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;

        // Кристаллы TC6 — только по аспекту
        if (isCrystal(a) || isCrystal(b)) {
            return isCrystal(a) && isCrystal(b) && crystalSame(a, b);
        }

        return ResourceIdentity.sameResource(a, b);
    }


    public java.util.Set<net.minecraft.util.math.BlockPos> getRequestersSnapshot() {
        java.util.HashSet<BlockPos> out = new java.util.HashSet<>(this.requesters);
        if (world != null && !world.isRemote) {
            for (BlockPos bp : boundTerminals) {
                TileEntity te = world.getTileEntity(bp);
                if (te instanceof ICraftEndpoint) out.add(bp.toImmutable());
            }
            for (BlockPos bp : boundRequesters) {
                TileEntity te = world.getTileEntity(bp);
                if (te instanceof ICraftEndpoint) out.add(bp.toImmutable());
            }
        }
        return out;
    }

}
