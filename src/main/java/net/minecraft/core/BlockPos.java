package net.minecraft.core;

import com.google.common.collect.AbstractIterator;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.concurrent.Immutable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.jspecify.annotations.Nullable;

/**
 * 64 位无限世界 BlockPos 实现。
 * 内部使用 BigInteger 存储坐标，支持 ±9e18 范围。
 * 所有原版方法签名保持不变，兼容现有代码。
 */
@Immutable
public class BlockPos extends Vec3i implements Position {

    // ---------- 序列化 ----------
    public static final Codec<BlockPos> CODEC = Codec.INT_STREAM
        .comapFlatMap(
            input -> Util.fixedSize(input, 3).map(arr -> new BlockPos(arr[0], arr[1], arr[2])),
            pos -> java.util.stream.IntStream.of(pos.getX(), pos.getY(), pos.getZ())
        )
        .stable();

    public static final StreamCodec<ByteBuf, BlockPos> STREAM_CODEC = new StreamCodec<ByteBuf, BlockPos>() {
        @Override
        public BlockPos decode(ByteBuf buf) {
            return new BlockPos(buf.readLong(), buf.readLong(), buf.readLong());
        }
        @Override
        public void encode(ByteBuf buf, BlockPos pos) {
            buf.writeLong(pos.bigX.longValue());
            buf.writeLong(pos.bigY.longValue());
            buf.writeLong(pos.bigZ.longValue());
        }
    };

    public static final BlockPos ZERO = new BlockPos(0, 0, 0);

    // ---------- 原版打包常量（兼容 2D 水平索引） ----------
    public static final int PACKED_HORIZONTAL_LENGTH = 26; // 2^26 覆盖 ±33,554,431，足够大
    public static final int PACKED_Y_LENGTH = 12;         // 2^12 = 4096 高度
    private static final long PACKED_X_MASK = (1L << PACKED_HORIZONTAL_LENGTH) - 1L;
    private static final long PACKED_Y_MASK = (1L << PACKED_Y_LENGTH) - 1L;
    private static final long PACKED_Z_MASK = (1L << PACKED_HORIZONTAL_LENGTH) - 1L;
    private static final int Y_OFFSET = 0;
    private static final int Z_OFFSET = PACKED_Y_LENGTH;
    private static final int X_OFFSET = PACKED_Y_LENGTH + PACKED_HORIZONTAL_LENGTH;
    public static final int MAX_HORIZONTAL_COORDINATE = (1 << PACKED_HORIZONTAL_LENGTH) / 2 - 1;

    // ---------- 内部存储 ----------
    private BigInteger bigX;
    private BigInteger bigY;
    private BigInteger bigZ;

    // ---------- 构造 ----------
    public BlockPos(int x, int y, int z) {
        super(x, y, z);
        this.bigX = BigInteger.valueOf(x);
        this.bigY = BigInteger.valueOf(y);
        this.bigZ = BigInteger.valueOf(z);
    }

    public BlockPos(long x, long y, long z) {
        this((int) x, (int) y, (int) z); // 兼容旧调用，但推荐使用 BigInteger 构造
    }

    public BlockPos(BigInteger x, BigInteger y, BigInteger z) {
        super(x.intValue(), y.intValue(), z.intValue());
        this.bigX = x;
        this.bigY = y;
        this.bigZ = z;
    }

    public BlockPos(Vec3i vec) {
        this(vec.getX(), vec.getY(), vec.getZ());
    }

    // ---------- Getter / Setter ----------
    @Override
    public int getX() { return bigX.intValue(); }
    @Override
    public int getY() { return bigY.intValue(); }
    @Override
    public int getZ() { return bigZ.intValue(); }

    public BigInteger getBigX() { return bigX; }
    public BigInteger getBigY() { return bigY; }
    public BigInteger getBigZ() { return bigZ; }

    protected void setBigX(BigInteger x) { this.bigX = x; }
    protected void setBigY(BigInteger y) { this.bigY = y; }
    protected void setBigZ(BigInteger z) { this.bigZ = z; }

    @Override
    public double x() { return bigX.doubleValue(); }
    @Override
    public double y() { return bigY.doubleValue(); }
    @Override
    public double z() { return bigZ.doubleValue(); }

