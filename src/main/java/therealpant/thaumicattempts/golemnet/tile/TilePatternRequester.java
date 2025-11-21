package therealpant.thaumicattempts.golemnet.tile;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.items.ItemTCEssentiaContainer;
import therealpant.thaumicattempts.golemcraft.item.ItemArcanePattern;
import therealpant.thaumicattempts.golemcraft.item.ItemBasePattern;
import therealpant.thaumicattempts.golemcraft.tile.TileEntityGolemCrafter;

import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Реквестер (плита над крафтером).
 * - НИЧЕГО не крафтит сам — только публикует каталожную инфу и «правильные» входы 1-в-1 как у крафтера.
 * - Менеджер читает отсюда:
 *      listCraftableResults()
 *      getPerCraftOutputCountFor(result)
 *      getRecipeInputsFor(result, times)
 * - Поддерживает привязку к менеджеру: get/set/clearManagerPos*.
 * - Имеет простой редстоун-выход (метод getOutSignal) — сейчас всегда 0 (пульс можно нарастить позже).
 */
public class TilePatternRequester extends TileEntity implements ITickable, IAnimatable {
    private final AnimationFactory factory = new AnimationFactory(this);


    // ===== Geckolib =====
    @Override
    public void registerControllers(AnimationData data) {
        data.addAnimationController(new AnimationController<>(
                this,
                "main_controller",
                0,
                this::animationPredicate
        ));
    }

    private <E extends IAnimatable> PlayState animationPredicate(AnimationEvent<E> event) {
        event.getController().setAnimation(
                new AnimationBuilder().addAnimation("animation.model.pattern_requester", true)
        );
        return PlayState.CONTINUE;
    }

    @Override
    public AnimationFactory getFactory() {
        return factory;
    }

    /* ====== Связь с менеджером ====== */

    @Nullable private BlockPos managerPos = null;
    private boolean needsRebind = false;

    /** Где сейчас привязан менеджер (или null). */
    public @Nullable BlockPos getManagerPos() { return managerPos; }

    /** Привязать к менеджеру и зарегистрироваться внутри него. */
    public void setManagerPos(@Nullable BlockPos pos) {
        this.managerPos = pos;
        markDirty();
    }

    /** Тихо сбросить привязку, если инициатор – именно этот менеджер. */
    public void clearManagerPosFromManager(BlockPos mp) {
        if (this.managerPos != null && this.managerPos.equals(mp)) {
            setManagerPos(null);
        }
    }

    /* ====== Ссылка на крафтер снизу ====== */
    @Nullable
    private TileEntityGolemCrafter getCrafterBelow() {
        if (world == null) return null;
        TileEntity te = world.getTileEntity(pos.down());
        return (te instanceof TileEntityGolemCrafter) ? (TileEntityGolemCrafter) te : null;
    }

    /* ====== Константы NBT, совпадающие с крафтером/паттерном ====== */
    private static final String TAG_RESULT = "Result";
    private static final String TAG_GRID   = "Grid";

    /* ====== Редстоун-выход (сейчас — всегда 0) ====== */
    private int outSignal = 0;
    public int getOutSignal() { return Math.max(0, Math.min(15, outSignal)); }

    /* Если нужен пульс в будущем — можно дергать это: */
    public void pulseOutSignal(int ticks, int level) {
        outSignal = Math.max(outSignal, Math.max(0, Math.min(15, level)));
        pulseTicks = Math.max(pulseTicks, Math.max(1, ticks));
        markDirty();
    }
    private int pulseTicks = 0;

    /* ====== Tick: гасим пульс, если он был ====== */
    @Override
    public void update() {
        if (world == null) return;

        if (!world.isRemote) {
            rsQueueTick(); // ← держит/сбрасывает сигнал по очереди крафтов
        }

        if (world.isRemote) return;

        // ваш существующий код пульса (если нужен):
        if (pulseTicks > 0) {
            pulseTicks--;
            if (pulseTicks == 0) {
                outSignal = 0;
                markDirty();
                world.notifyNeighborsOfStateChange(pos, world.getBlockState(pos).getBlock(), true);
            }
        }
    }


