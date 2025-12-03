package therealpant.thaumicattempts.client.render;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import software.bernie.geckolib3.renderers.geo.GeoBlockRenderer;
import therealpant.thaumicattempts.client.model.InfusionRequesterModel;
import therealpant.thaumicattempts.golemnet.tile.TileInfusionRequester;

@SideOnly(Side.CLIENT)
public class RenderInfusionRequester extends GeoBlockRenderer<TileInfusionRequester> {
    public RenderInfusionRequester() {
        super(new InfusionRequesterModel());
    }
}

