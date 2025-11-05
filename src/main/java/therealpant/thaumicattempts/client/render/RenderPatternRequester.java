// src/main/java/therealpant/thaumicattempts/client/render/RenderPatternRequester.java
package therealpant.thaumicattempts.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import therealpant.thaumicattempts.golemnet.block.BlockPatternRequester;
import therealpant.thaumicattempts.golemnet.tile.TilePatternRequester;

@SideOnly(Side.CLIENT)
public class RenderPatternRequester extends TileEntitySpecialRenderer<TilePatternRequester> {

    private static final float OFFSET_UP = 0.30f;   // над центром блока
    private static final float OFFSET_DN = -0.30f;  // под центром блока
    private static final float BOB_AMPL  = 0.06f;
    private static final float BOB_SPEED = (float)(Math.PI * 2.0 / 60.0);
    private static final float SPIN_BASE = 1.4f;
    private static final float SCALE     = 0.55f;

    private final ItemStack renderMirror = new ItemStack(thaumcraft.api.blocks.BlocksTC.mirror);

    @Override
    public void render(TilePatternRequester te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        if (te == null || te.getWorld() == null) return;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y + 0.5, z + 0.5);
        GlStateManager.color(1F,1F,1F,1F);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO
        );

        long t = te.getWorld().getTotalWorldTime();
        float tt = (t + partialTicks);
        float bob = MathHelper.sin(tt * BOB_SPEED) * BOB_AMPL;
        float spin = (tt * SPIN_BASE) % 360f;

        // где рисовать — сверху/снизу
        net.minecraft.block.state.IBlockState state = te.getWorld().getBlockState(te.getPos());
        net.minecraft.util.EnumFacing facing = state.getValue(BlockPatternRequester.FACING);
        float yOff = (facing == net.minecraft.util.EnumFacing.DOWN) ? OFFSET_DN : OFFSET_UP;

        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0, yOff + bob, 0.0);

        // если смотрит вниз — перевернём зеркало
        if (facing == net.minecraft.util.EnumFacing.DOWN) {
            GlStateManager.rotate(180f, 1f, 0f, 0f);
        }

        GlStateManager.rotate(spin, 0, 1, 0);
        GlStateManager.scale(SCALE, SCALE, SCALE);

        // свет
        BlockPos lp = new BlockPos(te.getPos().getX(), te.getPos().getY() + (yOff + bob), te.getPos().getZ());
        int light = te.getWorld().getCombinedLight(lp, 0);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,
                (float)(light & 0xFFFF),
                (float)((light >> 16) & 0xFFFF));

        RenderHelper.enableStandardItemLighting();
        Minecraft.getMinecraft().getRenderItem().renderItem(renderMirror, ItemCameraTransforms.TransformType.FIXED);
        RenderHelper.disableStandardItemLighting();

        GlStateManager.popMatrix();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }
}
