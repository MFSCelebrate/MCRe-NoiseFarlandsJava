package net.minecraft.world.level;
import it.unimi.dsi.fastutil.longs.LongSet;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Spliterators.AbstractSpliterator;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import org.jspecify.annotations.Nullable;

public final class ChunkPos {
    // ========== 序列化 ==========
    public static final Codec<ChunkPos> CODEC = Codec.INT_STREAM
        .comapFlatMap(
            input -> Util.fixedSize(input, 2).map(ints -> new ChunkPos(ints[0], ints[1])),
            pos -> IntStream.of((int) pos.x, (int) pos.z)
        )
        .stable();

    public static final StreamCodec<ByteBuf, ChunkPos> STREAM_CODEC = new StreamCodec<ByteBuf, ChunkPos>() {
        @Override
        public ChunkPos decode(final ByteBuf input) {
            return new ChunkPos(input.readLong(), input.readLong());
        }

        @Override
        public void encode(final ByteBuf output, final ChunkPos value) {
            output.writeLong(value.x);
            output.writeLong(value.z);
        }
    };

    // ========== 原版常量（保留） ==========
    private static final int SAFETY_MARGIN = 1056;
    public static final long INVALID_CHUNK_POS = pack(2147483647, 2147483647);
    public static final ChunkPos ZERO = new ChunkPos(0, 0);
    private static final long COORD_BITS = 32L;
    private static final long COORD_MASK = 4294967295L;
    private static final int REGION_BITS = 5;
    public static final int REGION_SIZE = 32;
    private static final int REGION_MASK = 31;
    public static final int REGION_MAX_INDEX = 31;
    private static final int HASH_A = 1664525;
    private static final int HASH_C = 1013904223;
    private static final int HASH_Z_XOR = -559038737;

    // ========== MCRe: 64 位坐标 ==========
    public final long x;
    public final long z;

    // ========== 构造 ==========
    public ChunkPos(final long x, final long z) {
        this.x = x;
        this.z = z;
    }

    public ChunkPos(final int x, final int z) {
        this((long) x, (long) z);
    }

    public ChunkPos(final BlockPos pos) {
        this(
            SectionPos.blockToSectionCoord(pos.getBigX().longValue()),
            SectionPos.blockToSectionCoord(pos.getBigZ().longValue())
        );
    }

    // ========== Getter（兼容旧代码调用 .x() / .z()） ==========
    public long x() {
        return this.x;
    }

    public long z() {
        return this.z;
    }

    // ========== 静态工厂 ==========
    public static ChunkPos containing(final BlockPos pos) {
        return new ChunkPos(
            SectionPos.blockToSectionCoord(pos.getBigX().longValue()),
            SectionPos.blockToSectionCoord(pos.getBigZ().longValue())
        );
    }

    public static ChunkPos unpack(final long key) {
        return new ChunkPos((int) key, (int) (key >> 32));
    }

    public static ChunkPos minFromRegion(final long regionX, final long regionZ) {
        return new ChunkPos(regionX << 5, regionZ << 5);
    }

    public static ChunkPos maxFromRegion(final long regionX, final long regionZ) {
        return new ChunkPos((regionX << 5) + 31, (regionZ << 5) + 31);
    }

    // ========== 有效性 ==========
    public boolean isValid() {
        return isValid(this.x, this.z);
    }

    public static boolean isValid(final long x, final long z) {
        // 原版始终返回 true，我们扩展为检查是否在 long 范围内
        return Math.abs(x) <= Long.MAX_VALUE / 2 && Math.abs(z) <= Long.MAX_VALUE / 2;
    }

    // ========== 打包/解包（兼容原版 32 位） ==========
    public long pack() {
        return pack(this.x, this.z);
    }

    public static long pack(final long x, final long z) {
        return (x & COORD_MASK) | ((z & COORD_MASK) << 32);
    }

    /**
     * @deprecated 此方法依赖于已废弃的 SectionPos 打包键，不再支持。
     * 请使用 {@link #ChunkPos(long, long)} 直接构造。
     */
    @Deprecated
    public static long fromSectionNode(final long sectionNode) {
        throw new UnsupportedOperationException("fromSectionNode is no longer supported; use new ChunkPos(x, z) instead.");
    }

    public static long pack(final BlockPos pos) {
        return pack(
            SectionPos.blockToSectionCoord(pos.getBigX().longValue()),
            SectionPos.blockToSectionCoord(pos.getBigZ().longValue())
        );
    }

    // ========== 解包（返回 long 坐标，用于兼容旧代码） ==========
    public static long getLongX(final long pos) {
        return pos & COORD_MASK;
    }

    public static long getLongZ(final long pos) {
        return (pos >>> 32) & COORD_MASK;
    }

    /** @deprecated 使用 {@link #getLongX(long)} 替代 */
    @Deprecated
    public static int getX(final long pos) {
        return (int) (pos & COORD_MASK);
    }

