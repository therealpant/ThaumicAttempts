package therealpant.thaumicattempts.client.render;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import software.bernie.geckolib3.renderers.geo.GeoBlockRenderer;
import therealpant.thaumicattempts.client.model.RiftonomiconModel;
import therealpant.thaumicattempts.world.tile.TileRiftonomicon;

@SideOnly(Side.CLIENT)
public class RenderRiftonomicon extends GeoBlockRenderer<TileRiftonomicon> {
    public RenderRiftonomicon() {
        super(new RiftonomiconModel());
    }
}
