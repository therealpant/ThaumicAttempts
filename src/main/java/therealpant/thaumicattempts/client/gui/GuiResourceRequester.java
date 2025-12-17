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
import therealpant.thaumicattempts.golemcraft.item.ItemResourceList;
import therealpant.thaumicattempts.golemnet.container.ContainerResourceRequester;
import therealpant.thaumicattempts.golemnet.tile.TileResourceRequester;

public class GuiResourceRequester extends GuiContainer {

    private static final ResourceLocation TEX_BG =
            new ResourceLocation(ThaumicAttempts.MODID, "textures/gui/resourse_requester.png");

    private static final int TEX_W = 354;
    private static final int TEX_H = 256;

    private static final int GRID_LEFT = 143;
    private static final int GRID_TOP  = 46;
    private static final int GRID_STEP = 18;

    private final TileResourceRequester tile;

    private final IItemHandler patterns;

    private int tickCounter = 0;
    private int lastSwitchTick = 0;
    private int currentSlot = -1;
    private final int switchPeriod = 30;

    public GuiResourceRequester(InventoryPlayer playerInv, TileResourceRequester tile) {
        super(new ContainerResourceRequester(playerInv, tile));
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
        int slots = Math.min(ContainerResourceRequester.PATTERN_COLS * ContainerResourceRequester.PATTERN_ROWS, patterns.getSlots());
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

    // Все надписи убираем
    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1F, 1F, 1F, 1F);
        int left = this.guiLeft;
        int top  = this.guiTop;

        mc.getTextureManager().bindTexture(TEX_BG);
        this.drawModalRectWithCustomSizedTexture(left, top, 0, 0, TEX_W, TEX_H, TEX_W, TEX_H);

        renderItemListPreview(left, top);
        highlightCurrentPattern(left, top);
    }

    private void renderItemListPreview(int guiLeft, int guiTop) {
        ItemStack pattern = getCurrentPattern();
        if (pattern.isEmpty()) return;

        NonNullList<ItemStack> grid = ItemResourceList.readGrid(pattern);
        if (grid == null || grid.isEmpty()) return;

        RenderHelper.enableGUIStandardItemLighting();
        for (int i = 0; i < Math.min(9, grid.size()); i++) {
            ItemStack st = grid.get(i);
            if (st == null || st.isEmpty()) continue;

            int col = i % 3;
            int row = i / 3;

            int x = guiLeft + GRID_LEFT + col * GRID_STEP;
            int y = guiTop  + GRID_TOP  + row * GRID_STEP;

            itemRender.renderItemAndEffectIntoGUI(st, x, y);
            itemRender.renderItemOverlayIntoGUI(this.fontRenderer, st, x, y, null);
        }
        RenderHelper.disableStandardItemLighting();
    }

    private void highlightCurrentPattern(int guiLeft, int guiTop) {
        if (currentSlot < 0) return;

        int baseX = guiLeft + ContainerResourceRequester.PATTERN_LEFT;
        int baseY = guiTop  + ContainerResourceRequester.PATTERN_TOP;

        int col = currentSlot % ContainerResourceRequester.PATTERN_COLS;
        int row = currentSlot / ContainerResourceRequester.PATTERN_COLS;

        int sx = baseX + col * ContainerResourceRequester.CELL;
        int sy = baseY + row * ContainerResourceRequester.CELL;

        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        drawRect(sx, sy, sx + 16, sy + 16, 0x80FFFFFF);
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
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
