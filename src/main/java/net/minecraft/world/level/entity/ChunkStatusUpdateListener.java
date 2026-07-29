package net.minecraft.world.level.entity;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.world.level.ChunkPos;

@FunctionalInterface
public interface ChunkStatusUpdateListener {
    void onChunkStatusChange(ChunkPos pos, FullChunkStatus chunkStatus);
}