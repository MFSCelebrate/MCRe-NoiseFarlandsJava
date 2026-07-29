package net.minecraft.world.entity.ai.behavior;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public interface PositionTracker {
    Vec3 currentPosition();

    BlockPos currentBlockPosition();

    boolean isVisibleBy(final LivingEntity body);
}