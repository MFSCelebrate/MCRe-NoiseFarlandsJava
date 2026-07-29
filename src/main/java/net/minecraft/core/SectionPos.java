package net.minecraft.core;

import com.google.common.collect.AbstractIterator;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.entity.EntityAccess;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * 64 位无限世界 SectionPos。
 * 使用 long 存储坐标，支持 ±9e18 范围的区块坐标。
 * 所有 API 与新的 BlockPos（BigInteger）兼容。
 */
public final class SectionPos extends Vec3i implements Position {

    // ==================== 核心存储 ====================
    private final long x;
    private final long y;
    private final long z;

    // ==================== 私有构造 ====================
    private SectionPos(long x, long y, long z) {
        super((int) x, (int) y, (int) z); // Vec3i 存储会被覆盖，仅满足继承
        this.x = x;
        this.y = y;
        this.z = z;
    }

    // ==================== 工厂方法 ====================
    public static SectionPos of(long x, long y, long z) {
        return new SectionPos(x, y, z);
    }

    public static SectionPos of(BlockPos pos) {
        return new SectionPos(
            blockToSectionCoord(pos.getBigX().longValue()),
            blockToSectionCoord(pos.getBigY().longValue()),
            blockToSectionCoord(pos.getBigZ().longValue())
        );
    }

    public static SectionPos of(ChunkPos pos, long sectionY) {
        return new SectionPos(pos.x, sectionY, pos.z);
    }

    public static SectionPos of(EntityAccess entity) {
        return of(entity.blockPosition());
    }

    public static SectionPos of(Position pos) {
        return new SectionPos(
            blockToSectionCoord(pos.x()),
            blockToSectionCoord(pos.y()),
            blockToSectionCoord(pos.z())
        );
    }

    /** @deprecated 使用 {@link #of(long, long, long)} 替代 */
    @Deprecated
    public static SectionPos of(long sectionNode) {
        // 旧版打包键不再支持，拆包会丢失信息，所以直接抛出异常
        throw new UnsupportedOperationException("Use of packed long for SectionPos is deprecated; use of(x,y,z) instead.");
    }

    public static SectionPos bottomOf(ChunkAccess chunk) {
        return of(chunk.getPos(), chunk.getMinSectionY());
    }

    // ==================== 坐标转换工具 ====================
    public static long blockToSectionCoord(long blockCoord) {
        return blockCoord >> 4;
    }

    public static long blockToSectionCoord(double coord) {
        return Mth.floor(coord) >> 4;
    }

    public static long sectionRelative(long blockCoord) {
        return blockCoord & 15;
    }

    public static long sectionToBlockCoord(long sectionCoord) {
        return sectionCoord << 4;
    }

    public static long sectionToBlockCoord(long sectionCoord, long offset) {
        return sectionToBlockCoord(sectionCoord) + offset;
    }

    // ==================== Getter ====================
    public long getLongX() { return x; }
    public long getLongY() { return y; }
    public long getLongZ() { return z; }

    // 重写 Vec3i 的 getter（返回 int，截断但通常范围安全）
    @Override
    public int getX() { return (int) x; }
    @Override
    public int getY() { return (int) y; }
    @Override
    public int getZ() { return (int) z; }

    // 实现 Position 接口
    @Override
    public double x() { return (double) x; }
    @Override
    public double y() { return (double) y; }
    @Override
    public double z() { return (double) z; }

    // ==================== 偏移 ====================
    public SectionPos offset(long dx, long dy, long dz) {
        if (dx == 0 && dy == 0 && dz == 0) return this;
        return new SectionPos(x + dx, y + dy, z + dz);
    }

    public SectionPos relative(Direction dir) {
        return offset(dir.getStepX(), dir.getStepY(), dir.getStepZ());
    }

    // ==================== 块坐标边界 ====================
    public long minBlockX() { return sectionToBlockCoord(x); }
    public long minBlockY() { return sectionToBlockCoord(y); }
    public long minBlockZ() { return sectionToBlockCoord(z); }
    public long maxBlockX() { return sectionToBlockCoord(x, 15); }
    public long maxBlockY() { return sectionToBlockCoord(y, 15); }
    public long maxBlockZ() { return sectionToBlockCoord(z, 15); }

    public BlockPos origin() {
        return new BlockPos(minBlockX(), minBlockY(), minBlockZ());
    }

    public BlockPos center() {
        return origin().offset(8, 8, 8);
    }

    // ==================== ChunkPos ====================
    public ChunkPos chunk() {
        return new ChunkPos((int) x, (int) z);
    }

