package net.minecraft.world.ticks;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.core.BlockPos;

public interface TickAccess<T> {
    void schedule(ScheduledTick<T> tick);

    boolean hasScheduledTick(BlockPos pos, T type);

    int count();
}