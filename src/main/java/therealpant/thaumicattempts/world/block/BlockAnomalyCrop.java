package therealpant.thaumicattempts.world.block;

import net.minecraft.block.BlockBush;
import net.minecraft.block.SoundType;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.EnumPlantType;
import net.minecraftforge.common.util.Constants;
import thaumcraft.api.aura.AuraHelper;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.golemcraft.ModBlocksItems;
import therealpant.thaumicattempts.init.TABlocks;
import therealpant.thaumicattempts.world.tile.TileAnomalyCrop;

import javax.annotation.Nullable;

public class BlockAnomalyCrop extends BlockBush {
    public static final PropertyInteger AGE = PropertyInteger.create("age", 0, 4);
    public static final PropertyEnum<CropVariant> VARIANT =
            PropertyEnum.create("variant", CropVariant.class);
    private static final int MAX_AGE = 4;
    private static final int GROWTH_CHANCE = 5;
    private static final float VIS_REQUIRED = 8.0F;
    private static final float MATURE_VIS_THRESHOLD = 300.0F;
    private static final float SEED_DROP_CHANCE_NONE = 0.10F;
    private static final float SEED_DROP_CHANCE_DOUBLE = 0.05F;

    private static final AxisAlignedBB[] AABB_BY_AGE = new AxisAlignedBB[] {
            new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.25D, 1.0D),
            new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.35D, 1.0D),
            new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 1.0D),
            new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.7D, 1.0D),
            new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.85D, 1.0D)
    };

    public BlockAnomalyCrop() {
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
        setTranslationKey(ThaumicAttempts.MODID + ".anomaly_crop");
        setRegistryName(ThaumicAttempts.MODID, "ta_anomaly_crop");
        setSoundType(SoundType.PLANT);
        setTickRandomly(true);
        setDefaultState(blockState.getBaseState()
                .withProperty(AGE, 0)
                .withProperty(VARIANT, CropVariant.DARK));
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return AABB_BY_AGE[state.getValue(AGE)];
    }

    @Override
    protected boolean canSustainBush(IBlockState state) {
        return state.getBlock() == TABlocks.ANOMALY_BED;
    }

    @Override
    public boolean canBlockStay(World worldIn, BlockPos pos, IBlockState state) {
        return isValidBedState(worldIn, pos, state);
    }

    @Override
    public void onBlockAdded(World worldIn, BlockPos pos, IBlockState state) {
        super.onBlockAdded(worldIn, pos, state);
        resetMinVis(worldIn, pos);
        initializeVariant(worldIn, pos, state);
    }

    @Override
    public void onBlockPlacedBy(World worldIn, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(worldIn, pos, state, placer, stack);
        resetMinVis(worldIn, pos);
        initializeVariant(worldIn, pos, state);
    }

    @Override
    public void breakBlock(World worldIn, BlockPos pos, IBlockState state) {
        resetMinVis(worldIn, pos);
        super.breakBlock(worldIn, pos, state);
    }

    @Override
    public void randomTick(World worldIn, BlockPos pos, IBlockState state, java.util.Random rand) {
        if (worldIn.isRemote) return;
        if (!canBlockStay(worldIn, pos, state)) {
            worldIn.setBlockToAir(pos);
            return;
        }
        int age = state.getValue(AGE);
        if (age >= MAX_AGE) return;
        if (rand.nextInt(GROWTH_CHANCE) != 0) return;

        CropVariant variant = state.getValue(VARIANT);
        if (variant == CropVariant.LIGHT) {
            float vis = AuraHelper.getVis(worldIn, pos);
            if (vis < VIS_REQUIRED) return;
            TileAnomalyCrop tile = getTile(worldIn, pos);
            if (tile != null) {
                float currentMin = tile.getMinVisDuringGrowth();
                tile.setMinVisDuringGrowth(Math.min(currentMin, vis));
            }
            AuraHelper.drainVis(worldIn, pos, VIS_REQUIRED, false);
            worldIn.setBlockState(pos, state.withProperty(AGE, age + 1), Constants.BlockFlags.DEFAULT);
            return;
        }

        if (variant == CropVariant.DARK) {
            worldIn.setBlockState(pos, state.withProperty(AGE, age + 1), Constants.BlockFlags.DEFAULT);
        }
    }

    @Override
    public void neighborChanged(IBlockState state, World worldIn, BlockPos pos, net.minecraft.block.Block blockIn,
                                BlockPos fromPos) {
        super.neighborChanged(state, worldIn, pos, blockIn, fromPos);
        if (worldIn.isRemote) return;
        if (!isValidBedState(worldIn, pos, state)) {
            // TA FIX: break without drops when bed form becomes incompatible.
            worldIn.setBlockToAir(pos);
        }
    }

    @Override
    public void updateTick(World worldIn, BlockPos pos, IBlockState state, java.util.Random rand) {
        if (worldIn.isRemote) return;
        if (!isValidBedState(worldIn, pos, state)) {
            // TA FIX: break without drops when bed form becomes incompatible.
            worldIn.setBlockToAir(pos);
        }
    }

    @Override
    public void getDrops(net.minecraft.util.NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos,
                         IBlockState state, int fortune) {
        drops.clear();
        int age = state.getValue(AGE);
        if (age < MAX_AGE) {
            if (world instanceof World) {
                addSeedDrops(drops, ((World) world).rand);
            } else {
                drops.add(new ItemStack(ModBlocksItems.ANOMALY_SEEDS));
            }
            return;
        }

        CropVariant variant = state.getValue(VARIANT);
        if (variant == CropVariant.DARK) {
            drops.add(new ItemStack(ModBlocksItems.TAINTED_MIND_FRUIT));
        } else if (variant == CropVariant.LIGHT) {
            float vis = world instanceof World ? AuraHelper.getVis((World) world, pos) : 0.0F;
            // TA FIX: decide fruit type by current aura vis at drop time.
            if (vis >= MATURE_VIS_THRESHOLD) {
                drops.add(new ItemStack(ModBlocksItems.MATURE_MIND_FRUIT));
            } else {
                drops.add(new ItemStack(ModBlocksItems.MIND_FRUIT));
            }
        }

        if (world instanceof World) {
            addSeedDrops(drops, ((World) world).rand);
        }
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        int age = Math.max(0, Math.min(MAX_AGE, meta & 7));
        CropVariant variant = (meta & 8) != 0 ? CropVariant.LIGHT : CropVariant.DARK;
        return getDefaultState()
                .withProperty(AGE, age)
                .withProperty(VARIANT, variant);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        int meta = state.getValue(AGE);
        if (state.getValue(VARIANT) == CropVariant.LIGHT) {
            meta |= 8;
        }
        return meta;
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, AGE, VARIANT);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileAnomalyCrop();
    }

    @Override
    public EnumPlantType getPlantType(IBlockAccess world, BlockPos pos) {
        return EnumPlantType.Crop;
    }

    @Override
    public IBlockState getPlant(IBlockAccess world, BlockPos pos) {
        return getDefaultState();
    }

    private boolean isValidBedState(World world, BlockPos pos, IBlockState state) {
        IBlockState soil = world.getBlockState(pos.down());
        if (!(soil.getBlock() instanceof BlockAnomalyBed)) {
            return false;
        }
        BlockAnomalyBed.BedState soilState = soil.getValue(BlockAnomalyBed.BED_STATE);
        CropVariant soilVariant = soilState == BlockAnomalyBed.BedState.LIGHT ? CropVariant.LIGHT : CropVariant.DARK;
        return soilVariant == state.getValue(VARIANT);
    }

    @Nullable
    private TileAnomalyCrop getTile(World world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        return tile instanceof TileAnomalyCrop ? (TileAnomalyCrop) tile : null;
    }

    private void resetMinVis(World world, BlockPos pos) {
        if (world.isRemote) return;
        TileAnomalyCrop tile = getTile(world, pos);
        if (tile == null) {
            tile = new TileAnomalyCrop();
            world.setTileEntity(pos, tile);
        }
        tile.resetMinVis();
    }
    private void initializeVariant(World world, BlockPos pos, IBlockState state) {
        if (world.isRemote) return;
        CropVariant variant = resolveVariantFromBed(world, pos);
        if (state.getValue(VARIANT) != variant) {
            world.setBlockState(pos, state.withProperty(VARIANT, variant), Constants.BlockFlags.DEFAULT);
        }
    }

    private CropVariant resolveVariantFromBed(World world, BlockPos pos) {
        IBlockState soil = world.getBlockState(pos.down());
        if (soil.getBlock() instanceof BlockAnomalyBed) {
            BlockAnomalyBed.BedState soilState = soil.getValue(BlockAnomalyBed.BED_STATE);
            return soilState == BlockAnomalyBed.BedState.LIGHT ? CropVariant.LIGHT : CropVariant.DARK;
        }
        return CropVariant.DARK;
    }

    private void addSeedDrops(net.minecraft.util.NonNullList<ItemStack> drops, java.util.Random rand) {
        float roll = rand.nextFloat();
        int count;
        if (roll < SEED_DROP_CHANCE_NONE) {
            count = 0;
        } else if (roll >= 1.0F - SEED_DROP_CHANCE_DOUBLE) {
            count = 2;
        } else {
            count = 1;
        }
        if (count > 0) {
            // TA FIX: seed distribution 85% (1), 10% (0), 5% (2).
            drops.add(new ItemStack(ModBlocksItems.ANOMALY_SEEDS, count));
        }
    }

    public enum CropVariant implements net.minecraft.util.IStringSerializable {
        LIGHT,
        DARK;

        @Override
        public String getName() {
            return name().toLowerCase();
        }
    }
}