    // ==================== 相对坐标编码（原版 API） ====================
    public static short sectionRelativePos(BlockPos pos) {
        long rx = sectionRelative(pos.getBigX().longValue());
        long ry = sectionRelative(pos.getBigY().longValue());
        long rz = sectionRelative(pos.getBigZ().longValue());
        return (short) ((rx << 8) | (rz << 4) | ry);
    }

    public static int sectionRelativeX(short relative) {
        return (relative >>> 8) & 15;
    }
    public static int sectionRelativeY(short relative) {
        return (relative >>> 0) & 15;
    }
    public static int sectionRelativeZ(short relative) {
        return (relative >>> 4) & 15;
    }

    public long relativeToBlockX(short relative) {
        return minBlockX() + sectionRelativeX(relative);
    }
    public long relativeToBlockY(short relative) {
        return minBlockY() + sectionRelativeY(relative);
    }
    public long relativeToBlockZ(short relative) {
        return minBlockZ() + sectionRelativeZ(relative);
    }

    public BlockPos relativeToBlockPos(short relative) {
        return new BlockPos(relativeToBlockX(relative), relativeToBlockY(relative), relativeToBlockZ(relative));
    }

    // ==================== Stream 遍历 ====================
    public Stream<BlockPos> blocksInside() {
        return BlockPos.betweenClosedStream(
            minBlockX(), minBlockY(), minBlockZ(),
            maxBlockX(), maxBlockY(), maxBlockZ()
        );
    }

    public static Stream<SectionPos> cube(SectionPos center, long radius) {
        long x0 = center.x - radius, y0 = center.y - radius, z0 = center.z - radius;
        long x1 = center.x + radius, y1 = center.y + radius, z1 = center.z + radius;
        return betweenClosedStream(x0, y0, z0, x1, y1, z1);
    }

    public static Stream<SectionPos> aroundChunk(ChunkPos center, long radius, long minSection, long maxSection) {
        return betweenClosedStream(center.x - radius, minSection, center.z - radius,
                                   center.x + radius, maxSection, center.z + radius);
    }

    public static Stream<SectionPos> betweenClosedStream(long minX, long minY, long minZ,
                                                         long maxX, long maxY, long maxZ) {
        long sizeX = maxX - minX + 1;
        long sizeY = maxY - minY + 1;
        long sizeZ = maxZ - minZ + 1;
        long total = sizeX * sizeY * sizeZ;
        return StreamSupport.stream(
            new AbstractIterator<SectionPos>() {
                private long index = 0;
                @Override
                protected SectionPos computeNext() {
                    if (index >= total) return endOfData();
                    long x = minX + (index % sizeX);
                    long slice = index / sizeX;
                    long y = minY + (slice % sizeY);
                    long z = minZ + (slice / sizeY);
                    index++;
                    return new SectionPos(x, y, z);
                }
            }.spliterator(), false
        );
    }

    // ==================== 旧版 asLong 兼容（弃用） ====================
    /**
     * @deprecated 使用对象引用替代，此方法仅用于过渡，返回的 long 不保证唯一性。
     */
    @Deprecated
    public long asLong() {
        // 返回一个基于哈希的组合，仅用于旧容器，不保证唯一。
        return Long.hashCode(x) ^ Long.hashCode(y) ^ Long.hashCode(z);
    }

    /**
     * @deprecated 使用对象引用替代。
     */
    @Deprecated
    public static long getZeroNode(long sectionNode) {
        // 原版用于清除 Y 坐标，我们现在不再使用打包键
        return sectionNode & ~0xFFFFF;
    }

    // ==================== 序列化 ====================
    public static final StreamCodec<ByteBuf, SectionPos> STREAM_CODEC = new StreamCodec<ByteBuf, SectionPos>() {
        @Override
        public SectionPos decode(ByteBuf buf) {
            return new SectionPos(buf.readLong(), buf.readLong(), buf.readLong());
        }
        @Override
        public void encode(ByteBuf buf, SectionPos pos) {
            buf.writeLong(pos.x);
            buf.writeLong(pos.y);
            buf.writeLong(pos.z);
        }
    };

    // ==================== equals / hashCode / toString ====================
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof SectionPos)) return false;
        SectionPos that = (SectionPos) obj;
        return this.x == that.x && this.y == that.y && this.z == that.z;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(x) ^ Long.hashCode(y) ^ Long.hashCode(z);
    }

    @Override
    public String toString() {
        return "SectionPos{x=" + x + ", y=" + y + ", z=" + z + "}";
    }
}