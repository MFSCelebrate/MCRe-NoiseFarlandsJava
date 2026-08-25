package net.minecraft.world.level.lighting;

import com.google.common.annotations.VisibleForTesting;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import org.jspecify.annotations.Nullable;

/**
 * SkyLightEngine — 天空光照引擎（MCRe NoiseFarlands 对象化版）
 * blockNode/sectionNode(long 打包) → BlockPos/SectionPos 对象。
 */
public final class SkyLightEngine extends LightEngine<SkyLightSectionStorage.SkyDataLayerStorageMap, SkyLightSectionStorage> {
    private static final long REMOVE_TOP_SKY_SOURCE_ENTRY = LightEngine.QueueEntry.decreaseAllDirections(15);
    private static final long REMOVE_SKY_SOURCE_ENTRY = LightEngine.QueueEntry.decreaseSkipOneDirection(15, Direction.UP);
    private static final long ADD_SKY_SOURCE_ENTRY = LightEngine.QueueEntry.increaseSkipOneDirection(15, false, Direction.UP);
    private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
    private final ChunkSkyLightSources emptyChunkSources;

    public SkyLightEngine(final LightChunkGetter chunkSource) {
        this(chunkSource, new SkyLightSectionStorage(chunkSource));
    }

    @VisibleForTesting
    SkyLightEngine(final LightChunkGetter chunkSource, final SkyLightSectionStorage storage) {
        super(chunkSource, storage);
        this.emptyChunkSources = new ChunkSkyLightSources(chunkSource.getLevel());
    }

    private static boolean isSourceLevel(final int value) {
        return value == 15;
    }

