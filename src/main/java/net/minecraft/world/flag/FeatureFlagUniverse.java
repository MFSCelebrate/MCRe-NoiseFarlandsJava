package net.minecraft.world.flag;
import it.unimi.dsi.fastutil.longs.LongSet;

public class FeatureFlagUniverse {
    private final String id;

    public FeatureFlagUniverse(final String id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return this.id;
    }
}