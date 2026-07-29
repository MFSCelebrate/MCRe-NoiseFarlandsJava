package net.minecraft.util.parsing.packrat;
import it.unimi.dsi.fastutil.longs.LongSet;

public record ErrorEntry<S>(int cursor, SuggestionSupplier<S> suggestions, Object reason) {
}