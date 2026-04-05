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
import thaumcraft.api.golems.GolemHelper;
import thaumcraft.api.golems.tasks.Task;
import thaumcraft.common.golems.seals.SealEntity;
import thaumcraft.common.golems.seals.SealHandler;
import thaumcraft.common.golems.seals.SealProvide;
import thaumcraft.common.lib.events.EssentiaHandler;
import therealpant.thaumicattempts.api.ICraftEndpoint;
import therealpant.thaumicattempts.api.ITerminalOrderIconProvider;
import therealpant.thaumicattempts.api.CraftOrderApi;
import therealpant.thaumicattempts.golemcraft.item.ItemResourceList;
import therealpant.thaumicattempts.golemcraft.tile.TileEntityGolemCrafter;
import therealpant.thaumicattempts.golemnet.net.msg.S2CFlyAnim;
import therealpant.thaumicattempts.golemnet.tile.TileSequentialCraftPlanner;
import therealpant.thaumicattempts.golemnet.logistics.LogisticsNetworkState;
import therealpant.thaumicattempts.golemnet.logistics.EndpointRef;
import therealpant.thaumicattempts.golemnet.logistics.NetworkOrder;
import therealpant.thaumicattempts.golemnet.logistics.OrderSourceType;
import therealpant.thaumicattempts.golemnet.logistics.RecipeNode;
import therealpant.thaumicattempts.integration.TcLogisticsCompat;
import therealpant.thaumicattempts.util.ThaumcraftProvisionHelper;

import therealpant.thaumicattempts.util.ItemKey;
import therealpant.thaumicattempts.util.ResourceIdentity;

import javax.annotation.Nullable;
import java.util.*;


import java.util.UUID;

