package therealpant.thaumicattempts.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.items.IItemHandler;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.golemcraft.item.ItemInfusionPattern;
import therealpant.thaumicattempts.golemnet.container.ContainerInfusionRequester;
import therealpant.thaumicattempts.golemnet.tile.TileInfusionRequester;

public class GuiInfusionRequester extends GuiContainer {

    private static final ResourceLocation TEX_BG =
            new ResourceLocation(ThaumicAttempts.MODID, "textures/gui/infusion_requester.png");

    private static final int TEX_W = 354;
    private static final int TEX_H = 256;

    private static final int PREVIEW_X = 230;
    private static final int PREVIEW_Y = 35;

    private static final int CENTER_LEFT = 161;
    private static final int CENTER_TOP  = 64;
    private static final int RING_RADIUS = 32;

    private final TileInfusionRequester tile;
    private final IItemHandler patterns;

    private int tickCounter = 0;
    private int lastSwitchTick = 0;
    private int currentSlot = -1;
    private final int switchPeriod = 30;

    public GuiInfusionRequester(InventoryPlayer playerInv, TileInfusionRequester tile) {
        super(new ContainerInfusionRequester(playerInv, tile));
        this.tile = tile;
        this.patterns = tile.getPatternHandler();
        this.xSize = TEX_W;
        this.ySize = TEX_H;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        tickCounter++;

        boolean powered = tile.getWorld() != null && tile.getWorld().isBlockPowered(tile.getPos());
        Integer active = powered ? tile.getActivePatternIndex() : null;
        if (powered && active != null) {
            currentSlot = active;
            return;
        }

        if (tickCounter - lastSwitchTick >= switchPeriod) {
            lastSwitchTick = tickCounter;
            nextNonEmptyPattern();
        }
    }

    private void nextNonEmptyPattern() {
        if (patterns == null) { currentSlot = -1; return; }
        int slots = Math.min(ContainerInfusionRequester.PATTERN_COLS * ContainerInfusionRequester.PATTERN_ROWS, patterns.getSlots());
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
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
    // нет текста
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1F, 1F, 1F, 1F);
        int left = this.guiLeft;
        int top = this.guiTop;

        Minecraft.getMinecraft().getTextureManager().bindTexture(TEX_BG);
        this.drawModalRectWithCustomSizedTexture(left, top, 0, 0, TEX_W, TEX_H, TEX_W, TEX_H);

        renderPatternPreview(left, top);
        highlightCurrentPattern(left, top);
    }

    private void highlightCurrentPattern(int guiLeft, int guiTop) {
        if (currentSlot < 0) return;

        int baseX = guiLeft + ContainerInfusionRequester.PATTERN_LEFT;
        int baseY = guiTop  + ContainerInfusionRequester.PATTERN_TOP;

        int col = currentSlot % ContainerInfusionRequester.PATTERN_COLS;
        int row = currentSlot / ContainerInfusionRequester.PATTERN_COLS;

        int sx = baseX + col * ContainerInfusionRequester.CELL;
        int sy = baseY + row * ContainerInfusionRequester.CELL;

        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        drawRect(sx, sy, sx + 16, sy + 16, 0x80FFFFFF);
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
    }

    private void renderPatternPreview(int guiLeft, int guiTop) {
        ItemStack pattern = getCurrentPattern();
        if (pattern.isEmpty()) return;

        NonNullList<ItemStack> order = ItemInfusionPattern.readOrder(pattern);
        ItemStack result = ItemInfusionPattern.calcResultPreview(pattern, mc.world);

        RenderHelper.enableGUIStandardItemLighting();
        renderResultItem(guiLeft, guiTop, result);
        renderCenterAndRing(guiLeft, guiTop, order);
        RenderHelper.disableStandardItemLighting();
    }

    private void renderResultItem(int guiLeft, int guiTop, ItemStack result) {
        if (result == null || result.isEmpty()) return;
        int x = guiLeft + PREVIEW_X;
        int y = guiTop  + PREVIEW_Y;
        itemRender.renderItemAndEffectIntoGUI(result, x, y);
        itemRender.renderItemOverlayIntoGUI(this.fontRenderer, result, x, y, null);
    }

    private void renderCenterAndRing(int guiLeft, int guiTop, NonNullList<ItemStack> order) {
        if (order == null || order.isEmpty()) return;

        int centerX = guiLeft + CENTER_LEFT;
        int centerY = guiTop  + CENTER_TOP;

        ItemStack center = order.get(0);
        if (center != null && !center.isEmpty()) {
            itemRender.renderItemAndEffectIntoGUI(center, centerX, centerY);
            itemRender.renderItemOverlayIntoGUI(this.fontRenderer, center, centerX, centerY, null);
        }

        int ringCount = Math.max(0, order.size() - 1);
        if (ringCount <= 0) return;

        double centerCx = centerX + 8.0;
        double centerCy = centerY + 8.0;

        for (int i = 0; i < ringCount; i++) {
            ItemStack st = order.get(i + 1);
            if (st == null || st.isEmpty()) continue;

            double angle = -Math.PI / 2 + (2 * Math.PI * i) / ringCount;
            int x = (int) Math.round(centerCx + Math.cos(angle) * RING_RADIUS) - 8;
            int y = (int) Math.round(centerCy + Math.sin(angle) * RING_RADIUS) - 8;

            itemRender.renderItemAndEffectIntoGUI(st, x, y);
            itemRender.renderItemOverlayIntoGUI(this.fontRenderer, st, x, y, null);
        }
    }

    private ItemStack getCurrentPattern() {
        if (patterns == null || currentSlot < 0 || currentSlot >= patterns.getSlots()) return ItemStack.EMPTY;
        return patterns.getStackInSlot(currentSlot);
    }

    @Override
    public void initGui() {
        super.initGui();

        boolean powered = tile.getWorld() != null && tile.getWorld().isBlockPowered(tile.getPos());
        if (powered) {
            Integer active = tile.getActivePatternIndex();
            if (active != null) currentSlot = active;
        }

        if (currentSlot < 0) nextNonEmptyPattern();
        lastSwitchTick = tickCounter;
    }
}