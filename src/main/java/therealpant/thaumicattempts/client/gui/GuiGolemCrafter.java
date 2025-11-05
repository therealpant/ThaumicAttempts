package therealpant.thaumicattempts.client.gui;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.items.IItemHandler;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.golemcraft.container.ContainerGolemCrafter;
import therealpant.thaumicattempts.golemcraft.item.ItemArcanePattern;
import therealpant.thaumicattempts.golemcraft.item.ItemCraftPattern;
import therealpant.thaumicattempts.golemcraft.tile.TileEntityGolemCrafter;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class GuiGolemCrafter extends GuiContainer {

    /* Единый атлас GUI */
    private static final ResourceLocation TEX_ATLAS =
            new ResourceLocation(ThaumicAttempts.MODID, "textures/gui/gui_crafter3_5.png");
    private static final ResourceLocation TEX_BASE_TC =
            new ResourceLocation("thaumcraft","textures/gui/gui_base.png");

    /* Атласные регионы */
    private static final int ATLAS_W = 354, ATLAS_H = 256;

    private static final int GRID_U = 0, GRID_V = 0, GRID_SIZE = 60, GRID_BORDER = 4;
    private static final int PREV_U = 60, PREV_V = 16, PREV_SIZE = 24;
    private static final int HIL_U  = 60, HIL_V  = 0,  HIL_SIZE  = 16; // подсветка 16×16
    private static final int PANEL_U = 0, PANEL_V = 119, PANEL_W = 60, PANEL_H = 96, PANEL_BORDER = 4;

    /* Сетка 3×3: ячейка 16×16, внешние зазоры между слотами 2 px */
    private static final int SLOT_SIZE = 16;
    private static final int SLOT_GAP  = 2;   // внешний между слотами
    private static final int STEP = SLOT_SIZE + SLOT_GAP; // 18

    /* Инвентарь игрока */
    private static final int INV_U = 0, INV_V = 166, INV_W = 176, INV_H = 90;

    private final TileEntityGolemCrafter te;
    private IItemHandler patterns;

    // цикл предпросмотра
    private int tickCounter = 0;
    private int lastSwitchTick = 0;
    private int currentSlot = -1;
    private final int switchPeriod = 30; // 1.5 сек @20 TPS

    public GuiGolemCrafter(InventoryPlayer playerInv, TileEntityGolemCrafter te) {
        super(new ContainerGolemCrafter(playerInv, te));
        this.te = te;

        this.xSize = 370; // фиксирована под разметку
        this.ySize = 166;

        this.patterns = tryGetPatternHandlerReflective(te);
    }

    private static IItemHandler tryGetPatternHandlerReflective(TileEntityGolemCrafter te) {
        try {
            Method m = te.getClass().getMethod("getPatternHandler");
            Object res = m.invoke(te);
            if (res instanceof IItemHandler) return (IItemHandler) res;
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        tickCounter++;

        boolean powered = te.getWorld() != null && te.getWorld().isBlockPowered(te.getPos());
        Integer activeIdx = powered ? tryGetActiveIndex(te) : null;

        if (powered) {
            if (activeIdx != null) currentSlot = activeIdx; // зафиксироваться на активе
            return; // пауза автоперебора при редстоуне
        }

        if (tickCounter - lastSwitchTick >= switchPeriod) {
            lastSwitchTick = tickCounter;
            nextNonEmptyPattern();
        }
    }

    private static Integer tryGetActiveIndex(TileEntityGolemCrafter te) {
        try {
            Method m = te.getClass().getMethod("getActivePatternIndex");
            Object res = m.invoke(te);
            if (res instanceof Integer) return (Integer) res;
        } catch (Exception ignored) {}
        return null;
    }

    private void nextNonEmptyPattern() {
        if (patterns == null) { currentSlot = -1; return; }
        int slots = Math.min(15, patterns.getSlots());
        if (slots <= 0) { currentSlot = -1; return; }

        int start = (currentSlot + 1 + slots) % slots;
        int i = start;
        do {
            ItemStack st = patterns.getStackInSlot(i);
            if (!st.isEmpty()) { currentSlot = i; return; }
            i = (i + 1) % slots;
        } while (i != start);

        currentSlot = -1;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        this.renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1F,1F,1F,1F);

        final int x = (width - xSize) / 2;
        final int y = (height - ySize) / 2;

        // Центр GUI
        final int centerX = x + xSize / 2;

        // --- Блок: [панель 60] + 8 + [сетка 60] + 8 + [превью 24]
        final int GAP = 8;
        final int BLOCK_W = PANEL_W + GAP + GRID_SIZE + GAP + PREV_SIZE; // 160
        final int blockLeft = centerX - BLOCK_W / 2;

        // Панель 3×5 (наружные координаты)
        final int panelOuterX = blockLeft;
        final int panelOuterY = y + (ContainerGolemCrafter.PANEL_TOP - PANEL_BORDER); // выровнено по верху превью/сетки

        // Сетка 3×3 (наружные координаты)
        final int gridBoxLeft = panelOuterX + PANEL_W + GAP;
        final int gridBoxTop  = y + 17;

        // Превью 24×24 (наружные координаты)
        final int prevBoxLeft = gridBoxLeft + GRID_SIZE + GAP;
        final int prevBoxTop  = gridBoxTop;

        // ===== Рисуем панели/фон =====
        mc.getTextureManager().bindTexture(TEX_ATLAS);
        // Сетка 60×60
        drawModalRectWithCustomSizedTexture(gridBoxLeft, gridBoxTop, GRID_U, GRID_V, GRID_SIZE, GRID_SIZE, ATLAS_W, ATLAS_H);
        // Превью 24×24
        drawModalRectWithCustomSizedTexture(prevBoxLeft,  prevBoxTop,  PREV_U, PREV_V, PREV_SIZE, PREV_SIZE, ATLAS_W, ATLAS_H);
        // Панель 3×5 60×96
        drawModalRectWithCustomSizedTexture(panelOuterX, panelOuterY, PANEL_U, PANEL_V, PANEL_W, PANEL_H, ATLAS_W, ATLAS_H);

        // ===== Иконка результата в квадратике превью (центр 24×24 → смещение 4,4) =====
        ItemStack preview = getResultPreviewForCurrent();
        if (preview != null && !preview.isEmpty()) {
            RenderHelper.enableGUIStandardItemLighting();
            int itemX = prevBoxLeft + (PREV_SIZE - SLOT_SIZE) / 2; // 4
            int itemY = prevBoxTop  + (PREV_SIZE - SLOT_SIZE) / 2; // 4
            itemRender.renderItemAndEffectIntoGUI(preview, itemX, itemY);
            itemRender.renderItemOverlayIntoGUI(this.fontRenderer, preview, itemX, itemY, null);
            RenderHelper.disableStandardItemLighting();
        }

        // ===== Инвентарь игрока (центр по X) =====
        int playerLeft = centerX - (9 * 18) / 2;
        int playerTop  = y + ContainerGolemCrafter.PLAYER_TOP;
        mc.getTextureManager().bindTexture(TEX_BASE_TC);
        drawTexturedModalRect(playerLeft - 8, playerTop - 8, INV_U, INV_V, INV_W, INV_H);

        // ===== Отрисовка рецепта 3×3 поверх сетки с корректной геометрией =====
        renderRecipeGrid(gridBoxLeft, gridBoxTop);

        // ===== Подсветка активного паттерна на панели (поднята на 1px) =====
        highlightCurrentPatternSlot(x, y);
    }

    private void highlightCurrentPatternSlot(int guiLeft, int guiTop) {
        if (currentSlot < 0) return;

        // базовая внутренняя точка начала слотов панели
        final int baseX = guiLeft + ContainerGolemCrafter.PANEL_LEFT;
        final int baseY = guiTop  + ContainerGolemCrafter.PANEL_TOP;

        int col = currentSlot % ContainerGolemCrafter.PANEL_COLS; // 0..2
        int row = currentSlot / ContainerGolemCrafter.PANEL_COLS; // 0..4

        int sx = baseX + col * STEP;
        int sy = baseY + row * STEP; // поднять бегунок на 1 px

        mc.getTextureManager().bindTexture(TEX_ATLAS);
        GlStateManager.enableBlend();
        drawModalRectWithCustomSizedTexture(sx, sy, HIL_U, HIL_V, HIL_SIZE, HIL_SIZE, ATLAS_W, ATLAS_H);
        GlStateManager.disableBlend();
    }

    private void renderRecipeGrid(int gridBoxLeft, int gridBoxTop) {
        List<ItemStack> grid = getPreviewForCurrent();
        if (grid == null) grid = java.util.Collections.emptyList();

        // внутренняя область сетки (после рамки 4 px)
        final int innerLeft = gridBoxLeft + GRID_BORDER;
        final int innerTop  = gridBoxTop  + GRID_BORDER;

        RenderHelper.enableGUIStandardItemLighting();
        for (int i = 0; i < Math.min(9, grid.size()); i++) {
            ItemStack st = grid.get(i);
            if (st == null || st.isEmpty()) continue;

            int col = i % 3;
            int row = i / 3;

            int cx = innerLeft + col * STEP;
            int cy = innerTop  + row * STEP;

            itemRender.renderItemAndEffectIntoGUI(st, cx, cy);
            itemRender.renderItemOverlayIntoGUI(this.fontRenderer, st, cx, cy, null);
        }
        RenderHelper.disableStandardItemLighting();
    }

    /* ---------- Источники превью (тот же порядок, что и раньше) ---------- */

    private List<ItemStack> getPreviewForCurrent() {
        if (patterns == null || currentSlot < 0) return java.util.Collections.emptyList();

        ItemStack pat = patterns.getStackInSlot(currentSlot);
        if (pat.isEmpty()) return java.util.Collections.emptyList();

        @SuppressWarnings("unchecked")
        List<ItemStack> viaTE = tryGetPreviewFromTE(currentSlot);
        if (viaTE != null && !viaTE.isEmpty()) return padTo9(viaTE);

        IItemHandler h = pat.getCapability(net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (h != null) {
            List<ItemStack> out = new ArrayList<>(9);
            for (int i = 0; i < 9; i++) out.add(i < h.getSlots() ? h.getStackInSlot(i) : ItemStack.EMPTY);
            return out;
        }

        net.minecraft.nbt.NBTTagCompound tag = pat.getTagCompound();
        if (tag != null && tag.hasKey("Grid", net.minecraftforge.common.util.Constants.NBT.TAG_LIST)) {
            net.minecraft.nbt.NBTTagList lst = tag.getTagList("Grid", net.minecraftforge.common.util.Constants.NBT.TAG_COMPOUND);
            List<ItemStack> out = new ArrayList<>(9);
            for (int i = 0; i < 9; i++) {
                ItemStack st = ItemStack.EMPTY;
                if (i < lst.tagCount()) {
                    net.minecraft.nbt.NBTTagCompound c = lst.getCompoundTagAt(i);
                    if (!c.getKeySet().isEmpty()) st = new ItemStack(c);
                }
                out.add(st);
            }
            return out;
        }

        return java.util.Collections.emptyList();
    }

    private ItemStack getResultPreviewForCurrent() {
        if (patterns == null || currentSlot < 0) return ItemStack.EMPTY;
        ItemStack pat = patterns.getStackInSlot(currentSlot);
        if (pat.isEmpty()) return ItemStack.EMPTY;

        if (pat.getItem() instanceof ItemArcanePattern) {
            ItemStack r = ItemArcanePattern.calcArcaneResultPreview(pat, mc.world);
            if (r != null && !r.isEmpty()) return r;
        }
        if (pat.getItem() instanceof ItemCraftPattern) {
            ItemStack r = ItemCraftPattern.calcResultPreview(pat, mc.world);
            if (r != null && !r.isEmpty()) return r;
        }

        net.minecraft.nbt.NBTTagCompound t = pat.getTagCompound();
        if (t != null) {
            if (t.hasKey("Result", net.minecraftforge.common.util.Constants.NBT.TAG_COMPOUND)) {
                ItemStack r = new ItemStack(t.getCompoundTag("Result"));
                if (!r.isEmpty()) return r;
            }
            if (t.hasKey(ItemArcanePattern.TAG_RESULT, net.minecraftforge.common.util.Constants.NBT.TAG_COMPOUND)) {
                ItemStack r = new ItemStack(t.getCompoundTag(ItemArcanePattern.TAG_RESULT));
                if (!r.isEmpty()) return r;
            }
        }

        List<ItemStack> grid = getPreviewForCurrent();
        if (grid != null && !grid.isEmpty()) {
            InventoryCrafting inv = new InventoryCrafting(new Container() {
                @Override public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer p) { return false; }
            }, 3, 3);
            for (int i = 0; i < 9; i++) inv.setInventorySlotContents(i, i < grid.size() ? grid.get(i).copy() : ItemStack.EMPTY);
            ItemStack r = CraftingManager.findMatchingResult(inv, mc.world);
            if (r != null) return r;
        }
        return ItemStack.EMPTY;
    }

    @SuppressWarnings("unchecked")
    private List<ItemStack> tryGetPreviewFromTE(int slot) {
        try {
            Method m = te.getClass().getMethod("getPatternPreview", int.class);
            Object res = m.invoke(te, slot);
            if (res instanceof List) return (List<ItemStack>) res;
        } catch (Exception ignored) {}
        return null;
    }

    private static List<ItemStack> padTo9(List<ItemStack> in) {
        List<ItemStack> out = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) out.add(i < in.size() ? (in.get(i) == null ? ItemStack.EMPTY : in.get(i)) : ItemStack.EMPTY);
        return out;
    }

    @Override
    public void initGui() {
        super.initGui();

        // Пауза перебора при редстоуне и показ активного паттерна
        boolean powered = te.getWorld() != null && te.getWorld().isBlockPowered(te.getPos());
        if (powered) {
            Integer activeIdx = tryGetActiveIndex(te);
            if (activeIdx != null) currentSlot = activeIdx;
        }

        // Если редстоун не зафиксировал слот — показываем ПЕРВЫЙ НЕПУСТОЙ сразу
        if (currentSlot < 0) nextNonEmptyPattern();

        // Чтобы автоперебор не щёлкал сразу после открытия
        lastSwitchTick = tickCounter;
    }

}
