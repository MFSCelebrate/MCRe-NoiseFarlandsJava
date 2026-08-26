package net.minecraft.world.level.lighting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.BitStorage;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ChunkSkyLightSources {
    private static final int SIZE = 16;
    public static final long NEGATIVE_INFINITY = Long.MIN_VALUE;
    private final long minY;
    private final BitStorage heightmap;
    private final BlockPos.MutableBlockPos mutablePos1 = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos mutablePos2 = new BlockPos.MutableBlockPos();

    public ChunkSkyLightSources(final LevelHeightAccessor level) {
        this.minY = level.getMinY() - 1L;
        int maxY = level.getMaxY() + 1;
        int bits = Mth.ceillog2((int) (maxY - this.minY + 1));
        this.heightmap = new SimpleBitStorage(bits, 256);
    }

    public void fillFrom(final ChunkAccess chunk) {
        int maxSectionIndex = chunk.getHighestFilledSectionIndex();
        if (maxSectionIndex == -1) {
            this.fill(this.minY);
        } else {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    long initialEdgeY = Math.max(this.findLowestSourceY(chunk, maxSectionIndex, x, z), this.minY);
                    this.set(index(x, z), initialEdgeY);
                }
            }
        }
    }

    private long findLowestSourceY(final ChunkAccess chunk, final int topSectionIndex, final int x, final int z) {
        // MCRe NoiseFarlands: topY 为世界 Y 坐标，Long 化；sectionIndex 为 0-24 小范围索引保持 int
        long topY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(topSectionIndex) + 1L);
        BlockPos.MutableBlockPos topPos = this.mutablePos1.set(x, topY, z);
        BlockPos.MutableBlockPos bottomPos = this.mutablePos2.setWithOffset(topPos, Direction.DOWN);
        BlockState topState = Blocks.AIR.defaultBlockState();

        for (int sectionIndex = topSectionIndex; sectionIndex >= 0; sectionIndex--) {
            LevelChunkSection section = chunk.getSection(sectionIndex);
            if (section.hasOnlyAir()) {
                topState = Blocks.AIR.defaultBlockState();
                // MCRe NoiseFarlands: section Y Long 化
        long sectionY = chunk.getSectionYFromSectionIndex(sectionIndex);
                topPos.setY(SectionPos.sectionToBlockCoord(sectionY));
                bottomPos.setY(topPos.getY() - 1);
            } else {
                for (int y = 15; y >= 0; y--) {
                    BlockState bottomState = section.getBlockState(x, y, z);
                    if (isEdgeOccluded(topState, bottomState)) {
                        return topPos.getY();
                    }

                    topState = bottomState;
                    topPos.set(bottomPos);
                    bottomPos.move(Direction.DOWN);
                }
            }
        }

        return this.minY;
    }

    public boolean update(final BlockGetter level, final int x, final long y, final int z) {
        long upperEdgeY = y + 1;
        int index = index(x, z);
        long currentLowestSourceY = this.get(index);
        if (upperEdgeY < currentLowestSourceY) {
            return false;
        }

        BlockPos topPos = this.mutablePos1.set(x, y + 1, z);
        BlockState topState = level.getBlockState(topPos);
        BlockPos middlePos = this.mutablePos2.set(x, y, z);
        BlockState middleState = level.getBlockState(middlePos);
        if (this.updateEdge(level, index, currentLowestSourceY, topPos, topState, middlePos, middleState)) {
            return true;
        }

        BlockPos bottomPos = this.mutablePos1.set(x, y - 1, z);
        BlockState bottomState = level.getBlockState(bottomPos);
        return this.updateEdge(level, index, currentLowestSourceY, middlePos, middleState, bottomPos, bottomState);
    }

    private boolean updateEdge(
        final BlockGetter level,
        final int index,
        final long oldTopEdgeY,
        final BlockPos topPos,
        final BlockState topState,
        final BlockPos bottomPos,
        final BlockState bottomState
    ) {
        long checkedEdgeY = topPos.getY();
        if (isEdgeOccluded(topState, bottomState)) {
            if (checkedEdgeY > oldTopEdgeY) {
                this.set(index, checkedEdgeY);
                return true;
            }
        } else if (checkedEdgeY == oldTopEdgeY) {
            this.set(index, this.findLowestSourceBelow(level, bottomPos, bottomState));
            return true;
        }

        return false;
    }

    private long findLowestSourceBelow(final BlockGetter level, final BlockPos startPos, final BlockState startState) {
        BlockPos.MutableBlockPos topPos = this.mutablePos1.set(startPos);
        BlockPos.MutableBlockPos bottomPos = this.mutablePos2.setWithOffset(startPos, Direction.DOWN);
        BlockState topState = startState;

        while (bottomPos.getY() >= this.minY) {
            BlockState bottomState = level.getBlockState(bottomPos);
            if (isEdgeOccluded(topState, bottomState)) {
                return topPos.getY();
            }

            topState = bottomState;
            topPos.set(bottomPos);
            bottomPos.move(Direction.DOWN);
        }

        return this.minY;
    }

    private static boolean isEdgeOccluded(final BlockState topState, final BlockState bottomState) {
        if (bottomState.getLightDampening() != 0) {
            return true;
        }

        VoxelShape topShape = LightEngine.getOcclusionShape(topState, Direction.DOWN);
        VoxelShape bottomShape = LightEngine.getOcclusionShape(bottomState, Direction.UP);
        return Shapes.faceShapeOccludes(topShape, bottomShape);
    }

    public long getLowestSourceY(final int x, final int z) {
        long value = this.get(index(x, z));
        return this.extendSourcesBelowWorld(value);
    }

    public long getHighestLowestSourceY() {
        long maxValue = Long.MIN_VALUE;

        for (int i = 0; i < this.heightmap.getSize(); i++) {
            // heightmap 存相对 minY 偏移（int 域），+ minY 还原世界 Y
            long value = (long) this.heightmap.get(i) + this.minY;
            if (value > maxValue) {
                maxValue = value;
            }
        }

        return this.extendSourcesBelowWorld(maxValue);
    }

    private void fill(final long lowestSourceY) {
        int value = Math.toIntExact(lowestSourceY - this.minY);

        for (int i = 0; i < this.heightmap.getSize(); i++) {
            this.heightmap.set(i, value);
        }
    }

    private void set(final int index, final long value) {
        // BitStorage 只支持 int：存相对偏移（世界高度量级，安全）
        this.heightmap.set(index, Math.toIntExact(value - this.minY));
    }

    private long get(final int index) {
        return (long) this.heightmap.get(index) + this.minY;
    }

    private long extendSourcesBelowWorld(final long value) {
        return value == this.minY ? NEGATIVE_INFINITY : value;
    }

    private static int index(final int x, final int z) {
        return x + z * 16;
    }
}