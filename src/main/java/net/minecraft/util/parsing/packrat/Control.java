package net.minecraft.util.parsing.packrat;
import it.unimi.dsi.fastutil.longs.LongSet;

public interface Control {
    Control UNBOUND = new Control() {
        @Override
        public void cut() {
        }

        @Override
        public boolean hasCut() {
            return false;
        }
    };

    void cut();

    boolean hasCut();
}