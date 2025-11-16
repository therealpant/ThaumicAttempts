package therealpant.thaumicattempts.client.render;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import software.bernie.geckolib3.renderers.geo.GeoBlockRenderer;
import therealpant.thaumicattempts.client.model.DispatcherModel;
import therealpant.thaumicattempts.golemnet.tile.TileGolemDispatcher;

@SideOnly(Side.CLIENT)
public class DispatcherRenderer extends GeoBlockRenderer<TileGolemDispatcher> {

    public DispatcherRenderer() {
        super(new DispatcherModel());
    }

    // Ничего переопределять не нужно – GeoBlockRenderer уже сам
    // всё делает как TESR для тайла
}
