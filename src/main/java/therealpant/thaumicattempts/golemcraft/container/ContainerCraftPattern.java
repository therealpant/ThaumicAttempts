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
import therealpant.thaumicattempts.golemcraft.SlotGhost;
import therealpant.thaumicattempts.golemcraft.item.ItemBasePattern;
import therealpant.thaumicattempts.golemcraft.item.ItemCraftPattern;
import therealpant.thaumicattempts.golemcraft.item.ItemResourceList;
/**
 * Контейнер редактирования шаблона крафта:
 * - 3x3 "призрачная" сетка (индексы 0..8)
 * - 1 слот результата (индекс 9) — только для отображения, редактирование запрещено
 * - инвентарь игрока
 *
 * Запоминает сетку в NBT предмета при закрытии. Результат считается на лету.
 */
public class ContainerCraftPattern extends Container {

    public static final int GRID_SIZE = 9;   // 0..8
    public static final int RESULT_IDX = 9;  // 9-й слот (результат)
    private static final int GHOST_INV_SIZE = GRID_SIZE + 1; // 10

    private final InventoryPlayer playerInv;
    private final ItemStack patternStack;
    private final IInventory ghostInv;
    private final boolean resourceListMode;

    // координаты GUI (под свой фон можно подправить)
    private static final int GRID_LEFT = 62;
    private static final int GRID_TOP  = 17;
    private static final int CELL = 18;

