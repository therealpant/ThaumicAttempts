package therealpant.thaumicattempts.world.block;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.items.ItemInterceptorStone;
import therealpant.thaumicattempts.init.ModBlocksItems;
import therealpant.thaumicattempts.world.tile.TileAnomalousMonolith;

import javax.annotation.Nullable;

public class BlockAnomalousMonolith extends Block {
    private static final AxisAlignedBB BOUNDS =
            new AxisAlignedBB(0.125D, -1.25D, 0.125D, 0.875D, 3.8125D, 0.875D);

    public BlockAnomalousMonolith() {
        super(Material.ROCK);
        setRegistryName(ThaumicAttempts.MODID, "anomalous_monolith");
        setTranslationKey(ThaumicAttempts.MODID + ".anomalous_monolith");
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
        setBlockUnbreakable();
        setResistance(40.0F);
        setSoundType(SoundType.STONE);
        setLightOpacity(0);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileAnomalousMonolith();
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.INVISIBLE;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return BOUNDS;
    }

    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return BOUNDS;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT_MIPPED;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand, EnumFacing side,
                                    float hitX, float hitY, float hitZ) {
        ItemStack held = player.getHeldItem(hand);
        TileEntity tile = world.getTileEntity(pos);
        if (player.isSneaking() && held.isEmpty()
                && tile instanceof TileAnomalousMonolith
                && ((TileAnomalousMonolith) tile).hasInterceptorStone()) {
            if (!world.isRemote
                    && ((TileAnomalousMonolith) tile).removeInterceptorStone()) {
                ItemStack removed = new ItemStack(ModBlocksItems.INTERCEPTOR_STONE);
                if (!player.addItemStackToInventory(removed)) player.dropItem(removed, false);
                player.sendStatusMessage(new net.minecraft.util.text.TextComponentTranslation(
                        "thaumicattempts.interceptor_stone.monolith_removed"), true);
            }
            return true;
        }
        if (held.getItem() instanceof ItemInterceptorStone) {
            return ItemInterceptorStone.installInMonolith(world, pos, player, hand);
        }
        return false;
    }
}
