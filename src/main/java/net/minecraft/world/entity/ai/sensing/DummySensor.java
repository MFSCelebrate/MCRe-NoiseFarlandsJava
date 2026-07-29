package net.minecraft.world.entity.ai.sensing;
import it.unimi.dsi.fastutil.longs.LongSet;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class DummySensor extends Sensor<LivingEntity> {
    @Override
    protected void doTick(final ServerLevel level, final LivingEntity body) {
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return ImmutableSet.of();
    }
}