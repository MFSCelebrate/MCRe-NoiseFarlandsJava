package net.minecraft.world.entity.animal;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.resources.Identifier;

public interface TemperatureVariants {
    Identifier TEMPERATE = Identifier.withDefaultNamespace("temperate");
    Identifier WARM = Identifier.withDefaultNamespace("warm");
    Identifier COLD = Identifier.withDefaultNamespace("cold");
}