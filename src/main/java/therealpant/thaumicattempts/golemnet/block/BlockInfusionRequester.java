package therealpant.thaumicattempts.golemnet.block;

import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.client.gui.GuiHandler;
import therealpant.thaumicattempts.golemcraft.item.ItemInfusionPattern;
import therealpant.thaumicattempts.golemnet.tile.TileInfusionRequester;

import javax.annotation.Nullable;

/**
 * Заготовка блока для нового инфузионного реквестера.
 * Логика стадий пока реализуется в TileInfusionRequester.
 */
public class BlockInfusionRequester extends BlockHorizontal {

    public BlockInfusionRequester() {
        super(Material.ROCK);
        setHardness(2f);
        setResistance(5f);
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
        setSoundType(SoundType.STONE);
        setDefaultState(this.blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
        setRegistryName("thaumicattempts", "infusion_requester");
        setTranslationKey("thaumicattempts.infusion_requester");
    }

    private static final AxisAlignedBB AABB = new AxisAlignedBB(0, 0, 0, 1, 12.0 / 16.0, 1);

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileInfusionRequester();
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
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
    public IBlockState withRotation(IBlockState state, Rotation rot) {
        return state.withProperty(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public IBlockState withMirror(IBlockState state, Mirror mirror) {
        return state.withRotation(mirror.toRotation(state.getValue(FACING)));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileInfusionRequester)) return false;

        ItemStack held = player.getHeldItem(hand);
        TileInfusionRequester tile = (TileInfusionRequester) te;

        if (!world.isRemote) {
            if (te instanceof TileInfusionRequester) {
                ((TileInfusionRequester) te).setOwner(player);
            }
        }

        if (!world.isRemote) {
            if (player.isSneaking()) {
                ItemStack extracted = tile.tryExtractPattern();
                if (!extracted.isEmpty()) {
                    if (!player.inventory.addItemStackToInventory(extracted)) {
                        player.dropItem(extracted, false);
                    }
                    return true;
                }
            } else if (!held.isEmpty() && held.getItem() instanceof ItemInfusionPattern) {
                if (tile.tryInsertPattern(held)) {
                    held.shrink(1);
                    return true;
                }
            }

            player.openGui(ThaumicAttempts.INSTANCE, GuiHandler.GUI_INFUSION_REQUESTER,
                    world, pos.getX(), pos.getY(), pos.getZ());
        }

        return true;
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileInfusionRequester) {
            ((TileInfusionRequester) te).dropContents();
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
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