package therealpant.thaumicattempts.golemcraft.tile;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.items.ItemStackHandler;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.items.ItemTCEssentiaContainer;
import therealpant.thaumicattempts.golemnet.tile.TilePatternRequester;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Одноцикловый голем-крафтер:
 * - Редстоун используется ТОЛЬКО для инициации одного цикла (по фронту 0→1).
 * - Цикл: пошаговые заявки ресурсов → один выпуск → завершение.
 * - Следующий цикл стартует только по следующему фронту 0→1.
 * - Стоимость эссенции: 2 * число уникальных типов входов. Эссенция: CRAFT, приём снизу.
 */
public class TileEntityGolemCrafter extends TileEntity implements ITickable, IEssentiaTransport {

    // ===== Константы =====
    public static final int PATTERN_SLOTS = 15;
    public static final int INPUT_SLOTS   = 18;
    private static final int OUTPUT_SLOTS = 9;

    private static final String NBT_PATTERNS   = "PatternInv";
    private static final String NBT_INPUT      = "InputInv";
    private static final String NBT_OUTPUT     = "OutputInv";
    private static final String NBT_CUSTOMNAME = "CustomName";
    private static final String TAG_RESULT = "Result";
    private static final String TAG_GRID   = "Grid";

    private static final int  CRAFT_CAP       = 128;
    private static final int  CRAFT_SUCTION   = 128;

    private int lastSignal = 0; // 0..15, чтобы ловить фронт 0->N

    // Очередь запусков от реквестера (индексы паттернов, 0-based)
    private final java.util.Deque<Integer> requesterQueue = new java.util.ArrayDeque<>();
    // Флаг: текущая работа запущена от реквестера (чтобы подавить самопровизию)
    private boolean jobViaRequester = false;

    // как часто перезаказываем тот же шаг, если не принесли
    private static final long REORDER_TICKS   = 10L;
    private static final int  SUPPLY_COOLDOWN = 0;

    // ===== Состояние / параметры =====
    private String customName = "";

    /** Тип эссенции — оставляем CRAFT; наследники могут заменить. */
    protected Aspect requiredAspect = Aspect.CRAFT;
    /** Сколько эссенции тратим за каждый ВИД ресурса. */
    protected int perTypeEssentia = 3;

    /** Буфер эссенции. */
    private int craftAmount = 0;
    private int jobEssentiaCost = 0;
    private int jobEssentiaPaid = 0;

    /** Активен ли единичный цикл. */
    private boolean jobActive = false;

    /** Какой слот-паттерн выбрали при старте цикла. */
    int jobPatternIndex = -1;

    /** Список шагов: ключ (count=1) → сколько штук нужно. */
    protected final List<Req> seq = new ArrayList<>();
    protected int step = 0;
    private long lastOrderWorldTime = 0;
    private int supplyCooldown = 0;
    private int suctionPingCooldown = 0;
    private int drawDelay = 0;

    private boolean suppressSelfProvision = false;
    public void setSuppressSelfProvision(boolean v){ suppressSelfProvision = v; }

