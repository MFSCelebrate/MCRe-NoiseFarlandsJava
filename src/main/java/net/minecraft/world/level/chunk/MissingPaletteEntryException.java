package net.minecraft.world.level.chunk;
import it.unimi.dsi.fastutil.longs.LongSet;

public class MissingPaletteEntryException extends RuntimeException {
    public MissingPaletteEntryException(final int index) {
        super("Missing Palette entry for index " + index + ".");
    }
}