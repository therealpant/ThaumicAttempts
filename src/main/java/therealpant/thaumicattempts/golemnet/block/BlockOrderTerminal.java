// src/main/java/therealpant/thaumicattempts/golemnet/block/BlockOrderTerminal.java
package therealpant.thaumicattempts.golemnet.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.client.gui.GuiHandlerOrderTerminal;
import therealpant.thaumicattempts.golemnet.tile.TileOrderTerminal;
import therealpant.thaumicattempts.golemnet.tile.TileResourceRequester;

public class BlockOrderTerminal extends Block {

    public static final PropertyDirection FACING = BlockHorizontal.FACING;

    public BlockOrderTerminal() {
        super(Material.WOOD);
        setLightOpacity(0);
        setHardness(2.0F);
        setResistance(10.0F);
        setSoundType(SoundType.WOOD);
        setTranslationKey(ThaumicAttempts.MODID + ".order_terminal");
        setRegistryName(ThaumicAttempts.MODID, "order_terminal");
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);

        setDefaultState(this.blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    @Override protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta & 3));
    }

    @Override public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex();
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer, EnumHand hand) {
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state,
                                EntityLivingBase placer, ItemStack stack) {
        world.setBlockState(pos, state.withProperty(FACING, placer.getHorizontalFacing().getOpposite()), 2);
    }

    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public TileEntity createTileEntity(World world, IBlockState state) { return new TileOrderTerminal(); }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand, EnumFacing side,
                                    float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            player.openGui(ThaumicAttempts.INSTANCE, GuiHandlerOrderTerminal.GUI_ID, world, pos.getX(), pos.getY(), pos.getZ());
        }
        return true;
    }

    @Override public boolean isOpaqueCube(IBlockState s){ return false; }
    @Override public boolean isFullCube(IBlockState s){ return false; }
    @Override public EnumBlockRenderType getRenderType(IBlockState s){ return EnumBlockRenderType.MODEL; }

    @SideOnly(Side.CLIENT)
    @Override public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT_MIPPED; // не TRANSLUCENT
    }
    @SideOnly(Side.CLIENT)
    @Override public boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
        return layer == BlockRenderLayer.CUTOUT_MIPPED;
    }
    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileOrderTerminal) {
            ((TileOrderTerminal) te).dropContents();
        }
        super.breakBlock(world, pos, state);
    }
}