    /** @deprecated 使用 {@link #getLongZ(long)} 替代 */
    @Deprecated
    public static int getZ(final long pos) {
        return (int) ((pos >>> 32) & COORD_MASK);
    }

    // ========== 哈希 ==========
    @Override
    public int hashCode() {
        return hash(this.x, this.z);
    }

    public static int hash(final long x, final long z) {
        int xi = (int) x;
        int zi = (int) z;
        int xTransform = 1664525 * xi + 1013904223;
        int zTransform = 1664525 * (zi ^ -559038737) + 1013904223;
        return xTransform ^ zTransform;
    }

    // ========== 坐标边界（返回 long） ==========
    public long getMiddleBlockX() {
        return this.getBlockX(8);
    }

    public long getMiddleBlockZ() {
        return this.getBlockZ(8);
    }

    public long getMinBlockX() {
        return SectionPos.sectionToBlockCoord(this.x);
    }

    public long getMinBlockZ() {
        return SectionPos.sectionToBlockCoord(this.z);
    }

    public long getMaxBlockX() {
        return this.getBlockX(15);
    }

    public long getMaxBlockZ() {
        return this.getBlockZ(15);
    }

    public long getRegionX() {
        return this.x >> 5;
    }

    public long getRegionZ() {
        return this.z >> 5;
    }

    public static long getRegionX(final long pos) {
        return getLongX(pos) >> 5;
    }

    public static long getRegionZ(final long pos) {
        return getLongZ(pos) >> 5;
    }

    public long getRegionLocalX() {
        return this.x & 31;
    }

    public long getRegionLocalZ() {
        return this.z & 31;
    }

    public long getBlockX(final int offset) {
        return SectionPos.sectionToBlockCoord(this.x, offset);
    }

    public long getBlockZ(final int offset) {
        return SectionPos.sectionToBlockCoord(this.z, offset);
    }

    // ========== BlockPos 转换 ==========
    public BlockPos getBlockAt(final int x, final int y, final int z) {
        return new BlockPos(this.getBlockX(x), y, this.getBlockZ(z));
    }

    public BlockPos getMiddleBlockPosition(final int y) {
        return new BlockPos(this.getMiddleBlockX(), y, this.getMiddleBlockZ());
    }

    public BlockPos getWorldPosition() {
        return new BlockPos(this.getMinBlockX(), 0, this.getMinBlockZ());
    }

    public boolean contains(final BlockPos pos) {
        long px = pos.getBigX().longValue();
        long pz = pos.getBigZ().longValue();
        return px >= this.getMinBlockX() && pz >= this.getMinBlockZ()
            && px <= this.getMaxBlockX() && pz <= this.getMaxBlockZ();
    }

    // ========== 距离方法（返回 long） ==========
    public long getChessboardDistance(final ChunkPos pos) {
        return this.getChessboardDistance(pos.x, pos.z);
    }

    public long getChessboardDistance(final long x, final long z) {
        return Math.max(Math.abs(x - this.x), Math.abs(z - this.z));
    }

    public long distanceSquared(final ChunkPos pos) {
        return this.distanceSquared(pos.x, pos.z);
    }

    public long distanceSquared(final long pos) {
        return this.distanceSquared(getLongX(pos), getLongZ(pos));
    }

    private long distanceSquared(final long x, final long z) {
        long dx = x - this.x;
        long dz = z - this.z;
        return dx * dx + dz * dz;
    }

    // ========== Stream 方法 ==========
    public static Stream<ChunkPos> rangeClosed(final ChunkPos center, final int radius) {
        return rangeClosed(
            new ChunkPos(center.x - radius, center.z - radius),
            new ChunkPos(center.x + radius, center.z + radius)
        );
    }

    public static Stream<ChunkPos> rangeClosed(final ChunkPos from, final ChunkPos to) {
        long xSize = Math.abs(from.x - to.x) + 1;
        long zSize = Math.abs(from.z - to.z) + 1;
        final long xDiff = from.x < to.x ? 1 : -1;
        final long zDiff = from.z < to.z ? 1 : -1;
        return StreamSupport.stream(new AbstractSpliterator<ChunkPos>((int) (xSize * zSize), 64) {
            private @Nullable ChunkPos pos;

            @Override
            public boolean tryAdvance(final Consumer<? super ChunkPos> action) {
                if (this.pos == null) {
                    this.pos = from;
                } else {
                    long x = this.pos.x;
                    long z = this.pos.z;
                    if (x == to.x) {
                        if (z == to.z) {
                            return false;
                        }
                        this.pos = new ChunkPos(from.x, z + zDiff);
                    } else {
                        this.pos = new ChunkPos(x + xDiff, z);
                    }
                }
                action.accept(this.pos);
                return true;
            }
        }, false);
    }

    // ========== equals / toString ==========
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ChunkPos)) return false;
        ChunkPos that = (ChunkPos) obj;
        return this.x == that.x && this.z == that.z;
    }

    @Override
    public String toString() {
        return "[" + this.x + ", " + this.z + "]";
    }
}