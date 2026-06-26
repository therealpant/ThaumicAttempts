package therealpant.thaumicattempts.client.render;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import software.bernie.geckolib3.renderers.geo.GeoBlockRenderer;
import therealpant.thaumicattempts.client.model.RiftStoneAltarModel;
import therealpant.thaumicattempts.world.tile.TileRiftStoneAltar;

@SideOnly(Side.CLIENT)
public class RenderRiftStoneAltar extends GeoBlockRenderer<TileRiftStoneAltar> {
    public RenderRiftStoneAltar() {
        super(new RiftStoneAltarModel());
    }
}
