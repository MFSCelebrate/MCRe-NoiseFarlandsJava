package net.minecraft.network.protocol;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.network.codec.StreamCodec;

@FunctionalInterface
public interface CodecModifier<B, V, C> {
    StreamCodec<? super B, V> apply(StreamCodec<? super B, V> original, C context);
}