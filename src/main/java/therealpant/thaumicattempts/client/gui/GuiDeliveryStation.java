package therealpant.thaumicattempts.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.golemcraft.container.ContainerDeliveryStation;
import therealpant.thaumicattempts.golemcraft.tile.TileDeliveryStation;

public class GuiDeliveryStation extends GuiContainer {

    private static final ResourceLocation TEX_TERMINAL =
            new ResourceLocation(ThaumicAttempts.MODID, "textures/gui/gui_terminal.png");
    private static final ResourceLocation TEX_CRAFTER =
            new ResourceLocation(ThaumicAttempts.MODID, "textures/gui/gui_crafter3_5.png");
    private static final ResourceLocation TEX_BASE_TC =
            new ResourceLocation("thaumcraft","textures/gui/gui_base.png");

    // gui_terminal: весь атлас 128×128, вырез под 3×3 – 60×60 из (0,0)
    private static final int TERM_U = 0, TERM_V = 166;
    private static final int TERM_W = 60, TERM_H = 60;
    private static final int TERM_TEX_W = 128, TERM_TEX_H = 128;

    // gui_crafter3_5: весь атлас 354×256, вырез под 3×5 – 60×96 из (0,60)
    private static final int CRAFTER_U = 0, CRAFTER_V = 60;
    private static final int CRAFTER_W = 60, CRAFTER_H = 96;
    private static final int CRAFTER_TEX_W = 354, CRAFTER_TEX_H = 256;

    private final TileDeliveryStation tile;

    public GuiDeliveryStation(InventoryPlayer playerInv, TileDeliveryStation tile) {
        super(new ContainerDeliveryStation(playerInv, tile));
        this.tile = tile;
        this.xSize = 194;
        this.ySize = 230;
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

        // === PATTERN 3×5 (ItemDeliveryPattern) ===
        Minecraft.getMinecraft().getTextureManager().bindTexture(TEX_CRAFTER);
        this.drawModalRectWithCustomSizedTexture(
                left + ContainerDeliveryStation.PATTERN_LEFT - 4,
                top  + ContainerDeliveryStation.PATTERN_TOP  - 4,
                CRAFTER_U, CRAFTER_V,
                CRAFTER_H, CRAFTER_W,
                CRAFTER_TEX_W, CRAFTER_TEX_H
        );

        // === PAYLOAD 1×1 (спец предмет ПКМ) ===
        Minecraft.getMinecraft().getTextureManager().bindTexture(TEX_TERMINAL);
        this.drawModalRectWithCustomSizedTexture(
                left + ContainerDeliveryStation.PAYLOAD_LEFT - 12,
                top  + ContainerDeliveryStation.PAYLOAD_TOP  - 12,
                0,0,
                TERM_W, TERM_H,
                TERM_TEX_W, TERM_TEX_H
        );

        /* ===== Инвентарь игрока (фон из gui_terminal, тот же вырез что и у 3×3) ===== */
        int invLeft = left + ContainerDeliveryStation.PLAYER_INV_LEFT;
        int invTop  = top  + ContainerDeliveryStation.PLAYER_INV_TOP;

        // общая область: 9×3 + зазор + хотбар, с рамкой по 4px
        int areaX = invLeft - 4;
        int areaY = invTop  - 4;

        mc.getTextureManager().bindTexture(TEX_BASE_TC);
        this.drawTexturedModalRect(
                areaX-4, areaY-4,
                TERM_U, TERM_V,          // тот же угол обрезки, что и у 3×3
                176, 90   // габарит png (128×128)
        );
    }
}
