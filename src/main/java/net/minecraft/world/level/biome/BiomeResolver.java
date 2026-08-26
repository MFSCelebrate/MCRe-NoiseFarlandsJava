package net.minecraft.world.level.biome;

import net.minecraft.core.Holder;

public interface BiomeResolver {
    Holder<Biome> getNoiseBiome(final long quartX, final long quartY, final long quartZ, final Climate.Sampler sampler);
}