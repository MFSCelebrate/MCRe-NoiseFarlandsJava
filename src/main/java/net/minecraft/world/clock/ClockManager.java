package net.minecraft.world.clock;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.core.Holder;

public interface ClockManager {
    long getTotalTicks(Holder<WorldClock> definition);
}