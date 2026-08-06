package therealpant.thaumicattempts.world.block;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.world.tile.TileAnomalyWell;

import javax.annotation.Nullable;
import java.util.List;

public class BlockAnomalyWell extends Block {
    private static final AxisAlignedBB MODEL_BOUNDS =
            new AxisAlignedBB(-1.125D, 0.0D, -1.125D, 2.125D, 0.75D, 2.125D);
    private static final AxisAlignedBB NORTH =
            new AxisAlignedBB(-1.125D, 0.0D, -1.125D, 2.125D, 0.75D, -0.4375D);
    private static final AxisAlignedBB SOUTH =
            new AxisAlignedBB(-1.125D, 0.0D, 1.4375D, 2.125D, 0.75D, 2.125D);
    private static final AxisAlignedBB WEST =
            new AxisAlignedBB(-1.125D, 0.0D, -0.4375D, -0.4375D, 0.75D, 1.4375D);
    private static final AxisAlignedBB EAST =
            new AxisAlignedBB(1.4375D, 0.0D, -0.4375D, 2.125D, 0.75D, 1.4375D);

    public BlockAnomalyWell() {
        super(Material.ROCK);
        setRegistryName(ThaumicAttempts.MODID, "anomaly_well");
        setTranslationKey(ThaumicAttempts.MODID + ".anomaly_well");
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
        return new TileAnomalyWell();
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.INVISIBLE;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return MODEL_BOUNDS;
    }

    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return NULL_AABB;
    }

    public static void addExtendedCollisionBoxes(BlockPos pos, AxisAlignedBB query,
                                                  List<AxisAlignedBB> collidingBoxes) {
        addExtendedCollisionBox(pos, query, collidingBoxes, NORTH);
        addExtendedCollisionBox(pos, query, collidingBoxes, SOUTH);
        addExtendedCollisionBox(pos, query, collidingBoxes, WEST);
        addExtendedCollisionBox(pos, query, collidingBoxes, EAST);
    }

    private static void addExtendedCollisionBox(BlockPos pos, AxisAlignedBB query,
                                                List<AxisAlignedBB> collidingBoxes,
                                                AxisAlignedBB localBox) {
        AxisAlignedBB worldBox = localBox.offset(pos);
        if (query.intersects(worldBox) && !collidingBoxes.contains(worldBox)) {
            collidingBoxes.add(worldBox);
        }
    }

    @Nullable
    @Override
    public RayTraceResult collisionRayTrace(IBlockState state, World world, BlockPos pos,
                                            net.minecraft.util.math.Vec3d start,
                                            net.minecraft.util.math.Vec3d end) {
        return rayTrace(pos, start, end, MODEL_BOUNDS);
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

    @SideOnly(Side.CLIENT)
    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT_MIPPED;
    }
}
