package therealpant.thaumicattempts.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.Vec3d;
import therealpant.thaumicattempts.world.tile.automation.ParadoxPedestalAutomation;

public final class ParadoxPedestalTransferRenderer {
    private ParadoxPedestalTransferRenderer() {}

    public static void render(TileEntity tile, ParadoxPedestalAutomation automation,
                              double x, double y, double z, float partialTicks) {
        if (tile.getWorld() == null) return;
        double now = tile.getWorld().getTotalWorldTime() + partialTicks;
        for (ParadoxPedestalAutomation.Transfer transfer : automation.getTransfers()) {
            ItemStack stack = transfer.getStack();
            if (stack.isEmpty()) continue;
            double progress = Math.max(0.0D, Math.min(1.0D,
                    (now - transfer.getStartedAt()) / ParadoxPedestalAutomation.FLIGHT_TICKS));
            double eased = progress * progress * (3.0D - 2.0D * progress);
            Vec3d start = transfer.getStart();
            Vec3d end = transfer.getEnd();
            double worldX = lerp(start.x, end.x, eased);
            double worldY = lerp(start.y, end.y, eased)
                    + Math.sin(Math.PI * progress) * Math.max(0.75D,
                    Math.sqrt(start.squareDistanceTo(end)) * 0.18D);
            double worldZ = lerp(start.z, end.z, eased);

            GlStateManager.pushMatrix();
            try {
                GlStateManager.translate(x + worldX - tile.getPos().getX(),
                        y + worldY - tile.getPos().getY(),
                        z + worldZ - tile.getPos().getZ());
                GlStateManager.rotate((float) (now * 7.0D), 0.0F, 1.0F, 0.0F);
                GlStateManager.scale(0.55D, 0.55D, 0.55D);
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

    private static double lerp(double from, double to, double amount) {
        return from + (to - from) * amount;
    }
}
