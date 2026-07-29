package net.minecraft.util.debugchart;
import it.unimi.dsi.fastutil.longs.LongSet;

public interface SampleLogger {
    void logFullSample(final long[] sample);

    void logSample(final long sample);

    void logPartialSample(final long sample, final int dimension);
}