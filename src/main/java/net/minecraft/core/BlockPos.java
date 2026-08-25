package net.minecraft.core;

import com.google.common.collect.AbstractIterator;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.concurrent.Immutable;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.MinecraftTools.Math._256Bit.Int256;
import net.MinecraftTools.Math._256Bit.util.Vec3d256;
import org.apache.commons.lang3.Validate;

/**
 * BlockPos — 方块坐标（MCRe NoiseFarlands 全面 Long 化版）
 *
 * <p>原版用 asLong() 将 (int x, int y, int z) 打包进 long（26+12+26 位），坐标上限被锁死在
 * ±33,554,431（2^25）。本版：移除打包系统，BlockPos 对象本身即键（不可变 + hashCode/equals），
 * 坐标升级为 long（突破 2^31 边界），{@link MutableBlockPos#set(double, double, double)}
 * 用 {@link Mth#lfloor(double)} 防 double → int 饱和截断（远距离≥2^31 时保 64 位精度）。
 *
 * <p><b>API 破坏性变更</b>（相对 vanilla）：
 * <ul>
 *   <li>{@link #BlockPos(long, long, long)} 构造函数接受 long</li>
 *   <li>{@link #offset}/{@link #multiply}/{@link #relative}/{@link #above}/... 步长参数为 long</li>
 *   <li>{@link MutableBlockPos#set(long, long, long)} / {@code setX/setY/setZ} 接受 long</li>
 *   <li>{@code containing(double, double, double)} 用 {@link Mth#lfloor} 防饱和截断</li>
 *   <li>{@link #CODEC} 用 {@link Codec#LONG_STREAM}；{@link #STREAM_CODEC} 用 {@link ByteBufCodecs#VAR_LONG</li>
 *</ul>
 */
