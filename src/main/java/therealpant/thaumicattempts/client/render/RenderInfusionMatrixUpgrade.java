package therealpant.thaumicattempts.client.render;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import software.bernie.geckolib3.renderers.geo.GeoBlockRenderer;
import software.bernie.geckolib3.geo.render.built.GeoModel;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.client.model.InfusionMatrixUpgradeModel;
import therealpant.thaumicattempts.world.block.BlockInfusionMatrixUpgrade;
import therealpant.thaumicattempts.world.tile.TileInfusionMatrixUpgrade;

@SideOnly(Side.CLIENT)
public class RenderInfusionMatrixUpgrade extends GeoBlockRenderer<TileInfusionMatrixUpgrade> {
    private static final ResourceLocation EMISSIVE_TEXTURE =
            new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/upgrade_e.png");
    private EnumFacing lastFacing = EnumFacing.UP;

    public RenderInfusionMatrixUpgrade() {
        super(new InfusionMatrixUpgradeModel());
    }

    @Override
    public void rotateBlock(EnumFacing facing) {
        lastFacing = facing == null ? EnumFacing.UP : facing;
        GlStateManager.translate(0.0F, 0.5F, 0.0F);
        switch (lastFacing) {
            case DOWN:  GlStateManager.rotate(180.0F, 1.0F, 0.0F, 0.0F); break;
            case NORTH: GlStateManager.rotate(-90.0F, 1.0F, 0.0F, 0.0F); break;
            case SOUTH: GlStateManager.rotate(90.0F, 1.0F, 0.0F, 0.0F); break;
            case EAST:  GlStateManager.rotate(-90.0F, 0.0F, 0.0F, 1.0F); break;
            case WEST:  GlStateManager.rotate(90.0F, 0.0F, 0.0F, 1.0F); break;
            case UP:
            default: break;
        }
        GlStateManager.translate(0.0F, -0.5F, 0.0F);
    }

    @Override
    public void render(TileInfusionMatrixUpgrade tile, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        if (tile != null && tile.getWorld() != null) {
            IBlockState state = tile.getWorld().getBlockState(tile.getPos());
            if (state.getBlock() instanceof BlockInfusionMatrixUpgrade) {
                lastFacing = state.getValue(BlockInfusionMatrixUpgrade.FACING);
            }
        }
        float[] previousLight = RenderSafety.captureLightmap();
        try {
            super.render(tile, x, y, z, partialTicks, destroyStage, alpha);
            renderEmissiveLayer(tile, x, y, z, partialTicks);
        } finally {
            RenderSafety.restoreLightmap(previousLight);
            RenderSafety.resetGlState();
        }
    }

    private void renderEmissiveLayer(TileInfusionMatrixUpgrade tile,
                                     double x, double y, double z,
                                     float partialTicks) {
        if (tile == null) return;
        GeoModel model = getGeoModelProvider().getModel(
                getGeoModelProvider().getModelLocation(tile));
        float[] previousLight = RenderSafety.captureLightmap();

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x + 0.5D, y + 0.01D, z + 0.5D);
            rotateBlock(lastFacing);
            RenderSafety.setFullBrightLightmap();
            GlStateManager.disableLighting();
            GlStateManager.enableBlend();
            GlStateManager.enableAlpha();
            GlStateManager.depthMask(true);
            GlStateManager.tryBlendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO);
            Minecraft.getMinecraft().getTextureManager().bindTexture(EMISSIVE_TEXTURE);
            render(model, tile, partialTicks, 1.0F, 1.0F, 1.0F, 1.0F);
        } finally {
            GlStateManager.popMatrix();
            RenderSafety.restoreLightmap(previousLight);
            RenderSafety.resetGlState();
        }
    }
}
