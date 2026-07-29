package net.minecraft.util.parsing.packrat;
import it.unimi.dsi.fastutil.longs.LongSet;

public record Atom<T>(String name) {
    @Override
    public String toString() {
        return "<" + this.name + ">";
    }

    public static <T> Atom<T> of(final String name) {
        return new Atom<>(name);
    }
}