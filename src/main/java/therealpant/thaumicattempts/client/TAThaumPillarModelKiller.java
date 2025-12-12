package therealpant.thaumicattempts.client;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.RegistrySimple;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import therealpant.thaumicattempts.ThaumicAttempts;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * Глушит таумовскую OBJ-модель pillar.obj, чтобы блок столба рендерился
 * ТОЛЬКО через наш TilePillar + RenderPillar (TESR / Geo).
 */
@Mod.EventBusSubscriber(modid = ThaumicAttempts.MODID, value = Side.CLIENT)
public final class TAThaumPillarModelKiller {

    private TAThaumPillarModelKiller() {}

    @SubscribeEvent
    public static void onModelBake(ModelBakeEvent event) {
        Object regObj = event.getModelRegistry();

        if (regObj instanceof RegistrySimple) {
            RegistrySimple registry = (RegistrySimple) regObj;

            for (Object keyObj : registry.getKeys()) {
                if (!(keyObj instanceof ModelResourceLocation)) continue;
                ModelResourceLocation mrl = (ModelResourceLocation) keyObj;

                if ("thaumcraft".equals(mrl.getNamespace())
                        && "pillar.obj".equals(mrl.getPath())) {

                    registry.putObject(mrl, EmptyModel.INSTANCE);
                    System.out.println("[ThaumicAttempts] Replaced model " + mrl + " with EmptyModel (pillar.obj killed)");
                }
            }
        } else {
            System.out.println("[ThaumicAttempts] Unexpected model registry type: "
                    + (regObj == null ? "null" : regObj.getClass().getName()));
        }
    }

    /**
     * Полностью пустая baked-модель – не отдаёт ни одного квадрата.
     * Блок визуально рисуется только TESR'ом (RenderPillar).
     */
    private static final class EmptyModel implements IBakedModel {

        private static final EmptyModel INSTANCE = new EmptyModel();

        @Nonnull
        @Override
        public List<BakedQuad> getQuads(@Nullable IBlockState state,
                                        @Nullable EnumFacing side,
                                        long rand) {
            return Collections.emptyList();
        }

        @Override
        public boolean isAmbientOcclusion() {
            return false;
        }

        @Override
        public boolean isGui3d() {
            return false;
        }

        @Override
        public boolean isBuiltInRenderer() {
            // false — это про "встроенный" item renderer, TESR всё равно будет вызываться
            return false;
        }

        @Nonnull
        @Override
        public TextureAtlasSprite getParticleTexture() {
            return Minecraft.getMinecraft().getTextureMapBlocks().getMissingSprite();
        }

        @Nonnull
        @Override
        public ItemCameraTransforms getItemCameraTransforms() {
            return ItemCameraTransforms.DEFAULT;
        }

        @Nonnull
        @Override
        public ItemOverrideList getOverrides() {
            return ItemOverrideList.NONE;
        }
    }
}
