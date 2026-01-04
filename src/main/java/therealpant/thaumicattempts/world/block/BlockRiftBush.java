package therealpant.thaumicattempts.world.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBush;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import thaumcraft.common.blocks.world.taint.ITaintBlock;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.blocks.world.taint.TaintHelper;
import therealpant.thaumicattempts.ThaumicAttempts;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * Высокий куст (2 блока), аналог ванильной розы.
 */
public class BlockRiftBush extends BlockBush implements ITaintBlock {

    public static final PropertyEnum<BlockHalf> HALF = PropertyEnum.create("half", BlockHalf.class);

    public BlockRiftBush() {
        super(Material.VINE);
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
        setTranslationKey(ThaumicAttempts.MODID + ".rift_bush");
        setRegistryName(ThaumicAttempts.MODID, "rift_bush");
        setSoundType(SoundType.PLANT);
        setHardness(0.0F);
        setResistance(0.0F);
        setDefaultState(this.blockState.getBaseState().withProperty(HALF, BlockHalf.LOWER));
    }

    @Override
    protected boolean canSustainBush(IBlockState state) {
        return state.getBlock() == Blocks.GRASS
                || state.getBlock() == Blocks.DIRT
                || state.getBlock() == BlocksTC.taintSoil
                || super.canSustainBush(state);
    }

    @Override
    public boolean canPlaceBlockAt(World worldIn, BlockPos pos) {
        return super.canPlaceBlockAt(worldIn, pos) && worldIn.isAirBlock(pos.up());
    }

    @Override
    public void onBlockPlacedBy(World worldIn, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        worldIn.setBlockState(pos, state.withProperty(HALF, BlockHalf.LOWER), 2);
        worldIn.setBlockState(pos.up(), state.withProperty(HALF, BlockHalf.UPPER), 2);
    }

    @Override
    public void neighborChanged(IBlockState state, World worldIn, BlockPos pos, Block blockIn, BlockPos fromPos) {
        if (!canBlockStay(worldIn, pos, state)) {
            removeHalf(worldIn, pos, state);
        }
    }

    private void removeHalf(World worldIn, BlockPos pos, IBlockState state) {
        boolean upper = state.getValue(HALF) == BlockHalf.UPPER;
        BlockPos otherPos = upper ? pos.down() : pos.up();
        IBlockState other = worldIn.getBlockState(otherPos);
        if (other.getBlock() == this && other.getValue(HALF) != state.getValue(HALF)) {
            worldIn.setBlockToAir(otherPos);
        }
        worldIn.setBlockToAir(pos);
    }

    public boolean canBlockStay(World worldIn, BlockPos pos, IBlockState state) {
        if (state.getValue(HALF) == BlockHalf.UPPER) {
            IBlockState below = worldIn.getBlockState(pos.down());
            return below.getBlock() == this && below.getValue(HALF) == BlockHalf.LOWER;
        }
        IBlockState above = worldIn.getBlockState(pos.up());
        return above.getBlock() == this && above.getValue(HALF) == BlockHalf.UPPER && super.canPlaceBlockAt(worldIn, pos);
    }

    @Override
    public void onBlockHarvested(World worldIn, BlockPos pos, IBlockState state, EntityPlayer player) {
        if (state.getValue(HALF) == BlockHalf.UPPER) {
            BlockPos downPos = pos.down();
            IBlockState downState = worldIn.getBlockState(downPos);
            if (downState.getBlock() == this && downState.getValue(HALF) == BlockHalf.LOWER) {
                if (player.isCreative()) {
                    worldIn.setBlockToAir(downPos);
                } else {
                    worldIn.destroyBlock(downPos, true);
                }
            }
        } else {
            BlockPos upPos = pos.up();
            IBlockState upState = worldIn.getBlockState(upPos);
            if (upState.getBlock() == this && upState.getValue(HALF) == BlockHalf.UPPER) {
                worldIn.setBlockToAir(upPos);
            }
        }
        super.onBlockHarvested(worldIn, pos, state, player);
    }

    @Override
    public List<ItemStack> getDrops(IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        if (state.getValue(HALF) == BlockHalf.UPPER) {
            return Collections.emptyList();
        }
        return Collections.singletonList(new ItemStack(Item.getItemFromBlock(this)));
    }

    @Override
    public ItemStack getItem(World worldIn, BlockPos pos, IBlockState state) {
        return new ItemStack(this);
    }

    @Override
    public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos, EntityPlayer player) {
        return new ItemStack(this);
    }

    @Override
    public IBlockState getStateForPlacement(World worldIn, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer) {
        return getDefaultState().withProperty(HALF, BlockHalf.LOWER);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        BlockHalf half = (meta & 1) == 1 ? BlockHalf.UPPER : BlockHalf.LOWER;
        return getDefaultState().withProperty(HALF, half);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(HALF) == BlockHalf.UPPER ? 1 : 0;
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, HALF);
    }

    public enum BlockHalf implements IStringSerializable {
        LOWER,
        UPPER;

        @Override
        public String getName() {
            return name().toLowerCase();
        }
    }

    @Override
    public void randomTick(World world, BlockPos pos, IBlockState state, java.util.Random rand) {
        if (world.isRemote) return;

        // если рядом нет taint seed → увядаем
        BlockPos basePos = state.getValue(HALF) == BlockHalf.UPPER ? pos.down() : pos;
        if (!TaintHelper.isNearTaintSeed(world, basePos)) {
            die(world, pos, state);
            return;
        }
        if (state.getValue(HALF) == BlockHalf.UPPER) return;

        int count = FluxResourceHelper.countBlocks(world, basePos, this, 4);
        if (!FluxResourceHelper.shouldReproduce(rand, count, 18, 1.0 / 6.0)) return;

        BlockPos target = FluxResourceHelper.randomOffset(basePos, rand, 4, 2);
        if (target.getY() < 1 || target.getY() >= world.getHeight() - 1) return;
        if (!world.isBlockLoaded(target)) return;
        if (!canPlaceBlockAt(world, target)) return;

        IBlockState lower = getDefaultState().withProperty(HALF, BlockHalf.LOWER);
        IBlockState upper = getDefaultState().withProperty(HALF, BlockHalf.UPPER);

        world.setBlockState(target, lower, 3);
        world.setBlockState(target.up(), upper, 3);
        FluxResourceHelper.damageNearestSeed(world, target, 0.25f);
    }

    @Override
    public void die(World world, BlockPos pos, IBlockState state) {
        if (world.isRemote) return;

        // если это верхняя часть — работаем от нижней
        BlockPos basePos = state.getValue(HALF) == BlockHalf.UPPER ? pos.down() : pos;

        IBlockState base = world.getBlockState(basePos);
        if (base.getBlock() == this) {
            world.setBlockToAir(basePos);
        }

        IBlockState upper = world.getBlockState(basePos.up());
        if (upper.getBlock() == this) {
            world.setBlockToAir(basePos.up());
        }

        // ставим сухой куст
        world.setBlockState(basePos, net.minecraft.init.Blocks.DEADBUSH.getDefaultState(), 2);
    }
}