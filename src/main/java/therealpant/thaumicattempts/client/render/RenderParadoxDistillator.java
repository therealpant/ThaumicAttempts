package therealpant.thaumicattempts.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.MinecraftForgeClient;
import software.bernie.geckolib3.renderers.geo.GeoBlockRenderer;
import therealpant.thaumicattempts.client.model.ParadoxDistillatorModel;
import therealpant.thaumicattempts.world.tile.TileParadoxDistillator;

public class RenderParadoxDistillator extends GeoBlockRenderer<TileParadoxDistillator> {
    private static final double ITEM_HEIGHT = 18.0D / 16.0D;
    private static final double[][] SLOT_ANCHORS = {
            {17.0D / 16.0D, 17.0D / 16.0D},
            {-17.0D / 16.0D, 17.0D / 16.0D},
            {-17.0D / 16.0D, -17.0D / 16.0D},
            {17.0D / 16.0D, -17.0D / 16.0D}
    };

    public RenderParadoxDistillator() { super(new ParadoxDistillatorModel()); }

    @Override
    public void render(TileParadoxDistillator tile, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        if (tile == null) return;
        super.render(tile, x, y, z, partialTicks, destroyStage, alpha);
        if (MinecraftForgeClient.getRenderPass() == 0) {
            renderInputs(tile, x, y, z, partialTicks);
            ParadoxPedestalTransferRenderer.render(tile, tile.getPedestalAutomation(),
                    x, y, z, partialTicks);
        }
    }

    private void renderInputs(TileParadoxDistillator tile, double x, double y, double z,
                              float partialTicks) {
        double ticks = tile.getWorld() == null ? partialTicks
                : tile.getWorld().getTotalWorldTime() + partialTicks;
        for (int slot = 0; slot < TileParadoxDistillator.SLOT_COUNT; slot++) {
            ItemStack stack = tile.getInput(slot);
            if (stack.isEmpty()) continue;
            GlStateManager.pushMatrix();
            try {
                GlStateManager.translate(
                        x + 0.5D + SLOT_ANCHORS[slot][0],
                        y + ITEM_HEIGHT,
                        z + 0.5D + SLOT_ANCHORS[slot][1]);
                GlStateManager.rotate((float) (ticks * 2.0D + slot * 90.0D),
                        0.0F, 1.0F, 0.0F);
                GlStateManager.scale(0.52D, 0.52D, 0.52D);
                RenderHelper.enableStandardItemLighting();
                Minecraft.getMinecraft().getRenderItem().renderItem(
                        stack, ItemCameraTransforms.TransformType.FIXED);
            } finally {
                RenderHelper.disableStandardItemLighting();
                GlStateManager.popMatrix();
                RenderSafety.resetGlState();
            }
        }
    }
}
