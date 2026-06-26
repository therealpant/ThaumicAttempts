package therealpant.thaumicattempts.world.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import therealpant.thaumicattempts.ThaumicAttempts;

public class BlockAuraBoosterCore extends Block {

    public BlockAuraBoosterCore() {
        super(Material.ROCK);
        setHardness(2.0F);
        setResistance(10.0F);
        setLightOpacity(0);
        setTranslationKey(ThaumicAttempts.MODID + ".aura_booster_core");
        setRegistryName(ThaumicAttempts.MODID, "aura_booster_core");
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
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
    public int getLightOpacity(IBlockState state, IBlockAccess world, BlockPos pos) {
        return 0;
    }

    @Override
    public boolean getUseNeighborBrightness(IBlockState state) {
        return true;
    }

    @Override
    public boolean doesSideBlockRendering(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing face) {
        return false;
    }
}
