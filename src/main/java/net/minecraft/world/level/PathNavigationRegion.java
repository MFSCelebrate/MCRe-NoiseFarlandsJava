package net.minecraft.world.level;

import com.google.common.base.Suppliers;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class PathNavigationRegion implements CollisionGetter {
    protected final long centerX;
    protected final long centerZ;
    protected final ChunkAccess[][] chunks;
    protected boolean allEmpty;
    protected final Level level;
    private final Supplier<Holder<Biome>> plains;

    public PathNavigationRegion(final Level level, final BlockPos start, final BlockPos end) {
        this.level = level;
        this.plains = Suppliers.memoize(() -> level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS));
        this.centerX = SectionPos.blockToSectionCoord(start.getX());
        this.centerZ = SectionPos.blockToSectionCoord(start.getZ());
        long xc2 = SectionPos.blockToSectionCoord(end.getX());
        long zc2 = SectionPos.blockToSectionCoord(end.getZ());
        // MCRe NoiseFarlands: region 数组尺寸为寻路范围量级，int 域边界
        this.chunks = new ChunkAccess[(int) (xc2 - this.centerX + 1)][(int) (zc2 - this.centerZ + 1)];
        ChunkSource chunkSource = level.getChunkSource();
        this.allEmpty = true;

        for (long xc = this.centerX; xc <= xc2; xc++) {
            for (long zc = this.centerZ; zc <= zc2; zc++) {
                this.chunks[(int) (xc - this.centerX)][(int) (zc - this.centerZ)] = chunkSource.getChunkNow(xc, zc);
            }
        }

        for (long xc = SectionPos.blockToSectionCoord(start.getX()); xc <= SectionPos.blockToSectionCoord(end.getX()); xc++) {
            for (long zc = SectionPos.blockToSectionCoord(start.getZ()); zc <= SectionPos.blockToSectionCoord(end.getZ()); zc++) {
                ChunkAccess chunk = this.chunks[(int) (xc - this.centerX)][(int) (zc - this.centerZ)];
                if (chunk != null && !chunk.isYSpaceEmpty(start.getY(), end.getY())) {
                    this.allEmpty = false;
                    return;
                }
            }
        }
    }

    private ChunkAccess getChunk(final BlockPos pos) {
        return this.getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    private ChunkAccess getChunk(final long chunkX, final long chunkZ) {
        // MCRe NoiseFarlands: 相对偏移为 region 内小范围索引，int 域边界
        int xc = (int) (chunkX - this.centerX);
        int zc = (int) (chunkZ - this.centerZ);
        if (xc >= 0 && xc < this.chunks.length && zc >= 0 && zc < this.chunks[xc].length) {
            ChunkAccess chunk = this.chunks[xc][zc];
            return chunk != null ? chunk : new EmptyLevelChunk(this.level, new ChunkPos(chunkX, chunkZ), this.plains.get());
        } else {
            return new EmptyLevelChunk(this.level, new ChunkPos(chunkX, chunkZ), this.plains.get());
        }
    }

    @Override
    public WorldBorder getWorldBorder() {
        return this.level.getWorldBorder();
    }

    @Override
    public BlockGetter getChunkForCollisions(final long chunkX, final long chunkZ) {
        return this.getChunk(chunkX, chunkZ);
    }

    @Override
    public List<VoxelShape> getEntityCollisions(final @Nullable Entity source, final AABB testArea) {
        return List.of();
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(final BlockPos pos) {
        ChunkAccess chunk = this.getChunk(pos);
        return chunk.getBlockEntity(pos);
    }

    @Override
    public BlockState getBlockState(final BlockPos pos) {
        if (this.isOutsideBuildHeight(pos)) {
            return Blocks.AIR.defaultBlockState();
        }

        ChunkAccess chunk = this.getChunk(pos);
        return chunk.getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(final BlockPos pos) {
        if (this.isOutsideBuildHeight(pos)) {
            return Fluids.EMPTY.defaultFluidState();
        }

        ChunkAccess chunk = this.getChunk(pos);
        return chunk.getFluidState(pos);
    }

    @Override
    public int getMinY() {
        return this.level.getMinY();
    }

    @Override
    public int getHeight() {
        // MCRe NoiseFarlands: getHeight() 为高度配置域(int) 契约，保持
        return this.level.getHeight();
    }
}