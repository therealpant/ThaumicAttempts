package therealpant.thaumicattempts.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.golemnet.container.ContainerInfusionRequester;
import therealpant.thaumicattempts.golemnet.tile.TileInfusionRequester;

public class GuiInfusionRequester extends GuiContainer {

    private static final ResourceLocation TEX_TERMINAL =
            new ResourceLocation(ThaumicAttempts.MODID, "textures/gui/gui_terminal.png");
    private static final ResourceLocation TEX_CRAFTER =
            new ResourceLocation(ThaumicAttempts.MODID, "textures/gui/gui_crafter3_5.png");
    private static final int TERM_W = 60, TERM_H = 60;
    private static final int TERM_TEX_W = 128, TERM_TEX_H = 128;
    private static final int CRAFTER_W = 60, CRAFTER_H = 96;
    private static final int CRAFTER_TEX_W = 354, CRAFTER_TEX_H = 256;

    private final TileInfusionRequester tile;

    public GuiInfusionRequester(InventoryPlayer playerInv, TileInfusionRequester tile) {
        super(new ContainerInfusionRequester(playerInv, tile));
        this.tile = tile;
        this.xSize = 194;
        this.ySize = 196;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        this.renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1F, 1F, 1F, 1F);
        int left = this.guiLeft;
        int top = this.guiTop;

        Minecraft.getMinecraft().getTextureManager().bindTexture(TEX_CRAFTER);
        this.drawModalRectWithCustomSizedTexture(
                left + ContainerInfusionRequester.PATTERN_LEFT - 4,
                top + ContainerInfusionRequester.PATTERN_TOP - 4,
                0, 60,
                CRAFTER_W, CRAFTER_H,
                CRAFTER_TEX_W, CRAFTER_TEX_H
        );

        Minecraft.getMinecraft().getTextureManager().bindTexture(TEX_TERMINAL);
        this.drawModalRectWithCustomSizedTexture(
                left + ContainerInfusionRequester.SPECIAL_LEFT - 4,
                top + ContainerInfusionRequester.SPECIAL_TOP - 4,
                0, 0,
                TERM_W, TERM_H,
                TERM_TEX_W, TERM_TEX_H
        );

        Minecraft.getMinecraft().getTextureManager().bindTexture(TEX_TERMINAL);
        this.drawModalRectWithCustomSizedTexture(
                left + ContainerInfusionRequester.RESULT_LEFT - 4,
                top + ContainerInfusionRequester.RESULT_TOP - 4,
                0, 0,
                TERM_W, TERM_H,
                TERM_TEX_W, TERM_TEX_H
        );

        mc.getTextureManager().bindTexture(new ResourceLocation("thaumcraft", "textures/gui/gui_base.png"));
        int areaX = left + ContainerInfusionRequester.PLAYER_INV_LEFT - 4;
        int areaY = top + ContainerInfusionRequester.PLAYER_INV_TOP - 4;
        this.drawTexturedModalRect(areaX - 4, areaY - 4, 0, 166, 176, 90);
    }
}