@Immutable
public class BlockPos extends Vec3i {
    public static final Codec<BlockPos> CODEC = Codec.LONG_STREAM
        .<BlockPos>comapFlatMap(
            input -> Util.fixedSize(input, 3).map(longs -> new BlockPos(longs[0], longs[1], longs[2])),
            pos -> LongStream.of(pos.getX(), pos.getY(), pos.getZ())
        )
        .stable();
    public static final StreamCodec<ByteBuf, BlockPos> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_LONG,
        BlockPos::getX,
        ByteBufCodecs.VAR_LONG,
        BlockPos::getY,
        ByteBufCodecs.VAR_LONG,
        BlockPos::getZ,
        BlockPos::new
    );
    public static final BlockPos ZERO = new BlockPos(0L, 0L, 0L);

    public BlockPos(final long x, final long y, final long z) {
        super(x, y, z);
    }

    public BlockPos(final Vec3i vec3i) {
        this(vec3i.getX(), vec3i.getY(), vec3i.getZ());
    }

    /**
     * MCRe：原版用 {@code Mth.floor}（double → int 饱和截断，≥ 2^31 时丢精度）。本版改用
     * {@link Mth#lfloor(double)} 返回 long，再传给 long 构造函数，保证 |x| ≥ 2^31 时正确。
     */
    public static BlockPos containing(final double x, final double y, final double z) {
        return new BlockPos(Mth.lfloor(x), Mth.lfloor(y), Mth.lfloor(z));
    }

    public static BlockPos containing(final Position pos) {
        return containing(pos.x(), pos.y(), pos.z());
    }

    public static BlockPos min(final BlockPos a, final BlockPos b) {
        return new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
    }

    public static BlockPos max(final BlockPos a, final BlockPos b) {
        return new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }

    public BlockPos offset(final long x, final long y, final long z) {
        return x == 0L && y == 0L && z == 0L ? this : new BlockPos(this.getX() + x, this.getY() + y, this.getZ() + z);
    }

    public BlockPos offset(final Vec3i vec) {
        return this.offset(vec.getX(), vec.getY(), vec.getZ());
    }

    public BlockPos subtract(final Vec3i vec) {
        return this.offset(-vec.getX(), -vec.getY(), -vec.getZ());
    }

    public BlockPos multiply(final long scale) {
        if (scale == 1L) {
            return this;
        } else {
            return scale == 0L ? ZERO : new BlockPos(this.getX() * scale, this.getY() * scale, this.getZ() * scale);
        }
    }

    public BlockPos above() {
        return this.relative(Direction.UP);
    }

    public BlockPos above(final long steps) {
        return this.relative(Direction.UP, steps);
    }

    public BlockPos below() {
        return this.relative(Direction.DOWN);
    }

    public BlockPos below(final long steps) {
        return this.relative(Direction.DOWN, steps);
    }

    public BlockPos north() {
        return this.relative(Direction.NORTH);
    }

    public BlockPos north(final long steps) {
        return this.relative(Direction.NORTH, steps);
    }

    public BlockPos south() {
        return this.relative(Direction.SOUTH);
    }

    public BlockPos south(final long steps) {
        return this.relative(Direction.SOUTH, steps);
    }

    public BlockPos west() {
        return this.relative(Direction.WEST);
    }

    public BlockPos west(final long steps) {
        return this.relative(Direction.WEST, steps);
    }

    public BlockPos east() {
        return this.relative(Direction.EAST);
    }

    public BlockPos east(final long steps) {
        return this.relative(Direction.EAST, steps);
    }

    public BlockPos relative(final Direction direction) {
        return new BlockPos(this.getX() + (long) direction.getStepX(), this.getY() + (long) direction.getStepY(), this.getZ() + (long) direction.getStepZ());
    }

    public BlockPos relative(final Direction direction, final long steps) {
        return steps == 0L
            ? this
            : new BlockPos(
                this.getX() + (long) direction.getStepX() * steps,
                this.getY() + (long) direction.getStepY() * steps,
                this.getZ() + (long) direction.getStepZ() * steps
            );
    }

    public BlockPos relative(final Direction.Axis axis, final long steps) {
        if (steps == 0L) {
            return this;
        }

        long xStep = axis == Direction.Axis.X ? steps : 0L;
        long yStep = axis == Direction.Axis.Y ? steps : 0L;
        long zStep = axis == Direction.Axis.Z ? steps : 0L;
        return new BlockPos(this.getX() + xStep, this.getY() + yStep, this.getZ() + zStep);
    }

    public BlockPos rotate(final Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> new BlockPos(-this.getZ(), this.getY(), this.getX());
            case CLOCKWISE_180 -> new BlockPos(-this.getX(), this.getY(), -this.getZ());
            case COUNTERCLOCKWISE_90 -> new BlockPos(this.getZ(), this.getY(), -this.getX());
            case NONE -> this;
        };
    }

    public BlockPos cross(final Vec3i upVector) {
        return new BlockPos(
            this.getY() * upVector.getZ() - this.getZ() * upVector.getY(),
            this.getZ() * upVector.getX() - this.getX() * upVector.getZ(),
            this.getX() * upVector.getY() - this.getY() * upVector.getX()
        );
    }

    public BlockPos atY(final long y) {
        return new BlockPos(this.getX(), y, this.getZ());
    }

    public BlockPos immutable() {
        return this;
    }

    public BlockPos.MutableBlockPos mutable() {
        return new BlockPos.MutableBlockPos(this.getX(), this.getY(), this.getZ());
    }

    public Vec3 clampLocationWithin(final Vec3 location) {
        return new Vec3(
            Mth.clamp(location.x, this.getX() + 1.0E-5F, this.getX() + 1.0 - 1.0E-5F),
            Mth.clamp(location.y, this.getY() + 1.0E-5F, this.getY() + 1.0 - 1.0E-5F),
            Mth.clamp(location.z, this.getZ() + 1.0E-5F, this.getZ() + 1.0 - 1.0E-5F)
        );
    }

    public static Iterable<BlockPos> randomInCube(final RandomSource random, final int limit, final BlockPos center, final long sizeToScanInAllDirections) {
        return randomBetweenClosed(
            random,
            limit,
            center.getX() - sizeToScanInAllDirections,
            center.getY() - sizeToScanInAllDirections,
            center.getZ() - sizeToScanInAllDirections,
            center.getX() + sizeToScanInAllDirections,
            center.getY() + sizeToScanInAllDirections,
            center.getZ() + sizeToScanInAllDirections
        );
    }

    @Deprecated
    public static Stream<BlockPos> squareOutSouthEast(final BlockPos from) {
        return Stream.of(from, from.south(), from.east(), from.south().east());
    }

    /**
     * MCRe：原版用 int 限制（width/height/depth 在 ±2^31 区域）。本版限速仍为 int（limit），
     * 坐标区间改 long，远距离场景可表达超过 2^31 块的区域。
     */
    public static Iterable<BlockPos> randomBetweenClosed(
        final RandomSource random, final int limit, final long minX, final long minY, final long minZ, final long maxX, final long maxY, final long maxZ
    ) {
        long width = maxX - minX + 1L;
        long height = maxY - minY + 1L;
        long depth = maxZ - minZ + 1L;
        return () -> new AbstractIterator<BlockPos>() {
            private final BlockPos.MutableBlockPos nextPos = new BlockPos.MutableBlockPos();
            private int counter = limit;

            protected BlockPos computeNext() {
                if (this.counter <= 0) {
                    return this.endOfData();
                }

                BlockPos next = this.nextPos.set(
                    minX + (long) random.nextInt((int) Math.min(width, Integer.MAX_VALUE)),
                    minY + (long) random.nextInt((int) Math.min(height, Integer.MAX_VALUE)),
                    minZ + (long) random.nextInt((int) Math.min(depth, Integer.MAX_VALUE))
                );
                this.counter--;
                return next;
            }
        };
    }

    /**
     * MCRe：原版 reachX/Y/Z 是 int（曼哈顿范围限制）。本版改 long，远距离 AI/路径探索可
     * 突破 ±2^15 块的限制；内部循环变量也升级 long。
     */
    public static Iterable<BlockPos> withinManhattan(final BlockPos origin, final long reachX, final long reachY, final long reachZ) {
        long maxDepth = reachX + reachY + reachZ;
        long originX = origin.getX();
        long originY = origin.getY();
        long originZ = origin.getZ();
        return () -> new AbstractIterator<BlockPos>() {
            private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            private long currentDepth;
            private long maxX;
            private long maxY;
            private long x;
            private long y;
            private boolean zMirror;

            protected BlockPos computeNext() {
                if (this.zMirror) {
                    this.zMirror = false;
                    this.cursor.setZ(originZ - (this.cursor.getZ() - originZ));
                    return this.cursor;
                }

                BlockPos found;
                for (found = null; found == null; this.y++) {
                    if (this.y > this.maxY) {
                        this.x++;
                        if (this.x > this.maxX) {
                            this.currentDepth++;
                            if (this.currentDepth > maxDepth) {
                                return this.endOfData();
                            }

                            this.maxX = Math.min(reachX, this.currentDepth);
                            this.x = -this.maxX;
                        }

                        this.maxY = Math.min(reachY, this.currentDepth - Math.abs(this.x));
                        this.y = -this.maxY;
                    }

                    long xx = this.x;
                    long yy = this.y;
                    long zz = this.currentDepth - Math.abs(xx) - Math.abs(yy);
                    if (zz <= reachZ) {
                        this.zMirror = zz != 0L;
                        found = this.cursor.set(originX + xx, originY + yy, originZ + zz);
                    }
                }

                return found;
            }
        };
    }

    public static Optional<BlockPos> findClosestMatch(
        final BlockPos startPos, final long horizontalSearchRadius, final long verticalSearchRadius, final Predicate<BlockPos> predicate
    ) {
        for (BlockPos blockPos : withinManhattan(startPos, horizontalSearchRadius, verticalSearchRadius, horizontalSearchRadius)) {
            if (predicate.test(blockPos)) {
                return Optional.of(blockPos);
            }
        }

        return Optional.empty();
    }

    public static Stream<BlockPos> withinManhattanStream(final BlockPos origin, final long reachX, final long reachY, final long reachZ) {
        return StreamSupport.stream(withinManhattan(origin, reachX, reachY, reachZ).spliterator(), false);
    }

    public static Iterable<BlockPos> betweenClosed(final AABB box) {
        BlockPos startPos = containing(box.minX, box.minY, box.minZ);
        BlockPos endPos = containing(box.maxX, box.maxY, box.maxZ);
        return betweenClosed(startPos, endPos);
    }

    public static Iterable<BlockPos> betweenClosed(final BlockPos a, final BlockPos b) {
        return betweenClosed(
            Math.min(a.getX(), b.getX()),
            Math.min(a.getY(), b.getY()),
            Math.min(a.getZ(), b.getZ()),
            Math.max(a.getX(), b.getX()),
            Math.max(a.getY(), b.getY()),
            Math.max(a.getZ(), b.getZ())
        );
    }

    public static Stream<BlockPos> betweenClosedStream(final BlockPos a, final BlockPos b) {
        return StreamSupport.stream(betweenClosed(a, b).spliterator(), false);
    }

    public static Stream<BlockPos> betweenClosedStream(final BoundingBox boundingBox) {
        return betweenClosedStream(
            Math.min(boundingBox.minX(), boundingBox.maxX()),
            Math.min(boundingBox.minY(), boundingBox.maxY()),
            Math.min(boundingBox.minZ(), boundingBox.maxZ()),
            Math.max(boundingBox.minX(), boundingBox.maxX()),
            Math.max(boundingBox.minY(), boundingBox.maxY()),
            Math.max(boundingBox.minZ(), boundingBox.maxZ())
        );
    }

    public static Stream<BlockPos> betweenClosedStream(final AABB box) {
        return betweenClosedStream(Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ), Mth.floor(box.maxX), Mth.floor(box.maxY), Mth.floor(box.maxZ));
    }

    /**
     * MCRe：坐标区间改 long，支持 ±2^31 区域。
     */
    public static Stream<BlockPos> betweenClosedStream(final long minX, final long minY, final long minZ, final long maxX, final long maxY, final long maxZ) {
        return StreamSupport.stream(betweenClosed(minX, minY, minZ, maxX, maxY, maxZ).spliterator(), false);
    }

    public static Iterable<BlockPos> betweenClosed(final long minX, final long minY, final long minZ, final long maxX, final long maxY, final long maxZ) {
        long width = maxX - minX + 1L;
        long height = maxY - minY + 1L;
        long depth = maxZ - minZ + 1L;
        long end = width * height * depth;
        return () -> new AbstractIterator<BlockPos>() {
            private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            private long index;

            protected BlockPos computeNext() {
                if (this.index == end) {
                    return this.endOfData();
                }

                long x = this.index % width;
                long slice = this.index / width;
                long y = slice % height;
                long z = slice / height;
                this.index++;
                return this.cursor.set(minX + x, minY + y, minZ + z);
            }
        };
    }

    public static Iterable<BlockPos> neighborColumn(final long startX, final long startY, final long startZ, final long endY) {
        long yDirection = endY > startY ? 1L : -1L;
        long height = Math.abs(endY - startY) + 1L;
        Vec3i[] steps = new Vec3i[]{
            new Vec3i(0L, 0L, 0L), Direction.NORTH.getUnitVec3i(), Direction.EAST.getUnitVec3i(), Direction.SOUTH.getUnitVec3i(), Direction.WEST.getUnitVec3i()
        };
        long stepCount = steps.length * height;
        return () -> new AbstractIterator<BlockPos>() {
            private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            private long index;

            protected BlockPos computeNext() {
                if (this.index == stepCount) {
                    return this.endOfData();
                }

                long y = this.index % height;
                long stepIndex = this.index / height;
                Vec3i step = steps[(int) stepIndex];
                this.index++;
                return this.cursor.set(startX + step.getX(), startY + y * yDirection, startZ + step.getZ());
            }
        };
    }

    public static Iterable<BlockPos.MutableBlockPos> spiralAround(
        final BlockPos center, final long radius, final Direction firstDirection, final Direction secondDirection
    ) {
        Validate.validState(firstDirection.getAxis() != secondDirection.getAxis(), "The two directions cannot be on the same axis");
        return () -> new AbstractIterator<BlockPos.MutableBlockPos>() {
            private final Direction[] directions = new Direction[]{
                firstDirection, secondDirection, firstDirection.getOpposite(), secondDirection.getOpposite()
            };
            private final BlockPos.MutableBlockPos cursor = center.mutable().move(secondDirection);
            private final long legs = 4L * radius;
            private long leg = -1L;
            private long legSize;
            private long legIndex;
            private long lastX = this.cursor.getX();
            private long lastY = this.cursor.getY();
            private long lastZ = this.cursor.getZ();

            protected BlockPos.MutableBlockPos computeNext() {
                this.cursor.set(this.lastX, this.lastY, this.lastZ).move(this.directions[(int) ((this.leg + 4L) % 4L)]);
                this.lastX = this.cursor.getX();
                this.lastY = this.cursor.getY();
                this.lastZ = this.cursor.getZ();
                if (this.legIndex >= this.legSize) {
                    if (this.leg >= this.legs) {
                        return this.endOfData();
                    }

                    this.leg++;
                    this.legIndex = 0L;
                    this.legSize = this.leg / 2L + 1L;
                }

                this.legIndex++;
                return this.cursor;
            }
        };
    }

    /**
     * 广度优先遍历（对象化：visited 用 HashSet<BlockPos>，替代原 LongSet 打包）
     */
    public static int breadthFirstTraversal(
        final BlockPos startPos,
        final long maxDepth,
        final int maxCount,
        final BiConsumer<BlockPos, Consumer<BlockPos>> neighbourProvider,
        final Function<BlockPos, BlockPos.TraversalNodeStatus> nodeProcessor
    ) {
        record Node(BlockPos pos, long depth) {
        }

        Queue<Node> nodes = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        nodes.add(new Node(startPos, 0L));
        int count = 0;

        while (!nodes.isEmpty()) {
            Node node = nodes.poll();
            BlockPos currentPos = node.pos;
            long depth = node.depth;
            if (visited.add(currentPos.immutable())) {
                BlockPos.TraversalNodeStatus next = nodeProcessor.apply(currentPos);
                if (next != BlockPos.TraversalNodeStatus.SKIP) {
                    if (next == BlockPos.TraversalNodeStatus.STOP) {
                        break;
                    }

                    if (++count >= maxCount) {
                        return count;
                    }

                    if (depth < maxDepth) {
                        neighbourProvider.accept(currentPos, pos -> nodes.add(new Node(pos.immutable(), depth + 1L)));
                    }
                }
            }
        }

        return count;
    }

    public static Iterable<BlockPos> betweenCornersInDirection(final AABB aabb, final Vec3 direction) {
        Vec3 minCorner = aabb.getMinPosition();
        long firstCornerX = Mth.lfloor(minCorner.x());
        long firstCornerY = Mth.lfloor(minCorner.y());
        long firstCornerZ = Mth.lfloor(minCorner.z());
        Vec3 maxCorner = aabb.getMaxPosition();
        long secondCornerX = Mth.lfloor(maxCorner.x());
        long secondCornerY = Mth.lfloor(maxCorner.y());
        long secondCornerZ = Mth.lfloor(maxCorner.z());
        return betweenCornersInDirection(firstCornerX, firstCornerY, firstCornerZ, secondCornerX, secondCornerY, secondCornerZ, direction);
    }

    public static Iterable<BlockPos> betweenCornersInDirection(final BlockPos firstCorner, final BlockPos secondCorner, final Vec3 direction) {
        return betweenCornersInDirection(
            firstCorner.getX(), firstCorner.getY(), firstCorner.getZ(), secondCorner.getX(), secondCorner.getY(), secondCorner.getZ(), direction
        );
    }

    public static Iterable<BlockPos> betweenCornersInDirection(
        final long firstCornerX,
        final long firstCornerY,
        final long firstCornerZ,
        final long secondCornerX,
        final long secondCornerY,
        final long secondCornerZ,
        final Vec3 direction
    ) {
        long minCornerX = Math.min(firstCornerX, secondCornerX);
        long minCornerY = Math.min(firstCornerY, secondCornerY);
        long minCornerZ = Math.min(firstCornerZ, secondCornerZ);
        long maxCornerX = Math.max(firstCornerX, secondCornerX);
        long maxCornerY = Math.max(firstCornerY, secondCornerY);
        long maxCornerZ = Math.max(firstCornerZ, secondCornerZ);
        long diffX = maxCornerX - minCornerX;
        long diffY = maxCornerY - minCornerY;
        long diffZ = maxCornerZ - minCornerZ;
        long startCornerX = direction.x >= 0.0 ? minCornerX : maxCornerX;
        long startCornerY = direction.y >= 0.0 ? minCornerY : maxCornerY;
        long startCornerZ = direction.z >= 0.0 ? minCornerZ : maxCornerZ;
        List<Direction.Axis> axes = Direction.axisStepOrder(direction);
        Direction.Axis firstVisitAxis = axes.get(0);
        Direction.Axis secondVisitAxis = axes.get(1);
        Direction.Axis thirdVisitAxis = axes.get(2);
        Direction firstVisitDir = direction.get(firstVisitAxis) >= 0.0 ? firstVisitAxis.getPositive() : firstVisitAxis.getNegative();
        Direction secondVisitDir = direction.get(secondVisitAxis) >= 0.0 ? secondVisitAxis.getPositive() : secondVisitAxis.getNegative();
        Direction thirdVisitDir = direction.get(thirdVisitAxis) >= 0.0 ? thirdVisitAxis.getPositive() : thirdVisitAxis.getNegative();
        long firstMax = firstVisitAxis.choose(diffX, diffY, diffZ);
        long secondMax = secondVisitAxis.choose(diffX, diffY, diffZ);
        long thirdMax = thirdVisitAxis.choose(diffX, diffY, diffZ);
        return () -> new AbstractIterator<BlockPos>() {
            private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            private long firstIndex;
            private long secondIndex;
            private long thirdIndex;
            private boolean end;
            private final long firstDirX = firstVisitDir.getStepX();
            private final long firstDirY = firstVisitDir.getStepY();
            private final long firstDirZ = firstVisitDir.getStepZ();
            private final long secondDirX = secondVisitDir.getStepX();
            private final long secondDirY = secondVisitDir.getStepY();
            private final long secondDirZ = secondVisitDir.getStepZ();
            private final long thirdDirX = thirdVisitDir.getStepX();
            private final long thirdDirY = thirdVisitDir.getStepY();
            private final long thirdDirZ = thirdVisitDir.getStepZ();

            protected BlockPos computeNext() {
                if (this.end) {
                    return this.endOfData();
                }

                this.cursor
                    .set(
                        startCornerX + this.firstDirX * this.firstIndex + this.secondDirX * this.secondIndex + this.thirdDirX * this.thirdIndex,
                        startCornerY + this.firstDirY * this.firstIndex + this.secondDirY * this.secondIndex + this.thirdDirY * this.thirdIndex,
                        startCornerZ + this.firstDirZ * this.firstIndex + this.secondDirZ * this.secondIndex + this.thirdDirZ * this.thirdIndex
                    );
                if (this.thirdIndex < thirdMax) {
                    this.thirdIndex++;
                } else if (this.secondIndex < secondMax) {
                    this.secondIndex++;
                    this.thirdIndex = 0L;
                } else if (this.firstIndex < firstMax) {
                    this.firstIndex++;
                    this.thirdIndex = 0L;
                    this.secondIndex = 0L;
                } else {
                    this.end = true;
                }

                return this.cursor;
            }
        };
    }

    // ═══════════ 256-bit 适配（MCRe NoiseFarlands） ═══════════

    /** 方块坐标精确转 Int256 */
    public Int256 x256() {
        return Int256.of(this.getX());
    }

    public Int256 y256() {
        return Int256.of(this.getY());
    }

    public Int256 z256() {
        return Int256.of(this.getZ());
    }

    /** 转 256-bit 向量 */
    public Vec3d256 to256() {
        return Vec3d256.ofInt(this.x256(), this.y256(), this.z256());
    }

    /** 256-bit 向量 → BlockPos（floor） */
    public static BlockPos from256(final Vec3d256 pos) {
        return new BlockPos(pos.x.floor().longValue(), pos.y.floor().longValue(), pos.z.floor().longValue());
    }

    public static class MutableBlockPos extends BlockPos {
        public MutableBlockPos() {
            this(0L, 0L, 0L);
        }

        public MutableBlockPos(final long x, final long y, final long z) {
            super(x, y, z);
        }

        /**
         * MCRe：原版用 {@code Mth.floor}（double → int 饱和截断）。本版改用 {@link Mth#lfloor}
         * 返回 long，避免 |x| ≥ 2^31 时所有远距离坐标坍塌到 {@link Integer#MAX_VALUE}。
         */
        public MutableBlockPos(final double x, final double y, final double z) {
            this(Mth.lfloor(x), Mth.lfloor(y), Mth.lfloor(z));
        }

        @Override
        public BlockPos offset(final long x, final long y, final long z) {
            return super.offset(x, y, z).immutable();
        }

        @Override
        public BlockPos multiply(final long scale) {
            return super.multiply(scale).immutable();
        }

        @Override
        public BlockPos relative(final Direction direction, final long steps) {
            return super.relative(direction, steps).immutable();
        }

        @Override
        public BlockPos relative(final Direction.Axis axis, final long steps) {
            return super.relative(axis, steps).immutable();
        }

        @Override
        public BlockPos rotate(final Rotation rotation) {
            return super.rotate(rotation).immutable();
        }

        public BlockPos.MutableBlockPos set(final long x, final long y, final long z) {
            this.setX(x);
            this.setY(y);
            this.setZ(z);
            return this;
        }

        /** MCRe：double → long 用 {@link Mth#lfloor} 防饱和截断 */
        public BlockPos.MutableBlockPos set(final double x, final double y, final double z) {
            return this.set(Mth.lfloor(x), Mth.lfloor(y), Mth.lfloor(z));
        }

        public BlockPos.MutableBlockPos set(final Vec3i vec) {
            return this.set(vec.getX(), vec.getY(), vec.getZ());
        }

        public BlockPos.MutableBlockPos set(final AxisCycle transform, final long x, final long y, final long z) {
            return this.set(
                transform.cycle(x, y, z, Direction.Axis.X),
                transform.cycle(x, y, z, Direction.Axis.Y),
                transform.cycle(x, y, z, Direction.Axis.Z)
            );
        }

        public BlockPos.MutableBlockPos setWithOffset(final Vec3i pos, final Direction direction) {
            return this.set(
                pos.getX() + (long) direction.getStepX(),
                pos.getY() + (long) direction.getStepY(),
                pos.getZ() + (long) direction.getStepZ()
            );
        }

        public BlockPos.MutableBlockPos setWithOffset(final Vec3i pos, final long x, final long y, final long z) {
            return this.set(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
        }

        public BlockPos.MutableBlockPos setWithOffset(final Vec3i pos, final Vec3i offset) {
            return this.set(pos.getX() + offset.getX(), pos.getY() + offset.getY(), pos.getZ() + offset.getZ());
        }

        public BlockPos.MutableBlockPos move(final Direction direction) {
            return this.move(direction, 1L);
        }

        public BlockPos.MutableBlockPos move(final Direction direction, final long steps) {
            return this.set(
                this.getX() + (long) direction.getStepX() * steps,
                this.getY() + (long) direction.getStepY() * steps,
                this.getZ() + (long) direction.getStepZ() * steps
            );
        }

        public BlockPos.MutableBlockPos move(final long x, final long y, final long z) {
            return this.set(this.getX() + x, this.getY() + y, this.getZ() + z);
        }

        public BlockPos.MutableBlockPos move(final Vec3i pos) {
            return this.set(this.getX() + pos.getX(), this.getY() + pos.getY(), this.getZ() + pos.getZ());
        }

        public BlockPos.MutableBlockPos clamp(final Direction.Axis axis, final long minimum, final long maximum) {
            return switch (axis) {
                case X -> this.set(Mth.clamp(this.getX(), minimum, maximum), this.getY(), this.getZ());
                case Y -> this.set(this.getX(), Mth.clamp(this.getY(), minimum, maximum), this.getZ());
                case Z -> this.set(this.getX(), this.getY(), Mth.clamp(this.getZ(), minimum, maximum));
            };
        }

        public BlockPos.MutableBlockPos setX(final long x) {
            super.setX(x);
            return this;
        }

        public BlockPos.MutableBlockPos setY(final long y) {
            super.setY(y);
            return this;
        }

        public BlockPos.MutableBlockPos setZ(final long z) {
            super.setZ(z);
            return this;
        }

        @Override
        public BlockPos immutable() {
            return new BlockPos(this);
        }
    }

    public enum TraversalNodeStatus {
        ACCEPT,
        SKIP,
        STOP;
    }
}
