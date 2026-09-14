package therealpant.thaumicattempts.world.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.init.TABlocks;
import therealpant.thaumicattempts.world.EntityAnomalyWellResult;
import therealpant.thaumicattempts.world.tile.TileAnomalyWellCollision;

import javax.annotation.Nullable;
import java.util.List;

public class BlockAnomalyWellCollision extends Block {
    public BlockAnomalyWellCollision() {
        super(Material.BARRIER);
        setRegistryName(ThaumicAttempts.MODID, "anomaly_well_collision");
        setTranslationKey(ThaumicAttempts.MODID + ".anomaly_well_collision");
        setBlockUnbreakable();
        setResistance(6000000.0F);
        setLightOpacity(0);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileAnomalyWellCollision();
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.INVISIBLE;
    }

    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return NULL_AABB;
    }

    @Override
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos,
                                      AxisAlignedBB entityBox, List<AxisAlignedBB> collidingBoxes,
                                      @Nullable Entity entity, boolean isActualState) {
        if (entity instanceof EntityAnomalyWellResult) {
            return;
        }
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileAnomalyWellCollision)) {
            return;
        }
        BlockPos controller = ((TileAnomalyWellCollision) tile).getControllerPos();
        if (controller == null || !world.isBlockLoaded(controller)
                || world.getBlockState(controller).getBlock() != TABlocks.ANOMALY_WELL) {
            return;
        }
        BlockAnomalyWell.addCollisionBoxesForPart(controller, pos, entityBox, collidingBoxes);
    }

    @Nullable
    @Override
    public RayTraceResult collisionRayTrace(IBlockState state, World world, BlockPos pos,
                                            Vec3d start, Vec3d end) {
        return null;
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
}