    public ContainerCraftPattern(InventoryPlayer playerInv, ItemStack patternStack) {
        this.playerInv = playerInv;
        this.patternStack = patternStack;
        this.resourceListMode = !patternStack.isEmpty() && patternStack.getItem() instanceof ItemResourceList;

        // Внутренний «призрачный» инвентарь
        this.ghostInv = new InventoryBasic("pattern_ghost", false, GHOST_INV_SIZE) {
            @Override public int getInventoryStackLimit() { return resourceListMode ? 64 : 1; }
            @Override public boolean isItemValidForSlot(int index, ItemStack stack) {
                // сетка — любые предметы; слот результата мы отдельно запретим в самом Slot
                return true;
            }
        };

        // Загрузить сетку 3×3 из NBT предмета
        readPatternToInventory(patternStack);
        // Первичный расчёт результата
        updateResult();

        // --- Сетка 3×3 (индексы 0..8) ---
        final int[] OFF_X = { -4, 0, +3 };
        final int[] OFF_Y = { -6, -2, +1 };

        for (int i = 0; i < GRID_SIZE; i++) {
            int row = i / 3, col = i % 3;
            int x = GRID_LEFT + col * CELL + OFF_X[col];
            int y = GRID_TOP  + row * CELL + OFF_Y[row];
            this.addSlotToContainer(new SlotGhost(ghostInv, i, x, y));
        }

        // --- Слот результата (только просмотр) ---
        int baseW = 3 * CELL;
        int resultX = GRID_LEFT + baseW / 2 - 8; // -8 чтобы иконка 16×16 была по центру
        int gap = 8;                              // зазор над сеткой
        int resultY = GRID_TOP - (24 + gap);      // 16px высота иконки + зазор

        if (resourceListMode) {
            this.addSlotToContainer(new SlotGhost(ghostInv, RESULT_IDX, resultX, resultY));
        } else {
            this.addSlotToContainer(new Slot(ghostInv, RESULT_IDX, resultX, resultY) {
                @Override public boolean isItemValid(ItemStack stack) { return false; }
                @Override public boolean canTakeStack(EntityPlayer playerIn) { return false; }
            });
        }

        // --- Инвентарь игрока ---
        addPlayerInventorySlots(playerInv, 8, 130);
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

    @Override
    public boolean canInteractWith(EntityPlayer playerIn) {
        return true;
    }

    /** Пересчитать и обновить слот результата по текущей сетке. */
    private void updateResult() {
        // сформируем временный ItemStack шаблона с актуальной сеткой в NBT (чтобы calcResultPreview её увидел)
        if (resourceListMode) {
            return;
        }

        ItemStack out = ItemStack.EMPTY;
        if (!patternStack.isEmpty() && patternStack.getItem() instanceof ItemCraftPattern) {
            ItemStack tmp = patternStack.copy();
            NonNullList<ItemStack> grid = NonNullList.withSize(GRID_SIZE, ItemStack.EMPTY);
            for (int i = 0; i < GRID_SIZE; i++) {
                ItemStack s = ghostInv.getStackInSlot(i);
                grid.set(i, s.isEmpty() ? ItemStack.EMPTY : s.copy());
            }
            ItemCraftPattern.writeInventoryToStack(tmp, grid, ItemStack.EMPTY);
            out = ItemCraftPattern.calcResultPreview(tmp, playerInv.player.world);
        }

        ghostInv.setInventorySlotContents(RESULT_IDX, out == null ? ItemStack.EMPTY : out);
    }

    @Override
    public ItemStack slotClick(int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player) {
        // Наши призрачные слоты: 0..9
        if (slotId >= 0 && slotId < this.inventorySlots.size()) {
            Slot slot = this.inventorySlots.get(slotId);

            // Shift-клик из инвентаря игрока → автоукладка в первый свободный слот сетки (без перемещения предметов)
            if (clickTypeIn == ClickType.QUICK_MOVE) {
                if (slot != null && slot.inventory == player.inventory) {
                    ItemStack from = slot.getStack();
                    if (!from.isEmpty()) {
                        ItemStack copy = from.copy();
                        if (!resourceListMode) copy.setCount(1);
                        for (int i = 0; i < GRID_SIZE; i++) {
                            if (ghostInv.getStackInSlot(i).isEmpty()) {
                                ghostInv.setInventorySlotContents(i, copy);
                                updateResult();
                                detectAndSendChanges();
                                return ItemStack.EMPTY;
                            }
                        }
                    }
                }
                return ItemStack.EMPTY;
            }

            // Клики по «призрачным» ячейкам сетки
            if (slot instanceof SlotGhost) {
                int ghostIndex = slot.getSlotIndex();

                // ПКМ — очистить
                if (dragType == 1) {
                    ghostInv.setInventorySlotContents(ghostIndex, ItemStack.EMPTY);
                    updateResult();
                    detectAndSendChanges();
                    return ItemStack.EMPTY;
                }

                // ЛКМ — положить копию курсора (для шаблонов — count=1) или очистить, если рука пустая
                ItemStack held = player.inventory.getItemStack();
                if (!held.isEmpty()) {
                    ItemStack copy = held.copy();
                    if (!resourceListMode) copy.setCount(1);
                    ghostInv.setInventorySlotContents(ghostIndex, copy);
                } else {
                    ghostInv.setInventorySlotContents(ghostIndex, ItemStack.EMPTY);
                }
                updateResult();
                detectAndSendChanges();
                return ItemStack.EMPTY;
            }

            // Слот результата — используем для настройки повторов запуска
            if (!resourceListMode && slot != null && slot.getSlotIndex() == RESULT_IDX && slot.inventory == ghostInv) {
                if (clickTypeIn == ClickType.PICKUP) {
                    int delta = (dragType == 1) ? -1 : 1; // ЛКМ = +1, ПКМ = -1
                    ItemBasePattern.adjustRepeatCount(patternStack, delta);
                    player.inventory.markDirty();
                    detectAndSendChanges();
                }
                return ItemStack.EMPTY;
            }
        }

        // остальное по дефолту
        return super.slotClick(slotId, dragType, clickTypeIn, player);
    }

    @Override
    public void onContainerClosed(EntityPlayer playerIn) {
        super.onContainerClosed(playerIn);
        if (patternStack.isEmpty()) return;

        NonNullList<ItemStack> grid = NonNullList.withSize(GRID_SIZE, ItemStack.EMPTY);
        for (int i = 0; i < GRID_SIZE; i++) {
            grid.set(i, ghostInv.getStackInSlot(i).copy());
        }

        if (patternStack.getItem() instanceof ItemCraftPattern) {
            ItemCraftPattern.writeInventoryToStack(patternStack, grid, ItemStack.EMPTY);
        } else if (patternStack.getItem() instanceof ItemResourceList) {
            ItemStack preview = ghostInv.getStackInSlot(RESULT_IDX).copy();
            ItemResourceList.writeInventoryToStack(patternStack, grid, preview);
        }
    }

    private void readPatternToInventory(ItemStack patternStack) {
        if (patternStack.isEmpty()) {
            for (int i = 0; i < GRID_SIZE; i++) ghostInv.setInventorySlotContents(i, ItemStack.EMPTY);
            return;
        }

        if (patternStack.getItem() instanceof ItemCraftPattern) {
            ItemCraftPattern.readGridToInventory(patternStack, ghostInv);
        } else if (patternStack.getItem() instanceof ItemResourceList) {
            ItemResourceList.readGridToInventory(patternStack, ghostInv);
        } else if (patternStack.getItem() instanceof ItemBasePattern) {
            NonNullList<ItemStack> grid = ItemBasePattern.readGrid(patternStack);
            for (int i = 0; i < GRID_SIZE && i < grid.size(); i++) {
                ghostInv.setInventorySlotContents(i, grid.get(i));
            }
        } else {
            for (int i = 0; i < GRID_SIZE; i++) ghostInv.setInventorySlotContents(i, ItemStack.EMPTY);
        }
    }
}
