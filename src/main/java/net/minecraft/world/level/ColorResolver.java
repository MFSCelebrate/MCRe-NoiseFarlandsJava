package net.minecraft.world.level;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.world.level.biome.Biome;

@FunctionalInterface
public interface ColorResolver {
    int getColor(Biome biome, final double x, final double z);
}