package therealpant.thaumicattempts.world.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.world.tile.TileRiftExtractor;

import javax.annotation.Nullable;

public class BlockRiftExtractor extends Block {

    public static final PropertyEnum<Part> PART = PropertyEnum.create("part", Part.class);

    private static boolean placingParts = false;

    // высота в пикселях 45px => 2.8125 блока => 3 блока в мире (LOW/MID/TOP)
    private static final AxisAlignedBB AABB_LOW  = new AxisAlignedBB(0, 0, 0, 1, 1, 1);
    private static final AxisAlignedBB AABB_MID  = new AxisAlignedBB(0, 0, 0, 1, 1, 1);
    private static final AxisAlignedBB AABB_TOP  = new AxisAlignedBB(0, 0, 0, 1, 45.0 / 16.0 - 2.0, 1); // 0.8125

    private static final double SLOT_SPLIT_Y = 2.0; // 32px от низа базы => 2 блока

    public BlockRiftExtractor() {
        super(Material.ROCK);
        setLightOpacity(0);
        setHardness(3.0F);
        setResistance(10.0F);
        setTranslationKey(ThaumicAttempts.MODID + ".rift_extractor");
        setRegistryName(ThaumicAttempts.MODID, "rift_extractor");
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);

        setDefaultState(this.blockState.getBaseState().withProperty(PART, Part.LOW));
    }

    @Override
    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        super.onBlockAdded(world, pos, state);
        if (world.isRemote) return;

        if (placingParts) return;

        Part part = state.getValue(PART);
        if (part != Part.LOW) return;

        // если над базой не стоят наши части — создаём
        BlockPos mid = pos.up();
        BlockPos top = pos.up(2);

        if (world.getBlockState(mid).getBlock() != this) {
            placingParts = true;
            try {
                world.setBlockState(mid, state.withProperty(PART, Part.MID), 3);
            } finally {
                placingParts = false;
            }
        }

        if (world.getBlockState(top).getBlock() != this) {
            placingParts = true;
            try {
                world.setBlockState(top, state.withProperty(PART, Part.TOP), 3);
            } finally {
                placingParts = false;
            }
        }
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block blockIn, BlockPos fromPos) {
        super.neighborChanged(state, world, pos, blockIn, fromPos);
        if (world.isRemote) return;

        Part part = state.getValue(PART);

        if (part == Part.LOW) {
            // если MID/TOP исчезли — пересоздать
            BlockPos mid = pos.up();
            BlockPos top = pos.up(2);

            if (world.getBlockState(mid).getBlock() != this) {
                world.setBlockState(mid, state.withProperty(PART, Part.MID), 3);
            }
            if (world.getBlockState(top).getBlock() != this) {
                world.setBlockState(top, state.withProperty(PART, Part.TOP), 3);
            }
            return;
        }

        // MID/TOP должны иметь LOW под собой (на 1 или 2 блока ниже)
        BlockPos basePos = (part == Part.MID) ? pos.down() : pos.down(2);
        IBlockState baseState = world.getBlockState(basePos);

        if (baseState.getBlock() != this || baseState.getValue(PART) != Part.LOW) {
            // база пропала — убрать прокси
            world.setBlockToAir(pos);
        }
    }

    // ---------- placement / multiblock ----------
    @Override
    public boolean canPlaceBlockAt(World worldIn, BlockPos pos) {
        // нужно 3 блока свободных: pos, pos.up, pos.up(2)
        return super.canPlaceBlockAt(worldIn, pos)
                && worldIn.isAirBlock(pos.up())
                && worldIn.isAirBlock(pos.up(2));
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state,
                                EntityLivingBase placer, ItemStack stack) {
        if (world.isRemote) return;

        // ставим 3 части
        world.setBlockState(pos,     state.withProperty(PART, Part.LOW),  3);
        world.setBlockState(pos.up(), state.withProperty(PART, Part.MID), 3);
        world.setBlockState(pos.up(2), state.withProperty(PART, Part.TOP), 3);
    }

    // ---------- AABB / selection ----------
    private AxisAlignedBB getLocalAABB(Part part) {
        switch (part) {
            case MID: return AABB_MID;
            case TOP: return AABB_TOP;
            case LOW:
            default:  return AABB_LOW;
        }
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return getLocalAABB(state.getValue(PART));
    }

    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return getLocalAABB(state.getValue(PART));
    }

    // ---------- TE only in LOW ----------
    @Override
    public boolean hasTileEntity(IBlockState state) {
        return state.getValue(PART) == Part.LOW;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return state.getValue(PART) == Part.LOW ? new TileRiftExtractor() : null;
    }

    // ---------- render ----------
    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        // Рендерит GeckoLib renderer от TE на LOW; MID/TOP должны быть невидимыми прокси.
        return state.getValue(PART) == Part.LOW ? EnumBlockRenderType.INVISIBLE : EnumBlockRenderType.INVISIBLE;
    }

    // ---------- break ----------
    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        Part part = state.getValue(PART);

        // Дроп только с базы
        if (part == Part.LOW) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileRiftExtractor) {
                TileRiftExtractor extractor = (TileRiftExtractor) te;
                dropStack(world, pos, extractor.getCrownStack());
                dropStack(world, pos, extractor.getCoreStack());
            }

            // удалить прокси-блоки выше
            if (world.getBlockState(pos.up()).getBlock() == this) {
                world.setBlockToAir(pos.up());
            }
            if (world.getBlockState(pos.up(2)).getBlock() == this) {
                world.setBlockToAir(pos.up(2));
            }
        } else {
            // если ломают MID/TOP — перенаправим на базу
            BlockPos base = getBasePosFromPart(pos, part);
            IBlockState bs = world.getBlockState(base);
            if (bs.getBlock() == this && bs.getValue(PART) == Part.LOW) {
                world.destroyBlock(base, true);
            }
        }

        super.breakBlock(world, pos, state);
    }

    private void dropStack(World world, BlockPos pos, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), stack);
    }

    private BlockPos getBasePosFromPart(BlockPos pos, Part part) {
        if (part == Part.MID) return pos.down();
        if (part == Part.TOP) return pos.down(2);
        return pos;
    }

    // ---------- interaction ----------
    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {

        if (world.isRemote) return true;

        Part part = state.getValue(PART);
        BlockPos basePos = getBasePosFromPart(pos, part);

        TileEntity te = world.getTileEntity(basePos);
        if (!(te instanceof TileRiftExtractor)) return false;

        TileRiftExtractor extractor = (TileRiftExtractor) te;

        // localY относительно базы (0..~3)
        double localY = (pos.getY() + (double) hitY) - (double) basePos.getY();
        boolean upper = localY > SLOT_SPLIT_Y;

        ItemStack held = player.getHeldItem(hand);
        boolean emptyHand = held.isEmpty();

        ThaumicAttempts.LOGGER.debug("[RiftExtractor] click at {} base={} localY={} upper={} sneaking={} emptyHand={}",
                pos, basePos, localY, upper, player.isSneaking(), emptyHand);

        if (upper) {
            if (player.isSneaking() && emptyHand) {
                extractor.tryExtractCrown(player);
                return true;
            }
            if (!player.isSneaking()) {
                extractor.tryInsertCrown(player, hand);
                return true;
            }
            return true;
        }

        if (player.isSneaking() && emptyHand) {
            extractor.tryExtractCore(player);
            return true;
        }
        return true;
    }

    // ---------- transparency ----------
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public int getLightOpacity(IBlockState state, IBlockAccess world, BlockPos pos) { return 0; }
    @Override public boolean isTranslucent(IBlockState state) { return true; }
    @Override public boolean getUseNeighborBrightness(IBlockState state) { return true; }
    @Override public boolean doesSideBlockRendering(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing face) { return false; }
    @Override public float getAmbientOcclusionLightValue(IBlockState state) { return 1.0F; }

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

    // ---------- blockstate/meta ----------
    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, PART);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        if (meta == 1) return getDefaultState().withProperty(PART, Part.MID);
        if (meta == 2) return getDefaultState().withProperty(PART, Part.TOP);
        return getDefaultState().withProperty(PART, Part.LOW);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        Part part = state.getValue(PART);
        if (part == Part.MID) return 1;
        if (part == Part.TOP) return 2;
        return 0;
    }

    public enum Part implements IStringSerializable {
        LOW("low"),
        MID("mid"),
        TOP("top");

        private final String name;
        Part(String name) { this.name = name; }
        @Override public String getName() { return name; }
        @Override public String toString() { return name; }
    }
}
