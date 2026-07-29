package net.minecraft.world.level;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.server.level.ServerLevel;

public interface CustomSpawner {
    void tick(ServerLevel level, boolean spawnEnemies);
}