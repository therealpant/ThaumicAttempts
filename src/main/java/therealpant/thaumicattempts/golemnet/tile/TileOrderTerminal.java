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
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.capabilities.Capability;

import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import therealpant.thaumicattempts.api.CraftOrderApi;
import therealpant.thaumicattempts.api.ICraftEndpoint;
import therealpant.thaumicattempts.api.ITerminalOrderAcceptor;
import therealpant.thaumicattempts.api.TerminalOrderApi;
import therealpant.thaumicattempts.golemnet.logistics.NetworkOrder;
import therealpant.thaumicattempts.golemnet.logistics.OrderStatus;
import therealpant.thaumicattempts.golemnet.logistics.OrderSourceType;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.items.ItemTCEssentiaContainer;
import therealpant.thaumicattempts.util.ItemKey;
import therealpant.thaumicattempts.util.ResourceIdentity;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Терминал заказов/склада.
 * - Локальный буфер 3×3
 * - Взаимодействие с MirrorManager
 * - Отдаёт GUI-снапшоты каталога постранично (5×7) по новому протоколу: snapshotId + pageIndex0.
 */
public class TileOrderTerminal extends TileEntity implements ITickable {
    private static final Logger LOG = LogManager.getLogger("ThaumicAttempts/OrderTerminal");
    private static final Map<String, String> ASPECT_ALIASES = buildAspectAliases();

