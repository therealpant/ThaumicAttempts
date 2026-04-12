package therealpant.thaumicattempts.golemnet.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.golemnet.tile.TileRevisionPiedestal;

import javax.annotation.Nullable;

public class BlockRevisionPiedestal extends Block {

    private static final AxisAlignedBB AABB = new AxisAlignedBB(0, 0, 0, 1, 1, 1);

    public BlockRevisionPiedestal() {
        super(Material.ROCK);
        setLightOpacity(0);
        setHardness(4.0F);
        setResistance(10.0F);
        setTranslationKey(ThaumicAttempts.MODID + ".revision_piedestal");
        setRegistryName(ThaumicAttempts.MODID, "revision_piedestal");
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileRevisionPiedestal();
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.INVISIBLE;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!player.isSneaking()) {
            return false;
        }
        if (world.isRemote) {
            return true;
        }

        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileRevisionPiedestal)) {
            return false;
        }

        ((TileRevisionPiedestal) te).toggleActive();
        return true;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return AABB;
    }

    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
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
    public int getLightOpacity(IBlockState state, IBlockAccess world, BlockPos pos) {
        return 0;
    }

    @Override
    public boolean isTranslucent(IBlockState state) {
        return true;
    }

    @Override
    public boolean getUseNeighborBrightness(IBlockState state) {
        return true;
    }

    @Override
    public boolean doesSideBlockRendering(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing face) {
        return false;
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
    public float getAmbientOcclusionLightValue(IBlockState state) {
        return 1.0F;
    }
}
