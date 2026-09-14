package therealpant.thaumicattempts.world.tile;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectContainer;
import thaumcraft.api.items.IGogglesDisplayExtended;
import therealpant.thaumicattempts.world.block.BlockInfusionMatrixUpgrade;

public class TileInfusionMatrixUpgrade extends TileEntity
        implements IAnimatable, IGogglesDisplayExtended, IAspectContainer {
    private final AnimationFactory factory = new AnimationFactory(this);

    @Override
    public void registerControllers(AnimationData data) {
        data.addAnimationController(new AnimationController<>(this, "upgrade_controller", 0, this::predicate));
    }

    private <E extends IAnimatable> PlayState predicate(AnimationEvent<E> event) {
        event.getController().setAnimation(new AnimationBuilder().addAnimation("upgrade", true));
        return PlayState.CONTINUE;
    }

    @Override
    public AnimationFactory getFactory() {
        return factory;
    }

    @Override
    public String[] getIGogglesText() {
        TileEntity matrix = getMatrix();
        return matrix instanceof IGogglesDisplayExtended
                ? ((IGogglesDisplayExtended) matrix).getIGogglesText()
                : new String[0];
    }

    @Override
    public AspectList getAspects() {
        IAspectContainer matrix = getAspectContainer();
        return matrix == null ? new AspectList() : matrix.getAspects();
    }

    @Override
    public void setAspects(AspectList aspects) {
        IAspectContainer matrix = getAspectContainer();
        if (matrix != null) matrix.setAspects(aspects);
    }

    @Override
    public boolean doesContainerAccept(Aspect aspect) {
        IAspectContainer matrix = getAspectContainer();
        return matrix != null && matrix.doesContainerAccept(aspect);
    }

    @Override
    public int addToContainer(Aspect aspect, int amount) {
        IAspectContainer matrix = getAspectContainer();
        return matrix == null ? amount : matrix.addToContainer(aspect, amount);
    }

    @Override
    public boolean takeFromContainer(Aspect aspect, int amount) {
        IAspectContainer matrix = getAspectContainer();
        return matrix != null && matrix.takeFromContainer(aspect, amount);
    }

    @Override
    @Deprecated
    public boolean takeFromContainer(AspectList aspects) {
        IAspectContainer matrix = getAspectContainer();
        return matrix != null && matrix.takeFromContainer(aspects);
    }

    @Override
    public boolean doesContainerContainAmount(Aspect aspect, int amount) {
        IAspectContainer matrix = getAspectContainer();
        return matrix != null && matrix.doesContainerContainAmount(aspect, amount);
    }

    @Override
    @Deprecated
    public boolean doesContainerContain(AspectList aspects) {
        IAspectContainer matrix = getAspectContainer();
        return matrix != null && matrix.doesContainerContain(aspects);
    }

    @Override
    public int containerContains(Aspect aspect) {
        IAspectContainer matrix = getAspectContainer();
        return matrix == null ? 0 : matrix.containerContains(aspect);
    }

    private IAspectContainer getAspectContainer() {
        TileEntity matrix = getMatrix();
        return matrix instanceof IAspectContainer ? (IAspectContainer) matrix : null;
    }

    private TileEntity getMatrix() {
        if (world == null) return null;
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockInfusionMatrixUpgrade)) return null;
        EnumFacing relativeFacing = state.getValue(BlockInfusionMatrixUpgrade.FACING);
        BlockPos matrixPos = pos.offset(relativeFacing.getOpposite());
        return world.getTileEntity(matrixPos);
    }
}
