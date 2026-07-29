package net.minecraft.world.damagesource;
import it.unimi.dsi.fastutil.longs.LongSet;

import org.jspecify.annotations.Nullable;

public record CombatEntry(DamageSource source, float damage, @Nullable FallLocation fallLocation, float fallDistance) {
}