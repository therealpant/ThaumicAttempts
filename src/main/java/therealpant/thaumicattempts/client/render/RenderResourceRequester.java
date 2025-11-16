// src/main/java/therealpant/thaumicattempts/client/render/RenderResourceRequester.java
package therealpant.thaumicattempts.client.render;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import software.bernie.geckolib3.renderers.geo.GeoBlockRenderer;
import therealpant.thaumicattempts.client.model.ResourceRequesterModel;
import therealpant.thaumicattempts.golemnet.tile.TileResourceRequester;

@SideOnly(Side.CLIENT)
public class RenderResourceRequester extends GeoBlockRenderer<TileResourceRequester> {

    public RenderResourceRequester() {
        super(new ResourceRequesterModel());
    }
}
