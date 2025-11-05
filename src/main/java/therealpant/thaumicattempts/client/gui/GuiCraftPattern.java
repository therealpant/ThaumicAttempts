package therealpant.thaumicattempts.client.gui;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.client.Minecraft;
import therealpant.thaumicattempts.golemcraft.container.ContainerCraftPattern;

public class GuiCraftPattern extends GuiContainer {

    private static final ResourceLocation TEX_PAPER_GILDED =
            new ResourceLocation("thaumcraft","textures/gui/papergilded.png");
    private static final ResourceLocation TEX_NET =
            new ResourceLocation("thaumcraft","textures/gui/gui_researchbook_overlay.png");
    private static final ResourceLocation TEX_BASE_TC =
            new ResourceLocation("thaumcraft","textures/gui/gui_base.png");

    private static final int INV_U = 0, INV_V = 166, INV_W = 176, INV_H = 90;

    private static final int PAPER_W = 160, PAPER_H = 160; // размер листа
    private static final int NET_GRID_U = 60, NET_GRID_V = 15, NET_GRID_W = 51, NET_GRID_H = 52;
    private static final float NET_GRID_SCALE = 1.20f;

    // тонкая подгонка наложения линий сетки поверх 3×3 (поднял выше)
    private static final float GRID_ADJ_X = -2.0f;
    private static final float GRID_ADJ_Y = -3.0f;

    private static final int CELL = 18;

    // 3×3 в контейнере: (x+62, y+17)
    private static final int GRID_LEFT_OFF = 62;
    private static final int GRID_TOP_OFF  = 17;

    // слоты игрока (рамка привяжется к этим координатам)
    private static final int PLAYER_LEFT_OFF = 8;
    private static final int PLAYER_TOP_OFF  = 130; // как в твоём последнем коде

    private boolean blurOn = false;

    public GuiCraftPattern(InventoryPlayer playerInv, ItemStack patternStack) {
        super(new ContainerCraftPattern(playerInv, patternStack));
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

        // ---- фактические координаты 3×3 из контейнера ----
        final int gridLeft = x + GRID_LEFT_OFF;
        final int gridTop  = y + GRID_TOP_OFF;

        // базовая область 3×3
        final int baseW = 3 * CELL, baseH = 3 * CELL;

        // центр 3×3 (нужен, чтобы центрировать ЛИСТ именно по сетке)
        final int gridCenterX = gridLeft + baseW / 2;
        final int gridCenterY = gridTop  + baseH / 2;

        // позиция листа: СЕРЕДИНА ЛИСТА == СЕРЕДИНА 3×3
        int paperX = gridCenterX - PAPER_W/ 2;
        int paperY = gridCenterY - PAPER_H/ 2;


        // рисуем лист
        mc.getTextureManager().bindTexture(TEX_PAPER_GILDED);
        drawModalRectWithCustomSizedTexture(paperX, paperY, 0, 0, PAPER_W, PAPER_H, PAPER_W,PAPER_H);

        // ---- оверлей сетки (увеличенный) — подогнан по линиям к предметам ----
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

        // ---- подложка под результат ----
        {
            // координаты "иконки результата" (мы их вычисляем в контейнере так же)
            int resultX = x + GRID_LEFT_OFF + baseW / 2 - 9;
            int gap = 8;
            int resultY = y + GRID_TOP_OFF - (28 + gap) + 1;

            // центр подложки = центр слота результата
            int decoSize = 30; // возьмём 32×32 из угла текстуры
            int decoU = 32, decoV = 0; // в papergilded.png кружочек в (0,0)
            int decoX = resultX - decoSize/2 + 8; // +8 чтобы центр совпал со слотом
            int decoY = resultY - decoSize/2 + 8;

            mc.getTextureManager().bindTexture(TEX_NET);
            drawModalRectWithCustomSizedTexture(decoX, decoY, decoU, decoV,
                    decoSize, decoSize, 440F, 440F);
        }


        // ---- рамка инвентаря игрока — строго под координаты слотов игрока ----
        final int playerLeft = x + PLAYER_LEFT_OFF;
        final int playerTop  = y + PLAYER_TOP_OFF;
        mc.getTextureManager().bindTexture(TEX_BASE_TC);
        drawTexturedModalRect(playerLeft - 8, playerTop - 8, INV_U, INV_V, INV_W, INV_H);
    }

    /* blur on/off */
    private void enableBlur() {
        try {
            if (Minecraft.getMinecraft().entityRenderer.getShaderGroup() == null) {
                Minecraft.getMinecraft().entityRenderer.loadShader(
                        new ResourceLocation("minecraft","shaders/post/blur.json"));
                blurOn = true;
            }
        } catch (Exception ignored) { blurOn = false; }
    }
    private void disableBlur() {
        try {
            ShaderGroup sg = Minecraft.getMinecraft().entityRenderer.getShaderGroup();
            if (sg != null && blurOn) {
                Minecraft.getMinecraft().entityRenderer.getShaderGroup().deleteShaderGroup();
            }
        } catch (Exception ignored) { }
        blurOn = false;
    }
}
