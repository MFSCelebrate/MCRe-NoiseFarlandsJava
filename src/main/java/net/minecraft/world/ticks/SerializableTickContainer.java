package net.minecraft.world.ticks;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.List;

public interface SerializableTickContainer<T> {
    List<SavedTick<T>> pack(long currentTick);
}