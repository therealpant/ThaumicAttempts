package therealpant.thaumicattempts.golemnet.block;

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
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.client.gui.GuiHandler;
import therealpant.thaumicattempts.golemcraft.item.ItemResourceList;
import therealpant.thaumicattempts.golemnet.tile.TileResourceRequester;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.BlockRenderLayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;

public class BlockResourceRequester extends BlockHorizontal {

    public static final PropertyDirection FACING = BlockHorizontal.FACING;

    private static final AxisAlignedBB AABB = new AxisAlignedBB(0, 0, 0, 1, 12.0 / 16.0, 1);

    public BlockResourceRequester() {
        super(Material.WOOD);
        setTranslationKey(ThaumicAttempts.MODID + ".resource_requester");
        setRegistryName(ThaumicAttempts.MODID, "resource_requester");
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
        setHardness(2.0F);
        setResistance(5.0F);
        setSoundType(SoundType.WOOD);
        setDefaultState(this.blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta & 3));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex();
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer) {
        EnumFacing horizontal = (placer == null) ? EnumFacing.NORTH : placer.getHorizontalFacing().getOpposite();
        return getDefaultState().withProperty(FACING, horizontal);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote) return true;
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileResourceRequester)) return false;

        TileResourceRequester tile = (TileResourceRequester) te;
        ItemStack held = player.getHeldItem(hand);

        if (!held.isEmpty() && held.getItem() instanceof ItemResourceList) {
            ItemStack copy = held.copy();
            copy.setCount(1);
            if (tile.tryInsertPattern(copy)) {
                if (!player.capabilities.isCreativeMode) {
                    held.shrink(1);
                    if (held.getCount() <= 0) player.setHeldItem(hand, ItemStack.EMPTY);
                }
                return true;
            }
            return false;
        }

        if (held.isEmpty() && player.isSneaking()) {
            ItemStack extracted = tile.tryExtractPattern();
            if (!extracted.isEmpty()) {
                if (!player.inventory.addItemStackToInventory(extracted)) {
                    player.dropItem(extracted, false);
                }
                return true;
            }
        }

        player.openGui(ThaumicAttempts.INSTANCE, GuiHandler.GUI_RESOURCE_REQUESTER, world,
                pos.getX(), pos.getY(), pos.getZ());
        return true;
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        // Модель блока — только через GeoBlockRenderer
        return EnumBlockRenderType.INVISIBLE;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT_MIPPED;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
        return layer == BlockRenderLayer.CUTOUT_MIPPED;
    }


    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileResourceRequester) {
            TileResourceRequester tile = (TileResourceRequester) te;
            tile.dropContents();
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileResourceRequester();
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return AABB;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean getUseNeighborBrightness(IBlockState state) {
        return true;
    }
}