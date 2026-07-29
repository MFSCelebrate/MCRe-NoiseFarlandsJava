package net.minecraft.world;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

public interface WorldlyContainerHolder {
    WorldlyContainer getContainer(BlockState state, LevelAccessor level, BlockPos pos);
}