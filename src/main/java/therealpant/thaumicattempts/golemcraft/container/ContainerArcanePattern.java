// src/main/java/therealpant/thaumicattempts/golemcraft/container/ContainerArcanePattern.java
package therealpant.thaumicattempts.golemcraft.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.items.ItemTCEssentiaContainer;
import therealpant.thaumicattempts.client.gui.PatternGuiLayout;
import therealpant.thaumicattempts.golemcraft.SlotGhost;
import therealpant.thaumicattempts.golemcraft.item.ItemBasePattern;
import therealpant.thaumicattempts.golemcraft.item.ItemArcanePattern;

import javax.annotation.Nullable;

public class ContainerArcanePattern extends Container {

    public static final int GRID_SIZE = 9;        // 0..8
    public static final int RESULT_IDX = 9;       // 9 — результат
    private static final int GHOST_INV_SIZE = 10; // 9 + 1

    private final InventoryPlayer playerInv;
    private final ItemStack patternStack;

    private final InventoryBasic ghostInv;

    // кристаллы — пока держим в памяти/nbt, слоты временно НЕ рисуем
    private int[] crystals = new int[6];

    // координаты как у обычного паттерна
    private static final int GRID_LEFT = PatternGuiLayout.GRID_LEFT;
    private static final int GRID_TOP  = PatternGuiLayout.GRID_TOP;
    private static final int CELL = PatternGuiLayout.CELL;

    public ContainerArcanePattern(InventoryPlayer inv, ItemStack pattern) {
        this.playerInv = inv;
        this.patternStack = pattern;

        this.ghostInv = new InventoryBasic("arcane_ghost", false, GHOST_INV_SIZE) {
            @Override public int getInventoryStackLimit() { return 1; }
        };

        // загрузить сетку/кристаллы из NBT
        ItemArcanePattern.readGridToInventory(pattern, ghostInv);
        this.crystals = ItemArcanePattern.getCrystalCounts(pattern);

        // --- Сетка 3×3 (как в ContainerCraftPattern с тонкой подгонкой OFF_X/OFF_Y)
        final int[] OFF_X = { 0, 0, 0 };
        final int[] OFF_Y = { 0, 0, 0 };

        for (int i = 0; i < GRID_SIZE; i++) {
            int row = i / 3, col = i % 3;
            int x = GRID_LEFT + col * CELL + OFF_X[col];
            int y = GRID_TOP  + row * CELL + OFF_Y[row];
            this.addSlotToContainer(new SlotGhost(ghostInv, i, x, y));
        }

        // --- Слот результата (коорд. идентичны обычному)
        int resultX = PatternGuiLayout.PREVIEW_LEFT;
        int resultY = PatternGuiLayout.PREVIEW_TOP;

        this.addSlotToContainer(new Slot(ghostInv, RESULT_IDX, resultX, resultY) {
            @Override public boolean isItemValid(ItemStack stack) { return false; }
            @Override public boolean canTakeStack(EntityPlayer playerIn) { return false; }
        });

        // --- Инвентарь игрока (x+8, y+130)
        addPlayerInventorySlots(playerInv, PatternGuiLayout.PLAYER_INV_LEFT, PatternGuiLayout.PLAYER_INV_TOP);
        // первичный пересчёт превью
        updateResult();
    }

    public ItemStack getPatternStack() {
        return patternStack;
    }

