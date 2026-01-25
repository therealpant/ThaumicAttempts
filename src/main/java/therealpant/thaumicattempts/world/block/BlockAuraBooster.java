package therealpant.thaumicattempts.world.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.world.tile.TileAuraBooster;

import javax.annotation.Nullable;

public class BlockAuraBooster extends Block {

    public BlockAuraBooster() {
        super(Material.ROCK);
        setHardness(3.0F);
        setResistance(10.0F);
        setTranslationKey(ThaumicAttempts.MODID + ".ta_aura_booster");
        setRegistryName(ThaumicAttempts.MODID, "ta_aura_booster");
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileAuraBooster();
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileAuraBooster) {
            ItemStack stack = ((TileAuraBooster) te).getPearlStack();
            if (!stack.isEmpty()) {
                InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return true;
        }
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileAuraBooster)) {
            return false;
        }
        TileAuraBooster booster = (TileAuraBooster) te;
        if (player.isSneaking()) {
            return booster.tryExtractPearl(player);
        }
        return booster.tryInsertPearl(player, hand);
    }
}
