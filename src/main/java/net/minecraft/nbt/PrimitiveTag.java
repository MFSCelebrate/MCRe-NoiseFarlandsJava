package net.minecraft.nbt;
import it.unimi.dsi.fastutil.longs.LongSet;

public sealed interface PrimitiveTag extends Tag permits NumericTag, StringTag {
    @Override
    default Tag copy() {
        return this;
    }
}