    private void addPlayerInventorySlots(InventoryPlayer inv, int left, int top) {
        // 3×9
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                this.addSlotToContainer(new Slot(inv, c + r * 9 + 9, left + c * 18, top + r * 18));
            }
        }
        // хотбар
        for (int c = 0; c < 9; c++) {
            this.addSlotToContainer(new Slot(inv, c, left + c * 18, top + 58));
        }
    }

    @Override public boolean canInteractWith(EntityPlayer playerIn) { return true; }



    @Override
    public ItemStack transferStackInSlot(EntityPlayer playerIn, int index) {
        // никаких шифткликов в призрачные слоты
        return ItemStack.EMPTY;
    }

    // --- Вставь в класс ContainerArcanePattern ---

    @Override
    public ItemStack slotClick(int slotId, int dragType, ClickType clickType, EntityPlayer player) {
        // 0..8 — призрачная сетка
        if (slotId >= 0 && slotId < 9) {
            // Shift-клик из инвентаря игрока -> автоукладка 1 шт в первый пустой призрачный слот
            if (clickType == ClickType.QUICK_MOVE) {
                Slot slot = (slotId < this.inventorySlots.size()) ? this.inventorySlots.get(slotId) : null;
                // если shift по самой «сетке», просто игнор
                return ItemStack.EMPTY;
            }

            if (clickType == ClickType.PICKUP) {
                // ПКМ — очистить ячейку
                if (dragType == 1) {
                    ghostInv.setInventorySlotContents(slotId, ItemStack.EMPTY);
                    updateResult();
                    detectAndSendChanges();
                    return ItemStack.EMPTY;
                }
                // ЛКМ — положить копию предмета из руки (count=1) или очистить, если рука пуста
                ItemStack carried = player.inventory.getItemStack();
                if (carried.isEmpty()) {
                    ghostInv.setInventorySlotContents(slotId, ItemStack.EMPTY);
                } else {
                    ItemStack one = carried.copy(); one.setCount(1);
                    ghostInv.setInventorySlotContents(slotId, one);
                }
                updateResult();
                detectAndSendChanges();
                return ItemStack.EMPTY;
            }
            return ItemStack.EMPTY;
        }

        // Shift-клик из ИНВЕНТАРЯ ИГРОКА -> автокладка в первый пустой слот призрачной сетки
        if (clickType == ClickType.QUICK_MOVE) {
            if (slotId >= 0 && slotId < this.inventorySlots.size()) {
                Slot src = this.inventorySlots.get(slotId);
                if (src != null && src.inventory == player.inventory) {
                    ItemStack from = src.getStack();
                    if (!from.isEmpty()) {
                        ItemStack one = from.copy(); one.setCount(1);
                        for (int i = 0; i < 9; i++) {
                            if (ghostInv.getStackInSlot(i).isEmpty()) {
                                ghostInv.setInventorySlotContents(i, one);
                                updateResult();
                                detectAndSendChanges();
                                break;
                            }
                        }
                    }
                }
            }
            return ItemStack.EMPTY;
        }

        // «кристальное поле»: используем слот результата как триггер
        if (slotId == RESULT_IDX && clickType == ClickType.PICKUP) {
            ItemStack carried = player.inventory.getItemStack();
            Aspect asp = aspectOfCrystal(carried);
            if (asp != null) {
                if (dragType == 1) {
                    clearCrystals();
                } else {
                    addCrystal(asp, +1);
                }
                return ItemStack.EMPTY;
            }

            if (dragType == 1 && player.isSneaking()) {
                clearCrystals();
                return ItemStack.EMPTY;
            }

            int delta = (dragType == 1) ? -1 : 1;
            ItemBasePattern.adjustRepeatCount(patternStack, delta);
            player.inventory.markDirty();
            detectAndSendChanges();
            return ItemStack.EMPTY;
        }

        return super.slotClick(slotId, dragType, clickType, player);
    }

    // Полный сброс кристаллов
    private void clearCrystals() {
        for (int i = 0; i < crystals.length; i++) crystals[i] = 0;
        updateResult();
        detectAndSendChanges();
    }

    // === обновлённый updateResult(): строгая валидация аркан-матча ===
    private void updateResult() {
        // 1) сохранить сетку + кристаллы в NBT паттерна
        NonNullList<ItemStack> grid = NonNullList.withSize(GRID_SIZE, ItemStack.EMPTY);
        for (int i = 0; i < GRID_SIZE; i++) {
            ItemStack s = ghostInv.getStackInSlot(i);
            grid.set(i, s.isEmpty() ? ItemStack.EMPTY : s.copy());
        }
        ItemArcanePattern.writeInventoryToStack(patternStack, grid, ItemStack.EMPTY);
        ItemArcanePattern.setCrystalCounts(patternStack, crystals);

        // 2) превью из твоей логики
        ItemStack out = ItemArcanePattern.calcArcaneResultPreview(patternStack, playerInv.player.world);

        // 3) строгая проверка соответствия аркан-рецептам (если не сходится — делаем превью пустым)
        if (!arcaneMatchesCurrent(grid, crystals)) {
            out = ItemStack.EMPTY;
        }
        ghostInv.setInventorySlotContents(RESULT_IDX, out == null ? ItemStack.EMPTY : out);
    }

    // Проверка: существует ли IArcaneRecipe, которая матчится при текущей сетке+кристаллах
    private boolean arcaneMatchesCurrent(NonNullList<ItemStack> grid, int[] cryst) {
        net.minecraft.world.World w = playerInv.player.world;
        ArcaneWorkbenchDummy inv = new ArcaneWorkbenchDummy();
        // 0..8 — сетка
        for (int i = 0; i < 9; i++) inv.setInventorySlotContents(i, grid.get(i).copy());
        // 9..14 — кристаллы (AER,TERRA,IGNIS,AQUA,ORDO,PERDITIO)
        Aspect[] P = ItemArcanePattern.PRIMALS;
        for (int i = 0; i < 6; i++) {
            int cnt = (cryst != null && i < cryst.length) ? Math.max(0, cryst[i]) : 0;
            ItemStack cs = cnt > 0 ? thaumcraft.api.ThaumcraftApiHelper.makeCrystal(P[i], cnt) : ItemStack.EMPTY;
            inv.setInventorySlotContents(9 + i, cs);
        }

        // 1) ForgeRegistries
        for (net.minecraft.item.crafting.IRecipe r : net.minecraftforge.fml.common.registry.ForgeRegistries.RECIPES.getValuesCollection()) {
            if (r instanceof thaumcraft.api.crafting.IArcaneRecipe && r.matches(inv, w)) return true;
        }
        // 2) CraftingManager (на всякий случай)
        for (net.minecraft.item.crafting.IRecipe r : net.minecraft.item.crafting.CraftingManager.REGISTRY) {
            if (r instanceof thaumcraft.api.crafting.IArcaneRecipe && r.matches(inv, w)) return true;
        }
        return false;
    }

    // Мини-дамми верстака 5x3 под IArcaneRecipe
    private static final class ArcaneWorkbenchDummy extends net.minecraft.inventory.InventoryCrafting
            implements thaumcraft.api.crafting.IArcaneWorkbench {
        ArcaneWorkbenchDummy() {
            super(new net.minecraft.inventory.Container() {
                @Override public boolean canInteractWith(EntityPlayer p){ return false; }
            }, 5, 3); // 5*3 = 15 слотов: 0..8 сетка, 9..14 кристаллы
        }
    }

    public static @Nullable Aspect aspectOfCrystal(ItemStack s) {
        if (s == null || s.isEmpty() || s.getItem() != ItemsTC.crystalEssence) return null;
        AspectList al = ((ItemTCEssentiaContainer) ItemsTC.crystalEssence).getAspects(s);
        return (al != null && al.size() == 1) ? al.getAspects()[0] : null;
    }

    @Override
    public void onContainerClosed(EntityPlayer playerIn) {
        super.onContainerClosed(playerIn);
        // финальная запись в NBT
        NonNullList<ItemStack> grid = NonNullList.withSize(GRID_SIZE, ItemStack.EMPTY);
        for (int i = 0; i < GRID_SIZE; i++) grid.set(i, ghostInv.getStackInSlot(i).copy());
        ItemArcanePattern.writeInventoryToStack(patternStack, grid, ItemStack.EMPTY);
        ItemArcanePattern.setCrystalCounts(patternStack, crystals);
    }

    public int[] getCrystalCountsView() {
        return crystals.clone();
    }

    private static int clamp(int v) { return v < 0 ? 0 : (v > 64 ? 64 : v); }

    private int indexOfAspect(Aspect a) {
        if (a == null) return -1;
        for (int i = 0; i < ItemArcanePattern.PRIMALS.length; i++) {
            if (ItemArcanePattern.PRIMALS[i] == a) return i;
        }
        return -1;
    }

    public void addCrystal(Aspect a, int delta) {
        int i = indexOfAspect(a);
        if (i < 0) return;
        crystals[i] = clamp(crystals[i] + delta);
        updateResult();
        detectAndSendChanges();
    }


}
