// src/main/java/therealpant/thaumicattempts/client/gui/GuiArcanePattern.java
package therealpant.thaumicattempts.client.gui;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.MathHelper;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.items.ItemTCEssentiaContainer;
import thaumcraft.api.items.ItemsTC;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.golemcraft.container.ContainerArcanePattern;
import therealpant.thaumicattempts.golemcraft.item.ItemBasePattern;

import javax.annotation.Nullable;
import java.io.IOException;

public class GuiArcanePattern extends GuiContainer {

    private static final ResourceLocation TEX_CRAFTER =
            new ResourceLocation(ThaumicAttempts.MODID, "textures/gui/golem_crafter.png");
    private static final ResourceLocation TEX_ORDER_TERMINAL =
            new ResourceLocation(ThaumicAttempts.MODID, "textures/gui/order_terminal.png");

    private static final int PAPER_U = 60;
    private static final int PAPER_V = 0;

    private boolean blurOn = false;

    public GuiArcanePattern(InventoryPlayer inv, ItemStack patternStack) {
        super(new ContainerArcanePattern(inv, patternStack));
        this.xSize = PatternGuiLayout.GUI_WIDTH;
        this.ySize = PatternGuiLayout.GUI_HEIGHT;
    }

    @Override public void initGui() { super.initGui(); enableBlur(); }
    @Override public void onGuiClosed() { super.onGuiClosed(); disableBlur(); }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        this.renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        super.drawGuiContainerForegroundLayer(mouseX, mouseY);

        ContainerArcanePattern c = (ContainerArcanePattern) this.inventorySlots;
        ItemStack pat = c.getPatternStack();
        int repeat = ItemBasePattern.getRepeatCount(pat);
        if (repeat > 1) {
            final int resultX = PatternGuiLayout.PREVIEW_LEFT;
            final int resultY = PatternGuiLayout.PREVIEW_TOP;

            String text = String.valueOf(repeat);
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            this.fontRenderer.drawStringWithShadow(text,
                    resultX + 16 - 1 - this.fontRenderer.getStringWidth(text),
                    resultY + 8,
                    0xFFFFFF);
            GlStateManager.enableLighting();
            GlStateManager.enableDepth();
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1F,1F,1F,1F);

        final int x = this.guiLeft;
        final int y = this.guiTop;

        final int gridLeft = x + PatternGuiLayout.GRID_LEFT;
        final int gridTop  = y + PatternGuiLayout.GRID_TOP;

        final int baseW = 3 * PatternGuiLayout.CELL;
        final int baseH = baseW;

        final int paperX = x + PatternGuiLayout.PAPER_LEFT;
        final int paperY = y + PatternGuiLayout.PAPER_TOP;

        final int backgroundX = x + PatternGuiLayout.BACKGROUND_LEFT;
        final int backgroundY = y + PatternGuiLayout.BACKGROUND_TOP;
        mc.getTextureManager().bindTexture(TEX_CRAFTER);
        drawModalRectWithCustomSizedTexture(backgroundX, backgroundY,
                PatternGuiLayout.BACKGROUND_U, PatternGuiLayout.BACKGROUND_V,
                PatternGuiLayout.BACKGROUND_W, PatternGuiLayout.BACKGROUND_H,
                354, 256);

        drawPaper(paperX, paperY);

        final int invBgX = x + PatternGuiLayout.PLAYER_INV_BG_LEFT;
        final int invBgY = y + PatternGuiLayout.PLAYER_INV_BG_TOP;
        mc.getTextureManager().bindTexture(TEX_ORDER_TERMINAL);
        drawModalRectWithCustomSizedTexture(invBgX, invBgY,
                PatternGuiLayout.PLAYER_INV_U, PatternGuiLayout.PLAYER_INV_V,
                PatternGuiLayout.PLAYER_INV_W, PatternGuiLayout.PLAYER_INV_H,
                354, 256);

