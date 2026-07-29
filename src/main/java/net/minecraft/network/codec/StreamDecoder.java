package net.minecraft.network.codec;
import it.unimi.dsi.fastutil.longs.LongSet;

@FunctionalInterface
public interface StreamDecoder<I, T> {
    T decode(I input);
}