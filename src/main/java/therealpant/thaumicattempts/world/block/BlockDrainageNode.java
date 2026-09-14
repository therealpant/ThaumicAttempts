package therealpant.thaumicattempts.world.block;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.world.item.ParadoxumContainer;
import therealpant.thaumicattempts.world.tile.TileDrainageNode;

import javax.annotation.Nullable;

public class BlockDrainageNode extends Block {
    private static final AxisAlignedBB SELECTION_BOUNDS = FULL_BLOCK_AABB;

    public BlockDrainageNode() {
        super(Material.ROCK);
        setRegistryName(ThaumicAttempts.MODID, "drainage_node");
        setTranslationKey(ThaumicAttempts.MODID + ".drainage_node");
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
        setHardness(6.0F);
        setResistance(30.0F);
        setSoundType(SoundType.STONE);
        setLightOpacity(0);
    }

    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Nullable @Override public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileDrainageNode();
    }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.INVISIBLE;
    }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return SELECTION_BOUNDS;
    }
    @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return SELECTION_BOUNDS;
    }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }

    @Override public boolean canPlaceBlockAt(World world,BlockPos pos) {
        return super.canPlaceBlockAt(world,pos) && DrainageNodeStructure.canForm(world,pos);
    }

    @Override public void onBlockPlacedBy(World world,BlockPos pos,IBlockState state,
                                          EntityLivingBase placer,ItemStack stack) {
        if (!world.isRemote && !DrainageNodeStructure.form(world,pos)) world.destroyBlock(pos,true);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand, EnumFacing facing,
                                    float hitX, float hitY, float hitZ) {
        TileEntity raw = world.getTileEntity(pos);
        if (!(raw instanceof TileDrainageNode)) return false;
        TileDrainageNode tile = (TileDrainageNode) raw;
        ItemStack held = player.getHeldItem(hand);
        if (player.isSneaking() && held.isEmpty()) {
            if (!world.isRemote) player.sendStatusMessage(tile.statusText(), true);
            return true;
        }

        if (tile.getHeldItem().isEmpty()) {
            if (!TileDrainageNode.accepts(held)) return false;
            if (!world.isRemote && tile.insertContainer(held)
                    && !player.capabilities.isCreativeMode) {
                held.shrink(1);
            }
            return true;
        }
        if (!held.isEmpty()) return false;
        if (hand == EnumHand.OFF_HAND
                && !player.getHeldItem(EnumHand.MAIN_HAND).isEmpty()) return true;
        if (!world.isRemote) {
            ItemStack extracted = tile.removeContainer();
            if (!extracted.isEmpty() && !player.inventory.addItemStackToInventory(extracted)) {
                player.dropItem(extracted, false);
            }
        }
        return true;
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity raw = world.getTileEntity(pos);
        if (raw instanceof TileDrainageNode) {
            TileDrainageNode tile = (TileDrainageNode) raw;
            ItemStack held = tile.getHeldItem();
            if (!held.isEmpty()) InventoryHelper.spawnItemStack(
                    world, pos.getX(), pos.getY(), pos.getZ(), held.copy());
            if (!world.isRemote) tile.getPedestalAutomation().dropAllTransfers();
        }
        if (!world.isRemote && !DrainageNodeStructure.isDismantling())
            DrainageNodeStructure.removeUpperParts(world,pos);
        super.breakBlock(world, pos, state);
    }

    @SideOnly(Side.CLIENT)
    @Override public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT_MIPPED; }
    @Override public boolean hasComparatorInputOverride(IBlockState state) { return true; }
    @Override public int getComparatorInputOverride(IBlockState state, World world, BlockPos pos) {
        TileEntity raw = world.getTileEntity(pos);
        return raw instanceof TileDrainageNode ? Math.min(15,(int)Math.ceil(((TileDrainageNode)raw).getTension()*0.15D)) : 0;
    }
}
