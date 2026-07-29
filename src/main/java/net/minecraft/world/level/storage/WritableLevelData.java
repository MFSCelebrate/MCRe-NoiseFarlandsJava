package net.minecraft.world.level.storage;
import it.unimi.dsi.fastutil.longs.LongSet;

public interface WritableLevelData extends LevelData {
    void setSpawn(final LevelData.RespawnData respawnData);
}