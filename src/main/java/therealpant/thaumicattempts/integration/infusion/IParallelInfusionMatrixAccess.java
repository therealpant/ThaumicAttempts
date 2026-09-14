package therealpant.thaumicattempts.integration.infusion;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import thaumcraft.api.aspects.AspectList;

import java.util.ArrayList;

/** Accessors injected into Thaumcraft's infusion matrix by the TA coremod. */
public interface IParallelInfusionMatrixAccess {
    AspectList ta$getRecipeEssentia();
    ArrayList<ItemStack> ta$getRecipeIngredients();
    ArrayList<BlockPos> ta$getPedestals();
    float ta$getStability();

    int ta$getCountDelay();
    void ta$setStability(float stability);
    void ta$setCheckSurroundings(boolean check);
}
