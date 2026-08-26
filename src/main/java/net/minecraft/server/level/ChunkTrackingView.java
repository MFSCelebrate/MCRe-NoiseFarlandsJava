package net.minecraft.server.level;

import com.google.common.annotations.VisibleForTesting;
import java.util.function.Consumer;
import net.minecraft.world.level.ChunkPos;

public interface ChunkTrackingView {
    ChunkTrackingView EMPTY = new ChunkTrackingView() {
        @Override
        // MCRe NoiseFarlands: chunk 坐标 Long 化
        public boolean contains(final long chunkX, final long chunkZ, final boolean includeNeighbors) {
            return false;
        }

        @Override
        public void forEach(final Consumer<ChunkPos> consumer) {
        }
    };

    static ChunkTrackingView of(final ChunkPos center, final int radius) {
        return new ChunkTrackingView.Positioned(center, radius);
    }

    static void difference(final ChunkTrackingView from, final ChunkTrackingView to, final Consumer<ChunkPos> onEnter, final Consumer<ChunkPos> onLeave) {
        if (!from.equals(to)) {
            if (from instanceof ChunkTrackingView.Positioned last && to instanceof ChunkTrackingView.Positioned next && last.squareIntersects(next)) {
                int minX = Math.min(last.minX(), next.minX());
                int minZ = Math.min(last.minZ(), next.minZ());
                int maxX = Math.max(last.maxX(), next.maxX());
                int maxZ = Math.max(last.maxZ(), next.maxZ());

                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        boolean saw = last.contains(x, z);
                        boolean sees = next.contains(x, z);
                        if (saw != sees) {
                            if (sees) {
                                onEnter.accept(new ChunkPos(x, z));
                            } else {
                                onLeave.accept(new ChunkPos(x, z));
                            }
                        }
                    }
                }
            } else {
                from.forEach(onLeave);
                to.forEach(onEnter);
            }
        }
    }

    default boolean contains(final ChunkPos pos) {
        return this.contains((int)pos.x(), (int)pos.z());
    }

    // MCRe NoiseFarlands
    default boolean contains(final long x, final long z) {
        return this.contains(x, z, true);
    }

    boolean contains(long chunkX, long chunkZ, boolean includeNeighbors);

    void forEach(Consumer<ChunkPos> consumer);

    // MCRe NoiseFarlands: chunk 坐标 Long 化
    default boolean isInViewDistance(final long chunkX, final long chunkZ) {
        return this.contains(chunkX, chunkZ, false);
    }

    // MCRe NoiseFarlands: chunk 坐标 Long 化
    static boolean isInViewDistance(final long centerX, final long centerZ, final int viewDistance, final long chunkX, final long chunkZ) {
        return isWithinDistance(centerX, centerZ, viewDistance, chunkX, chunkZ, false);
    }

    // MCRe NoiseFarlands: chunk 坐标 Long 化
    static boolean isWithinDistance(
        final long centerX, final long centerZ, final int viewDistance, final long chunkX, final long chunkZ, final boolean includeNeighbors
    ) {
        int bufferRange = includeNeighbors ? 2 : 1;
        long deltaX = Math.max(0, Math.abs(chunkX - centerX) - bufferRange);
        long deltaZ = Math.max(0, Math.abs(chunkZ - centerZ) - bufferRange);
        long distanceSquared = deltaX * deltaX + deltaZ * deltaZ;
        int radiusSquared = viewDistance * viewDistance;
        return distanceSquared < radiusSquared;
    }

    record Positioned(ChunkPos center, int viewDistance) implements ChunkTrackingView {
        private int minX() {
            return (int)this.center.x() - this.viewDistance - 1;
        }

        private int minZ() {
            return (int)this.center.z() - this.viewDistance - 1;
        }

        private int maxX() {
            return (int)this.center.x() + this.viewDistance + 1;
        }

        private int maxZ() {
            return (int)this.center.z() + this.viewDistance + 1;
        }

        @VisibleForTesting
        boolean squareIntersects(final ChunkTrackingView.Positioned other) {
            return this.minX() <= other.maxX() && this.maxX() >= other.minX() && this.minZ() <= other.maxZ() && this.maxZ() >= other.minZ();
        }

        @Override
        // MCRe NoiseFarlands: chunk 坐标 Long 化
        public boolean contains(final long chunkX, final long chunkZ, final boolean includeNeighbors) {
            return ChunkTrackingView.isWithinDistance((int)this.center.x(), (int)this.center.z(), this.viewDistance, chunkX, chunkZ, includeNeighbors);
        }

        @Override
        public void forEach(final Consumer<ChunkPos> consumer) {
            for (int x = this.minX(); x <= this.maxX(); x++) {
                for (int z = this.minZ(); z <= this.maxZ(); z++) {
                    if (this.contains(x, z)) {
                        consumer.accept(new ChunkPos(x, z));
                    }
                }
            }
        }
    }
}