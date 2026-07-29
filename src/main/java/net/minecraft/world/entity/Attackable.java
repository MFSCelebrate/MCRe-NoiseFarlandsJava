package net.minecraft.world.entity;
import it.unimi.dsi.fastutil.longs.LongSet;

import org.jspecify.annotations.Nullable;

public interface Attackable {
    @Nullable LivingEntity getLastAttacker();
}