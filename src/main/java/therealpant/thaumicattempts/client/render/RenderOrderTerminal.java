// src/main/java/therealpant/thaumicattempts/golemnet/client/render/RenderOrderTerminal.java
package therealpant.thaumicattempts.client.render;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.model.ModelBook;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import therealpant.thaumicattempts.golemnet.block.BlockOrderTerminal;
import therealpant.thaumicattempts.golemnet.tile.TileOrderTerminal;

public class RenderOrderTerminal extends TileEntitySpecialRenderer<TileOrderTerminal> {

    private static final ResourceLocation BOOK_TEX =
            new ResourceLocation("minecraft", "textures/entity/enchanting_table_book.png");
    private final ModelBook modelBook = new ModelBook();

    @Override
    public void render(TileOrderTerminal te, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        if (te == null || te.getWorld() == null) return;

        // свет
        int light = te.getWorld().getCombinedLight(te.getPos().up(), 0);
        OpenGlHelper.setLightmapTextureCoords(
                OpenGlHelper.lightmapTexUnit,
                (light & 0xFFFF), ((light >> 16) & 0xFFFF));

        // интерполяции
        float pageFlip  = te.pageFlipPrev  + (te.pageFlip  - te.pageFlipPrev)  * partialTicks;
        float bookPitch = te.bookPitchPrev + (te.bookPitch - te.bookPitchPrev) * partialTicks;

        // yaw из FACING
        float yaw = 0F;
        IBlockState state = te.getWorld().getBlockState(te.getPos());
        if (state != null && state.getPropertyKeys().contains(BlockOrderTerminal.FACING)) {
            EnumFacing f = state.getValue(BlockOrderTerminal.FACING);
            yaw = f.getHorizontalAngle(); // S=0,W=90,N=180,E=270
        }

        GlStateManager.pushMatrix();

        // 1) центр блока (без бокового смещения!)
        GlStateManager.translate(x + 0.5D, y + 0.975D, z + 0.5D);

        // 2) повернуть в локальную систему блока
        GlStateManager.rotate(270.0F - yaw, 0.0F, 1.0F, 0.0F);

        // 3) ТЕПЕРЬ локальный сдвиг к «полке» (поворачивается вместе с блоком)
        final double LOCAL_X =  0.125D; // как раньше 0.375 от центра: -0.125 по X
        final double LOCAL_Z =  0.000D;
        GlStateManager.translate(LOCAL_X, 0.0D, LOCAL_Z);

        // 4) покачивание/тангаж
        float bob = 0.2F + (float)Math.sin((te.tickCount + partialTicks) * 0.1D) * 0.01F;
        GlStateManager.translate(0.0D, bob, 0.0D);
        GlStateManager.rotate(bookPitch * (180F / (float)Math.PI), 0.0F, 0.0F, 1.0F);

        // 5) ванильный наклон и масштаб
        GlStateManager.rotate(80.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.scale(0.8F, 0.8F, 0.8F);

        this.bindTexture(BOOK_TEX);
        GlStateManager.color(1F,1F,1F,1F);
        GlStateManager.enableRescaleNormal();

        float open      = 1.0F;
        float leftFlip  = clamp01(fracf(pageFlip * 0.5F + 0.25F) * 1.6F - 0.3F);
        float rightFlip = clamp01(fracf(pageFlip * 0.5F + 0.75F) * 1.6F - 0.3F);
        modelBook.render(null, te.tickCount + partialTicks, leftFlip, rightFlip, open, 0.0F, 0.0625F);

        GlStateManager.disableRescaleNormal();
        GlStateManager.popMatrix();
    }


    // Утилиты для 1.12 (аналог MathHelper.frac и clamp[0..1])
    private static float fracf(float x) { return x - (float) Math.floor(x); }
    private static float clamp01(float v) { return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v); }
}
