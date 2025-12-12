// src/main/java/therealpant/thaumicattempts/client/model/ModelPillar.java
package therealpant.thaumicattempts.client.model;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.model.AnimatedGeoModel;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.tile.TilePillar;

public class ModelPillar extends AnimatedGeoModel<TilePillar> {

    private static final ResourceLocation MODEL =
            new ResourceLocation(ThaumicAttempts.MODID, "geo/pillar.geo.json");

    // три текстуры
    private static final ResourceLocation TEX_ARCANE =
            new ResourceLocation("thaumicattempts","textures/blocks/pillar.png"); // обычный
    private static final ResourceLocation TEX_ANCIENT =
            new ResourceLocation("thaumicattempts", "textures/blocks/pillar_ancient.png");
    private static final ResourceLocation TEX_ELDRITCH =
            new ResourceLocation("thaumicattempts", "textures/blocks/pillar_void.png");

    // id блоков Таумкрафта
    private static final ResourceLocation ID_ARCANE  =
            new ResourceLocation("thaumcraft", "infusion_pillar");
    private static final ResourceLocation ID_ANCIENT =
            new ResourceLocation("thaumcraft", "pillar_ancient");
    private static final ResourceLocation ID_ELDRITCH =
            new ResourceLocation("thaumcraft", "pillar_eldritch");

    @Override
    public ResourceLocation getModelLocation(TilePillar object) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureLocation(TilePillar tile) {
        if (tile == null) return TEX_ARCANE;

        World w = tile.getWorld();
        if (w == null) return TEX_ARCANE;

        IBlockState state = w.getBlockState(tile.getPos());
        Block b = state.getBlock();
        ResourceLocation id = b.getRegistryName();
        if (id == null) return TEX_ARCANE;

        if (ID_ANCIENT.equals(id)) {
            return TEX_ANCIENT;
        }
        if (ID_ELDRITCH.equals(id)) {
            return TEX_ELDRITCH; // твой перекрас
        }
        // по умолчанию – обычный инфузионный пилон
        return TEX_ARCANE;
    }

    @Override
    public ResourceLocation getAnimationFileLocation(TilePillar object) {
        return null; // без анимаций
    }

    @Override
    public void setLivingAnimations(TilePillar entity, Integer uniqueID, AnimationEvent customPredicate) {
        // анимаций нет – ничего не делаем
    }
}