import static therealpant.thaumicattempts.integration.ThaumcraftCompat.LOG;

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
    private final Set<BlockPos> boundPlanners = new HashSet<>();
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
    private final Map<BlockPos, Integer> consumerActiveOrders = new HashMap<>();
    private final Map<BlockPos, Long> consumerFocusCooldowns = new HashMap<>();

    private final List<MirrorSlot> activeMirrors = new ArrayList<>(MAX_SLOTS);
    private long renderSeed = 0L;

    private static final class DispatcherStats {
        int golems;
        int busy;
    }

    public int getMirrorUsed() {
        return boundTerminals.size() + boundRequesters.size();
    }

    public int getComputeUsed() {
        int used = boundRequesters.size();
        used += boundPlanners.size();
        for (DispatcherStats stats : boundDispatchers.values()) {
            used += 1 + stats.golems;
        }
        return used;
    }

    public int getDispatcherSealColor() {
        return dispatcherSealColor & 15;
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

    private static final int REQ_DEBOUNCE_TICKS = 5;
    private static final int STALL_TICKS = 40;  // 2с
    private static final int REQUEST_BUDGET = 4;
    private static final int MAX_REQ_PER_TICK = 64;
    private static final int AUTO_SCAN_PERIOD_TICKS = 100;
    private static final int PROVIDER_SCAN_RADIUS = 16;
    private static final int PROVIDER_RESCAN_TICKS = 200;
    private static final String TAG_LOGISTICS = "logisticsState";

    private final LogisticsNetworkState logistics = new LogisticsNetworkState();

    /* ===================== Владелец / доступ ===================== */

    private final Set<String> allowedOwners = new HashSet<>();
    private @Nullable String ownerUuid;

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
    private static final int MIRROR_FOCUS_RELEASE_TICKS = 20;
    private static final int MIRROR_DELIVERY_MAX_TICKS = 150;

    private static final String TAG_MIRRORS = "mirrors";
    private static final String TAG_RENDER_SEED = "renderSeed";
    private static final String TAG_PEND_EJECTS = "pendEjects";
    private static final String TAG_DISPATCHERS = "boundDispatchers";
    private static final String TAG_PLANNERS = "boundPlanners";
    private static final String TAG_CONSUMER_ORDERS = "consumerOrders";
    private static final String TAG_CONSUMER_FOCUS_GRACE = "consumerFocusGrace";

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

    public ItemStack acceptProvisionResult(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;

        ItemStack before = stack.copy();
        ItemStack rem = silentInsertToBuffer(stack);

        int accepted = before.getCount() - (rem.isEmpty() ? 0 : rem.getCount());
        if (accepted > 0) {
            onArrivedToBuffer(before, accepted);

            Batch b = peekNextBatch();
            if (b != null) {
                processBatchHead(b);
            }

            markDirtyAndSync();
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

    private void focusMirrorForDeliveryTarget(BlockPos destPos) {
        BlockPos consumerPos = resolveConsumerForDestination(destPos);
        if (consumerPos != null) {
            focusMirrorForConsumer(consumerPos);
        }
    }

    private void registerOrderForConsumer(BlockPos destPos) {
        if (world == null || world.isRemote) return;
        BlockPos consumerPos = resolveConsumerForDestination(destPos);
        if (consumerPos == null) return;

        BlockPos key = consumerPos.toImmutable();
        consumerActiveOrders.put(key, consumerActiveOrders.getOrDefault(key, 0) + 1);
        consumerFocusCooldowns.remove(key);
        focusMirrorForConsumer(key);
        markDirty();
    }

    private void completeOrderForConsumer(BlockPos destPos) {
        if (world == null || world.isRemote) return;
        BlockPos consumerPos = resolveConsumerForDestination(destPos);
        if (consumerPos == null) return;

        BlockPos key = consumerPos.toImmutable();
        int active = consumerActiveOrders.getOrDefault(key, 0);
        if (active <= 1) {
            consumerActiveOrders.remove(key);
            consumerFocusCooldowns.put(key, world.getTotalWorldTime() + MIRROR_FOCUS_RELEASE_TICKS);
        } else {
            consumerActiveOrders.put(key, active - 1);
        }
        focusMirrorForConsumer(key, MIRROR_FOCUS_TICKS);
        markDirty();
    }

    private void tickConsumerFocus() {
        if (world == null || world.isRemote) return;

        long now = world.getTotalWorldTime();
        boolean changed = false;
        Set<BlockPos> keys = new HashSet<>();
        keys.addAll(consumerActiveOrders.keySet());
        keys.addAll(consumerFocusCooldowns.keySet());

        for (BlockPos key : keys) {
            int active = consumerActiveOrders.getOrDefault(key, 0);
            long grace = consumerFocusCooldowns.getOrDefault(key, 0L);
            boolean keepFocus = active > 0 || grace > now;
            if (!keepFocus) {
                consumerFocusCooldowns.remove(key);
                changed = true;
                continue;
            }

            int duration = (active > 0) ? MIRROR_FOCUS_TICKS : MIRROR_FOCUS_RELEASE_TICKS;
            focusMirrorForConsumer(key, duration);
        }

        if (changed) {
            markDirty();
        }
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
        if (world == null || !world.isRemote) return;
        long now = world.getTotalWorldTime();
        flying.add(new FlyingItem(stack, ring, slot, now, duration, seed));
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

    private void unlinkPlannerSideEffects(BlockPos plannerPos) {
        if (world == null || world.isRemote || plannerPos == null) return;
        TileEntity te = world.getTileEntity(plannerPos);
        if (te instanceof TileSequentialCraftPlanner) {
            ((TileSequentialCraftPlanner) te).clearManagerPosFromManager(this.pos);
        }
    }

    private void unlinkTerminalSideEffects(BlockPos terminalPos) {
        if (world == null || world.isRemote || terminalPos == null) return;
        TileEntity te = world.getTileEntity(terminalPos);
        if (te instanceof TileOrderTerminal) {
            ((TileOrderTerminal) te).clearManagerPosFromManager(this.pos); // «тихо»
            cancelAllForDestination(terminalPos); // подчистить очереди
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
                    Batch b = peekNextBatch();
                    if (b != null) processBatchHead(b);
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
                if (!(te instanceof ICraftEndpoint)) {
                    it.remove();
                    changed = true;
                }
            }
            for (BlockPos rp : boundRequesters) {
                TileEntity te = world.getTileEntity(rp);
                if ((te instanceof ICraftEndpoint) && !requesters.contains(rp)) {
                    requesters.add(rp.toImmutable());
                    changed = true;
                }
            }
            if (changed) markDirtyAndSync();
            propagateDispatcherColors();
        }
    }

    /* ===================== Прогресс/в пути ===================== */

    private final Map<ItemKey, Integer> inflight = new HashMap<>();
    private final Map<ItemKey, Integer> lastProgressTick = new HashMap<>();
    private final Map<ItemKey, Integer> lastReqTick = new HashMap<>();

    private boolean decInflightRelaxed(ItemStack arrived, int count) {
        if (arrived == null || arrived.isEmpty() || count <= 0) return false;

        ItemKey exact = ItemKey.of(arrived);
        int cur = inflight.getOrDefault(exact, 0);
        if (cur > 0) {
            int left = Math.max(0, cur - count);
            if (left == 0) inflight.remove(exact);
            else inflight.put(exact, left);
            return true;
        }

        if (isCrystal(arrived)) {
            for (Map.Entry<ItemKey, Integer> e : inflight.entrySet()) {
                if (e.getValue() <= 0) continue;
                ItemStack want = e.getKey().toStack(1);
                if (isCrystal(want) && crystalSame(want, arrived)) {
                    int left = Math.max(0, e.getValue() - count);
                    e.setValue(left);
                    return true;
                }
            }
        }

        for (Map.Entry<ItemKey, Integer> e : inflight.entrySet()) {
            if (e.getValue() <= 0) continue;
            if (ResourceIdentity.sameResource(e.getKey().toStack(1), arrived)) {
                int left = Math.max(0, e.getValue() - count);
                e.setValue(left);
                return true;
            }
        }

        if (arrived.getMaxStackSize() == 1) {
            for (Map.Entry<ItemKey, Integer> e : inflight.entrySet()) {
                if (e.getValue() <= 0) continue;
                ItemStack want = e.getKey().toStack(1);
                if (matchesForDelivery(arrived, want)) {
                    int left = Math.max(0, e.getValue() - count);
                    e.setValue(left);
                    return true;
                }
            }
        }
        return false;
    }

    private void onArrivedToBuffer(ItemStack stack, int count) {
        if (stack.isEmpty() || count <= 0) return;
        boolean matched = decInflightRelaxed(stack, count);
        if (matched) releaseDispatcherGolem();


        lastProgressTick.put(ItemKey.of(stack), tickCounter);
        if (isCrystal(stack)) {
            thaumcraft.api.aspects.Aspect a = aspectOf(stack);
            if (a != null) {
                ItemStack like = thaumcraft.api.ThaumcraftApiHelper.makeCrystal(a, 1);
                lastProgressTick.put(ItemKey.of(like), tickCounter);
            }
        }
    }

    private boolean isWantedForAnyOrder(ItemStack s) {
        if (s == null || s.isEmpty()) return false;
        ItemKey k = ItemKey.of(s);
        if (inflight.getOrDefault(k, 0) > 0) return true;

        for (Deque<Batch> bq : batchQueues) {
            for (Batch b : bq) {
                if (b.kind != Batch.Kind.DELIVERY) continue;
                for (int i = Math.max(0, b.index); i < b.lines.size(); i++) {
                    Line ln = b.lines.get(i);
                    boolean match;
                    if (s.getMaxStackSize() == 1) {
                        match = matchesForDelivery(s, ln.wanted1);
                    } else if (isCrystal(s) || isCrystal(ln.wanted1)) {
                        match = isCrystal(s) && isCrystal(ln.wanted1) && crystalSame(s, ln.wanted1);
                    } else {
                        match = ResourceIdentity.sameResource(ln.wanted1, s);
                    }
                    if (match) return true;
                }
            }
        }
        return false;
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

            boolean want = isWantedForAnyOrder(drop);
            boolean haveRoom = !ItemHandlerHelper.insertItem(buffer, drop.copy(), true).equals(drop);
            if (!want && !haveRoom) continue;

            ItemStack before = drop.copy();
            ItemStack rem = ItemHandlerHelper.insertItem(buffer, drop, false);
            int accepted = before.getCount() - (rem.isEmpty() ? 0 : rem.getCount());
            if (accepted > 0) {
                onArrivedToBuffer(before, accepted);
                if (rem.isEmpty()) ei.setDead();
                else ei.setItem(rem);

                Batch b = peekNextBatch();
                if (b != null && b.kind == Batch.Kind.DELIVERY) {
                    processBatchHead(b);
                }
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

    public boolean tryBindPlanner(BlockPos pos) {
        if (pos == null) return false;
        if (pos.distanceSq(getPos()) > (double) (calcRange * calcRange)) return false;
        BlockPos key = pos.toImmutable();
        if (boundPlanners.contains(key)) return true;
        if (!hasFreeComputeCell()) return false;
        boundPlanners.add(key);
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

    private int getAvailableDispatcherGolems() {
        int free = 0;
        for (DispatcherStats stats : boundDispatchers.values()) {
            free += Math.max(0, stats.golems - stats.busy);
        }
        return free;
    }

    private int getDispatcherParallelLimit() {
        if (boundDispatchers.isEmpty()) return 1;
        int total = 0;
        for (DispatcherStats stats : boundDispatchers.values()) {
            total += Math.max(0, stats.golems);
        }
        return Math.max(1, total);
    }

    private boolean reserveDispatcherGolem() {
        if (boundDispatchers.isEmpty()) return true;
        for (Map.Entry<BlockPos, DispatcherStats> entry : boundDispatchers.entrySet()) {
            DispatcherStats stats = entry.getValue();
            if (stats.golems > stats.busy) {
                stats.busy++;
                dispatcherBusyQueue.addLast(entry.getKey());
                setDispatcherBusyCount(entry.getKey(), stats.busy);
                markDirty();
                return true;
            }
        }
        return false;
    }

    private void releaseDispatcherGolem() {
        if (boundDispatchers.isEmpty()) return;
        BlockPos key = dispatcherBusyQueue.pollFirst();
        if (key == null) return;
        DispatcherStats stats = boundDispatchers.get(key);
        if (stats == null) return;
        if (stats.busy > 0) {
            stats.busy--;
            setDispatcherBusyCount(key, stats.busy);
            markDirty();
        }
    }

    private void releaseDispatcherGolems(int count) {
        if (count <= 0) return;
        for (int i = 0; i < count; i++) {
            releaseDispatcherGolem();
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

    private boolean enqueueProvisionTask(ItemStack stack) {
        if (world == null || stack == null || stack.isEmpty()) return false;

        boolean had = hasActiveDispatchers();

        if (!had) {
            // Нет диспетчеров — классический TC provisioning, это ОК.
            GolemHelper.requestProvisioning(
                    world,
                    this.pos,
                    EnumFacing.UP,
                    stack,
                    0
            );
            return true;
        }

        // Есть диспетчеры — используем ТОЛЬКО форсированные таски через helper.
        // Внутри: ищется свободный линкованный голем и создаётся Task с жёстким golemUUID.
        return ThaumcraftProvisionHelper.requestProvisioningForManager(this, stack);
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

    /* ===================== BATCH-очереди ===================== */

    private static final class Line {
        enum State {PENDING, DISPATCHED, IN_FLIGHT, DELIVERED, STALLED, FAILED}
        final ItemStack wanted1;
        public int craftsScheduled;
        int craftsExpected;
        int craftsCompleted;
        int perCraftOut = 1;
        int remaining;
        int reserved;
        @Nullable
        BlockPos requester;

        // НОВОЕ:
        @Nullable
        UUID golemId; // закреплённый курьер для этой линии (только для DELIVERY от менеджера)
        State state = State.PENDING;
        int lastStateTick = -1;
        int lastRemainingSnapshot = -1;

        Line(ItemStack like1, int amount) {
            this.wanted1 = like1.copy();
            this.wanted1.setCount(1);
            this.remaining = Math.max(1, amount);
            this.reserved = 0;
            this.lastRemainingSnapshot = this.remaining;
        }
    }

    private List<UUID> getDispatcherGolemsSnapshot() {
        List<UUID> all = new ArrayList<>();
        if (world == null || world.isRemote) return all;
        for (Map.Entry<BlockPos, DispatcherStats> e : boundDispatchers.entrySet()) {
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
        return all;
    }
    private void assignLinesToGolemsRoundRobin(List<Line> lines) {
        if (world == null || world.isRemote) return;
        if (lines == null || lines.isEmpty()) return;

        List<UUID> golems = getDispatcherGolemsSnapshot();
        if (golems.isEmpty()) return; // нет курьеров — оставляем golemId = null

        // Чтобы между батчами не всегда начинать с первого — можно
        // использовать dispatcherRoundRobinIndex (он уже есть в классе).
        int idx = dispatcherRoundRobinIndex % golems.size();
        if (idx < 0) idx = 0;

        for (Line ln : lines) {
            if (ln == null || ln.remaining <= 0) continue;
            ln.golemId = golems.get(idx);
            idx = (idx + 1) % golems.size();
        }

        dispatcherRoundRobinIndex = idx;
    }

    private static final class Batch {
        enum Kind {DELIVERY, CRAFT}

        final Kind kind;
        final BlockPos dest;
        final int destSide;
        final int queueId;
        final List<Line> lines = new ArrayList<>();
        int index = 0;
        int seenTick = -1;

        Batch(Kind kind, BlockPos dest, int destSide, int queueId) {
            this.kind = kind;
            this.dest = dest.toImmutable();
            this.destSide = destSide;
            this.queueId = queueId;
        }
    }

    private final List<Deque<Batch>> batchQueues = new ArrayList<>(6);
    private int activeQueue = 0;

    public TileMirrorManager() {
        for (int i = 0; i < 6; i++) batchQueues.add(new ArrayDeque<>());
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

    public interface RequesterFinder {
        @Nullable
        BlockPos find(ItemKey key);
    }

    public void enqueueBatchDelivery(BlockPos dest, int destSide, int queueId,
                                     List<Map.Entry<ItemKey, Integer>> moved) {
        if (dest == null || moved == null || moved.isEmpty()) return;
        int q = Math.max(0, Math.min(5, queueId));
        Batch b = new Batch(Batch.Kind.DELIVERY, dest, destSide, q);
        for (Map.Entry<ItemKey, Integer> e : moved) {
            ItemStack like1 = e.getKey().toStack(1);
            int amt = Math.max(1, e.getValue());
            b.lines.add(new Line(like1, amt));
        }

        // НОВОЕ:
        assignLinesToGolemsRoundRobin(b.lines);

        batchQueues.get(q).addLast(b);
        registerOrderForConsumer(dest);
    }

    private int harvestAnyFromCrafterOutput(BlockPos requesterPos) {
        if (world == null || requesterPos == null) return 0;
        TileEntity te = world.getTileEntity(requesterPos.down());
        if (!(te instanceof TileEntityGolemCrafter)) return 0;

        IItemHandler out = ((TileEntityGolemCrafter) te).getOutputHandler();
        if (out == null || out.getSlots() <= 0) return 0;

        // заглянули — пусто?
        ItemStack peek = out.extractItem(0, 64, true);
        if (peek.isEmpty()) return 0;

        // забираем всё, что есть
        ItemStack taken = out.extractItem(0, peek.getCount(), false);
        if (taken.isEmpty()) return 0;

        // складываем в буфер менеджера
        ItemStack rest = ItemHandlerHelper.insertItem(buffer, taken, false);
        int accepted = taken.getCount() - (rest.isEmpty() ? 0 : rest.getCount());

        if (!rest.isEmpty()) {
            // если буфер забит — вернём остаток обратно, чтобы не потерять
            out.insertItem(0, rest, false);
        } else {
            // отметим «движение» для тайм-аутов/дебаунсов (не обязательно, но полезно)
            lastProgressTick.put(ItemKey.of(peek), tickCounter);
        }
        if (accepted > 0) markDirty();
        return accepted;
    }

    private int harvestLikeToBufferFromRequester(BlockPos requester, ItemStack like, int upTo) {
        if (world == null || requester == null || like == null || like.isEmpty() || upTo <= 0) return 0;
        BlockPos cp = requester.down();
        TileEntity cte = world.getTileEntity(cp);
        if (!(cte instanceof TileEntityGolemCrafter)) return 0;

        IItemHandler out = ((TileEntityGolemCrafter) cte).getOutputHandler();
        if (out == null) return 0;

        int moved = 0;
        for (int s = 0; s < out.getSlots() && moved < upTo; s++) {
            ItemStack peek = out.extractItem(s, 64, true);
            if (peek.isEmpty()) continue;

            boolean match;
            if (isCrystal(like) || isCrystal(peek)) {
                match = isCrystal(like) && isCrystal(peek) && crystalSame(peek, like);
            } else {
                match = (like.getMaxStackSize() == 1)
                        ? matchesForDelivery(peek, like)
                        : ResourceIdentity.sameResource(peek, like);
            }
            if (!match) continue;

            int want = Math.min(upTo - moved, peek.getCount());
            ItemStack taken = out.extractItem(s, want, false);
            if (taken.isEmpty()) continue;

            ItemStack rest = ItemHandlerHelper.insertItem(buffer, taken, false);
            int accepted = taken.getCount() - (rest.isEmpty() ? 0 : rest.getCount());
            moved += accepted;

            if (!rest.isEmpty()) {
                out.insertItem(s, rest, false);
                break;
            }
        }
        if (moved > 0) {
            markDirty();
        }
        return moved;
    }


    private int harvestLikeToBufferFromHandler(BlockPos sourcePos, int sourceSide, ItemStack like, int upTo) {
        if (world == null || sourcePos == null || like == null || like.isEmpty() || upTo <= 0) return 0;
        IItemHandler src = getDestHandler(sourcePos, sourceSide);
        if (src == null) return 0;
        int moved = 0;
        for (int s = 0; s < src.getSlots() && moved < upTo; s++) {
            ItemStack peek = src.extractItem(s, Math.min(64, upTo - moved), true);
            if (peek.isEmpty()) continue;
            boolean match = (like.getMaxStackSize() == 1)
                    ? matchesForDelivery(peek, like)
                    : (isCrystal(like)
                    ? (isCrystal(peek) && crystalSame(peek, like))
                    : ResourceIdentity.sameResource(peek, like));
            if (!match) continue;
            int want = Math.min(upTo - moved, peek.getCount());
            ItemStack taken = src.extractItem(s, want, false);
            if (taken.isEmpty()) continue;
            ItemStack rest = ItemHandlerHelper.insertItem(buffer, taken, false);
            int accepted = taken.getCount() - (rest.isEmpty() ? 0 : rest.getCount());
            moved += accepted;
            if (!rest.isEmpty()) {
                src.insertItem(s, rest, false);
                break;
            }
        }
        if (moved > 0) markDirty();
        return moved;
    }

    private int harvestLikeToBufferFromOutputCrafter(TileEntityGolemCrafter crafter, ItemStack like, int upTo) {
        if (crafter == null || like == null || like.isEmpty() || upTo <= 0) return 0;
        IItemHandler src = crafter.getOutputHandler();
        if (src == null) return 0;
        int moved = 0;
        for (int s = 0; s < src.getSlots() && moved < upTo; s++) {
            ItemStack peek = src.extractItem(s, Math.min(64, upTo - moved), true);
            if (peek.isEmpty()) continue;
            if (!matchesForDelivery(peek, like)) continue;
            ItemStack taken = src.extractItem(s, Math.min(upTo - moved, peek.getCount()), false);
            if (taken.isEmpty()) continue;
            ItemStack rest = ItemHandlerHelper.insertItem(buffer, taken, false);
            int accepted = taken.getCount() - (rest.isEmpty() ? 0 : rest.getCount());
            moved += accepted;
            if (!rest.isEmpty()) {
                src.insertItem(s, rest, false);
                break;
            }
        }
        if (moved > 0) markDirty();
        return moved;
    }

    public void enqueueBatchCraft(BlockPos dest, int destSide, int queueId,
                                  List<Map.Entry<ItemKey, Integer>> moved, RequesterFinder finder) {
        if (world == null || dest == null || moved == null || moved.isEmpty()) return;

        final int q = Math.max(0, Math.min(5, queueId));

        // вычищаем возможные дубль-доставки в ту же точку
        final java.util.function.BiConsumer<BlockPos, ItemStack> dropDupDelivery = (d, like1) -> {
            for (Deque<Batch> qd : batchQueues) {
                for (Iterator<Batch> itB = qd.iterator(); itB.hasNext(); ) {
                    Batch b = itB.next();
                    if (b.kind != Batch.Kind.DELIVERY) continue;
                    if (!b.dest.equals(d)) continue;
                    for (Iterator<Line> itL = b.lines.iterator(); itL.hasNext(); ) {
                        Line ln = itL.next();
                        boolean same = (like1.getMaxStackSize() == 1)
                                ? matchesForDelivery(ln.wanted1, like1)
                                : (isCrystal(like1)
                                ? (isCrystal(ln.wanted1) && crystalSame(ln.wanted1, like1))
                                : ResourceIdentity.sameResource(ln.wanted1, like1));
                        if (same) itL.remove();
                    }
                    if (b.lines.isEmpty()) itB.remove();
                }
            }
        };

        // для КРАФТЕРОВ: накопление потребностей
        Map<BlockPos, LinkedHashMap<ItemKey, Integer>> provisioning = new HashMap<>();
        Map<BlockPos, Map<ItemKey, Integer>> availablePerCrafter = new HashMap<>();
        List<Batch> craftBatches = new ArrayList<>();

        for (Map.Entry<ItemKey, Integer> e : moved) {
            ItemStack like1 = e.getKey().toStack(1);
            int amount = Math.max(1, e.getValue());

            // находим реквестер
            BlockPos rp = (finder != null) ? finder.find(e.getKey()) : findRequesterForKey(e.getKey());
            if (rp == null) {
                LOG.warn("[Manager {}] enqueueBatchCraft skip line: no requester found key={} amount={} dest={} queue={}",
                        pos, e.getKey(), amount, dest, q);
                continue;
            }


            dropDupDelivery.accept(dest, like1);

            TileEntity rte = world.getTileEntity(rp);
            if (!(rte instanceof ICraftEndpoint)) {
                LOG.warn("[Manager {}] enqueueBatchCraft skip line: requester is not ICraftEndpoint key={} amount={} requester={} requesterType={} dest={} queue={}",
                        pos, e.getKey(), amount, rp, rte == null ? "null" : rte.getClass().getName(), dest, q);
                continue;
            }

            ICraftEndpoint endpoint = (ICraftEndpoint) rte;
            if (!CraftOrderApi.isCrafter(endpoint)) {
                LOG.warn("[Manager {}] enqueueBatchCraft skip line: endpoint lacks crafter tag key={} amount={} requester={} endpointClass={} dest={} queue={}",
                        pos, e.getKey(), amount, rp, rte.getClass().getName(), dest, q);
                continue;
            }

            LOG.info("[Manager {}] terminal craft request submitted key={} amount={} endpointPos={} endpointClass={} dest={} queue={}",
                    pos, e.getKey(), amount, rp, rte.getClass().getSimpleName(), dest, q);


            Line ln = new Line(like1, amount);
            ln.requester = rp;

            int outPerCraft = Math.max(1, endpoint.getPerCraftOutputCountFor(like1));
            int craftsCount = (amount + outPerCraft - 1) / outPerCraft;
            ln.craftsExpected = craftsCount;
            ln.perCraftOut = outPerCraft;

            List<ItemStack> needList = endpoint.getRecipeInputsFor(like1, craftsCount);
            LOG.info("[Manager {}] craft endpoint found manager={} endpoint={} endpointClass={} key={} requested={} crafts={} perCraft={} inputs={}",
                    pos, pos, rp, rte.getClass().getSimpleName(), e.getKey(), amount, craftsCount, outPerCraft, needList);

            if (needList != null && !needList.isEmpty()) {
                LinkedHashMap<ItemKey, Integer> needs = provisioning.computeIfAbsent(
                        rp, x -> new LinkedHashMap<>());
                Map<ItemKey, Integer> available = availablePerCrafter.computeIfAbsent(
                        rp, x -> new HashMap<>());

                for (ItemStack need : needList) {
                    if (need == null || need.isEmpty()) continue;
                    ItemKey key = ItemKey.of(need);
                    ItemStack likeNeed = key.toStack(1);

                    int have = available.computeIfAbsent(key, k ->
                            countAtDestLike(rp, -1, likeNeed)
                                    + countQueuedFor(rp, likeNeed)
                                    + countInBufferLike(likeNeed));

                    int missing = Math.max(0, need.getCount() - have);
                    if (missing > 0) {
                        needs.merge(key, missing, Integer::sum);
                    }
                    int leftover = Math.max(0, have - need.getCount());
                    available.put(key, leftover);
                }
                LOG.info("[Manager {}] provisioning scheduled manager={} endpoint={} endpointClass={} key={} crafts={} needs={} queue={}",
                        pos, pos, rp, rte.getClass().getSimpleName(), e.getKey(), craftsCount, needs, q);
            }
            int acceptedAmount = endpoint.enqueueCraftOrder(this.pos, dest, destSide, like1, amount);
            int acceptedCrafts = (outPerCraft > 0) ? ((acceptedAmount + outPerCraft - 1) / outPerCraft) : 0;
            if (acceptedAmount <= 0 || acceptedCrafts <= 0) {
                LOG.warn("[Manager {}] craft order rejected manager={} endpoint={} endpointClass={} key={} requested={} crafts={} acceptedAmount={} dest={} queue={}",
                        pos, pos, rp, rte.getClass().getSimpleName(), e.getKey(), amount, craftsCount, acceptedAmount, dest, q);
                continue;
            }

            ln.craftsExpected = acceptedCrafts;
            ln.remaining = Math.min(amount, acceptedAmount);
            LOG.info("[Manager {}] craft order accepted manager={} endpoint={} endpointClass={} key={} requested={} crafts={} acceptedAmount={} acceptedCrafts={} dest={} queue={}",
                    pos, pos, rp, rte.getClass().getSimpleName(), e.getKey(), amount, craftsCount, acceptedAmount, acceptedCrafts, dest, q);

            Batch b = new Batch(Batch.Kind.CRAFT, dest, destSide, q);
            b.lines.add(ln);
            craftBatches.add(b);
        }

        // обеспечиваем доставку сырья ДЛЯ КРАФТЕРОВ (инфузия тут не участвует)
        if (!provisioning.isEmpty()) {
            for (Map.Entry<BlockPos, LinkedHashMap<ItemKey, Integer>> entry : provisioning.entrySet()) {
                ensureDeliveryForExact(entry.getKey(), entry.getValue(), q);
            }
        }

        // ставим в очередь только craft-батчи для крафтеров
        for (Batch b : craftBatches) {
            batchQueues.get(q).addLast(b);
            registerOrderForConsumer(dest);
        }

        activeQueue = q;
        markDirty();
    }

    private Batch peekNextBatch() {
        for (int i = 0; i < 6; i++) {
            int idx = (activeQueue + i) % 6;
            if (!batchQueues.get(idx).isEmpty()) {
                activeQueue = idx;
                return batchQueues.get(idx).peekFirst();
            }
        }
        return null;
    }

    private void popBatch(@Nullable Batch b) {
        if (b != null) {
            completeOrderForConsumer(b.dest);
        }
        Deque<Batch> q = batchQueues.get(activeQueue);
        if (!q.isEmpty()) q.removeFirst();
        markDirty();
    }

    /* ===== Handler адресата ===== */

    @Nullable
    private IItemHandler getDestHandler(BlockPos destPos, int destSide) {
        if (world == null || destPos == null) return null;

        TileEntity te = world.getTileEntity(destPos);
        if (te == null) return null;

        if (te instanceof TilePatternRequester) {
            TileEntity below = world.getTileEntity(destPos.down());
            if (below instanceof TileEntityGolemCrafter) {
                return ((TileEntityGolemCrafter) below).getInputHandler();
            }
        }

        if (te instanceof TileEntityGolemCrafter) {
            return ((TileEntityGolemCrafter) te).getInputHandler();
        }

        if (te instanceof TileOrderTerminal) {
            return ((TileOrderTerminal) te).getBufferHandler();
        }

        EnumFacing side = (destSide >= 0 && destSide < 6) ? EnumFacing.byIndex(destSide) : null;

        IItemHandler h = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side);
        if (h != null) return h;

        h = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (h != null) return h;

        h = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.UP);
        if (h != null) return h;

        for (EnumFacing f : EnumFacing.values()) {
            h = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, f);
            if (h != null) return h;
        }
        return null;
    }

    private boolean hasItemCapAt(BlockPos pos, int side) {
        return getDestHandler(pos, side) != null;
    }

    private int pushFromBufferTo(BlockPos destPos, int destSide, ItemStack like, int upTo) {
        if (like == null || like.isEmpty() || upTo <= 0) return 0;
        IItemHandler dest = getDestHandler(destPos, destSide);
        if (dest == null) return 0;

        // 1) Сначала — сколько адресат реально готов принять
        int cap = simulateAccept(dest, like, upTo);
        if (cap <= 0) return 0;

        int left = cap, moved = 0;

        // 2) Теперь извлекаем из буфера не больше, чем "cap"
        while (left > 0) {
            ItemStack take = extractFromBuffer(like, left);
            if (take.isEmpty()) break;

            ItemStack notFit = ItemHandlerHelper.insertItem(dest, take, false);
            int accepted = take.getCount() - (notFit.isEmpty() ? 0 : notFit.getCount());
            if (accepted > 0) {
                moved += accepted;
                left -= accepted;

                handleMirrorDeliveryAnim(destPos, like);
            }
            if (!notFit.isEmpty()) {
                // Возвращаем остаток в буфер — БЕЗ анимации
                silentInsertToBuffer(notFit);
                break;
            }
        }

        if (moved > 0) {
            TileEntity te = world.getTileEntity(destPos);
            if (te != null) te.markDirty();
            this.markDirty();
            if (te instanceof TileOrderTerminal) {
                LOG.info("[Manager {}] craft output delivered to terminal terminal={} like={} moved={}",
                        pos, destPos, like, moved);
                ((TileOrderTerminal) te).onDelivered(like, moved);
            }
        }
        return moved;
    }

    /**
     * Сервер: запуск анимации полёта предмета в зеркало,
     * закреплённое за конкретным потребителем (терминалом/реквестером).
     */
    private void handleMirrorDeliveryAnim(BlockPos destPos, ItemStack like) {
        if (world == null || world.isRemote) return;
        if (destPos == null || like == null || like.isEmpty()) return;

        BlockPos consumerPos = resolveConsumerForDestination(destPos);
        if (consumerPos == null) return;

        MirrorKey mk = getOrAssignMirrorForConsumer(consumerPos);
        if (mk == null) return;

        MirrorSlot ms = findMirrorSlot(mk);
        boolean focused = ms != null && ms.focus(world.getTotalWorldTime(), EJECT_HOVER_TICKS);
        if (focused) {
            // синхронизируем focusUntil на клиент
            markDirtyAndSync();
        }

        int dur = 36 + world.rand.nextInt(16);
        long seed = world.rand.nextLong();

        S2CFlyAnim.dispatch(
                world, this.pos, like, mk.ring, mk.slot, dur, seed
        );
    }

    private ItemStack extractFromBuffer(ItemStack like, int max) {
        if (like == null || like.isEmpty() || max <= 0) return ItemStack.EMPTY;

        if (like.getMaxStackSize() == 1) {
            for (int i = 0; i < buffer.getSlots(); i++) {
                ItemStack s = buffer.getStackInSlot(i);
                if (s.isEmpty()) continue;
                if (!matchesForDelivery(s, like)) continue;
                return buffer.extractItem(i, 1, false);
            }
            return ItemStack.EMPTY;
        }

        int left = max;
        ItemStack acc = ItemStack.EMPTY;
        for (int i = 0; i < buffer.getSlots() && left > 0; i++) {
            ItemStack s = buffer.getStackInSlot(i);
            if (s.isEmpty()) continue;

            boolean match = isCrystal(like)
                    ? (isCrystal(s) && crystalSame(s, like))
                    : ResourceIdentity.sameResource(s, like);
            if (!match) continue;

            int take = Math.min(left, s.getCount());
            ItemStack part = buffer.extractItem(i, take, false);
            if (part.isEmpty()) continue;

            if (acc.isEmpty()) acc = part;
            else acc.grow(part.getCount());
            left -= part.getCount();
        }
        return acc;
    }

    private static final class LineProcessResult {
        final boolean done;
        final int dispatcherSlotsUsed;

        LineProcessResult(boolean done, int dispatcherSlotsUsed) {
            this.done = done;
            this.dispatcherSlotsUsed = dispatcherSlotsUsed;
        }

        static LineProcessResult of(boolean done) {
            return new LineProcessResult(done, 0);
        }
    }

    private LineProcessResult processOneDeliveryLine(Batch b, Line ln, int dispatcherBudget, int dispatcherCapForLine) {
        if (ln.remaining <= 0) return LineProcessResult.of(true);
        if (ln.wanted1 == null || ln.wanted1.isEmpty()) return LineProcessResult.of(true);
        final ItemKey key = ItemKey.of(ln.wanted1);

        if (ln.requester != null) return LineProcessResult.of(true); // delivery в обычное место

        if (!hasItemCapAt(b.dest, b.destSide)) return LineProcessResult.of(false);

        int pushed0 = pushFromBufferTo(b.dest, b.destSide, ln.wanted1, ln.remaining);
        if (pushed0 > 0) {
            markDeliveryHappened();
            ln.remaining -= pushed0;
            lastProgressTick.put(key, tickCounter);

            if (ln.remaining <= 0) {
                return new LineProcessResult(true, 0);
            }
        }

        boolean toCrafter = world.getTileEntity(b.dest) instanceof TileEntityGolemCrafter;

        int flying = inflight.getOrDefault(key, 0);
        int queuedAll = countQueuedFor(b.dest, ln.wanted1);
        int queuedOthers = Math.max(0, queuedAll - ln.remaining);
        int lastReq = lastReqTick.getOrDefault(key, -9999);

        if (flying > 0 && tickCounter - lastReq > STALL_TICKS) {
            inflight.put(key, 0);
            flying = 0;
            if (ln.reserved > 0) {
                releaseDispatcherGolems(ln.reserved);
                ln.reserved = 0;
            }
            lastReqTick.put(key, tickCounter - REQ_DEBOUNCE_TICKS);
        }

        int need;
        if (toCrafter) {
            int lp = lastProgressTick.getOrDefault(key, -9999);
            if (tickCounter - lp > STALL_TICKS && (ln.reserved > 0 || flying > 0)) {
                inflight.put(key, 0);
                flying = 0;
                if (ln.reserved > 0) {
                    releaseDispatcherGolems(ln.reserved);
                    ln.reserved = 0;
                }
                lastReqTick.put(key, tickCounter - REQ_DEBOUNCE_TICKS);
                lastProgressTick.put(key, tickCounter);
            }

            int covered = queuedOthers + flying;
            need = Math.max(0, ln.remaining - covered);
        } else {
            int lp = lastProgressTick.getOrDefault(key, -9999);
            if (tickCounter - lp > STALL_TICKS && (ln.reserved > 0 || flying > 0)) {
                inflight.put(key, 0);
                flying = 0;

                int reservedBefore = ln.reserved;
                ln.reserved = 0;
                if (reservedBefore > 0) {
                    releaseDispatcherGolems(reservedBefore);
                }

                lastReqTick.put(key, tickCounter - REQ_DEBOUNCE_TICKS);
                lastProgressTick.put(key, tickCounter);
            }

            need = Math.max(0, ln.remaining - flying);
        }

        int reservationsCommitted = 0;

        if (need > 0) {
            int lt = lastReqTick.getOrDefault(key, -9999);
            if (tickCounter - lt >= REQ_DEBOUNCE_TICKS) {

                boolean hasDispatchers = !boundDispatchers.isEmpty();

                int budget;
                if (hasDispatchers) {
                    int stackLimit = Math.max(1, ln.wanted1.getMaxStackSize());
                    int golemParallel = Math.max(0, dispatcherCapForLine);
                    int pooledBudget = golemParallel * stackLimit;
                    budget = Math.min(need, pooledBudget);
                } else {
                    budget = Math.min(MAX_REQ_PER_TICK, need);
                }
                int requested = 0;

                if (!hasDispatchers) {
                    // старое ванильное поведение без менеджерской сети
                    while (budget > 0) {
                        int chunk = Math.min(ln.wanted1.getMaxStackSize(), budget);
                        if (chunk <= 0) break;

                        ItemStack req = normalizeForProvision(ln.wanted1, chunk);
                        if (req.isEmpty()) break;

                        GolemHelper.requestProvisioning(world, this.pos, EnumFacing.UP, req, 0);
                        requested += chunk;
                        budget -= chunk;
                    }
                } else {
                    // Менеджер сначала ставит таск в очередь, а диспетчер раскидывает по курьерам
                    java.util.concurrent.ConcurrentHashMap<Integer, Task> tasksSnapshot =
                            ThaumcraftProvisionHelper.getTasksSafe();
                    while (budget > 0) {
                        int chunk = Math.min(ln.wanted1.getMaxStackSize(), budget);
                        if (chunk <= 0) break;

                        ItemStack req = normalizeForProvision(ln.wanted1, chunk);

                        if (req.isEmpty()) break;

                        UUID assignedGolem = null;
                        if (ln.golemId != null
                                && isDispatcherLinkedGolem(ln.golemId)
                                && !isDispatcherGolemBusy(tasksSnapshot, ln.golemId)) {
                            assignedGolem = ln.golemId;
                        } else {
                            assignedGolem = findFreeDispatcherGolemUUID(tasksSnapshot);
                            ln.golemId = assignedGolem;
                        }

                        boolean ok;
                        if (assignedGolem != null) {
                            ok = ThaumcraftProvisionHelper.requestProvisioningForManagerWithGolem(this, req, assignedGolem);
                        } else {
                            ok = false;
                        }
                        if (!ok) {
                            // Нет свободного диспетчера/не удалось создать таск — прерываемся и доберём на следующих тиках.
                            break;
                        }
                        requested += chunk;
                        budget -= chunk;
                    }
                }

                if (requested > 0) {
                    inflight.put(key, flying + requested);
                    lastReqTick.put(key, tickCounter);
                }
            }
        }


        int pushed1 = pushFromBufferTo(b.dest, b.destSide, ln.wanted1, ln.remaining);
        if (pushed1 > 0) {
            markDeliveryHappened();
            ln.remaining -= pushed1;
            if (ln.reserved > 0) ln.reserved = Math.max(0, ln.reserved - pushed1);
            lastProgressTick.put(key, tickCounter);
            if (ln.remaining <= 0) return new LineProcessResult(true, reservationsCommitted);
        }

        return new LineProcessResult(false, reservationsCommitted);

    }

    private boolean isDispatcherGolemBusy(@Nullable java.util.concurrent.ConcurrentHashMap<Integer, Task> tasks,
                                          @Nullable UUID golemId) {
        if (golemId == null || tasks == null || tasks.isEmpty()) return false;
        for (Task t : tasks.values()) {
            if (t == null) continue;
            if (t.isCompleted() || t.isSuspended()) continue;
            UUID gid = t.getGolemUUID();
            if (golemId.equals(gid)) return true;
        }
        return false;
    }

    private boolean processOneCraftLine(Batch b, Line ln) {
        LOG.info("[Manager {}] processOneCraftLine enter wanted={} remaining={} requester={} dest={} queueId={} perCraftOut={} craftsScheduled={} craftsCompleted={} craftsExpected={}",
                pos,
                ln == null ? "null" : ln.wanted1,
                ln == null ? -1 : ln.remaining,
                ln == null ? "null" : ln.requester,
                b == null ? "null" : b.dest,
                b == null ? -1 : b.queueId,
                ln == null ? -1 : ln.perCraftOut,
                ln == null ? -1 : ln.craftsScheduled,
                ln == null ? -1 : ln.craftsCompleted,
                ln == null ? -1 : ln.craftsExpected);

        if (ln.remaining <= 0) {
            LOG.info("[Manager {}] processOneCraftLine done immediately: remaining<=0 wanted={}", pos, ln.wanted1);
            return true;
        }
        if (ln.requester == null) {
            LOG.warn("[Manager {}] processOneCraftLine done immediately: requester null wanted={}", pos, ln.wanted1);
            return true;
        }

        final BlockPos rp = ln.requester;
        harvestAnyFromCrafterOutput(rp);
        final TileEntity rte = world.getTileEntity(rp);
        final BlockPos ioPos = (rte instanceof TilePatternRequester) ? rp.down() : rp;
        final TileEntity cte = world.getTileEntity(ioPos);

        LOG.info("[Manager {}] processOneCraftLine tiles requesterPos={} requesterTe={} crafterPos={} crafterTe={}",
                pos,
                rp,
                rte == null ? "null" : rte.getClass().getName(),
                ioPos,
                cte == null ? "null" : cte.getClass().getName());

        if (!(rte instanceof ICraftEndpoint)) {
            LOG.warn("[Manager {}] processOneCraftLine abort: endpoint invalid at {} for wanted={}",
                    pos, rp, ln.wanted1);
            return true;
        }
        ICraftEndpoint endpoint = (ICraftEndpoint) rte;

        int perCraft = (ln.perCraftOut > 0) ? ln.perCraftOut : 1;
        if (perCraft <= 0) {
            perCraft = Math.max(1, endpoint.getPerCraftOutputCountFor(ln.wanted1));
            ln.perCraftOut = perCraft;
        }

        LOG.info("[Manager {}] processOneCraftLine perCraft resolved wanted={} perCraft={}",
                pos, ln.wanted1, perCraft);

        int movedFromCrafter = harvestLikeToBufferFromRequester(rp, ln.wanted1, ln.remaining);
        if (movedFromCrafter <= 0 && !(rte instanceof TilePatternRequester)) {
            movedFromCrafter = harvestLikeToBufferFromHandler(ioPos, -1, ln.wanted1, ln.remaining);
        }
        if (movedFromCrafter > 0) {
            LOG.info("[Manager {}] processOneCraftLine harvested output wanted={} movedFromCrafter={}",
                    pos, ln.wanted1, movedFromCrafter);

            if (perCraft > 0) {
                int craftsDone = (movedFromCrafter + perCraft - 1) / perCraft;
                if (ln.craftsScheduled > 0) {
                    ln.craftsScheduled = Math.max(0, ln.craftsScheduled - craftsDone);
                }
                ln.craftsCompleted += craftsDone;
            }

            int pushed = pushFromBufferTo(b.dest, b.destSide, ln.wanted1, movedFromCrafter);
            LOG.info("[Manager {}] processOneCraftLine pushed harvested output wanted={} pushed={} dest={}",
                    pos, ln.wanted1, pushed, b.dest);

            if (pushed > 0) {
                ln.remaining -= pushed;
                lastProgressTick.put(ItemKey.of(ln.wanted1), tickCounter);
                if (ln.remaining <= 0) {
                    LOG.info("[Manager {}] processOneCraftLine completed after harvested push wanted={}", pos, ln.wanted1);
                    return true;
                }
            }
        }

        int bufferedOut = countInBufferLike(ln.wanted1);
        int remainingAfterBuffer = Math.max(0, ln.remaining - bufferedOut);

        int craftsNeeded = (remainingAfterBuffer + perCraft - 1) / perCraft;

        LOG.info("[Manager {}] processOneCraftLine buffer state wanted={} bufferedOut={} remainingAfterBuffer={} craftsNeeded={}",
                pos, ln.wanted1, bufferedOut, remainingAfterBuffer, craftsNeeded);

        if (craftsNeeded <= 0) {
            int pushed = pushFromBufferTo(b.dest, b.destSide, ln.wanted1, ln.remaining);
            LOG.info("[Manager {}] processOneCraftLine no new crafts needed wanted={} pushedFromBuffer={} dest={}",
                    pos, ln.wanted1, pushed, b.dest);

            if (pushed > 0) {
                ln.remaining -= pushed;
                lastProgressTick.put(ItemKey.of(ln.wanted1), tickCounter);
                if (ln.remaining <= 0) {
                    LOG.info("[Manager {}] processOneCraftLine completed from buffer wanted={}", pos, ln.wanted1);
                    return true;
                }
            }
            return false;
        }

        List<ItemStack> needList = endpoint.getRecipeInputsFor(ln.wanted1, craftsNeeded);

        LOG.info("[Manager {}] processOneCraftLine recipe inputs wanted={} craftsNeeded={} needList={}",
                pos, ln.wanted1, craftsNeeded, needList);

        Map<ItemKey, Integer> miss = new LinkedHashMap<>();
        IItemHandler in = getDestHandler(ioPos, -1);
        if (in == null) in = exposedBuffer;
        if (needList != null) {
            for (ItemStack need : needList) {
                if (need == null || need.isEmpty()) continue;
                int want = need.getCount(), have = 0;
                for (int s = 0; s < in.getSlots(); s++) {
                    ItemStack cur = in.getStackInSlot(s);
                    if (cur.isEmpty()) continue;

                    boolean match;
                    if (isCrystal(need)) {
                        match = isCrystal(cur) && crystalSame(cur, need);
                    } else if (need.getMaxStackSize() == 1) {
                        match = matchesForDelivery(cur, need);
                    } else {
                        match = ResourceIdentity.sameResource(cur, need);
                    }

                    if (match) have += cur.getCount();
                }
                int lacking = Math.max(0, want - have);
                LOG.info("[Manager {}] processOneCraftLine input check wantedResult={} need={} want={} have={} lacking={}",
                        pos, ln.wanted1, need, want, have, lacking);
                if (lacking > 0) miss.merge(ItemKey.of(need), lacking, Integer::sum);
            }
        }

        LOG.info("[Manager {}] processOneCraftLine miss map wanted={} miss={}",
                pos, ln.wanted1, miss);

        if (!miss.isEmpty()) {

            for (Map.Entry<ItemKey, Integer> e : miss.entrySet()) {
                if (e.getValue() <= 0) continue;
                int pushed = pushFromBufferTo(ioPos, -1, e.getKey().toStack(1), e.getValue());
                if (pushed > 0) {
                    e.setValue(Math.max(0, e.getValue() - pushed));
                }
                LOG.info("[Manager {}] processOneCraftLine post-buffer-fill missingKey={} pushedToCrafter={} stillMissing={}",
                        pos, e.getKey(), pushed, e.getValue());
            }
        }

        if (!miss.isEmpty()) {
            LOG.info("[Manager {}] processOneCraftLine waiting intermediates wanted={} miss={} crafterPos={} rootDest={}",
                    pos, ln.wanted1, miss, ioPos, b.dest);
            int qDelivery = (b.queueId + 1) % 6;
            LOG.info("[Manager {}] processOneCraftLine ensureDeliveryForExact wanted={} miss={} crafterPos={} deliveryQueue={}",
                    pos, ln.wanted1, miss, ioPos, qDelivery);

            ensureDeliveryForExact(ioPos, miss, qDelivery);
            activeQueue = qDelivery;
        }

        if (boundDispatchers.isEmpty()) {
            Map.Entry<ItemKey, Integer> firstMiss = miss.entrySet().stream()
                    .filter(e -> e.getValue() > 0)
                    .findFirst().orElse(null);
            if (firstMiss != null) {
                ItemStack like1 = firstMiss.getKey().toStack(1);
                int chunk = Math.min(like1.getMaxStackSize(), firstMiss.getValue());
                ItemStack req = normalizeForProvision(like1, chunk);
                if (!req.isEmpty()) {
                    LOG.info("[Manager {}] processOneCraftLine enqueueProvisionTask wanted={} req={}",
                            pos, ln.wanted1, req);
                    enqueueProvisionTask(req);
                }
            }
        }

        boolean allReady = true;
        for (Map.Entry<ItemKey, Integer> e : miss.entrySet()) {
            if (e.getValue() > 0) {
                allReady = false;
                break;
            }
        }

        LOG.info("[Manager {}] processOneCraftLine readiness wanted={} allReady={} miss={}",
                pos, ln.wanted1, allReady, miss);

        int pushed2 = pushFromBufferTo(b.dest, b.destSide, ln.wanted1, ln.remaining);
        LOG.info("[Manager {}] processOneCraftLine final push wanted={} pushed2={} dest={} remainingBefore={}",
                pos, ln.wanted1, pushed2, b.dest, ln.remaining);

        if (pushed2 > 0) {
            ln.remaining -= pushed2;
            lastProgressTick.put(ItemKey.of(ln.wanted1), tickCounter);
            if (ln.remaining <= 0) {
                LOG.info("[Manager {}] processOneCraftLine completed after final push wanted={}", pos, ln.wanted1);
                return true;
            }
        }

        LOG.info("[Manager {}] processOneCraftLine exit wanted={} remaining={} craftsScheduled={} craftsCompleted={}",
                pos, ln.wanted1, ln.remaining, ln.craftsScheduled, ln.craftsCompleted);

        return false;
    }

    public int allocatePlannerQueue(int rootQueueId) {
        int root = Math.max(0, Math.min(5, rootQueueId));

        // 1) сначала ищем полностью пустую очередь, кроме корневой
        for (int i = 0; i < batchQueues.size(); i++) {
            if (i == root) continue;
            Deque<Batch> q = batchQueues.get(i);
            if (q == null || q.isEmpty()) {
                LOG.info("[Manager {}] allocatePlannerQueue rootQueue={} -> emptyQueue={}", pos, root, i);
                return i;
            }
        }

        // 2) если пустых нет — ищем наименее загруженную, кроме корневой
        int bestQueue = -1;
        int bestScore = Integer.MAX_VALUE;

        for (int i = 0; i < batchQueues.size(); i++) {
            if (i == root) continue;
            Deque<Batch> q = batchQueues.get(i);
            int score = 0;

            if (q != null) {
                score += q.size() * 1000;
                for (Batch b : q) {
                    if (b != null) {
                        score += b.lines.size();
                    }
                }
            }

            if (score < bestScore) {
                bestScore = score;
                bestQueue = i;
            }
        }

        if (bestQueue >= 0) {
            LOG.info("[Manager {}] allocatePlannerQueue rootQueue={} -> bestQueue={} score={}",
                    pos, root, bestQueue, bestScore);
            return bestQueue;
        }

        // 3) крайний случай — оставляем старую очередь
        LOG.warn("[Manager {}] allocatePlannerQueue fallback to rootQueue={}", pos, root);
        return root;
    }

    public static final class PlannerCraftObservation {
        public final ItemKey wanted;
        public final int remaining;
        public final BlockPos requester;
        public final BlockPos crafter;
        public final BlockPos destination;
        public final int queueId;

        public PlannerCraftObservation(ItemKey wanted,
                                       int remaining,
                                       BlockPos requester,
                                       BlockPos crafter,
                                       BlockPos destination,
                                       int queueId) {
            this.wanted = wanted;
            this.remaining = Math.max(0, remaining);
            this.requester = requester == null ? BlockPos.ORIGIN : requester.toImmutable();
            this.crafter = crafter == null ? BlockPos.ORIGIN : crafter.toImmutable();
            this.destination = destination == null ? BlockPos.ORIGIN : destination.toImmutable();
            this.queueId = queueId;
        }
    }
        boolean anyAccepted = false;

    public List<PlannerCraftObservation> getActiveCraftObservations() {
        List<PlannerCraftObservation> out = new ArrayList<>();
        if (world == null || world.isRemote) return out;

        for (Deque<Batch> q : batchQueues) {
            for (Batch b : q) {
                if (b.kind != Batch.Kind.CRAFT) continue;
                if (b.lines == null || b.lines.isEmpty()) continue;

                int start = Math.max(0, Math.min(b.index, b.lines.size() - 1));
                for (int i = start; i < b.lines.size(); i++) {
                    Line ln = b.lines.get(i);
                    if (ln == null || ln.remaining <= 0 || ln.requester == null || ln.wanted1 == null || ln.wanted1.isEmpty()) {
                        continue;
                    }
                    out.add(new PlannerCraftObservation(
                            ItemKey.of(ln.wanted1),
                            ln.remaining,
                            ln.requester,
                            ln.requester.down(),
                            b.dest,
                            b.queueId
                    ));
                }
            }
        }
        return out;
    }
    public int getServerTickCounter() {
        return tickCounter;
    }

    public UUID submitOrder(ItemKey key, int amount, OrderSourceType sourceType, BlockPos sourcePos, @Nullable BlockPos returnDestination) {
        return submitOrder(key, amount, sourceType, sourcePos, returnDestination, NetworkOrder.RequestIntent.NORMAL);
    }


    @Nullable
    public UUID submitCreationOrder(ItemKey key,
                                    int amount,
                                    OrderSourceType sourceType,
                                    BlockPos sourcePos,
                                    @Nullable BlockPos returnDestination,
                                    NetworkOrder.RequestIntent intent,
                                    therealpant.thaumicattempts.golemnet.logistics.CreationOutputMode outputMode) {
        if (world == null || world.isRemote || key == null || key == ItemKey.EMPTY || amount <= 0) return null;
        logistics.refreshRecipeIndex(this);
        UUID id = logistics.submitOrder(
                this,
                key,
                amount,
                sourceType,
                sourcePos,
                returnDestination,
                null,
                0,
                "creation-submit",
                intent,
                NetworkOrder.OrderKind.CREATION,
                outputMode
        );
        if (id != null) {
            markDirty();
        }
        return id;
    }

    public boolean canAcceptCraftRequest(ItemKey key, int amount) {
        if (world == null || world.isRemote || key == null || key == ItemKey.EMPTY || amount <= 0) return false;
        logistics.refreshRecipeIndex(this);

        TileSequentialCraftPlanner planner = getActiveSequentialPlanner();
        if (planner != null && planner.isActivePlanner()) {
            return planner.canPlanDemand(this, key, amount);
        }

        return isDirectCraftAvailable(key);
    }

    @Nullable
    public UUID submitCraftRequest(ItemKey key,
                                   int amount,
                                   OrderSourceType sourceType,
                                   BlockPos sourcePos,
                                   @Nullable BlockPos returnDestination,
                                   NetworkOrder.RequestIntent intent) {
        if (world == null || world.isRemote || key == null || key == ItemKey.EMPTY || amount <= 0) return null;
        logistics.refreshRecipeIndex(this);

        TileSequentialCraftPlanner planner = getActiveSequentialPlanner();
        if (planner != null && planner.isActivePlanner()) {
            if (!planner.canPlanDemand(this, key, amount)) {
                return null;
            }
            UUID planned = planner.planAndSubmitRootDemand(
                    this,
                    key,
                    amount,
                    sourceType,
                    sourcePos,
                    returnDestination,
                    intent
            );
            if (planned != null) {
                markDirty();
            }
            return planned;
        }

        if (!isDirectCraftAvailable(key)) {
            return null;
        }

        therealpant.thaumicattempts.golemnet.logistics.CreationOutputMode outputMode =
                (sourceType == OrderSourceType.REDSTONE_CRAFTER)
                        ? therealpant.thaumicattempts.golemnet.logistics.CreationOutputMode.LEAVE_IN_CRAFTER
                        : therealpant.thaumicattempts.golemnet.logistics.CreationOutputMode.RETURN_TO_REQUESTER;

        return submitCreationOrder(
                key,
                amount,
                sourceType,
                sourcePos,
                returnDestination,
                intent,
                outputMode
        );
    }

    public UUID submitOrder(ItemKey key, int amount, OrderSourceType sourceType, BlockPos sourcePos,
                            @Nullable BlockPos returnDestination, NetworkOrder.RequestIntent intent) {
        if (world == null || world.isRemote || key == null || key == ItemKey.EMPTY || amount <= 0) return null;
        logistics.refreshRecipeIndex(this);
        UUID id = logistics.submitOrder(this, key, amount, sourceType, sourcePos, returnDestination, null, 0, "root-submit", intent);
        markDirty();
        return id;
    }

    private boolean isDirectCraftAvailable(ItemKey key) {
        if (key == null || key == ItemKey.EMPTY) return false;
        List<ItemStack> craftables = getCraftablesCatalog();
        if (craftables == null || craftables.isEmpty()) return false;

        ItemStack like = key.toStack(1);
        for (ItemStack out : craftables) {
            if (out == null || out.isEmpty()) continue;
            if (ResourceIdentity.sameResource(out, like)) {
                return true;
            }
        }
        return false;
    }


    @Nullable
    private TileSequentialCraftPlanner getActiveSequentialPlanner() {
        if (world == null || world.isRemote) return null;
        for (BlockPos plannerPos : boundPlanners) {
            TileEntity te = world.getTileEntity(plannerPos);
            if (te instanceof TileSequentialCraftPlanner) {
                return (TileSequentialCraftPlanner) te;
            }
        }
        return null;
    }

    public List<ItemStack> getPlannerCraftablesCatalog() {
        LinkedHashMap<ItemKey, ItemStack> out = new LinkedHashMap<ItemKey, ItemStack>();

        List<ItemStack> direct = getCraftablesCatalog();
        if (direct != null) {
            for (ItemStack s : direct) {
                if (s == null || s.isEmpty()) continue;
                ItemKey k = ItemKey.of(s);
                if (k == null || k == ItemKey.EMPTY) continue;
                out.putIfAbsent(k, s.copy());
            }
        }

        for (RecipeNode node : logistics.getAllRecipeNodesForPlanning()) {
            if (node == null || node.result == null || node.result == ItemKey.EMPTY) continue;
            ItemStack s = node.result.toStack(Math.max(1, node.outputPerCycle));
            if (s.isEmpty()) continue;
            ItemKey k = ItemKey.of(s);
            if (k == null || k == ItemKey.EMPTY) continue;
            out.putIfAbsent(k, s);
        }

        return new ArrayList<ItemStack>(out.values());
    }

    @Nullable
    public RecipeNode getPlannerRecipe(ItemKey key) {
        if (world == null || world.isRemote) return null;
        if (key == null || key == ItemKey.EMPTY) return null;

        /*
         * Planner при рекурсивном построении дерева должен смотреть на
         * актуальный recipe index, а не на индекс десятитиковой давности.
         */
        refreshRecipeIndexFromPlanner();

        return logistics.getRecipeNodeForPlanning(key);
    }

    @Nullable
    public TileSequentialCraftPlanner findBestSequentialPlanner() {
        if (world == null) return null;

        for (BlockPos pos : getRequestersSnapshot()) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileSequentialCraftPlanner) {
                return (TileSequentialCraftPlanner) te;
            }
        }
        return null;
    }

    public UUID submitEndpointRequest(OrderSourceType sourceType,
                                      BlockPos endpointPos,
                                      ItemKey key,
                                      int amount,
                                      @Nullable BlockPos destination,
                                      @Nullable String metadata) {
        return submitEndpointRequest(sourceType, endpointPos, key, amount, destination, metadata, NetworkOrder.RequestIntent.NORMAL);
    }

    public UUID submitEndpointRequest(OrderSourceType sourceType,
                                      BlockPos endpointPos,
                                      ItemKey key,
                                      int amount,
                                      @Nullable BlockPos destination,
                                      @Nullable String metadata,
                                      NetworkOrder.RequestIntent intent) {
        if (world == null || world.isRemote || endpointPos == null || key == null || key == ItemKey.EMPTY || amount <= 0) return null;
        logistics.refreshRecipeIndex(this);
        UUID id = logistics.submitOrder(this, key, amount, sourceType, endpointPos, destination, null, 0,
                metadata == null ? "endpoint-submit" : metadata, intent);
        if (id != null) {
            LOG.info("[Manager {}] submitEndpointRequest accepted order={} sourceType={} endpoint={} key={} amount={} destination={} reason={} intent={}",
                    pos, id, sourceType, endpointPos, key, amount, destination, metadata, intent);
        }
        markDirty();
        return id;
    }

    public boolean isOrderActive(@Nullable UUID orderId) {
        return logistics.isOrderActive(orderId);
    }

    @Nullable
    public therealpant.thaumicattempts.golemnet.logistics.OrderStatus getOrderStatus(@Nullable UUID orderId) {
        return logistics.getOrderStatus(orderId);
    }

    @Nullable
    public String getOrderLastError(@Nullable UUID orderId) {
        return logistics.getOrderLastError(orderId);
    }

    public boolean hasActivePlannerContinuation(@Nullable UUID orderId) {
        return logistics.hasActiveChildOrders(orderId);
    }

    public int executeTransferTask(ItemKey key, int amount, BlockPos source, BlockPos target) {
        if (world == null || world.isRemote || key == null || key == ItemKey.EMPTY || amount <= 0 || source == null || target == null) {
            return 0;
        }

        ItemStack like = key.toStack(1);

        /*
         * Спец-случай:
         * задача "склад -> буфер менеджера".
         * Здесь нельзя ограничиваться одной попыткой pullLikeFromProvideSetToBuffer(),
         * иначе при переноске меньше amount задача зависает.
         *
         * Мы:
         * 1) смотрим, что уже есть в буфере;
         * 2) мгновенно добираем локально, что можно;
         * 3) недостачу отправляем в batch-очередь exact-delivery на сам буфер менеджера.
         */
        if (source.equals(this.pos) && target.equals(this.pos)) {
            int bufferedNow = countInManagerBufferLike(like);
            if (bufferedNow >= amount) {
                return amount;
            }

            int need = amount - bufferedNow;
            int pulledNow = pullLikeFromProvideSetToBuffer(like, need);

            int bufferedAfterPull = countInManagerBufferLike(like);
            int stillNeed = Math.max(0, amount - bufferedAfterPull);

            if (stillNeed > 0) {
                LinkedHashMap<ItemKey, Integer> needs = new LinkedHashMap<ItemKey, Integer>();
                needs.put(key, stillNeed);
                ensureDeliveryForExact(this.pos, needs, 0);
            }

            int queued = countQueuedFor(this.pos, like);
            return Math.min(amount, bufferedAfterPull + queued);
        }

        if (!source.equals(this.pos)) {
            harvestLikeToBufferFromHandler(source, -1, like, amount);
        } else {
            pullLikeFromProvideSetToBuffer(like, amount);
        }

        return pushFromBufferTo(target, -1, like, amount);
    }

    public BlockPos resolveInputHandlerPos(BlockPos endpointPos) {
        if (endpointPos == null) return BlockPos.ORIGIN;
        if (world == null) return endpointPos.toImmutable();
        TileEntity te = world.getTileEntity(endpointPos);
        if (te instanceof ICraftEndpoint) {
            TileEntity below = world.getTileEntity(endpointPos.down());
            if (below instanceof TileEntityGolemCrafter) {
                return endpointPos.down().toImmutable();
            }
        }
        return endpointPos.toImmutable();
    }

    public BlockPos resolveOutputHandlerPos(BlockPos endpointPos) {
        return resolveInputHandlerPos(endpointPos);
    }

    public BlockPos resolveEndpointPos(EndpointRef endpoint) {
        if (endpoint == null) return BlockPos.ORIGIN;
        switch (endpoint.mode) {
            case INPUT:
                return resolveInputHandlerPos(endpoint.pos);
            case OUTPUT:
                return resolveOutputHandlerPos(endpoint.pos);
            case BUFFER:
            case DIRECT:
            default:
                return endpoint.pos;
        }
    }

    private int countCoveredAtTarget(EndpointRef target, ItemKey key, int amount) {
        if (target == null || key == null || key == ItemKey.EMPTY || amount <= 0) return 0;

        int atTarget = countItemAtEndpoint(target, key);
        int queued = countQueuedForEndpoint(target, key);

        return Math.min(amount, Math.max(0, atTarget + queued));
    }

    private int countCoveredForManagerBuffer(ItemKey key, int amount) {
        if (key == null || key == ItemKey.EMPTY || amount <= 0) return 0;

        int buffered = countBuffered(key);
        int queued = countQueuedFor(this.pos, key.toStack(1));

        return Math.min(amount, Math.max(0, buffered + queued));
    }

    public int countItemAtEndpoint(EndpointRef endpoint, ItemKey key) {
        if (endpoint != null && endpoint.mode == EndpointRef.AccessMode.OUTPUT && world != null) {
            BlockPos resolved = resolveEndpointPos(endpoint);
            TileEntity te = world.getTileEntity(resolved);
            if (te instanceof TileEntityGolemCrafter) {
                return countAtHandlerLike(((TileEntityGolemCrafter) te).getOutputHandler(), key.toStack(1));
            }
        }
        BlockPos resolved = resolveEndpointPos(endpoint);
        return countItemAt(resolved, key);
    }

    public boolean isInboundToManagerBuffer(EndpointRef source, EndpointRef target) {
        if (source == null || target == null) return false;
        return target.mode == EndpointRef.AccessMode.BUFFER
                && this.pos.equals(target.pos)
                && !this.pos.equals(resolveEndpointPos(source));
    }

    public int countQueuedForEndpoint(EndpointRef endpoint, ItemKey key) {
        if (endpoint == null || key == null || key == ItemKey.EMPTY) return 0;
        return countQueuedFor(resolveEndpointPos(endpoint), key.toStack(1));
    }

    public boolean hasAvailableItems(EndpointRef source, ItemKey key) {
        if (source == null || key == null || key == ItemKey.EMPTY) return false;
        int atSource = countItemAtEndpoint(source, key);
        if (atSource > 0) return true;
        int queuedToSource = countQueuedForEndpoint(source, key);
        return queuedToSource > 0;
    }

    public boolean dispatchInboundToManagerByGolems(EndpointRef source, ItemKey key, int amount, int queueId) {
        if (world == null || world.isRemote || key == null || key == ItemKey.EMPTY || amount <= 0) return false;
        LinkedHashMap<ItemKey, Integer> needs = new LinkedHashMap<ItemKey, Integer>();
        needs.put(key, amount);
        ensureDeliveryForExact(this.pos, needs, queueId);
        return countQueuedFor(this.pos, key.toStack(1)) > 0;
    }


    public boolean dispatchTransferTask(EndpointRef source, EndpointRef target, ItemKey key, int amount, int queueId) {
        if (world == null || world.isRemote || key == null || key == ItemKey.EMPTY || amount <= 0 || source == null || target == null) {
            return false;
        }

        BlockPos src = resolveEndpointPos(source);
        BlockPos dst = resolveEndpointPos(target);
        ItemStack like = key.toStack(1);

        /*
         * Сначала считаем, сколько вообще ещё реально нужно target.
         * Нельзя ни тянуть из source, ни отправлять в target больше этого остатка.
         */
        final int coveredAtTarget = countCoveredAtTarget(target, key, amount);
        final int remainingNeedAtTarget = Math.max(0, amount - coveredAtTarget);

        if (remainingNeedAtTarget <= 0) {
            return true;
        }

        /*
         * 1) Буфер менеджера -> конечная цель
         * Ничего со складов не подтягиваем, только ограниченный push по недостаче.
         */
        if (source.mode == EndpointRef.AccessMode.BUFFER && src.equals(this.pos)) {
            int canSend = Math.min(remainingNeedAtTarget, countInManagerBufferLike(like));
            if (canSend <= 0) {
                return countQueuedFor(dst, like) > 0;
            }

            int movedNow = pushFromBufferTo(dst, -1, like, canSend);
            return movedNow > 0 || countQueuedFor(dst, like) > 0;
        }

        /*
         * 2) Любой сценарий "... -> буфер менеджера"
         * Нужно покрыть только недостачу буфера, а не тянуть amount целиком.
         */
        if (target.mode == EndpointRef.AccessMode.BUFFER && dst.equals(this.pos)) {
            int coveredByManagerBuffer = countCoveredForManagerBuffer(key, amount);
            int remainingNeedForBuffer = Math.max(0, amount - coveredByManagerBuffer);

            if (remainingNeedForBuffer <= 0) {
                return true;
            }

            int pulled = 0;
            if (!src.equals(this.pos)) {
                if (source.mode == EndpointRef.AccessMode.OUTPUT && world.getTileEntity(src) instanceof TileEntityGolemCrafter) {
                    pulled = harvestLikeToBufferFromOutputCrafter(
                            (TileEntityGolemCrafter) world.getTileEntity(src),
                            like,
                            remainingNeedForBuffer
                    );
                } else {
                    pulled = harvestLikeToBufferFromHandler(src, -1, like, remainingNeedForBuffer);
                }
            } else {
                pulled = pullLikeFromProvideSetToBuffer(like, remainingNeedForBuffer);
            }

            int coveredAfterPull = countCoveredForManagerBuffer(key, amount);
            int stillNeed = Math.max(0, amount - coveredAfterPull);

            if (stillNeed > 0) {
                LinkedHashMap<ItemKey, Integer> needs = new LinkedHashMap<ItemKey, Integer>();
                needs.put(key, stillNeed);
                ensureDeliveryForExact(this.pos, needs, queueId);
            }

            return pulled > 0 || countQueuedFor(this.pos, like) > 0 || countCoveredForManagerBuffer(key, amount) >= amount;
        }

        /*
         * 3) Обычная доставка "... -> конечная цель"
         *
         * Критический фикс:
         * из source тянем не amount, а только столько, сколько реально ещё нужно цели,
         * за вычетом уже имеющегося в буфере менеджера.
         */
        int bufferedNow = countInManagerBufferLike(like);
        int needAfterBuffer = Math.max(0, remainingNeedAtTarget - bufferedNow);

        int pulled = 0;
        if (needAfterBuffer > 0) {
            if (!src.equals(this.pos)) {
                if (source.mode == EndpointRef.AccessMode.OUTPUT && world.getTileEntity(src) instanceof TileEntityGolemCrafter) {
                    pulled = harvestLikeToBufferFromOutputCrafter(
                            (TileEntityGolemCrafter) world.getTileEntity(src),
                            like,
                            needAfterBuffer
                    );
                } else {
                    pulled = harvestLikeToBufferFromHandler(src, -1, like, needAfterBuffer);
                }
            } else {
                pulled = pullLikeFromProvideSetToBuffer(like, needAfterBuffer);
            }
        }

        int bufferedAfterPull = countInManagerBufferLike(like);
        int canPushNow = Math.min(remainingNeedAtTarget, bufferedAfterPull);

        int movedNow = 0;
        if (canPushNow > 0) {
            movedNow = pushFromBufferTo(dst, -1, like, canPushNow);
        }

        int coveredAfterPush = countCoveredAtTarget(target, key, amount);
        int stillNeed = Math.max(0, amount - coveredAfterPush);

        if (stillNeed > 0) {
            LinkedHashMap<ItemKey, Integer> needs = new LinkedHashMap<ItemKey, Integer>();
            needs.put(key, stillNeed);
            ensureDeliveryForExact(dst, needs, queueId);
        }

        return pulled > 0 || movedNow > 0 || countQueuedFor(dst, like) > 0;
    }

    public int startCraftTask(BlockPos crafterPos, ItemKey key, int amount) {
        if (world == null || world.isRemote || crafterPos == null || key == null || key == ItemKey.EMPTY || amount <= 0) return 0;
        TileEntity te = world.getTileEntity(crafterPos);
        if (!(te instanceof ICraftEndpoint)) return 0;
        ICraftEndpoint endpoint = (ICraftEndpoint) te;
        int accepted = endpoint.startAssignedCraftTask(this.pos, crafterPos, -1, key.toStack(1), amount);
        return Math.max(0, accepted);
    }

    public int countItemAt(BlockPos pos, ItemKey key) {
        if (pos == null || key == null || key == ItemKey.EMPTY) return 0;
        return countAtDestLike(pos, -1, key.toStack(1));
    }

    private int countAtHandlerLike(@Nullable IItemHandler h, ItemStack like) {
        if (h == null || like == null || like.isEmpty()) return 0;
        int total = 0;
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack s = h.getStackInSlot(i);
            if (s.isEmpty()) continue;
            if (matchesForDelivery(s, like)) total += s.getCount();
        }
        return total;
    }

    private int countInManagerBufferLike(ItemStack like) {
        if (like == null || like.isEmpty()) return 0;

        int total = 0;
        for (int i = 0; i < buffer.getSlots(); i++) {
            ItemStack s = buffer.getStackInSlot(i);
            if (s.isEmpty()) continue;

            boolean match;
            if (like.getMaxStackSize() == 1) {
                match = matchesForDelivery(s, like);
            } else if (isCrystal(like) || isCrystal(s)) {
                match = isCrystal(like) && isCrystal(s) && crystalSame(s, like);
            } else {
                match = ResourceIdentity.sameResource(s, like);
            }

            if (match) {
                total += s.getCount();
            }
        }
        return total;
    }

    private int pullLikeFromProvideSetToBuffer(ItemStack like, int upTo) {
        if (world == null || world.isRemote || like == null || like.isEmpty() || upTo <= 0) return 0;
        int moved = 0;
        rebuildProvideSetFromSeals();
        for (TrackedInv ti : provideSet) {
            if (moved >= upTo) break;
            EnumFacing face = (ti.side >= 0 && ti.side < 6) ? EnumFacing.byIndex(ti.side) : null;
            IItemHandler ih = getSealExactInventory(world, ti.pos, face);
            if (ih == null) continue;
            for (int s = 0; s < ih.getSlots() && moved < upTo; s++) {
                ItemStack peek = ih.extractItem(s, Math.min(64, upTo - moved), true);
                if (peek.isEmpty() || !matchesForDelivery(peek, like)) continue;
                ItemStack taken = ih.extractItem(s, Math.min(peek.getCount(), upTo - moved), false);
                if (taken.isEmpty()) continue;
                ItemStack rest = ItemHandlerHelper.insertItem(buffer, taken, false);
                int accepted = taken.getCount() - (rest.isEmpty() ? 0 : rest.getCount());
                moved += accepted;
                if (!rest.isEmpty()) ih.insertItem(s, rest, false);
            }
        }
        if (moved > 0) markDirty();
        return moved;
    }

    private void transitionLineState(Line line, Line.State next, String reason) {
        if (line == null || next == null) return;
        Line.State prev = line.state;
        if (prev == next) return;
        line.state = next;
        line.lastStateTick = tickCounter;
        LOG.info("[Manager {}] LINE STATE {} -> {} reason={} wanted={} remaining={}",
                pos, prev, next, reason, line.wanted1, line.remaining);
    }

    private void processBatchHead(Batch b) {
        if (b == null) {
            LOG.warn("[Manager {}] processBatchHead called with null batch", pos);
            return;
        }

        LOG.info("[Manager {}] processBatchHead enter queue={} kind={} dest={} destSide={} lines={} index={}  seenTick={} tickCounter={}",
                pos,
                activeQueue,
                b.kind,
                b.dest,
                b.destSide,
                b.lines == null ? -1 : b.lines.size(),
                b.index,
                b.seenTick,
                tickCounter);

        if (b.seenTick == tickCounter) {
            LOG.debug("[Manager {}] processBatchHead skip same tick queue={} dest={}",
                    pos, activeQueue, b.dest);
            return;
        }
        b.seenTick = tickCounter;

        if (b.lines.isEmpty()) {
            LOG.info("[Manager {}] processBatchHead pop empty batch queue={} dest={}",
                    pos, activeQueue, b.dest);
            popBatch(b);
            return;
        }
        if (!hasItemCapAt(b.dest, b.destSide)) {
            for (Line ln : b.lines) {
                transitionLineState(ln, Line.State.FAILED, "destination-cap-missing");
            }
            LOG.warn("[Manager {}] processBatchHead pop batch: no item capability at dest={} side={} queue={}",
                    pos, b.dest, b.destSide, activeQueue);
            popBatch(b);
            return;
        }

        focusMirrorForConsumer(b.dest);

        if (b.index >= b.lines.size()) {
            b.index = 0;
        }

        int parallelLimit = 1;
        if (b.kind == Batch.Kind.DELIVERY && !boundDispatchers.isEmpty()) {
            parallelLimit = Math.max(1, Math.min(getDispatcherParallelLimit(), b.lines.size()));
        }

        boolean hasDispatchers = !boundDispatchers.isEmpty();
        boolean sequentialDelivery = (b.kind == Batch.Kind.DELIVERY && !hasDispatchers);

        int dispatcherBudget = (b.kind == Batch.Kind.DELIVERY && hasDispatchers)
                ? Math.max(0, getAvailableDispatcherGolems())
                : Integer.MAX_VALUE;

        int touchedIncomplete = 0;
        int index = b.lines.isEmpty() ? 0 : Math.max(0, Math.min(b.index, b.lines.size() - 1));
        int startIndex = index;

        while (index < b.lines.size()) {
            Line ln = b.lines.get(index);
            int remainingBefore = ln.remaining;

            LOG.info("[Manager {}] processBatchHead line queue={} idx={} wanted={} remaining={} requester={} dest={} kind={}",
                    pos,
                    activeQueue,
                    index,
                    ln.wanted1,
                    ln.remaining,
                    ln.requester,
                    b.dest,
                    b.kind);

            if (ln.remaining <= 0) {
                transitionLineState(ln, Line.State.DELIVERED, "empty-remaining");
            } else if (ln.state == Line.State.PENDING) {
                transitionLineState(ln, Line.State.DISPATCHED, "line-selected");
            }

            if (ln.remaining == ln.lastRemainingSnapshot
                    && (ln.state == Line.State.DISPATCHED || ln.state == Line.State.IN_FLIGHT)
                    && ln.lastStateTick >= 0
                    && (tickCounter - ln.lastStateTick) > 100) {
                transitionLineState(ln, Line.State.STALLED, "no-progress");
            }

            LineProcessResult result;
            if (b.kind == Batch.Kind.DELIVERY) {
                int dispatcherCapForLine = Integer.MAX_VALUE;
                if (hasDispatchers) {
                    if (dispatcherBudget <= 0) {
                        dispatcherCapForLine = 0;
                    } else {
                        int linesRemaining = Math.max(1, b.lines.size() - index);
                        dispatcherCapForLine = (int) Math.ceil(dispatcherBudget / (double) linesRemaining);
                        dispatcherCapForLine = Math.max(1, dispatcherCapForLine);
                        dispatcherCapForLine = Math.min(dispatcherCapForLine, dispatcherBudget);
                    }
                }
                result = processOneDeliveryLine(b, ln, dispatcherBudget, dispatcherCapForLine);
            } else {
                result = LineProcessResult.of(processOneCraftLine(b, ln));
            }

            LOG.info("[Manager {}] processBatchHead line result queue={} idx={} wanted={} done={} dispatcherUsed={} remainingAfter={}",
                    pos,
                    activeQueue,
                    index,
                    ln.wanted1,
                    result.done,
                    result.dispatcherSlotsUsed,
                    ln.remaining);

            if (result.done) {
                transitionLineState(ln, Line.State.DELIVERED, "line-done");
            } else if (ln.remaining < remainingBefore) {
                transitionLineState(ln, Line.State.IN_FLIGHT, "remaining-decreased");
            } else if (ln.state == Line.State.PENDING) {
                transitionLineState(ln, Line.State.DISPATCHED, "dispatch-attempted");
            }

            ln.lastRemainingSnapshot = ln.remaining;

            if (result.dispatcherSlotsUsed > 0 && dispatcherBudget != Integer.MAX_VALUE) {
                dispatcherBudget = Math.max(0, dispatcherBudget - result.dispatcherSlotsUsed);
            }

            if (result.done) {
                if (ln.reserved > 0) {
                    releaseDispatcherGolems(ln.reserved);
                    ln.reserved = 0;
                }
                if (!b.lines.isEmpty()) {
                    if (index < b.lines.size() && b.lines.get(index) == ln) {
                        b.lines.remove(index);
                    } else {
                        b.lines.remove(ln);
                    }
                }
                markDirty();
                if (sequentialDelivery) {
                    continue;
                }
                continue;
            }

            touchedIncomplete++;
            if (sequentialDelivery) {
                break;
            }
            if (touchedIncomplete >= parallelLimit) {
                index++;
                break;
            }

            index++;
        }

        if (b.lines.isEmpty()) {
            LOG.info("[Manager {}] processBatchHead pop finished batch queue={} dest={}",
                    pos, activeQueue, b.dest);
            popBatch(b);
            return;
        }

        if (sequentialDelivery) {
            if (startIndex >= b.lines.size()) {
                b.index = b.lines.size() - 1;
            } else {
                b.index = Math.max(0, Math.min(startIndex, b.lines.size() - 1));
            }
        } else if (index >= b.lines.size()) {
            b.index = 0;
        } else {
            b.index = Math.max(0, index);
        }

        LOG.info("[Manager {}] processBatchHead exit queue={} nextIndex={} linesRemaining={} dest={}",
                pos, activeQueue, b.index, b.lines.size(), b.dest);
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
        processMirrorItemsInBuffer();
        tickMirrorEjects();

        for (int i = 0; i < REQUEST_BUDGET; i++) {
            Batch b = peekNextBatch();
            if (b == null) break;
            processBatchHead(b);
            activeQueue = (activeQueue + 1) % 6;
        }

        logistics.tick(this);

        tickConsumerFocus();

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
        List<BlockPos> deadPlanner = new ArrayList<>();

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
            if (!(te instanceof TileOrderTerminal)) {
                deadTerm.add(tp);
                continue;
            }
            BlockPos mp = ((TileOrderTerminal) te).getManagerPos();
            if (mp == null || !mp.equals(this.pos)) {
                deadTerm.add(tp);
            }
        }

        for (BlockPos pp : boundPlanners) {
            TileEntity te = world.getTileEntity(pp);
            if (!(te instanceof TileSequentialCraftPlanner)) {
                deadPlanner.add(pp);
                continue;
            }
            BlockPos mp = ((TileSequentialCraftPlanner) te).getManagerPos();
            if (mp == null || !mp.equals(this.pos)) {
                deadPlanner.add(pp);
            }
        }

        if (deadReq.isEmpty() && deadTerm.isEmpty() && deadPlanner.isEmpty()) return;

        // Снимаем из наборов
        boundRequesters.removeAll(deadReq);
        boundTerminals.removeAll(deadTerm);
        boundPlanners.removeAll(deadPlanner);

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
            for (BlockPos pp : deadPlanner) {
                TileEntity te = world.getTileEntity(pp);
                if (te instanceof TileSequentialCraftPlanner) {
                    ((TileSequentialCraftPlanner) te).clearManagerPosFromManager(this.pos);
                }
            }
        } finally {
            isUnlinking = false;
        }

        markDirtyAndSync();
    }

    @Nullable
    public UUID findFreeDispatcherGolemUUID(@Nullable java.util.concurrent.ConcurrentHashMap<Integer, Task> tasks) {
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
                    if (b == therealpant.thaumicattempts.golemcraft.ModBlocksItems.MIRROR_STABILIZER) stabsAll.add(q);
                    else if (b == therealpant.thaumicattempts.golemcraft.ModBlocksItems.MATH_CORE) coresAll.add(q);
                }

        // --- Волновая активация от блока прямо под менеджером ---
        java.util.Set<BlockPos> stabsActive = new java.util.HashSet<>();
        java.util.Set<BlockPos> coresActive = new java.util.HashSet<>();

        BlockPos seed = p.down(); // «блок, верхней гранью касающийся менеджера» — ровно под ним
        net.minecraft.block.Block seedBlock = world.getBlockState(seed).getBlock();
        boolean seedIsStab = seedBlock == therealpant.thaumicattempts.golemcraft.ModBlocksItems.MIRROR_STABILIZER && stabsAll.contains(seed);
        boolean seedIsCore = seedBlock == therealpant.thaumicattempts.golemcraft.ModBlocksItems.MATH_CORE && coresAll.contains(seed);


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
                if (st.getBlock() == therealpant.thaumicattempts.golemcraft.ModBlocksItems.MIRROR_STABILIZER) {
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
                if (st.getBlock() == therealpant.thaumicattempts.golemcraft.ModBlocksItems.MATH_CORE) {
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

                    if (b == therealpant.thaumicattempts.golemcraft.ModBlocksItems.MIRROR_STABILIZER) {
                        if (st.getValue(therealpant.thaumicattempts.golemnet.block.BlockMirrorStabilizer.ACTIVE)) {
                            world.setBlockState(q, st.withProperty(
                                    therealpant.thaumicattempts.golemnet.block.BlockMirrorStabilizer.ACTIVE, false), 3);
                        }
                    } else if (b == therealpant.thaumicattempts.golemcraft.ModBlocksItems.MATH_CORE) {
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
        List<BlockPos> dropDisp = new ArrayList<>();
        List<BlockPos> dropPlanner = new ArrayList<>();

        // 1) радиус
        for (BlockPos bp : boundRequesters) if (!inRange.test(bp)) dropReq.add(bp);
        for (BlockPos bp : boundTerminals) if (!inRange.test(bp)) dropTerm.add(bp);
        for (BlockPos bp : boundDispatchers.keySet()) if (!inRange.test(bp)) dropDisp.add(bp);

        // 2) вычисл. кап
        int overCompute = getComputeUsed() - calcComputeCap;
        if (overCompute > 0) {
            Iterator<BlockPos> itP = boundPlanners.iterator();
            while (overCompute > 0 && itP.hasNext()) {
                dropPlanner.add(itP.next());
                overCompute--;
            }
            Iterator<BlockPos> it = boundRequesters.iterator();
            while (overCompute > 0 && it.hasNext()) {
                dropReq.add(it.next());
                overCompute--;
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

        if (dropReq.isEmpty() && dropTerm.isEmpty() && dropDisp.isEmpty() && dropPlanner.isEmpty()) return;

        // Уникализируем
        java.util.Set<BlockPos> uniqReq = new java.util.LinkedHashSet<>(dropReq);
        java.util.Set<BlockPos> uniqTerm = new java.util.LinkedHashSet<>(dropTerm);
        java.util.Set<BlockPos> uniqDisp = new java.util.LinkedHashSet<>(dropDisp);
        java.util.Set<BlockPos> uniqPlanner = new java.util.LinkedHashSet<>(dropPlanner);

        boundRequesters.removeAll(uniqReq);
        boundTerminals.removeAll(uniqTerm);
        boundDispatchers.keySet().removeAll(uniqDisp);
        boundPlanners.removeAll(uniqPlanner);
        if (!uniqDisp.isEmpty()) dispatcherBusyQueue.removeIf(uniqDisp::contains);

        // «Тихо» уведомляем
        isUnlinking = true;
        try {
            for (BlockPos rp : uniqReq) unlinkRequesterSideEffects(rp);
            for (BlockPos tp : uniqTerm) unlinkTerminalSideEffects(tp);
            for (BlockPos dp : uniqDisp) unlinkDispatcherSideEffects(dp);
            for (BlockPos pp : uniqPlanner) unlinkPlannerSideEffects(pp);
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
            for (BlockPos dp : new java.util.ArrayList<>(boundDispatchers.keySet())) {
                unlinkDispatcherSideEffects(dp);
            }
            for (BlockPos pp : new java.util.ArrayList<>(boundPlanners)) {
                unlinkPlannerSideEffects(pp);
            }
            boundRequesters.clear();
            boundTerminals.clear();
            boundDispatchers.clear();
            boundPlanners.clear();
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
            boundPlanners.remove(consumerPos);
            markDirty();
            return;
        }

        isUnlinking = true;
        try {
            boolean wasReq = boundRequesters.remove(consumerPos);
            boolean wasTerm = boundTerminals.remove(consumerPos);
            boolean wasPlanner = boundPlanners.remove(consumerPos);
            DispatcherStats removedDisp = boundDispatchers.remove(consumerPos);
            boolean wasDisp = removedDisp != null;
            if (wasDisp) {
                dispatcherBusyQueue.removeIf(bp -> bp.equals(consumerPos));
            }

            if (wasReq) unlinkRequesterSideEffects(consumerPos);
            if (wasTerm) unlinkTerminalSideEffects(consumerPos);
            if (wasDisp) unlinkDispatcherSideEffects(consumerPos);
            if (wasPlanner) unlinkPlannerSideEffects(consumerPos);

            markDirty();
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        } finally {
            isUnlinking = false;
        }
    }


    public void cancelAllForDestination(BlockPos dst) {
        for (Deque<Batch> q : batchQueues) q.removeIf(b -> b.dest.equals(dst));
        markDirty();
    }

    /* ===================== Подсобки ===================== */

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


    private int countQueuedFor(BlockPos dest, ItemStack like) {
        if (dest == null || like == null || like.isEmpty()) return 0;
        int total = 0;

        for (Deque<Batch> q : batchQueues)
            for (Batch b : q) {
                if (!dest.equals(b.dest)) continue;
                for (int i = b.index; i < b.lines.size(); i++) {
                    Line ln = b.lines.get(i);

                    boolean match;
                    if (like.getMaxStackSize() == 1) {
                        match = matchesForDelivery(ln.wanted1, like);
                    } else if (isCrystal(like) || isCrystal(ln.wanted1)) {
                        match = isCrystal(like) && isCrystal(ln.wanted1) && crystalSame(ln.wanted1, like);
                    } else {
                        match = ResourceIdentity.sameResource(ln.wanted1, like);
                    }

                    if (match) total += Math.max(0, ln.remaining);
                }
            }
        return total;
    }

    private void trimQueuedFor(BlockPos dest, ItemStack like, int amount) {
        if (dest == null || like == null || like.isEmpty() || amount <= 0) return;
        int left = amount;
        for (Deque<Batch> q : batchQueues) {
            for (Iterator<Batch> itB = q.iterator(); itB.hasNext() && left > 0; ) {
                Batch b = itB.next();
                if (b.kind != Batch.Kind.DELIVERY) continue;
                if (!dest.equals(b.dest)) continue;

                for (Iterator<Line> itL = b.lines.iterator(); itL.hasNext() && left > 0; ) {
                    Line ln = itL.next();

                    boolean match;
                    if (like.getMaxStackSize() == 1) {
                        match = matchesForDelivery(ln.wanted1, like);
                    } else if (isCrystal(like) || isCrystal(ln.wanted1)) {
                        match = isCrystal(like) && isCrystal(ln.wanted1) && crystalSame(ln.wanted1, like);
                    } else {
                        match = ResourceIdentity.sameResource(ln.wanted1, like);
                    }

                    if (!match) continue;

                    int take = Math.min(left, ln.remaining);
                    ln.remaining -= take;

                    if (ln.remaining <= 0) {
                        if (ln.reserved > 0) {
                            releaseDispatcherGolems(ln.reserved);
                            ln.reserved = 0;
                        }
                        itL.remove();
                    }
                }
                if (b.lines.isEmpty()) itB.remove();
            }
        }
        if (amount != left) markDirty();
    }

    public void reconcileForDelivery(BlockPos dest, ItemStack like, int wantNow) {
        if (world == null || dest == null || like == null || like.isEmpty()) return;

        int queued = countQueuedFor(dest, like);
        int missing = wantNow - queued;

        if (missing < 0) {
            trimQueuedFor(dest, like, -missing);
        } else if (missing > 0) {
            int pushed = pushFromBufferTo(dest, -1, like, missing);
            int need = missing - pushed;
            if (need > 0) {
                List<Map.Entry<ItemKey, Integer>> lst = new ArrayList<>(1);
                lst.add(new AbstractMap.SimpleEntry<>(ItemKey.of(like), need));
                enqueueBatchDelivery(dest, -1, 0, lst);
            }
        }
    }

    public void ensureDeliveryFor(BlockPos dest, Map<ItemKey, Integer> needs, int queueId) {
        if (world == null || world.isRemote || dest == null || needs == null || needs.isEmpty()) return;

        focusMirrorForDeliveryTarget(dest);

        List<Map.Entry<ItemKey, Integer>> miss = new ArrayList<>();

        for (Map.Entry<ItemKey, Integer> e : needs.entrySet()) {
            int want = Math.max(1, e.getValue());
            ItemStack like = e.getKey().toStack(1);

            int atDest = countAtDestLike(dest, -1, like);
            int queued = countQueuedFor(dest, like);
            int missing = want - (atDest + queued);
            if (missing <= 0) continue;

            int pushedNow = pushFromBufferTo(dest, -1, like, missing);
            missing -= pushedNow;

            if (missing > 0) {
                miss.add(new AbstractMap.SimpleImmutableEntry<>(e.getKey(), missing));
            }
        }

        if (!miss.isEmpty()) {
            int q = Math.max(0, Math.min(5, queueId));
            enqueueBatchDelivery(dest, -1, q, miss);
            activeQueue = q;
        }
    }

    public void ensureDeliveryForExact(BlockPos dest, Map<ItemKey, Integer> needs, int queueId) {
        if (world == null || world.isRemote || dest == null || needs == null || needs.isEmpty()) return;

        focusMirrorForDeliveryTarget(dest);

        List<Map.Entry<ItemKey, Integer>> miss = new ArrayList<>();

        for (Map.Entry<ItemKey, Integer> e : needs.entrySet()) {
            int want = Math.max(1, e.getValue());
            ItemStack like = e.getKey().toStack(1);

            int queued = countQueuedFor(dest, like);
            int missing = want - queued;
            if (missing <= 0) continue;

            int pushedNow = pushFromBufferTo(dest, -1, like, missing);
            missing -= pushedNow;

            if (missing > 0) {
                miss.add(new AbstractMap.SimpleImmutableEntry<>(e.getKey(), missing));
            }
        }

        if (!miss.isEmpty()) {
            int q = Math.max(0, Math.min(5, queueId));
            enqueueBatchDelivery(dest, -1, q, miss);
            activeQueue = q;
        }
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
        int net = stab - instab;

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

    public void ensureDeliveryFor(BlockPos dest, Map<ItemKey, Integer> needs) {
        ensureDeliveryFor(dest, needs, 0);
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
        NBTTagList pl = new NBTTagList();
        for (BlockPos bp : boundPlanners) pl.appendTag(new NBTTagLong(bp.toLong()));
        nbt.setTag(TAG_PLANNERS, pl);

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

        NBTTagList co = new NBTTagList();
        for (Map.Entry<BlockPos, Integer> entry : consumerActiveOrders.entrySet()) {
            NBTTagCompound c = new NBTTagCompound();
            c.setLong("pos", entry.getKey().toLong());
            c.setInteger("count", entry.getValue());
            co.appendTag(c);
        }
        nbt.setTag(TAG_CONSUMER_ORDERS, co);

        NBTTagList cf = new NBTTagList();
        for (Map.Entry<BlockPos, Long> entry : consumerFocusCooldowns.entrySet()) {
            NBTTagCompound c = new NBTTagCompound();
            c.setLong("pos", entry.getKey().toLong());
            c.setLong("until", entry.getValue());
            cf.appendTag(c);
        }
        nbt.setTag(TAG_CONSUMER_FOCUS_GRACE, cf);

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
        NBTTagCompound logisticsTag = new NBTTagCompound();
        logistics.writeToNbt(logisticsTag);
        nbt.setTag(TAG_LOGISTICS, logisticsTag);

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
        boundPlanners.clear();
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
        if (nbt.hasKey(TAG_PLANNERS, 9)) {
            NBTTagList pl = nbt.getTagList(TAG_PLANNERS, 4);
            for (int i = 0; i < pl.tagCount(); i++)
                boundPlanners.add(BlockPos.fromLong(((NBTTagLong) pl.get(i)).getLong()));
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

        consumerActiveOrders.clear();
        if (nbt.hasKey(TAG_CONSUMER_ORDERS, 9)) {
            NBTTagList co = nbt.getTagList(TAG_CONSUMER_ORDERS, 10);
            for (int i = 0; i < co.tagCount(); i++) {
                NBTTagCompound c = co.getCompoundTagAt(i);
                if (!c.hasKey("pos")) continue;
                BlockPos cp = BlockPos.fromLong(c.getLong("pos"));
                int cnt = Math.max(0, c.getInteger("count"));
                if (cnt > 0) consumerActiveOrders.put(cp.toImmutable(), cnt);
            }
        }

        consumerFocusCooldowns.clear();
        if (nbt.hasKey(TAG_CONSUMER_FOCUS_GRACE, 9)) {
            NBTTagList cf = nbt.getTagList(TAG_CONSUMER_FOCUS_GRACE, 10);
            for (int i = 0; i < cf.tagCount(); i++) {
                NBTTagCompound c = cf.getCompoundTagAt(i);
                if (!c.hasKey("pos")) continue;
                BlockPos cp = BlockPos.fromLong(c.getLong("pos"));
                consumerFocusCooldowns.put(cp.toImmutable(), c.getLong("until"));
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
                if (te instanceof ICraftEndpoint) {
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
        if (nbt.hasKey(TAG_LOGISTICS, 10)) {
            logistics.readFromNbt(nbt.getCompoundTag(TAG_LOGISTICS));
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
        return ResourceIdentity.sameResource(a, like);
    }

    private int countAtDestLike(BlockPos destPos, int destSide, ItemStack like) {
        IItemHandler h = getDestHandler(destPos, destSide);
        if (h == null || like == null || like.isEmpty()) return 0;

        int total = 0;
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack s = h.getStackInSlot(i);
            if (s.isEmpty()) continue;

            if (like.getMaxStackSize() == 1) {
                if (matchesForDelivery(s, like)) total += s.getCount();
            } else if (isCrystal(like)) {
                if (isCrystal(s) && crystalSame(s, like)) total += s.getCount();
            } else {
                if (matchesForDelivery(s, like)) total += s.getCount();
            }
        }
        return total;
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
        if (world == null) return out;

        LinkedHashMap<ItemKey, ItemStack> uniq = new LinkedHashMap<>();

        for (BlockPos rp : getRequestersSnapshot()) {
            TileEntity te = world.getTileEntity(rp);
            if (!(te instanceof ICraftEndpoint)) continue;

            ICraftEndpoint ep = (ICraftEndpoint) te;
            List<ItemStack> lst = ep.listCraftableResults();
            if (lst == null || lst.isEmpty()) continue;

            for (ItemStack s : lst) {
                if (s == null || s.isEmpty()) continue;
                ItemKey k = ItemKey.of(s);
                if (k == null || k == ItemKey.EMPTY) continue;

                ItemStack one = s.copy();
                if (one.getCount() <= 0) one.setCount(1);

                if (!uniq.containsKey(k)) {
                    uniq.put(k, one);
                }
            }
        }

        out.addAll(uniq.values());
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

    public int countBuffered(ItemKey key) {
        if (key == null || key == ItemKey.EMPTY) return 0;
        return countInBufferLike(key.toStack(1));
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
        if (pos == null) return;
        BlockPos p = pos.toImmutable();
        if (requesters.add(p)) {
            logistics.refreshRecipeIndex(this);
            markDirty();
        }
    }

    public void unregisterRequester(BlockPos pos) {
        if (pos == null) return;
        if (requesters.remove(pos)) {
            logistics.refreshRecipeIndex(this);
            markDirty();
        }
    }

    public void refreshRecipeIndexFromPlanner() {
        logistics.refreshRecipeIndex(this);
    }

    public boolean isLogisticsHealthy() {
        return logistics != null;
    }

    boolean isConsumerBound(BlockPos pos) {
        if (pos == null) return false;
        return boundTerminals.contains(pos) || boundRequesters.contains(pos);
    }

    /* ===================== NBT owner allow (оставлено как было) ===================== */
    public boolean isOwnerAllowed(String owner) {
        return allowedOwners.isEmpty() || (owner != null && allowedOwners.contains(owner));
    }

    @Nullable
    private BlockPos findRequesterForKey(ItemKey key) {
        if (world == null) return null;
        ItemStack like = key.toStack(1);
        for (BlockPos rp : requesters) {
            TileEntity te = world.getTileEntity(rp);
            if (!(te instanceof ICraftEndpoint)) continue;
            ICraftEndpoint endpoint = (ICraftEndpoint) te;
            if (!CraftOrderApi.isCrafter(endpoint)) continue;
            for (ItemStack out : endpoint.listCraftableResults()) {
                if (out == null || out.isEmpty()) continue;
                boolean same = ResourceIdentity.sameResource(out, like);
                if (same) return rp;
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
        // Верните копию своего внутреннего множества/списка реквестеров
        return new java.util.HashSet<>(this.requesters); // <-- замените this.requesters на ваши данные
    }

    public java.util.Set<net.minecraft.util.math.BlockPos> getPlannersSnapshot() {
        return new java.util.HashSet<>(this.boundPlanners);
    }

    public java.util.List<TileEntity> getOperationProviderTiles() {
        java.util.ArrayList<TileEntity> out = new java.util.ArrayList<>();
        if (world == null) return out;
        for (BlockPos rp : requesters) {
            TileEntity te = world.getTileEntity(rp);
            if (te != null && !te.isInvalid()) out.add(te);
        }
        return out;
    }
}
