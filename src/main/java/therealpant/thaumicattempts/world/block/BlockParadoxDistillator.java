package therealpant.thaumicattempts.world.block;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
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
import therealpant.thaumicattempts.init.TABlocks;
import therealpant.thaumicattempts.world.item.ParadoxumContainer;
import therealpant.thaumicattempts.world.tile.TileParadoxDistillator;
import therealpant.thaumicattempts.world.tile.TileParadoxDistillatorPort;

import javax.annotation.Nullable;

public class BlockParadoxDistillator extends Block {
    private static final AxisAlignedBB SELECTION_BOUNDS =
            new AxisAlignedBB(-1.0D, 0.0D, -1.0D, 2.0D, 1.875D, 2.0D);

    public BlockParadoxDistillator() {
        super(Material.ROCK);
        setRegistryName(ThaumicAttempts.MODID, "paradox_distillator");
        setTranslationKey(ThaumicAttempts.MODID + ".paradox_distillator");
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
        setHardness(6.0F);
        setResistance(30.0F);
        setSoundType(SoundType.STONE);
        setLightOpacity(0);
    }

    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Nullable @Override public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileParadoxDistillator();
    }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.INVISIBLE;
    }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return SELECTION_BOUNDS;
    }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }

    @Override
    public boolean canPlaceBlockAt(World world, BlockPos pos) {
        return super.canPlaceBlockAt(world, pos) && world.isAirBlock(pos.up());
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state,
                                EntityLivingBase placer, ItemStack stack) {
        if (!world.isRemote) ensureOutputPort(world, pos);
    }

    public static void ensureOutputPort(World world, BlockPos controllerPos) {
        if (world == null || world.isRemote || TABlocks.PARADOX_DISTILLATOR_PORT == null) return;
        BlockPos portPos = controllerPos.up();
        IBlockState state = world.getBlockState(portPos);
        if (state.getBlock() != TABlocks.PARADOX_DISTILLATOR_PORT) {
            if (!world.isAirBlock(portPos)) return;
            world.setBlockState(portPos,
                    TABlocks.PARADOX_DISTILLATOR_PORT.getDefaultState(), 3);
        }
        TileEntity tile = world.getTileEntity(portPos);
        if (tile instanceof TileParadoxDistillatorPort) {
            ((TileParadoxDistillatorPort) tile).setControllerPos(controllerPos);
        }
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand, EnumFacing facing,
                                    float hitX, float hitY, float hitZ) {
        TileEntity raw = world.getTileEntity(pos);
        if (!(raw instanceof TileParadoxDistillator)) return false;
        TileParadoxDistillator tile = (TileParadoxDistillator) raw;
        ItemStack held = player.getHeldItem(hand);

        if (ParadoxumContainer.isContainer(held)) {
            if (!world.isRemote && tile.insertContainer(held)
                    && !player.capabilities.isCreativeMode) held.shrink(1);
            return true;
        }
        if (!held.isEmpty()) return false;
        if (hand == EnumHand.OFF_HAND
                && !player.getHeldItem(EnumHand.MAIN_HAND).isEmpty()) return true;
        if (!world.isRemote) {
            ItemStack extracted = tile.removeLastContainer();
            if (!extracted.isEmpty() && !player.inventory.addItemStackToInventory(extracted)) {
                player.dropItem(extracted, false);
            }
        }
        return true;
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity raw = world.getTileEntity(pos);
        if (raw instanceof TileParadoxDistillator) {
            TileParadoxDistillator tile = (TileParadoxDistillator) raw;
            for (int slot = 0; slot < TileParadoxDistillator.SLOT_COUNT; slot++) {
                ItemStack stack = tile.getInput(slot);
                if (!stack.isEmpty()) InventoryHelper.spawnItemStack(
                        world, pos.getX(), pos.getY(), pos.getZ(), stack.copy());
            }
            if (!world.isRemote) tile.getPedestalAutomation().dropAllTransfers();
        }
        BlockPos portPos = pos.up();
        if (!world.isRemote && world.getBlockState(portPos).getBlock()
                == TABlocks.PARADOX_DISTILLATOR_PORT) {
            TileEntity port = world.getTileEntity(portPos);
            if (port instanceof TileParadoxDistillatorPort) world.setBlockToAir(portPos);
        }
        super.breakBlock(world, pos, state);
    }

    @SideOnly(Side.CLIENT)
    @Override public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT_MIPPED; }
}
