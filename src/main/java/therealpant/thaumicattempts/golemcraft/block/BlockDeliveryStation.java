package therealpant.thaumicattempts.golemnet.block;

import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import thaumcraft.api.items.ItemsTC;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.golemcraft.item.ItemDeliveryPattern;
import therealpant.thaumicattempts.golemnet.link.DeliveryLinkHandler;
import therealpant.thaumicattempts.golemnet.tile.TileDeliveryStation;

import javax.annotation.Nullable;

public class BlockDeliveryStation extends BlockHorizontal {

    public static final PropertyDirection FACING = BlockHorizontal.FACING;
    private static final AxisAlignedBB AABB = new AxisAlignedBB(0, 0, 0, 1, 14.0 / 16.0, 1);

    public BlockDeliveryStation() {
        super(Material.WOOD);
        setTranslationKey(ThaumicAttempts.MODID + ".delivery_station");
        setRegistryName(ThaumicAttempts.MODID, "delivery_station");
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
        setHardness(2.0F);
        setResistance(5.0F);
        setSoundType(SoundType.WOOD);
        setDefaultState(this.blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta & 3));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex();
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer) {
        EnumFacing horizontal = (placer == null) ? EnumFacing.NORTH : placer.getHorizontalFacing().getOpposite();
        return getDefaultState().withProperty(FACING, horizontal);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        ItemStack held = player.getHeldItem(hand);
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileDeliveryStation)) return false;
        TileDeliveryStation tile = (TileDeliveryStation) te;

        if (world.isRemote) {
            return true;
        }

        // start or continue linking via golem bell
        if (player.isSneaking() && held.getItem() == ItemsTC.golemBell) {
            DeliveryLinkHandler.startLinking(player, world.provider.getDimension(), pos);
            player.sendStatusMessage(new TextComponentTranslation("thaumicattempts.link.start"), true);
            return true;
        }

        if (!held.isEmpty() && held.getItem() instanceof ItemDeliveryPattern) {
            if (tile.tryInsertPattern(held)) {
                if (!player.capabilities.isCreativeMode) {
                    held.shrink(1);
                }
                player.sendStatusMessage(new TextComponentTranslation("thaumicattempts.delivery_station.inserted_pattern"), true);
                return true;
            }
            return false;
        }

        // пустой рукой в приседе: извлекаем сначала предмет для клика, затем паттерн
        if (held.isEmpty() && player.isSneaking()) {
            ItemStack extracted = tile.tryExtractPayload();
            if (extracted.isEmpty()) {
                extracted = tile.tryExtractPattern();
            }
            if (!extracted.isEmpty()) {
                if (!player.inventory.addItemStackToInventory(extracted)) {
                    player.dropItem(extracted, false);
                }
                return true;
            }
        }

        // загрузить предмет для финального ПКМ
        if (!held.isEmpty() && tile.tryInsertPayload(held)) {
            if (!player.capabilities.isCreativeMode) {
                held.shrink(1);
            }
            player.sendStatusMessage(new TextComponentTranslation("thaumicattempts.delivery_station.inserted_payload"), true);
            return true;
        }

        return false;
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.MODEL;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return AABB;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) { return false; }

    @Override
    public boolean isFullCube(IBlockState state) { return false; }

    @Override
    public boolean hasTileEntity(IBlockState state) { return true; }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileDeliveryStation();
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileDeliveryStation) {
            ((TileDeliveryStation) te).dropContents();
        }
        super.breakBlock(world, pos, state);
    }
}