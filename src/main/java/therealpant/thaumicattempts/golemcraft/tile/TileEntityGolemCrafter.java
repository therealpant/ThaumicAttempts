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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.api.golems.GolemHelper;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.items.ItemTCEssentiaContainer;
import therealpant.thaumicattempts.api.IPatternedWorksite;
import therealpant.thaumicattempts.api.PatternProvisioningSpec;
import therealpant.thaumicattempts.api.PatternRedstoneMode;
import therealpant.thaumicattempts.api.PatternResourceList;
import therealpant.thaumicattempts.golemnet.tile.TileMirrorManager;
import therealpant.thaumicattempts.golemnet.tile.TilePatternRequester;
import therealpant.thaumicattempts.golemnet.logistics.OrderSourceType;
import therealpant.thaumicattempts.util.ItemKey;
import therealpant.thaumicattempts.util.ResourceIdentity;
import therealpant.thaumicattempts.golemcraft.item.ItemBasePattern;


import javax.annotation.Nullable;
import java.util.*;

/**
 * Одноцикловый голем-крафтер:
 * - Редстоун используется ТОЛЬКО для инициации одного цикла (по фронту 0→1).
 * - Цикл: пошаговые заявки ресурсов → один выпуск → завершение.
 * - Следующий цикл стартует только по следующему фронту 0→1.
 - Стоимость эссенции: 2 * число уникальных типов входов. Эссенция: CRAFT, приём снизу/с боков.
 */
public class TileEntityGolemCrafter extends TileEntity implements ITickable, IEssentiaTransport, IPatternedWorksite {
    private static final Logger LOG = LogManager.getLogger("ThaumicAttempts/GolemCrafter");

    // ===== Константы =====
    public static final int PATTERN_SLOTS = 15;
    public static final int INPUT_SLOTS   = 18;
    private static final int OUTPUT_SLOTS = 9;
    // как часто перезаказываем тот же шаг, если не принесли
    private static final long REORDER_TICKS   = 2L;
    private static final int  SUPPLY_COOLDOWN = 0;
    private static final int  ESSENTIA_PULL_INTERVAL = 2;

    private static final PatternProvisioningSpec PROVISION_SPEC = PatternProvisioningSpec.crafterSpec(SUPPLY_COOLDOWN);

    private static final String NBT_PATTERNS   = "PatternInv";
    private static final String NBT_INPUT      = "InputInv";
    private static final String NBT_OUTPUT     = "OutputInv";
    private static final String NBT_CUSTOMNAME = "CustomName";
    private static final String TAG_RESULT = "Result";
    private static final String TAG_GRID   = "Grid";
    private static final EnumFacing[] ESSENTIA_INPUT_FACES = {
            EnumFacing.DOWN, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST
    };

    private boolean waitingForEssentia = false;
    private long lastEssentiaWaitLogTime = 0L;
    private boolean waitingForOutputSpace = false;
    private long lastOutputWaitLogTime = 0L;
    private int stalledJobTicks = 0;

    private static final int  CRAFT_CAP       = 128;
    private static final int  CRAFT_SUCTION   = 128;
    private static final int  BATCH_OUTPUT_LIMIT = 32;

    private int lastSignal = 0; // 0..15, чтобы ловить фронт 0->N

    // Привязка к менеджеру (через PatternRequester сверху)
    private @Nullable BlockPos managerPos = null;
    private boolean managerFromPattern = false;

    @Nullable
    private UUID managerRedstoneOrderId = null;


    private static final class JobRequest {
        final int patternIndex;
        final int outputItems;

        JobRequest(int patternIndex, int outputItems) {
            this.patternIndex = patternIndex;
            this.outputItems = Math.max(1, outputItems);
        }
    }

    // Очередь запусков от реквестера/менеджера (индекс паттерна + итоговые предметы)
    private final java.util.Deque<JobRequest> requesterQueue = new java.util.ArrayDeque<>();
    private final java.util.Deque<JobRequest> managedExecutionQueue = new java.util.ArrayDeque<>();
    // Флаг: текущая работа запущена от реквестера (чтобы подавить самопровизию)
    private boolean jobViaRequester = false;
    private boolean jobViaManagerAssignment = false;

    // ===== Состояние / параметры =====
    private String customName = "";

    /** Тип эссенции — оставляем CRAFT; наследники могут заменить. */
    protected Aspect requiredAspect = Aspect.CRAFT;
    /** Сколько эссенции тратим за каждый ВИД ресурса. */
    protected int perTypeEssentia = 1;

    /** Буфер эссенции. */
    private int craftAmount = 0;
    private int essentiaDebt = 0;
    private int costRemaining = 0;

    /** Активен ли единичный заказ.. */
    private boolean jobActive = false;
    private int totalCraftsRemainingForOrder = 0;
    private int totalCraftsInitialForOrder = 0;
    private int totalOrderOutputRemainingItems = 0;
    private int totalOrderOutputInitialItems = 0;

    private int activeBatchCraftsRemaining = 0;
    private int activeBatchCraftsTotal = 0;
    private int activeBatchOutputItems = 0;
    private int activeBatchOutputPerCraft = 1;
    private int activeBatchEssentiaDebt = 0;

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

    @Override
    public net.minecraftforge.items.IItemHandler getPatternHandler(){ return patterns; }
    public net.minecraftforge.items.IItemHandler getInputHandler() { return input; }
    public net.minecraftforge.items.IItemHandler getOutputHandler(){ return output; }

