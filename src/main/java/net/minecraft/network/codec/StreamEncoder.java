package net.minecraft.network.codec;
import it.unimi.dsi.fastutil.longs.LongSet;

@FunctionalInterface
public interface StreamEncoder<O, T> {
    void encode(O output, T value);
}