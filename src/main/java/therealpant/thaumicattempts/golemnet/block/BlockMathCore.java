package therealpant.thaumicattempts.golemnet.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import therealpant.thaumicattempts.ThaumicAttempts;
import net.minecraft.item.ItemStack;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;


public class BlockMathCore extends Block {
    public static final PropertyBool ACTIVE = PropertyBool.create("active");
    public static final PropertyInteger SIG = PropertyInteger.create("sig", 0, 15);

    public BlockMathCore() {
        super(Material.IRON);
        setHardness(2.0F);
        setResistance(10.0F);
        setTranslationKey(ThaumicAttempts.MODID + ".math_core");
        setRegistryName(ThaumicAttempts.MODID, "math_core");
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
        setDefaultState(this.blockState.getBaseState().withProperty(ACTIVE, false).withProperty(SIG, 15));
        setLightOpacity(0);
    }

    @Override public boolean isOpaqueCube(IBlockState s){ return false; }
    @Override public boolean isFullCube(IBlockState s){ return false; }
    @SideOnly(Side.CLIENT) @Override public BlockRenderLayer getRenderLayer(){ return BlockRenderLayer.CUTOUT_MIPPED; }

    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, ACTIVE, SIG); }

    @Override public IBlockState getStateFromMeta(int meta) {
        int sig = (meta <= 1) ? 15 : (meta & 15);
        return getDefaultState().withProperty(ACTIVE, false).withProperty(SIG, sig);
    }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(SIG); }

    @Override
    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote) {
            // Если у тебя уже есть свой calcInitialSig(...) — используй его.
            // Ниже — безопасный вызов, как у стабилизатора.
            if (state.getValue(SIG) == 15) {
                int sig = calcInitialSig(world, pos, /*thisIsCore*/ true);
                world.setBlockState(pos, state.withProperty(SIG, sig), 2 | 16);
            }
            therealpant.thaumicattempts.golemnet.tile.TileMirrorManager.touchManagerNearUpgrade(world, pos);
        }
        super.onBlockAdded(world, pos, state);
    }

    @Override
    public void onBlockPlacedBy(World w, BlockPos p, IBlockState s, net.minecraft.entity.EntityLivingBase placer, ItemStack stack) {
        if (!w.isRemote) {
            if (s.getValue(SIG) == 15) {
                int sig = calcInitialSig(w, p, /*thisIsCore*/ true);
                w.setBlockState(p, s.withProperty(SIG, sig), 2 | 16);
            }
            therealpant.thaumicattempts.golemnet.tile.TileMirrorManager.touchManagerNearUpgrade(w, p);
        }
    }
    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block blockIn, BlockPos fromPos) {
        if (!world.isRemote) {
            therealpant.thaumicattempts.golemnet.tile.TileMirrorManager.touchManagerNearUpgrade(world, pos);
        }
        super.neighborChanged(state, world, pos, blockIn, fromPos);
    }

    private int calcInitialSig(World w, BlockPos pos, boolean thisIsCore) {
        if (!isInsideManagerField(w, pos)) return 15;
        if (isTouchingManagerAbove(w, pos)) return 0;

        int min = Integer.MAX_VALUE;
        for (EnumFacing f : EnumFacing.VALUES) {
            IBlockState ns = w.getBlockState(pos.offset(f));
            Block nb = ns.getBlock();
            if (!(nb instanceof BlockMirrorStabilizer)) continue; // ядро питается от стаба
            int nSig = ns.getValue(BlockMirrorStabilizer.SIG);
            if (nSig < min) min = nSig;
        }
        if (min == Integer.MAX_VALUE || min >= 15) return 15;
        return Math.min(15, min + 1);
    }

    @Override
    public void breakBlock(World w, BlockPos p, IBlockState s) {
        super.breakBlock(w, p, s);
        if (!w.isRemote) {
            therealpant.thaumicattempts.golemnet.tile.TileMirrorManager.touchManagerNearUpgrade(w, p);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        // На клиенте вычисляем «должно ли быть активно» по локальному полю менеджера
        boolean visualActive = computeActiveVisual(world, pos, /*thisIsCore*/ true);
        return state.withProperty(ACTIVE, visualActive);
    }
    private static boolean computeActiveVisual(IBlockAccess w, BlockPos me, boolean thisIsCore) {
        // 1) ищем менеджер в кубоиде 5×5×3 НАД нами
        BlockPos managerPos = null;
        for (int dy = 1; dy <= 3 && managerPos == null; dy++)
            for (int dx = -2; dx <= 2 && managerPos == null; dx++)
                for (int dz = -2; dz <= 2 && managerPos == null; dz++) {
                    BlockPos p = me.add(dx, dy, dz);
                    if (w.getBlockState(p).getBlock() == therealpant.thaumicattempts.init.TABlocks.MIRROR_MANAGER) {
                        managerPos = p.toImmutable();
                    }
                }
        if (managerPos == null) return false;

        // 2) ограничиваемся ровно полем под менеджером
        int minX = managerPos.getX() - 2, maxX = managerPos.getX() + 2;
        int minZ = managerPos.getZ() - 2, maxZ = managerPos.getZ() + 2;
        int minY = managerPos.getY() - 3, maxY = managerPos.getY() - 1;

        if (me.getX()<minX||me.getX()>maxX||me.getY()<minY||me.getY()>maxY||me.getZ()<minZ||me.getZ()>maxZ)
            return false;

        // 3) seed = блок ВПЛОТНУЮ под менеджером; волной включаем стабы/ядра по граням
        BlockPos seed = managerPos.down();
        Block seedBlock = w.getBlockState(seed).getBlock();
        boolean seedIsStab = seedBlock == therealpant.thaumicattempts.golemcraft.ModBlocksItems.MIRROR_STABILIZER;
        boolean seedIsCore = seedBlock == therealpant.thaumicattempts.golemcraft.ModBlocksItems.MATH_CORE;
        if (!seedIsStab && !seedIsCore) return false;

        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> q = new ArrayDeque<>();
        q.add(seed);
        visited.add(seed);

        while (!q.isEmpty()) {
            BlockPos p = q.poll();
            if (p.equals(me)) return true; // дошли до нас — активны

            Block bHere = w.getBlockState(p).getBlock();
            boolean hereIsStab = (bHere == therealpant.thaumicattempts.golemcraft.ModBlocksItems.MIRROR_STABILIZER);
            boolean hereIsCore = (bHere == therealpant.thaumicattempts.golemcraft.ModBlocksItems.MATH_CORE);

            // из стаба растём только в ядра, из ядра — только в стабы
            for (EnumFacing f : EnumFacing.VALUES) {
                BlockPos nb = p.offset(f);
                if (nb.getX()<minX||nb.getX()>maxX||nb.getY()<minY||nb.getY()>maxY||nb.getZ()<minZ||nb.getZ()>maxZ) continue;
                if (visited.contains(nb)) continue;

                Block b = w.getBlockState(nb).getBlock();
                boolean isStab = b == therealpant.thaumicattempts.golemcraft.ModBlocksItems.MIRROR_STABILIZER;
                boolean isCore = b == therealpant.thaumicattempts.golemcraft.ModBlocksItems.MATH_CORE;

                if ((hereIsStab && isCore) || (hereIsCore && isStab)) {
                    visited.add(nb);
                    q.add(nb);
                }
            }
        }
        return false;
    }


    private static boolean isTouchingManagerAbove(World w, BlockPos pos) {
        return w.getBlockState(pos.up()).getBlock() == therealpant.thaumicattempts.init.TABlocks.MIRROR_MANAGER;
    }
    private static boolean isInsideManagerField(World w, BlockPos pos) {
        for (int dy = 1; dy <= 3; dy++)
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++)
                    if (w.getBlockState(pos.add(dx, dy, dz)).getBlock()
                            == therealpant.thaumicattempts.init.TABlocks.MIRROR_MANAGER) return true;
        return false;
    }

    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.MODEL; }
}