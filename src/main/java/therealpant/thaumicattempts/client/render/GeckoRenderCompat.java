package therealpant.thaumicattempts.client.render;

import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Client-side targets used by the GeckoLib compatibility transformer. */
@SideOnly(Side.CLIENT)
public final class GeckoRenderCompat {

    private GeckoRenderCompat() {
    }

    public static void setLightmapTextureCoords(int ignoredInvalidTarget, float x, float y) {
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, x, y);
    }
}
