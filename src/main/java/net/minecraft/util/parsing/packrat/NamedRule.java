package net.minecraft.util.parsing.packrat;
import it.unimi.dsi.fastutil.longs.LongSet;

public interface NamedRule<S, T> {
    Atom<T> name();

    Rule<S, T> value();
}