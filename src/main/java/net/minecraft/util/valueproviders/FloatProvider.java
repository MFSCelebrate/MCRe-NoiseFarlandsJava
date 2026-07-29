package net.minecraft.util.valueproviders;
import it.unimi.dsi.fastutil.longs.LongSet;

import com.mojang.serialization.MapCodec;

public interface FloatProvider extends SampledFloat {
    float min();

    float max();

    MapCodec<? extends FloatProvider> codec();
}