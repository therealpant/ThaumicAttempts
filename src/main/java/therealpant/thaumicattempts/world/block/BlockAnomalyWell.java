package therealpant.thaumicattempts.world.block;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.init.TABlocks;
import therealpant.thaumicattempts.world.tile.TileAnomalyWell;
import therealpant.thaumicattempts.world.tile.TileAnomalyWellCollision;

import javax.annotation.Nullable;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

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
    private static final AxisAlignedBB[] COLLISION_WALLS = {NORTH, SOUTH, WEST, EAST};
    private static final List<BlockPos> COLLISION_PART_OFFSETS = createCollisionPartOffsets();

    public static AxisAlignedBB getModelBounds(BlockPos pos) {
        return MODEL_BOUNDS.offset(pos);
    }

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
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand, EnumFacing facing,
                                    float hitX, float hitY, float hitZ) {
        ItemStack held = player.getHeldItem(hand);
        if (held.isEmpty()) return false;
        if (world.isRemote) return true;
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileAnomalyWell)
                || !((TileAnomalyWell) tile).insertOne(held)) {
            return true;
        }
        if (!player.capabilities.isCreativeMode) {
            held.shrink(1);
        }
        world.playSound(null, pos, SoundEvents.ENTITY_ITEM_PICKUP,
                SoundCategory.BLOCKS, 0.45F, 0.85F + world.rand.nextFloat() * 0.2F);
        return true;
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

    public static void ensureCollisionParts(World world, BlockPos centre) {
        if (world == null || world.isRemote || TABlocks.ANOMALY_WELL_COLLISION == null) {
            return;
        }
        for (BlockPos offset : COLLISION_PART_OFFSETS) {
            BlockPos partPos = centre.add(offset);
            if (!world.isBlockLoaded(partPos)) {
                continue;
            }
            IBlockState state = world.getBlockState(partPos);
            if (state.getBlock() != TABlocks.ANOMALY_WELL_COLLISION) {
                if (!world.isAirBlock(partPos)) {
                    continue;
                }
                world.setBlockState(partPos, TABlocks.ANOMALY_WELL_COLLISION.getDefaultState(), 3);
            }
            TileEntity tile = world.getTileEntity(partPos);
            if (tile instanceof TileAnomalyWellCollision) {
                ((TileAnomalyWellCollision) tile).setControllerPos(centre);
            }
        }
    }

    public static void removeCollisionParts(World world, BlockPos centre) {
        if (world == null || TABlocks.ANOMALY_WELL_COLLISION == null) {
            return;
        }
        for (BlockPos offset : COLLISION_PART_OFFSETS) {
            BlockPos partPos = centre.add(offset);
            if (!world.isBlockLoaded(partPos)
                    || world.getBlockState(partPos).getBlock() != TABlocks.ANOMALY_WELL_COLLISION) {
                continue;
            }
            TileEntity tile = world.getTileEntity(partPos);
            if (tile instanceof TileAnomalyWellCollision
                    && centre.equals(((TileAnomalyWellCollision) tile).getControllerPos())) {
                world.setBlockToAir(partPos);
            }
        }
    }

    public static void addCollisionBoxesForPart(BlockPos centre, BlockPos partPos,
                                                 AxisAlignedBB query,
                                                 List<AxisAlignedBB> collidingBoxes) {
        AxisAlignedBB cell = new AxisAlignedBB(partPos);
        for (AxisAlignedBB localWall : COLLISION_WALLS) {
            AxisAlignedBB wall = localWall.offset(centre);
            double minX = Math.max(cell.minX, wall.minX);
            double minY = Math.max(cell.minY, wall.minY);
            double minZ = Math.max(cell.minZ, wall.minZ);
            double maxX = Math.min(cell.maxX, wall.maxX);
            double maxY = Math.min(cell.maxY, wall.maxY);
            double maxZ = Math.min(cell.maxZ, wall.maxZ);
            if (maxX <= minX || maxY <= minY || maxZ <= minZ) {
                continue;
            }
            AxisAlignedBB clipped = new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ);
            if (query.intersects(clipped) && !collidingBoxes.contains(clipped)) {
                collidingBoxes.add(clipped);
            }
        }
    }

    private static List<BlockPos> createCollisionPartOffsets() {
        List<BlockPos> offsets = new ArrayList<>();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                AxisAlignedBB cell = new AxisAlignedBB(x, 0, z, x + 1, 1, z + 1);
                for (AxisAlignedBB wall : COLLISION_WALLS) {
                    if (cell.intersects(wall)) {
                        offsets.add(new BlockPos(x, 0, z));
                        break;
                    }
                }
            }
        }
        return Collections.unmodifiableList(offsets);
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        removeCollisionParts(world, pos);
        super.breakBlock(world, pos, state);
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
