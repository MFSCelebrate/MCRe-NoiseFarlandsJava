package net.minecraft.world.level.entity;
import it.unimi.dsi.fastutil.longs.LongSet;

public interface LevelCallback<T> {
    void onCreated(T entity);

    void onDestroyed(T entity);

    void onTickingStart(T entity);

    void onTickingEnd(T entity);

    void onTrackingStart(T entity);

    void onTrackingEnd(T entity);

    void onSectionChange(T entity);
}