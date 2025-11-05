package therealpant.thaumicattempts.golemnet.tile;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
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
import thaumcraft.api.ThaumcraftInvHelper;
import thaumcraft.common.golems.seals.SealEntity;
import thaumcraft.common.golems.seals.SealHandler;
import thaumcraft.common.golems.seals.SealProvide;
import therealpant.thaumicattempts.golemcraft.tile.TileEntityGolemCrafter;
import therealpant.thaumicattempts.integration.TcLogisticsCompat;
import therealpant.thaumicattempts.util.ItemKey;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Менеджер: батчи + единая «корона» зеркал (активные/подвешенные), без дюпа.
 */
public class TileMirrorManager extends TileEntity implements ITickable {

    /* ===================== Базовые лимиты и апгрейды ===================== */

    int craftsScheduled = 0;

    private static final int BASE_RANGE        = 32;
    private static final int BASE_MIRROR_CAP   = 1;
    private static final int BASE_COMPUTE_CAP  = 0;

    private static final int STAB_RANGE_INC    = 8;
    private static final int STAB_MIRROR_INC   = 2;
    private static final int CORE_COMPUTE_INC  = 2;

    private int calcRange = BASE_RANGE;
    private int calcMirrorCap = BASE_MIRROR_CAP;
    private int calcComputeCap = BASE_COMPUTE_CAP;

    private final Set<BlockPos> boundTerminals  = new HashSet<>();
    private final Set<BlockPos> boundRequesters = new HashSet<>();

    public int getRange()        { return calcRange; }
    public int getMirrorCap()    { return calcMirrorCap; }
    public int getComputeCap()   { return calcComputeCap; }
    public int getMirrorUsed()   { return boundTerminals.size() + boundRequesters.size(); }
    public int getComputeUsed()  { return boundRequesters.size(); }
    private boolean isUnlinking = false;
    private int staleSweepTicker = 0;
    private boolean suppressAcceptAnim = false;

    /* ===================== Голем-логистика ===================== */

    private static final int REQ_DEBOUNCE_TICKS     = 5;
    private static final int STALL_TICKS            = 40;  // 2с
    private static final int REQUEST_BUDGET         = 4;
    private static final int MAX_REQ_PER_TICK       = 64;
    private static final int AUTO_SCAN_PERIOD_TICKS = 100;
    private static final int PROVIDER_SCAN_RADIUS   = 16;
    private static final int PROVIDER_RESCAN_TICKS  = 200;

    /* ===================== Владелец / доступ ===================== */

    private final Set<String> allowedOwners = new HashSet<>();
    private @Nullable String ownerUuid;
    public @Nullable String getOwnerUuid() { return ownerUuid; }
    public void setOwnerUuid(@Nullable String uuid) {
        if (!Objects.equals(ownerUuid, uuid)) { ownerUuid = uuid; markDirty(); }
    }
    public void allowOwner(UUID uuid) { if (uuid != null && allowedOwners.add(uuid.toString())) markDirty(); }
    private static final String TAG_REQ_REG = "registeredRequesters";

    /* ===================== Каталог (как в TC) ===================== */

    public TcLogisticsCompat.Page buildLogisticsPage(int startRow, String search) {
        return TcLogisticsCompat.build(this.world, this.ownerUuid, this.pos, startRow, search);
    }

    /* ===================== Корона зеркал ===================== */

    // Геометрия (должна совпадать с TESR)
    private static final double RADIUS = 1.6;
    private static final float  BASE_Y = 1.35f;
    private static final float  Y_STEP = 0.48f;
    private static final float  RING_SHIFT_YAW = 30f;

    private static final int RINGS = 4;
    private static final int SLOTS_PER_RING = 6;
    private static final int MAX_SLOTS = RINGS * SLOTS_PER_RING;

    private static final String TAG_MIRRORS     = "mirrors";
    private static final String TAG_RENDER_SEED = "renderSeed";
    private static final String TAG_PEND_EJECTS = "pendEjects";
    // ЕДИНАЯ занятость слотов
    private final boolean[][] slotBusy = new boolean[RINGS][SLOTS_PER_RING];

    // Активные зеркала (видимые и учитываемые лимитом)
    public static final class MirrorSlot {
        public final int ring, slot;
        public final long phase;
        public MirrorSlot(int r, int s, long p){ ring=r; slot=s; phase=p; }
    }
    private final List<MirrorSlot> activeMirrors = new ArrayList<>(MAX_SLOTS);
    private long renderSeed = 0L;

    // Подвешенные к выбросу (занимают слот до дропа)
    private static final int EJECT_HOVER_TICKS = 40; // ~2с
    private static final class PendingEject {
        final int ring, slot;
        final long startTick;
        PendingEject(int r, int s, long start){ ring=r; slot=s; startTick=start; }
    }
    private final List<PendingEject> pendingEjects = new ArrayList<>();

