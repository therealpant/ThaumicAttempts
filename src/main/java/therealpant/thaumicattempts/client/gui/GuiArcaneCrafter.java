package therealpant.thaumicattempts.client.gui;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.golemcraft.container.ContainerArcaneCrafter;
import therealpant.thaumicattempts.golemcraft.tile.TileEntityArcaneCrafter;

public class GuiArcaneCrafter extends GuiGolemCrafter {

    private static final ResourceLocation BACKGROUND = new ResourceLocation(
            ThaumicAttempts.MODID,
            "textures/gui/a_crafter.png"
    );

    private static final int PREVIEW_X = 249;
    private static final int PREVIEW_Y = 41;

    private static final int GRID_LEFT = 143;
    private static final int GRID_TOP = 46;
    private static final int GRID_STEP = 18;

    private static final int[][] ARCANE_CRYSTAL_POINTS = new int[][]{
            {161, 23},
            {120, 49},
            {120, 79},
            {161, 104},
            {202, 79},
            {202, 49}
    };

    public GuiArcaneCrafter(InventoryPlayer playerInv, TileEntityArcaneCrafter te) {
        super(playerInv, te, new ContainerArcaneCrafter(playerInv, te));
    }

    @Override
    protected ResourceLocation getBackgroundTexture() {
        return BACKGROUND;
    }

    @Override
    protected int getPreviewX() {
        return PREVIEW_X;
    }

    @Override
    protected int getPreviewY() {
        return PREVIEW_Y;
    }

    @Override
    protected int getGridLeft() {
        return GRID_LEFT;
    }

    @Override
    protected int getGridTop() {
        return GRID_TOP;
    }

    @Override
    protected int getGridStep() {
        return GRID_STEP;
    }

    @Override
    protected boolean shouldRenderArcaneCrystals() {
        return true;
    }

    @Override
    protected int[][] getArcaneCrystalPoints() {
        return ARCANE_CRYSTAL_POINTS;
    }
}