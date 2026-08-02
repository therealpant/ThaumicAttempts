package therealpant.thaumicattempts.client.render;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.EnumFacing;
import software.bernie.geckolib3.renderers.geo.GeoBlockRenderer;
import therealpant.thaumicattempts.client.model.AncientColumnModel;
import therealpant.thaumicattempts.world.tile.TileAncientColumn;

public class RenderAncientColumn extends GeoBlockRenderer<TileAncientColumn> {

    public RenderAncientColumn() {
        super(new AncientColumnModel());
    }

    @Override
    protected void rotateBlock(EnumFacing facing) {
        if (facing == EnumFacing.DOWN) {
            GlStateManager.translate(0.0D, 1.0D, 0.0D);
            GlStateManager.rotate(180.0F, 1.0F, 0.0F, 0.0F);
        }
        // UP is the native Blockbench orientation.
    }
}
