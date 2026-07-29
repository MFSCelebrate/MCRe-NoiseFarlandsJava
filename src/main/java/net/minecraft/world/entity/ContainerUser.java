package net.minecraft.world.entity;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;

public interface ContainerUser {
    boolean hasContainerOpen(final ContainerOpenersCounter container, final BlockPos blockPos);

    double getContainerInteractionRange();

    default LivingEntity getLivingEntity() {
        if (this instanceof LivingEntity livingEntity) {
            return livingEntity;
        } else {
            throw new IllegalStateException("A container user must be a LivingEntity");
        }
    }
}