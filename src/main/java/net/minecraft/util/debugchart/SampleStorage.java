package net.minecraft.util.debugchart;
import it.unimi.dsi.fastutil.longs.LongSet;

public interface SampleStorage {
    int capacity();

    int size();

    long get(final int index);

    long get(final int index, final int dimension);

    void reset();
}