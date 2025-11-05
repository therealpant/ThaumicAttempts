// src/main/java/therealpant/thaumicattempts/golemcraft/block/BlockArcaneCrafter.java
package therealpant.thaumicattempts.golemcraft.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.client.gui.GuiHandler;
import therealpant.thaumicattempts.golemcraft.tile.TileEntityArcaneCrafter;

import javax.annotation.Nullable;

public class BlockArcaneCrafter extends Block {

    private static final AxisAlignedBB AABB_LOW = new AxisAlignedBB(0, 0, 0, 1, 11.0/16.0, 1);

    public BlockArcaneCrafter() {
        super(Material.ROCK);
        setLightOpacity(0);
        setHardness(2.0F);
        setResistance(10.0F);
        setTranslationKey(ThaumicAttempts.MODID + ".arcane_crafter");
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
    }

    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public TileEntity createTileEntity(World w, IBlockState s) { return new TileEntityArcaneCrafter(); }

    @Override
    public boolean onBlockActivated(World w, BlockPos pos, IBlockState st,
                                    EntityPlayer p, EnumHand hand, EnumFacing side,
                                    float hitX, float hitY, float hitZ) {
        if (w.isRemote) return true;
        TileEntity te = w.getTileEntity(pos);
        if (te instanceof TileEntityArcaneCrafter) {
            p.openGui(ThaumicAttempts.INSTANCE, GuiHandler.GUI_GOLEM_CRAFTER, w, pos.getX(), pos.getY(), pos.getZ());
            return true;
        }
        return false;
    }

    @Override public void breakBlock(World w, BlockPos pos, IBlockState st) {
        super.breakBlock(w, pos, st);
        w.removeTileEntity(pos);
    }

    @Override public boolean isOpaqueCube(IBlockState s){ return false; }
    @Override public boolean isFullCube(IBlockState s){ return false; }
    @Override public EnumBlockRenderType getRenderType(IBlockState s){ return EnumBlockRenderType.MODEL; }
    @Override public float getAmbientOcclusionLightValue(IBlockState state){ return 1.0F; }

    @SideOnly(Side.CLIENT)
    @Override public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT_MIPPED;
    }
    @SideOnly(Side.CLIENT)
    @Override public boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
        return layer == BlockRenderLayer.CUTOUT_MIPPED;
    }
    @Override
    public boolean doesSideBlockRendering(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing face) {
        return false; // не гасим соседей
    }


    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return AABB_LOW;                    // контур выбора
    }

    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return AABB_LOW;                    // коллизия (во что упирается игрок)
    }

}
