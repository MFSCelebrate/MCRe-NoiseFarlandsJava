package net.minecraft.util;
import it.unimi.dsi.fastutil.longs.LongSet;

@FunctionalInterface
public interface ToFloatFunction<T> {
    float applyAsFloat(T value);
}