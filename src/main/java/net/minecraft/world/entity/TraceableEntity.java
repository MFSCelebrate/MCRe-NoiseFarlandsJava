package net.minecraft.world.entity;
import it.unimi.dsi.fastutil.longs.LongSet;

import org.jspecify.annotations.Nullable;

public interface TraceableEntity {
    @Nullable Entity getOwner();
}