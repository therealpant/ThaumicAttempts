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
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.items.ItemInterceptorStone;
import therealpant.thaumicattempts.world.tile.TileRiftWell;

import javax.annotation.Nullable;

public class BlockRiftWell extends Block {
    public BlockRiftWell() {
        super(Material.ROCK);
        setRegistryName(ThaumicAttempts.MODID, "rift_well");
        setTranslationKey(ThaumicAttempts.MODID + ".rift_well");
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
        setHardness(8.0F);
        setResistance(40.0F);
        setSoundType(SoundType.STONE);
    }

    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Nullable @Override public TileEntity createTileEntity(World world, IBlockState state) { return new TileRiftWell(); }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.INVISIBLE; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }

    @Override
    public boolean canPlaceBlockAt(World world, BlockPos pos) {
        return super.canPlaceBlockAt(world, pos) && RiftWellStructure.canForm(world, pos);
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state,
                                EntityLivingBase placer, ItemStack stack) {
        if (!world.isRemote && !RiftWellStructure.form(world, pos)) {
            RiftWellStructure.dismantle(world, pos, true);
            if (placer instanceof EntityPlayer) ((EntityPlayer) placer).sendStatusMessage(
                    new TextComponentTranslation("thaumicattempts.rift_well.no_room"), true);
        }
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand, EnumFacing side,
                                    float hitX, float hitY, float hitZ) {
        if (world.isRemote) return true;
        ItemStack held = player.getHeldItem(hand);
        if (held.getItem() instanceof ItemInterceptorStone) {
            return ItemInterceptorStone.installInWell(world, pos, player, hand);
        }
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof TileRiftWell) {
            TileRiftWell well = (TileRiftWell) tile;
            // The action bar is single-line and clipped this information. Two chat
            // components preserve localization and remain readable on narrow GUIs.
            player.sendMessage(well.statusTextPrimary());
            player.sendMessage(well.statusTextSecondary());
        }
        return true;
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote) {
            TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof TileRiftWell) {
                ((TileRiftWell) tile).onControllerBroken();
                for (ItemStack stone : ((TileRiftWell) tile).takeInstalledStones()) {
                    InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), stone);
                }
            }
        }
        if (!world.isRemote && !RiftWellStructure.isDismantling()) {
            RiftWellStructure.dismantle(world, pos, false);
        }
        super.breakBlock(world, pos, state);
    }

    @Override public boolean hasComparatorInputOverride(IBlockState state) { return true; }
    @Override public int getComparatorInputOverride(IBlockState state, World world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        return tile instanceof TileRiftWell ? Math.min(15, (int) Math.ceil(((TileRiftWell) tile).getTension()*0.15D)) : 0;
    }
}