    /* ====== Публикация каталога крафтабельного ====== */

    /** Список всех результатов из паттернов (каждый — с правильным count за 1 крафт). */
    public List<ItemStack> listCraftableResults() {
        TileEntityGolemCrafter cr = getCrafterBelow();
        if (cr == null) return Collections.emptyList();

        IItemHandler patt = cr.getPatternHandler();
        if (patt == null) return Collections.emptyList();

        ArrayList<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < patt.getSlots(); i++) {
            ItemStack pat = patt.getStackInSlot(i);
            if (pat.isEmpty() || !(pat.getItem() instanceof ItemBasePattern)) continue;

            NonNullList<ItemStack> grid = readGridFromPattern(pat);
            ItemStack preview = calcPreview(pat, grid);
            if (preview.isEmpty()) continue;

            ItemStack one = preview.copy();
            if (one.getCount() <= 0) one.setCount(1);
            out.add(one);
        }
        return out;
    }

    /** Сколько штук даёт ровно один крафт для результата «как этот». */
    public int getPerCraftOutputCountFor(ItemStack like) {
        if (like == null || like.isEmpty()) return 0;

        TileEntityGolemCrafter cr = getCrafterBelow();
        if (cr == null) return 0;

        IItemHandler patt = cr.getPatternHandler();
        if (patt == null) return 0;

        for (int i = 0; i < patt.getSlots(); i++) {
            ItemStack pat = patt.getStackInSlot(i);
            if (pat.isEmpty() || !(pat.getItem() instanceof ItemBasePattern)) continue;

            NonNullList<ItemStack> grid = readGridFromPattern(pat);
            ItemStack preview = calcPreview(pat, grid);
            if (preview.isEmpty()) continue;

            if (matchForRecipeRelaxed(preview, like)) {
                return Math.max(1, preview.getCount());
            }
        }
        return 0;
    }

    /**
     * Полный список входов, необходимых для `times` крафтов (агрегирован по «ключу сетки»).
     * Ключ и сравнение в точности как у крафтера:
     *  - кристаллы — по аспекту;
     *  - нестакуемые — item+meta (NBT игнорируется);
     *  - стакаемые — relaxed.
     */
    public List<ItemStack> getRecipeInputsFor(ItemStack resultLike, int times) {
        if (resultLike == null || resultLike.isEmpty() || times <= 0) return Collections.emptyList();

        TileEntityGolemCrafter cr = getCrafterBelow();
        if (cr == null) return Collections.emptyList();

        IItemHandler patt = cr.getPatternHandler();
        if (patt == null) return Collections.emptyList();

        for (int i = 0; i < patt.getSlots(); i++) {
            ItemStack pat = patt.getStackInSlot(i);
            if (pat.isEmpty() || !(pat.getItem() instanceof ItemBasePattern)) continue;

            NonNullList<ItemStack> grid = readGridFromPattern(pat);
            ItemStack preview = calcPreview(pat, grid);
            if (preview.isEmpty()) continue;

            if (!matchForRecipeRelaxed(preview, resultLike)) continue;

            // === агрегируем входы как в крафтере (sameForGrid/key1ForGrid)
            Map<ItemStack, Integer> need = new LinkedHashMap<>();
            for (int g = 0; g < 9; g++) {
                ItemStack s = grid.get(g);
                if (s.isEmpty()) continue;
                ItemStack k1 = key1ForGrid(s);
                if (k1.isEmpty()) continue;

                ItemStack found = null;
                for (ItemStack ex : need.keySet()) { if (sameForGrid(ex, k1)) { found = ex; break; } }
                if (found == null) need.put(k1, 1);
                else need.put(found, need.get(found) + 1);
            }

            // === ДОБАВКА: если под нами аркан-крафтер — докладываем прималы из паттерна
            if (cr instanceof therealpant.thaumicattempts.golemcraft.tile.TileEntityArcaneCrafter) {
                int[] counts = therealpant.thaumicattempts.golemcraft.item.ItemArcanePattern.getCrystalCounts(pat);
                thaumcraft.api.aspects.Aspect[] primals = therealpant.thaumicattempts.golemcraft.item.ItemArcanePattern.PRIMALS;
                if (counts != null && primals != null) {
                    for (int j = 0; j < primals.length && j < counts.length; j++) {
                        int c = Math.max(0, counts[j]);
                        if (c <= 0) continue;
                        ItemStack k1 = thaumcraft.api.ThaumcraftApiHelper.makeCrystal(primals[j], 1);

                        ItemStack found = null;
                        for (ItemStack ex : need.keySet()) { if (sameForGrid(ex, k1)) { found = ex; break; } }
                        if (found == null) need.put(k1, c);
                        else need.put(found, need.get(found) + c);
                    }
                }
            }

            if (need.isEmpty()) return Collections.emptyList();

            ArrayList<ItemStack> out = new ArrayList<>(need.size());
            for (Map.Entry<ItemStack,Integer> e : need.entrySet()) {
                int total = Math.max(0, e.getValue()) * Math.max(1, times);
                if (total <= 0) continue;
                ItemStack k = e.getKey().copy();
                k.setCount(total);
                out.add(k);
            }
            return out;
        }

        return Collections.emptyList();
    }

    /* ====== Чтение сетки и превью из самого паттерна ====== */

    private NonNullList<ItemStack> readGridFromPattern(ItemStack pattern) {
        NonNullList<ItemStack> grid = NonNullList.withSize(9, ItemStack.EMPTY);
        if (pattern == null || pattern.isEmpty()) return grid;
        NBTTagCompound tag = pattern.getTagCompound();
        if (tag == null || !tag.hasKey(TAG_GRID, Constants.NBT.TAG_LIST)) return grid;

        NBTTagList list = tag.getTagList(TAG_GRID, Constants.NBT.TAG_COMPOUND);
        int max = Math.min(9, list.tagCount());
        for (int i = 0; i < max; i++) {
            ItemStack s = new ItemStack(list.getCompoundTagAt(i));
            grid.set(i, (s == null ? ItemStack.EMPTY : s));
        }
        return grid;
    }

    private ItemStack readStoredPreview(ItemStack pattern) {
        if (pattern == null || pattern.isEmpty()) return ItemStack.EMPTY;

        // Аркан-паттерн имеет собственный вычислитель превью
        if (pattern.getItem() instanceof ItemArcanePattern) {
            ItemStack arc = ItemArcanePattern.calcArcaneResultPreview(pattern, world);
            if (arc != null && !arc.isEmpty()) return arc.copy();
        }

        NBTTagCompound tag = pattern.getTagCompound();
        if (tag != null && tag.hasKey(TAG_RESULT, Constants.NBT.TAG_COMPOUND)) {
            ItemStack res = new ItemStack(tag.getCompoundTag(TAG_RESULT));
            if (!res.isEmpty()) return res;
        }
        return ItemStack.EMPTY;
    }

    /** Ровно как у крафтера: сначала NBT-превью, иначе ванильный CraftingManager. */
    private ItemStack calcPreview(ItemStack pattern, NonNullList<ItemStack> grid) {
        ItemStack cached = readStoredPreview(pattern);
        if (!cached.isEmpty()) return cached;

        if (grid == null) return ItemStack.EMPTY;
        InventoryCrafting inv = new InventoryCrafting(new Container() {
            @Override public boolean canInteractWith(EntityPlayer playerIn) { return false; }
        }, 3, 3);
        for (int i = 0; i < 9; i++) inv.setInventorySlotContents(i, grid.get(i).copy());
        ItemStack direct = CraftingManager.findMatchingResult(inv, world);
        return (direct == null || direct.isEmpty()) ? ItemStack.EMPTY : direct.copy();
    }

    /* ====== Матчинг/нормализация — 1в1 с крафтером ====== */

    private static boolean isTcCrystal(ItemStack s) {
        return s != null && !s.isEmpty() && s.getItem() == ItemsTC.crystalEssence;
    }
    @Nullable
    private static Aspect crystalAspect(ItemStack s) {
        if (!isTcCrystal(s)) return null;
        thaumcraft.api.aspects.AspectList al =
                ((ItemTCEssentiaContainer) ItemsTC.crystalEssence).getAspects(s);
        if (al != null && al.size() == 1) return al.getAspects()[0];
        return null;
    }

    /** Тот же ключ, что использует крафтер для «входов сетки». */
    private static ItemStack key1ForGrid(ItemStack s) {
        if (s == null || s.isEmpty()) return ItemStack.EMPTY;
        if (isTcCrystal(s)) {
            Aspect a = crystalAspect(s);
            return (a == null) ? ItemStack.EMPTY : thaumcraft.api.ThaumcraftApiHelper.makeCrystal(a, 1);
        }
        if (s.getMaxStackSize() == 1) { // без NBT
            return new ItemStack(s.getItem(), 1, s.getMetadata());
        }
        ItemStack k = s.copy(); k.setCount(1); return k;
    }

    /** Совпадение «как у крафтера» для входов. */
    private static boolean sameForGrid(ItemStack a, ItemStack b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;

        if (isTcCrystal(a) || isTcCrystal(b)) {
            Aspect ax = crystalAspect(a), bx = crystalAspect(b);
            return ax != null && ax == bx;
        }
        if (a.getMaxStackSize() == 1 || b.getMaxStackSize() == 1) {
            if (a.getItem() != b.getItem()) return false;
            if (a.getHasSubtypes() && a.getMetadata() != b.getMetadata()) return false;
            return true; // NBT игнорируем
        }
        return ItemHandlerHelper.canItemStacksStackRelaxed(a, b);
    }

    /** Совпадение результата (послабленное для стакаемых, строгое для нестакуемых/кристаллов). */
    private static boolean matchForRecipeRelaxed(ItemStack a, ItemStack b) {
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
        return ItemHandlerHelper.canItemStacksStackRelaxed(a, b);
    }

    /* ====== NBT ====== */
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        if (managerPos != null) nbt.setLong("ManagerPos", managerPos.toLong());
        nbt.setInteger("OutSig", outSignal);
        nbt.setInteger("Pulse", pulseTicks);
        NBTTagList q = new NBTTagList();
        for (RsTask t : rsQueue) {
            if (t == null || t.like1 == null || t.like1.isEmpty() || t.crafts <= 0) continue;
            NBTTagCompound c = new NBTTagCompound();
            c.setTag("S", t.like1.writeToNBT(new NBTTagCompound()));
            c.setInteger("N", t.crafts);
            q.appendTag(c);
        }
        nbt.setTag("RSQ", q);
        return nbt;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        managerPos = nbt.hasKey("ManagerPos", Constants.NBT.TAG_LONG)
                ? BlockPos.fromLong(nbt.getLong("ManagerPos")) : null;
        outSignal = nbt.getInteger("OutSig");
        pulseTicks = nbt.getInteger("Pulse");
        rsQueue.clear();
        if (nbt.hasKey("RSQ", Constants.NBT.TAG_LIST)) {
            NBTTagList q = nbt.getTagList("RSQ", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < q.tagCount(); i++) {
                NBTTagCompound c = q.getCompoundTagAt(i);
                ItemStack s = new ItemStack(c.getCompoundTag("S"));
                int n = c.getInteger("N");
                if (!s.isEmpty() && n > 0) rsQueue.addLast(new RsTask(s, n));
            }
        }
    }

    public int findPatternIndexForResultLike(ItemStack like1) {
        if (like1 == null || like1.isEmpty()) return -1;

        TileEntityGolemCrafter cr = getCrafterBelow();
        if (cr == null) return -1;

        IItemHandler patt = cr.getPatternHandler();
        if (patt == null) return -1;

        for (int i = 0; i < patt.getSlots(); i++) {
            ItemStack pat = patt.getStackInSlot(i);
            if (pat.isEmpty() || !(pat.getItem() instanceof therealpant.thaumicattempts.golemcraft.item.ItemBasePattern)) {
                continue;
            }

            NonNullList<ItemStack> grid = readGridFromPattern(pat);
            ItemStack preview = calcPreview(pat, grid);
            if (preview.isEmpty()) continue;

            // Совпадение ровно как в остальном коде реквестера/крафтера
            if (matchForRecipeRelaxed(preview, like1)) {
                return i;
            }
        }
        return -1;
    }

    // ======= REDSTONE CRAFT QUEUE (fresh minimal implementation) =======

    // --- очередь пунктов заказа ---
    private static final class RsTask {
        final ItemStack like1;     // результат (count=1)
        final int crafts;          // сколько циклов выполнить
        RsTask(ItemStack like1, int crafts) {
            ItemStack s = (like1 == null ? ItemStack.EMPTY : like1.copy());
            if (!s.isEmpty()) s.setCount(1);
            this.like1 = s;
            this.crafts = Math.max(0, crafts);
        }
    }
    private final java.util.Deque<RsTask> rsQueue = new java.util.ArrayDeque<>();

    // --- активный пункт ---
    @Nullable private ItemStack rsLike = ItemStack.EMPTY;
    private int rsCraftsLeft = 0;
    private int rsPerCraft   = 1;   // сколько предметов выдаёт один цикл
    private int rsHoldLevel  = 0;   // сила сигнала = patIdx+1 (1..15)

    // учёт по output крафтера (считаем только «ушедшее»)
    private int rsOutBaseline = 0;  // сколько целевых предметов было в output ДО старта
    private int rsOutPrev     = 0;  // счётчик прошлого тика
    private int rsDelivered   = 0;  // доставлено нашими циклами (в штуках)

    // --- Публичные вызовы из терминала/менеджера ---
    public void queueCraft(ItemStack resultLike, int crafts) {
        if (resultLike == null || resultLike.isEmpty() || crafts <= 0) return;
        rsQueue.addLast(new RsTask(resultLike, crafts));
    }

    public void queueCrafts(java.util.List<java.util.Map.Entry<ItemStack,Integer>> lines) {
        if (lines == null) return;
        for (java.util.Map.Entry<ItemStack,Integer> e : lines) {
            if (e == null) continue;
            ItemStack s = e.getKey();
            int n = (e.getValue() == null ? 0 : e.getValue());
            queueCraft(s, n);
        }
    }

    public void clearCraftQueue() {
        rsQueue.clear();
        rsClearActive();
    }

    public boolean hasActiveOrQueued() {
        return (rsLike != null && !rsLike.isEmpty() && rsCraftsLeft > 0) || !rsQueue.isEmpty();
    }

    public void rsQueueAdd(net.minecraft.item.ItemStack like1, int crafts) {
        // перекидываем на новую реализацию
        this.queueCraft(like1, crafts);
    }

    public void rsQueueClear() {
        // очищаем очередь и активный пункт
        this.clearCraftQueue();
    }

    public boolean rsIsActiveOrQueued() {
        // есть активный пункт или элементы в очереди?
        return this.hasActiveOrQueued();
    }

    // (не обязательно, но на всякий случай, если где-то зовётся)
    public void rsQueueAddAll(java.util.List<java.util.Map.Entry<net.minecraft.item.ItemStack,Integer>> lines) {
        this.queueCrafts(lines);
    }

    // --- tick-движок: зови из update() на сервере ---
    private void rsQueueTick() {
        if (world == null || world.isRemote) return;

        // если нет активного — стартуем следующий
        if ((rsLike == null || rsLike.isEmpty() || rsCraftsLeft <= 0) && !rsQueue.isEmpty()) {
            RsTask t = rsQueue.pollFirst();
            if (t != null) rsStartActive(t);
        }

        // нет активного — держим 0
        if (rsLike == null || rsLike.isEmpty() || rsCraftsLeft <= 0) {
            setOutSignal(0);
            return;
        }

        // держим постоянный уровень, не мигаем
        if (rsHoldLevel > 0) setOutSignal(rsHoldLevel);

        // считаем «ушедшее» из output крафтера
        TileEntityGolemCrafter cr = getCrafterBelow();
        if (cr == null) return;

        int cur = countOutLike(cr, rsLike);
        int removed = Math.max(0, rsOutPrev - cur);
        if (removed > 0) {
            // сперва вычитаем то, что лежало в output до старта
            int base = Math.min(removed, rsOutBaseline);
            rsOutBaseline -= base;
            removed -= base;

            // остаток — это наша партия: преобразуем «штуки» в «циклы»
            if (removed > 0) {
                rsDelivered += removed;
                while (rsDelivered >= rsPerCraft && rsCraftsLeft > 0) {
                    rsDelivered -= rsPerCraft;
                    rsCraftsLeft--;
                }
            }
        }
        rsOutPrev = cur;

        // весь пункт выполнен — снимаем сигнал и стартуем следующий
        if (rsCraftsLeft <= 0) {
            rsClearActive();              // сигнал 0
            if (!rsQueue.isEmpty()) {     // сразу берём следующий
                RsTask t = rsQueue.pollFirst();
                if (t != null) rsStartActive(t);
            }
        }
    }

    // --- старт/сброс активного пункта ---
    private void rsStartActive(RsTask t) {
        if (t == null || t.like1 == null || t.like1.isEmpty() || t.crafts <= 0) { rsClearActive(); return; }

        this.rsLike       = t.like1.copy();
        this.rsCraftsLeft = t.crafts;
        this.rsPerCraft   = Math.max(1, getPerCraftOutputCountFor(this.rsLike));

        int patIdx        = findPatternIndexForResultLike(this.rsLike);
        this.rsHoldLevel  = (patIdx < 0) ? 0 : Math.min(15, patIdx + 1);

        // базовые счётчики по output (чтобы не считать старые остатки как «наши»)
        TileEntityGolemCrafter cr = getCrafterBelow();
        int cur = countOutLike(cr, this.rsLike);
        this.rsOutBaseline = cur;
        this.rsOutPrev     = cur;
        this.rsDelivered   = 0;

        // сразу подаём сигнал (даже если сырьё ещё не пришло)
        setOutSignal(this.rsHoldLevel);
    }

    private void rsClearActive() {
        this.rsLike       = ItemStack.EMPTY;
        this.rsCraftsLeft = 0;
        this.rsPerCraft   = 1;
        this.rsHoldLevel  = 0;
        this.rsOutBaseline= 0;
        this.rsOutPrev    = 0;
        this.rsDelivered  = 0;
        setOutSignal(0);
    }

    // --- утилиты ---
    private int countOutLike(TileEntityGolemCrafter cr, ItemStack like) {
        if (cr == null || like == null || like.isEmpty()) return 0;
        net.minecraftforge.items.IItemHandler out = cr.getOutputHandler();
        if (out == null || out.getSlots() <= 0) return 0;
        int total = 0;
        for (int i = 0; i < out.getSlots(); i++) {
            ItemStack s = out.getStackInSlot(i);
            if (s.isEmpty()) continue;
            if (matchForRecipeRelaxed(s, like)) total += s.getCount();
        }
        return total;
    }

    /** Установить сигнал и уведомить мир. */
    private void setOutSignal(int level) {
        level = Math.max(0, Math.min(15, level));
        if (this.outSignal == level) return;
        this.outSignal = level;
        if (world != null && !world.isRemote) {
            world.notifyNeighborsOfStateChange(pos, world.getBlockState(pos).getBlock(), true);
        }
    }
}
