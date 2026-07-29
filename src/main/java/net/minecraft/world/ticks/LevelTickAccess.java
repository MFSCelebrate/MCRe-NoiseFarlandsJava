package net.minecraft.world.ticks;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.core.BlockPos;

public interface LevelTickAccess<T> extends TickAccess<T> {
    boolean willTickThisTick(BlockPos pos, T type);
}