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
import therealpant.thaumicattempts.golemcraft.container.ContainerArcanePattern;

import javax.annotation.Nullable;
import java.io.IOException;

public class GuiArcanePattern extends GuiContainer {

    // текстуры и геометрия — как ты уже утвердил
    private static final ResourceLocation TEX_PAPER_GILDED =
            new ResourceLocation("thaumcraft","textures/gui/papergilded.png");
    private static final ResourceLocation TEX_NET =
            new ResourceLocation("thaumcraft","textures/gui/gui_researchbook_overlay.png");
    private static final ResourceLocation TEX_BASE_TC =
            new ResourceLocation("thaumcraft","textures/gui/gui_base.png");

    private static final int INV_U = 0, INV_V = 166, INV_W = 176, INV_H = 90;

    private static final int PAPER_W = 160, PAPER_H = 160;
    private static final int NET_GRID_U = 60, NET_GRID_V = 15, NET_GRID_W = 51, NET_GRID_H = 52;
    private static final float NET_GRID_SCALE = 1.20f;
    private static final float GRID_ADJ_X = -2.0f, GRID_ADJ_Y = -3.0f;

    private static final int CELL = 18;
    private static final int GRID_LEFT_OFF = 62;
    private static final int GRID_TOP_OFF  = 17;

    private static final int PLAYER_LEFT_OFF = 8;
    private static final int PLAYER_TOP_OFF  = 130;

    private boolean blurOn = false;

    public GuiArcanePattern(InventoryPlayer inv, ItemStack patternStack) {
        super(new ContainerArcanePattern(inv, patternStack));
        this.xSize = 176;
        this.ySize = 166;
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
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1F,1F,1F,1F);

        final int x = (width - xSize) / 2;
        final int y = (height - ySize) / 2;

        final int gridLeft = x + GRID_LEFT_OFF;
        final int gridTop  = y + GRID_TOP_OFF;

        final int baseW = 3 * CELL, baseH = 3 * CELL;

        final int gridCenterX = gridLeft + baseW / 2;
        final int gridCenterY = gridTop  + baseH / 2;

        int paperX = gridCenterX - PAPER_W/ 2;
        int paperY = gridCenterY - PAPER_H/ 2;

        // лист
        mc.getTextureManager().bindTexture(TEX_PAPER_GILDED);
        drawModalRectWithCustomSizedTexture(paperX, paperY, 0, 0, PAPER_W, PAPER_H, PAPER_W, PAPER_H);

        // сетка
        final int targetW = Math.round(baseW * NET_GRID_SCALE);
        final int targetH = Math.round(baseH * NET_GRID_SCALE);
        int gridDrawX = gridLeft - (targetW - baseW) / 2 + Math.round(GRID_ADJ_X);
        int gridDrawY = gridTop  - (targetH - baseH) / 2 + Math.round(GRID_ADJ_Y);

        GlStateManager.pushMatrix();
        GlStateManager.translate(gridDrawX, gridDrawY, 0);
        GlStateManager.scale(targetW / (float) NET_GRID_W, targetH / (float) NET_GRID_H, 1f);
        mc.getTextureManager().bindTexture(TEX_NET);
        drawTexturedModalRect(0, 0, NET_GRID_U, NET_GRID_V, NET_GRID_W, NET_GRID_H);
        GlStateManager.popMatrix();

        // подложка результата (как раньше)
        {
            int resultX = x + GRID_LEFT_OFF + baseW / 2 - 9;
            int gap = 8;
            int resultY = y + GRID_TOP_OFF - (28 + gap) + 1;

            int decoSize = 30, decoU = 32, decoV = 0;
            int decoX = resultX - decoSize/2 + 8;
            int decoY = resultY - decoSize/2 + 8;

            mc.getTextureManager().bindTexture(TEX_NET);
            drawModalRectWithCustomSizedTexture(decoX, decoY, decoU, decoV,
                    decoSize, decoSize, 440F, 440F);
        }

        // фон инвентаря игрока
        final int playerLeft = x + PLAYER_LEFT_OFF;
        final int playerTop  = y + PLAYER_TOP_OFF;
        mc.getTextureManager().bindTexture(TEX_BASE_TC);
        drawTexturedModalRect(playerLeft - 8, playerTop - 8, INV_U, INV_V, INV_W, INV_H);

        // === ФУНКЦИОНАЛЬНОЕ ПОЛЕ КРИСТАЛЛОВ ===
        drawCrystalField(paperX, paperY, gridTop, baseH);
    }

    /* ===== поле кристаллов: отрисовка ===== */
    private void drawCrystalField(int paperX, int paperY, int gridTop, int baseH) {
        // геометрия поля: по ширине — весь лист, по высоте — от низа 3×3 до низа листа
        int fieldLeft   = paperX;
        int fieldRight  = paperX + PAPER_W;
        int fieldTop    = gridTop + baseH + 4;
        int fieldBottom = paperY + PAPER_H - 6;
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
        final int x = (width - xSize) / 2;
        final int y = (height - ySize) / 2;

        final int CELL = 18;
        final int GRID_LEFT_OFF = 62;
        final int GRID_TOP_OFF  = 17;

        // фактические координаты сетки 3×3
        final int gridLeft = x + GRID_LEFT_OFF;
        final int gridTop  = y + GRID_TOP_OFF;
        final int baseW = 3 * CELL, baseH = 3 * CELL;

        // центр 3×3 — лист центрируем по нему (как в твоём GuiCraftPattern)
        final int gridCenterX = gridLeft + baseW / 2;
        final int gridCenterY = gridTop  + baseH / 2;

        // позиция листа
        final int paperX = gridCenterX - PAPER_W/2;
        final int paperY = gridCenterY - PAPER_H/2;

        // прямоугольник поля: от НИЗА сетки до НИЗА листа, на всю ширину листа
        final int fieldRelX = paperX - x;
        final int fieldRelY = (gridTop + baseH) - y;
        final int fieldW    = PAPER_W;
        final int fieldH    = (paperY + PAPER_H) - (gridTop + baseH);

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
