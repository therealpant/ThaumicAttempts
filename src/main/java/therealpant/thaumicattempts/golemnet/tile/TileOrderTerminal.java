package therealpant.thaumicattempts.golemnet.tile;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.common.capabilities.Capability;

import net.minecraftforge.common.util.Constants;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import therealpant.thaumicattempts.api.CraftOrderApi;
import therealpant.thaumicattempts.api.ICraftEndpoint;
import therealpant.thaumicattempts.api.ITerminalOrderAcceptor;
import therealpant.thaumicattempts.api.TerminalOrderApi;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.items.ItemTCEssentiaContainer;
import therealpant.thaumicattempts.util.ItemKey;
import therealpant.thaumicattempts.golemnet.tile.TileInfusionRequester;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Терминал заказов/склада.
 * - Локальный буфер 3×3
 * - Взаимодействие с MirrorManager
 * - Отдаёт GUI-снапшоты каталога постранично (5×7) по новому протоколу: snapshotId + pageIndex0.
 */
public class TileOrderTerminal extends TileEntity implements ITickable {

    /* ===== Клиентская анимация книги ===== */
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT) public int tickCount;
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT) public float pageFlip, pageFlipPrev;
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT) public float bookSpread, bookSpreadPrev;
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT) public float bookRotation, bookRotationPrev;
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT) public float tRot;
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT) private float flipT, flipA;
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT) public float bookPitch, bookPitchPrev;
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT) private float pitchTarget;
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT) private final java.util.Random rand = new java.util.Random();

    /* ===== Pending-базовые снимки для выдачи со склада ===== */
    private final LinkedHashMap<ItemKey, Integer> deliveryBaselines = new LinkedHashMap<>();

    /* ===== Черновик/пэндинг (до 9 позиций) ===== */
    private final LinkedHashMap<ItemKey, Integer> draftDelivery   = new LinkedHashMap<>();
    private final LinkedHashMap<ItemKey, Integer> draftCraft      = new LinkedHashMap<>();
    private final LinkedHashMap<ItemKey, Integer> pendingDelivery = new LinkedHashMap<>();
    private final LinkedHashMap<ItemKey, Integer> pendingCraft    = new LinkedHashMap<>();

    private static final class TerminalOrderRequest {
        final BlockPos pos;
        final int slot;
        final int count;
        private TerminalOrderRequest(BlockPos pos, int slot, int count) {            this.pos = pos;
            this.slot = slot;
            this.count = count;
        }
    }

    private static final class InfusionOrderTarget {
        final BlockPos pos;
        final int slot;
        InfusionOrderTarget(BlockPos pos, int slot) {
            this.pos = pos;
            this.slot = slot;
        }
    }

    /* ===== Буфер 3×3 ===== */
    private final ItemStackHandler buffer = new ItemStackHandler(15) {
        @Override protected void onContentsChanged(int slot) {
            markDirty();
            if (world != null && !world.isRemote) {
                reconcilePendingByBufferInstant();
                world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
            }
        }
    };

    /* ===== Кристаллы TC6 ===== */
    private static boolean isCrystal(ItemStack s) {
        return s != null && !s.isEmpty() && s.getItem() == thaumcraft.api.items.ItemsTC.crystalEssence;
    }
    @Nullable
    private static thaumcraft.api.aspects.Aspect aspectOf(ItemStack s) {
        if (!isCrystal(s)) return null;
        AspectList al = ((ItemTCEssentiaContainer) thaumcraft.api.items.ItemsTC.crystalEssence).getAspects(s);
        return (al != null && al.size() == 1) ? al.getAspects()[0] : null;
    }
    private static boolean crystalSame(ItemStack a, ItemStack b) {
        thaumcraft.api.aspects.Aspect x = aspectOf(a), y = aspectOf(b);
        return x != null && x == y;
    }

    /* ===== Служебные флаги ===== */
    private boolean inRecon = false;
    private boolean needEnsureWithManager = false;

    private int tickCounter = 0;
    private int lastEnsureTick = -9999;

    public ItemStackHandler getBuffer() { return buffer; }
    public net.minecraftforge.items.IItemHandler getBufferHandler() { return buffer; }

    /* ===== Утилиты ===== */
    private static void trimTo9(LinkedHashMap<ItemKey, Integer> map) {
        if (map.size() <= 9) return;
        Iterator<ItemKey> it = map.keySet().iterator();
        int i = 0;
        while (it.hasNext()) {
            it.next();
            if (++i > 9) it.remove();
        }
    }
    private static void addToMap(Map<ItemKey, Integer> map, ItemKey k, int delta) {
        if (delta == 0) return;
        int v = map.getOrDefault(k, 0) + delta;
        if (v <= 0) map.remove(k); else map.put(k, v);
    }
    private static List<ItemStack> mapToNineStacks(Map<ItemKey, Integer> src) {
        ArrayList<ItemStack> out = new ArrayList<>(9);
        for (Map.Entry<ItemKey, Integer> e : src.entrySet()) {
            out.add(e.getKey().toStack(Math.max(1, e.getValue())));
            if (out.size() == 9) break;
        }
        while (out.size() < 9) out.add(ItemStack.EMPTY);
        return out;
    }

    public void dropContents() {
        if (world == null || world.isRemote) return;
        for (int i = 0; i < buffer.getSlots(); i++) {
            ItemStack stack = buffer.getStackInSlot(i);
            if (!stack.isEmpty()) {
                net.minecraft.inventory.InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), stack.copy());
                buffer.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }
    private boolean isFrozen(boolean craftTab) {
        Map<ItemKey, Integer> pend = craftTab ? pendingCraft : pendingDelivery;
        return !pend.isEmpty();
    }

    /* ===== Доступ к MirrorManager ===== */
    private @Nullable BlockPos managerPos;

    @Nullable public BlockPos getManagerPos() { return managerPos; }
    public void clearManagerPosFromManager(BlockPos mgrPos) {
        if (mgrPos != null && mgrPos.equals(this.managerPos)) { this.managerPos = null; markDirty(); }
    }
    public void setManagerPos(@Nullable BlockPos pos) {
        if (!Objects.equals(this.managerPos, pos)) { this.managerPos = (pos == null ? null : pos.toImmutable()); markDirty(); }
    }

    /* ===== Публичные API для GUI ===== */
    public Map<ItemKey, Integer> getDraftSnapshot(boolean craftTab) { return new LinkedHashMap<>(craftTab ? draftCraft : draftDelivery); }
    public List<ItemStack> getPendingSnapshot(boolean craftTab) { return mapToNineStacks(craftTab ? pendingCraft : pendingDelivery); }

    /* ===== Подсчёты ===== */
    private int getAvailableFromManager(ItemKey k) {
        if (world == null || managerPos == null) return 0;
        TileEntity te = world.getTileEntity(managerPos);
        if (!(te instanceof TileMirrorManager)) return 0;
        LinkedHashMap<ItemKey, Integer> cat = ((TileMirrorManager) te).getReachableCatalog();
        return countInCatalogRelaxed(cat, k.toStack(1));
    }

    private static int countInCatalogRelaxed(Map<ItemKey,Integer> cat, ItemStack like) {
        if (cat == null || cat.isEmpty() || like == null || like.isEmpty()) return 0;
        int sum = 0;

        if (isCrystal(like)) {
            thaumcraft.api.aspects.Aspect a = aspectOf(like);
            if (a == null) return 0;
            for (Map.Entry<ItemKey,Integer> e : cat.entrySet()) {
                ItemStack one = e.getKey().toStack(1);
                if (isCrystal(one) && a.equals(aspectOf(one))) sum += Math.max(1, e.getValue());
            }
            return sum;
        }

        if (like.getMaxStackSize() == 1) {
            for (Map.Entry<ItemKey,Integer> e : cat.entrySet()) {
                ItemStack one = e.getKey().toStack(1);
                if (!one.isEmpty() && one.getItem() == like.getItem()) sum += Math.max(1, e.getValue());
            }
            return sum;
        }

        for (Map.Entry<ItemKey,Integer> e : cat.entrySet()) {
            ItemStack one = e.getKey().toStack(1);
            if (!one.isEmpty() && ItemHandlerHelper.canItemStacksStackRelaxed(one, like)) sum += Math.max(1, e.getValue());
        }
        return sum;
    }

    /* ===== Черновик/отправка ===== */
    public void adjustDraft(ItemStack keyOne, int delta, boolean craftTab) {
        if (keyOne == null || keyOne.isEmpty() || delta == 0) return;
        if (isFrozen(craftTab)) return;

        ItemKey k = ItemKey.of(keyOne);
        Map<ItemKey, Integer> map = craftTab ? draftCraft : draftDelivery;

        if (!craftTab && delta > 0) {
            int available = getAvailableFromManager(k);
            int alreadyPlanned = map.getOrDefault(k, 0) + pendingDelivery.getOrDefault(k, 0);
            int room = Math.max(0, available - alreadyPlanned);
            if (room <= 0) return;
            delta = Math.min(delta, room);
        }

        addToMap(map, k, delta);
        markDirty();
    }

    public void submitDraft(boolean craftTab) {
        Map<ItemKey, Integer> draft = craftTab ? draftCraft : draftDelivery;
        Map<ItemKey, Integer> pend  = craftTab ? pendingCraft : pendingDelivery;
        if (draft.isEmpty()) { sendSnapshotToViewers(craftTab); return; }

        int freeDistinct = Math.max(0, 9 - pend.size());
        List<Map.Entry<ItemKey, Integer>> movedRaw = new ArrayList<>();
        List<TerminalOrderRequest> directTerminalOrders = new ArrayList<>();

        for (Map.Entry<ItemKey, Integer> e : new ArrayList<>(draft.entrySet())) {
            ItemKey key = e.getKey();
            int amt = Math.max(1, e.getValue());

            ItemStack keyStack = key.toStack(1);
            if (TerminalOrderApi.isOrderIcon(keyStack)) {
                BlockPos target = TerminalOrderApi.getOrderIconPos(keyStack);
                int slot = TerminalOrderApi.getOrderIconSlot(keyStack);
                if (target != null && slot >= 0) {
                    directTerminalOrders.add(new TerminalOrderRequest(target, slot, amt));
                }
                draft.remove(key);
                continue;
            }

            int toMove = amt;
            if (!craftTab) {
                int available = getAvailableFromManager(key);
                int havePend  = pendingDelivery.getOrDefault(key, 0);
                int room = Math.max(0, available - havePend);
                toMove = Math.min(toMove, room);
            }
            if (toMove <= 0) continue;

            boolean alreadyInPend = pend.containsKey(key);
            if (!alreadyInPend && freeDistinct <= 0) continue;

            addToMap(pend, key, toMove);

            if (!craftTab && !alreadyInPend && !deliveryBaselines.containsKey(key)) {
                int base = countInBufferLike(key.toStack(1));
                deliveryBaselines.put(key, base);
            }

            int remain = e.getValue() - toMove;
            if (remain > 0) draft.put(key, remain); else draft.remove(key);

            movedRaw.add(new AbstractMap.SimpleEntry<>(key, toMove));
            if (!alreadyInPend) freeDistinct--;
        }

        if (!directTerminalOrders.isEmpty()) {
            markDirty();
            for (TerminalOrderRequest order : directTerminalOrders) {
                TileEntity te = world.getTileEntity(order.pos);
                if (te instanceof ITerminalOrderAcceptor) {
                    ((ITerminalOrderAcceptor) te).triggerFromTerminal(order.slot, order.count);
                }
            }
        }

        if (movedRaw.isEmpty()) {
            if (directTerminalOrders.isEmpty()) {
                markDirty();
            }
            sendSnapshotToViewers(craftTab);
            return;
        }

        LinkedHashMap<ItemKey, Integer> merged = new LinkedHashMap<>();
        for (Map.Entry<ItemKey, Integer> e : movedRaw) merged.merge(e.getKey(), e.getValue(), Integer::sum);
        List<Map.Entry<ItemKey, Integer>> moved = new ArrayList<>(merged.entrySet());

        markDirty();

        TileEntity mte = (managerPos == null) ? null : world.getTileEntity(managerPos);
        if (mte instanceof TileMirrorManager) {
            TileMirrorManager mgr = (TileMirrorManager) mte;
            final int QUEUE_ID = craftTab ? 1 : 0;

            if (!craftTab) {
                mgr.enqueueBatchDelivery(this.pos, -1, QUEUE_ID, moved);
                if (!pendingDelivery.isEmpty()) {
                    mgr.ensureDeliveryFor(this.pos, new LinkedHashMap<>(pendingDelivery));
                }
            } else {
                // CRAFT вкладка, единая логика
                List<Map.Entry<ItemKey,Integer>> toManager = new ArrayList<>();

                for (Map.Entry<ItemKey,Integer> e : moved) {
                    ItemKey key = e.getKey();
                    int n = Math.max(1, e.getValue());
                    toManager.add(new AbstractMap.SimpleEntry<>(key, n));
                }

                if (!toManager.isEmpty()) {
                    mgr.enqueueBatchCraft(
                            this.pos, -1, QUEUE_ID, moved,
                            key -> findCraftEndpointFor(mgr, key.toStack(1))  // ищет любой ICraftEndpoint
                    );
                }
            }
        }

            sendSnapshotToViewers(craftTab);
    }

    @Nullable
    private BlockPos findCraftEndpointFor(TileMirrorManager mgr, ItemStack result) {
        if (result == null || result.isEmpty()) return null;
        Set<BlockPos> reqs = mgr.getRequestersSnapshot();
        if (reqs == null || reqs.isEmpty()) return null;

        for (BlockPos rp : reqs) {
            TileEntity te = world.getTileEntity(rp);
            if (!(te instanceof ICraftEndpoint)) continue;

            ICraftEndpoint ep = (ICraftEndpoint) te;
            if (!CraftOrderApi.isCrafter(ep)) continue;
            List<ItemStack> outs = ep.listCraftableResults();
            if (outs == null || outs.isEmpty()) continue;

            for (ItemStack out : outs) {
                if (out == null || out.isEmpty()) continue;
                boolean same = (result.getMaxStackSize() == 1)
                        ? (out.getItem() == result.getItem()
                        && (!out.getHasSubtypes() || out.getMetadata() == result.getMetadata()))
                        : ItemHandlerHelper.canItemStacksStackRelaxed(out, result);
                if (same) return rp;
            }
        }
        return null;
    }

    @Nullable
    private ItemKey findMatchingKeyRelaxed(Map<ItemKey, Integer> map, ItemStack like) {
        if (like == null || like.isEmpty() || map == null || map.isEmpty()) return null;
        ItemKey exact = ItemKey.of(like);
        if (map.containsKey(exact)) return exact;

        for (ItemKey k : map.keySet()) {
            ItemStack one = k.toStack(1);
            boolean match;
            if (isCrystal(one) || isCrystal(like)) {
                match = isCrystal(one) && isCrystal(like) && crystalSame(one, like);
            } else {
                match = (one.getMaxStackSize() == 1)
                        ? (one.getItem() == like.getItem())
                        : ItemHandlerHelper.canItemStacksStackRelaxed(one, like);
            }
            if (match) return k;
        }
        return null;
    }

    public void onDelivered(ItemStack like, int count) {
        if (like == null || like.isEmpty() || count <= 0) return;

        int left = count;

        ItemKey keyDel = findMatchingKeyRelaxed(pendingDelivery, like);
        if (keyDel != null) {
            int have = Math.max(0, pendingDelivery.getOrDefault(keyDel, 0));
            if (have > 0) {
                int take = Math.min(have, left);
                addToMap(pendingDelivery, keyDel, -take);
                left -= take;

                if (!pendingDelivery.containsKey(keyDel)) {
                    deliveryBaselines.remove(keyDel);
                } else {
                    int base = Math.max(0, deliveryBaselines.getOrDefault(keyDel, 0));
                    int haveInBuf = Math.max(0, countInBufferLike(like));
                    int newBase = Math.min(base + take, haveInBuf);
                    deliveryBaselines.put(keyDel, newBase);
                }
            }
        }

        if (left > 0) {
            ItemKey keyCr = findMatchingKeyRelaxed(pendingCraft, like);
            if (keyCr != null) {
                int have = Math.max(0, pendingCraft.getOrDefault(keyCr, 0));
                if (have > 0) {
                    int take = Math.min(have, left);
                    addToMap(pendingCraft, keyCr, -take);
                    left -= take;
                }
            }
        }

        if (world != null && !world.isRemote && managerPos != null && keyDel != null) {
            TileEntity te = world.getTileEntity(managerPos);
            if (te instanceof TileMirrorManager) {
                int remainWantDel = Math.max(0, pendingDelivery.getOrDefault(keyDel, 0));
                ((TileMirrorManager) te).reconcileForDelivery(this.pos, like, remainWantDel);
            }
        }

        markDirty();
        sendSnapshotToViewers(false);
        sendSnapshotToViewers(true);
    }

    /* ===== Capabilities ===== */
    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return true;
        return super.hasCapability(capability, facing);
    }
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return (T) buffer;
        return super.getCapability(capability, facing);
    }

    /* ===== Tick ===== */
    @Override
    public void update() {
        if (world == null) return;

        if (world.isRemote) { clientTickBookAnimation(); return; }

        tickCounter++;

        if (needEnsureWithManager) {
            ensurePendingWithManager(0);
            needEnsureWithManager = false;
        }

        ensurePendingWithManager(30);

        if ((tickCounter % 20) == 0) autoReconcilePendingByBuffer();
    }

    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    private void clientTickBookAnimation() {
        this.bookSpreadPrev = this.bookSpread;
        this.pageFlipPrev   = this.pageFlip;
        this.bookRotationPrev = this.bookRotation;
        this.bookPitchPrev  = this.bookPitch;

        this.tRot = 0.0F;
        this.bookRotation = 0.0F;

        float targetOpen = 1.0F;
        this.bookSpread += (targetOpen - this.bookSpread) * 0.2F;
        this.bookSpread = MathHelper.clamp(this.bookSpread, 0.0F, 1.0F);

        this.tickCount++;
        this.pageFlip += 0.1F;

        float pitchTarget = (float) Math.toRadians(-22.5D);
        float dPitch = pitchTarget - this.bookPitch;
        dPitch = MathHelper.clamp(dPitch, -0.08F, 0.08F);
        this.bookPitch += dPitch;
    }

    private void ensurePendingWithManager(int periodTicks) {
        if (managerPos == null) return;
        if (periodTicks > 0 && (tickCounter - lastEnsureTick) < periodTicks) return;

        TileEntity te = world.getTileEntity(managerPos);
        if (!(te instanceof TileMirrorManager)) return;

        TileMirrorManager mgr = (TileMirrorManager) te;
        if (!pendingDelivery.isEmpty()) {
            mgr.ensureDeliveryForExact(this.pos, new LinkedHashMap<>(pendingDelivery), 0);
        }

        lastEnsureTick = tickCounter;
    }

    private void reconcilePendingByBufferInstant() {
        if (pendingDelivery.isEmpty()) return;

        boolean changed = false;

        TileMirrorManager mgr = null;
        if (managerPos != null) {
            TileEntity te = world.getTileEntity(managerPos);
            if (te instanceof TileMirrorManager) mgr = (TileMirrorManager) te;
        }

        final boolean outerCall = !inRecon;
        inRecon = true;
        try {
            List<Map.Entry<ItemKey,Integer>> snapshot = new ArrayList<>(pendingDelivery.entrySet());
            Map<ItemKey,Integer> toPut   = new LinkedHashMap<>();
            Set<ItemKey>         toRemove = new HashSet<>();

            for (Map.Entry<ItemKey, Integer> e : snapshot) {
                ItemKey k = e.getKey();
                int pending = Math.max(0, e.getValue());
                if (pending <= 0) { toRemove.add(k); continue; }

                ItemStack like1 = k.toStack(1);
                int baseline = Math.max(0, deliveryBaselines.getOrDefault(k, 0));
                int have = Math.max(0, countInBufferLike(like1));
                int delta = Math.max(0, have - baseline);

                if (delta > 0) {
                    int take = Math.min(delta, pending);
                    int left = pending - take;

                    if (left > 0) toPut.put(k, left); else toRemove.add(k);

                    int newBase = Math.min(baseline + take, have);
                    deliveryBaselines.put(k, newBase);

                    if (mgr != null) {
                        if (outerCall) mgr.reconcileForDelivery(this.pos, like1, Math.max(0, left));
                        else needEnsureWithManager = true;
                    }
                    changed = true;
                }
            }

            for (ItemKey k : toRemove) { pendingDelivery.remove(k); deliveryBaselines.remove(k); }
            for (Map.Entry<ItemKey,Integer> put : toPut.entrySet()) pendingDelivery.put(put.getKey(), put.getValue());

        } finally {
            inRecon = false;
        }

        if (changed) { markDirty(); sendSnapshotToViewers(false); }
    }

    private void autoReconcilePendingByBuffer() { reconcilePendingByBufferInstant(); }

    private void sendDraftSnapshotTo(EntityPlayerMP who, boolean craftTab) {
        Map<ItemKey, Integer> draftMap = craftTab ? draftCraft : draftDelivery;
        Map<ItemKey, Integer> pendMap  = craftTab ? pendingCraft : pendingDelivery;

        List<ItemStack> draft9 = new ArrayList<>(9);
        List<Integer> draftCnt = new ArrayList<>(9);
        for (Map.Entry<ItemKey, Integer> e : draftMap.entrySet()) {
            draft9.add(e.getKey().toStack(1));
            draftCnt.add(Math.max(1, e.getValue()));
            if (draft9.size() == 9) break;
        }
        while (draft9.size() < 9) { draft9.add(ItemStack.EMPTY); draftCnt.add(0); }

        List<ItemStack> pending9 = new ArrayList<>(9);
        List<Integer> pendingCnt = new ArrayList<>(9);
        for (Map.Entry<ItemKey, Integer> e : pendMap.entrySet()) {
            pending9.add(e.getKey().toStack(1));
            pendingCnt.add(Math.max(1, e.getValue()));
            if (pending9.size() == 9) break;
        }
        while (pending9.size() < 9) { pending9.add(ItemStack.EMPTY); pendingCnt.add(0); }

        therealpant.thaumicattempts.golemnet.net.msg.S2C_DraftSnapshot pkt =
                new therealpant.thaumicattempts.golemnet.net.msg.S2C_DraftSnapshot(craftTab, draft9, draftCnt, pending9, pendingCnt);

        therealpant.thaumicattempts.ThaumicAttempts.NET.sendTo(pkt, who);
    }

    /* ===== NBT ===== */
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);

        nbt.setTag("buffer", buffer.serializeNBT());
        if (managerPos != null) nbt.setLong("manager", managerPos.toLong());

        nbt.setTag("draftD", writeMap(draftDelivery));
        nbt.setTag("draftC", writeMap(draftCraft));
        nbt.setTag("pendD",  writeMap(pendingDelivery));
        nbt.setTag("pendC",  writeMap(pendingCraft));

        NBTTagList bl = new NBTTagList();
        for (Map.Entry<ItemKey, Integer> e : deliveryBaselines.entrySet()) {
            NBTTagCompound t = new NBTTagCompound();
            t.setTag("k", e.getKey().toStack(1).serializeNBT());
            t.setInteger("b", Math.max(0, e.getValue()));
            bl.appendTag(t);
        }
        nbt.setTag("deliveryBaselines", bl);

        return nbt;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);

        if (nbt.hasKey("buffer")) buffer.deserializeNBT(nbt.getCompoundTag("buffer"));
        managerPos = nbt.hasKey("manager") ? BlockPos.fromLong(nbt.getLong("manager")) : null;

        readMap(nbt.getTagList("draftD", 10), draftDelivery);
        readMap(nbt.getTagList("draftC", 10), draftCraft);
        readMap(nbt.getTagList("pendD",  10), pendingDelivery);
        readMap(nbt.getTagList("pendC",  10), pendingCraft);

        deliveryBaselines.clear();
        if (nbt.hasKey("deliveryBaselines")) {
            NBTTagList bl = nbt.getTagList("deliveryBaselines", 10);
            for (int i = 0; i < bl.tagCount(); i++) {
                NBTTagCompound t = bl.getCompoundTagAt(i);
                ItemStack st = new ItemStack(t.getCompoundTag("k"));
                int b = Math.max(0, t.getInteger("b"));
                if (!st.isEmpty()) deliveryBaselines.put(ItemKey.of(st), b);
            }
        }
    }

    private void triggerInfusionOrder(InfusionOrderTarget target, int count) {
        if (world == null || target == null || count <= 0) return;
        TileEntity te = world.getTileEntity(target.pos);
        if (te instanceof TileInfusionRequester) {
            ((TileInfusionRequester) te).triggerExternalRequest(target.slot, count);
        }
    }

    private static NBTTagList writeMap(LinkedHashMap<ItemKey, Integer> map) {
        NBTTagList lst = new NBTTagList();
        for (Map.Entry<ItemKey, Integer> e : map.entrySet()) {
            NBTTagCompound t = new NBTTagCompound();
            t.setTag("k", e.getKey().toStack(1).serializeNBT());
            t.setInteger("c", e.getValue());
            lst.appendTag(t);
        }
        return lst;
    }
    private static void readMap(NBTTagList lst, LinkedHashMap<ItemKey, Integer> out) {
        out.clear();
        for (int i = 0; i < lst.tagCount(); i++) {
            NBTTagCompound t = lst.getCompoundTagAt(i);
            ItemStack st = new ItemStack(t.getCompoundTag("k"));
            int c = t.getInteger("c");
            if (!st.isEmpty() && c > 0) out.put(ItemKey.of(st), c);
        }
        trimTo9(out);
    }

    public int tryInsertToBuffer(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        ItemStack remain = ItemHandlerHelper.insertItem(buffer, stack.copy(), false);
        int accepted = stack.getCount() - (remain.isEmpty() ? 0 : remain.getCount());
        if (accepted > 0) markDirty();
        return accepted;
    }

    private void sendSnapshotToViewers(boolean craftTab) {
        if (world == null || world.isRemote) return;

        List<EntityPlayerMP> viewers =
                world.getPlayers(EntityPlayerMP.class,
                        p -> p.openContainer instanceof therealpant.thaumicattempts.golemnet.container.ContainerOrderTerminal);

        for (EntityPlayerMP p : viewers) {
            sendDraftSnapshotTo(p, craftTab);
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (world != null && !world.isRemote && managerPos != null) {
            TileEntity te = world.getTileEntity(managerPos);
            if (te instanceof TileMirrorManager) ((TileMirrorManager) te).cancelAllForDestination(this.pos);
        }
    }
    @Override public void onChunkUnload() { invalidate(); }

    /* ===================================================================== */
    /* ===================== ОТВЕТ НА ЗАПРОС КАТАЛОГА ====================== */
    /* ===================================================================== */



    public void handleCatalogPageRequest(EntityPlayerMP player, boolean craftTab, long snapshotId, int pageIndex0, String search) {
        if (world == null || world.isRemote) return;

        if (pageIndex0 < 0) pageIndex0 = 0;
        if (search == null) search = "";
        final String q = search.trim().toLowerCase(java.util.Locale.ROOT);

        // --- 1) Создать/зафиксировать snapshotId и ОБЯЗАТЕЛЬНО выслать его клиенту при создании
        final long sid;
        if (snapshotId < 0) {
            long mix = (world.getTotalWorldTime() << 1)
                    ^ this.pos.toLong()
                    ^ player.getUniqueID().getMostSignificantBits()
                    ^ (craftTab ? 0xCAFEBABEL : 0xDEADBEEFL);
            long positive = mix & Long.MAX_VALUE; // снимем знак, GUI ждёт sid > 0
            if (positive == 0L) positive = 1L;    // на всякий
            sid = positive;
            therealpant.thaumicattempts.ThaumicAttempts.NET.sendTo(
                    new therealpant.thaumicattempts.golemnet.net.msg.S2C_SnapshotCreated(craftTab, sid),
                    player
            );
        } else {
            sid = snapshotId;
        }

        // --- 2) Достаём менеджер
        final int COLS = 5, ROWS = 7, PER_PAGE = COLS * ROWS;
        TileEntity teMgr = (managerPos == null) ? null : world.getTileEntity(managerPos);
        TileMirrorManager mgr = (teMgr instanceof TileMirrorManager) ? (TileMirrorManager) teMgr : null;

        // Если менеджера нет — отправляем пустую страницу, но с валидным sid
        if (mgr == null) {
            therealpant.thaumicattempts.ThaumicAttempts.NET.sendTo(
                    new therealpant.thaumicattempts.golemnet.net.msg.S2C_CatalogPage(
                            craftTab, sid, 1, 1, false,
                            java.util.Collections.<ItemStack>emptyList(),
                            java.util.Collections.<Integer>emptyList(),
                            java.util.Collections.<Integer>emptyList(),
                            java.util.Collections.<Boolean>emptyList()
                    ),
                    player
            );
            sendDraftSnapshotTo(player, false);
            sendDraftSnapshotTo(player, true);
            return;
        }

        if (craftTab) {
            // ===== CRAFT вкладка =====
            java.util.List<ItemStack> all = mgr.getCraftablesCatalog();
            if (all == null) all = java.util.Collections.emptyList();

            java.util.List<ItemStack> filtered = new java.util.ArrayList<ItemStack>();
            java.util.List<Integer> perCraft  = new java.util.ArrayList<Integer>();
            for (ItemStack s : all) {
                if (s == null || s.isEmpty()) continue;
                String name = s.getDisplayName();
                if (!q.isEmpty() && (name == null || !name.toLowerCase(java.util.Locale.ROOT).contains(q))) continue;
                filtered.add(s);
                perCraft.add(Math.max(1, s.getCount()));
            }

            int totalPages = Math.max(1, (filtered.size() + PER_PAGE - 1) / PER_PAGE);
            int page = Math.min(pageIndex0, Math.max(0, totalPages - 1));
            int from = page * PER_PAGE;
            int to   = Math.min(from + PER_PAGE, filtered.size());

            java.util.List<ItemStack> items35 = new java.util.ArrayList<ItemStack>(PER_PAGE);
            java.util.List<Integer>   counts35 = new java.util.ArrayList<Integer>(PER_PAGE);
            for (int i = from; i < to; i++) {
                ItemStack s = filtered.get(i);
                ItemStack icon = s.copy(); icon.setCount(1);
                items35.add(icon);
                counts35.add(perCraft.get(i));
            }
            while (items35.size() < PER_PAGE) { items35.add(ItemStack.EMPTY); counts35.add(0); }

            therealpant.thaumicattempts.golemnet.net.msg.PageCraftCalc.CalcResult cr =
                    therealpant.thaumicattempts.golemnet.net.msg.PageCraftCalc.computeMakeForPage(
                            mgr, items35, mgr.getReachableCatalog()
                    );

            boolean hasMore = (page + 1) < totalPages;

            therealpant.thaumicattempts.ThaumicAttempts.NET.sendTo(
                    new therealpant.thaumicattempts.golemnet.net.msg.S2C_CatalogPage(
                            true, sid, page + 1, totalPages, hasMore,
                            items35, counts35,
                            (cr.makeCounts != null ? cr.makeCounts : java.util.Collections.<Integer>emptyList()),
                            (cr.possible   != null ? cr.possible   : java.util.Collections.<Boolean>emptyList())
                    ),
                    player
            );

        } else {
            // ===== DELIVERY вкладка =====
            java.util.LinkedHashMap<ItemKey,Integer> cat = mgr.getReachableCatalog();
            java.util.ArrayList<java.util.Map.Entry<ItemKey,Integer>> entries =
                    new java.util.ArrayList<java.util.Map.Entry<ItemKey,Integer>>(cat.entrySet());

            java.util.List<ItemStack> filtered = new java.util.ArrayList<ItemStack>();
            java.util.List<Integer>   counts   = new java.util.ArrayList<Integer>();

            for (java.util.Map.Entry<ItemKey,Integer> e : entries) {
                ItemStack s = e.getKey().toStack(1);
                if (s == null || s.isEmpty()) continue;
                String name = s.getDisplayName();
                if (!q.isEmpty() && (name == null || !name.toLowerCase(java.util.Locale.ROOT).contains(q))) continue;
                filtered.add(s);
                counts.add(Math.max(1, e.getValue()));
            }

            int totalPages = Math.max(1, (filtered.size() + PER_PAGE - 1) / PER_PAGE);
            int page = Math.min(pageIndex0, Math.max(0, totalPages - 1));
            int from = page * PER_PAGE;
            int to   = Math.min(from + PER_PAGE, filtered.size());

            java.util.List<ItemStack> items35 = new java.util.ArrayList<ItemStack>(PER_PAGE);
            java.util.List<Integer>   counts35 = new java.util.ArrayList<Integer>(PER_PAGE);
            for (int i = from; i < to; i++) {
                items35.add(filtered.get(i).copy());
                counts35.add(counts.get(i));
            }
            while (items35.size() < PER_PAGE) { items35.add(ItemStack.EMPTY); counts35.add(0); }

            boolean hasMore = (page + 1) < totalPages;

            therealpant.thaumicattempts.ThaumicAttempts.NET.sendTo(
                    new therealpant.thaumicattempts.golemnet.net.msg.S2C_CatalogPage(
                            false, sid, page + 1, totalPages, hasMore,
                            items35, counts35,
                            java.util.Collections.<Integer>emptyList(),
                            java.util.Collections.<Boolean>emptyList()
                    ),
                    player
            );
        }

        // --- 3) Синхронизация черновиков/пэндинга (как было)
        sendDraftSnapshotTo(player, false);
        sendDraftSnapshotTo(player, true);
    }



    private int countInBufferLike(ItemStack like) {
        if (like == null || like.isEmpty()) return 0;
        int total = 0;
        for (int i = 0; i < buffer.getSlots(); i++) {
            ItemStack s = buffer.getStackInSlot(i);
            if (s.isEmpty()) continue;

            if (isCrystal(like)) {
                if (isCrystal(s) && crystalSame(s, like)) total += s.getCount();
            } else if (like.getMaxStackSize() == 1) {
                if (s.getItem() == like.getItem()
                        && (!like.getHasSubtypes() || s.getMetadata() == like.getMetadata())) {
                    total += s.getCount();
                }
            } else {
                if (ItemHandlerHelper.canItemStacksStackRelaxed(s, like)) total += s.getCount();
            }
        }
        return total;
    }
}
