package net.minecraft.world.level.chunk;

import net.minecraft.world.level.block.state.BlockState;

// MCRe NoiseFarlands: blockY 为世界 Y，Long 化
public interface BlockColumn {
    BlockState getBlock(final long blockY);

    void setBlock(final long blockY, final BlockState state);
}