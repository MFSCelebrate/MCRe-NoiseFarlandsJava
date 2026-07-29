package net.minecraft.util.profiling.jfr.stats;
import it.unimi.dsi.fastutil.longs.LongSet;

import jdk.jfr.consumer.RecordedEvent;

public record CpuLoadStat(double jvm, double userJvm, double system) {
    public static CpuLoadStat from(final RecordedEvent event) {
        return new CpuLoadStat(event.getFloat("jvmSystem"), event.getFloat("jvmUser"), event.getFloat("machineTotal"));
    }
}