package net.minecraft.world.level.entity;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.world.entity.Entity;

public interface EntityInLevelCallback {
    EntityInLevelCallback NULL = new EntityInLevelCallback() {
        @Override
        public void onMove() {
        }

        @Override
        public void onRemove(final Entity.RemovalReason reason) {
        }
    };

    void onMove();

    void onRemove(final Entity.RemovalReason reason);
}