    public java.util.List<int[]> getPendingEjectVisuals() {
        java.util.ArrayList<int[]> out = new java.util.ArrayList<>(pendingEjects.size());
        long now = (world != null ? world.getTotalWorldTime() : 0L);
        for (PendingEject p : pendingEjects) {
            int age = (int)Math.max(0L, now - p.startTick);
            out.add(new int[]{p.ring, p.slot, age, EJECT_HOVER_TICKS});
        }
        return out;
    }
    public int getEjectHoverTicks() { return EJECT_HOVER_TICKS; }

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
    @Nullable
    private int[] pickFreeSlot() {
        for (int r = 0; r < RINGS; r++)
            for (int s = 0; s < SLOTS_PER_RING; s++)
                if (!slotBusy[r][s]) return new int[]{r,s};
        return null;
    }
    private void occupySlot(int r, int s) { slotBusy[r][s] = true; }
    private void freeSlot(int r, int s)   { slotBusy[r][s] = false; }

    private Vec3d slotWorldPos(int ring, int slot) {
        float base = slot * 60f;
        if (ring == 1 || ring == 3) base += RING_SHIFT_YAW;
        double ang = Math.toRadians(base);
        double px = pos.getX() + 0.5 + Math.cos(ang) * RADIUS;
        double pz = pos.getZ() + 0.5 + Math.sin(ang) * RADIUS;
        double py = pos.getY() + BASE_Y + ring * Y_STEP;
        return new Vec3d(px, py, pz);
    }

    public List<MirrorSlot> getRenderMirrors() { return new ArrayList<>(activeMirrors); }

    /** Выбрать случайный занятый слот со зеркалом, либо -1/-1 если пусто. */
    public int[] pickRandomMirrorSlot() {
        if (activeMirrors.isEmpty()) return new int[]{-1,-1};
        Random rnd = new Random(world.getTotalWorldTime() ^ pos.toLong() ^ System.nanoTime());
        MirrorSlot m = activeMirrors.get(rnd.nextInt(activeMirrors.size()));
        return new int[]{m.ring, m.slot};
    }

