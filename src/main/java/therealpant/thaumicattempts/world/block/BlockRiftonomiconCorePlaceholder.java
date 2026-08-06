package therealpant.thaumicattempts.world.block;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.world.World;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.world.tile.TileRiftonomiconCorePlaceholder;

import javax.annotation.Nullable;

/** Technical item/block used to render the Riftonomicon core in research icons. */
public class BlockRiftonomiconCorePlaceholder extends Block {
    public BlockRiftonomiconCorePlaceholder() {
        super(Material.ROCK);
        setRegistryName(ThaumicAttempts.MODID, "riftonomicon_core_placeholder");
        setTranslationKey(ThaumicAttempts.MODID + ".riftonomicon_core_placeholder");
        setHardness(5.0F);
        setResistance(20.0F);
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
        return new TileRiftonomiconCorePlaceholder();
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.INVISIBLE;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }
}
