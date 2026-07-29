package net.minecraft.world.level.biome;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.core.Holder;

public interface BiomeResolver {
    Holder<Biome> getNoiseBiome(final int quartX, final int quartY, final int quartZ, final Climate.Sampler sampler);
}