package therealpant.thaumicattempts.api;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Настройки ресурса, который должна породить аномалия.
 */
public final class FluxAnomalyResource {

    private final Block block;
    private final int blockCount;

    private FluxAnomalyResource(Block block, int blockCount) {
        this.block = Objects.requireNonNull(block, "Resource block");
        this.blockCount = Math.max(0, blockCount);
    }

    public static FluxAnomalyResource of(@Nonnull Block block, int blockCount) {
        return new FluxAnomalyResource(block, blockCount);
    }

    public static FluxAnomalyResource empty() {
        return new FluxAnomalyResource(Blocks.AIR, 0);
    }

    public Block getBlock() {
        return block;
    }

    public int getBlockCount() {
        return blockCount;
    }

    public boolean isPresent() {
        return block != Blocks.AIR && blockCount > 0;
    }
}