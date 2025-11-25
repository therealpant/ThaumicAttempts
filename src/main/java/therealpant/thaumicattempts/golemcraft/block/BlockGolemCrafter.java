package therealpant.thaumicattempts.golemcraft.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
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
import therealpant.thaumicattempts.golemcraft.tile.TileEntityGolemCrafter;
import net.minecraft.init.Blocks;

import javax.annotation.Nullable;

/**
 * Простой блок-крафтер:
 *  - TE: TileEntityGolemCrafter
 *  - ПКМ открывает GUI (поставь свой GUI_ID в openGui)
 */
public class BlockGolemCrafter extends Block {

    public static final int GUI_ID = 51; // <-- синхронизируй с твоим IGuiHandler

    public BlockGolemCrafter() {
        super(Material.ROCK);
        setLightOpacity(0);
        setHardness(2.0F);
        setResistance(10.0F);
        setTranslationKey(ThaumicAttempts.MODID + ".golem_crafter");
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
        // если используешь свою вкладку — раскомментируй:
        // setCreativeTab(therealpant.thaumicattempts.init.TACreativeTab.INSTANCE);
    }

    private static final AxisAlignedBB AABB_LOW = new AxisAlignedBB(0, 0, 0, 1, 11.0/16.0, 1);
    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return AABB_LOW;                    // контур выбора
    }
    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return AABB_LOW;                    // коллизия (во что упирается игрок)
    }

    @Override public boolean hasTileEntity(IBlockState state) { return true; }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileEntityGolemCrafter();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand, EnumFacing side,
                                    float hitX, float hitY, float hitZ) {
        if (world.isRemote) return true;

        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntityGolemCrafter) {
            player.openGui(ThaumicAttempts.INSTANCE, GuiHandler.GUI_GOLEM_CRAFTER, world, pos.getX(), pos.getY(), pos.getZ());
            return true;
        }
        return false;
    }

    @Override
    public void breakBlock(World worldIn, BlockPos pos, IBlockState state) {
        TileEntity te = worldIn.getTileEntity(pos);
        if (te instanceof TileEntityGolemCrafter) {
            ((TileEntityGolemCrafter) te).dropContents();
        }
        super.breakBlock(worldIn, pos, state);
        worldIn.removeTileEntity(pos);
    }
    // Общие оверрайды:
    @Override public boolean isOpaqueCube(IBlockState s) { return false; }
    @Override public boolean isFullCube(IBlockState s)   { return false; }

    // Skylight/освещение
    @Override public int  getLightOpacity(IBlockState s, IBlockAccess w, BlockPos p) { return 0; }
    @Override public boolean isTranslucent(IBlockState s) { return true; }
    @Override public boolean getUseNeighborBrightness(IBlockState s) { return true; }

    // Чтобы не считать грань «перекрывающей»
    @Override
    public boolean doesSideBlockRendering(IBlockState s, IBlockAccess w, BlockPos p, EnumFacing f) { return false; }

    // Рендер-слой
    @SideOnly(Side.CLIENT)
    @Override public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT_MIPPED; }
    @SideOnly(Side.CLIENT)
    @Override public boolean canRenderInLayer(IBlockState s, BlockRenderLayer layer) {
        return layer == BlockRenderLayer.CUTOUT_MIPPED;
    }

    // (опционально, если есть)
    @Override public float getAmbientOcclusionLightValue(IBlockState s) { return 1.0F; }


}
