package therealpant.thaumicattempts.world.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.init.TABlocks;
import therealpant.thaumicattempts.world.tile.TileRiftStoneFurnace;
import therealpant.thaumicattempts.world.tile.TileRiftStoneFurnacePort;

import javax.annotation.Nullable;
import java.util.List;

public class BlockRiftStoneFurnace extends Block {
    public static final PropertyEnum<Part> PART = PropertyEnum.create("part", Part.class);

    private static final AxisAlignedBB FULL = new AxisAlignedBB(0, 0, 0, 1, 1, 1);
    private static final AxisAlignedBB CENTER_LOW = new AxisAlignedBB(0, 0, 0, 1, 0.5D, 1);
    private static final AxisAlignedBB TOP_NORTH = new AxisAlignedBB(0, 0, 0.5D, 1, 0.5D, 1);
    private static final AxisAlignedBB TOP_SOUTH = new AxisAlignedBB(0, 0, 0, 1, 0.5D, 0.5D);
    private static final AxisAlignedBB TOP_WEST = new AxisAlignedBB(0.5D, 0, 0, 1, 0.5D, 1);
    private static final AxisAlignedBB TOP_EAST = new AxisAlignedBB(0, 0, 0, 0.5D, 0.5D, 1);

    public BlockRiftStoneFurnace() {
        super(Material.ROCK);
        setHardness(45.0F);
        setResistance(200.0F);
        setLightOpacity(0);
        setTranslationKey(ThaumicAttempts.MODID + ".rift_stone_furnace");
        setRegistryName(ThaumicAttempts.MODID, "rift_stone_furnace");
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
        setDefaultState(this.blockState.getBaseState().withProperty(PART, Part.CENTER_LOW));
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        Part part = state.getValue(PART);
        return part == Part.CENTER_LOW || part == Part.TOP_CORNER;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        Part part = state.getValue(PART);
        if (part == Part.CENTER_LOW) {
            return new TileRiftStoneFurnace();
        }
        if (part == Part.TOP_CORNER) {
            return new TileRiftStoneFurnacePort();
        }
        return null;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess worldIn, BlockPos pos) {
        return getLocalAABB(state.getValue(PART), worldIn, pos);
    }

    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess worldIn, BlockPos pos) {
        return getLocalAABB(state.getValue(PART), worldIn, pos);
    }

    @Override
    public void addCollisionBoxToList(IBlockState state, World worldIn, BlockPos pos, AxisAlignedBB entityBox,
                                      List<AxisAlignedBB> collidingBoxes, @Nullable net.minecraft.entity.Entity entityIn,
                                      boolean isActualState) {
        addCollisionBoxToList(pos, entityBox, collidingBoxes, getLocalAABB(state.getValue(PART), worldIn, pos));
    }

    private static AxisAlignedBB getLocalAABB(Part part, IBlockAccess world, BlockPos pos) {
        switch (part) {
            case CENTER_LOW:
                return CENTER_LOW;
            case TOP_NORTH:
            case TOP_SOUTH:
            case TOP_WEST:
            case TOP_EAST:
                return getTopRimAABB(part, world, pos);
            case LOWER_FULL:
            case TOP_CORNER:
            default:
                return FULL;
        }
    }

    private static AxisAlignedBB getTopRimAABB(Part part, IBlockAccess world, BlockPos pos) {
        BlockPos center = findCenterLow(world, pos);
        if (center != null) {
            int dx = pos.getX() - center.getX();
            int dz = pos.getZ() - center.getZ();
            if (dx < 0) {
                return TOP_WEST;
            }
            if (dx > 0) {
                return TOP_EAST;
            }
            if (dz < 0) {
                return TOP_NORTH;
            }
            if (dz > 0) {
                return TOP_SOUTH;
            }
        }

        switch (part) {
            case TOP_NORTH:
                return TOP_NORTH;
            case TOP_SOUTH:
                return TOP_SOUTH;
            case TOP_WEST:
                return TOP_WEST;
            case TOP_EAST:
            default:
                return TOP_EAST;
        }
    }

    @Nullable
    private static BlockPos findCenterLow(IBlockAccess world, BlockPos pos) {
        if (world == null || pos == null) {
            return null;
        }

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos candidate = pos.add(dx, -1, dz);
                IBlockState state = world.getBlockState(candidate);
                if (state.getBlock() == TABlocks.RIFT_STONE_FURNACE
                        && state.getValue(PART) == Part.CENTER_LOW) {
                    return candidate;
                }
            }
        }
        return null;
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.INVISIBLE;
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

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!RiftStoneFurnaceStructureHelper.isRestoring()) {
            RiftStoneFurnaceStructureHelper.restoreFromPart(world, pos, state.getValue(PART));
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, PART);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        Part[] parts = Part.values();
        if (meta < 0 || meta >= parts.length) {
            return getDefaultState();
        }
        return getDefaultState().withProperty(PART, parts[meta]);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(PART).ordinal();
    }

    public enum Part implements IStringSerializable {
        LOWER_FULL("lower_full"),
        CENTER_LOW("center_low"),
        TOP_CORNER("top_corner"),
        TOP_NORTH("top_north"),
        TOP_WEST("top_west"),
        TOP_EAST("top_east"),
        TOP_SOUTH("top_south");

        private final String name;

        Part(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