    private static Map<String, String> buildAspectAliases() {
        Map<String, String> map = new HashMap<>();

        try {
            // 1) Все зарегистрированные аспекты Thaumcraft
            for (Aspect aspect : getAllRegisteredAspects()) {
                if (aspect == null) continue;

                String tag = safeLower(aspect.getTag());
                if (tag == null || tag.isEmpty()) continue;

                // Сам internal id аспекта: aer, ignis, ordo...
                map.put(tag, tag);

                // Попытка добавить "имя" аспекта, если доступно
                String name = safeAspectName(aspect);
                if (name != null && !name.isEmpty()) {
                    map.put(name, tag);

                    // нормализованный вариант без пробелов/дефисов
                    String compact = normalizeAspectSearchToken(name);
                    if (!compact.equals(name)) {
                        map.put(compact, tag);
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        // 2) Надёжные английские алиасы для примальных аспектов
        // Их можно оставить как fallback, но уже не держать весь справочник вручную
        putIfAbsent(map, "air", "aer");
        putIfAbsent(map, "earth", "terra");
        putIfAbsent(map, "fire", "ignis");
        putIfAbsent(map, "water", "aqua");
        putIfAbsent(map, "order", "ordo");
        putIfAbsent(map, "entropy", "perditio");

        // 3) Доп. синонимы, если хочешь
        putIfAbsent(map, "void", "vacuos");
        putIfAbsent(map, "life", "victus");
        putIfAbsent(map, "death", "mortuus");
        putIfAbsent(map, "energy", "potentia");
        putIfAbsent(map, "magic", "praecantatio");
        putIfAbsent(map, "flux", "vitium");

        return map;
    }

    private static void putIfAbsent(Map<String, String> map, String key, String value) {
        if (key == null || value == null) return;
        map.putIfAbsent(key.toLowerCase(Locale.ROOT), value.toLowerCase(Locale.ROOT));
    }

    private static String safeLower(String s) {
        return s == null ? null : s.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeAspectSearchToken(String s) {
        if (s == null) return "";
        return s.trim()
                .toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replace(" ", "")
                .replace("-", "")
                .replace("_", "");
    }

    private static String safeAspectName(Aspect aspect) {
        try {
            // В TC6 обычно getName() возвращает читаемое имя аспекта
            String name = aspect.getName();
            if (name != null && !name.trim().isEmpty()) {
                return normalizeAspectSearchToken(name);
            }
        } catch (Throwable ignored) {
        }

        try {
            // fallback на локализованный description/tag, если getName() не подходит
            String tag = aspect.getTag();
            return normalizeAspectSearchToken(tag);
        } catch (Throwable ignored) {
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private static Collection<Aspect> getAllRegisteredAspects() {
        try {
            // В TC6 у Aspect обычно есть статическая коллекция/карта аспектов
            java.lang.reflect.Field f = Aspect.class.getDeclaredField("aspects");
            f.setAccessible(true);
            Object val = f.get(null);

            if (val instanceof Map) {
                return ((Map<?, Aspect>) val).values();
            }
            if (val instanceof Collection) {
                return (Collection<Aspect>) val;
            }
        } catch (Throwable ignored) {
        }

        // fallback: хотя бы 6 базовых аспектов
        List<Aspect> fallback = new ArrayList<>();
        addIfNotNull(fallback, Aspect.getAspect("aer"));
        addIfNotNull(fallback, Aspect.getAspect("terra"));
        addIfNotNull(fallback, Aspect.getAspect("ignis"));
        addIfNotNull(fallback, Aspect.getAspect("aqua"));
        addIfNotNull(fallback, Aspect.getAspect("ordo"));
        addIfNotNull(fallback, Aspect.getAspect("perditio"));
        return fallback;
    }

    private static void addIfNotNull(List<Aspect> list, Aspect aspect) {
        if (aspect != null) list.add(aspect);
    }

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


    /* ===== Черновик/пэндинг (до 9 позиций) ===== */
    private final List<TerminalOrderSlot> draftDelivery   = new ArrayList<>();
    private final List<TerminalOrderSlot> draftCraft      = new ArrayList<>();
    private final List<TerminalOrderSlot> pendingDelivery = new ArrayList<>();
    private final List<TerminalOrderSlot> pendingCraft    = new ArrayList<>();
    private final LinkedHashMap<UUID, TerminalOrderBatch> batchesById = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, TerminalOrderBinding> bindingsByRootOrderId = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, TerminalOrderBinding> bindingsBySlotId = new LinkedHashMap<>();

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
    private static int totalAmountByKey(List<TerminalOrderSlot> slots, ItemKey key) {
        if (slots == null || key == null || key == ItemKey.EMPTY) return 0;
        int out = 0;
        for (TerminalOrderSlot slot : slots) {
            if (slot == null || slot.key == null) continue;
            if (ResourceIdentity.sameResource(slot.key.toStack(1), key.toStack(1))) {
                out += Math.max(0, slot.amount);
            }
        }
        return out;
    }

    private static List<ItemStack> slotsToStacks(List<TerminalOrderSlot> slots) {
        ArrayList<ItemStack> out = new ArrayList<>(9);
        if (slots != null) {
            for (TerminalOrderSlot slot : slots) {
                if (slot == null || slot.key == null) continue;
                ItemStack st = slot.key.toStack(Math.max(1, slot.amount));
                if (st.isEmpty()) continue;
                st.setCount(Math.max(1, slot.amount));
                out.add(st);
                if (out.size() >= 9) break;
            }
        }
        while (out.size() < 9) out.add(ItemStack.EMPTY);
        return out;
    }

    private static List<Integer> slotsToCounts(List<TerminalOrderSlot> slots) {
        ArrayList<Integer> out = new ArrayList<>(9);
        if (slots != null) {
            for (TerminalOrderSlot slot : slots) {
                if (slot == null) continue;
                out.add(Math.max(1, slot.amount));
                if (out.size() >= 9) break;
            }
        }
        while (out.size() < 9) out.add(0);
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
        return !(craftTab ? pendingCraft : pendingDelivery).isEmpty();
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
    public List<TerminalOrderSlot> getDraftSnapshot(boolean craftTab) {
        return new ArrayList<>(craftTab ? draftCraft : draftDelivery);
    }

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

        for (Map.Entry<ItemKey,Integer> e : cat.entrySet()) {
            ItemStack one = e.getKey().toStack(1);
            if (!one.isEmpty() && ResourceIdentity.sameResource(one, like)) sum += Math.max(1, e.getValue());
        }
        return sum;
    }

    /* ===== Черновик/отправка ===== */
    public void adjustDraft(ItemStack keyOne, int delta, boolean craftTab) {
        if (keyOne == null || keyOne.isEmpty() || delta == 0) return;
        if (isFrozen(craftTab)) return;

        ItemKey k = ItemKey.of(keyOne);
        if (k == null || k == ItemKey.EMPTY) return;

        List<TerminalOrderSlot> draft = craftTab ? draftCraft : draftDelivery;
        List<TerminalOrderSlot> pending = craftTab ? pendingCraft : pendingDelivery;

        if (delta > 0) {
            int left = delta;
            if (!craftTab) {
                int available = getAvailableFromManager(k);
                int alreadyPlanned = totalAmountByKey(draft, k) + totalAmountByKey(pending, k);
                left = Math.min(left, Math.max(0, available - alreadyPlanned));
            }
            if (left <= 0) return;

            while (left > 0) {
                TerminalOrderSlot firstPartial = null;
                for (TerminalOrderSlot slot : draft) {
                    if (slot == null || slot.key == null) continue;
                    if (!ResourceIdentity.sameResource(slot.key.toStack(1), k.toStack(1))) continue;
                    int room = getSlotLimit(slot.key) - Math.max(0, slot.amount);
                    if (room > 0) {
                        firstPartial = slot;
                        break;
                    }
                }
                if (firstPartial != null) {
                    int room = getSlotLimit(firstPartial.key) - Math.max(0, firstPartial.amount);
                    int toAdd = Math.min(left, room);
                    if (toAdd <= 0) break;
                    firstPartial.amount += toAdd;
                    left -= toAdd;
                    continue;
                }

                if (draft.size() >= 9) break;
                int amount = Math.min(left, getSlotLimit(k));
                draft.add(new TerminalOrderSlot(UUID.randomUUID(), k, amount, world == null ? 0L : world.getTotalWorldTime()));
                left -= amount;
            }
        } else {
            int left = Math.abs(delta);
            for (int i = draft.size() - 1; i >= 0 && left > 0; i--) {
                TerminalOrderSlot slot = draft.get(i);
                if (slot == null || slot.key == null) continue;
                if (!ResourceIdentity.sameResource(slot.key.toStack(1), k.toStack(1))) continue;

                int take = Math.min(left, Math.max(0, slot.amount));
                slot.amount -= take;
                left -= take;
                if (slot.amount <= 0) draft.remove(i);
            }
        }

        markDirty();
    }

    public void submitDraft(boolean craftTab) {
        List<TerminalOrderSlot> draft = craftTab ? draftCraft : draftDelivery;
        List<TerminalOrderSlot> pend  = craftTab ? pendingCraft : pendingDelivery;
        if (draft.isEmpty()) { sendSnapshotToViewers(craftTab); return; }

        List<TerminalOrderRequest> directTerminalOrders = new ArrayList<>();
        Map<BlockPos, List<Map.Entry<ItemStack, Integer>>> directInfusionOrders = new HashMap<>();
        List<TerminalOrderSlot> accepted = new ArrayList<>();

        for (TerminalOrderSlot slot : new ArrayList<>(draft)) {
            ItemKey key = slot.key;
            int amt = Math.max(1, slot.amount);

            ItemStack keyStack = key.toStack(1);
            if (TerminalOrderApi.isOrderIcon(keyStack)) {
                BlockPos target = TerminalOrderApi.getOrderIconPos(keyStack);
                int iconSlot = TerminalOrderApi.getOrderIconSlot(keyStack);
                ItemStack resultLike = TerminalOrderApi.stripOrderIconData(keyStack);
                if (target != null && iconSlot >= 0) {
                    if (!resultLike.isEmpty()) {
                        directInfusionOrders
                                .computeIfAbsent(target, k -> new ArrayList<>())
                                .add(new AbstractMap.SimpleEntry<>(resultLike, amt));
                    }
                    directTerminalOrders.add(new TerminalOrderRequest(target, iconSlot, amt));
                }
                draft.remove(slot);
                continue;
            }

            int toMove = amt;
            if (!craftTab) {
                int available = getAvailableFromManager(key);
                int havePend  = totalAmountByKey(pendingDelivery, key);
                int room = Math.max(0, available - havePend);
                toMove = Math.min(toMove, room);
            }
            if (toMove <= 0) continue;
            accepted.add(new TerminalOrderSlot(slot.slotId, key, toMove, slot.createdTick));
        }

        if (!directTerminalOrders.isEmpty()) {
            markDirty();
            for (TerminalOrderRequest order : directTerminalOrders) {
                TileEntity te = world.getTileEntity(order.pos);
                if (te instanceof TileInfusionRequester) {
                    List<Map.Entry<ItemStack, Integer>> infusionOrders = directInfusionOrders.remove(order.pos);
                    if (infusionOrders != null) {
                        TileInfusionRequester requester = (TileInfusionRequester) te;
                        for (Map.Entry<ItemStack, Integer> orderEntry : infusionOrders) {
                            int acceptedCount = requester.enqueueCrafterOrder(
                                    managerPos, this.pos, -1, orderEntry.getKey(), orderEntry.getValue());
                            if (acceptedCount > 0) {
                                // direct-infusion path is external and does not create terminal root orders.
                            }
                        }
                        continue;
                    }
                }
                if (te instanceof ITerminalOrderAcceptor) {
                    ((ITerminalOrderAcceptor) te).triggerFromTerminal(order.slot, order.count);
                }
            }
            if (!directInfusionOrders.isEmpty()) {
                for (Map.Entry<BlockPos, List<Map.Entry<ItemStack, Integer>>> entry : directInfusionOrders.entrySet()) {
                    TileEntity te = world.getTileEntity(entry.getKey());
                    if (!(te instanceof TileInfusionRequester)) continue;
                    TileInfusionRequester inf = (TileInfusionRequester) te;
                    for (Map.Entry<ItemStack, Integer> infOrder : entry.getValue()) {
                        int acceptedCount = inf.enqueueCrafterOrder(
                                managerPos, this.pos, -1, infOrder.getKey(), infOrder.getValue());
                        if (acceptedCount > 0) {
                            // direct-infusion path is external and does not create terminal root orders.
                        }
                    }
                }
            }
        }
        if (accepted.isEmpty()) {
            if (directTerminalOrders.isEmpty()) {
                markDirty();
            }
            sendSnapshotToViewers(craftTab);
            return;
        }

        markDirty();

        TileEntity mte = (managerPos == null) ? null : world.getTileEntity(managerPos);
        if (mte instanceof TileMirrorManager) {
            TileMirrorManager mgr = (TileMirrorManager) mte;

            UUID batchId = UUID.randomUUID();
            TerminalOrderBatch batch = new TerminalOrderBatch(batchId, craftTab);
            for (TerminalOrderSlot acceptedSlot : accepted) {
                if (acceptedSlot == null || acceptedSlot.key == null || acceptedSlot.amount <= 0) continue;
                ItemKey key = acceptedSlot.key;
                int amount = acceptedSlot.amount;
                UUID orderId;

                if (craftTab) {
                    orderId = submitCraftRequest(mgr, key, amount);
                } else {
                    orderId = mgr.submitOrder(
                            key,
                            amount,
                            OrderSourceType.TERMINAL,
                            this.pos,
                            this.pos,
                            NetworkOrder.RequestIntent.NORMAL
                    );
                }
                if (orderId == null) continue;
                TerminalOrderSlot pendingSlot = new TerminalOrderSlot(
                        acceptedSlot.slotId, acceptedSlot.key, acceptedSlot.amount, acceptedSlot.createdTick
                );
                pend.add(pendingSlot);
                draft.removeIf(s -> s.slotId.equals(acceptedSlot.slotId));

                TerminalOrderBinding binding = new TerminalOrderBinding(
                        acceptedSlot.slotId, batchId, orderId, acceptedSlot.key, acceptedSlot.amount
                );
                bindingsByRootOrderId.put(orderId, binding);
                bindingsBySlotId.put(acceptedSlot.slotId, binding);
                batch.slotIds.add(acceptedSlot.slotId);
                batch.rootOrderIds.add(orderId);
            }
            if (!batch.rootOrderIds.isEmpty()) batchesById.put(batchId, batch);
        }
        sendSnapshotToViewers(craftTab);
    }

    private static int getSlotLimit(ItemKey key) {
        if (key == null || key == ItemKey.EMPTY) return 1;
        ItemStack probe = key.toStack(1);
        if (probe.isEmpty()) return 1;

        int max = probe.getMaxStackSize();
        if (max <= 0) max = 1;
        return Math.max(1, max);
    }

    public List<ItemStack> getDraftSnapshotStacks(boolean craftTab) {
        return slotsToStacks(craftTab ? draftCraft : draftDelivery);
    }

    public List<Integer> getDraftSnapshotCounts(boolean craftTab) {
        return slotsToCounts(craftTab ? draftCraft : draftDelivery);
    }

    public List<ItemStack> getPendingSnapshot(boolean craftTab) {
        return slotsToStacks(craftTab ? pendingCraft : pendingDelivery);
    }

    public List<Integer> getPendingSnapshotCounts(boolean craftTab) {
        return slotsToCounts(craftTab ? pendingCraft : pendingDelivery);
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
                boolean same = ResourceIdentity.sameResource(out, result);
                if (same) return rp;
            }
        }
        return null;
    }

    @Nullable
    private ItemKey findMatchingKeyRelaxed(List<TerminalOrderSlot> slots, ItemStack like) {
        if (like == null || like.isEmpty() || slots == null || slots.isEmpty()) return null;
        for (TerminalOrderSlot slot : slots) {
            if (slot == null || slot.key == null) continue;
            ItemStack one = slot.key.toStack(1);
            if (ResourceIdentity.sameResource(one, like)) return slot.key;
        }
        return null;
    }

    public void onDelivered(ItemStack like, int count) {
        if (like == null || like.isEmpty() || count <= 0) return;

        refreshPendingOrderStatuses();
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
        refreshPendingOrderStatuses();
    }

    private void refreshPendingOrderStatuses() {
        if (bindingsByRootOrderId.isEmpty() || managerPos == null) return;

        TileEntity te = world.getTileEntity(managerPos);
        if (!(te instanceof TileMirrorManager)) return;
        TileMirrorManager manager = (TileMirrorManager) te;

        boolean draftChangedDelivery = false;
        boolean draftChangedCraft = false;
        for (TerminalOrderBinding binding : new ArrayList<>(bindingsByRootOrderId.values())) {
            if (binding == null || binding.rootOrderId == null) continue;
            if (binding.completed || binding.failed) continue;

            OrderStatus status = manager.getOrderStatus(binding.rootOrderId);
            if (status == null) continue;
            if (status != OrderStatus.DONE && status != OrderStatus.FAILED && status != OrderStatus.CANCELED) continue;

            binding.completed = (status == OrderStatus.DONE);
            binding.failed = !binding.completed;
            boolean craftTab = removePendingSlotById(binding.slotId);
            updateBatchDone(binding.batchId);

            if (craftTab) draftChangedCraft = true;
            else draftChangedDelivery = true;
        }

        if (draftChangedDelivery || draftChangedCraft) {
            markDirty();
            if (draftChangedDelivery) sendSnapshotToViewers(false);
            if (draftChangedCraft) sendSnapshotToViewers(true);
        }
    }

    private boolean removePendingSlotById(UUID slotId) {
        if (slotId == null) return false;
        for (int i = 0; i < pendingDelivery.size(); i++) {
            TerminalOrderSlot slot = pendingDelivery.get(i);
            if (slot != null && slot.slotId.equals(slotId)) {
                pendingDelivery.remove(i);
                return false;
            }
        }
        for (int i = 0; i < pendingCraft.size(); i++) {
            TerminalOrderSlot slot = pendingCraft.get(i);
            if (slot != null && slot.slotId.equals(slotId)) {
                pendingCraft.remove(i);
                return true;
            }
        }
        return false;
    }

    private void updateBatchDone(UUID batchId) {
        if (batchId == null) return;
        TerminalOrderBatch batch = batchesById.get(batchId);
        if (batch == null || batch.done) return;
        for (UUID rootOrderId : batch.rootOrderIds) {
            TerminalOrderBinding binding = bindingsByRootOrderId.get(rootOrderId);
            if (binding == null || (!binding.completed && !binding.failed)) return;
        }
        batch.done = true;
    }

    private boolean isCraftRequestPossible(ItemStack stack, int requestedAmount) {
        if (stack == null || stack.isEmpty() || requestedAmount <= 0) {
            return false;
        }
        if (world == null || managerPos == null) {
            return false;
        }

        TileEntity te = world.getTileEntity(managerPos);
        if (!(te instanceof TileMirrorManager)) {
            return false;
        }

        TileMirrorManager mgr = (TileMirrorManager) te;
        ItemKey key = ItemKey.of(stack);
        if (key == null || key == ItemKey.EMPTY) {
            return false;
        }

        return mgr.canAcceptCraftRequest(key, requestedAmount);
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

        lastEnsureTick = tickCounter;
    }

    private void reconcilePendingByBufferInstant() {
        refreshPendingOrderStatuses();
    }

    private void autoReconcilePendingByBuffer() { reconcilePendingByBufferInstant(); }

    private void sendDraftSnapshotTo(EntityPlayerMP who, boolean craftTab) {
        List<ItemStack> draft9 = getDraftSnapshotStacks(craftTab);
        List<Integer> draftCnt = getDraftSnapshotCounts(craftTab);

        List<ItemStack> pending9 = getPendingSnapshot(craftTab);
        List<Integer> pendingCnt = getPendingSnapshotCounts(craftTab);

        therealpant.thaumicattempts.golemnet.net.msg.S2C_DraftSnapshot pkt =
                new therealpant.thaumicattempts.golemnet.net.msg.S2C_DraftSnapshot(
                        craftTab, draft9, draftCnt, pending9, pendingCnt
                );

        therealpant.thaumicattempts.ThaumicAttempts.NET.sendTo(pkt, who);
    }

    /* ===== NBT ===== */
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);

        nbt.setTag("buffer", buffer.serializeNBT());
        if (managerPos != null) nbt.setLong("manager", managerPos.toLong());

        nbt.setTag("draftD", writeSlots(draftDelivery));
        nbt.setTag("draftC", writeSlots(draftCraft));
        nbt.setTag("pendD",  writeSlots(pendingDelivery));
        nbt.setTag("pendC",  writeSlots(pendingCraft));
        nbt.setTag("terminalBatches", writeBatches());
        nbt.setTag("terminalBindings", writeBindings());

        return nbt;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);

        if (nbt.hasKey("buffer")) buffer.deserializeNBT(nbt.getCompoundTag("buffer"));
        managerPos = nbt.hasKey("manager") ? BlockPos.fromLong(nbt.getLong("manager")) : null;

        readSlots(nbt.getTagList("draftD", 10), draftDelivery);
        readSlots(nbt.getTagList("draftC", 10), draftCraft);
        readSlots(nbt.getTagList("pendD",  10), pendingDelivery);
        readSlots(nbt.getTagList("pendC",  10), pendingCraft);
        readBatches(nbt.getTagList("terminalBatches", 10));
        readBindings(nbt.getTagList("terminalBindings", 10));
    }

    private void triggerInfusionOrder(InfusionOrderTarget target, int count) {
        if (world == null || target == null || count <= 0) return;
        TileEntity te = world.getTileEntity(target.pos);
        if (te instanceof TileInfusionRequester) {
            ((TileInfusionRequester) te).triggerExternalRequest(target.slot, count);
        }
    }

    private static NBTTagList writeSlots(List<TerminalOrderSlot> slots) {
        NBTTagList lst = new NBTTagList();
        for (TerminalOrderSlot slot : slots) {
            if (slot == null || slot.key == null || slot.key == ItemKey.EMPTY) continue;
            lst.appendTag(slot.writeToNbt());
        }
        return lst;
    }

    private static void readSlots(NBTTagList lst, List<TerminalOrderSlot> out) {
        out.clear();
        for (int i = 0; i < lst.tagCount(); i++) {
            TerminalOrderSlot slot = TerminalOrderSlot.readFromNbt(lst.getCompoundTagAt(i));
            if (slot != null) out.add(slot);
            if (out.size() >= 9) break;
        }
    }

    private NBTTagList writeBatches() {
        NBTTagList list = new NBTTagList();
        for (TerminalOrderBatch batch : batchesById.values()) {
            if (batch == null) continue;
            list.appendTag(batch.writeToNbt());
        }
        return list;
    }

    private NBTTagList writeBindings() {
        NBTTagList list = new NBTTagList();
        for (TerminalOrderBinding binding : bindingsByRootOrderId.values()) {
            if (binding == null) continue;
            list.appendTag(binding.writeToNbt());
        }
        return list;
    }

    private void readBatches(NBTTagList list) {
        batchesById.clear();
        for (int i = 0; i < list.tagCount(); i++) {
            TerminalOrderBatch batch = TerminalOrderBatch.readFromNbt(list.getCompoundTagAt(i));
            if (batch != null) batchesById.put(batch.batchId, batch);
        }
    }

    private void readBindings(NBTTagList list) {
        bindingsByRootOrderId.clear();
        bindingsBySlotId.clear();
        for (int i = 0; i < list.tagCount(); i++) {
            TerminalOrderBinding binding = TerminalOrderBinding.readFromNbt(list.getCompoundTagAt(i));
            if (binding == null) continue;
            bindingsByRootOrderId.put(binding.rootOrderId, binding);
            bindingsBySlotId.put(binding.slotId, binding);
        }
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
        final Aspect aspectQuery = parseAspectSearch(q);

        final long sid;
        if (snapshotId < 0) {
            long mix = (world.getTotalWorldTime() << 1)
                    ^ this.pos.toLong()
                    ^ player.getUniqueID().getMostSignificantBits()
                    ^ (craftTab ? 0xCAFEBABEL : 0xDEADBEEFL);
            long positive = mix & Long.MAX_VALUE;
            if (positive == 0L) positive = 1L;
            sid = positive;

            therealpant.thaumicattempts.ThaumicAttempts.NET.sendTo(
                    new therealpant.thaumicattempts.golemnet.net.msg.S2C_SnapshotCreated(craftTab, sid),
                    player
            );
        } else {
            sid = snapshotId;
        }

        final int COLS = 5, ROWS = 7, PER_PAGE = COLS * ROWS;
        TileEntity teMgr = (managerPos == null) ? null : world.getTileEntity(managerPos);
        TileMirrorManager mgr = (teMgr instanceof TileMirrorManager) ? (TileMirrorManager) teMgr : null;

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
            java.util.List<ItemStack> all = mgr.getPlannerCraftablesCatalog();
            if (all == null) all = java.util.Collections.emptyList();

            java.util.List<ItemStack> filtered = new java.util.ArrayList<ItemStack>();
            java.util.List<Integer> perCraft  = new java.util.ArrayList<Integer>();
            for (ItemStack s : all) {
                if (s == null || s.isEmpty()) continue;
                if (!matchesSearch(s, q, aspectQuery)) continue;

                int amount = Math.max(1, s.getCount());
                if (!isCraftRequestPossible(s, amount)) continue;

                filtered.add(s);
                perCraft.add(amount);
            }

            int totalPages = Math.max(1, (filtered.size() + PER_PAGE - 1) / PER_PAGE);
            int page = Math.min(pageIndex0, Math.max(0, totalPages - 1));
            int from = page * PER_PAGE;
            int to   = Math.min(from + PER_PAGE, filtered.size());

            java.util.List<ItemStack> items35 = new java.util.ArrayList<ItemStack>(PER_PAGE);
            java.util.List<Integer> counts35 = new java.util.ArrayList<Integer>(PER_PAGE);
            java.util.List<Integer> make35 = new java.util.ArrayList<Integer>(PER_PAGE);
            java.util.List<Boolean> possible35 = new java.util.ArrayList<Boolean>(PER_PAGE);

            for (int i = from; i < to; i++) {
                ItemStack s = filtered.get(i);
                int perCraftCount = perCraft.get(i);

                ItemStack icon = s.copy();
                icon.setCount(1);

                items35.add(icon);
                counts35.add(perCraftCount);

                /*
                 * Старый PageCraftCalc не знает про planner-first sequential chain,
                 * поэтому terminal не понимал, что предмет достижим через промежуточные крафты.
                 *
                 * Здесь possible считаем planner-aware.
                 */
                boolean possible = isCraftRequestPossible(s, perCraftCount);
                possible35.add(possible);

                /*
                 * Для GUI makeCount оставляем минимально безопасным:
                 * если крафт возможен — показываем хотя бы один крафт-цикл.
                 * Если невозможен — 0.
                 */
                make35.add(possible ? perCraftCount : 0);
            }

            while (items35.size() < PER_PAGE) {
                items35.add(ItemStack.EMPTY);
                counts35.add(0);
                make35.add(0);
                possible35.add(false);
            }

            boolean hasMore = (page + 1) < totalPages;

            therealpant.thaumicattempts.ThaumicAttempts.NET.sendTo(
                    new therealpant.thaumicattempts.golemnet.net.msg.S2C_CatalogPage(
                            true, sid, page + 1, totalPages, hasMore,
                            items35, counts35, make35, possible35
                    ),
                    player
            );

        } else {
            java.util.LinkedHashMap<ItemKey,Integer> cat = mgr.getReachableCatalog();
            java.util.ArrayList<java.util.Map.Entry<ItemKey,Integer>> entries =
                    new java.util.ArrayList<java.util.Map.Entry<ItemKey,Integer>>(cat.entrySet());

            java.util.List<ItemStack> filtered = new java.util.ArrayList<ItemStack>();
            java.util.List<Integer>   counts   = new java.util.ArrayList<Integer>();

            for (java.util.Map.Entry<ItemKey,Integer> e : entries) {
                ItemStack s = e.getKey().toStack(1);
                if (s == null || s.isEmpty()) continue;
                if (!matchesSearch(s, q, aspectQuery)) continue;
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
            while (items35.size() < PER_PAGE) {
                items35.add(ItemStack.EMPTY);
                counts35.add(0);
            }

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

        sendDraftSnapshotTo(player, false);
        sendDraftSnapshotTo(player, true);
    }

    @Nullable
    private UUID submitCraftRequest(TileMirrorManager mgr, ItemKey key, int amount) {
        if (mgr == null || key == null || key == ItemKey.EMPTY || amount <= 0) return null;

        return mgr.submitCraftRequest(
                key,
                amount,
                OrderSourceType.TERMINAL,
                this.pos,
                this.pos,
                NetworkOrder.RequestIntent.CRAFT_ONLY
        );
    }

    @Nullable
    private static Aspect parseAspectSearch(String q) {
        if (q == null) return null;

        String search = q.trim();
        if (!search.startsWith("!")) return null;

        String raw = normalizeAspectSearchToken(search.substring(1));
        if (raw.isEmpty()) return null;

        String normalized = ASPECT_ALIASES.getOrDefault(raw, raw);
        return Aspect.getAspect(normalized);
    }

    private static boolean matchesSearch(ItemStack stack, String q, @Nullable Aspect aspectQuery) {
        if (stack == null || stack.isEmpty()) return false;

        String trimmed = (q == null) ? "" : q.trim();
        if (trimmed.isEmpty()) return true;

        if (trimmed.startsWith("!")) {
            if (aspectQuery == null) return false;
            return hasAspect(stack, aspectQuery);
        }

        String name = stack.getDisplayName();
        return name != null && name.toLowerCase(java.util.Locale.ROOT).contains(trimmed);
    }

    private static boolean hasAspect(ItemStack stack, Aspect wanted) {
        AspectList al = null;
        if (stack.getItem() instanceof ItemTCEssentiaContainer) {
            al = ((ItemTCEssentiaContainer) stack.getItem()).getAspects(stack);
        }
        if (al == null || al.size() == 0) {
            al = reflectObjectAspects(stack);
        }
        return al != null && al.getAmount(wanted) > 0;
    }

    @Nullable
    private static AspectList reflectObjectAspects(ItemStack stack) {
        try {
            Class<?> helper = Class.forName("thaumcraft.api.aspects.AspectHelper");
            java.lang.reflect.Method m = helper.getMethod("getObjectAspects", ItemStack.class);
            Object out = m.invoke(null, stack);
            return (out instanceof AspectList) ? (AspectList) out : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private int countInBufferLike(ItemStack like) {
        if (like == null || like.isEmpty()) return 0;
        int total = 0;
        for (int i = 0; i < buffer.getSlots(); i++) {
            ItemStack s = buffer.getStackInSlot(i);
            if (s.isEmpty()) continue;

            if (ResourceIdentity.sameResource(s, like)) total += s.getCount();
        }
        return total;
    }
}
