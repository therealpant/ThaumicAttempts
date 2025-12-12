package therealpant.thaumicattempts.client.gui;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.util.NonNullList;
import net.minecraft.inventory.Slot;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.golemcraft.container.ContainerCraftPattern;
import therealpant.thaumicattempts.golemcraft.container.ContainerInfusionPattern;
import therealpant.thaumicattempts.golemcraft.container.IPatternContainer;
import therealpant.thaumicattempts.golemcraft.item.ItemBasePattern;
import therealpant.thaumicattempts.golemcraft.item.ItemInfusionPattern;

public class GuiCraftPattern extends GuiContainer {

    private static final ResourceLocation TEX_CRAFTER =
            new ResourceLocation(ThaumicAttempts.MODID, "textures/gui/golem_crafter.png");
    private static final ResourceLocation TEX_ORDER_TERMINAL =
            new ResourceLocation(ThaumicAttempts.MODID, "textures/gui/order_terminal.png");

    private static final int PAPER_U_CRAFT = 158;
    private static final int PAPER_V_CRAFT = 0;
    private static final int PAPER_U_INFUSION = 256;
    private static final int PAPER_V_INFUSION = 0;

    private boolean blurOn = false;
    private static final int INFUSION_RADIUS = 20;

    private final IPatternContainer patternContainer;

    public GuiCraftPattern(InventoryPlayer playerInv, ItemStack patternStack) {
        super(createContainer(playerInv, patternStack));
        this.patternContainer = (IPatternContainer) this.inventorySlots;
        this.xSize = PatternGuiLayout.GUI_WIDTH;
        this.ySize = PatternGuiLayout.GUI_HEIGHT;
    }

    private static net.minecraft.inventory.Container createContainer(InventoryPlayer playerInv, ItemStack patternStack) {
        if (!patternStack.isEmpty() && patternStack.getItem() instanceof ItemInfusionPattern) {
            return new ContainerInfusionPattern(playerInv, patternStack);
        }
        return new ContainerCraftPattern(playerInv, patternStack);
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

        ItemStack pat = patternContainer.getPatternStack();
        int repeat = ItemBasePattern.getRepeatCount(pat);
        if (repeat > 1) {
            final int resultX = PatternGuiLayout.PREVIEW_LEFT;
            final int resultY = PatternGuiLayout.PREVIEW_TOP;

            String text = String.valueOf(repeat);
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            this.fontRenderer.drawStringWithShadow(text,
                    resultX + 12 - this.fontRenderer.getStringWidth(text),
                    resultY - 10,
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

        boolean infusionMode = patternContainer.isInfusionMode();

        final int backgroundX = x + PatternGuiLayout.BACKGROUND_LEFT;
        final int backgroundY = y + PatternGuiLayout.BACKGROUND_TOP;

        mc.getTextureManager().bindTexture(TEX_CRAFTER);
        drawModalRectWithCustomSizedTexture(backgroundX, backgroundY,
                PatternGuiLayout.BACKGROUND_U, PatternGuiLayout.BACKGROUND_V,
                PatternGuiLayout.BACKGROUND_W, PatternGuiLayout.BACKGROUND_H,
                354, 256);

        final int paperX = x + PatternGuiLayout.PAPER_LEFT;
        final int paperY = y + PatternGuiLayout.PAPER_TOP;
        drawPaper(infusionMode, paperX, paperY);

        final int gridLeft = x + PatternGuiLayout.GRID_LEFT;
        final int gridTop  = y + PatternGuiLayout.GRID_TOP;
        final int baseW = 3 * PatternGuiLayout.CELL;

        if (infusionMode) {
            final int orderCenterX = x + PatternGuiLayout.INFUSION_CENTER_LEFT + 8;
            final int orderCenterY = y + PatternGuiLayout.INFUSION_CENTER_TOP + 8;
            drawInfusionOrder(orderCenterX, orderCenterY);
        }

        final int invBgX = x + PatternGuiLayout.PLAYER_INV_BG_LEFT;
        final int invBgY = y + PatternGuiLayout.PLAYER_INV_BG_TOP;
        mc.getTextureManager().bindTexture(TEX_ORDER_TERMINAL);
        drawModalRectWithCustomSizedTexture(invBgX, invBgY,
                PatternGuiLayout.PLAYER_INV_U, PatternGuiLayout.PLAYER_INV_V,
                PatternGuiLayout.PLAYER_INV_W, PatternGuiLayout.PLAYER_INV_H,
                354, 256);
    }

        // ---- рамка инвентаря игрока — строго под координаты слотов игрока ----
        private void drawPaper(boolean infusionMode, int paperX, int paperY) {
            int u = infusionMode ? PAPER_U_INFUSION : PAPER_U_CRAFT;
            int v = infusionMode ? PAPER_V_INFUSION : PAPER_V_CRAFT;

            mc.getTextureManager().bindTexture(TEX_CRAFTER);
            drawModalRectWithCustomSizedTexture(paperX, paperY, u, v,
                    PatternGuiLayout.PAPER_DRAW_W, PatternGuiLayout.PAPER_DRAW_H,
                    354, 256);
            GlStateManager.popMatrix();
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

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws java.io.IOException {
        Slot slot = this.getSlotUnderMouse();
        if (slot != null) {
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }

        if (!isInfusionAreaClick(mouseX, mouseY) || !handleInfusionClick(mouseButton)) {
            super.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    private boolean isInfusionAreaClick(int mouseX, int mouseY) {
        if (!patternContainer.isInfusionMode()) return false;

        final int x = (width - xSize) / 2;
        final int y = (height - ySize) / 2;

        int paperX = x + PatternGuiLayout.PAPER_LEFT;
        int paperY = y + PatternGuiLayout.PAPER_TOP;

        return mouseX >= paperX && mouseX < paperX + PatternGuiLayout.PAPER_DRAW_W &&
                mouseY >= paperY && mouseY < paperY + PatternGuiLayout.PAPER_DRAW_H;
    }

    private boolean handleInfusionClick(int mouseButton) {
        if (!patternContainer.isInfusionMode()) return false;

        int action = mouseButton == 1 ? 1 : 0; // ЛКМ — добавить, ПКМ — убрать последний
        this.mc.playerController.sendEnchantPacket(this.inventorySlots.windowId, action);
        return true;
    }

    private void drawInfusionOrder(int centerX, int centerY) {
        if (!patternContainer.isInfusionMode()) return;

        NonNullList<ItemStack> order = patternContainer.getOrderView();
        int count = 0;
        for (ItemStack stack : order) {
            if (!stack.isEmpty()) count++;
        }
        if (count == 0) return;

        int rendered = 0;
        for (int i = 0; i < order.size(); i++) {
            ItemStack stack = order.get(i);
            if (stack.isEmpty()) continue;

            int drawX;
            int drawY;
            if (rendered == 0) {
                drawX = centerX - 8;
                drawY = centerY - 8;
            } else {
                double angle = 360.0 * (rendered - 1) / (Math.max(count - 1, 1));
                double rad = Math.toRadians(angle);
                double dx = Math.sin(rad) * INFUSION_RADIUS;
                double dy = -Math.cos(rad) * INFUSION_RADIUS;
                drawX = (int) Math.round(centerX + dx) - 8;
                drawY = (int) Math.round(centerY + dy) - 8;
            }

            this.itemRender.renderItemAndEffectIntoGUI(stack, drawX, drawY);
            this.itemRender.renderItemOverlayIntoGUI(this.fontRenderer, stack, drawX, drawY, null);
            rendered++;
        }
    }
}
