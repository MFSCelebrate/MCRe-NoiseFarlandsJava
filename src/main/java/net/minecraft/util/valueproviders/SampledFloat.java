package net.minecraft.util.valueproviders;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.util.RandomSource;

public interface SampledFloat {
    float sample(final RandomSource random);
}