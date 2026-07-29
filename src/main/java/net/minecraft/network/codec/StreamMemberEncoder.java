package net.minecraft.network.codec;
import it.unimi.dsi.fastutil.longs.LongSet;

@FunctionalInterface
public interface StreamMemberEncoder<O, T> {
    void encode(T value, O output);
}