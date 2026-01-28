package therealpant.thaumicattempts.common.crafting.infusion;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.crafting.InfusionRecipe;
import therealpant.thaumicattempts.items.ItemTAGem;
import therealpant.thaumicattempts.util.TAGemInlayUtil;

/**
 * Infusion recipe for extracting a gem from armor.
 */
public class RecipeExtractGem extends InfusionRecipe {
    private static final ItemStack DUMMY_CENTRAL = new ItemStack(Items.BOOK);

    public RecipeExtractGem(String research, int instability, AspectList aspects, Object... components) {
        super(research, new ItemStack(Items.IRON_CHESTPLATE), instability, aspects,
                Ingredient.fromStacks(DUMMY_CENTRAL), components);
    }

    @Override
    public boolean matches(List<ItemStack> input, ItemStack central, World world, EntityPlayer player) {
        if (central == null || central.isEmpty()) return false;
        if (!(central.getItem() instanceof ItemArmor)) return false;
        if (!TAGemInlayUtil.hasGem(central)) return false;
        if (!super.matches(input, DUMMY_CENTRAL, world, player)) return false;
        return TAGemInlayUtil.getGemId(central) != null;
    }

    @Override
    public ItemStack  getRecipeOutput(EntityPlayer player, ItemStack central, List<ItemStack> comps) {
        ItemStack out = central.copy();
        out.setCount(1);
        ResourceLocation id = TAGemInlayUtil.getGemId(central);
        int tier = TAGemInlayUtil.getTier(central);
        int dmg = TAGemInlayUtil.getDamage(central);
        TAGemInlayUtil.clearGem(out);
        giveGemToPlayer(player, id, tier, dmg);
        return out;
    }

    private static void giveGemToPlayer(EntityPlayer player, ResourceLocation id, int tier, int dmg) {
        if (player == null || player.world == null || player.world.isRemote) return;
        if (id == null) return;
        ItemStack gem = ItemTAGem.makeGem(id, tier, dmg);
        boolean inserted = player.inventory.addItemStackToInventory(gem);
        if (!inserted) {
            player.entityDropItem(gem, 0.5F);
        }
    }
}