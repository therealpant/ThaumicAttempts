package therealpant.thaumicattempts.golemnet.tile;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.items.ItemTCEssentiaContainer;
import therealpant.thaumicattempts.api.*;
import therealpant.thaumicattempts.golemcraft.item.ItemArcanePattern;
import therealpant.thaumicattempts.golemcraft.item.ItemBasePattern;
import therealpant.thaumicattempts.golemcraft.tile.TileEntityGolemCrafter;
import therealpant.thaumicattempts.golemcraft.item.ItemInfusionPattern;
import therealpant.thaumicattempts.util.ItemKey;
import therealpant.thaumicattempts.util.ResourceIdentity;

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
 */
public class TilePatternRequester extends TileEntity implements ITickable, IAnimatable, ICraftEndpoint,
        CraftOrderApi.TagProvider, ITerminalOrderAcceptor, IAutomationOrderAcceptor, ITerminalOrderIconProvider {
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

    /** Доступ к паттернам крафтера под плитой (пустой обработчик, если крафтера нет). */
    public IItemHandler getPatternHandler() {
        TileEntityGolemCrafter crafter = getCrafterBelow();
        if (crafter != null) return crafter.getPatternHandler();
        return net.minecraftforge.items.wrapper.EmptyHandler.INSTANCE;
    }

    /* ====== Константы NBT, совпадающие с крафтером/паттерном ====== */
    private static final String TAG_RESULT = "Result";
    private static final String TAG_GRID   = "Grid";

    /* ====== Редстоун-выход (только декоративный/совместимость, на крафт не влияет) ====== */
    private int outSignal = 0;
    public int getOutSignal() { return Math.max(0, Math.min(15, outSignal)); }

    private int pulseTicks = 0;

    /* ====== Tick: гасим пульс, если он был ====== */
    @Override
    public void update() {
        if (world == null) return;
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
    @Override
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
    @Override
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
    @Override
    public void enqueueCraft(ItemStack resultLike, int crafts) {
        TileEntityGolemCrafter crafter = getCrafterBelow();
        if (crafter == null || resultLike == null || resultLike.isEmpty() || crafts <= 0) return;
        crafter.enqueueFromPatternRequester(resultLike, crafts);
    }

    @Override
    public List<ItemStack> listTerminalOrderIcons() {
        if (world == null) return Collections.emptyList();
        TileEntityGolemCrafter crafter = getCrafterBelow();
        if (crafter == null) return Collections.emptyList();

        IItemHandler patt = crafter.getPatternHandler();
        if (patt == null) return Collections.emptyList();

        ArrayList<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < patt.getSlots(); i++) {
            ItemStack pat = patt.getStackInSlot(i);
            if (pat.isEmpty() || !(pat.getItem() instanceof ItemBasePattern)) continue;

            ItemStack preview = calcPreview(pat, readGridFromPattern(pat));
            if (preview.isEmpty()) continue;
            ItemStack icon = TerminalOrderApi.makeOrderIcon(preview, world.getBlockState(pos).getBlock(), pos, i);
            if (!icon.isEmpty()) out.add(icon);
        }
        return out;
    }

    @Override
    public void triggerFromTerminal(int slot, int count) {
        submitAutomationOrder(slot, count, managerPos, null, -1);
    }

    @Override
    public int submitAutomationOrder(int slot, int items, @Nullable BlockPos managerPos, @Nullable BlockPos dest, int destSide) {
        if (world == null || world.isRemote || slot < 0 || items <= 0) return 0;
        TileEntityGolemCrafter crafter = getCrafterBelow();
        if (crafter == null) return 0;

        ItemStack resultLike = getResultLikeForPatternIndex(slot);
        if (resultLike.isEmpty()) return 0;

        int perCraft = Math.max(1, resultLike.getCount());
        int crafts = (items + perCraft - 1) / perCraft;
        if (crafts <= 0) return 0;

        if (managerPos != null) {
            TileEntity te = world.getTileEntity(managerPos);
            if (te instanceof TileMirrorManager) {
                List<ItemStack> needList = getRecipeInputsFor(resultLike, crafts);
                LinkedHashMap<ItemKey, Integer> needs = new LinkedHashMap<>();
                for (ItemStack need : needList) {
                    if (need == null || need.isEmpty()) continue;
                    ItemKey key = ItemKey.of(need);
                    if (key == null || key == ItemKey.EMPTY) continue;
                    needs.merge(key, Math.max(1, need.getCount()), Integer::sum);
                }
                if (!needs.isEmpty()) {
                    ((TileMirrorManager) te).ensureDeliveryForExact(pos.down(), needs, 0);
                }
            }
        }

        int acceptedCrafts = crafter.enqueueFromPatternRequester(slot, crafts);
        if (acceptedCrafts <= 0) return 0;
        return Math.max(1, acceptedCrafts * perCraft);
    }

    private ItemStack getResultLikeForPatternIndex(int idx) {
        TileEntityGolemCrafter crafter = getCrafterBelow();
        if (crafter == null) return ItemStack.EMPTY;
        IItemHandler patt = crafter.getPatternHandler();
        if (patt == null || idx < 0 || idx >= patt.getSlots()) return ItemStack.EMPTY;
        ItemStack pat = patt.getStackInSlot(idx);
        if (pat.isEmpty() || !(pat.getItem() instanceof ItemBasePattern)) return ItemStack.EMPTY;
        ItemStack preview = calcPreview(pat, readGridFromPattern(pat));
        if (preview.isEmpty()) return ItemStack.EMPTY;
        ItemStack one = preview.copy();
        one.setCount(1);
        return one;
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

        // --- НОВОЕ: отдельная ветка для infusion-паттерна ---
        if (pattern.getItem() instanceof ItemInfusionPattern) {
            // читаем «последовательность» и раскладываем в 3×3 по ORDER_TO_GRID
            NonNullList<ItemStack> order = ItemInfusionPattern.readOrder(pattern);
            if (!order.isEmpty()) {
                return ItemInfusionPattern.orderToGrid(order);
            }
            return grid;
        }

        // --- обычный путь для craft/arcane паттернов ---
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
        // --- НОВОЕ: инфузионный паттерн тоже имеет своё превью ---
        if (pattern.getItem() instanceof ItemInfusionPattern) {
            // он уже умеет читать TAG_RESULT правильно
            ItemStack inf = ItemInfusionPattern.readResult(pattern);
            if (inf != null && !inf.isEmpty()) return inf.copy();
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
        return ResourceIdentity.normalizeForKey(s);
    }

    /** Совпадение «как у крафтера» для входов. */
    private static boolean sameForGrid(ItemStack a, ItemStack b) {
        return ResourceIdentity.sameResource(a, b);
    }

    /** Совпадение результата (послабленное для стакаемых, строгое для нестакуемых/кристаллов). */
    private static boolean matchForRecipeRelaxed(ItemStack a, ItemStack b) {
        return ResourceIdentity.sameResource(a, b);
    }

    /* ====== NBT ====== */
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        if (managerPos != null) nbt.setLong("ManagerPos", managerPos.toLong());
        nbt.setInteger("OutSig", outSignal);
        nbt.setInteger("Pulse", pulseTicks);
        return nbt;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        managerPos = nbt.hasKey("ManagerPos", Constants.NBT.TAG_LONG)
                ? BlockPos.fromLong(nbt.getLong("ManagerPos")) : null;
        outSignal = nbt.getInteger("OutSig");
        pulseTicks = nbt.getInteger("Pulse");
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

    @Override
    public boolean hasActiveOrQueued() {
        TileEntityGolemCrafter crafter = getCrafterBelow();
        return crafter != null && crafter.hasRequesterQueue();
    }

    @Override
    public java.util.Set<String> getCraftOrderTags() {
        return CraftOrderApi.singletonTag(CraftOrderApi.TAG_CRAFTER);
    }
}
