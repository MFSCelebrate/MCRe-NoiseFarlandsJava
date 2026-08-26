package net.minecraft.world.level;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.BlockColumn;

public final class NoiseColumn implements BlockColumn {
    private final int minY;
    private final BlockState[] column;

    public NoiseColumn(final int minY, final BlockState[] column) {
        this.minY = minY;
        this.column = column;
    }

    @Override
    // MCRe NoiseFarlands: 世界 Y Long 化
    public BlockState getBlock(final long blockY) {
// MCRe NoiseFarlands: 列缓存相对索引 int 域边界
        int yIndex = (int) (blockY - this.minY);
        return yIndex >= 0 && yIndex < this.column.length ? this.column[yIndex] : Blocks.AIR.defaultBlockState();
    }

    @Override
    // MCRe NoiseFarlands: 世界 Y Long 化
    public void setBlock(final long blockY, final BlockState state) {
// MCRe NoiseFarlands: 列缓存相对索引 int 域边界
        int yIndex = (int) (blockY - this.minY);
        if (yIndex >= 0 && yIndex < this.column.length) {
            this.column[yIndex] = state;
        } else {
            throw new IllegalArgumentException("Outside of column height: " + blockY);
        }
    }
}