    protected int getFuel() { return this.craftAmount; }
    protected void consumeFuel(int amt) { this.craftAmount = Math.max(0, this.craftAmount - Math.max(0, amt)); markDirty(); }

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
        return ResourceIdentity.sameResource(a, b);
    }

    private ItemStackHandler output = makeOutputHandler(OUTPUT_SLOTS);

    private ItemStackHandler makeOutputHandler(int size) {
        return new ItemStackHandler(size) {
            @Override protected void onContentsChanged(int slot) { markDirty(); }
        };
    }

    private static ItemStack key1ForGrid(ItemStack s) {
        return PatternResourceList.normalizeForKey(s);
    }

    // ===== Привязки =====
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
        ItemStack pattern = patterns.getStackInSlot(patternIndex);
        NonNullList<ItemStack> grid = getGrid(pattern);
        ItemStack preview = getCraftPreview(pattern, grid);
        int perCraft = Math.max(1, preview.getCount());
        requesterQueue.addLast(new JobRequest(patternIndex, n * perCraft));
        markDirty();
        return n * perCraft;
    }

    public void dropContents() {
        if (world == null || world.isRemote) return;

        dropHandler(patterns);
        dropHandler(input);
        dropHandler(output);
    }

    private void dropHandler(ItemStackHandler handler) {
        if (handler == null) return;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                net.minecraft.inventory.InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), stack.copy());
                handler.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }

    /** Поставить в очередь N запусков по "результату как этот". Удобно, если реквестер знает только like1. */
    public int enqueueCraftsByRequesterLike(ItemStack like1, int times) {
        if (like1 == null || like1.isEmpty()) return 0;
        int idx = findPatternIndexForResultLike(like1);
        return (idx < 0) ? 0 : enqueueCraftsByRequesterIndex(idx, times);
    }

    @Override
    public int enqueueFromPatternRequester(int patternSlot, int times) {
        return enqueueCraftsByRequesterIndex(patternSlot, times);
    }

    @Override
    public int enqueueFromPatternRequester(ItemStack resultLike, int times) {
        return enqueueCraftsByRequesterLike(resultLike, times);
    }

    public int startManagedExecution(ItemStack resultLike, int amount) {
        if (resultLike == null || resultLike.isEmpty() || amount <= 0) return 0;
        int patternIndex = findPatternIndexForResultLike(resultLike);
        if (patternIndex < 0 || !patternIndexValid(patternIndex)) return 0;

        ItemStack pattern = patterns.getStackInSlot(patternIndex);
        if (pattern.isEmpty()) return 0;
        NonNullList<ItemStack> grid = getGrid(pattern);
        ItemStack preview = getCraftPreview(pattern, grid);
        if (preview.isEmpty()) return 0;

        int perCraft = Math.max(1, preview.getCount());
        managedExecutionQueue.addLast(new JobRequest(patternIndex, amount));
        markDirty();
        return amount;
    }

    private void tryStartNextManagedJob() {
        if (jobActive) return;
        JobRequest next = managedExecutionQueue.pollFirst();
        if (next == null) return;
        if (!patternIndexValid(next.patternIndex)) return;

        this.suppressSelfProvision = true;
        this.jobViaRequester = false;
        this.jobViaManagerAssignment = true;
        startOneCraftCycle(next.patternIndex, next.outputItems);
    }

    private void tryStartNextRequesterJob() {
        if (jobActive) return;
        JobRequest next = managedExecutionQueue.pollFirst();
        if (next == null) return;
        if (!patternIndexValid(next.patternIndex)) return;

        this.suppressSelfProvision = true; // от реквестера — без заявок снабжения
        this.jobViaRequester = true;
        startOneCraftCycle(next.patternIndex, next.outputItems);
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

        List<PatternResourceList.Entry> resources = PatternResourceList.build(pattern);
        if ((resources == null || resources.isEmpty()) && grid != null) {
            resources = PatternResourceList.aggregate(grid, false);
        }

        if (resources != null) {
            for (PatternResourceList.Entry e : resources) {
                if (e == null) continue;
                ItemStack key = e.getKeyStack();
                if (key == null || key.isEmpty()) continue;
                seq.add(new Req(key, e.getCount()));
            }
        }
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

    protected boolean hasAllForGrid(NonNullList<ItemStack> grid, int crafts) {
        ItemStack pat = (this.jobPatternIndex >= 0 && this.jobPatternIndex < PATTERN_SLOTS)
                ? this.patterns.getStackInSlot(this.jobPatternIndex) : ItemStack.EMPTY;

        List<PatternResourceList.Entry> need = PatternResourceList.build(pat);
        if ((need == null || need.isEmpty()) && grid != null) {
            need = PatternResourceList.aggregate(grid, false);
        }
        if (need == null || need.isEmpty()) return false;

        for (PatternResourceList.Entry entry : need) {
            if (entry == null) continue;

            ItemStack key = entry.getKeyStack();
            if (key == null || key.isEmpty()) continue;

            int required = Math.max(0, entry.getCount() * Math.max(1, crafts));

            if (countInInput(key) < required) return false;
        }
        return true;
    }

    protected void consumeForGrid(NonNullList<ItemStack> grid, int crafts) {
        ItemStack pat = (this.jobPatternIndex >= 0 && this.jobPatternIndex < PATTERN_SLOTS)
                ? this.patterns.getStackInSlot(this.jobPatternIndex) : ItemStack.EMPTY;
        List<PatternResourceList.Entry> need = PatternResourceList.build(pat);
        if ((need == null || need.isEmpty()) && grid != null) {
            need = PatternResourceList.aggregate(grid, false);
        }
        if (need == null || need.isEmpty()) return;

        for (PatternResourceList.Entry entry : need) {
            if (entry == null) continue;
            int left = Math.max(0, entry.getCount() * Math.max(1, crafts));
            ItemStack key = entry.getKeyStack();
            if (key == null || key.isEmpty()) continue;
            for (int i = 0; i < input.getSlots() && left > 0; i++) {
                ItemStack have = input.getStackInSlot(i);
                if (have.isEmpty()) continue;
                if (sameForGrid(have, key)) {
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

    private int getPatternRepeatCount(ItemStack pattern) {
        if (pattern == null || pattern.isEmpty()) return 1;
        if (pattern.getItem() instanceof ItemBasePattern) {
            return ItemBasePattern.getRepeatCount(pattern);
        }
        return 1;
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
    private boolean drawEssentiaTicked() {
        if (world == null || world.isRemote) return false;

        drawDelay++;
        if (drawDelay < ESSENTIA_PULL_INTERVAL) return false;
        drawDelay = 0;

        if (craftAmount >= CRAFT_CAP && essentiaDebt <= 0) return false;

        boolean pulled = false;
        for (EnumFacing inFace : ESSENTIA_INPUT_FACES) {
            TileEntity te = ThaumcraftApiHelper.getConnectableTile(world, pos, inFace);
            if (!(te instanceof IEssentiaTransport)) continue;

            IEssentiaTransport ic = (IEssentiaTransport) te;
            EnumFacing opp = inFace.getOpposite();

            if (!ic.canOutputTo(opp)) continue;

            if (ic.getSuctionAmount(opp) < this.getSuctionAmount(inFace)) {
                int taken = ic.takeEssentia(requiredAspect, 1, opp);
                if (taken > 0) {
                    acceptIncomingEssentia(taken);
                    pulled = true;
                    break;
                }
            }
        }

        if (pulled) {
            markDirty();
            pingNeighbors();
        }
        return pulled;
    }

    private int acceptIncomingEssentia(int incoming) {
        int toApply = Math.max(0, incoming);
        int debtPaid = 0;
        if (toApply > 0 && essentiaDebt > 0) {
            debtPaid = Math.min(toApply, essentiaDebt);
            essentiaDebt -= debtPaid;
            toApply -= debtPaid;
            if (activeBatchCraftsRemaining <= 0) {
                activeBatchEssentiaDebt = essentiaDebt;
            }
        }
        if (toApply > 0) {
            craftAmount = Math.min(CRAFT_CAP, craftAmount + toApply);
        }
        waitingForEssentia = false;
        LOG.info("[Crafter {}] essentia incoming incoming={} debtPaid={} newDebt={} newCraftAmount={}",
                pos, incoming, debtPaid, essentiaDebt, craftAmount);
        return incoming - toApply;
    }

    /** Пытается списать до amt единиц эссенции. Возвращает true, если чанк оплачен полностью. */
    private boolean tryPayEssentiaChunk(int amt) {
        if (amt <= 0) return true;

        int take = Math.min(amt, Math.max(0, craftAmount));
        craftAmount -= take;
        int missing = Math.max(0, amt - take);
        if (missing > 0 && activeBatchCraftsRemaining > 0) {
            essentiaDebt += missing;
            activeBatchEssentiaDebt = essentiaDebt;
        }
        costRemaining = Math.max(0, costRemaining - amt);
        waitingForEssentia = (activeBatchCraftsRemaining <= 0) && (craftAmount <= 0 || essentiaDebt > 0);
        markDirty();
        pingNeighbors();
        return true;
    }


    // ===== Выход =====
    private List<ItemStack> buildBatchResultStackList(ItemStack preview, int totalOutputItems) {
        List<ItemStack> stacks = new ArrayList<>();
        if (preview == null || preview.isEmpty() || totalOutputItems <= 0) return stacks;
        int maxStack = Math.max(1, preview.getMaxStackSize());
        int left = totalOutputItems;
        while (left > 0) {
            int take = Math.min(left, maxStack);
            ItemStack part = preview.copy();
            part.setCount(take);
            stacks.add(part);
            left -= take;
        }
        return stacks;
    }

    private boolean canAcceptBatchResult(List<ItemStack> stacks) {
        if (stacks == null || stacks.isEmpty()) return false;
        for (ItemStack s : stacks) {
            if (s == null || s.isEmpty()) continue;
            ItemStack left = s.copy();
            for (int i = 0; i < output.getSlots(); i++) {
                left = output.insertItem(i, left, true);
                if (left.isEmpty()) break;
            }
            if (!left.isEmpty()) return false;
        }
        return true;
    }
    private void pushBatchResult(List<ItemStack> stacks) {
        if (stacks == null || stacks.isEmpty()) return;
        for (ItemStack s : stacks) {
            ItemStack left = s.copy();
            for (int i = 0; i < output.getSlots(); i++) {
                left = output.insertItem(i, left, false);
                if (left.isEmpty()) break;
            }
        }
    }

    /** Выбор индекса паттерна по сигналу, с фолбэком на первый валидный. */
    private int choosePatternIndexForSignal(int signal) {
        int idx = patternIndexFromSignal(signal); // 1->0, 2->1, ...
        if (patternIndexValid(idx)) return idx;
        for (int i = 0; i < PATTERN_SLOTS; i++) if (patternIndexValid(i)) return i;
        return -1;
    }

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
            if (patternManager != null) {
                setManagerPos(patternManager, true);
            } else if (managerFromPattern) {
                setManagerPos(null, false);
            }
        } else if (managerFromPattern) {
            setManagerPos(null, false);
        }
    }

    private void setManagerPos(@Nullable BlockPos pos, boolean fromPattern) {
        BlockPos newPos = pos == null ? null : pos.toImmutable();
        boolean newFlag = fromPattern && newPos != null;
        if (!Objects.equals(this.managerPos, newPos) || this.managerFromPattern != newFlag) {
            this.managerPos = newPos;
            this.managerFromPattern = newFlag;
            this.managerRedstoneOrderId = null;
            markDirty();
        }
    }

    public void setManagerPos(@Nullable BlockPos pos) {
        setManagerPos(pos, false);
    }

    public @Nullable BlockPos getManagerPos() { return managerPos; }

    @Override
    public void setManagerPosFromPattern(@Nullable BlockPos managerPos) {
        setManagerPos(managerPos, true);
    }

    @Override
    public @Nullable BlockPos getManagerPosForPattern() {
        return getManagerPos();
    }

    @Override
    public PatternRedstoneMode getRedstoneMode() {
        return PatternRedstoneMode.RISING_EDGE_SELECTS_SLOT;
    }

    @Override
    public PatternProvisioningSpec getProvisioningSpec() {
        return PROVISION_SPEC;
    }

    public void clearManagerPosFromManager(BlockPos pos) {
        if (pos != null && pos.equals(this.managerPos)) {
            setManagerPos(null);
        }
    }

    private boolean submitManagerCraftOrderForSignal(int idx, int repeats) {
        if (world == null || world.isRemote || managerPos == null) {
            LOG.info("[Crafter {}] redstone root-order rejected: manager unavailable managerPos={} patternIndex={} repeats={}",
                    pos, managerPos, idx, repeats);
            return false;
        }

        TileEntity te = world.getTileEntity(managerPos);
        if (!(te instanceof TileMirrorManager)) {
            LOG.info("[Crafter {}] redstone root-order rejected: target is not manager managerPos={} targetClass={} patternIndex={} repeats={}",
                    pos, managerPos, te == null ? "null" : te.getClass().getName(), idx, repeats);
            return false;
        }
        TileMirrorManager manager = (TileMirrorManager) te;
        if (managerRedstoneOrderId != null && manager.isOrderActive(managerRedstoneOrderId)) {
            LOG.info("[Crafter {}] redstone root-order already active managerPos={} orderId={} patternIndex={} repeats={}",
                    pos, managerPos, managerRedstoneOrderId, idx, repeats);
            return true;
        }
        managerRedstoneOrderId = null;

        ItemStack pat = patterns.getStackInSlot(idx);
        if (pat.isEmpty()) {
            LOG.info("[Crafter {}] redstone root-order rejected: empty pattern slot index={} managerPos={}",
                    pos, idx, managerPos);
            return false;
        }
        NonNullList<ItemStack> grid = getGrid(pat);
        ItemStack preview = getCraftPreview(pat, grid);
        if (preview.isEmpty()) {
            LOG.info("[Crafter {}] redstone root-order rejected: empty preview index={} managerPos={}",
                    pos, idx, managerPos);
            return false;
        }
        ItemKey key = ItemKey.of(preview);
        if (key == null || key == ItemKey.EMPTY) {
            LOG.info("[Crafter {}] redstone root-order rejected: invalid key index={} managerPos={}",
                    pos, idx, managerPos);
            return false;
        }

        int amount = Math.max(1, preview.getCount()) * Math.max(1, repeats);
        if (!manager.canAcceptCraftRequest(key, amount)) {
            LOG.info("[Crafter {}] redstone root-order rejected: craft request cannot be accepted managerPos={} key={} amount={} patternIndex={} repeats={}",
                    pos, managerPos, key, amount, idx, repeats);
            return false;
        }

        UUID id = manager.submitCraftRequest(
                key,
                amount,
                OrderSourceType.REDSTONE_CRAFTER,
                this.pos,
                null,
                therealpant.thaumicattempts.golemnet.logistics.NetworkOrder.RequestIntent.CRAFT_ONLY
        );
        if (id != null) {
            managerRedstoneOrderId = id;
            LOG.info("[Crafter {}] redstone root-order submit success managerPos={} orderId={} key={} amount={} patternIndex={} repeats={}",
                    pos, managerPos, id, key, amount, idx, repeats);
            return true;// попробуем позже
        }
        LOG.info("[Crafter {}] redstone root-order submit failed managerPos={} key={} amount={} patternIndex={} repeats={}",
                pos, managerPos, key, amount, idx, repeats);
        return false;
    }

    // ===== Tick =====
    @Override
    public void onLoad() {
        super.onLoad();
        if (world != null && !world.isRemote) {
            this.lastSignal = readSignal(); // синхронизация, чтобы не стартовать случайно
            syncManagerFromPattern();
        }
    }


    @Override
    public void update() {
        if (world == null) return;

        if (!world.isRemote) {
            syncManagerFromPattern();
        }

        if (world.isRemote) return;

        if (supplyCooldown > 0) supplyCooldown--;
        drawEssentiaTicked();
        if (suctionPingCooldown-- <= 0) {
            if (craftAmount < CRAFT_CAP) pingNeighbors();
            suctionPingCooldown = 20;
        }

        // СНАЧАЛА пробуем запустить из очереди реквестера
        int signal = readSignal();

        if (!jobActive && !managedExecutionQueue.isEmpty()) {
            tryStartNextManagedJob();
        }

        // 1) если есть очередь от реквестера — запускаем её первой
        if (!jobActive && !requesterQueue.isEmpty()) {
            tryStartNextRequesterJob();
        }

        // 2) редстоун-триггер по фронту — только внешний root-order/legacy старт
        if (!jobActive && managedExecutionQueue.isEmpty() && requesterQueue.isEmpty() && lastSignal == 0 && signal > 0) {
            int idx = choosePatternIndexForSignal(signal);
            if (idx >= 0) {
                ItemStack pat = patterns.getStackInSlot(idx);
                int repeats = getPatternRepeatCount(pat);
                boolean managerMode = (managerPos != null);
                LOG.info("[Crafter {}] redstone edge detected signal={} selectedPatternIndex={} managerMode={} managerPos={}",
                        pos, signal, idx, managerMode, managerPos);
                this.jobViaRequester = false;
                if (managerMode) {
                    boolean submitted = submitManagerCraftOrderForSignal(idx, repeats);
                    LOG.info("[Crafter {}] redstone root-order submit result success={} selectedPatternIndex={} managerMode={} managerPos={} orderId={}",
                            pos, submitted, idx, managerMode, managerPos, managerRedstoneOrderId);
                } else {
                    this.suppressSelfProvision = false;
                    int outputItems = Math.max(1, previewForPattern(idx).getCount()) * Math.max(1, repeats);
                    startOneCraftCycle(idx, outputItems);
                    LOG.info("[Crafter {}] redstone started legacy/local cycle selectedPatternIndex={} repeats={} managerMode={} managerPos={}",
                            pos, idx, repeats, managerMode, managerPos);
                }
            }
        }

        if (jobActive) runActiveJob();

        lastSignal = signal;
    }


    private ItemStack previewForPattern(int idx) {
        if (idx < 0 || idx >= PATTERN_SLOTS) return ItemStack.EMPTY;
        ItemStack pat = patterns.getStackInSlot(idx);
        if (pat.isEmpty()) return ItemStack.EMPTY;
        return getCraftPreview(pat, getGrid(pat));
    }

    protected void startOneCraftCycle(int idx, int outputItems) {
        this.costRemaining = 0;

        jobActive = true;
        jobPatternIndex = idx;
        ItemStack preview = previewForPattern(idx);
        int perCraft = Math.max(1, preview.getCount());
        totalOrderOutputInitialItems = Math.max(1, outputItems);
        totalOrderOutputRemainingItems = totalOrderOutputInitialItems;
        totalCraftsInitialForOrder = Math.max(1, (totalOrderOutputInitialItems + perCraft - 1) / perCraft);
        totalCraftsRemainingForOrder = totalCraftsInitialForOrder;
        activeBatchCraftsRemaining = 0;
        activeBatchCraftsTotal = 0;
        activeBatchOutputItems = 0;
        activeBatchOutputPerCraft = perCraft;
        activeBatchEssentiaDebt = 0;


        seq.clear();
        step = 0;

        waitingForEssentia = false;
        lastEssentiaWaitLogTime = 0L;
        waitingForOutputSpace = false;
        lastOutputWaitLogTime = 0L;
        stalledJobTicks = 0;

        long now = (world != null) ? world.getTotalWorldTime() : 0L;
        lastOrderWorldTime = now - REORDER_TICKS;
        supplyCooldown = 0;

        markDirty();
    }

    protected void startOneCraftCycle(int idx) {
        ItemStack preview = previewForPattern(idx);
        int perCraft = Math.max(1, preview.getCount());
        startOneCraftCycle(idx, perCraft);
    }


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

        stalledJobTicks++;

        if (jobPatternIndex < 0 || jobPatternIndex >= PATTERN_SLOTS) {
            LOG.warn("[Crafter {}] aborting active job: invalid pattern index={}", pos, jobPatternIndex);
            finishJob();
            return;
        }

        ItemStack pat = patterns.getStackInSlot(jobPatternIndex);
        if (pat.isEmpty()) {
            LOG.warn("[Crafter {}] aborting active job: empty pattern slot index={}", pos, jobPatternIndex);
            finishJob();
            return;
        }

        NonNullList<ItemStack> grid = getGrid(pat);

        if (seq.isEmpty()) rebuildSequence(pat, grid);

        ItemStack preview = getCraftPreview(pat, grid);
        if (preview.isEmpty()) {
            LOG.warn("[Crafter {}] aborting active job: empty craft preview patternIndex={}", pos, jobPatternIndex);
            finishJob();
            return;
        }

        if (activeBatchCraftsRemaining <= 0) {
            if (totalCraftsRemainingForOrder <= 0 || totalOrderOutputRemainingItems <= 0) {
                finishJob();
                return;
            }
            if (essentiaDebt > 0 || craftAmount <= 0) {
                long now = world.getTotalWorldTime();
                if (lastEssentiaWaitLogTime == 0L || now - lastEssentiaWaitLogTime >= 40L) {
                    LOG.info("[Crafter {}] waiting next batch due to essentia gate craftAmount={} essentiaDebt={} remainingOrderItems={}",
                            pos, craftAmount, essentiaDebt, totalOrderOutputRemainingItems);
                    lastEssentiaWaitLogTime = now;
                }
                waitingForEssentia = true;
                return;
            }

            int outputPerCraft = Math.max(1, preview.getCount());
            int craftsPerBatch = Math.max(1, (BATCH_OUTPUT_LIMIT + outputPerCraft - 1) / outputPerCraft);
            int maxByItems = Math.max(1, (totalOrderOutputRemainingItems + outputPerCraft - 1) / outputPerCraft);
            activeBatchCraftsTotal = Math.max(1, Math.min(totalCraftsRemainingForOrder, Math.min(craftsPerBatch, maxByItems)));
            activeBatchCraftsRemaining = activeBatchCraftsTotal;
            activeBatchOutputPerCraft = outputPerCraft;
            activeBatchOutputItems = Math.min(totalOrderOutputRemainingItems, activeBatchCraftsTotal * outputPerCraft);
            activeBatchEssentiaDebt = essentiaDebt;
            seq.clear();
            rebuildSequence(pat, grid);
            step = 0;
            stalledJobTicks = 0;
            LOG.info("[Crafter {}] batch start patternIndex={} outputPerCraft={} craftsInBatch={} batchOutputItems={} craftAmount={} essentiaDebt={}",
                    pos, jobPatternIndex, outputPerCraft, activeBatchCraftsTotal, activeBatchOutputItems, craftAmount, essentiaDebt);
        }

        if (costRemaining <= 0) costRemaining = getPerCraftCost(pat, grid) * Math.max(1, activeBatchCraftsTotal);

        if (isAllInputsReadyForJob()) step = seq.size();

        // ----- Поочередная проверка входов и оплата по типам -----
        if (step < seq.size()) {
            Req r = seq.get(step);
            int have = countInInput(r.key1);
            int repeats = Math.max(1, activeBatchCraftsRemaining);
            int totalNeed = r.count * repeats;

            if (have >= totalNeed) {
                int chunk = Math.max(1, perTypeEssentia) * Math.max(1, activeBatchCraftsRemaining);
                tryPayEssentiaChunk(chunk);
                waitingForEssentia = false;
                stalledJobTicks = 0;
                step++;
            } else {
                waitingForEssentia = false;

                int missingTotal = Math.max(0, totalNeed - have);

                if (useManagerForProvision()) {
                    if (stalledJobTicks == 40 || stalledJobTicks == 100 || stalledJobTicks % 200 == 0) {
                        LOG.info("[Crafter {}] waiting for managed inputs step={} patternIndex={} have={} need={} missing={}",
                                pos, step, jobPatternIndex, have, totalNeed, missingTotal);
                    }
                    return;
                }

                long now = world.getTotalWorldTime();
                if (!suppressSelfProvision && (lastOrderWorldTime == 0L || now - lastOrderWorldTime >= REORDER_TICKS)) {
                    ItemStack req = normalizeForProvision(r.key1, Math.min(missingTotal, r.key1.getMaxStackSize()));
                    GolemHelper.requestProvisioning(world, pos, EnumFacing.UP, req, 0);
                    lastOrderWorldTime = now;
                    supplyCooldown = SUPPLY_COOLDOWN;
                }

                if (stalledJobTicks == 40 || stalledJobTicks == 100 || stalledJobTicks % 200 == 0) {
                    LOG.info("[Crafter {}] waiting for inputs step={} patternIndex={} have={} need={} missing={}",
                            pos, step, jobPatternIndex, have, totalNeed, missingTotal);
                }
                return;
            }
        }

        // ----- Финальная фаза -----
        if (step >= seq.size()) {
            if (costRemaining > 0) {
                int chunk = Math.min(Math.max(1, perTypeEssentia), costRemaining);
                if (!tryPayEssentiaChunk(chunk)) {
                    waitingForEssentia = true;

                    long now = world.getTotalWorldTime();
                    if (lastEssentiaWaitLogTime == 0L || now - lastEssentiaWaitLogTime >= 40L) {
                        LOG.info("[Crafter {}] waiting for essentia before final craft commit patternIndex={} craftAmount={} costRemaining={} requiredAspect={}",
                                pos, jobPatternIndex, craftAmount, costRemaining, requiredAspect);
                        lastEssentiaWaitLogTime = now;
                    }
                    return;
                } else {
                    waitingForEssentia = false;
                }
            }

            if (!hasAllForGrid(grid, activeBatchCraftsTotal)) {
                if (stalledJobTicks == 40 || stalledJobTicks == 100 || stalledJobTicks % 200 == 0) {
                    LOG.info("[Crafter {}] final phase waiting for inputs patternIndex={} costRemaining={} craftAmount={}",
                            pos, jobPatternIndex, costRemaining, craftAmount);
                }
                return;
            }

            List<ItemStack> batchResult = buildBatchResultStackList(preview, activeBatchOutputItems);
            if (!canAcceptBatchResult(batchResult)) {
                waitingForOutputSpace = true;

                long now = world.getTotalWorldTime();
                if (lastOutputWaitLogTime == 0L || now - lastOutputWaitLogTime >= 40L) {
                    LOG.info("[Crafter {}] waiting for output space patternIndex={} batchOutputItems={} outputSlots={} costRemaining={}",
                            pos, jobPatternIndex, activeBatchOutputItems, output.getSlots(), costRemaining);
                    lastOutputWaitLogTime = now;
                }
                return;
            } else {
                waitingForOutputSpace = false;
            }

            int finishedBatchOutput = activeBatchOutputItems;
            int finishedBatchCrafts = activeBatchCraftsTotal;
            consumeForGrid(grid, finishedBatchCrafts);
            pushBatchResult(batchResult);

            totalCraftsRemainingForOrder = Math.max(0, totalCraftsRemainingForOrder - finishedBatchCrafts);
            totalOrderOutputRemainingItems = Math.max(0, totalOrderOutputRemainingItems - finishedBatchOutput);
            activeBatchCraftsRemaining = 0;
            activeBatchCraftsTotal = 0;
            activeBatchOutputItems = 0;
            activeBatchEssentiaDebt = essentiaDebt;
            stalledJobTicks = 0;
            markDirty();
            pingNeighbors();

            LOG.info("[Crafter {}] batch finished batchOutputItems={} remainingOrderItems={} craftAmount={} essentiaDebt={}",
                    pos, finishedBatchOutput, totalOrderOutputRemainingItems, craftAmount, essentiaDebt);

            seq.clear();
            step = 0;
            costRemaining = 0;
            long now = world != null ? world.getTotalWorldTime() : 0L;
            lastOrderWorldTime = now - REORDER_TICKS;
            supplyCooldown = 0;
            if (totalOrderOutputRemainingItems <= 0 || totalCraftsRemainingForOrder <= 0) {
                finishJob();
            }

            finishJob();
        }
    }


    private void finishJob() {
        this.jobActive = false;
        this.jobPatternIndex = -1;
        this.totalCraftsRemainingForOrder = 0;
        this.totalCraftsInitialForOrder = 0;
        this.totalOrderOutputRemainingItems = 0;
        this.totalOrderOutputInitialItems = 0;
        this.activeBatchCraftsRemaining = 0;
        this.activeBatchCraftsTotal = 0;
        this.activeBatchOutputItems = 0;
        this.activeBatchOutputPerCraft = 1;
        this.activeBatchEssentiaDebt = 0;
        this.seq.clear();
        this.step = 0;
        this.lastOrderWorldTime = 0;
        this.waitingForEssentia = false;
        this.lastEssentiaWaitLogTime = 0L;
        this.waitingForOutputSpace = false;
        this.lastOutputWaitLogTime = 0L;
        this.stalledJobTicks = 0;
        this.costRemaining = 0;

        this.jobViaRequester = false;
        this.jobViaManagerAssignment = false;
        this.suppressSelfProvision = false;

        if (world == null) return;

        if (!managedExecutionQueue.isEmpty()) {
            tryStartNextManagedJob();
            return;
        }

        if (!requesterQueue.isEmpty()) {
            tryStartNextRequesterJob();
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

    private boolean isAllInputsReadyForJob() {
        if (seq.isEmpty()) return true;
        int repeats = Math.max(1, activeBatchCraftsRemaining);
        for (Req r : seq) {
            if (r == null || r.key1 == null || r.key1.isEmpty()) continue;
            int need = Math.max(0, r.count * repeats);
            if (countInInput(r.key1) < need) return false;
        }
        return true;
    }

    // ===== IEssentiaTransport =====
    private static boolean canFaceInput(EnumFacing face) {
        if (face == null) return false;
        for (EnumFacing f : ESSENTIA_INPUT_FACES) if (f == face) return true;
        return false;
    }

    @Override public boolean isConnectable(EnumFacing face) { return canFaceInput(face); }
    @Override public boolean canInputFrom(EnumFacing face)  { return canFaceInput(face); }
    @Override public boolean canOutputTo(EnumFacing face)   { return false; }
    @Override public void setSuction(Aspect aspect, int amount) {}
    @Override public Aspect getSuctionType(EnumFacing face) { return (canFaceInput(face) && (craftAmount < CRAFT_CAP || essentiaDebt > 0)) ? requiredAspect : null; }
    @Override public int getSuctionAmount(EnumFacing face)  { return (canFaceInput(face) && (craftAmount < CRAFT_CAP || essentiaDebt > 0)) ? CRAFT_SUCTION : 0; }
    @Override public int addEssentia(Aspect aspect, int amount, EnumFacing face) {
        if (!canFaceInput(face)) return 0;
        if (aspect != requiredAspect || amount <= 0) return 0;
        int can = Math.min(amount, CRAFT_CAP - craftAmount + essentiaDebt);
        if (can <= 0) return 0;
        craftAmount += can;
        acceptIncomingEssentia(can);
        return can;
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
        nbt.setInteger("EssentiaDebt", essentiaDebt);
        nbt.setString("ReqAspect", requiredAspect == null ? "" : requiredAspect.getTag());
        nbt.setInteger("PerTypeEssentia", perTypeEssentia);
        nbt.setInteger("CostRemaining", costRemaining);

        nbt.setBoolean("JobActive", jobActive);
        nbt.setInteger("JobPatternIndex", jobPatternIndex);
        nbt.setInteger("Step", step);
        nbt.setLong("LastOrderWT", lastOrderWorldTime);
        nbt.setInteger("OrderCraftsRemaining", totalCraftsRemainingForOrder);
        nbt.setInteger("OrderCraftsTotal", totalCraftsInitialForOrder);
        nbt.setInteger("OrderOutputRemaining", totalOrderOutputRemainingItems);
        nbt.setInteger("OrderOutputTotal", totalOrderOutputInitialItems);
        nbt.setInteger("BatchCraftsRemaining", activeBatchCraftsRemaining);
        nbt.setInteger("BatchCraftsTotal", activeBatchCraftsTotal);
        nbt.setInteger("BatchOutputItems", activeBatchOutputItems);
        nbt.setInteger("BatchOutputPerCraft", activeBatchOutputPerCraft);
        nbt.setInteger("BatchEssentiaDebt", activeBatchEssentiaDebt);

        nbt.setInteger("LastSignal", lastSignal);

        nbt.setBoolean("WaitingForEssentia", waitingForEssentia);
        nbt.setLong("LastEssentiaWaitLogTime", lastEssentiaWaitLogTime);
        nbt.setBoolean("WaitingForOutputSpace", waitingForOutputSpace);
        nbt.setLong("LastOutputWaitLogTime", lastOutputWaitLogTime);
        nbt.setInteger("StalledJobTicks", stalledJobTicks);

        net.minecraft.nbt.NBTTagList rq = new net.minecraft.nbt.NBTTagList();
        for (JobRequest req : requesterQueue) {
            net.minecraft.nbt.NBTTagCompound c = new net.minecraft.nbt.NBTTagCompound();
            c.setInteger("I", req == null ? -1 : req.patternIndex);
            c.setInteger("O", req == null ? 0 : req.outputItems);
            rq.appendTag(c);
        }
        nbt.setTag("RequesterQ", rq);

        net.minecraft.nbt.NBTTagList mq = new net.minecraft.nbt.NBTTagList();
        for (JobRequest req : managedExecutionQueue) {
            net.minecraft.nbt.NBTTagCompound c = new net.minecraft.nbt.NBTTagCompound();
            c.setInteger("I", req == null ? -1 : req.patternIndex);
            c.setInteger("O", req == null ? 0 : req.outputItems);
            mq.appendTag(c);
        }
        nbt.setTag("ManagedQ", mq);

        if (managerPos != null) nbt.setLong("Manager", managerPos.toLong());
        nbt.setBoolean("ManagerPattern", managerFromPattern && managerPos != null);

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
        essentiaDebt = Math.max(0, nbt.getInteger("EssentiaDebt"));
        Aspect a = Aspect.getAspect(nbt.getString("ReqAspect"));
        if (a != null) requiredAspect = a;
        perTypeEssentia = Math.max(1, nbt.getInteger("PerTypeEssentia"));
        costRemaining = Math.max(0, nbt.getInteger("CostRemaining"));

        jobActive = nbt.getBoolean("JobActive");
        jobPatternIndex = nbt.getInteger("JobPatternIndex");
        step = Math.max(0, nbt.getInteger("Step"));
        lastOrderWorldTime = nbt.getLong("LastOrderWT");
        totalCraftsRemainingForOrder = Math.max(0, nbt.getInteger("OrderCraftsRemaining"));
        totalCraftsInitialForOrder = Math.max(0, nbt.getInteger("OrderCraftsTotal"));
        totalOrderOutputRemainingItems = Math.max(0, nbt.getInteger("OrderOutputRemaining"));
        totalOrderOutputInitialItems = Math.max(0, nbt.getInteger("OrderOutputTotal"));
        activeBatchCraftsRemaining = Math.max(0, nbt.getInteger("BatchCraftsRemaining"));
        activeBatchCraftsTotal = Math.max(0, nbt.getInteger("BatchCraftsTotal"));
        activeBatchOutputItems = Math.max(0, nbt.getInteger("BatchOutputItems"));
        activeBatchOutputPerCraft = Math.max(1, nbt.getInteger("BatchOutputPerCraft"));
        activeBatchEssentiaDebt = Math.max(0, nbt.getInteger("BatchEssentiaDebt"));

        lastSignal = nbt.getInteger("LastSignal");

        waitingForEssentia = nbt.getBoolean("WaitingForEssentia");
        lastEssentiaWaitLogTime = nbt.getLong("LastEssentiaWaitLogTime");
        waitingForOutputSpace = nbt.getBoolean("WaitingForOutputSpace");
        lastOutputWaitLogTime = nbt.getLong("LastOutputWaitLogTime");
        stalledJobTicks = Math.max(0, nbt.getInteger("StalledJobTicks"));

        requesterQueue.clear();
        if (nbt.hasKey("RequesterQ", Constants.NBT.TAG_LIST)) {
            net.minecraft.nbt.NBTTagList rq = nbt.getTagList("RequesterQ", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < rq.tagCount(); i++) {
                net.minecraft.nbt.NBTTagCompound c = rq.getCompoundTagAt(i);
                int idx = c.getInteger("I");
                int out = Math.max(1, c.hasKey("O", Constants.NBT.TAG_INT) ? c.getInteger("O") : 1);
                if (idx >= 0 && idx < PATTERN_SLOTS) requesterQueue.addLast(new JobRequest(idx, out));
            }
        }

        managedExecutionQueue.clear();
        if (nbt.hasKey("ManagedQ", Constants.NBT.TAG_LIST)) {
            net.minecraft.nbt.NBTTagList mq = nbt.getTagList("ManagedQ", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < mq.tagCount(); i++) {
                net.minecraft.nbt.NBTTagCompound c = mq.getCompoundTagAt(i);
                int idx = c.getInteger("I");
                int out = Math.max(1, c.hasKey("O", Constants.NBT.TAG_INT) ? c.getInteger("O") : 1);
                if (idx >= 0 && idx < PATTERN_SLOTS) managedExecutionQueue.addLast(new JobRequest(idx, out));
            }
        }

        managerPos = nbt.hasKey("Manager", Constants.NBT.TAG_LONG)
                ? BlockPos.fromLong(nbt.getLong("Manager")) : null;
        managerFromPattern = nbt.getBoolean("ManagerPattern") && managerPos != null;

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
