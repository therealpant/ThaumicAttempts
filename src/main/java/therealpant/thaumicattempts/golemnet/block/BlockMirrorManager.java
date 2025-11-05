// therealpant.thaumicattempts.golemnet.block.BlockMirrorManager.java
package therealpant.thaumicattempts.golemnet.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.golemnet.tile.TileMirrorManager;

import javax.annotation.Nullable;

public class BlockMirrorManager extends Block {
    public BlockMirrorManager() {
        super(Material.ROCK);
        setLightOpacity(0);
        setHardness(2.0F);
        setResistance(10.0F);
        setTranslationKey("mirror_manager");
        setRegistryName(ThaumicAttempts.MODID, "mirror_manager");
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
    }

    @Override public boolean hasTileEntity(IBlockState state) { return true; }

    @Override public TileEntity createTileEntity(World world, IBlockState state) { return new TileMirrorManager(); }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand, EnumFacing facing,
                                    float hitX, float hitY, float hitZ) {
        if (world.isRemote) return true;

        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof therealpant.thaumicattempts.golemnet.tile.TileMirrorManager)) return false;
        therealpant.thaumicattempts.golemnet.tile.TileMirrorManager mgr =
                (therealpant.thaumicattempts.golemnet.tile.TileMirrorManager) te;

        ItemStack held = player.getHeldItem(hand);

        // Снятие: Shift+ПКМ пустой рукой
        if (player.isSneaking() && (held.isEmpty())) {
            ItemStack got = mgr.removeMirror();
            if (!got.isEmpty()) {
                if (!player.inventory.addItemStackToInventory(got)) {
                    net.minecraft.entity.item.EntityItem ei = new net.minecraft.entity.item.EntityItem(world,
                            player.posX, player.posY + 0.5, player.posZ, got);
                    world.spawnEntity(ei);
                }
                world.playSound(null, pos, net.minecraft.init.SoundEvents.ENTITY_ITEM_PICKUP,
                        net.minecraft.util.SoundCategory.BLOCKS, 0.6f, 1.2f);
                return true;
            }
            return false;
        }

        // Добавление: ПКМ предметом Magic Mirror из Thaumcraft
        boolean isTcMirror = !held.isEmpty() && held.getItem().getRegistryName() != null
                && "thaumcraft".equals(held.getItem().getRegistryName().getNamespace())
                && "mirror".equals(held.getItem().getRegistryName().getPath());

        if (isTcMirror) {
            boolean ok = mgr.addMirror();
            if (ok) {
                held.shrink(1);
                world.playSound(null, pos, net.minecraft.init.SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE,
                        net.minecraft.util.SoundCategory.BLOCKS, 0.5f, 1.1f);
            } else {
                // все 24 заняты
                world.playSound(null, pos, net.minecraft.init.SoundEvents.BLOCK_NOTE_HARP,
                        net.minecraft.util.SoundCategory.BLOCKS, 0.4f, 0.6f);
            }
            return true;
        }
        if (!player.isSneaking() && !held.isEmpty() && !isTcMirror) {
            if (!world.isRemote) {

            }
            return true;
        }
        return false;
    }

    private static final AxisAlignedBB AABB_LOW = new AxisAlignedBB(0, 0, 0, 1, 7.0/16.0, 1);
    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return AABB_LOW;                    // контур выбора
    }
    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return AABB_LOW;                    // коллизия (во что упирается игрок)
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

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileMirrorManager) {
            ((TileMirrorManager) te).forceUnbindAll();
        }
        // Сбрасываем ВСЕ стабы/ядра в 5×5×3 под менеджером
        therealpant.thaumicattempts.golemnet.tile.TileMirrorManager.deactivateUpgradesAround(world, pos);
        super.breakBlock(world, pos, state);
    }
}


