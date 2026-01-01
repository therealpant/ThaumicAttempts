package therealpant.thaumicattempts.golemnet.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import thaumcraft.api.blocks.BlocksTC;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.golemnet.tile.TileMirrorManager;
import therealpant.thaumicattempts.init.TABlocks;

public class BlockMirrorManagerCore extends Block {
    private static final String DUST_ID = "thaumcraft:salis_mundus";

    public BlockMirrorManagerCore() {
        super(Material.ROCK);
        setLightOpacity(0);
        setHardness(2.5F);
        setResistance(12.0F);
        setTranslationKey(ThaumicAttempts.MODID + ".mirror_manager_core");
        setRegistryName(ThaumicAttempts.MODID, "mirror_manager_core");
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
    }

    @Override
    public net.minecraft.util.EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.MODEL;
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
    public int getLightOpacity(IBlockState state, net.minecraft.world.IBlockAccess world, BlockPos pos) {
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
    public boolean doesSideBlockRendering(IBlockState state, net.minecraft.world.IBlockAccess world,
                                          BlockPos pos, EnumFacing face) {
        return false;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public net.minecraft.util.BlockRenderLayer getRenderLayer() {
        return net.minecraft.util.BlockRenderLayer.CUTOUT_MIPPED;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canRenderInLayer(IBlockState state, net.minecraft.util.BlockRenderLayer layer) {
        return layer == net.minecraft.util.BlockRenderLayer.CUTOUT_MIPPED;
    }

    @Override
    public float getAmbientOcclusionLightValue(IBlockState state) {
        return 1.0F;
    }

}