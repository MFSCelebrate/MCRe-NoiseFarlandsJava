package net.minecraft.util.profiling.jfr.stats;
import it.unimi.dsi.fastutil.longs.LongSet;

import jdk.jfr.consumer.RecordedEvent;

public record FpsStat(int fps) {
    public static FpsStat from(final RecordedEvent event, final String field) {
        return new FpsStat(event.getInt(field));
    }
}