    // ===== Инвентари =====
    protected final ItemStackHandler patterns = new ItemStackHandler(PATTERN_SLOTS) {
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return stack != null && !stack.isEmpty()
                    && stack.getItem() instanceof therealpant.thaumicattempts.golemcraft.item.ItemBasePattern;
        }
        @Override protected void onContentsChanged(int slot) { markDirty(); }
    };

    private final ItemStackHandler input   = new ItemStackHandler(INPUT_SLOTS) {
        @Override protected void onContentsChanged(int slot) { markDirty(); }
    };


    public net.minecraftforge.items.IItemHandler getPatternHandler(){ return patterns; }
    public net.minecraftforge.items.IItemHandler getInputHandler() { return input; }
    public net.minecraftforge.items.IItemHandler getOutputHandler(){ return output; }

    protected int getFuel() { return this.craftAmount; }
    protected void consumeFuel(int amt) { this.craftAmount = Math.max(0, this.craftAmount - Math.max(0, amt)); markDirty(); }

    public Aspect getRequiredAspectType() { return requiredAspect; }
    public int getStoredEssentiaUnits() { return craftAmount; }
    public int getActiveEssentiaCost() { return jobEssentiaCost; }
    public int getActiveEssentiaPaid() { return jobEssentiaPaid; }
    public int getEssentiaAvailableUnits() { return craftAmount + jobEssentiaPaid; }

    public int getPerCraftEssentiaCostForPatternIndex(int idx) {
        if (idx < 0 || idx >= PATTERN_SLOTS) return 0;
        ItemStack pat = patterns.getStackInSlot(idx);
        if (pat.isEmpty()) return 0;
        NonNullList<ItemStack> grid = getGrid(pat);
        return Math.max(0, getPerCraftCost(pat, grid));
    }

    public int getPerCraftEssentiaCostForResultLike(ItemStack like1) {
        int idx = findPatternIndexForResultLike(like1);
        return idx < 0 ? 0 : getPerCraftEssentiaCostForPatternIndex(idx);
    }

    private boolean needsEssentiaForActiveJob() {
        return jobActive && jobEssentiaPaid < jobEssentiaCost;
    }

    private void syncActiveJobCost(int cost) {
        cost = Math.max(0, cost);
        if (jobEssentiaCost != cost) {
            jobEssentiaCost = cost;
            if (jobEssentiaPaid > jobEssentiaCost) {
                jobEssentiaPaid = jobEssentiaCost;
            }
            markDirty();
        }
    }

    private boolean pullFuelForActiveJob() {
        if (!needsEssentiaForActiveJob()) return false;
        int missing = jobEssentiaCost - jobEssentiaPaid;
        int take = Math.min(craftAmount, missing);
        if (take <= 0) return false;
        jobEssentiaPaid += take;
        consumeFuel(take);
        return true;
    }

    private void resetJobEssentiaState() {
        if (jobEssentiaCost != 0 || jobEssentiaPaid != 0) {
            jobEssentiaCost = 0;
            jobEssentiaPaid = 0;
            markDirty();
        } else {
            jobEssentiaCost = 0;
            jobEssentiaPaid = 0;
        }
    }

    // ===== Utility: кристаллы / матчинг =====
    private static boolean isTcCrystal(ItemStack s) {
        return s != null && !s.isEmpty() && s.getItem() == ItemsTC.crystalEssence;
    }
    @Nullable
    private static Aspect crystalAspect(ItemStack s) {
        if (!isTcCrystal(s)) return null;
        thaumcraft.api.aspects.AspectList al = ((ItemTCEssentiaContainer) ItemsTC.crystalEssence).getAspects(s);
        if (al != null && al.size() == 1) return al.getAspects()[0];
        return null;
    }

    /** Универсальный матчинг для «входов сетки» и списаний:
     *  - кристаллы — строго по аспекту
     *  - нестакуемые — item + meta
     *  - стакаемые — relaxed
     */
    private static boolean sameForGrid(ItemStack a, ItemStack b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;

        if (isTcCrystal(a) || isTcCrystal(b)) {
            Aspect ax = crystalAspect(a), bx = crystalAspect(b);
            return ax != null && ax == bx;
        }
        if (a.getMaxStackSize() == 1 || b.getMaxStackSize() == 1) {
            if (a.getItem() != b.getItem()) return false;
            if (a.getHasSubtypes() && a.getMetadata() != b.getMetadata()) return false;
            return true;
        }
        return net.minecraftforge.items.ItemHandlerHelper.canItemStacksStackRelaxed(a, b);
    }

    private ItemStackHandler output = makeOutputHandler(OUTPUT_SLOTS);

    private ItemStackHandler makeOutputHandler(int size) {
        return new ItemStackHandler(size) {
            @Override protected void onContentsChanged(int slot) { markDirty(); }
        };
    }

    private static ItemStack key1ForGrid(ItemStack s) {
        if (s == null || s.isEmpty()) return ItemStack.EMPTY;
        if (isTcCrystal(s)) {
            Aspect a = crystalAspect(s);
            return (a == null) ? ItemStack.EMPTY : thaumcraft.api.ThaumcraftApiHelper.makeCrystal(a, 1);
        }
        if (s.getMaxStackSize() == 1) {
            return new ItemStack(s.getItem(), 1, s.getMetadata()); // без NBT
        }
        ItemStack k = s.copy(); k.setCount(1); return k;
    }

    // ===== Привязки =====
    private boolean hasRequesterAbove() {
        if (world == null) return false;
        TileEntity te = world.getTileEntity(pos.up());
        return te instanceof TilePatternRequester;
    }

    public boolean tryStartOneCraftCycleByIndex(int idx) {
        if (world == null || idx < 0 || idx >= PATTERN_SLOTS) return false;
        if (jobActive) return false;
        if (!patternIndexValid(idx)) return false;
        startOneCraftCycle(idx);
        return true;
    }

    public int findPatternIndexForResultLike(ItemStack like1) {
        if (like1 == null || like1.isEmpty()) return -1;
        if (patterns == null) return -1;

        final int slots = patterns.getSlots();
        for (int i = 0; i < slots; i++) {
            ItemStack pat = patterns.getStackInSlot(i);
            if (pat == null || pat.isEmpty()) continue;

            NonNullList<ItemStack> grid = getGrid(pat);
            ItemStack preview = getCraftPreview(pat, grid);
            if (preview.isEmpty()) continue;

            // ВАЖНО: используем ту же семантику, что и для входов/у реквестера
            if (sameForGrid(preview, like1)) {
                return i;
            }
        }
        return -1;
    }

    /** Поставить в очередь N запусков по индексу паттерна (0-based). Возвращает, сколько реально добавили. */
    public int enqueueCraftsByRequesterIndex(int patternIndex, int times) {
        if (patternIndex < 0 || patternIndex >= PATTERN_SLOTS) return 0;
        if (!patternIndexValid(patternIndex)) return 0;
        int n = Math.max(1, times);
        for (int i = 0; i < n; i++) requesterQueue.addLast(patternIndex);
        markDirty();
        return n;
    }

    /** Поставить в очередь N запусков по "результату как этот". Удобно, если реквестер знает только like1. */
    public int enqueueCraftsByRequesterLike(ItemStack like1, int times) {
        if (like1 == null || like1.isEmpty()) return 0;
        int idx = findPatternIndexForResultLike(like1);
        return (idx < 0) ? 0 : enqueueCraftsByRequesterIndex(idx, times);
    }

    /** Текущее количество ожиданий в очереди реквестера. */
    public int getRequesterQueueSize() { return requesterQueue.size(); }

    /** Очистить очередь запусков от реквестера. Текущую работу не прерывает. */
    public void clearRequesterQueue() { requesterQueue.clear(); markDirty(); }

    private void tryStartNextRequesterJob() {
        if (jobActive) return;
        Integer next = requesterQueue.pollFirst();
        if (next == null) return;
        if (!patternIndexValid(next)) return;

        this.suppressSelfProvision = true; // от реквестера — без заявок снабжения
        this.jobViaRequester = true;
        startOneCraftCycle(next);
    }


    // ===== Внутренние типы =====
    static final class Req {
        final ItemStack key1; // ключ (count=1)
        final int count;
        Req(ItemStack key1, int count) { this.key1 = key1; this.count = Math.max(1, count); }
    }

    // ===== Capability =====
    @Override
    public boolean hasCapability(net.minecraftforge.common.capabilities.Capability<?> capability, EnumFacing facing) {
        if (capability == net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return true;
        return super.hasCapability(capability, facing);
    }
    @SuppressWarnings("unchecked")
    @Override
    public <T> T getCapability(net.minecraftforge.common.capabilities.Capability<T> capability, EnumFacing facing) {
        if (capability == net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return (T) (facing == EnumFacing.DOWN ? output : input);
        }
        return super.getCapability(capability, facing);
    }

    // ===== Редстоун ===== //
    private int readSignal() {
        if (world == null) return 0;

        int signal = 0;

        // слабый сигнал со всех сторон (в т.ч. от проводов)
        for (EnumFacing f : EnumFacing.VALUES) {
            signal = Math.max(signal, world.getRedstonePower(pos, f));
        }

        // сильный сигнал блока-питателя (рычаги/факелы/компараторы вплотную)
        signal = Math.max(signal, world.getStrongPower(pos));

        // ВАЖНО: НЕ использовать world.isBlockPowered(pos) -> это только boolean,
        // который нельзя "апгрейдить" до 15, иначе любой сигнал станет 15.
        if (signal < 0) signal = 0;
        if (signal > 15) signal = 15;

        return signal;
    }

    /** Перевод силы сигнала в индекс паттерна: 1→0, 2→1, ... */
    private int patternIndexFromSignal(int signal) {
        if (signal <= 0) return -1;
        return Math.min(signal - 1, PATTERN_SLOTS - 1);
    }

    // ===== Паттерн/сетка =====
    private NonNullList<ItemStack> getGrid(ItemStack pattern) {
        NonNullList<ItemStack> grid = NonNullList.withSize(9, ItemStack.EMPTY);
        if (pattern == null || pattern.isEmpty()) return grid;
        NBTTagCompound tag = pattern.getTagCompound();
        if (tag == null || !tag.hasKey(TAG_GRID, Constants.NBT.TAG_LIST)) return grid;
        NBTTagList list = tag.getTagList(TAG_GRID, Constants.NBT.TAG_COMPOUND);
        int max = Math.min(list.tagCount(), 9);
        for (int i = 0; i < max; i++) {
            ItemStack s = new ItemStack(list.getCompoundTagAt(i));
            grid.set(i, s == null ? ItemStack.EMPTY : s);
        }
        return grid;
    }

    protected void rebuildSequence(ItemStack pattern, NonNullList<ItemStack> grid) {
        seq.clear();

        // посчитать требуемые позиции, объединяя по sameForGrid
        Map<ItemStack, Integer> need = new LinkedHashMap<>();
        for (int i = 0; i < 9; i++) {
            ItemStack s = grid.get(i);
            if (s.isEmpty()) continue;
            ItemStack k1 = key1ForGrid(s);
            if (k1.isEmpty()) continue;

            ItemStack found = null;
            for (ItemStack ex : need.keySet()) { if (sameForGrid(ex, k1)) { found = ex; break; } }
            if (found == null) need.put(k1, 1);
            else need.put(found, need.get(found) + 1);
        }

        for (Map.Entry<ItemStack,Integer> e : need.entrySet()) seq.add(new Req(e.getKey(), e.getValue()));
        step = 0;
    }

    // ===== Подсчёты/списание =====
    protected int countInInput(ItemStack key1) {
        int have = 0;
        for (int i = 0; i < input.getSlots(); i++) {
            ItemStack s = input.getStackInSlot(i);
            if (!s.isEmpty() && sameForGrid(s, key1)) have += s.getCount();
        }
        return have;
    }

    protected boolean hasAllForGrid(NonNullList<ItemStack> grid) {
        Map<ItemStack, Integer> need = new LinkedHashMap<>();
        for (ItemStack s : grid) {
            if (s.isEmpty()) continue;
            ItemStack k1 = key1ForGrid(s);
            if (k1.isEmpty()) continue;
            ItemStack found = null;
            for (ItemStack ex : need.keySet()) { if (sameForGrid(ex, k1)) { found = ex; break; } }
            if (found == null) need.put(k1, 1); else need.put(found, need.get(found) + 1);
        }
        if (need.isEmpty()) return false;
        for (Map.Entry<ItemStack,Integer> e : need.entrySet())
            if (countInInput(e.getKey()) < e.getValue()) return false;
        return true;
    }

    protected ItemStackHandler getInputHandlerWritable() { return this.input; }

    protected void consumeForGrid(NonNullList<ItemStack> grid) {
        Map<ItemStack, Integer> need = new LinkedHashMap<>();
        for (ItemStack s : grid) {
            if (s.isEmpty()) continue;
            ItemStack k1 = key1ForGrid(s);
            if (k1.isEmpty()) continue;
            ItemStack found = null;
            for (ItemStack ex : need.keySet()) { if (sameForGrid(ex, k1)) { found = ex; break; } }
            if (found == null) need.put(k1, 1); else need.put(found, need.get(found) + 1);
        }
        for (Map.Entry<ItemStack,Integer> e : need.entrySet()) {
            int left = e.getValue();
            for (int i = 0; i < input.getSlots() && left > 0; i++) {
                ItemStack have = input.getStackInSlot(i);
                if (have.isEmpty()) continue;
                if (sameForGrid(have, e.getKey())) {
                    int take = Math.min(left, have.getCount());
                    ItemStack ns = have.copy(); ns.shrink(take);
                    input.setStackInSlot(i, ns);
                    left -= take;
                }
            }
        }
    }

    // ===== Превью/стоимость =====
    private ItemStack getStoredPreview(ItemStack pattern) {
        if (pattern == null || pattern.isEmpty()) return ItemStack.EMPTY;
        // Аркан-шаблон — доверяем его превью
        if (pattern.getItem() instanceof therealpant.thaumicattempts.golemcraft.item.ItemArcanePattern) {
            ItemStack arc = therealpant.thaumicattempts.golemcraft.item.ItemArcanePattern
                    .calcArcaneResultPreview(pattern, world);
            if (arc != null && !arc.isEmpty()) return arc.copy();
        }
        // Обычный — только TAG_RESULT
        NBTTagCompound tag = pattern.getTagCompound();
        if (tag != null && tag.hasKey(TAG_RESULT, Constants.NBT.TAG_COMPOUND)) {
            ItemStack res = new ItemStack(tag.getCompoundTag(TAG_RESULT));
            if (!res.isEmpty()) return res;
        }
        return ItemStack.EMPTY;
    }

    /** Единая точка получения превью результата:
     *  1) сначала читаем из NBT (TAG_RESULT),
     *  2) если нет — вычисляем ванильный 3×3 через CraftingManager.
     */
    protected ItemStack getCraftPreview(ItemStack pattern, NonNullList<ItemStack> grid) {
        // 1) из NBT
        ItemStack cached = getStoredPreview(pattern);
        if (!cached.isEmpty()) return cached;

        // 2) расчет ванильного 3×3
        if (grid == null) return ItemStack.EMPTY;
        InventoryCrafting inv = new InventoryCrafting(new Container() {
            @Override public boolean canInteractWith(EntityPlayer p) { return false; }
        }, 3, 3);
        for (int i = 0; i < 9; i++) inv.setInventorySlotContents(i, grid.get(i).copy());
        ItemStack direct = CraftingManager.findMatchingResult(inv, world);
        return (direct == null || direct.isEmpty()) ? ItemStack.EMPTY : direct.copy();
    }

    private int distinctTypes(NonNullList<ItemStack> grid) {
        List<ItemStack> uniq = new ArrayList<>();
        for (ItemStack s : grid) {
            if (s.isEmpty()) continue;
            ItemStack k1 = key1ForGrid(s);
            if (k1.isEmpty()) continue;
            boolean seen = false;
            for (ItemStack u : uniq) { if (sameForGrid(u, k1)) { seen = true; break; } }
            if (!seen) uniq.add(k1);
        }
        return uniq.size();
    }

    protected int getPerCraftCost(ItemStack pattern, NonNullList<ItemStack> grid) {
        return Math.max(1, perTypeEssentia) * Math.max(0, distinctTypes(grid));
    }

    // ===== Эссенция =====
    private boolean convertCrystalsToEssentia() {
        if (world == null || world.isRemote) return false;
        if (requiredAspect == null) return false;
        int room = CRAFT_CAP - craftAmount;
        if (room <= 0) return false;
        ItemStack key = thaumcraft.api.ThaumcraftApiHelper.makeCrystal(requiredAspect, 1);
        if (key == null || key.isEmpty()) return false;

        boolean changed = false;
        for (int i = 0; i < input.getSlots() && room > 0; i++) {
            ItemStack s = input.getStackInSlot(i);
            if (s.isEmpty()) continue;
            if (!sameForGrid(s, key)) continue;

            int take = Math.min(room, s.getCount());
            if (take <= 0) continue;

            craftAmount += take;
            room -= take;
            changed = true;

            ItemStack ns = s.copy();
            ns.shrink(take);
            input.setStackInSlot(i, ns);
        }

        if (changed) {
            markDirty();
            pingNeighbors();
        }
        return changed;
    }

    private boolean drawEssentiaTicked() {
        if (++drawDelay % 5 != 0 || world == null || world.isRemote) return false;
        if (craftAmount >= CRAFT_CAP && !needsEssentiaForActiveJob()) return false;
        EnumFacing inFace = EnumFacing.DOWN;
        TileEntity te = ThaumcraftApiHelper.getConnectableTile(world, pos, inFace);
        if (te instanceof IEssentiaTransport) {
            IEssentiaTransport ic = (IEssentiaTransport) te;
            EnumFacing opp = inFace.getOpposite();
            if (!ic.canOutputTo(opp)) return false;
            if (ic.getSuctionAmount(opp) < this.getSuctionAmount(inFace)) {
                int taken = ic.takeEssentia(requiredAspect, 1, opp);
                if (taken > 0) {
                    craftAmount = Math.min(CRAFT_CAP, craftAmount + taken);
                    markDirty();
                    pullFuelForActiveJob();
                    pingNeighbors();
                    return true;
                }
            }
        }
        return false;
    }

    // ===== Выход =====
    private boolean canAcceptResult(ItemStack result) {
        if (result == null || result.isEmpty()) return false;
        ItemStack left = result.copy();
        // симуляцией проверяем, что ВСЯ стопка влезет в output (в любую комбинацию слотов)
        for (int i = 0; i < output.getSlots(); i++) {
            left = output.insertItem(i, left, true); // simulate = true
            if (left.isEmpty()) return true;
        }
        return false;
    }
    private void pushResult(ItemStack result) {
        if (result == null || result.isEmpty()) return;
        ItemStack left = result.copy();
        for (int i = 0; i < output.getSlots(); i++) {
            left = output.insertItem(i, left, false); // реальная вставка
            if (left.isEmpty()) return;
        }
    }

    /** Выбор индекса паттерна по сигналу, с фолбэком на первый валидный. */
    private int choosePatternIndexForSignal(int signal) {
        int idx = patternIndexFromSignal(signal); // 1->0, 2->1, ...
        if (patternIndexValid(idx)) return idx;
        for (int i = 0; i < PATTERN_SLOTS; i++) if (patternIndexValid(i)) return i;
        return -1;
    }

    // ===== Tick =====
    @Override
    public void onLoad() {
        super.onLoad();
        if (world != null && !world.isRemote) {
            this.lastSignal = readSignal(); // синхронизация, чтобы не стартовать случайно
        }
    }


    @Override
    public void update() {
        if (world == null || world.isRemote) return;

        if (supplyCooldown > 0) supplyCooldown--;
        boolean drew = drawEssentiaTicked();
        boolean crystals = convertCrystalsToEssentia();
        if (drew || crystals) {
            if (pullFuelForActiveJob()) {
                pingNeighbors();
            }
        }
        if (suctionPingCooldown-- <= 0) {
            if (craftAmount < CRAFT_CAP) pingNeighbors();
            suctionPingCooldown = 20;
        }

        // СНАЧАЛА пробуем запустить из очереди реквестера
        int signal = readSignal();

        // 1) если есть очередь от реквестера — запускаем её первой
        if (!jobActive && !requesterQueue.isEmpty()) {
            tryStartNextRequesterJob();
        }

        // 2) редстоун-триггер по фронту — как было
        if (!jobActive && requesterQueue.isEmpty() && lastSignal == 0 && signal > 0) {
            int idx = choosePatternIndexForSignal(signal);
            if (idx >= 0) {
                this.suppressSelfProvision = false; // от редстоуна — разрешаем автопровизию
                this.jobViaRequester = false;
                startOneCraftCycle(idx);
            }
        }

        if (jobActive) runActiveJob();

        lastSignal = signal;
    }


    protected void startOneCraftCycle(int idx) {
        jobActive = true;
        jobPatternIndex = idx;

        seq.clear();
        step = 0;

        resetJobEssentiaState();

        long now = (world != null) ? world.getTotalWorldTime() : 0L;
        lastOrderWorldTime = now - REORDER_TICKS;
        supplyCooldown = 0;

        markDirty();
    }

    public boolean isIdle() { return !jobActive; }

    private boolean patternIndexValid(int idx) {
        if (idx < 0 || idx >= PATTERN_SLOTS) return false;
        ItemStack pat = patterns.getStackInSlot(idx);
        if (pat.isEmpty()) return false;
        NonNullList<ItemStack> grid = getGrid(pat);
        ItemStack preview = getCraftPreview(pat, grid); // ← обязательно так
        return !preview.isEmpty();
    }

    private void runActiveJob() {
        if (!jobActive) return;

        if (jobPatternIndex < 0 || jobPatternIndex >= PATTERN_SLOTS) { finishJob(); return; }
        ItemStack pat = patterns.getStackInSlot(jobPatternIndex);
        if (pat.isEmpty()) { finishJob(); return; }

        NonNullList<ItemStack> grid = getGrid(pat);
        convertCrystalsToEssentia();
        if (seq.isEmpty()) rebuildSequence(pat, grid);
        if (hasAllForGrid(grid)) {
            step = seq.size(); // всё готово — сразу к выпуску
        }
        ItemStack preview = getCraftPreview(pat, grid); // ← обязательно так
        if (preview.isEmpty()) { finishJob(); return; }
        int cost = getPerCraftCost(pat, grid);
        syncActiveJobCost(cost);
        if (pullFuelForActiveJob()) {
            pingNeighbors();
        }
        // ---- снабжение текущего шага как раньше ----
        if (step < seq.size()) {
            Req r = seq.get(step);
            int have = countInInput(r.key1);
            if (have >= r.count) {
                step++;
            } else {
                int need = r.count - have;
                long now = world.getTotalWorldTime();
                if (lastOrderWorldTime == 0L || now - lastOrderWorldTime >= REORDER_TICKS) {
                    if (!suppressSelfProvision) {
                        ItemStack req = normalizeForProvision(r.key1, Math.min(need, r.key1.getMaxStackSize()));
                        thaumcraft.api.golems.GolemHelper.requestProvisioning(world, pos, EnumFacing.UP, req, 0);
                    }
                    lastOrderWorldTime = now;
                    supplyCooldown = SUPPLY_COOLDOWN;
                }
                if (pullFuelForActiveJob()) {
                    pingNeighbors();
                }
                return;
            }
        }

        // ---- списание и выпуск без CraftingManager ----
        if (jobEssentiaPaid >= jobEssentiaCost && hasAllForGrid(grid) && canAcceptResult(preview)) {
            consumeForGrid(grid);   // снять входы как в сетке
            pushResult(preview);    // выдать именно превью из шаблона/расчёта
            markDirty();
            pingNeighbors();
            finishJob(); // единичный цикл
        }
    }

    private void finishJob() {
        this.jobActive = false;
        this.jobPatternIndex = -1;
        this.seq.clear();
        this.step = 0;
        this.lastOrderWorldTime = 0;

        // сбрасываем режим «от реквестера» и подавление провизии
        this.jobViaRequester = false;
        this.suppressSelfProvision = false;

        resetJobEssentiaState();

        if (world == null) return;

        // сначала — очередь от реквестера
        if (!requesterQueue.isEmpty()) {
            tryStartNextRequesterJob();
            return;
        }

    }

    private void pingNeighbors() {
        if (world == null || world.isRemote) return;
        try {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
            world.notifyNeighborsOfStateChange(pos, state.getBlock(), true);
            for (EnumFacing f : EnumFacing.VALUES) {
                BlockPos np = pos.offset(f);
                world.notifyNeighborsOfStateChange(np, world.getBlockState(np).getBlock(), true);
            }
        } catch (Throwable ignored) {}
    }

    // ===== Self-provision normalize =====
    private static ItemStack normalizeForProvision(ItemStack like, int amount) {
        if (isTcCrystal(like)) {
            Aspect a = crystalAspect(like);
            if (a != null) return ThaumcraftApiHelper.makeCrystal(a, Math.max(1, amount));
        }
        ItemStack r = like.copy(); r.setCount(Math.max(1, amount)); return r;
    }



    // ===== IEssentiaTransport =====
    @Override public boolean isConnectable(EnumFacing face) { return face == EnumFacing.DOWN; }
    @Override public boolean canInputFrom(EnumFacing face)  { return face == EnumFacing.DOWN; }
    @Override public boolean canOutputTo(EnumFacing face)   { return false; }
    @Override public void setSuction(Aspect aspect, int amount) {}
    @Override public Aspect getSuctionType(EnumFacing face) { return (face == EnumFacing.DOWN && craftAmount < CRAFT_CAP) ? requiredAspect : null; }
    @Override public int getSuctionAmount(EnumFacing face)  { return (face == EnumFacing.DOWN && craftAmount < CRAFT_CAP) ? CRAFT_SUCTION : 0; }
    @Override public int addEssentia(Aspect aspect, int amount, EnumFacing face) {
        if (face != EnumFacing.DOWN) return 0;
        if (aspect != requiredAspect || amount <= 0) return 0;
        int accepted = 0;

        if (jobActive && jobEssentiaPaid < jobEssentiaCost) {
            int toPay = Math.min(amount, jobEssentiaCost - jobEssentiaPaid);
            if (toPay > 0) {
                jobEssentiaPaid += toPay;
                accepted += toPay;
                amount -= toPay;
                markDirty();
            }
        }

        int canStore = Math.min(amount, CRAFT_CAP - craftAmount);
        if (canStore > 0) {
            craftAmount += canStore;
            accepted += canStore;
            markDirty();
        }

        boolean consumed = pullFuelForActiveJob();
        boolean converted = convertCrystalsToEssentia();
        if (converted && pullFuelForActiveJob()) {
            consumed = true;
        }
        if (accepted > 0 || consumed || converted) {
            pingNeighbors();
        }

        return accepted;
    }
    @Override public int takeEssentia(Aspect aspect, int amount, EnumFacing face) { return 0; }
    @Override public Aspect getEssentiaType(EnumFacing face) { return craftAmount > 0 ? requiredAspect : null; }
    @Override public int getEssentiaAmount(EnumFacing face)  { return craftAmount; }
    @Override public int getMinimumSuction()                 { return 8; }

    // ===== NBT / сеть =====
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setTag(NBT_PATTERNS, patterns.serializeNBT());
        nbt.setTag(NBT_INPUT,    input.serializeNBT());
        nbt.setTag(NBT_OUTPUT,   output.serializeNBT());
        if (hasCustomName()) nbt.setString(NBT_CUSTOMNAME, customName);
        nbt.setInteger("CraftAmount", craftAmount);
        nbt.setString("ReqAspect", requiredAspect == null ? "" : requiredAspect.getTag());
        nbt.setInteger("PerTypeEssentia", perTypeEssentia);

        nbt.setBoolean("JobActive", jobActive);
        nbt.setInteger("JobPatternIndex", jobPatternIndex);
        nbt.setInteger("Step", step);
        nbt.setLong("LastOrderWT", lastOrderWorldTime);
        nbt.setInteger("JobEssentiaCost", jobEssentiaCost);
        nbt.setInteger("JobEssentiaPaid", jobEssentiaPaid);

        nbt.setInteger("LastSignal", lastSignal);

        net.minecraft.nbt.NBTTagList rq = new net.minecraft.nbt.NBTTagList();
        for (Integer idx : requesterQueue) {
            net.minecraft.nbt.NBTTagCompound c = new net.minecraft.nbt.NBTTagCompound();
            c.setInteger("I", idx == null ? -1 : idx);
            rq.appendTag(c);
        }
        nbt.setTag("RequesterQ", rq);

        return nbt;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        if (nbt.hasKey(NBT_PATTERNS, Constants.NBT.TAG_COMPOUND)) patterns.deserializeNBT(nbt.getCompoundTag(NBT_PATTERNS));
        if (nbt.hasKey(NBT_INPUT,    Constants.NBT.TAG_COMPOUND)) input.deserializeNBT(nbt.getCompoundTag(NBT_INPUT));
        if (nbt.hasKey(NBT_OUTPUT,   Constants.NBT.TAG_COMPOUND)) output.deserializeNBT(nbt.getCompoundTag(NBT_OUTPUT));
        if (output.getSlots() < OUTPUT_SLOTS) {
            ItemStackHandler neo = makeOutputHandler(OUTPUT_SLOTS);
            for (int i = 0; i < output.getSlots(); i++) {
                neo.setStackInSlot(i, output.getStackInSlot(i));
            }
            output = neo;
        }
        customName = nbt.hasKey(NBT_CUSTOMNAME, Constants.NBT.TAG_STRING) ? nbt.getString(NBT_CUSTOMNAME) : "";
        craftAmount = nbt.getInteger("CraftAmount");
        Aspect a = Aspect.getAspect(nbt.getString("ReqAspect"));
        if (a != null) requiredAspect = a;
        perTypeEssentia = Math.max(1, nbt.getInteger("PerTypeEssentia"));

        jobActive = nbt.getBoolean("JobActive");
        jobPatternIndex = nbt.getInteger("JobPatternIndex");
        step = Math.max(0, nbt.getInteger("Step"));
        lastOrderWorldTime = nbt.getLong("LastOrderWT");
        jobEssentiaCost = Math.max(0, nbt.getInteger("JobEssentiaCost"));
        jobEssentiaPaid = Math.max(0, Math.min(jobEssentiaCost, nbt.getInteger("JobEssentiaPaid")));

        lastSignal = nbt.getInteger("LastSignal");

        requesterQueue.clear();
        if (nbt.hasKey("RequesterQ", Constants.NBT.TAG_LIST)) {
            net.minecraft.nbt.NBTTagList rq = nbt.getTagList("RequesterQ", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < rq.tagCount(); i++) {
                net.minecraft.nbt.NBTTagCompound c = rq.getCompoundTagAt(i);
                int idx = c.getInteger("I");
                if (idx >= 0 && idx < PATTERN_SLOTS) requesterQueue.addLast(idx);
            }
        }

        // последовательность пересоберём при первом тике работы
        seq.clear();
    }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(getPos(), 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) { readFromNBT(pkt.getNbtCompound()); }

    // ===== UI =====
    public boolean hasCustomName() { return customName != null && !customName.isEmpty(); }
    public String getName() { return hasCustomName() ? customName : "container.thaumicattempts.golem_crafter"; }
    public void setCustomName(String name) { this.customName = name == null ? "" : name; markDirty(); }
    public ITextComponent getDisplayName() { return new TextComponentTranslation(getName()); }
}