    private long getLowestSourceY(final long x, final long z, final long defaultValue) {
        ChunkSkyLightSources sources = this.getChunkSources(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
        return sources == null ? defaultValue : sources.getLowestSourceY((int) SectionPos.sectionRelative(x), (int) SectionPos.sectionRelative(z));
    }

    private @Nullable ChunkSkyLightSources getChunkSources(final long chunkX, final long chunkZ) {
        LightChunk chunk = this.chunkSource.getChunkForLighting(chunkX, chunkZ);
        return chunk != null ? chunk.getSkyLightSources() : null;
    }

    @Override
    protected void checkNode(final BlockPos blockNode) {
        long x = blockNode.getX();
        long y = blockNode.getY();
        long z = blockNode.getZ();
        SectionPos sectionNode = SectionPos.of(blockNode);
        long lowestSourceY = this.storage.lightOnInSection(sectionNode) ? this.getLowestSourceY(x, z, Long.MAX_VALUE) : Long.MAX_VALUE;
        if (lowestSourceY != Integer.MAX_VALUE) {
            this.updateSourcesInColumn(x, z, lowestSourceY);
        }

        if (this.storage.storingLightForSection(sectionNode)) {
            boolean isSource = y >= lowestSourceY;
            if (isSource) {
                this.enqueueDecrease(blockNode, REMOVE_SKY_SOURCE_ENTRY);
                this.enqueueIncrease(blockNode, ADD_SKY_SOURCE_ENTRY);
            } else {
                int oldLevel = this.storage.getStoredLevel(blockNode);
                if (oldLevel > 0) {
                    this.storage.setStoredLevel(blockNode, 0);
                    this.enqueueDecrease(blockNode, LightEngine.QueueEntry.decreaseAllDirections(oldLevel));
                } else {
                    this.enqueueDecrease(blockNode, PULL_LIGHT_IN_ENTRY);
                }
            }
        }
    }

    private void updateSourcesInColumn(final long x, final long z, final long lowestSourceY) {
        long worldBottomY = SectionPos.sectionToBlockCoord(this.storage.getBottomSectionY());
        this.removeSourcesBelow(x, z, lowestSourceY, worldBottomY);
        this.addSourcesAbove(x, z, lowestSourceY, worldBottomY);
    }

    private void removeSourcesBelow(final long x, final long z, final long lowestSourceY, final long worldBottomY) {
        if (lowestSourceY > worldBottomY) {
            long sectionX = SectionPos.blockToSectionCoord(x);
            long sectionZ = SectionPos.blockToSectionCoord(z);
            long startY = lowestSourceY - 1;

            for (long sectionY = SectionPos.blockToSectionCoord(startY); this.storage.hasLightDataAtOrBelow(sectionY); sectionY--) {
                if (this.storage.storingLightForSection(SectionPos.of(sectionX, sectionY, sectionZ))) {
                    long sectionBottomY = SectionPos.sectionToBlockCoord(sectionY);
                    long sectionTopY = sectionBottomY + 15;

                    for (long y = Math.min(sectionTopY, startY); y >= sectionBottomY; y--) {
                        BlockPos blockNode = new BlockPos(x, y, z);
                        if (!isSourceLevel(this.storage.getStoredLevel(blockNode))) {
                            return;
                        }

                        this.storage.setStoredLevel(blockNode, 0);
                        this.enqueueDecrease(blockNode, y == lowestSourceY - 1 ? REMOVE_TOP_SKY_SOURCE_ENTRY : REMOVE_SKY_SOURCE_ENTRY);
                    }
                }
            }
        }
    }

    private void addSourcesAbove(final long x, final long z, final long lowestSourceY, final long worldBottomY) {
        long sectionX = SectionPos.blockToSectionCoord(x);
        long sectionZ = SectionPos.blockToSectionCoord(z);
        long neighborLowestSourceY = Math.max(
            Math.max(this.getLowestSourceY(x - 1, z, Long.MIN_VALUE), this.getLowestSourceY(x + 1, z, Long.MIN_VALUE)),
            Math.max(this.getLowestSourceY(x, z - 1, Long.MIN_VALUE), this.getLowestSourceY(x, z + 1, Long.MIN_VALUE))
        );
        long startY = Math.max(lowestSourceY, worldBottomY);

        for (SectionPos sectionNode = SectionPos.of(sectionX, SectionPos.blockToSectionCoord(startY), sectionZ);
            !this.storage.isAboveData(sectionNode);
            sectionNode = sectionNode.offset(0, 1, 0)
        ) {
            if (this.storage.storingLightForSection(sectionNode)) {
                long sectionBottomY = SectionPos.sectionToBlockCoord(sectionNode.y());
                long sectionTopY = sectionBottomY + 15;

                for (long y = Math.max(sectionBottomY, startY); y <= sectionTopY; y++) {
                    BlockPos blockNode = new BlockPos(x, y, z);
                    if (isSourceLevel(this.storage.getStoredLevel(blockNode))) {
                        return;
                    }

                    this.storage.setStoredLevel(blockNode, 15);
                    if (y < neighborLowestSourceY || y == lowestSourceY) {
                        this.enqueueIncrease(blockNode, ADD_SKY_SOURCE_ENTRY);
                    }
                }
            }
        }
    }

    @Override
    protected void propagateIncrease(final BlockPos fromNode, final long increaseData, final int fromLevel) {
        BlockState fromState = null;
        int emptySectionsBelow = this.countEmptySectionsBelowIfAtBorder(fromNode);

        for (Direction propagationDirection : PROPAGATION_DIRECTIONS) {
            if (LightEngine.QueueEntry.shouldPropagateInDirection(increaseData, propagationDirection)) {
                BlockPos toNode = fromNode.relative(propagationDirection);
                if (this.storage.storingLightForSection(SectionPos.of(toNode))) {
                    int toLevel = this.storage.getStoredLevel(toNode);
                    int maxPossibleNewToLevel = fromLevel - 1;
                    if (maxPossibleNewToLevel > toLevel) {
                        this.mutablePos.set(toNode);
                        BlockState toState = this.getState(this.mutablePos);
                        int newToLevel = fromLevel - this.getOpacity(toState);
                        if (newToLevel > toLevel) {
                            if (fromState == null) {
                                fromState = LightEngine.QueueEntry.isFromEmptyShape(increaseData)
                                    ? Blocks.AIR.defaultBlockState()
                                    : this.getState(this.mutablePos.set(fromNode));
                            }

                            if (!this.shapeOccludes(fromState, toState, propagationDirection)) {
                                this.storage.setStoredLevel(toNode, newToLevel);
                                if (newToLevel > 1) {
                                    this.enqueueIncrease(
                                        toNode,
                                        LightEngine.QueueEntry.increaseSkipOneDirection(newToLevel, isEmptyShape(toState), propagationDirection.getOpposite())
                                    );
                                }

                                this.propagateFromEmptySections(toNode, propagationDirection, newToLevel, true, emptySectionsBelow);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void propagateDecrease(final BlockPos fromNode, final long decreaseData) {
        int emptySectionsBelow = this.countEmptySectionsBelowIfAtBorder(fromNode);
        int oldFromLevel = LightEngine.QueueEntry.getFromLevel(decreaseData);

        for (Direction propagationDirection : PROPAGATION_DIRECTIONS) {
            if (LightEngine.QueueEntry.shouldPropagateInDirection(decreaseData, propagationDirection)) {
                BlockPos toNode = fromNode.relative(propagationDirection);
                if (this.storage.storingLightForSection(SectionPos.of(toNode))) {
                    int toLevel = this.storage.getStoredLevel(toNode);
                    if (toLevel != 0) {
                        if (toLevel <= oldFromLevel - 1) {
                            this.storage.setStoredLevel(toNode, 0);
                            this.enqueueDecrease(toNode, LightEngine.QueueEntry.decreaseSkipOneDirection(toLevel, propagationDirection.getOpposite()));
                            this.propagateFromEmptySections(toNode, propagationDirection, toLevel, false, emptySectionsBelow);
                        } else {
                            this.enqueueIncrease(toNode, LightEngine.QueueEntry.increaseOnlyOneDirection(toLevel, false, propagationDirection.getOpposite()));
                        }
                    }
                }
            }
        }
    }

    private int countEmptySectionsBelowIfAtBorder(final BlockPos blockNode) {
        long y = blockNode.getY();
        int localY = (int) SectionPos.sectionRelative(y);
        if (localY != 0) {
            return 0;
        }

        long x = blockNode.getX();
        long z = blockNode.getZ();
        int localX = (int) SectionPos.sectionRelative(x);
        int localZ = (int) SectionPos.sectionRelative(z);
        if (localX != 0 && localX != 15 && localZ != 0 && localZ != 15) {
            return 0;
        }

        long sectionX = SectionPos.blockToSectionCoord(x);
        long sectionY = SectionPos.blockToSectionCoord(y);
        long sectionZ = SectionPos.blockToSectionCoord(z);
        int emptySectionsBelow = 0;

        while (
            !this.storage.storingLightForSection(SectionPos.of(sectionX, sectionY - emptySectionsBelow - 1L, sectionZ))
                && this.storage.hasLightDataAtOrBelow(sectionY - emptySectionsBelow - 1L)
        ) {
            emptySectionsBelow++;
        }

        return emptySectionsBelow;
    }

    private void propagateFromEmptySections(
        final BlockPos toNode, final Direction propagationDirection, final int toLevel, final boolean increase, final int emptySectionsBelow
    ) {
        if (emptySectionsBelow != 0) {
            long x = toNode.getX();
            long z = toNode.getZ();
            if (crossedSectionEdge(propagationDirection, (int) SectionPos.sectionRelative(x), (int) SectionPos.sectionRelative(z))) {
                long y = toNode.getY();
                long sectionX = SectionPos.blockToSectionCoord(x);
                long sectionZ = SectionPos.blockToSectionCoord(z);
                long sectionY = SectionPos.blockToSectionCoord(y) - 1;
                long bottomSectionY = sectionY - emptySectionsBelow + 1;

                while (sectionY >= bottomSectionY) {
                    if (!this.storage.storingLightForSection(SectionPos.of(sectionX, sectionY, sectionZ))) {
                        sectionY--;
                    } else {
                        long sectionMinY = SectionPos.sectionToBlockCoord(sectionY);

                        for (int localY = 15; localY >= 0; localY--) {
                            BlockPos blockNode = new BlockPos(x, sectionMinY + localY, z);
                            if (increase) {
                                this.storage.setStoredLevel(blockNode, toLevel);
                                if (toLevel > 1) {
                                    this.enqueueIncrease(
                                        blockNode, LightEngine.QueueEntry.increaseSkipOneDirection(toLevel, true, propagationDirection.getOpposite())
                                    );
                                }
                            } else {
                                this.storage.setStoredLevel(blockNode, 0);
                                this.enqueueDecrease(blockNode, LightEngine.QueueEntry.decreaseSkipOneDirection(toLevel, propagationDirection.getOpposite()));
                            }
                        }

                        sectionY--;
                    }
                }
            }
        }
    }

    private static boolean crossedSectionEdge(final Direction propagationDirection, final int x, final int z) {
        return switch (propagationDirection) {
            case NORTH -> z == 15;
            case SOUTH -> z == 0;
            case WEST -> x == 15;
            case EAST -> x == 0;
            default -> false;
        };
    }

    @Override
    public void setLightEnabled(final ChunkPos pos, final boolean enable) {
        super.setLightEnabled(pos, enable);
        if (enable) {
            ChunkSkyLightSources sources = Objects.requireNonNullElse(this.getChunkSources(pos.x(), pos.z()), this.emptyChunkSources);
            long highestNonSourceY = sources.getHighestLowestSourceY() - 1;
            long lowestFullySourceSectionY = SectionPos.blockToSectionCoord(highestNonSourceY) + 1;
            SectionPos zeroNode = SectionPos.of(pos.x(), 0L, pos.z());
            long topSectionY = this.storage.getTopSectionY(zeroNode);
            long bottomSectionY = Math.max(this.storage.getBottomSectionY(), lowestFullySourceSectionY);

            for (long sectionY = topSectionY - 1; sectionY >= bottomSectionY; sectionY--) {
                DataLayer dataLayer = this.storage.getDataLayerToWrite(SectionPos.of(pos.x(), sectionY, pos.z()));
                if (dataLayer != null && dataLayer.isEmpty()) {
                    dataLayer.fill(15);
                }
            }
        }
    }

    @Override
    public void propagateLightSources(final ChunkPos pos) {
        SectionPos zeroNode = SectionPos.of(pos.x(), 0L, pos.z());
        this.storage.setLightEnabled(zeroNode, true);
        ChunkSkyLightSources sources = Objects.requireNonNullElse(this.getChunkSources(pos.x(), pos.z()), this.emptyChunkSources);
        ChunkSkyLightSources northSources = Objects.requireNonNullElse(this.getChunkSources(pos.x(), pos.z() - 1), this.emptyChunkSources);
        ChunkSkyLightSources southSources = Objects.requireNonNullElse(this.getChunkSources(pos.x(), pos.z() + 1), this.emptyChunkSources);
        ChunkSkyLightSources westSources = Objects.requireNonNullElse(this.getChunkSources(pos.x() - 1, pos.z()), this.emptyChunkSources);
        ChunkSkyLightSources eastSources = Objects.requireNonNullElse(this.getChunkSources(pos.x() + 1, pos.z()), this.emptyChunkSources);
        long topSectionY = this.storage.getTopSectionY(zeroNode);
        long bottomSectionY = this.storage.getBottomSectionY();
        long sectionMinX = SectionPos.sectionToBlockCoord(pos.x());
        long sectionMinZ = SectionPos.sectionToBlockCoord(pos.z());

        for (long sectionY = topSectionY - 1; sectionY >= bottomSectionY; sectionY--) {
            SectionPos sectionNode = SectionPos.of(pos.x(), sectionY, pos.z());
            DataLayer dataLayer = this.storage.getDataLayerToWrite(sectionNode);
            if (dataLayer != null) {
                long sectionMinY = SectionPos.sectionToBlockCoord(sectionY);
                long sectionMaxY = sectionMinY + 15;
                boolean sourcesBelow = false;

                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        long lowestSourceY = sources.getLowestSourceY(x, z);
                        if (lowestSourceY <= sectionMaxY) {
                            long northLowestSourceY = z == 0 ? northSources.getLowestSourceY(x, 15) : sources.getLowestSourceY(x, z - 1);
                            long southLowestSourceY = z == 15 ? southSources.getLowestSourceY(x, 0) : sources.getLowestSourceY(x, z + 1);
                            long westLowestSourceY = x == 0 ? westSources.getLowestSourceY(15, z) : sources.getLowestSourceY(x - 1, z);
                            long eastLowestSourceY = x == 15 ? eastSources.getLowestSourceY(0, z) : sources.getLowestSourceY(x + 1, z);
                            long neighborLowestSourceY = Math.max(
                                Math.max(northLowestSourceY, southLowestSourceY), Math.max(westLowestSourceY, eastLowestSourceY)
                            );

                            for (long y = sectionMaxY; y >= Math.max(sectionMinY, lowestSourceY); y--) {
                                dataLayer.set(x, (int) SectionPos.sectionRelative(y), z, 15);
                                if (y == lowestSourceY || y < neighborLowestSourceY) {
                                    BlockPos blockNode = new BlockPos(sectionMinX + x, y, sectionMinZ + z);
                                    this.enqueueIncrease(
                                        blockNode,
                                        LightEngine.QueueEntry.increaseSkySourceInDirections(
                                            y == lowestSourceY, y < northLowestSourceY, y < southLowestSourceY, y < westLowestSourceY, y < eastLowestSourceY
                                        )
                                    );
                                }
                            }

                            if (lowestSourceY < sectionMinY) {
                                sourcesBelow = true;
                            }
                        }
                    }
                }

                if (!sourcesBelow) {
                    break;
                }
            }
        }
    }
}
