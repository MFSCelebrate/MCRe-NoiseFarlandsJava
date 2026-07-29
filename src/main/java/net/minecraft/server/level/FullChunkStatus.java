package net.minecraft.server.level;
import it.unimi.dsi.fastutil.longs.LongSet;

public enum FullChunkStatus {
    INACCESSIBLE,
    FULL,
    BLOCK_TICKING,
    ENTITY_TICKING;

    public boolean isOrAfter(final FullChunkStatus step) {
        return this.ordinal() >= step.ordinal();
    }
}