        // === ФУНКЦИОНАЛЬНОЕ ПОЛЕ КРИСТАЛЛОВ ===
        drawCrystalField(paperX, paperY, gridTop, baseH);
    }

    private void drawPaper(int paperX, int paperY) {
        mc.getTextureManager().bindTexture(TEX_CRAFTER);
        drawModalRectWithCustomSizedTexture(paperX, paperY, PAPER_U, PAPER_V,
                PatternGuiLayout.PAPER_DRAW_W, PatternGuiLayout.PAPER_DRAW_H,
                354, 256);
    }

    /* ===== поле кристаллов: отрисовка ===== */
    private void drawCrystalField(int paperX, int paperY, int gridTop, int baseH) {
        // геометрия поля: по ширине — весь лист, по высоте — от низа 3×3 до низа листа
        int fieldLeft   = paperX;
        int fieldRight  = paperX + PatternGuiLayout.PAPER_DRAW_W;
        int fieldTop    = gridTop + baseH + 4;
        int fieldBottom = paperY + PatternGuiLayout.PAPER_DRAW_H - 6;
        int fieldW = Math.max(0, fieldRight - fieldLeft);
        int fieldH = Math.max(0, fieldBottom - fieldTop);
        if (fieldW <= 0 || fieldH <= 6) return;

        // соберём, что рисовать
        ContainerArcanePattern c = (ContainerArcanePattern) this.inventorySlots;
        int[] counts = c.getCrystalCountsView();

        Aspect[] A = therealpant.thaumicattempts.golemcraft.item.ItemArcanePattern.PRIMALS;
        java.util.List<Aspect> toDraw = new java.util.ArrayList<>(6);
        for (int i = 0; i < A.length; i++) if (counts[i] > 0) toDraw.add(A[i]);

        if (toDraw.isEmpty()) return;

        // иконки по центру по горизонтали
        int slot = 18; // шаг
        int totalW = toDraw.size() * slot;
        int startX = fieldLeft + (fieldW - totalW) / 2;
        int y = fieldTop + (fieldH - 16) / 2;

        for (int i = 0; i < toDraw.size(); i++) {
            Aspect asp = toDraw.get(i);
            ItemStack icon = ThaumcraftApiHelper.makeCrystal(asp, 1);
            int cx = startX + i * slot;

            // предметная иконка
            this.itemRender.renderItemAndEffectIntoGUI(icon, cx, y);

            // цифра поверх (как ванильный оверлей)
            int idx = indexOfAspect(asp);
            if (idx >= 0 && counts[idx] > 1) {
                String text = String.valueOf(MathHelper.clamp(counts[idx], 0, 99));
                this.itemRender.renderItemOverlayIntoGUI(this.fontRenderer, icon, cx, y, text);
            }
        }
    }

    private int indexOfAspect(Aspect a) {
        Aspect[] P = therealpant.thaumicattempts.golemcraft.item.ItemArcanePattern.PRIMALS;
        for (int i = 0; i < P.length; i++) if (P[i] == a) return i;
        return -1;
    }

    /* ===== клик по полю: инкремент/декремент ===== */
    // Добавь в GuiArcanePattern:

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws java.io.IOException {
        // === вычисляем прямоугольник «поля кристаллов» (нижняя часть листа) ===
        final int x = this.guiLeft;
        final int y = this.guiTop;

        // фактические координаты сетки 3×3
        final int gridLeft = x + PatternGuiLayout.GRID_LEFT;
        final int gridTop  = y + PatternGuiLayout.GRID_TOP;
        final int baseW = 3 * PatternGuiLayout.CELL, baseH = baseW;

        // позиция листа
        final int paperX = x + PatternGuiLayout.PAPER_LEFT;
        final int paperY = y + PatternGuiLayout.PAPER_TOP;

        // прямоугольник поля: от НИЗА сетки до НИЗА листа, на всю ширину листа
        final int fieldRelX = paperX - x;
        final int fieldRelY = (gridTop + baseH) - y;
        final int fieldW    = PatternGuiLayout.PAPER_DRAW_W;
        final int fieldH    = (paperY + PatternGuiLayout.PAPER_DRAW_H) - (gridTop + baseH);

        // если кликнули внутри поля — эмулируем клик по слоту RESULT_IDX
        if (fieldH > 0 && isPointInRegion(fieldRelX, fieldRelY, fieldW, fieldH, mouseX, mouseY)) {
            net.minecraft.inventory.Slot slot = this.inventorySlots.getSlot(therealpant.thaumicattempts.golemcraft.container.ContainerArcanePattern.RESULT_IDX);
            // ClickType.PICKUP + mouseButton: 0 = ЛКМ (+1 кристалл), 1 = ПКМ (сброс)
            this.handleMouseClick(slot,
                    therealpant.thaumicattempts.golemcraft.container.ContainerArcanePattern.RESULT_IDX,
                    mouseButton,
                    net.minecraft.inventory.ClickType.PICKUP);
            return; // не даём базовой логике перехватывать этот клик
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }


    /* ==== утиль для TC-кристаллов (клиент) ==== */
    private static boolean isCrystal(ItemStack s) {
        return s != null && !s.isEmpty() && s.getItem() == ItemsTC.crystalEssence;
    }
    @Nullable
    private static Aspect aspectOfCrystal(ItemStack s) {
        if (!isCrystal(s)) return null;
        AspectList al = ((ItemTCEssentiaContainer) ItemsTC.crystalEssence).getAspects(s);
        return (al != null && al.size() == 1) ? al.getAspects()[0] : null;
    }

    /* blur on/off — без изменений */
    private void enableBlur() { /* как было */ }
    private void disableBlur() { /* как было */ }
}
