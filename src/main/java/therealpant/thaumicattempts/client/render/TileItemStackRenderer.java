package therealpant.thaumicattempts.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.function.Supplier;

/**
 * Renders a tile entity as an item using its GeoBlockRenderer.
 */
@SideOnly(Side.CLIENT)
public class TileItemStackRenderer<T extends TileEntity> extends TileEntityItemStackRenderer {

    private final T tile;

    public TileItemStackRenderer(Supplier<T> factory) {
        this.tile = factory.get();
        this.tile.setPos(BlockPos.ORIGIN);
    }

    @Override
    public void renderByItem(ItemStack itemStackIn, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.world;
        if (world != null) {
            tile.setWorld(world);
            tile.setPos(mc.player != null ? mc.player.getPosition() : BlockPos.ORIGIN);
        }

        RenderSafety.GlStateSnapshot prevState = RenderSafety.captureGlState();
        float[] prevLight = RenderSafety.captureLightmap();
        RenderSafety.pushItemRender();
        try {
            RenderSafety.beginItemLighting();
            RenderSafety.setFullBrightLightmap();
            TileEntityRendererDispatcher.instance.render(tile, 0d, 0d, 0d, partialTicks);
        } finally {
            RenderSafety.popItemRender();
            RenderSafety.restoreLightmap(prevLight);
            RenderSafety.endItemLighting();
            RenderSafety.restoreGlState(prevState);
        }
    }
}
