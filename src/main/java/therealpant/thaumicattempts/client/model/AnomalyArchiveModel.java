package therealpant.thaumicattempts.client.model;

import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.processor.IBone;
import software.bernie.geckolib3.model.AnimatedGeoModel;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.world.tile.TileAnomalyArchive;

public class AnomalyArchiveModel extends AnimatedGeoModel<TileAnomalyArchive> {

    @Override
    public ResourceLocation getModelLocation(TileAnomalyArchive object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "geo/anomali_archiv.geo.json");
    }

    @Override
    public ResourceLocation getTextureLocation(TileAnomalyArchive object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/archiv.png");
    }

    @Override
    public ResourceLocation getAnimationFileLocation(TileAnomalyArchive object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "animations/anomali_archiv.animation.json");
    }

    @Override
    public void setLivingAnimations(TileAnomalyArchive tile, Integer uniqueId, AnimationEvent event) {
        super.setLivingAnimations(tile, uniqueId, event);
        for (int index = 0; index < TileAnomalyArchive.getPanelBoneCount(); index++) {
            int boneId = TileAnomalyArchive.getPanelBone(index);
            IBone bone = getAnimationProcessor().getBone("bone" + boneId);
            if (bone != null) {
                bone.setHidden(tile != null && tile.isBoneHidden(boneId), false);
            }
        }
    }
}
