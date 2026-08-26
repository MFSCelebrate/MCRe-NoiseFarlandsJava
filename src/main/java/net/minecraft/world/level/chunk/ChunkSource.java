package net.minecraft.world.level.chunk;


import java.io.IOException;
import java.util.Set;
import java.util.function.BooleanSupplier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.jspecify.annotations.Nullable;

public abstract class ChunkSource implements AutoCloseable, LightChunkGetter {
    public @Nullable LevelChunk getChunk(final long x, final long z, final boolean loadOrGenerate) {
        return (LevelChunk)this.getChunk(x, z, ChunkStatus.FULL, loadOrGenerate);
    }

    public @Nullable LevelChunk getChunkNow(final long x, final long z) {
        return this.getChunk(x, z, false);
    }

    @Override
    public @Nullable LightChunk getChunkForLighting(final long x, final long z) {
        // MCRe NoiseFarlands: x/z 已 Long 化；getChunk 尚属区块管理模块（int 域），此处为 API 边界强转
        return this.getChunk((int) x, (int) z, ChunkStatus.EMPTY, false);
    }

    public boolean hasChunk(final long x, final long z) {
        return this.getChunk(x, z, ChunkStatus.FULL, false) != null;
    }

    public abstract @Nullable ChunkAccess getChunk(long x, long z, ChunkStatus targetStatus, boolean loadOrGenerate);

    public abstract void tick(BooleanSupplier haveTime, final boolean tickChunks);

    public void onSectionEmptinessChanged(final long sectionX, final long sectionY, final long sectionZ, final boolean empty) {
    }

    public abstract String gatherStats();

    public abstract int getLoadedChunksCount();

    @Override
    public void close() throws IOException {
    }

    public abstract LevelLightEngine getLightEngine();

    public void setSpawnSettings(final boolean spawnEnemies) {
    }

    public boolean updateChunkForced(final ChunkPos pos, final boolean forced) {
        return false;
    }

    public Set<ChunkPos> getForceLoadedChunks() {
        return Set.of();
    }
}