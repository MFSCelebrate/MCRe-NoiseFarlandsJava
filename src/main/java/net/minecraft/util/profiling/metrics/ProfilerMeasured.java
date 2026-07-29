package net.minecraft.util.profiling.metrics;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.List;

public interface ProfilerMeasured {
    List<MetricSampler> profiledMetrics();
}