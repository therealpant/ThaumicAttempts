package therealpant.thaumicattempts.world.block;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.world.tile.TileStrangePedestal;

import javax.annotation.Nullable;

public class BlockStrangePedestal extends Block {
    private static final AxisAlignedBB BOUNDS =
            new AxisAlignedBB(0.0D, 0.0D, 0.0D,
                    1.0D, 1.0D, 1.0D);

    public BlockStrangePedestal() {
        super(Material.ROCK);
        setRegistryName(ThaumicAttempts.MODID, "strange_pedestal");
        setTranslationKey(ThaumicAttempts.MODID + ".strange_pedestal");
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
        setHardness(8.0F);
        setResistance(30.0F);
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
        return new TileStrangePedestal();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        TileEntity rawTile = world.getTileEntity(pos);
        if (!(rawTile instanceof TileStrangePedestal)) return false;
        TileStrangePedestal tile = (TileStrangePedestal) rawTile;
        ItemStack held = player.getHeldItem(hand);

        if (tile.getHeldItem().isEmpty()) {
            if (held.isEmpty()
                    || held.getItem() != therealpant.thaumicattempts.init.ModBlocksItems.RIFT_AMETIST) {
                return false;
            }
            if (!world.isRemote && tile.insertAmethyst(held)) {
                if (!player.capabilities.isCreativeMode) held.shrink(1);
            }
            return true;
        }

        // Let held items (notably Thaumcraft's seal placer) process the click.
        if (!held.isEmpty()) return false;
        // Do not let a failed main-hand item interaction fall through to an
        // empty offhand and accidentally extract the amethyst.
        if (hand == EnumHand.OFF_HAND
                && !player.getHeldItem(EnumHand.MAIN_HAND).isEmpty()) {
            return true;
        }
        if (!world.isRemote) {
            ItemStack extracted = tile.removeAmethyst();
            if (!extracted.isEmpty()
                    && !player.inventory.addItemStackToInventory(extracted)) {
                player.dropItem(extracted, false);
            }
        }
        return true;
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity rawTile = world.getTileEntity(pos);
        if (rawTile instanceof TileStrangePedestal) {
            ItemStack held = ((TileStrangePedestal) rawTile).getHeldItem();
            if (!held.isEmpty()) {
                InventoryHelper.spawnItemStack(
                        world, pos.getX(), pos.getY(), pos.getZ(), held.copy());
            }
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public boolean canProvidePower(IBlockState state) {
        return true;
    }

    @Override
    public int getWeakPower(IBlockState blockState, IBlockAccess blockAccess,
                            BlockPos pos, EnumFacing side) {
        TileEntity tile = blockAccess.getTileEntity(pos);
        return tile instanceof TileStrangePedestal
                ? ((TileStrangePedestal) tile).getRedstonePower()
                : 0;
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.INVISIBLE;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return BOUNDS;
    }

    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return BOUNDS;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT_MIPPED;
    }
}
