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
import therealpant.thaumicattempts.world.tile.TileRiftonomicon;

import javax.annotation.Nullable;
import java.util.List;

public class BlockRiftonomicon extends Block {
    public static final PropertyEnum<Part> PART = PropertyEnum.create("part", Part.class);

    private static final AxisAlignedBB FULL = new AxisAlignedBB(0, 0, 0, 1, 1, 1);
    private static final AxisAlignedBB BASE_EDGE = new AxisAlignedBB(0, 0, 0, 1, 0.35D, 1);
    private static final AxisAlignedBB BASE_INNER = new AxisAlignedBB(0, 0, 0, 1, 0.55D, 1);
    private static final AxisAlignedBB SECOND_LAYER_COLUMN = new AxisAlignedBB(0.125D, 0, 0.125D, 0.875D, 1.5D, 0.875D);
    private static final AxisAlignedBB TOP_CRYSTAL = new AxisAlignedBB(0.25D, 0, 0.25D, 0.75D, 1, 0.75D);

    public BlockRiftonomicon() {
        super(Material.ROCK);
        setHardness(45.0F);
        setResistance(200.0F);
        setLightOpacity(0);
        setTranslationKey(ThaumicAttempts.MODID + ".riftonomicon");
        setRegistryName(ThaumicAttempts.MODID, "riftonomicon");
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
        setDefaultState(this.blockState.getBaseState().withProperty(PART, Part.CORE));
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return state.getValue(PART) == Part.CORE;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return state.getValue(PART) == Part.CORE ? new TileRiftonomicon() : null;
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
        BlockPos center = findCenter(world, pos);
        if (center != null) {
            int dy = pos.getY() - center.getY();
            if (dy == 0) {
                return FULL;
            }
            if (dy == 1) {
                return SECOND_LAYER_COLUMN;
            }
            if (dy >= 2 && dy <= 4) {
                return TOP_CRYSTAL;
            }
        }

        switch (part) {
            case CORE:
            case CORNER:
                return FULL;
            case INNER:
                return BASE_INNER;
            case COLUMN:
                return SECOND_LAYER_COLUMN;
            case TOP:
                return TOP_CRYSTAL;
            case EDGE:
            default:
                return BASE_EDGE;
        }
    }

    @Nullable
    private static BlockPos findCenter(IBlockAccess world, BlockPos pos) {
        if (world == null || pos == null) {
            return null;
        }

        for (int dy = -4; dy <= 0; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos candidate = pos.add(dx, dy, dz);
                    IBlockState state = world.getBlockState(candidate);
                    if (state.getBlock() == TABlocks.RIFTONOMICON
                            && state.getValue(PART) == Part.CORE) {
                        return candidate.add(0, 0, 3);
                    }
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
        if (!RiftonomiconStructureHelper.isRestoring()) {
            RiftonomiconStructureHelper.restoreFromPart(world, pos);
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
        CORE("core"),
        BASE("base"),
        INNER("inner"),
        EDGE("edge"),
        CORNER("corner"),
        COLUMN("column"),
        TOP("top");

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
