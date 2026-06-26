package therealpant.thaumicattempts.client.render;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import software.bernie.geckolib3.renderers.geo.GeoBlockRenderer;
import therealpant.thaumicattempts.client.model.RiftPortalPlatformModel;
import therealpant.thaumicattempts.init.TABlocks;
import therealpant.thaumicattempts.world.tile.TileRiftPortalPlatform;

@SideOnly(Side.CLIENT)
public class RenderRiftPortalPlatform extends GeoBlockRenderer<TileRiftPortalPlatform> {
    public RenderRiftPortalPlatform() {
        super(new RiftPortalPlatformModel());
    }

    @Override
    public void render(TileRiftPortalPlatform tile, double x, double y, double z, float partialTicks,
                       int destroyStage, float alpha) {
        if (tile != null && tile.getWorld() != null
                && tile.getWorld().getBlockState(tile.getPos().up()).getBlock() != TABlocks.RIFT_STONE_PORTAL) {
            return;
        }

        super.render(tile, x, y, z, partialTicks, destroyStage, alpha);
    }
}
