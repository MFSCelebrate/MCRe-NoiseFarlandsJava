package net.minecraft.world.level.levelgen.feature.configurations;
import it.unimi.dsi.fastutil.longs.LongSet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

public class NoneFeatureConfiguration implements FeatureConfiguration {
    public static final NoneFeatureConfiguration INSTANCE = new NoneFeatureConfiguration();
    public static final Codec<NoneFeatureConfiguration> CODEC = MapCodec.unitCodec(INSTANCE);
}