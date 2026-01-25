package therealpant.thaumicattempts.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import therealpant.thaumicattempts.world.tile.TileAuraBooster;

public class RenderTileAuraBooster extends TileEntitySpecialRenderer<TileAuraBooster> {


    @Override
    public void render(TileAuraBooster te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        if (te == null || te.getWorld() == null) return;
        ItemStack stack = te.getPearlStack();
        if (stack == null || stack.isEmpty()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.getRenderItem() == null) return;
        RenderItem renderItem = mc.getRenderItem();

        float ticks = te.getWorld().getTotalWorldTime() + partialTicks;
        float hover = MathHelper.sin(ticks * 0.1F) * 0.1F + 0.1F;
        float rotation = ticks * 2.0F;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5D, y + 1.1D + hover, z + 0.5D);
        GlStateManager.rotate(rotation, 0.0F, 1.0F, 0.0F);
        GlStateManager.scale(0.75F, 0.75F, 0.75F);
        renderItem.renderItem(stack, ItemCameraTransforms.TransformType.GROUND);
        GlStateManager.popMatrix();
    }
}