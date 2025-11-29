package therealpant.thaumicattempts.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import therealpant.thaumicattempts.golemcraft.container.ContainerDeliveryPattern;
import therealpant.thaumicattempts.golemcraft.item.ItemBasePattern;

public class GuiDeliveryPattern extends GuiContainer {

    private static final ResourceLocation TEX_PAPER_GILDED =
            new ResourceLocation("thaumcraft","textures/gui/papergilded.png");
    private static final ResourceLocation TEX_NET =
            new ResourceLocation("thaumcraft","textures/gui/gui_researchbook_overlay.png");
    private static final ResourceLocation TEX_BASE_TC =
            new ResourceLocation("thaumcraft","textures/gui/gui_base.png");

    private static final int INV_U = 0, INV_V = 166, INV_W = 176, INV_H = 90;
    private static final int PAPER_W = 160, PAPER_H = 160;

    private static final int NET_U = 60, NET_V = 15, NET_W = 51, NET_H = 52;
    private static final float NET_SCALE = 1.65f;

    private static final int CENTER_X = 88;
    private static final int CENTER_Y = 52;

    private boolean blurOn = false;

    public GuiDeliveryPattern(InventoryPlayer playerInv, ItemStack patternStack) {
        super(new ContainerDeliveryPattern(playerInv, patternStack));
        this.xSize = 176;
        this.ySize = 200;
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

        ContainerDeliveryPattern c = (ContainerDeliveryPattern) this.inventorySlots;
        ItemStack pat = c.getPatternStack();
        int repeat = ItemBasePattern.getRepeatCount(pat);
        if (repeat > 1) {
            String text = String.valueOf(repeat);
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            this.fontRenderer.drawStringWithShadow(text,
                    CENTER_X - this.fontRenderer.getStringWidth(text) / 2f,
                    18,
                    0xFFFFFF);
            GlStateManager.enableLighting();
            GlStateManager.enableDepth();
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1F,1F,1F,1F);

        final int x = (width - xSize) / 2;
        final int y = (height - ySize) / 2;

        int centerX = x + CENTER_X;
        int centerY = y + CENTER_Y;

        int paperX = centerX - PAPER_W / 2;
        int paperY = centerY - PAPER_H / 2;

        mc.getTextureManager().bindTexture(TEX_PAPER_GILDED);
        drawModalRectWithCustomSizedTexture(paperX, paperY, 0, 0, PAPER_W, PAPER_H, PAPER_W,PAPER_H);

        // оверлей матрицы наполнения (растянутый крест из книги)
        final int targetW = Math.round(NET_W * NET_SCALE);
        final int targetH = Math.round(NET_H * NET_SCALE);
        int netX = centerX - targetW / 2;
        int netY = centerY - targetH / 2;

        GlStateManager.pushMatrix();
        GlStateManager.translate(netX, netY, 0);
        GlStateManager.scale(targetW / (float) NET_W, targetH / (float) NET_H, 1f);
        mc.getTextureManager().bindTexture(TEX_NET);
        drawTexturedModalRect(0, 0, NET_U, NET_V, NET_W, NET_H);
        GlStateManager.popMatrix();

        // подложка под превью результата
        ContainerDeliveryPattern c = (ContainerDeliveryPattern) this.inventorySlots;
        int resultSlot = c.getResultSlotIndex();
        if (resultSlot >= 0 && resultSlot < c.inventorySlots.size()) {
            int sx = x + c.inventorySlots.get(resultSlot).xPos;
            int sy = y + c.inventorySlots.get(resultSlot).yPos;

            mc.getTextureManager().bindTexture(TEX_NET);
            drawModalRectWithCustomSizedTexture(sx - 8, sy - 8, 32, 0, 30, 30, 440F, 440F);
        }

        // рамка инвентаря игрока
        final int playerLeft = x + 8;
        final int playerTop  = y + 130;
        mc.getTextureManager().bindTexture(TEX_BASE_TC);
        drawTexturedModalRect(playerLeft - 8, playerTop - 8, INV_U, INV_V, INV_W, INV_H);

        // пронумеруем видимые слоты (только предметы паттерна)
        int visible = c.getVisibleSlots();
        for (int i = 0; i < visible; i++) {
            if (i >= c.getPatternSlotCount()) break;
            if (i >= c.inventorySlots.size()) break;
            int sx = x + c.inventorySlots.get(i).xPos;
            int sy = y + c.inventorySlots.get(i).yPos;
            String num = String.valueOf(i + 1);
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            this.fontRenderer.drawStringWithShadow(num, sx + 10 - this.fontRenderer.getStringWidth(num) / 2f, sy - 10, 0x404040);
            GlStateManager.enableLighting();
            GlStateManager.enableDepth();
        }
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