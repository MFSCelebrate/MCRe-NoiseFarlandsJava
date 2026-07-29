package net.minecraft.util.profiling.jfr.callback;
import it.unimi.dsi.fastutil.longs.LongSet;

@FunctionalInterface
public interface ProfiledDuration {
    void finish(boolean success);
}