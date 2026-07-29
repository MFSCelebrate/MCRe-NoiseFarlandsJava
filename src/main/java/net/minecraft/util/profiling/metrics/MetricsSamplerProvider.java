package net.minecraft.util.profiling.metrics;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.util.profiling.ProfileCollector;

public interface MetricsSamplerProvider {
    Set<MetricSampler> samplers(final Supplier<ProfileCollector> singleTickProfiler);
}