package therealpant.thaumicattempts.world.item;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.capabilities.IPlayerWarp;

public class ItemTaintedMindFruitFood extends ItemFood {
    public ItemTaintedMindFruitFood(int amount, float saturation) {
        super(amount, saturation, false);
    }

    @Override
    protected void onFoodEaten(ItemStack stack, World worldIn, EntityPlayer player) {
        if (!worldIn.isRemote) {
            ThaumcraftApi.internalMethods.addWarpToPlayer(player, 2, IPlayerWarp.EnumWarpType.NORMAL);
        }

        super.onFoodEaten(stack, worldIn, player);
    }
}
