package net.minecraft.world.item.equipment;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.world.entity.EntityType;

@FunctionalInterface
public interface AllowedEntitiesProvider {
    HolderSet<EntityType<?>> get(HolderGetter<EntityType<?>> entityGetter);
}