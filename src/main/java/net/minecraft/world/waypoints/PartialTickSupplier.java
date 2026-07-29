package net.minecraft.world.waypoints;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.world.entity.Entity;

@FunctionalInterface
public interface PartialTickSupplier {
    float apply(Entity entity);
}