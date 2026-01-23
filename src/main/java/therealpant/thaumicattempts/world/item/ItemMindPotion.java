package therealpant.thaumicattempts.world.item;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.init.TAPotionTypes;

public class ItemMindPotion extends Item {
    public ItemMindPotion() {
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
        setTranslationKey(ThaumicAttempts.MODID + ".mind_potion");
        setRegistryName(ThaumicAttempts.MODID, "mind_potion");
        setMaxStackSize(1);
    }

    @Override
    public int getMaxItemUseDuration(ItemStack stack) {
        return 32;
    }

    @Override
    public EnumAction getItemUseAction(ItemStack stack) {
        return EnumAction.DRINK;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World worldIn, EntityPlayer playerIn, EnumHand handIn) {
        playerIn.setActiveHand(handIn);
        return new ActionResult<>(EnumActionResult.SUCCESS, playerIn.getHeldItem(handIn));
    }

    @Override
    public ItemStack onItemUseFinish(ItemStack stack, World worldIn, EntityLivingBase entityLiving) {
        if (!worldIn.isRemote) {
            entityLiving.addPotionEffect(new PotionEffect(
                    MobEffects.REGENERATION, TAPotionTypes.REGENERATION_DURATION, 0));
            Potion warpWard = Potion.getPotionFromResourceLocation("thaumcraft:warpWard");
            if (warpWard != null) {
                entityLiving.addPotionEffect(new PotionEffect(
                        warpWard, TAPotionTypes.WARP_WARD_DURATION, 0));
            }
        }

        if (!(entityLiving instanceof EntityPlayer)) {
            return stack;
        }

        EntityPlayer player = (EntityPlayer) entityLiving;
        if (!player.capabilities.isCreativeMode) {
            stack.shrink(1);
            if (stack.isEmpty()) {
                return new ItemStack(Items.GLASS_BOTTLE);
            }
            player.inventory.addItemStackToInventory(new ItemStack(Items.GLASS_BOTTLE));
        }
        return stack;
    }
}
