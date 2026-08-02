package therealpant.thaumicattempts.client.render;

import net.minecraft.util.EnumFacing;
import software.bernie.geckolib3.renderers.geo.GeoBlockRenderer;
import therealpant.thaumicattempts.client.model.AncientFireModel;
import therealpant.thaumicattempts.world.tile.TileAncientFire;

public class RenderAncientFire extends GeoBlockRenderer<TileAncientFire> {

    public RenderAncientFire() {
        super(new AncientFireModel());
    }

    @Override
    protected void rotateBlock(EnumFacing facing) {
        if (facing != null && facing.getAxis().isHorizontal()) {
            super.rotateBlock(facing);
        }
        // Floor and ceiling variants keep their native Blockbench orientation.
    }
}