    /** Добавить активное зеркало, если есть свободный слот (для визуала/лимита). */
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
        freeSlot(m.ring, m.slot);
        markDirtyAndSync();
        pruneBindingsByRangeAndCapacity();   // << сразу отлинковать лишних
        return new ItemStack(thaumcraft.api.blocks.BlocksTC.mirror);
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
            this.ring = r; this.slot = sl; this.start = st; this.duration = Math.max(5, dur); this.seed = sd;
        }
    }
    private final List<FlyingItem> flying = new ArrayList<>();
    public List<FlyingItem> getFlying() { return new ArrayList<>(flying); }
    public void clientAddFlying(ItemStack stack, int ring, int slot, int duration, long seed) {
        if (world == null || !world.isRemote) return;
        long now = world.getTotalWorldTime();
        flying.add(new FlyingItem(stack, ring, slot, now, duration, seed));
    }
    public void clientCullFlying() {
        if (world == null || !world.isRemote) return;
        long now = world.getTotalWorldTime();
        flying.removeIf(f -> (now - f.start) > (long)f.duration + 2);
    }

    /* === «Лишние» зеркала: 2с висим и падаем === */

    private static boolean isMirrorItem(ItemStack s) {
        return s != null && !s.isEmpty()
                && s.getItem() == net.minecraft.item.Item.getItemFromBlock(thaumcraft.api.blocks.BlocksTC.mirror);
    }

    /** Из буфера поднимаем зеркала до лимита активных; сверх — подвешиваем и дропаем. */
    private void processMirrorItemsInBuffer() {
        if (world == null || world.isRemote) return;

        boolean removedExtras = false;
        while (activeMirrors.size() > calcMirrorCap) {
            MirrorSlot m = activeMirrors.remove(activeMirrors.size() - 1);
            pendingEjects.add(new PendingEject(m.ring, m.slot, world.getTotalWorldTime()));
            removedExtras = true;
        }
        if (removedExtras) {
            pruneBindingsByRangeAndCapacity();   // << сразу
            markDirtyAndSync();
        }

        // если активных больше нового лимита — перевести «лишние» в подвешенные
        while (activeMirrors.size() > calcMirrorCap) {
            MirrorSlot m = activeMirrors.remove(activeMirrors.size() - 1);
            // слот занят подвешенным
            pendingEjects.add(new PendingEject(m.ring, m.slot, world.getTotalWorldTime()));
        }

        // дозаполнить активные из буфера, если есть лимит
        int wantActive = Math.max(0, calcMirrorCap - activeMirrors.size());
        if (wantActive > 0) {
            for (int i = 0; i < buffer.getSlots() && wantActive > 0; i++) {
                ItemStack peek = buffer.extractItem(i, 1, true);
                if (peek.isEmpty() || !isMirrorItem(peek)) continue;
                if (addMirror()) { // consume 1
                    buffer.extractItem(i, 1, false);
                    wantActive--;
                }
            }
        }

        // всё, что осталось зеркал в буфере — подвесить и потом выбросить
        for (int i = 0; i < buffer.getSlots(); i++) {
            ItemStack peek = buffer.extractItem(i, 1, true);
            if (peek.isEmpty() || !isMirrorItem(peek)) continue;

            int[] fs = pickFreeSlot();
            if (fs == null) break; // визуальных позиций не осталось
            occupySlot(fs[0], fs[1]);
            pendingEjects.add(new PendingEject(fs[0], fs[1], world.getTotalWorldTime()));
            buffer.extractItem(i, 1, false); // изъяли — выбросим сами
        }

        if (!pendingEjects.isEmpty()) markDirtyAndSync();
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
                    m.setPos(upgradePos.getX()+dx, upgradePos.getY()+dy, upgradePos.getZ()+dz);
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



    /** Дропнуть подвешенные, освободив слот. */
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
            // если это зеркало и активных уже по лимиту — не кладём в буфер пачку,
            // а забираем РОВНО 1 шт. и подвешиваем к выбросу
            if (!simulate && isMirrorItem(stack) && activeMirrors.size() >= calcMirrorCap) {
                // занять свободный слот для визуала подвешенного
                int[] fs = pickFreeSlot();
                if (fs != null) {
                    occupySlot(fs[0], fs[1]);
                    pendingEjects.add(new PendingEject(fs[0], fs[1], world.getTotalWorldTime()));
                    stack.shrink(1); // забрали 1
                    markDirtyAndSync();
                    return stack; // остаток возвращаем источнику
                }
                // если свободных визуальных слотов нет — просто не принимаем (возврат stack без изменений)
                return stack;
            }

            // обычная логика
            ItemStack before = stack.copy();
            ItemStack res = super.insertItem(slot, stack, simulate);
            if (!simulate) {
                int accepted = before.getCount() - (res.isEmpty() ? 0 : res.getCount());
                if (accepted > 0 && !suppressAcceptAnim) {
                    ItemStack one = before.copy(); one.setCount(1);
                    onItemAccepted(one);
                }
            }
            return res;
        }
        @Override protected void onContentsChanged(int slot) { markDirty(); }
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
    private void onItemAccepted(ItemStack stack) {
        if (world == null || world.isRemote || stack.isEmpty()) return;
        int[] pick = pickRandomMirrorSlot();
        if (pick[0] < 0) return; // нет активных зеркал — нет анимации

        int dur  = 36 + world.rand.nextInt(16);
        long seed = world.rand.nextLong();

        therealpant.thaumicattempts.golemnet.net.msg.S2CFlyAnim pkt =
                new therealpant.thaumicattempts.golemnet.net.msg.S2CFlyAnim(this.pos, stack, pick[0], pick[1], dur, seed);

        net.minecraftforge.fml.common.network.NetworkRegistry.TargetPoint tp =
                new net.minecraftforge.fml.common.network.NetworkRegistry.TargetPoint(
                        world.provider.getDimension(), pos.getX()+0.5, pos.getY()+0.5, pos.getZ()+0.5, 64);
        therealpant.thaumicattempts.ThaumicAttempts.NET.sendToAllAround(pkt, tp);
    }

    /** Внешняя обёртка — подхватывает приход и продвигает текущий батч. */
    private final IItemHandler exposedBuffer = new IItemHandler() {
        @Override public int getSlots() { return buffer.getSlots(); }
        @Override public ItemStack getStackInSlot(int slot) { return buffer.getStackInSlot(slot); }
        @Override public int getSlotLimit(int slot) { return buffer.getSlotLimit(slot); }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return buffer.extractItem(slot, amount, simulate); }
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
            for (Iterator<BlockPos> it = requesters.iterator(); it.hasNext();) {
                BlockPos rp = it.next();
                TileEntity te = world.getTileEntity(rp);
                if (!(te instanceof TilePatternRequester)) { it.remove(); changed = true; }
            }
            for (BlockPos rp : boundRequesters) {
                TileEntity te = world.getTileEntity(rp);
                if (te instanceof TilePatternRequester && !requesters.contains(rp)) {
                    requesters.add(rp.toImmutable());
                    changed = true;
                }
            }
            if (changed) markDirtyAndSync();
        }
    }

    /* ===================== Прогресс/в пути ===================== */

    private final Map<ItemKey, Integer> inflight = new HashMap<>();
    private final Map<ItemKey, Integer> lastProgressTick = new HashMap<>();
    private final Map<ItemKey, Integer> lastReqTick = new HashMap<>();

    private void decInflightRelaxed(ItemStack arrived, int count) {
        if (arrived == null || arrived.isEmpty() || count <= 0) return;

        ItemKey exact = ItemKey.of(arrived);
        int cur = inflight.getOrDefault(exact, 0);
        if (cur > 0) {
            int left = Math.max(0, cur - count);
            if (left == 0) inflight.remove(exact); else inflight.put(exact, left);
            return;
        }

        if (isCrystal(arrived)) {
            for (Map.Entry<ItemKey,Integer> e : inflight.entrySet()) {
                if (e.getValue() <= 0) continue;
                ItemStack want = e.getKey().toStack(1);
                if (isCrystal(want) && crystalSame(want, arrived)) {
                    int left = Math.max(0, e.getValue() - count);
                    e.setValue(left);
                    return;
                }
            }
        }

        for (Map.Entry<ItemKey,Integer> e : inflight.entrySet()) {
            if (e.getValue() <= 0) continue;
            if (ItemHandlerHelper.canItemStacksStackRelaxed(e.getKey().toStack(1), arrived)) {
                int left = Math.max(0, e.getValue() - count);
                e.setValue(left);
                return;
            }
        }

        if (arrived.getMaxStackSize() == 1) {
            for (Map.Entry<ItemKey,Integer> e : inflight.entrySet()) {
                if (e.getValue() <= 0) continue;
                ItemStack want = e.getKey().toStack(1);
                if (matchesForDelivery(arrived, want)) {
                    int left = Math.max(0, e.getValue() - count);
                    e.setValue(left);
                    return;
                }
            }
        }
    }

    private void onArrivedToBuffer(ItemStack stack, int count) {
        if (stack.isEmpty() || count <= 0) return;
        decInflightRelaxed(stack, count);

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
                if (rem.isEmpty()) ei.setDead(); else ei.setItem(rem);

                Batch b = peekNextBatch();
                if (b != null && b.kind == Batch.Kind.DELIVERY) {
                    processBatchHead(b);
                }
            }
        }
    }

    public boolean tryBindTerminal(BlockPos pos) {
        if (pos == null) return false;
        if (pos.distanceSq(getPos()) > (double)(calcRange * calcRange)) return false;
        // требуется РЕАЛЬНО свободное зеркало
        if (!hasFreeMirror()) return false;
        boundTerminals.add(pos.toImmutable());
        markDirty();
        return true;
    }

    public boolean tryBindRequester(BlockPos pos) {
        if (pos == null) return false;
        if (pos.distanceSq(getPos()) > (double)(calcRange * calcRange)) return false;
        // требуется и зеркало, и вычислительная ячейка
        if (!hasFreeMirror()) return false;
        if (!hasFreeComputeCell()) return false;
        boundRequesters.add(pos.toImmutable());
        markDirty();
        return true;
    }

    /* ===================== Поставщики (автоскан) ===================== */

    public static final class TrackedInv {
        public final BlockPos pos; public final int side;
        public TrackedInv(BlockPos pos, int side){ this.pos = pos; this.side = side; }
        @Override public boolean equals(Object o){ if (!(o instanceof TrackedInv)) return false; TrackedInv t=(TrackedInv)o; return side==t.side && Objects.equals(pos,t.pos); }
        @Override public int hashCode(){ return Objects.hash(pos, side); }
    }
    private final LinkedHashSet<TrackedInv> provideSet = new LinkedHashSet<>();

    private int tickCounter = 0;
    private int lastProviderScan = -9999;

    private void rescanProvidersRadius() {
        if (world == null || world.isRemote) return;
        LinkedHashSet<TrackedInv> found = new LinkedHashSet<>();
        final int r = PROVIDER_SCAN_RADIUS;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

        for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++) for (int dz = -r; dz <= r; dz++) {
            m.setPos(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
            TileEntity te = world.getTileEntity(m);
            if (te == null) continue;
            if (m.equals(this.pos)) continue;

            IItemHandler any = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
            if (any != null) { found.add(new TrackedInv(m.toImmutable(), -1)); continue; }
            for (EnumFacing face : EnumFacing.values()) {
                if (te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face) != null) {
                    found.add(new TrackedInv(m.toImmutable(), face.getIndex()));
                }
            }
        }

        if (!found.equals(provideSet)) { provideSet.clear(); provideSet.addAll(found); markDirty(); }
    }

    private void rescanProvidersAuto() {
        if (world == null || world.isRemote) return;

        LinkedHashSet<TrackedInv> newSet = new LinkedHashSet<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        final int R = PROVIDER_SCAN_RADIUS;

        for (int dx = -R; dx <= R; dx++)
            for (int dy = -R; dy <= R; dy++)
                for (int dz = -R; dz <= R; dz++) {
                    m.setPos(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    TileEntity te = world.getTileEntity(m);
                    if (te == null) continue;
                    if (te == this) continue;
                    if (te instanceof TileOrderTerminal) continue;

                    IItemHandler any = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
                    if (any != null) { newSet.add(new TrackedInv(m.toImmutable(), -1)); continue; }

                    for (int si = 0; si < 6; si++) {
                        EnumFacing f = EnumFacing.byIndex(si);
                        if (te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, f) != null) {
                            newSet.add(new TrackedInv(m.toImmutable(), si)); break;
                        }
                    }
                }

        if (!newSet.equals(this.provideSet)) { this.provideSet.clear(); this.provideSet.addAll(newSet); markDirty(); }
    }

    /* ===================== BATCH-очереди ===================== */

    private static final class Line {
        final ItemStack wanted1;
        public int craftsScheduled;
        int remaining; @Nullable BlockPos requester;
        Line(ItemStack like1, int amount) { this.wanted1 = like1.copy(); this.wanted1.setCount(1); this.remaining = Math.max(1, amount); }
    }
    private static final class Batch {
        enum Kind { DELIVERY, CRAFT }
        final Kind kind;
        final BlockPos dest;
        final int destSide;
        final int queueId;
        final List<Line> lines = new ArrayList<>();
        int index = 0;
        int seenTick = -1;
        Batch(Kind kind, BlockPos dest, int destSide, int queueId) {
            this.kind = kind; this.dest = dest.toImmutable(); this.destSide = destSide; this.queueId = queueId;
        }
    }

    private final List<Deque<Batch>> batchQueues = new ArrayList<>(6);
    private int activeQueue = 0;

    public TileMirrorManager() { for (int i = 0; i < 6; i++) batchQueues.add(new ArrayDeque<>()); }

    public interface RequesterFinder { @Nullable BlockPos find(ItemKey key); }

    public void enqueueBatchDelivery(BlockPos dest, int destSide, int queueId, List<Map.Entry<ItemKey, Integer>> moved) {
        if (dest == null || moved == null || moved.isEmpty()) return;
        int q = Math.max(0, Math.min(5, queueId));
        Batch b = new Batch(Batch.Kind.DELIVERY, dest, destSide, q);
        for (Map.Entry<ItemKey,Integer> e : moved) {
            ItemStack like1 = e.getKey().toStack(1);
            int amt = Math.max(1, e.getValue());
            b.lines.add(new Line(like1, amt));
        }
        batchQueues.get(q).addLast(b);
        markDirty();
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

            if (!rest.isEmpty()) { out.insertItem(s, rest, false); break; }
        }
        if (moved > 0) { markDirty(); }
        return moved;
    }

    public void enqueueBatchCraft(BlockPos dest, int destSide, int queueId,
                                  List<Map.Entry<ItemKey, Integer>> moved, RequesterFinder finder) {
        if (world == null || dest == null || moved == null || moved.isEmpty()) return;

        final int q = Math.max(0, Math.min(5, queueId));

        final java.util.function.BiConsumer<BlockPos, ItemStack> dropDupDelivery = (d, like1) -> {
            for (Deque<Batch> qd : batchQueues) {
                for (Iterator<Batch> itB = qd.iterator(); itB.hasNext();) {
                    Batch b = itB.next();
                    if (b.kind != Batch.Kind.DELIVERY) continue;
                    if (!b.dest.equals(d)) continue;
                    for (Iterator<Line> itL = b.lines.iterator(); itL.hasNext();) {
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

        for (Map.Entry<ItemKey,Integer> e : moved) {
            ItemStack like1 = e.getKey().toStack(1);
            int amount = Math.max(1, e.getValue());

            BlockPos rp = (finder != null) ? finder.find(e.getKey()) : findRequesterForKey(e.getKey());
            if (rp == null) continue;

            dropDupDelivery.accept(dest, like1);

            Batch b = new Batch(Batch.Kind.CRAFT, dest, destSide, q);
            Line ln = new Line(like1, amount);
            ln.requester = rp;
            b.lines.add(ln);
            batchQueues.get(q).addLast(b);
        }

        activeQueue = q;
        markDirty();
    }

    private Batch peekNextBatch() {
        for (int i = 0; i < 6; i++) {
            int idx = (activeQueue + i) % 6;
            if (!batchQueues.get(idx).isEmpty()) { activeQueue = idx; return batchQueues.get(idx).peekFirst(); }
        }
        return null;
    }
    private void popBatch() { Deque<Batch> q = batchQueues.get(activeQueue); if (!q.isEmpty()) q.removeFirst(); markDirty(); }

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
                left  -= accepted;
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

            if (acc.isEmpty()) acc = part; else acc.grow(part.getCount());
            left -= part.getCount();
        }
        return acc;
    }

    private boolean processOneDeliveryLine(Batch b, Line ln) {
        if (ln.remaining <= 0) return true;
        final ItemKey key = ItemKey.of(ln.wanted1);

        if (ln.requester != null) return true; // delivery в обычное место

        if (!hasItemCapAt(b.dest, b.destSide)) return false;

        int pushed0 = pushFromBufferTo(b.dest, b.destSide, ln.wanted1, ln.remaining);
        if (pushed0 > 0) {
            ln.remaining -= pushed0;
            lastProgressTick.put(key, tickCounter);
            if (ln.remaining <= 0) return true;
        }

        boolean toCrafter = world.getTileEntity(b.dest) instanceof TileEntityGolemCrafter;

        int flying = inflight.getOrDefault(key, 0);
        int atDest = countAtDestLike(b.dest, b.destSide, ln.wanted1);
        int queuedAll = countQueuedFor(b.dest, ln.wanted1);
        int queuedOthers = Math.max(0, queuedAll - ln.remaining);

        int need;
        if (toCrafter) {
            int covered = atDest + queuedOthers + flying;
            need = (covered >= ln.remaining) ? 0 : (ln.remaining - covered);
        } else {
            int lp = lastProgressTick.getOrDefault(key, -9999);
            if (tickCounter - lp > STALL_TICKS) {
                inflight.put(key, 0);
                flying = 0;
            }
            need = ln.remaining - flying;
        }

        if (need > 0) {
            int lt = lastReqTick.getOrDefault(key, -9999);
            if (tickCounter - lt >= REQ_DEBOUNCE_TICKS) {
                int budget = Math.min(MAX_REQ_PER_TICK, need);
                int requested = 0;
                while (budget > 0) {
                    int chunk = Math.min(ln.wanted1.getMaxStackSize(), budget);
                    ItemStack req = normalizeForProvision(ln.wanted1, chunk);
                    thaumcraft.api.golems.GolemHelper.requestProvisioning(world, this.pos, EnumFacing.UP, req, 0);
                    requested += chunk;
                    budget    -= chunk;
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
            lastProgressTick.put(key, tickCounter);
            if (ln.remaining <= 0) return true;
        }

        return false;
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

        int movedFromCrafter = harvestLikeToBufferFromRequester(rp, ln.wanted1, ln.remaining);
        if (movedFromCrafter > 0) {
            int pushed = pushFromBufferTo(b.dest, b.destSide, ln.wanted1, movedFromCrafter);
            if (pushed > 0) {
                ln.remaining -= pushed;
                lastProgressTick.put(ItemKey.of(ln.wanted1), tickCounter);
                if (ln.remaining <= 0) return true;
            }
        }

        int bufferedOut = countInBufferLike(ln.wanted1);
        int remainingAfterBuffer = Math.max(0, ln.remaining - bufferedOut);

        int perCraft = 1;
        if (rte instanceof TilePatternRequester) {
            perCraft = Math.max(1, ((TilePatternRequester) rte).getPerCraftOutputCountFor(ln.wanted1));
        }
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
        int availableEssentia = cr.getEssentiaAvailableUnits();
        thaumcraft.api.aspects.Aspect requiredAspect = cr.getRequiredAspectType();
        if (needList != null) {
            for (ItemStack need : needList) {
                if (need == null || need.isEmpty()) continue;
                int want = need.getCount();
                int haveFromInput = 0;
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

                    if (match) haveFromInput += cur.getCount();
                }

                int extra = 0;
                if (isCrystal(need) && requiredAspect != null) {
                    thaumcraft.api.aspects.Aspect needAspect = aspectOf(need);
                    if (needAspect != null && needAspect == requiredAspect) {
                        int deficit = Math.max(0, want - haveFromInput);
                        extra = Math.min(availableEssentia, deficit);
                        availableEssentia -= extra;
                    }
                }

                int have = haveFromInput + extra;
                int lacking = Math.max(0, want - have);
                if (lacking > 0) miss.merge(ItemKey.of(need), lacking, Integer::sum);
            }
        }

        if (!miss.isEmpty()) {
            for (Map.Entry<ItemKey,Integer> e : miss.entrySet()) {
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

        Map.Entry<ItemKey,Integer> firstMiss = miss.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .findFirst().orElse(null);
        if (firstMiss != null) {
            ItemStack like1 = firstMiss.getKey().toStack(1);
            int chunk = Math.min(like1.getMaxStackSize(), firstMiss.getValue());
            ItemStack req = normalizeForProvision(like1, chunk); // <— ключевое
            thaumcraft.api.golems.GolemHelper.requestProvisioning(world, this.pos, EnumFacing.UP, req, 0);
        }


        TileEntityGolemCrafter cr = (TileEntityGolemCrafter) cte;

        // ❗ Стартуем ТОЛЬКО когда всё лежит в инпуте (miss == 0)
        // Это важный момент: раньше мы пытались "до срока".
        boolean allReady = true;
        for (Map.Entry<ItemKey,Integer> e : miss.entrySet()) {
            if (e.getValue() > 0) { allReady = false; break; }
        }

        if (allReady && rte instanceof TilePatternRequester) {
            TilePatternRequester rq = (TilePatternRequester) rte;

            // Сколько единиц выдаётся за 1 цикл именно для этого результата
            final int outPerCraft = Math.max(1, rq.getPerCraftOutputCountFor(ln.wanted1));

            // Сколько ещё единиц надо ПОСЛЕ учёта буфера
            int bufferedOut2 = countInBufferLike(ln.wanted1);
            int remainingAfterBuffer2 = Math.max(0, ln.remaining - bufferedOut2);

            // Сколько циклов реально нужно, исходя из «остатка после буфера»
            int craftsNeedNow = (remainingAfterBuffer2 + outPerCraft - 1) / outPerCraft;

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

        if (b.lines.isEmpty()) { popBatch(); return; }
        if (!hasItemCapAt(b.dest, b.destSide)) { popBatch(); return; }

        while (b.index < b.lines.size()) {
            Line ln = b.lines.get(b.index);
            boolean done = (b.kind == Batch.Kind.DELIVERY)
                    ? processOneDeliveryLine(b, ln)
                    : processOneCraftLine(b, ln);
            if (done) { b.index++; markDirty(); continue; }
            break;
        }

        if (b.index >= b.lines.size()) popBatch();
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

        if ((tickCounter % AUTO_SCAN_PERIOD_TICKS) == 0) rescanProvidersRadius();
        if (tickCounter - lastProviderScan >= PROVIDER_RESCAN_TICKS) {
            lastProviderScan = tickCounter;
            rescanProvidersAuto();
        }
    }
    private void pruneStaleBindings() {
        if (world == null || world.isRemote) return;

        List<BlockPos> deadReq = new ArrayList<>();
        List<BlockPos> deadTerm = new ArrayList<>();

        // requesters: тайла может не быть, может быть, но уже не наш менеджер
        for (BlockPos rp : boundRequesters) {
            TileEntity te = world.getTileEntity(rp);
            if (!(te instanceof TilePatternRequester)) { deadReq.add(rp); continue; }
            BlockPos mp = ((TilePatternRequester) te).getManagerPos();
            if (mp == null || !mp.equals(this.pos)) { deadReq.add(rp); }
        }

        // terminals: аналогично
        for (BlockPos tp : boundTerminals) {
            TileEntity te = world.getTileEntity(tp);
            if (!(te instanceof TileOrderTerminal)) { deadTerm.add(tp); continue; }
            BlockPos mp = ((TileOrderTerminal) te).getManagerPos();
            if (mp == null || !mp.equals(this.pos)) { deadTerm.add(tp); }
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

        calcRange      = BASE_RANGE      + activeStabs * STAB_RANGE_INC;
        calcMirrorCap  = BASE_MIRROR_CAP + activeStabs * STAB_MIRROR_INC;
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
        lastActiveStabs.clear(); lastActiveStabs.addAll(stabsActive);
        lastActiveCores.clear(); lastActiveCores.addAll(coresActive);
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
                bp != null && bp.distanceSq(getPos()) <= (double)(calcRange * calcRange);

        List<BlockPos> dropReq = new ArrayList<>();
        List<BlockPos> dropTerm = new ArrayList<>();

        // 1) радиус
        for (BlockPos bp : boundRequesters) if (!inRange.test(bp)) dropReq.add(bp);
        for (BlockPos bp : boundTerminals)  if (!inRange.test(bp)) dropTerm.add(bp);

        // 2) вычисл. кап
        int overCompute = getComputeUsed() - calcComputeCap;
        if (overCompute > 0) {
            Iterator<BlockPos> it = boundRequesters.iterator();
            while (overCompute > 0 && it.hasNext()) { dropReq.add(it.next()); overCompute--; }
        }

        // 3) логический кап зеркал
        int overMirrors = getMirrorUsed() - calcMirrorCap;
        if (overMirrors > 0) {
            Iterator<BlockPos> itR = boundRequesters.iterator();
            while (overMirrors > 0 && itR.hasNext()) { dropReq.add(itR.next()); overMirrors--; }
            Iterator<BlockPos> itT = boundTerminals.iterator();
            while (overMirrors > 0 && itT.hasNext()) { dropTerm.add(itT.next()); overMirrors--; }
        }

        // 4) ФАКТИЧЕСКИЕ активные зеркала (если их меньше, чем занято)
        int freeReal = getActiveMirrorCount();
        int used     = getMirrorUsed();
        int overReal = used - freeReal;
        if (overReal > 0) {
            Iterator<BlockPos> itR = boundRequesters.iterator();
            while (overReal > 0 && itR.hasNext()) { dropReq.add(itR.next()); overReal--; }
            Iterator<BlockPos> itT = boundTerminals.iterator();
            while (overReal > 0 && itT.hasNext()) { dropTerm.add(itT.next()); overReal--; }
        }

        if (dropReq.isEmpty() && dropTerm.isEmpty()) return;

        // Уникализируем
        java.util.Set<BlockPos> uniqReq = new java.util.LinkedHashSet<>(dropReq);
        java.util.Set<BlockPos> uniqTerm = new java.util.LinkedHashSet<>(dropTerm);

        boundRequesters.removeAll(uniqReq);
        boundTerminals.removeAll(uniqTerm);

        // «Тихо» уведомляем
        isUnlinking = true;
        try {
            for (BlockPos rp : uniqReq)  unlinkRequesterSideEffects(rp);
            for (BlockPos tp : uniqTerm) unlinkTerminalSideEffects(tp);
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
            boundRequesters.clear();
            boundTerminals.clear();
        } finally {
            isUnlinking = false;
        }
        markDirtyAndSync();
    }





    public void unbind(@Nullable BlockPos consumerPos) {
        if (world == null || world.isRemote || consumerPos == null) return;

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

            if (wasReq)  unlinkRequesterSideEffects(consumerPos);
            if (wasTerm) unlinkTerminalSideEffects(consumerPos);

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
        ItemStack r = like.copy(); r.setCount(Math.max(1, amount)); return r;
    }


    private int countQueuedFor(BlockPos dest, ItemStack like) {
        if (dest == null || like == null || like.isEmpty()) return 0;
        int total = 0;

        for (Deque<Batch> q : batchQueues) for (Batch b : q) {
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
                    ln.remaining -= take;
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
            int need   = missing - pushed;
            if (need > 0) {
                List<Map.Entry<ItemKey,Integer>> lst = new ArrayList<>(1);
                lst.add(new AbstractMap.SimpleEntry<>(ItemKey.of(like), need));
                enqueueBatchDelivery(dest, -1, 0, lst);
            }
        }
    }

    public void ensureDeliveryFor(BlockPos dest, Map<ItemKey,Integer> needs, int queueId) {
        if (world == null || world.isRemote || dest == null || needs == null || needs.isEmpty()) return;

        List<Map.Entry<ItemKey,Integer>> miss = new ArrayList<>();

        for (Map.Entry<ItemKey,Integer> e : needs.entrySet()) {
            int want  = Math.max(1, e.getValue());
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

    public void ensureDeliveryForExact(BlockPos dest, Map<ItemKey,Integer> needs, int queueId) {
        if (world == null || world.isRemote || dest == null || needs == null || needs.isEmpty()) return;

        List<Map.Entry<ItemKey,Integer>> miss = new ArrayList<>();

        for (Map.Entry<ItemKey,Integer> e : needs.entrySet()) {
            int want  = Math.max(1, e.getValue());
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

    public void ensureDeliveryFor(BlockPos dest, Map<ItemKey,Integer> needs) {
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

        // зеркала (активные)
        NBTTagList ml = new NBTTagList();
        for (MirrorSlot m : activeMirrors) {
            NBTTagCompound c = new NBTTagCompound();
            c.setInteger("ring", m.ring);
            c.setInteger("slot", m.slot);
            c.setLong("phase", m.phase);
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
        calcRange      = nbt.getInteger("calcRange");
        calcMirrorCap  = nbt.getInteger("calcMirrorCap");
        calcComputeCap = nbt.getInteger("calcComputeCap");

        boundTerminals.clear();
        boundRequesters.clear();
        if (nbt.hasKey("boundTerminals", 9)) {
            NBTTagList t = nbt.getTagList("boundTerminals", 4);
            for (int i=0;i<t.tagCount();i++) boundTerminals.add(BlockPos.fromLong(((NBTTagLong)t.get(i)).getLong()));
        }
        if (nbt.hasKey("boundRequesters", 9)) {
            NBTTagList r = nbt.getTagList("boundRequesters", 4);
            for (int i=0;i<r.tagCount();i++) boundRequesters.add(BlockPos.fromLong(((NBTTagLong)r.get(i)).getLong()));
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
                if (te instanceof TilePatternRequester) requesters.add(rp.toImmutable());
            }
        }

        // зеркала
        for (int rr=0; rr<RINGS; rr++) Arrays.fill(slotBusy[rr], false);
        activeMirrors.clear();
        if (nbt.hasKey(TAG_MIRRORS, 9)) {
            NBTTagList ml = nbt.getTagList(TAG_MIRRORS, 10);
            for (int i=0;i<ml.tagCount();i++) {
                NBTTagCompound c = ml.getCompoundTagAt(i);
                int r = Math.max(0, Math.min(RINGS-1, c.getInteger("ring")));
                int s = Math.max(0, Math.min(SLOTS_PER_RING-1, c.getInteger("slot")));
                long ph = c.getLong("phase");
                if (!slotBusy[r][s]) {
                    slotBusy[r][s] = true;
                    activeMirrors.add(new MirrorSlot(r,s,ph));
                }
            }
        }
        pendingEjects.clear();
        if (nbt.hasKey(TAG_PEND_EJECTS, 9)) {
            NBTTagList pe = nbt.getTagList(TAG_PEND_EJECTS, 10);
            for (int i=0;i<pe.tagCount();i++) {
                NBTTagCompound c = pe.getCompoundTagAt(i);
                int r = Math.max(0, Math.min(RINGS-1, c.getInteger("ring")));
                int s = Math.max(0, Math.min(SLOTS_PER_RING-1, c.getInteger("slot")));
                long st = c.getLong("start");
                // слот должен считаться занятым, чтобы не залезли другие
                slotBusy[r][s] = true;
                pendingEjects.add(new PendingEject(r, s, st));
            }
        }
        renderSeed = nbt.getLong(TAG_RENDER_SEED);
        staleSweepTicker = 0;
    }

    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket() {
        return new net.minecraft.network.play.server.SPacketUpdateTileEntity(getPos(), 0, getUpdateTag());
    }
    @Override public void onDataPacket(net.minecraft.network.NetworkManager net, net.minecraft.network.play.server.SPacketUpdateTileEntity pkt) {
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

    /** Агрегация "как в TC": только PROVIDE-печати владельца. */
    public LinkedHashMap<ItemKey, Integer> getReachableCatalog() {
        LinkedHashMap<ItemKey, Integer> out = new LinkedHashMap<>();
        if (world == null || world.isRemote) return out;

        for (SealEntity se : SealHandler.getSealsInRange(world, pos, 32)) {
            if (!(se.getSeal() instanceof SealProvide)) continue;
            if (this.ownerUuid != null && !this.ownerUuid.equals(se.getOwner())) continue;

            IItemHandler ih = ThaumcraftInvHelper.getItemHandlerAt(world, se.getSealPos().pos, se.getSealPos().face);
            if (ih == null) continue;

            SealProvide provide = (SealProvide) se.getSeal();
            for (int s = 0; s < ih.getSlots(); s++) {
                ItemStack st = ih.getStackInSlot(s);
                if (st.isEmpty()) continue;
                if (!provide.matchesFilters(st)) continue;

                ItemKey key = normKeyForCatalog(st);
                out.merge(key, st.getCount(), Integer::sum);
            }
        }
        return out;
    }

    /** Сводный список крафтабельных результатов. */
    public List<ItemStack> getCraftablesCatalog() {
        LinkedHashMap<ItemKey, Integer> map = new LinkedHashMap<>();
        if (world == null || world.isRemote) return Collections.emptyList();

        for (BlockPos rp : requesters) {
            TileEntity te = world.getTileEntity(rp);
            if (!(te instanceof TilePatternRequester)) continue;

            List<ItemStack> outs = ((TilePatternRequester) te).listCraftableResults();
            if (outs == null || outs.isEmpty()) continue;

            for (ItemStack s : outs) {
                if (s == null || s.isEmpty()) continue;
                ItemKey k = ItemKey.of(s);
                int outCount = s.getCount() <= 0 ? 1 : s.getCount();
                map.put(k, Math.max(map.getOrDefault(k, 0), outCount));
            }
        }

        ArrayList<ItemStack> result = new ArrayList<>(map.size());
        for (Map.Entry<ItemKey,Integer> e : map.entrySet()) {
            ItemStack one = e.getKey().toStack(1);
            one.setCount(e.getValue());
            result.add(one);
        }
        return result;
    }

    /* ===================== Реквестеры (крафтеры) ===================== */
    private final Set<BlockPos> requesters = new HashSet<>();
    public void registerRequester(BlockPos pos) { if (pos != null) { requesters.add(pos.toImmutable()); markDirty(); } }
    public void unregisterRequester(BlockPos pos) { if (pos != null && requesters.remove(pos)) markDirty(); }

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

    public net.minecraft.world.World getWorld() {
        return this.world; // если класс не является TileEntity, верните ссылку на мир другим способом
    }
}
