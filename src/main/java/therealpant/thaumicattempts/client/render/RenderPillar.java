package therealpant.thaumicattempts.client.render;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import software.bernie.geckolib3.renderers.geo.GeoBlockRenderer;
import therealpant.thaumicattempts.client.model.ModelPillar;
import therealpant.thaumicattempts.tile.TilePillar;

@SideOnly(Side.CLIENT)
public class RenderPillar extends GeoBlockRenderer<TilePillar> {

    public RenderPillar() {
        super(new ModelPillar());
    }
}
