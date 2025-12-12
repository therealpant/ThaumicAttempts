// src/main/java/therealpant/thaumicattempts/client/render/RenderPatternRequesterGeo.java
package therealpant.thaumicattempts.client.render;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import software.bernie.geckolib3.geo.render.built.GeoModel;
import software.bernie.geckolib3.renderers.geo.GeoBlockRenderer;
import thaumcraft.api.blocks.BlocksTC;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.client.model.PatternRequesterModel;
import therealpant.thaumicattempts.golemnet.block.BlockPatternRequester;
import therealpant.thaumicattempts.golemnet.tile.TileGolemDispatcher;
import therealpant.thaumicattempts.golemnet.tile.TilePatternRequester;

@SideOnly(Side.CLIENT)
public class RenderPatternRequesterGeo extends GeoBlockRenderer<TilePatternRequester> {

    private static final ResourceLocation TEX_EMISSIVE =
            new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/pattern_requester_e.png");

    private static final float EMISSIVE_Y_OFFSET = 0.01f; // подбери значение сам


    public RenderPatternRequesterGeo() {
        super(new PatternRequesterModel());
    }

    /** Прочитать facing из блока (по умолчанию UP). */
    private EnumFacing getFacing(TilePatternRequester te) {
        if (te == null || te.getWorld() == null) return EnumFacing.UP;
        IBlockState state = te.getWorld().getBlockState(te.getPos());
        if (state.getBlock() instanceof BlockPatternRequester) {
            return state.getValue(BlockPatternRequester.FACING);
        }
        return EnumFacing.UP;
    }

    /** Повернуть локальную ось Y модели в сторону facing. */
    private void applyFacingTransform(EnumFacing facing) {
        switch (facing) {
            case UP:
                // локальная Y совпадает с глобальной Y – ничего
                break;
            case DOWN:
                // Y -> -Y
                GlStateManager.rotate(180f, 1f, 0f, 0f);
                break;
            case NORTH:
                // Y -> -Z
                GlStateManager.rotate(-90f, 1f, 0f, 0f);
                break;
            case SOUTH:
                // Y -> +Z
                GlStateManager.rotate(90f, 1f, 0f, 0f);
                break;
            case EAST:
                // Y -> +X
                GlStateManager.rotate(-90f, 0f, 0f, 1f);
                break;
            case WEST:
                // Y -> -X
                GlStateManager.rotate(90f, 0f, 0f, 1f);
                break;
        }
    }

    /** Geckolib вызывает это перед отрисовкой модели. */
    @Override
    public void rotateBlock(EnumFacing facing) {
        // Сдвигаем локальный origin в центр блока по Y
        GlStateManager.translate(0.0F, 0.5F, 0.0F);
        applyFacingTransform(facing);
        // Возвращаем origin обратно
        GlStateManager.translate(0.0F, -0.5F, 0.0F);

    }

    // ========= ЗЕРКАЛО ==========

    // +2px по твоей просьбе
    private static final float OFFSET_UP = 0.30f + 0.125f;
    private static final float OFFSET_DN = -0.30f + 0.125f;

    private static final float BOB_AMPL  = 0.06f;
    private static final float BOB_SPEED = (float)(Math.PI * 2.0 / 60.0);
    private static final float SPIN_BASE = 1.4f;
    private static final float SCALE     = 0.55f;

    private final ItemStack renderMirror = new ItemStack(BlocksTC.mirror);

    @Override
    public void render(TilePatternRequester te,
                       double x, double y, double z,
                       float partialTicks,
                       int destroyStage,
                       float alpha) {

        // Сначала гео-модель
        super.render(te, x, y, z, partialTicks, destroyStage, alpha);

        if (te == null || te.getWorld() == null) return;

        long t = te.getWorld().getTotalWorldTime();
        float tt = (t + partialTicks);
        float bob = MathHelper.sin(tt * BOB_SPEED) * BOB_AMPL;
        float spin = (tt * SPIN_BASE) % 360f;

        EnumFacing facing = getFacing(te);

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y + 0.5, z + 0.5);
        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO
        );

        // Поворачиваем зеркало тем же способом: локальная Y → FACING
        applyFacingTransform(facing);

        float yOff = (facing == EnumFacing.DOWN) ? OFFSET_DN : OFFSET_UP;
        GlStateManager.translate(0.0, yOff + bob, 0.0F);

        GlStateManager.rotate(spin, 0, 1, 0);
        GlStateManager.scale(SCALE, SCALE, SCALE);

        // свет
        BlockPos lp = new BlockPos(
                te.getPos().getX(),
                te.getPos().getY() + (yOff + bob),
                te.getPos().getZ()
        );
        int light = te.getWorld().getCombinedLight(lp, 0);
        OpenGlHelper.setLightmapTextureCoords(
                OpenGlHelper.lightmapTexUnit,
                (float)(light & 0xFFFF),
                (float)((light >> 16) & 0xFFFF)
        );

        RenderHelper.enableStandardItemLighting();
        Minecraft.getMinecraft().getRenderItem()
                .renderItem(renderMirror, ItemCameraTransforms.TransformType.FIXED);
        RenderHelper.disableStandardItemLighting();

        GlStateManager.disableBlend();
        GlStateManager.popMatrix();

        renderEmissiveLayer(te, x, y, z, partialTicks);
    }

    /**
     * Второй проход: та же Geo-модель, но с эмисс-текстурой и фуллбрайтом.
     */
    private void renderEmissiveLayer(TilePatternRequester te,
                                     double x, double y, double z,
                                     float partialTicks) {
        if (te == null || te.getWorld() == null) return;

        GeoModel model = this.getGeoModelProvider().getModel(
                this.getGeoModelProvider().getModelLocation(te)
        );

        float prevX = OpenGlHelper.lastBrightnessX;
        float prevY = OpenGlHelper.lastBrightnessY;

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x + 0.5, y + EMISSIVE_Y_OFFSET, z + 0.5);

            // Фуллбрайт
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);

            GlStateManager.disableLighting();

            Minecraft.getMinecraft().getTextureManager().bindTexture(TEX_EMISSIVE);

            // Рендерим как непрозрачный фуллбрайт слой
            this.render(model, te, partialTicks, 1f, 1f, 1f, 1f);

            // Возвращаем настройки OpenGL
            GlStateManager.depthMask(true);
            GlStateManager.enableLighting();

        } finally {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevX, prevY);
            GlStateManager.popMatrix();
        }
    }
}
