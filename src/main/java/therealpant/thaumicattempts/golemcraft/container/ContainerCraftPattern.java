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
import therealpant.thaumicattempts.golemcraft.item.ItemCraftPatternInfusion;
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

    private final InventoryPlayer playerInv;
    private final ItemStack patternStack;
    private final IInventory ghostInv;
    private final boolean resourceListMode;
    private final boolean infusionMode;
    private final int orderSize;
    private final int resultIndex;

    // координаты GUI (под свой фон можно подправить)
    private static final int GRID_LEFT = 62;
    private static final int GRID_TOP  = 17;
    private static final int CELL = 18;

    public ContainerCraftPattern(InventoryPlayer playerInv, ItemStack patternStack) {
        this.playerInv = playerInv;
        this.patternStack = patternStack;
        this.resourceListMode = !patternStack.isEmpty() && patternStack.getItem() instanceof ItemResourceList;
        this.infusionMode = !patternStack.isEmpty() && patternStack.getItem() instanceof ItemCraftPatternInfusion;
        this.orderSize = infusionMode ? ItemCraftPatternInfusion.MAX_ORDER : GRID_SIZE;
        this.resultIndex = orderSize;

        // Внутренний «призрачный» инвентарь
        this.ghostInv = new InventoryBasic("pattern_ghost", false, orderSize + 1) {
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
        // --- Сетка 3×3 (индексы 0..8) или инфузионный круг ---
        if (!infusionMode) {
            final int[] OFF_X = { -4, 0, +3 };
            final int[] OFF_Y = { -6, -2, +1 };

            for (int i = 0; i < GRID_SIZE; i++) {
                int row = i / 3, col = i % 3;
                int x = GRID_LEFT + col * CELL + OFF_X[col];
                int y = GRID_TOP  + row * CELL + OFF_Y[row];
                this.addSlotToContainer(new SlotGhost(ghostInv, i, x, y));
            }
        } else {
            // скрытые слоты, чтобы синхронизировать порядок между клиентом и сервером
            for (int i = 0; i < orderSize; i++) {
                this.addSlotToContainer(new SlotGhost(ghostInv, i, -10000, -10000));
            }
        }

        // --- Слот результата (только просмотр) ---
        int baseW = 3 * CELL;
        int resultX = GRID_LEFT + baseW / 2 - 8; // -8 чтобы иконка 16×16 была по центру
        int gap = 8;                              // зазор над сеткой
        int resultY = GRID_TOP - (24 + gap);      // 16px высота иконки + зазор

        if (resourceListMode) {
            this.addSlotToContainer(new SlotGhost(ghostInv, resultIndex, resultX, resultY));
        } else {
            this.addSlotToContainer(new Slot(ghostInv, resultIndex, resultX, resultY) {
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
        // для resourceList и infusion — ничего не пересчитываем
        if (resourceListMode || infusionMode) {
            return;
        }

        ItemStack out = ItemStack.EMPTY;
        if (!patternStack.isEmpty()) {
            ItemStack tmp = patternStack.copy();
            NonNullList<ItemStack> grid = NonNullList.withSize(GRID_SIZE, ItemStack.EMPTY);
            for (int i = 0; i < grid.size(); i++) {
                ItemStack s = ghostInv.getStackInSlot(i);
                grid.set(i, s.isEmpty() ? ItemStack.EMPTY : s.copy());
            }
            if (patternStack.getItem() instanceof ItemCraftPattern) {
                ItemCraftPattern.writeInventoryToStack(tmp, grid, ItemStack.EMPTY);
                out = ItemCraftPattern.calcResultPreview(tmp, playerInv.player.world);
            }
        }

        ghostInv.setInventorySlotContents(resultIndex, out == null ? ItemStack.EMPTY : out);
    }

    @Override
    public ItemStack slotClick(int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player) {
        // Наши призрачные слоты: 0..9
        if (slotId >= 0 && slotId < this.inventorySlots.size()) {
            Slot slot = this.inventorySlots.get(slotId);

            // Shift-клик из инвентаря игрока → автоукладка в первый свободный слот сетки (без перемещения предметов)
            if (!infusionMode && clickTypeIn == ClickType.QUICK_MOVE) {
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
            if (!infusionMode && slot instanceof SlotGhost) {
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

            // Слот результата
            if (!resourceListMode && slot != null && slot.getSlotIndex() == resultIndex && slot.inventory == ghostInv) {
                ItemStack held = player.inventory.getItemStack();

                // === НОВОЕ: инфузионный паттерн + предмет в руке -> записываем результат ===
                if (infusionMode && !held.isEmpty() && clickTypeIn == ClickType.PICKUP) {
                    // ghost-копия результата
                    ItemStack ghost = held.copy();
                    ghost.setCount(1);

                    // собираем текущий порядок/сетку из ghostInv
                    NonNullList<ItemStack> order = NonNullList.withSize(orderSize, ItemStack.EMPTY);
                    for (int i = 0; i < orderSize; i++) {
                        ItemStack s = ghostInv.getStackInSlot(i);
                        order.set(i, s.isEmpty() ? ItemStack.EMPTY : s.copy());
                    }

                    // пишем в сам предмет инфузионного паттерна (включая TAG_RESULT)
                    if (patternStack.getItem() instanceof ItemCraftPatternInfusion) {
                        ItemCraftPatternInfusion.writeInventoryToStack(patternStack, order, ghost);
                    }

                    // визуально кладём ghost в слот превью
                    ghostInv.setInventorySlotContents(resultIndex, ghost);

                    detectAndSendChanges();
                    return ItemStack.EMPTY;
                }

                // === СТАРОЕ ПОВЕДЕНИЕ: пустая рука -> управление repeat ===
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

        NonNullList<ItemStack> grid = NonNullList.withSize(orderSize, ItemStack.EMPTY);
        for (int i = 0; i < orderSize; i++) {
            grid.set(i, ghostInv.getStackInSlot(i).copy());
        }

        if (patternStack.getItem() instanceof ItemCraftPattern) {
            ItemCraftPattern.writeInventoryToStack(patternStack, grid, ItemStack.EMPTY);
        } else if (patternStack.getItem() instanceof ItemCraftPatternInfusion) {
            ItemStack preview = ghostInv.getStackInSlot(resultIndex).copy();
            ItemCraftPatternInfusion.writeInventoryToStack(patternStack, grid, preview);
        } else if (patternStack.getItem() instanceof ItemResourceList) {
            ItemStack preview = ghostInv.getStackInSlot(resultIndex).copy();
            ItemResourceList.writeInventoryToStack(patternStack, grid, preview);
        }
    }

    private void readPatternToInventory(ItemStack patternStack) {
        if (patternStack.isEmpty()) {
            for (int i = 0; i < orderSize; i++) ghostInv.setInventorySlotContents(i, ItemStack.EMPTY);
            return;
        }

        if (patternStack.getItem() instanceof ItemCraftPattern) {
            ItemCraftPattern.readGridToInventory(patternStack, ghostInv);
        } else if (patternStack.getItem() instanceof ItemCraftPatternInfusion) {
            ItemCraftPatternInfusion.readOrderToInventory(patternStack, ghostInv, orderSize);
            // подтянуть сохранённый результат
            ItemStack preview = ItemCraftPatternInfusion.readResult(patternStack);
            ghostInv.setInventorySlotContents(resultIndex, preview);
        } else if (patternStack.getItem() instanceof ItemResourceList) {
            ItemResourceList.readGridToInventory(patternStack, ghostInv);
        } else if (patternStack.getItem() instanceof ItemBasePattern) {
            NonNullList<ItemStack> grid = ItemBasePattern.readGrid(patternStack);
            for (int i = 0; i < orderSize  && i < grid.size(); i++) {
                ghostInv.setInventorySlotContents(i, grid.get(i));
            }
        } else {
            for (int i = 0; i < orderSize; i++) ghostInv.setInventorySlotContents(i, ItemStack.EMPTY);
        }
    }

    public boolean isInfusionMode() {
        return infusionMode;
    }

    /**
     * Кастомная команда от GUI: клики по листу вместо явных слотов. id=0 — добавить предмет из руки,
     * id=1 — удалить последний записанный предмет.
     */
    @Override
    public boolean enchantItem(EntityPlayer player, int id) {
        if (!infusionMode) return super.enchantItem(player, id);

        boolean changed = false;
        if (id == 0) {
            ItemStack held = player.inventory.getItemStack();
            if (!held.isEmpty()) {
                ItemStack copy = held.copy();
                copy.setCount(1);
                for (int i = 0; i < orderSize; i++) {
                    if (ghostInv.getStackInSlot(i).isEmpty()) {
                        ghostInv.setInventorySlotContents(i, copy);
                        changed = true;
                        break;
                    }
                }
            }
        } else if (id == 1) {
            for (int i = orderSize - 1; i >= 0; i--) {
                if (!ghostInv.getStackInSlot(i).isEmpty()) {
                    ghostInv.setInventorySlotContents(i, ItemStack.EMPTY);
                    changed = true;
                    break;
                }
            }
        }

        if (changed) {
            updateResult();
            detectAndSendChanges();
        }

        return changed;
    }

    /** Представление текущей последовательности для клиента. */
    public NonNullList<ItemStack> getOrderView() {
        NonNullList<ItemStack> view = NonNullList.withSize(orderSize, ItemStack.EMPTY);
        for (int i = 0; i < orderSize; i++) {
            ItemStack stack = ghostInv.getStackInSlot(i);
            view.set(i, stack == null ? ItemStack.EMPTY : stack.copy());
        }
        return view;
    }
}