package therealpant.thaumicattempts.world.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.blocks.world.taint.ITaintBlock;
import thaumcraft.common.blocks.world.taint.TaintHelper;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.init.ModBlocksItems;
import therealpant.thaumicattempts.util.TADrops;
import therealpant.thaumicattempts.world.EntityFluxAnomalyBurst;
import therealpant.thaumicattempts.world.tile.AnomalyLinkedTile;
import therealpant.thaumicattempts.world.tile.TileAnomalyStone;

import javax.annotation.Nullable;

public class BlockAnomalyStone extends Block implements ITaintBlock {
    private static final AxisAlignedBB AABB = new AxisAlignedBB(0, 0, 0, 1, 1, 1);
    public static final PropertyEnum<HarvestState> HARVEST_STATE =
            PropertyEnum.create("harvest_state", HarvestState.class);
    public static final long REGROW_TICKS = 6L * 60L * 20L;

    public BlockAnomalyStone() {
        super(Material.ROCK);
        setLightOpacity(0);
        setHardness(40.5F);
        setResistance(200.0F);
        setTranslationKey(ThaumicAttempts.MODID + ".anomaly_stone");
        setRegistryName(ThaumicAttempts.MODID, "anomaly_stone");
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
        setTickRandomly(true);
        setDefaultState(this.blockState.getBaseState().withProperty(HARVEST_STATE, HarvestState.READY));
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileAnomalyStone createTileEntity(World world, IBlockState state) {
        return new TileAnomalyStone();
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess worldIn, BlockPos pos) {
        return AABB;
    }

    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState blockState, IBlockAccess worldIn, BlockPos pos) {
        return AABB;
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.INVISIBLE;
    }

    @Override
    public void getDrops(net.minecraft.util.NonNullList<net.minecraft.item.ItemStack> drops,
                         net.minecraft.world.IBlockAccess world,
                         net.minecraft.util.math.BlockPos pos,
                         net.minecraft.block.state.IBlockState state,
                         int fortune) {
        drops.clear();

        net.minecraft.world.World w = (net.minecraft.world.World) world;
        TADrops.addRiftDropFixed(drops, w, ModBlocksItems.RIFT_STONE);
    }

    @Override
    public boolean removedByPlayer(IBlockState state, World world, BlockPos pos,
                                   EntityPlayer player, boolean willHarvest) {
        if (player.isCreative() || state.getValue(HARVEST_STATE) == HarvestState.COLLECTED) {
            return super.removedByPlayer(state, world, pos, player, willHarvest);
        }

        if (!world.isRemote) {
            int normalReward = TADrops.rollCount(world.rand);
            int firstHarvestReward = (normalReward * 3 + 1) / 2;
            spawnAsEntity(world, pos, new ItemStack(ModBlocksItems.RIFT_STONE, firstHarvestReward));

            TileEntity tileEntity = world.getTileEntity(pos);
            if (tileEntity instanceof TileAnomalyStone) {
                ((TileAnomalyStone) tileEntity).startRegrowth(world.getTotalWorldTime() + REGROW_TICKS);
            }
            world.setBlockState(pos, state.withProperty(HARVEST_STATE, HarvestState.COLLECTED), 3);
            world.playEvent(2001, pos, Block.getStateId(state));

            ItemStack held = player.getHeldItemMainhand();
            if (!held.isEmpty()) {
                held.damageItem(1, player);
            }
        }
        return false;
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(
                HARVEST_STATE,
                (meta & 1) == 1 ? HarvestState.COLLECTED : HarvestState.READY
        );
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(HARVEST_STATE) == HarvestState.COLLECTED ? 1 : 0;
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, HARVEST_STATE);
    }

    public enum HarvestState implements IStringSerializable {
        READY,
        COLLECTED;

        @Override
        public String getName() {
            return name().toLowerCase();
        }
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
    public void randomTick(World world, BlockPos pos, IBlockState state, java.util.Random rand) {
        updateTick(world, pos, state, rand);
        if (world.isRemote) return;
        if (world.getBlockState(pos).getBlock() != this) return;

        // Ресурс привязан к флюкс-аномалии, а не к логике таинта.
        EntityFluxAnomalyBurst anomaly = resolveAnomaly(world, pos);
        if (anomaly == null || !anomaly.isResourceBlock(this)) {
            return;
        }

        if (!anomaly.isResourcesBootstrapped()) return;

        TileAnomalyStone tile = world.getTileEntity(pos) instanceof TileAnomalyStone
                ? (TileAnomalyStone) world.getTileEntity(pos)
                : null;
        if (tile == null) return;
        int cooldown = tile.getReproduceCooldown();
        if (cooldown > 0) {
            tile.setReproduceCooldown(cooldown - 1);
            return;
        }
        if (rand.nextInt(10) != 0) return;
        anomaly.requestExtraResource(this);
        tile.setReproduceCooldown(40 + rand.nextInt(80));
    }

    @Override
    public void updateTick(World world, BlockPos pos, IBlockState state, java.util.Random rand) {
        if (world.isRemote) return;
        if (rand.nextInt(10) != 0) return;
        if (!TaintHelper.isNearTaintSeed(world, pos)) {
            die(world, pos, state);
        }
    }

    @Override
    public void die(World world, BlockPos pos, IBlockState state) {
        if (world.isRemote) return;
        world.setBlockState(pos, BlocksTC.stonePorous.getDefaultState(), 2);
    }

    private EntityFluxAnomalyBurst resolveAnomaly(World world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof AnomalyLinkedTile)) {
            return null;
        }
        AnomalyLinkedTile linked = (AnomalyLinkedTile) tile;
        return FluxResourceHelper.findAnomaly(world, linked.getAnomalyId(), linked.getSeedPos());
    }
}
