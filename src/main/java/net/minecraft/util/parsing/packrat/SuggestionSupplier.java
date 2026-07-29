package net.minecraft.util.parsing.packrat;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.stream.Stream;

public interface SuggestionSupplier<S> {
    Stream<String> possibleValues(ParseState<S> state);

    static <S> SuggestionSupplier<S> empty() {
        return state -> Stream.empty();
    }
}