package therealpant.thaumicattempts.world.tile;

import net.minecraft.tileentity.TileEntity;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;

/** Static animatable host for the core-only GeckoLib model. */
public class TileRiftonomiconCorePlaceholder extends TileEntity implements IAnimatable {
    private final AnimationFactory factory = new AnimationFactory(this);

    @Override
    public void registerControllers(AnimationData data) {
        // Intentionally static: the research icon does not need the core animation.
    }

    @Override
    public AnimationFactory getFactory() {
        return factory;
    }
}
