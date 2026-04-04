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
        tile.setWorld(null);
        tile.setPos(BlockPos.ORIGIN);
        TileEntityRendererDispatcher.instance.render(tile, 0.0D, 0.0D, 0.0D, partialTicks);
    }
}
