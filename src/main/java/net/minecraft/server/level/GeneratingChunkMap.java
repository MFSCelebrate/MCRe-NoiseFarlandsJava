package net.minecraft.server.level;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.concurrent.CompletableFuture;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;

public interface GeneratingChunkMap {
    // ===== 修改：参数从 long chunkNode 改为 ChunkPos pos =====
    GenerationChunkHolder acquireGeneration(ChunkPos pos);

    void releaseGeneration(GenerationChunkHolder chunkHolder);

    CompletableFuture<ChunkAccess> applyStep(GenerationChunkHolder chunkHolder, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache);

    ChunkGenerationTask scheduleGenerationTask(ChunkStatus targetStatus, ChunkPos pos);

    void runGenerationTasks();
}