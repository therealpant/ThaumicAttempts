package therealpant.thaumicattempts.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.geo.render.built.GeoBone;
import software.bernie.geckolib3.geo.render.built.GeoModel;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.model.provider.GeoModelProvider;
import software.bernie.geckolib3.renderers.geo.IGeoRenderer;
import therealpant.thaumicattempts.client.model.NavigationStoneModel;
import therealpant.thaumicattempts.items.ItemPortalRune;
import therealpant.thaumicattempts.world.tile.TileRiftonomicon;

import java.awt.Color;

public final class RiftonomiconWispRenderer implements IGeoRenderer<TileRiftonomicon> {
    private static final String CRYSTAL_BONE = "cristal";
    private final NavigationStoneModel modelProvider = new NavigationStoneModel();
    private float crystalRed = 1.0F;
    private float crystalGreen = 1.0F;
    private float crystalBlue = 1.0F;

    public NavigationStoneModel getModelProvider() {
        return modelProvider;
    }

    public void prepareAnimation(TileRiftonomicon tile, int uniqueId, int phaseTicks) {
        // First pass creates/initializes GeckoLib's controller for this stone.
        modelProvider.setLivingAnimations(tile, uniqueId);
        AnimationData data = tile.getFactory().getOrCreateAnimationData(uniqueId);
        AnimationController<?> controller = data.getAnimationControllers().get("controller");
        if (controller == null || tile.getWorld() == null) return;

        // GeckoLib 3 normally counts from the moment a chunk becomes visible. Rebase
        // every stone onto world time so unloading cannot restart its cycle.
        double worldTick = tile.getWorld().getTotalWorldTime();
        double desiredTick = positiveModulo(worldTick + phaseTicks, 60.0D);
        data.tick = tile.getWorld().getTotalWorldTime();
        controller.tickOffset = data.tick - desiredTick;
        modelProvider.setLivingAnimations(tile, uniqueId);
    }

    private static double positiveModulo(double value, double divisor) {
        double result = value % divisor;
        return result < 0.0D ? result + divisor : result;
    }

    public void render(TileRiftonomicon tile, double x, double y, double z, float yaw,
                       int packedLight, float partialTicks, boolean emissive,
                       ItemPortalRune.PortalPoint target, ResourceLocation emissiveTexture) {
        GeoModel model = modelProvider.getModel(modelProvider.getModelLocation(tile));
        setCrystalColor(target == null ? 0 : target.dimension);
        float[] previousLight = RenderSafety.captureLightmap();
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x, y, z);
            GlStateManager.rotate(yaw, 0.0F, 1.0F, 0.0F);
            GlStateManager.translate(0.0F, -7.0F / 16.0F, 0.0F);
            if (emissive) {
                RenderSafety.setFullBrightLightmap();
                GlStateManager.disableLighting();
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(
                        GlStateManager.SourceFactor.SRC_ALPHA,
                        GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                        GlStateManager.SourceFactor.ONE,
                        GlStateManager.DestFactor.ZERO
                );
            } else {
                RenderSafety.setLightmapFromPacked(packedLight);
                GlStateManager.enableLighting();
            }
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            Minecraft.getMinecraft().getTextureManager().bindTexture(
                    emissive ? emissiveTexture : modelProvider.getTextureLocation(tile)
            );
            render(model, tile, partialTicks, 1.0F, 1.0F, 1.0F, 1.0F);
        } finally {
            GlStateManager.popMatrix();
            RenderSafety.restoreLightmap(previousLight);
            RenderSafety.resetGlState();
        }
    }

    @Override
    public void renderRecursively(BufferBuilder buffer, GeoBone bone, float red, float green, float blue, float alpha) {
        if (CRYSTAL_BONE.equals(bone.getName())) {
            IGeoRenderer.super.renderRecursively(buffer, bone, crystalRed, crystalGreen, crystalBlue, alpha);
        } else {
            IGeoRenderer.super.renderRecursively(buffer, bone, red, green, blue, alpha);
        }
    }

    private void setCrystalColor(int dimension) {
        int rgb;
        if (dimension == 0) {
            rgb = 0x9DFF63;
        } else if (dimension == -1) {
            rgb = 0xFF5A24;
        } else if (dimension == 1) {
            rgb = 0x7B72FF;
        } else {
            float hue = ((dimension * 0.61803398875F) % 1.0F + 1.0F) % 1.0F;
            rgb = Color.HSBtoRGB(hue, 0.62F, 1.0F) & 0xFFFFFF;
        }
        crystalRed = ((rgb >> 16) & 255) / 255.0F;
        crystalGreen = ((rgb >> 8) & 255) / 255.0F;
        crystalBlue = (rgb & 255) / 255.0F;
    }

    @Override
    public GeoModelProvider<TileRiftonomicon> getGeoModelProvider() {
        return modelProvider;
    }

    @Override
    public net.minecraft.util.ResourceLocation getTextureLocation(TileRiftonomicon instance) {
        return modelProvider.getTextureLocation(instance);
    }
}