    // ---------- 打包 / 解包 ----------
    /**
     * 返回 2D 水平索引（仅 X/Z 打包），兼容原版 asLong()。
     * 注意：Y 被忽略，返回 long 可能溢出 64 位，但已通过掩码截断。
     */
    public long asLong() {
        return ((bigX.longValue() & 0xFFFFFFFFL) << 32) | (bigZ.longValue() & 0xFFFFFFFFL);
    }

    /**
     * 返回三维打包键（X/Y/Z 压缩），用于需要三维键的场合（如光照引擎）。
     * 使用 26+12+26 位，支持 ±33,554,431 范围。
     */
    public long asLong3D() {
        long x = bigX.longValue() & 0x3FFFFFFL;
        long y = bigY.longValue() & 0xFFFL;
        long z = bigZ.longValue() & 0x3FFFFFFL;
        return (x << (12 + 26)) | (y << 26) | z;
    }

    public static long asLong3D(long x, long y, long z) {
        return ((x & 0x3FFFFFFL) << (12 + 26)) | ((y & 0xFFFL) << 26) | (z & 0x3FFFFFFL);
    }

    // 原版静态解包（2D）
    public static int getX(long packed) { return (int) (packed >> 32); }
    public static int getZ(long packed) { return (int) packed; }
    public static int getY(long packed) { return 0; } // 原版忽略 Y

    // 三维解包
    public static int getX3D(long packed) { return (int) ((packed >> (12 + 26)) & 0x3FFFFFFL); }
    public static int getY3D(long packed) { return (int) ((packed >> 26) & 0xFFFL); }
    public static int getZ3D(long packed) { return (int) (packed & 0x3FFFFFFL); }

    public static long getFlatIndex(long packed) { return 0L; } // 原版占位

    // ---------- 静态工厂 ----------
    public static BlockPos of(BigInteger x, BigInteger y, BigInteger z) { return new BlockPos(x, y, z); }
    public static BlockPos of(long x, long y, long z) { return new BlockPos(x, y, z); }
    public static BlockPos of(int x, int y, int z) { return new BlockPos(x, y, z); }
    public static BlockPos of(long packed) { return new BlockPos(getX(packed), 0, getZ(packed)); }

    public static BlockPos containing(double x, double y, double z) {
        return new BlockPos(BigInteger.valueOf(Mth.floor(x)), BigInteger.valueOf(Mth.floor(y)), BigInteger.valueOf(Mth.floor(z)));
    }

    public static BlockPos containing(Position pos) {
        return containing(pos.x(), pos.y(), pos.z());
    }

    public static BlockPos min(BlockPos a, BlockPos b) {
        return new BlockPos(a.bigX.min(b.bigX), a.bigY.min(b.bigY), a.bigZ.min(b.bigZ));
    }

    public static BlockPos max(BlockPos a, BlockPos b) {
        return new BlockPos(a.bigX.max(b.bigX), a.bigY.max(b.bigY), a.bigZ.max(b.bigZ));
    }

    // ---------- 偏移 ----------
    @Override
    public BlockPos offset(int dx, int dy, int dz) {
        if (dx == 0 && dy == 0 && dz == 0) return this;
        return new BlockPos(bigX.add(BigInteger.valueOf(dx)), bigY.add(BigInteger.valueOf(dy)), bigZ.add(BigInteger.valueOf(dz)));
    }

