package net.minecraft.world.level.entity;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface UUIDLookup<IdentifiedType extends UniquelyIdentifyable> {
    @Nullable IdentifiedType lookup(UUID uuid);
}