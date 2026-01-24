package therealpant.thaumicattempts.world.item;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.world.World;

public class ItemMindFruitFood extends ItemFood {
    private final int warpWardDurationTicks;

    public ItemMindFruitFood(int amount, float saturation, int warpWardDurationTicks) {
        super(amount, saturation, false);
        this.warpWardDurationTicks = warpWardDurationTicks;
    }

    @Override
    protected void onFoodEaten(ItemStack stack, World worldIn, EntityPlayer player) {
        if (!worldIn.isRemote) {
            Potion warpWard = Potion.getPotionFromResourceLocation("thaumcraft:warpWard");
            if (warpWard != null) {
                player.addPotionEffect(new PotionEffect(warpWard, warpWardDurationTicks, 0));
            }
        }

        super.onFoodEaten(stack, worldIn, player);
    }
}
