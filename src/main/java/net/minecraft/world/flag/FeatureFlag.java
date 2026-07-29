package net.minecraft.world.flag;
import it.unimi.dsi.fastutil.longs.LongSet;

public class FeatureFlag {
    final FeatureFlagUniverse universe;
    final long mask;

    FeatureFlag(final FeatureFlagUniverse universe, final int bit) {
        this.universe = universe;
        this.mask = 1L << bit;
    }
}