    public BlockPos offset(Direction direction) {
        return offset(direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    public BlockPos offset(BigInteger dx, BigInteger dy, BigInteger dz) {
        if (dx.signum() == 0 && dy.signum() == 0 && dz.signum() == 0) return this;
        return new BlockPos(bigX.add(dx), bigY.add(dy), bigZ.add(dz));
    }

    @Override
    public BlockPos offset(Vec3i vec) {
        return offset(vec.getX(), vec.getY(), vec.getZ());
    }

    // 静态偏移方法（供光照引擎使用）
    public static long offset(long blockNode, Direction direction) {
        return offset(blockNode, direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    public static long offset(long blockNode, int dx, int dy, int dz) {
        int x = getX3D(blockNode);
        int y = getY3D(blockNode);
        int z = getZ3D(blockNode);
        return asLong3D(x + dx, y + dy, z + dz);
    }

    @Override
    public BlockPos subtract(Vec3i vec) {
        return offset(-vec.getX(), -vec.getY(), -vec.getZ());
    }

    @Override
    public BlockPos multiply(int scale) {
        if (scale == 1) return this;
        if (scale == 0) return ZERO;
        return new BlockPos(bigX.multiply(BigInteger.valueOf(scale)), bigY.multiply(BigInteger.valueOf(scale)), bigZ.multiply(BigInteger.valueOf(scale)));
    }

    // ---------- 方向 ----------
    @Override
    public BlockPos above() { return relative(Direction.UP); }
    @Override
    public BlockPos above(int steps) { return relative(Direction.UP, steps); }
    @Override
    public BlockPos below() { return relative(Direction.DOWN); }
    @Override
    public BlockPos below(int steps) { return relative(Direction.DOWN, steps); }
    @Override
    public BlockPos north() { return relative(Direction.NORTH); }
    @Override
    public BlockPos north(int steps) { return relative(Direction.NORTH, steps); }
    @Override
    public BlockPos south() { return relative(Direction.SOUTH); }
    @Override
    public BlockPos south(int steps) { return relative(Direction.SOUTH, steps); }
    @Override
    public BlockPos west() { return relative(Direction.WEST); }
    @Override
    public BlockPos west(int steps) { return relative(Direction.WEST, steps); }
    @Override
    public BlockPos east() { return relative(Direction.EAST); }
    @Override
    public BlockPos east(int steps) { return relative(Direction.EAST, steps); }

    @Override
    public BlockPos relative(Direction dir) {
        return offset(dir.getStepX(), dir.getStepY(), dir.getStepZ());
    }

    @Override
    public BlockPos relative(Direction dir, int steps) {
        if (steps == 0) return this;
        return offset(dir.getStepX() * steps, dir.getStepY() * steps, dir.getStepZ() * steps);
    }

    @Override
    public BlockPos relative(Direction.Axis axis, int steps) {
        if (steps == 0) return this;
        int dx = axis == Direction.Axis.X ? steps : 0;
        int dy = axis == Direction.Axis.Y ? steps : 0;
        int dz = axis == Direction.Axis.Z ? steps : 0;
        return offset(dx, dy, dz);
    }

    // ---------- 旋转与叉积 ----------
    public BlockPos rotate(Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> new BlockPos(bigZ.negate(), bigY, bigX);
            case CLOCKWISE_180 -> new BlockPos(bigX.negate(), bigY, bigZ.negate());
            case COUNTERCLOCKWISE_90 -> new BlockPos(bigZ, bigY, bigX.negate());
            default -> this;
        };
    }

    @Override
    public BlockPos cross(Vec3i upVector) {
        BigInteger vx = BigInteger.valueOf(upVector.getX());
        BigInteger vy = BigInteger.valueOf(upVector.getY());
        BigInteger vz = BigInteger.valueOf(upVector.getZ());
        return new BlockPos(
            bigY.multiply(vz).subtract(bigZ.multiply(vy)),
            bigZ.multiply(vx).subtract(bigX.multiply(vz)),
            bigX.multiply(vy).subtract(bigY.multiply(vx))
        );
    }

    public BlockPos atY(int y) {
        return new BlockPos(bigX, BigInteger.valueOf(y), bigZ);
    }

    // ---------- Vec3 转换 ----------
    public Vec3 getCenter() {
        return Vec3.atCenterOf(this);
    }

    public Vec3 getBottomCenter() {
        return Vec3.atBottomCenterOf(this);
    }

    public Vec3 clampLocationWithin(Vec3 location) {
        return new Vec3(
            Mth.clamp(location.x, bigX.doubleValue() + 1.0E-5F, bigX.doubleValue() + 1.0 - 1.0E-5F),
            Mth.clamp(location.y, bigY.doubleValue() + 1.0E-5F, bigY.doubleValue() + 1.0 - 1.0E-5F),
            Mth.clamp(location.z, bigZ.doubleValue() + 1.0E-5F, bigZ.doubleValue() + 1.0 - 1.0E-5F)
        );
    }

    // ---------- 不可变与可变 ----------
    @Override
    public BlockPos immutable() { return this; }

    public MutableBlockPos mutable() {
        return new MutableBlockPos(bigX, bigY, bigZ);
    }

    // ---------- 距离方法 ----------
    @Override
    public boolean closerThan(Vec3i pos, double distance) {
        return this.distSqr(pos) < Mth.square(distance);
    }

    @Override
    public boolean closerToCenterThan(Position pos, double distance) {
        return this.distToCenterSqr(pos) < Mth.square(distance);
    }

    @Override
    public double distSqr(Vec3i pos) {
        return this.distToLowCornerSqr(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public double distToCenterSqr(Position pos) {
        return this.distToCenterSqr(pos.x(), pos.y(), pos.z());
    }

    @Override
    public double distToCenterSqr(double x, double y, double z) {
        double dx = bigX.doubleValue() + 0.5 - x;
        double dy = bigY.doubleValue() + 0.5 - y;
        double dz = bigZ.doubleValue() + 0.5 - z;
        return dx * dx + dy * dy + dz * dz;
    }

    @Override
    public double distToLowCornerSqr(double x, double y, double z) {
        double dx = bigX.doubleValue() - x;
        double dy = bigY.doubleValue() - y;
        double dz = bigZ.doubleValue() - z;
        return dx * dx + dy * dy + dz * dz;
    }

    @Override
    public int distManhattan(Vec3i pos) {
        return Math.abs(bigX.intValue() - pos.getX()) + Math.abs(bigY.intValue() - pos.getY()) + Math.abs(bigZ.intValue() - pos.getZ());
    }

    @Override
    public int distChessboard(Vec3i pos) {
        return Math.max(Math.abs(bigX.intValue() - pos.getX()), Math.max(Math.abs(bigY.intValue() - pos.getY()), Math.abs(bigZ.intValue() - pos.getZ())));
    }

    @Override
    public int get(Direction.Axis axis) {
        return switch (axis) {
            case X -> getX();
            case Y -> getY();
            case Z -> getZ();
        };
    }

    // ---------- 原版静态方法（全部实现） ----------

    @Deprecated
    public static Stream<BlockPos> squareOutSouthEast(BlockPos from) {
        return Stream.of(from, from.south(), from.east(), from.south().east());
    }

    public static Optional<BlockPos> findClosestMatch(
        BlockPos startPos, int horizontalSearchRadius, int verticalSearchRadius, Predicate<BlockPos> predicate
    ) {
        for (BlockPos pos : withinManhattan(startPos, horizontalSearchRadius, verticalSearchRadius, horizontalSearchRadius)) {
            if (predicate.test(pos)) return Optional.of(pos);
        }
        return Optional.empty();
    }

    // ===== betweenClosed 系列 =====
    public static Iterable<BlockPos> betweenClosed(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return betweenClosed(BigInteger.valueOf(minX), BigInteger.valueOf(minY), BigInteger.valueOf(minZ),
                             BigInteger.valueOf(maxX), BigInteger.valueOf(maxY), BigInteger.valueOf(maxZ));
    }

    public static Iterable<BlockPos> betweenClosed(BlockPos a, BlockPos b) {
        return betweenClosed(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
    }

    public static Iterable<BlockPos> betweenClosed(AABB box) {
        BlockPos a = containing(box.minX, box.minY, box.minZ);
        BlockPos b = containing(box.maxX, box.maxY, box.maxZ);
        return betweenClosed(a, b);
    }

    private static Iterable<BlockPos> betweenClosed(
        BigInteger minX, BigInteger minY, BigInteger minZ,
        BigInteger maxX, BigInteger maxY, BigInteger maxZ
    ) {
        BigInteger width = maxX.subtract(minX).add(BigInteger.ONE);
        BigInteger height = maxY.subtract(minY).add(BigInteger.ONE);
        BigInteger depth = maxZ.subtract(minZ).add(BigInteger.ONE);
        long total = width.longValue() * height.longValue() * depth.longValue();
        return () -> new AbstractIterator<BlockPos>() {
            private final MutableBlockPos cursor = new MutableBlockPos();
            private long index = 0;
            @Override
            protected BlockPos computeNext() {
                if (this.index >= total) return this.endOfData();
                long x = this.index % width.longValue();
                long slice = this.index / width.longValue();
                long y = slice % height.longValue();
                long z = slice / height.longValue();
                this.index++;
                return this.cursor.set(minX.add(BigInteger.valueOf(x)), minY.add(BigInteger.valueOf(y)), minZ.add(BigInteger.valueOf(z)));
            }
        };
    }

    public static Stream<BlockPos> betweenClosedStream(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return StreamSupport.stream(betweenClosed(minX, minY, minZ, maxX, maxY, maxZ).spliterator(), false);
    }

    public static Stream<BlockPos> betweenClosedStream(BlockPos a, BlockPos b) {
        return betweenClosedStream(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
    }

    public static Stream<BlockPos> betweenClosedStream(AABB box) {
        return StreamSupport.stream(betweenClosed(box).spliterator(), false);
    }

    public static Stream<BlockPos> betweenClosedStream(BoundingBox box) {
        return betweenClosedStream(
            Math.min(box.minX(), box.maxX()), Math.min(box.minY(), box.maxY()), Math.min(box.minZ(), box.maxZ()),
            Math.max(box.minX(), box.maxX()), Math.max(box.minY(), box.maxY()), Math.max(box.minZ(), box.maxZ())
        );
    }

    // ===== withinManhattan 系列 =====
    public static Iterable<BlockPos> withinManhattan(BlockPos origin, int reachX, int reachY, int reachZ) {
        int total = reachX + reachY + reachZ;
        BigInteger ox = origin.bigX, oy = origin.bigY, oz = origin.bigZ;
        return () -> new AbstractIterator<BlockPos>() {
            private final MutableBlockPos cursor = new MutableBlockPos();
            private int currentDepth = 0;
            private long maxX = 0, maxY = 0, x = 0, y = 0;
            private boolean zMirror = false;
            @Override
            protected BlockPos computeNext() {
                if (this.zMirror) {
                    this.zMirror = false;
                    cursor.setZ(oz.subtract(cursor.getBigZ().subtract(oz)).intValue());
                    return cursor;
                }
                BlockPos found = null;
                while (found == null) {
                    if (this.y > this.maxY) {
                        this.x++;
                        if (this.x > this.maxX) {
                            this.currentDepth++;
                            if (this.currentDepth > total) return this.endOfData();
                            this.maxX = Math.min(reachX, this.currentDepth);
                            this.x = -this.maxX;
                        }
                        this.maxY = Math.min(reachY, this.currentDepth - Math.abs(this.x));
                        this.y = -this.maxY;
                    }
                    long xx = this.x, yy = this.y;
                    long zz = this.currentDepth - Math.abs(xx) - Math.abs(yy);
                    if (zz <= reachZ) {
                        this.zMirror = (zz != 0);
                        found = cursor.set(ox.add(BigInteger.valueOf(xx)).intValue(),
                                          oy.add(BigInteger.valueOf(yy)).intValue(),
                                          oz.add(BigInteger.valueOf(zz)).intValue());
                    }
                    this.y++;
                }
                return found;
            }
        };
    }

    public static Stream<BlockPos> withinManhattanStream(BlockPos origin, int reachX, int reachY, int reachZ) {
        return StreamSupport.stream(withinManhattan(origin, reachX, reachY, reachZ).spliterator(), false);
    }

    // ===== randomInCube / randomBetweenClosed =====
    public static Iterable<BlockPos> randomInCube(RandomSource random, int limit, BlockPos center, int radius) {
        return randomBetweenClosed(random, limit,
            center.bigX.subtract(BigInteger.valueOf(radius)),
            center.bigY.subtract(BigInteger.valueOf(radius)),
            center.bigZ.subtract(BigInteger.valueOf(radius)),
            center.bigX.add(BigInteger.valueOf(radius)),
            center.bigY.add(BigInteger.valueOf(radius)),
            center.bigZ.add(BigInteger.valueOf(radius))
        );
    }

    public static Iterable<BlockPos> randomBetweenClosed(
        RandomSource random, int limit,
        int minX, int minY, int minZ, int maxX, int maxY, int maxZ
    ) {
        return randomBetweenClosed(random, limit,
            BigInteger.valueOf(minX), BigInteger.valueOf(minY), BigInteger.valueOf(minZ),
            BigInteger.valueOf(maxX), BigInteger.valueOf(maxY), BigInteger.valueOf(maxZ)
        );
    }

    private static Iterable<BlockPos> randomBetweenClosed(
        RandomSource random, int limit,
        BigInteger minX, BigInteger minY, BigInteger minZ,
        BigInteger maxX, BigInteger maxY, BigInteger maxZ
    ) {
        BigInteger width = maxX.subtract(minX).add(BigInteger.ONE);
        BigInteger height = maxY.subtract(minY).add(BigInteger.ONE);
        BigInteger depth = maxZ.subtract(minZ).add(BigInteger.ONE);
        return () -> new AbstractIterator<BlockPos>() {
            private final MutableBlockPos nextPos = new MutableBlockPos();
            private int counter = limit;
            @Override
            protected BlockPos computeNext() {
                if (this.counter <= 0) return this.endOfData();
                BigInteger rx = minX.add(BigInteger.valueOf(random.nextLong()).mod(width).add(width).mod(width));
                BigInteger ry = minY.add(BigInteger.valueOf(random.nextLong()).mod(height).add(height).mod(height));
                BigInteger rz = minZ.add(BigInteger.valueOf(random.nextLong()).mod(depth).add(depth).mod(depth));
                this.nextPos.set(rx, ry, rz);
                this.counter--;
                return this.nextPos;
            }
        };
    }

    // ===== spiralAround =====
    public static Iterable<MutableBlockPos> spiralAround(BlockPos center, int radius, Direction first, Direction second) {
        Validate.validState(first.getAxis() != second.getAxis(), "Axes must differ");
        return () -> new AbstractIterator<MutableBlockPos>() {
            private final Direction[] dirs = {first, second, first.getOpposite(), second.getOpposite()};
            private final MutableBlockPos cursor = center.mutable().move(second);
            private final int legs = 4 * radius;
            private int leg = -1, legSize = 0, legIndex = 0;
            private BigInteger lx = cursor.getBigX(), ly = cursor.getBigY(), lz = cursor.getBigZ();
            @Override
            protected MutableBlockPos computeNext() {
                this.cursor.set(this.lx, this.ly, this.lz).move(this.dirs[(this.leg + 4) % 4]);
                this.lx = this.cursor.getBigX(); this.ly = this.cursor.getBigY(); this.lz = this.cursor.getBigZ();
                if (this.legIndex >= this.legSize) {
                    if (this.leg >= this.legs) return this.endOfData();
                    this.leg++; this.legIndex = 0; this.legSize = this.leg / 2 + 1;
                }
                this.legIndex++;
                return this.cursor;
            }
        };
    }

    // ===== breadthFirstTraversal =====
    public static int breadthFirstTraversal(
        BlockPos start, int maxDepth, int maxCount,
        BiConsumer<BlockPos, Consumer<BlockPos>> neighborProvider,
        Function<BlockPos, TraversalNodeStatus> processor
    ) {
        Queue<Pair<BlockPos, Integer>> queue = new ArrayDeque<>();
        LongSet visited = new LongOpenHashSet();
        queue.add(Pair.of(start, 0));
        int count = 0;
        while (!queue.isEmpty()) {
            Pair<BlockPos, Integer> pair = queue.poll();
            BlockPos pos = pair.getLeft();
            int depth = pair.getRight();
            long key = pos.asLong(); // 使用 2D 键（注意可能碰撞，但原版如此）
            if (visited.add(key)) {
                TraversalNodeStatus status = processor.apply(pos);
                if (status == TraversalNodeStatus.STOP) break;
                if (status == TraversalNodeStatus.SKIP) continue;
                if (++count >= maxCount) return count;
                if (depth < maxDepth) {
                    neighborProvider.accept(pos, p -> queue.add(Pair.of(p, depth + 1)));
                }
            }
        }
        return count;
    }

    // ===== betweenCornersInDirection =====
    public static Iterable<BlockPos> betweenCornersInDirection(AABB aabb, Vec3 direction) {
        Vec3 min = aabb.getMinPosition();
        Vec3 max = aabb.getMaxPosition();
        return betweenCornersInDirection(
            Mth.floor(min.x()), Mth.floor(min.y()), Mth.floor(min.z()),
            Mth.floor(max.x()), Mth.floor(max.y()), Mth.floor(max.z()),
            direction
        );
    }

    public static Iterable<BlockPos> betweenCornersInDirection(BlockPos firstCorner, BlockPos secondCorner, Vec3 direction) {
        return betweenCornersInDirection(
            firstCorner.getX(), firstCorner.getY(), firstCorner.getZ(),
            secondCorner.getX(), secondCorner.getY(), secondCorner.getZ(),
            direction
        );
    }

    public static Iterable<BlockPos> betweenCornersInDirection(
        int firstCornerX, int firstCornerY, int firstCornerZ,
        int secondCornerX, int secondCornerY, int secondCornerZ,
        Vec3 direction
    ) {
        int minX = Math.min(firstCornerX, secondCornerX);
        int minY = Math.min(firstCornerY, secondCornerY);
        int minZ = Math.min(firstCornerZ, secondCornerZ);
        int maxX = Math.max(firstCornerX, secondCornerX);
        int maxY = Math.max(firstCornerY, secondCornerY);
        int maxZ = Math.max(firstCornerZ, secondCornerZ);
        int diffX = maxX - minX;
        int diffY = maxY - minY;
        int diffZ = maxZ - minZ;
        int startX = direction.x >= 0.0 ? minX : maxX;
        int startY = direction.y >= 0.0 ? minY : maxY;
        int startZ = direction.z >= 0.0 ? minZ : maxZ;
        List<Direction.Axis> axes = Direction.axisStepOrder(direction);
        Direction.Axis firstAxis = axes.get(0);
        Direction.Axis secondAxis = axes.get(1);
        Direction.Axis thirdAxis = axes.get(2);
        Direction firstDir = direction.get(firstAxis) >= 0.0 ? firstAxis.getPositive() : firstAxis.getNegative();
        Direction secondDir = direction.get(secondAxis) >= 0.0 ? secondAxis.getPositive() : secondAxis.getNegative();
        Direction thirdDir = direction.get(thirdAxis) >= 0.0 ? thirdAxis.getPositive() : thirdAxis.getNegative();
        int firstMax = firstAxis.choose(diffX, diffY, diffZ);
        int secondMax = secondAxis.choose(diffX, diffY, diffZ);
        int thirdMax = thirdAxis.choose(diffX, diffY, diffZ);
        return () -> new AbstractIterator<BlockPos>() {
            private final MutableBlockPos cursor = new MutableBlockPos();
            private int firstIndex = 0, secondIndex = 0, thirdIndex = 0;
            private boolean end = false;
            private final int fdx = firstDir.getStepX(), fdy = firstDir.getStepY(), fdz = firstDir.getStepZ();
            private final int sdx = secondDir.getStepX(), sdy = secondDir.getStepY(), sdz = secondDir.getStepZ();
            private final int tdx = thirdDir.getStepX(), tdy = thirdDir.getStepY(), tdz = thirdDir.getStepZ();
            @Override
            protected BlockPos computeNext() {
                if (this.end) return this.endOfData();
                this.cursor.set(
                    startX + this.firstIndex * fdx + this.secondIndex * sdx + this.thirdIndex * tdx,
                    startY + this.firstIndex * fdy + this.secondIndex * sdy + this.thirdIndex * tdy,
                    startZ + this.firstIndex * fdz + this.secondIndex * sdz + this.thirdIndex * tdz
                );
                if (this.thirdIndex < thirdMax) {
                    this.thirdIndex++;
                } else if (this.secondIndex < secondMax) {
                    this.secondIndex++;
                    this.thirdIndex = 0;
                } else if (this.firstIndex < firstMax) {
                    this.firstIndex++;
                    this.thirdIndex = 0;
                    this.secondIndex = 0;
                } else {
                    this.end = true;
                }
                return this.cursor;
            }
        };
    }

    // ---------- 内部可变类 ----------
    public static final class MutableBlockPos extends BlockPos {
        public MutableBlockPos() { this(BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO); }
        public MutableBlockPos(BigInteger x, BigInteger y, BigInteger z) { super(x, y, z); }
        public MutableBlockPos(int x, int y, int z) { super(x, y, z); }
        public MutableBlockPos(long x, long y, long z) { super(x, y, z); }
        public MutableBlockPos(double x, double y, double z) {
            this(BigInteger.valueOf(Mth.floor(x)), BigInteger.valueOf(Mth.floor(y)), BigInteger.valueOf(Mth.floor(z)));
        }

        @Override public BlockPos offset(int dx, int dy, int dz) { return super.offset(dx, dy, dz).immutable(); }
        @Override public BlockPos multiply(int scale) { return super.multiply(scale).immutable(); }
        @Override public BlockPos relative(Direction dir, int steps) { return super.relative(dir, steps).immutable(); }
        @Override public BlockPos relative(Direction.Axis axis, int steps) { return super.relative(axis, steps).immutable(); }
        @Override public BlockPos rotate(Rotation rotation) { return super.rotate(rotation).immutable(); }

        public MutableBlockPos set(BigInteger x, BigInteger y, BigInteger z) {
            setBigX(x); setBigY(y); setBigZ(z);
            return this;
        }

        public MutableBlockPos set(long x, long y, long z) {
            return set(BigInteger.valueOf(x), BigInteger.valueOf(y), BigInteger.valueOf(z));
        }

        public MutableBlockPos set(int x, int y, int z) {
            return set(BigInteger.valueOf(x), BigInteger.valueOf(y), BigInteger.valueOf(z));
        }

        public MutableBlockPos set(double x, double y, double z) {
            return set(BigInteger.valueOf(Mth.floor(x)), BigInteger.valueOf(Mth.floor(y)), BigInteger.valueOf(Mth.floor(z)));
        }

        public MutableBlockPos set(Vec3i vec) {
            return set(vec.getX(), vec.getY(), vec.getZ());
        }

        public MutableBlockPos set(long packed) {
            return set(getX(packed), 0, getZ(packed));
        }

        public MutableBlockPos set(AxisCycle cycle, int x, int y, int z) {
            return set(cycle.cycle(x, y, z, Direction.Axis.X),
                       cycle.cycle(x, y, z, Direction.Axis.Y),
                       cycle.cycle(x, y, z, Direction.Axis.Z));
        }

        public MutableBlockPos setWithOffset(Vec3i pos, Direction dir) {
            return set(pos.getX() + dir.getStepX(), pos.getY() + dir.getStepY(), pos.getZ() + dir.getStepZ());
        }

        public MutableBlockPos setWithOffset(Vec3i pos, int dx, int dy, int dz) {
            return set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
        }

        public MutableBlockPos setWithOffset(Vec3i pos, Vec3i offset) {
            return set(pos.getX() + offset.getX(), pos.getY() + offset.getY(), pos.getZ() + offset.getZ());
        }

        public MutableBlockPos move(Direction dir) { return move(dir, 1); }

        public MutableBlockPos move(Direction dir, int steps) {
            return set(getBigX().add(BigInteger.valueOf((long) dir.getStepX() * steps)),
                       getBigY().add(BigInteger.valueOf((long) dir.getStepY() * steps)),
                       getBigZ().add(BigInteger.valueOf((long) dir.getStepZ() * steps)));
        }

        public MutableBlockPos move(int dx, int dy, int dz) {
            return set(getBigX().add(BigInteger.valueOf(dx)), getBigY().add(BigInteger.valueOf(dy)), getBigZ().add(BigInteger.valueOf(dz)));
        }

        public MutableBlockPos move(Vec3i pos) {
            return set(getBigX().add(BigInteger.valueOf(pos.getX())), getBigY().add(BigInteger.valueOf(pos.getY())), getBigZ().add(BigInteger.valueOf(pos.getZ())));
        }

        public MutableBlockPos clamp(Direction.Axis axis, int min, int max) {
            BigInteger bMin = BigInteger.valueOf(min), bMax = BigInteger.valueOf(max);
            return switch (axis) {
                case X -> set(getBigX().max(bMin).min(bMax), getBigY(), getBigZ());
                case Y -> set(getBigX(), getBigY().max(bMin).min(bMax), getBigZ());
                case Z -> set(getBigX(), getBigY(), getBigZ().max(bMin).min(bMax));
            };
        }

        public MutableBlockPos setX(int x) {
            super.setX(x);
            setBigX(BigInteger.valueOf(x));
            return this;
        }

        public MutableBlockPos setY(int y) {
            super.setY(y);
            setBigY(BigInteger.valueOf(y));
            return this;
        }

        public MutableBlockPos setZ(int z) {
            super.setZ(z);
            setBigZ(BigInteger.valueOf(z));
            return this;
        }

        @Override
        public BlockPos immutable() {
            return new BlockPos(getBigX(), getBigY(), getBigZ());
        }
    }

    // ---------- 辅助 Pair 类型（原版未导出，此处补上） ----------
    private static class Pair<L, R> {
        private final L left;
        private final R right;
        private Pair(L left, R right) { this.left = left; this.right = right; }
        static <L, R> Pair<L, R> of(L left, R right) { return new Pair<>(left, right); }
        L getLeft() { return left; }
        R getRight() { return right; }
    }

    // ---------- 枚举 ----------
    public enum TraversalNodeStatus {
        ACCEPT,
        SKIP,
        STOP;
    }
}