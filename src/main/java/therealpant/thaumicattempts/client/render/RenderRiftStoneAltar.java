package therealpant.thaumicattempts.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import therealpant.thaumicattempts.client.fx.FXPrismInfusionItem;
import therealpant.thaumicattempts.items.ItemPrism;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import software.bernie.geckolib3.renderers.geo.GeoBlockRenderer;
import therealpant.thaumicattempts.client.model.RiftStoneAltarModel;
import therealpant.thaumicattempts.world.tile.TileRiftStoneAltar;

@SideOnly(Side.CLIENT)
public class RenderRiftStoneAltar extends GeoBlockRenderer<TileRiftStoneAltar> {
    private final Map<TileRiftStoneAltar, Long> lastFxTicks = new WeakHashMap<>();
    public RenderRiftStoneAltar() {
        super(new RiftStoneAltarModel());
    }

    @Override
    public void render(TileRiftStoneAltar tile, double x, double y, double z, float partialTicks,
                       int destroyStage, float alpha) {
        super.render(tile, x, y, z, partialTicks, destroyStage, alpha);
        ItemStack prism = tile.getPrism();
        if (prism.isEmpty()) return;
        double prismY = tile.isWorking() ? 2.55D : 1.55D;
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x + 0.5D, y + prismY, z + 0.5D);
            GlStateManager.rotate((tile.getWorld().getTotalWorldTime() + partialTicks) * 2.0F, 0, 1, 0);
            GlStateManager.scale(0.55F, 0.55F, 0.55F);
            RenderHelper.enableStandardItemLighting();
            Minecraft.getMinecraft().getRenderItem().renderItem(prism, ItemCameraTransforms.TransformType.FIXED);
        } finally {
            RenderHelper.disableStandardItemLighting();
            GlStateManager.popMatrix();
        }
        spawnTransmutationFx(tile, prism);
    }

    private void spawnTransmutationFx(TileRiftStoneAltar tile, ItemStack prism) {
        BlockPos core = tile.getLinkedRiftonomiconPos();
        if (!tile.isWorking() || core == null || tile.getWorld() == null) return;
        long tick = tile.getWorld().getTotalWorldTime();
        Long previous = lastFxTicks.get(tile);
        if (previous != null && previous == tick) return;
        lastFxTicks.put(tile, tick);

        double sx = tile.getPos().getX() + 0.5D;
        double sy = tile.getPos().getY() + (tile.isWorking() ? 2.55D : 1.55D);
        double sz = tile.getPos().getZ() + 0.5D;
        double tx = core.getX() + 0.5D;
        double ty = core.getY() - 0.5D;
        double tz = core.getZ() + 0.5D;
        double waypointY = core.getY() + 1.5D;

        Minecraft.getMinecraft().effectRenderer.addEffect(new FXPrismInfusionItem(
                tile.getWorld(), sx, sy, sz,
                tx, waypointY, tz, tx, ty, tz, prism, core.down()));
    }

    private float[] getPrismColor(int meta) {
        switch (meta) {
            case ItemPrism.META_AIR: return new float[] { 0.85F, 0.95F, 1.0F };
            case ItemPrism.META_AQUA: return new float[] { 0.2F, 0.55F, 1.0F };
            case ItemPrism.META_IGNIS: return new float[] { 1.0F, 0.25F, 0.08F };
            case ItemPrism.META_TERRA:
            default: return new float[] { 0.25F, 0.9F, 0.3F };
        }
    }
}
