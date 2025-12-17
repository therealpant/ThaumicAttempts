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
import thaumcraft.api.ThaumcraftInvHelper;
import thaumcraft.api.golems.GolemHelper;
import thaumcraft.api.golems.tasks.Task;
import thaumcraft.common.golems.seals.SealEntity;
import thaumcraft.common.golems.seals.SealHandler;
import thaumcraft.common.golems.seals.SealProvide;
import therealpant.thaumicattempts.api.ICraftEndpoint;
import therealpant.thaumicattempts.api.ITerminalOrderIconProvider;
import therealpant.thaumicattempts.api.CraftOrderApi;
import therealpant.thaumicattempts.golemcraft.item.ItemResourceList;
import therealpant.thaumicattempts.golemcraft.tile.TileEntityGolemCrafter;
import therealpant.thaumicattempts.golemnet.net.msg.S2CFlyAnim;
import therealpant.thaumicattempts.golemnet.tile.TileInfusionRequester;
import therealpant.thaumicattempts.integration.TcLogisticsCompat;
import therealpant.thaumicattempts.util.ThaumcraftProvisionHelper;

import therealpant.thaumicattempts.util.ItemKey;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.Entity;
import thaumcraft.common.golems.EntityThaumcraftGolem;
import therealpant.thaumicattempts.util.ThaumcraftProvisionHelper;

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

    /* ===================== Голем-логистика ===================== */

    private static final int REQ_DEBOUNCE_TICKS = 5;
    private static final int STALL_TICKS = 40;  // 2с
    private static final int REQUEST_BUDGET = 4;
    private static final int MAX_REQ_PER_TICK = 64;
    private static final int AUTO_SCAN_PERIOD_TICKS = 100;
    private static final int PROVIDER_SCAN_RADIUS = 16;
    private static final int PROVIDER_RESCAN_TICKS = 200;

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

    private static final String TAG_MIRRORS = "mirrors";
    private static final String TAG_RENDER_SEED = "renderSeed";
    private static final String TAG_PEND_EJECTS = "pendEjects";
    private static final String TAG_DISPATCHERS = "boundDispatchers";
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

        public MirrorSlot(int r, int s, long p) {
            ring = r;
            slot = s;
            phase = p;
            focusUntil = 0L;
        }

        public boolean focus(long now, int durationTicks) {
            long target = now + durationTicks;
            long remaining = focusUntil - now;
            if ((focusUntil < now || remaining < (durationTicks / 4)) && target > focusUntil) {
                focusUntil = target;
                return true;
            }
            return false;
        }
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

    /**
     * Выбрать свободное зеркало для нового потребителя.
     * Берём один из activeMirrors, который ещё никому не принадлежит.
     */
    @Nullable
    private MirrorKey assignMirrorForConsumer(BlockPos consumerPos) {
        if (consumerPos == null) return null;
        // уже есть – возвращаем существующее
        MirrorKey existing = consumerMirrors.get(consumerPos);
        if (existing != null && findMirrorSlot(existing) != null) {
            return existing;
        }

        // ищем свободный слот среди активных зеркал
        for (MirrorSlot m : activeMirrors) {
            if (!isMirrorTakenByConsumer(m.ring, m.slot)) {
                MirrorKey mk = new MirrorKey(m.ring, m.slot);
                consumerMirrors.put(consumerPos.toImmutable(), mk);
                return mk;
            }
        }
        // не нашли свободного – значит, по идее, зеркал просто не хватает
        return null;
    }

    /**
     * Получить или назначить зеркало для данного ресурса (ItemKey).
     * Всегда старается вернуть существующее зеркало. Если привязанное
     * зеркало исчезло – выбирает новое.
     */

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
        // Используем уже существующую тихую вставку в buffer,
        // которая триггерит onItemAccepted/onArrivedToBuffer и корректно обновляет inflight.
        try {
            // если есть suppressAcceptAnim — можно включить, чтобы не спамить лишними эффектами
            return silentInsertToBuffer(stack);
        } catch (Throwable ignored) {
            // на всякий пожарный — чтобы не крашило мир
            return stack;
        }
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
        if (boundTerminals.contains(destPos) || boundRequesters.contains(destPos)) return destPos;

        if (world != null) {
            TileEntity te = world.getTileEntity(destPos);
            if (te instanceof TileEntityGolemCrafter) {
                BlockPos up = destPos.up();
                if (boundTerminals.contains(up) || boundRequesters.contains(up)) {
                    return up;
                }
            }
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

    private void unlinkTerminalSideEffects(BlockPos terminalPos) {
        if (world == null || world.isRemote || terminalPos == null) return;
        TileEntity te = world.getTileEntity(terminalPos);
        if (te instanceof TileOrderTerminal) {
            ((TileOrderTerminal) te).clearManagerPosFromManager(this.pos); // «тихо»
            cancelAllForDestination(terminalPos); // подчистить очереди
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
            ItemStack before = stack.copy();
            ItemStack res = super.insertItem(slot, stack, simulate);
            if (!simulate) {
                int accepted = before.getCount() - (res.isEmpty() ? 0 : res.getCount());
                if (accepted > 0 && !suppressAcceptAnim) {
                    ItemStack one = before.copy();
                    one.setCount(1);
                    onItemAccepted(one);
                }
            }
            return res;
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


    // сервер: выбранное зеркало + рассылка клиентам для анимации «полетело в корону»
    // сервер: выбранное зеркало + рассылка клиентам для анимации «полетело в корону»
    private void onItemAccepted(ItemStack stack) {
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
            if (ItemHandlerHelper.canItemStacksStackRelaxed(e.getKey().toStack(1), arrived)) {
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
                        match = ItemHandlerHelper.canItemStacksStackRelaxed(ln.wanted1, s);
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

        Line(ItemStack like1, int amount) {
            this.wanted1 = like1.copy();
            this.wanted1.setCount(1);
            this.remaining = Math.max(1, amount);
            this.reserved = 0;
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
                        : ItemHandlerHelper.canItemStacksStackRelaxed(peek, like);
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
                                : ItemHandlerHelper.canItemStacksStackRelaxed(ln.wanted1, like1));
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
            if (rp == null) continue;

            dropDupDelivery.accept(dest, like1);

            TileEntity rte = world.getTileEntity(rp);

            if (rte instanceof ICraftEndpoint && CraftOrderApi.isCrafter((ICraftEndpoint) rte)
                    && rte instanceof TileInfusionRequester) {
                TileInfusionRequester inf = (TileInfusionRequester) rte;

                int accepted = inf.enqueueCrafterOrder(this.pos, dest, destSide, like1, amount);
                if (accepted > 0) {
                    dropDupDelivery.accept(dest, like1);
                }
                continue;
            }

            // === 2) Обычный PatternRequester + GolemCrafter как было раньше ===
            Line ln = new Line(like1, amount);
            ln.requester = rp;

            if (rte instanceof TilePatternRequester) {
                TilePatternRequester rq = (TilePatternRequester) rte;

                int outPerCraft = Math.max(1, rq.getPerCraftOutputCountFor(like1));
                int craftsCount = (amount + outPerCraft - 1) / outPerCraft;

                List<ItemStack> needList = rq.getRecipeInputsFor(like1, craftsCount);
                if (needList != null && !needList.isEmpty()) {
                    BlockPos crafterPos = rp.down();
                    LinkedHashMap<ItemKey, Integer> needs = provisioning.computeIfAbsent(
                            crafterPos, x -> new LinkedHashMap<>());
                    Map<ItemKey, Integer> available = availablePerCrafter.computeIfAbsent(
                            crafterPos, x -> new HashMap<>());

                    for (ItemStack need : needList) {
                        if (need == null || need.isEmpty()) continue;
                        ItemKey key = ItemKey.of(need);
                        ItemStack likeNeed = key.toStack(1);

                        int have = available.computeIfAbsent(key, k ->
                                countAtDestLike(crafterPos, -1, likeNeed)
                                        + countQueuedFor(crafterPos, likeNeed)
                                        + countInBufferLike(likeNeed));

                        int missing = Math.max(0, need.getCount() - have);
                        if (missing > 0) {
                            needs.merge(key, missing, Integer::sum);
                        }

                        int leftover = Math.max(0, have - need.getCount());
                        available.put(key, leftover);
                    }
                }

                ln.craftsExpected = craftsCount;
                ln.perCraftOut = outPerCraft;
            }

            // сам крафт-батч (только для обычных крафтеров)
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
            if (te instanceof TileOrderTerminal) ((TileOrderTerminal) te).onDelivered(like, moved);
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
                    : ItemHandlerHelper.canItemStacksStackRelaxed(s, like);
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
            ln.remaining -= pushed0;
            if (ln.reserved > 0) ln.reserved = Math.max(0, ln.reserved - pushed0);
            lastProgressTick.put(key, tickCounter);
            if (ln.remaining <= 0) return new LineProcessResult(true, 0);
        }

        boolean toCrafter = world.getTileEntity(b.dest) instanceof TileEntityGolemCrafter;

        int flying = inflight.getOrDefault(key, 0);
        int atDest = countAtDestLike(b.dest, b.destSide, ln.wanted1);
        int queuedAll = countQueuedFor(b.dest, ln.wanted1);
        int queuedOthers = Math.max(0, queuedAll - ln.remaining);

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
            int reservedHere = Math.max(0, Math.min(ln.reserved, ln.remaining));
            int covered = atDest + queuedOthers + reservedHere;
            need = (covered >= ln.remaining) ? 0 : (ln.remaining - covered);
        } else {
            int lp = lastProgressTick.getOrDefault(key, -9999);
            if (tickCounter - lp > STALL_TICKS) {
                inflight.put(key, 0);
                flying = 0;
                int reservedBefore = ln.reserved;
                ln.reserved = 0;
                releaseDispatcherGolems(reservedBefore);
                releaseDispatcherGolem();
            }
            int reservedHere = Math.max(0, Math.min(ln.reserved, ln.remaining));
            int accounted = Math.max(flying, reservedHere);
            need = Math.max(0, ln.remaining - accounted);
        }

        int reservationsCommitted = 0;

        if (need > 0) {
            int lt = lastReqTick.getOrDefault(key, -9999);
            if (tickCounter - lt >= REQ_DEBOUNCE_TICKS) {

                boolean hasDispatchers = !boundDispatchers.isEmpty();

                int budget = Math.min(MAX_REQ_PER_TICK, need);
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
                    int chunk = Math.min(ln.wanted1.getMaxStackSize(), budget);
                    if (chunk > 0) {
                        ItemStack req = normalizeForProvision(ln.wanted1, chunk);
                        if (!req.isEmpty()) {
                            boolean ok = ThaumcraftProvisionHelper.requestProvisioningForManager(this, req);
                            if (ok) {
                                requested += chunk;
                            }
                        }
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
            ln.remaining -= pushed1;
            if (ln.reserved > 0) ln.reserved = Math.max(0, ln.reserved - pushed1);
            lastProgressTick.put(key, tickCounter);
            if (ln.remaining <= 0) return new LineProcessResult(true, reservationsCommitted);
        }

        return new LineProcessResult(false, reservationsCommitted);

    }

    private boolean processOneCraftLine(Batch b, Line ln) {
        if (ln.remaining <= 0) return true;
        if (ln.requester == null) return true;

        final BlockPos rp = ln.requester;
        harvestAnyFromCrafterOutput(rp);
        final BlockPos crafterPos = rp.down();
        final TileEntity rte = world.getTileEntity(rp);
        final TileEntity cte = world.getTileEntity(crafterPos);
        if (!(cte instanceof TileEntityGolemCrafter)) return true;

        int perCraft = (ln.perCraftOut > 0) ? ln.perCraftOut : 1;
        if (perCraft <= 0 && rte instanceof TilePatternRequester) {
            perCraft = Math.max(1, ((TilePatternRequester) rte).getPerCraftOutputCountFor(ln.wanted1));
            ln.perCraftOut = perCraft;
        }

        int movedFromCrafter = harvestLikeToBufferFromRequester(rp, ln.wanted1, ln.remaining);
        if (movedFromCrafter > 0) {
            if (perCraft > 0) {
                int craftsDone = (movedFromCrafter + perCraft - 1) / perCraft;
                if (ln.craftsScheduled > 0) {
                    ln.craftsScheduled = Math.max(0, ln.craftsScheduled - craftsDone);
                }
                ln.craftsCompleted += craftsDone;
            }

            int pushed = pushFromBufferTo(b.dest, b.destSide, ln.wanted1, movedFromCrafter);
            if (pushed > 0) {
                ln.remaining -= pushed;
                lastProgressTick.put(ItemKey.of(ln.wanted1), tickCounter);
                if (ln.remaining <= 0) return true;
            }
        }

        int bufferedOut = countInBufferLike(ln.wanted1);
        int remainingAfterBuffer = Math.max(0, ln.remaining - bufferedOut);

        int craftsNeeded = (remainingAfterBuffer + perCraft - 1) / perCraft;
        if (craftsNeeded <= 0) {
            int pushed = pushFromBufferTo(b.dest, b.destSide, ln.wanted1, ln.remaining);
            if (pushed > 0) {
                ln.remaining -= pushed;
                lastProgressTick.put(ItemKey.of(ln.wanted1), tickCounter);
                if (ln.remaining <= 0) return true;
            }
            return false;
        }

        List<ItemStack> needList = Collections.emptyList();
        if (rte instanceof TilePatternRequester) {
            needList = ((TilePatternRequester) rte).getRecipeInputsFor(ln.wanted1, craftsNeeded);
        }

        Map<ItemKey, Integer> miss = new LinkedHashMap<>();
        IItemHandler in = ((TileEntityGolemCrafter) cte).getInputHandler();
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
                        // нестакуемые — строго item+meta, без NBT
                        match = matchesForDelivery(cur, need);
                    } else {
                        // стакаемые — relaxed
                        match = ItemHandlerHelper.canItemStacksStackRelaxed(cur, need);
                    }

                    if (match) have += cur.getCount();
                }
                int lacking = Math.max(0, want - have);
                if (lacking > 0) miss.merge(ItemKey.of(need), lacking, Integer::sum);
            }
        }

        if (!miss.isEmpty()) {
            for (Map.Entry<ItemKey, Integer> e : miss.entrySet()) {
                if (e.getValue() <= 0) continue;
                int pushed = pushFromBufferTo(crafterPos, -1, e.getKey().toStack(1), e.getValue());
                if (pushed > 0) e.setValue(Math.max(0, e.getValue() - pushed));
            }
        }

        if (!miss.isEmpty()) {
            int qDelivery = (b.queueId + 1) % 6;
            ensureDeliveryForExact(crafterPos, miss, qDelivery);
            activeQueue = qDelivery;
        }

        if (boundDispatchers.isEmpty()) {
            Map.Entry<ItemKey, Integer> firstMiss = miss.entrySet().stream()
                    .filter(e -> e.getValue() > 0)
                    .findFirst().orElse(null);
            if (firstMiss != null) {
                ItemStack like1 = firstMiss.getKey().toStack(1);
                int chunk = Math.min(like1.getMaxStackSize(), firstMiss.getValue());
                ItemStack req = normalizeForProvision(like1, chunk); // <— ключевое
                if (!req.isEmpty()) {
                    enqueueProvisionTask(req);
                }
            }
        }


        TileEntityGolemCrafter cr = (TileEntityGolemCrafter) cte;

        // ❗ Стартуем ТОЛЬКО когда всё лежит в инпуте (miss == 0)
        // Это важный момент: раньше мы пытались "до срока".
        boolean allReady = true;
        for (Map.Entry<ItemKey, Integer> e : miss.entrySet()) {
            if (e.getValue() > 0) {
                allReady = false;
                break;
            }
        }

        if (allReady && rte instanceof TilePatternRequester) {
            TilePatternRequester rq = (TilePatternRequester) rte;

            // Сколько единиц выдаётся за 1 цикл именно для этого результата
            final int outPerCraft = Math.max(1, ln.perCraftOut > 0 ? ln.perCraftOut : rq.getPerCraftOutputCountFor(ln.wanted1));
            ln.perCraftOut = outPerCraft;


            // Сколько ещё единиц надо ПОСЛЕ учёта буфера
            int bufferedOut2 = countInBufferLike(ln.wanted1);
            int remainingAfterBuffer2 = Math.max(0, ln.remaining - bufferedOut2);

            // Сколько циклов реально нужно, исходя из «остатка после буфера»
            int craftsNeedNow = (remainingAfterBuffer2 + outPerCraft - 1) / outPerCraft;
            if (ln.craftsExpected > 0) {
                int craftsLeft = Math.max(0, ln.craftsExpected - ln.craftsCompleted);
                craftsNeedNow = Math.max(craftsNeedNow, craftsLeft);
            }

            // Сколько циклов уже запланировано ранее
            int craftsDelta = Math.max(0, craftsNeedNow - ln.craftsScheduled);

            if (craftsDelta > 0) {
                // Пинаем крафтер НА craftsDelta циклов, без автопровизии
                if (cte instanceof TileEntityGolemCrafter) {
                    ((TileEntityGolemCrafter) cte).enqueueCraftsByRequesterLike(ln.wanted1, craftsDelta);
                    ln.craftsScheduled += craftsDelta;
                    lastProgressTick.put(ItemKey.of(ln.wanted1), tickCounter);
                }
            }
        }
        // ... и уже после этого пробуем ещё раз добросить готовое в адресата:
        int pushed2 = pushFromBufferTo(b.dest, b.destSide, ln.wanted1, ln.remaining);
        if (pushed2 > 0) {
            ln.remaining -= pushed2;
            lastProgressTick.put(ItemKey.of(ln.wanted1), tickCounter);
            if (ln.remaining <= 0) return true;
        }
        return false;

    }

    private void processBatchHead(Batch b) {
        if (b == null) return;
        if (b.seenTick == tickCounter) return;
        b.seenTick = tickCounter;

        if (b.lines.isEmpty()) {
            popBatch(b);
            return;
        }
        if (!hasItemCapAt(b.dest, b.destSide)) {
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

        tickConsumerFocus();

        // Периодически актуализируем список складов под SealProvide
        if ((tickCounter % PROVIDER_RESCAN_TICKS) == 0) {
            rebuildProvideSetFromSeals();
        }
        rebuildProvideSetFromSeals();
    }

    private void pruneStaleBindings() {
        if (world == null || world.isRemote) return;

        List<BlockPos> deadReq = new ArrayList<>();
        List<BlockPos> deadTerm = new ArrayList<>();

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

        if (deadReq.isEmpty() && deadTerm.isEmpty()) return;

        // Снимаем из наборов
        boundRequesters.removeAll(deadReq);
        boundTerminals.removeAll(deadTerm);

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

        // 1) радиус
        for (BlockPos bp : boundRequesters) if (!inRange.test(bp)) dropReq.add(bp);
        for (BlockPos bp : boundTerminals) if (!inRange.test(bp)) dropTerm.add(bp);
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

        if (dropReq.isEmpty() && dropTerm.isEmpty() && dropDisp.isEmpty()) return;

        // Уникализируем
        java.util.Set<BlockPos> uniqReq = new java.util.LinkedHashSet<>(dropReq);
        java.util.Set<BlockPos> uniqTerm = new java.util.LinkedHashSet<>(dropTerm);
        java.util.Set<BlockPos> uniqDisp = new java.util.LinkedHashSet<>(dropDisp);

        boundRequesters.removeAll(uniqReq);
        boundTerminals.removeAll(uniqTerm);
        boundDispatchers.keySet().removeAll(uniqDisp);
        if (!uniqDisp.isEmpty()) dispatcherBusyQueue.removeIf(uniqDisp::contains);

        // «Тихо» уведомляем
        isUnlinking = true;
        try {
            for (BlockPos rp : uniqReq) unlinkRequesterSideEffects(rp);
            for (BlockPos tp : uniqTerm) unlinkTerminalSideEffects(tp);
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
            for (BlockPos dp : new java.util.ArrayList<>(boundDispatchers.keySet())) {
                unlinkDispatcherSideEffects(dp);
            }
            boundRequesters.clear();
            boundTerminals.clear();
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
            markDirty();
            return;
        }

        isUnlinking = true;
        try {
            boolean wasReq = boundRequesters.remove(consumerPos);
            boolean wasTerm = boundTerminals.remove(consumerPos);
            DispatcherStats removedDisp = boundDispatchers.remove(consumerPos);
            boolean wasDisp = removedDisp != null;
            if (wasDisp) {
                dispatcherBusyQueue.removeIf(bp -> bp.equals(consumerPos));
            }

            if (wasReq) unlinkRequesterSideEffects(consumerPos);
            if (wasTerm) unlinkTerminalSideEffects(consumerPos);
            if (wasDisp) unlinkDispatcherSideEffects(consumerPos);

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
                match = ItemHandlerHelper.canItemStacksStackRelaxed(s, like);
            }
            if (match) total += s.getCount();
        }
        return total;
    }

    private static ItemStack normalizeForProvision(ItemStack like, int amount) {
        if (isCrystal(like)) {
            thaumcraft.api.aspects.Aspect a = aspectOf(like);
            if (a != null) return thaumcraft.api.ThaumcraftApiHelper.makeCrystal(a, Math.max(1, amount));
        }
        if (like.getMaxStackSize() == 1) {
            return new ItemStack(like.getItem(), Math.max(1, amount), like.getMetadata()); // без NBT
        }
        ItemStack r = like.copy();
        r.setCount(Math.max(1, amount));
        return r;
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
                        match = ItemHandlerHelper.canItemStacksStackRelaxed(ln.wanted1, like);
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
                        match = ItemHandlerHelper.canItemStacksStackRelaxed(ln.wanted1, like);
                    }

                    if (!match) continue;

                    int take = Math.min(left, ln.remaining);
                    int beforeReserved = ln.reserved;
                    ln.remaining -= take;
                    if (ln.reserved > ln.remaining) {
                        ln.reserved = Math.max(0, ln.remaining);
                    }
                    int released = Math.max(0, beforeReserved - ln.reserved);
                    if (released > 0) releaseDispatcherGolems(released);
                    left -= take;
                    if (ln.remaining <= 0) itL.remove();
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

        NBTTagList t = new NBTTagList();
        for (BlockPos bp : boundTerminals) t.appendTag(new NBTTagLong(bp.toLong()));
        nbt.setTag("boundTerminals", t);

        NBTTagList r = new NBTTagList();
        for (BlockPos bp : boundRequesters) r.appendTag(new NBTTagLong(bp.toLong()));
        nbt.setTag("boundRequesters", r);

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

    private static boolean crystalSame(ItemStack a, ItemStack b) {
        if (!isCrystal(a) || !isCrystal(b)) return false;
        thaumcraft.api.aspects.Aspect ax = aspectOf(a);
        thaumcraft.api.aspects.Aspect bx = aspectOf(b);
        return ax != null && bx != null && ax == bx;
    }

    private static ItemKey normKeyForCatalog(ItemStack s) {
        if (isCrystal(s)) {
            thaumcraft.api.aspects.Aspect a = aspectOf(s);
            if (a != null) {
                ItemStack like = thaumcraft.api.ThaumcraftApiHelper.makeCrystal(a, 1);
                return ItemKey.of(like);
            }
        }
        // Нестакаемые — item+meta без NBT (как было)
        if (!s.isEmpty() && s.getMaxStackSize() == 1) {
            return ItemKey.of(new ItemStack(s.getItem(), 1, s.getMetadata()));
        }
        // NEW: для стакаемых с сабтипами различаем meta (NBT оставляем, если есть)
        if (s.getHasSubtypes()) {
            ItemStack k = new ItemStack(s.getItem(), 1, s.getMetadata());
            if (s.hasTagCompound()) k.setTagCompound(s.getTagCompound().copy());
            return ItemKey.of(k);
        }
        // обычные стакаемые — как есть
        return ItemKey.of(s);
    }


    private static boolean matchesForDelivery(ItemStack a, ItemStack like) {
        if (a == null || like == null || a.isEmpty() || like.isEmpty()) return false;
        if (a.getItem() != like.getItem()) return false;
        if (a.getHasSubtypes() && a.getMetadata() != like.getMetadata()) return false;
        if (a.getMaxStackSize() == 1) return true;
        return ItemStack.areItemStackTagsEqual(a, like);
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
                if (ItemHandlerHelper.canItemStacksStackRelaxed(s, like)) total += s.getCount();
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
        Set<BlockPos> reqs = getRequestersSnapshot();
        if (world == null || reqs == null) return out;

        for (BlockPos rp : reqs) {
            TileEntity te = world.getTileEntity(rp);
            if (!(te instanceof ICraftEndpoint)) continue;

            ICraftEndpoint ep = (ICraftEndpoint) te;
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
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;

        // если хочешь — можешь заюзать ту же crystal-логику, что в крафтере/терминале
        if (a.getMaxStackSize() == 1 || b.getMaxStackSize() == 1) {
            if (a.getItem() != b.getItem()) return false;
            if (a.getHasSubtypes() && a.getMetadata() != b.getMetadata()) return false;
            return true;
        }
        return ItemHandlerHelper.canItemStacksStackRelaxed(a, b);
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

    /* ===================== NBT owner allow (оставлено как было) ===================== */
    public boolean isOwnerAllowed(String owner) {
        return allowedOwners.isEmpty() || (owner != null && allowedOwners.contains(owner));
    }

    @Nullable
    private BlockPos findRequesterForKey(ItemKey key) {
        if (world == null) return null;
        for (BlockPos rp : requesters) {
            TileEntity te = world.getTileEntity(rp);
            if (!(te instanceof TilePatternRequester)) continue;
            for (ItemStack out : ((TilePatternRequester) te).listCraftableResults()) {
                if (out == null || out.isEmpty()) continue;
                ItemStack like = key.toStack(1);
                boolean same = (like.getMaxStackSize() == 1)
                        ? (out.getItem() == like.getItem()
                        && (!out.getHasSubtypes() || out.getMetadata() == like.getMetadata()))
                        : ItemHandlerHelper.canItemStacksStackRelaxed(out, like);
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

        if (a.getItem() != b.getItem()) return false;

        // Если есть сабтипы — мету учитываем обязательно
        if ((a.getHasSubtypes() || b.getHasSubtypes()) && a.getMetadata() != b.getMetadata()) return false;

        // Для стакаемых учитываем NBT, чтобы не склеивать варианты
        return ItemStack.areItemStackTagsEqual(a, b);
    }


    public java.util.Set<net.minecraft.util.math.BlockPos> getRequestersSnapshot() {
        // Верните копию своего внутреннего множества/списка реквестеров
        return new java.util.HashSet<>(this.requesters); // <-- замените this.requesters на ваши данные
    }

}
