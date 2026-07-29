package net.minecraft.core;
import it.unimi.dsi.fastutil.longs.LongSet;

public interface HolderOwner<T> {
    default boolean canSerializeIn(final HolderOwner<T> context) {
        return context == this;
    }
}