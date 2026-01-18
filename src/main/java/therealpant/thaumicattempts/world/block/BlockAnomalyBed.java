package therealpant.thaumicattempts.world.block;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import thaumcraft.api.blocks.BlocksTC;
import therealpant.thaumicattempts.ThaumicAttempts;

public class BlockAnomalyBed extends Block {
    public static final PropertyEnum<BedState> BED_STATE = PropertyEnum.create("bed_state", BedState.class);
    private static final int SCAN_RADIUS = 2;
    private static final int SCAN_RADIUS_SQ = SCAN_RADIUS * SCAN_RADIUS;
    private static final int SCAN_MIN_Y = -1;
    private static final int SCAN_MAX_Y = 1;

    public BlockAnomalyBed() {
        super(Material.GROUND);
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
        setTranslationKey(ThaumicAttempts.MODID + ".anomaly_bed");
        setRegistryName(ThaumicAttempts.MODID, "ta_anomaly_bed");
        setSoundType(SoundType.GROUND);
        setHardness(0.6F);
        setResistance(2.0F);
        setTickRandomly(true);
        setDefaultState(blockState.getBaseState().withProperty(BED_STATE, BedState.NORMAL));
    }

    @Override
    public void randomTick(World worldIn, BlockPos pos, IBlockState state, java.util.Random rand) {
        updateBedState(worldIn, pos, state);
    }

    @Override
    public void neighborChanged(IBlockState state, World worldIn, BlockPos pos, Block blockIn, BlockPos fromPos) {
        updateBedState(worldIn, pos, state);
    }

    private void updateBedState(World world, BlockPos pos, IBlockState state) {
        if (world.isRemote) return;
        BedState next = scanForFluid(world, pos);
        if (state.getValue(BED_STATE) != next) {
            world.setBlockState(pos, state.withProperty(BED_STATE, next), 2);
        }
    }

    private BedState scanForFluid(World world, BlockPos pos) {
        boolean foundLight = false;
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                if ((dx * dx + dz * dz) > SCAN_RADIUS_SQ) continue;
                for (int dy = SCAN_MIN_Y; dy <= SCAN_MAX_Y; dy++) {
                    BlockPos checkPos = pos.add(dx, dy, dz);
                    Block block = world.getBlockState(checkPos).getBlock();
                    if (block == BlocksTC.liquidDeath) {
                        return BedState.DARK;
                    }
                    if (block == BlocksTC.purifyingFluid) {
                        foundLight = true;
                    }
                }
            }
        }
        return foundLight ? BedState.LIGHT : BedState.NORMAL;
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        if (meta < 0 || meta >= BedState.values().length) {
            return getDefaultState();
        }
        return getDefaultState().withProperty(BED_STATE, BedState.values()[meta]);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(BED_STATE).ordinal();
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, BED_STATE);
    }

    public enum BedState implements IStringSerializable {
        NORMAL,
        LIGHT,
        DARK;

        @Override
        public String getName() {
            return name().toLowerCase();
        }
    }
}
