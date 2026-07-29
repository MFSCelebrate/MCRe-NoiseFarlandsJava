package net.minecraft.nbt;
import it.unimi.dsi.fastutil.longs.LongSet;

public class NbtException extends RuntimeException {
    public NbtException(final String message) {
        super(message);
    }
}