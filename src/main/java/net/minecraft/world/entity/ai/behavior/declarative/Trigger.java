package net.minecraft.world.entity.ai.behavior.declarative;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

public interface Trigger<E extends LivingEntity> {
    boolean trigger(final ServerLevel level, final E body, final long timestamp);
}