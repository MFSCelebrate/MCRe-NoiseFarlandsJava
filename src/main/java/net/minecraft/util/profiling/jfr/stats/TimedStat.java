package net.minecraft.util.profiling.jfr.stats;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.time.Duration;

public interface TimedStat {
    Duration duration();
}