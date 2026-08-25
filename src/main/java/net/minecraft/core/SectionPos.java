package net.minecraft.core;

import io.netty.buffer.ByteBuf;
import java.util.Spliterators.AbstractSpliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.entity.EntityAccess;
import net.MinecraftTools.Math._256Bit.Int256;
import net.MinecraftTools.Math._256Bit.util.Vec3d256;

/**
 * SectionPos — 区块节坐标（MCRe NoiseFarlands 全面 Long 化版）
 *
 * <p>原版用 asLong() 将 (int x, int y, int z) 打包进 long（22+20+22 位），坐标上限被锁死在
 * ±2^23（约 8,388,607 区块节）。本版：移除打包系统，SectionPos 对象本身即键（不可变 +
 * hashCode/equals），坐标升级为 long（突破 2^31 边界）。
 *
 * <p><b>API 破坏性变更</b>（相对 vanilla）：
 * <ul>
 *   <li>{@link #x()}/{@link #y()}/{@link #z()} 返回 <b>long</b</li>
 *   <li>{@link #minBlockX()}/{@link #minBlockY()}/{@link #minBlockZ()} 返回 long（原版返回 int，超 2^27 溢出）</li>
 *   <li>{@link #maxBlockX()}/{@link #maxBlockY()}/{@link #maxBlockZ()} 返回 long</li>
 *   <li>{@link #blockToSectionCoord}/{@link #sectionToBlockCoord} 参数 long</li>
 *   <li>{@link #STREAM_CODEC} 用 {@link ByteBufCodecs#VAR_LONG</li>
 *   <li>原 {@code minBlockXLong/centerXLong} 已合并到 {@code minBlockX/centerXLong}（同名返回 long）</li>
 *</ul>
 */
public class SectionPos extends Vec3i {
    public static final int SECTION_BITS = 4;
    public static final int SECTION_SIZE = 16;
    public static final int SECTION_BLOCK_COUNT = 4096;
    public static final int SECTION_MASK = 15;
    public static final int SECTION_HALF_SIZE = 8;
    public static final int SECTION_MAX_INDEX = 15;
    private static final int RELATIVE_X_SHIFT = 8;
    private static final int RELATIVE_Y_SHIFT = 0;
    private static final int RELATIVE_Z_SHIFT = 4;
    public static final StreamCodec<ByteBuf, SectionPos> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_LONG,
        SectionPos::x,
        ByteBufCodecs.VAR_LONG,
        SectionPos::y,
        ByteBufCodecs.VAR_LONG,
        SectionPos::z,
        SectionPos::of
    );

    private SectionPos(final long x, final long y, final long z) {
        super(x, y, z);
    }

    public static SectionPos of(final long x, final long y, final long z) {
        return new SectionPos(x, y, z);
    }

    public static SectionPos of(final BlockPos pos) {
        return new SectionPos(
            blockToSectionCoord(pos.getX()),
            blockToSectionCoord(pos.getY()),
            blockToSectionCoord(pos.getZ())
        );
    }

    public static SectionPos of(final ChunkPos pos, final long sectionY) {
        return new SectionPos(pos.x(), sectionY, pos.z());
    }

    public static SectionPos of(final EntityAccess entity) {
        return of(entity.blockPosition());
    }

    public static SectionPos of(final Position pos) {
        return new SectionPos(
            Mth.lfloor(pos.x()) >> 4,
            Mth.lfloor(pos.y()) >> 4,
            Mth.lfloor(pos.z()) >> 4
        );
    }

    public static SectionPos bottomOf(final ChunkAccess chunk) {
        return of(chunk.getPos(), chunk.getMinSectionY());
    }

    public static long posToSectionCoord(final double pos) {
        return Mth.lfloor(pos) >> 4;
    }

    /** long 坐标 → 区块节坐标（ChunkPos long 化支持） */
    public static long blockToSectionCoord(final long blockCoord) {
        return blockCoord >> 4;
    }

    public static long sectionRelative(final long blockCoord) {
        return blockCoord & 15L;
    }

    public static short sectionRelativePos(final BlockPos pos) {
        long x = sectionRelative(pos.getX());
        long y = sectionRelative(pos.getY());
        long z = sectionRelative(pos.getZ());
        return (short)((int)(x << 8) | (int)(z << 4) | (int)(y << 0));
    }

    public static int sectionRelativeX(final short relative) {
        return relative >>> 8 & 15;
    }

    public static int sectionRelativeY(final short relative) {
        return relative >>> 0 & 15;
    }

    public static int sectionRelativeZ(final short relative) {
        return relative >>> 4 & 15;
    }

    public long relativeToBlockX(final short relative) {
        return this.minBlockX() + sectionRelativeX(relative);
    }

    public long relativeToBlockY(final short relative) {
        return this.minBlockY() + sectionRelativeY(relative);
    }

    public long relativeToBlockZ(final short relative) {
        return this.minBlockZ() + sectionRelativeZ(relative);
    }

    public BlockPos relativeToBlockPos(final short relative) {
        return new BlockPos(this.relativeToBlockX(relative), this.relativeToBlockY(relative), this.relativeToBlockZ(relative));
    }

    /** long 区块节坐标 → 方块坐标（MCRe NoiseFarlands long 版） */
    public static long sectionToBlockCoord(final long sectionCoord) {
        return sectionCoord << 4;
    }

    public static long sectionToBlockCoord(final long sectionCoord, final long offset) {
        return (sectionCoord << 4) + offset;
    }

    /**
     * MCRe：原版 x()/y()/z() 返回 int。long 化后返回 long，突破 2^31 区块节限制。
     */
    public long x() {
        return this.getX();
    }

    public long y() {
        return this.getY();
    }

    public long z() {
        return this.getZ();
    }

    /**
     * MCRe：原版返回 int（{@code sectionCoord << 4} 在 sectionCoord > 2^27 时溢出）。
     * 本版返回 long。原有的 {@code minBlockXLong()} 等别名已合并到此方法。
     */
    public long minBlockX() {
        return sectionToBlockCoord(this.x());
    }

    public long minBlockY() {
        return sectionToBlockCoord(this.y());
    }

    public long minBlockZ() {
        return sectionToBlockCoord(this.z());
    }

    public long maxBlockX() {
        return sectionToBlockCoord(this.x(), 15L);
    }

    public long maxBlockY() {
        return sectionToBlockCoord(this.y(), 15L);
    }

    public long maxBlockZ() {
        return sectionToBlockCoord(this.z(), 15L);
    }

    public BlockPos origin() {
        return new BlockPos(sectionToBlockCoord(this.x()), sectionToBlockCoord(this.y()), sectionToBlockCoord(this.z()));
    }

    /** section 中心方块坐标（long） */
    public long centerXLong() {
        return (this.x() << 4) + 8L;
    }

    public long centerYLong() {
        return (this.y() << 4) + 8L;
    }

    public long centerZLong() {
        return (this.z() << 4) + 8L;
    }

    public BlockPos center() {
        return this.origin().offset(8L, 8L, 8L);
    }

    public ChunkPos chunk() {
        return new ChunkPos(this.x(), this.z());
    }

    public SectionPos offset(final long x, final long y, final long z) {
        return x == 0L && y == 0L && z == 0L ? this : new SectionPos(this.x() + x, this.y() + y, this.z() + z);
    }

    public Stream<BlockPos> blocksInside() {
        return BlockPos.betweenClosedStream(this.minBlockX(), this.minBlockY(), this.minBlockZ(), this.maxBlockX(), this.maxBlockY(), this.maxBlockZ());
    }

    public static Stream<SectionPos> cube(final SectionPos center, final long radius) {
        long x = center.x();
        long y = center.y();
        long z = center.z();
        return betweenClosedStream(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius);
    }

    public static Stream<SectionPos> aroundChunk(final ChunkPos center, final long radius, final long minSection, final long maxSection) {
        long x = center.x();
        long z = center.z();
        return betweenClosedStream(x - radius, minSection, z - radius, x + radius, maxSection, z + radius);
    }

    /**
     * MCRe：所有坐标 long 化。迭代器内部 cursor 也需要 long 化。
     */
    public static Stream<SectionPos> betweenClosedStream(
        final long minX, final long minY, final long minZ, final long maxX, final long maxY, final long maxZ
    ) {
        long count = (maxX - minX + 1L) * (maxY - minY + 1L) * (maxZ - minZ + 1L);
        return StreamSupport.stream(new AbstractSpliterator<SectionPos>(count, 64) {
            private final Cursor3D cursor = new Cursor3D(minX, minY, minZ, maxX, maxY, maxZ);

            @Override
            public boolean tryAdvance(final Consumer<? super SectionPos> action) {
                if (this.cursor.advance()) {
                    action.accept(new SectionPos(this.cursor.nextX(), this.cursor.nextY(), this.cursor.nextZ()));
                    return true;
                } else {
                    return false;
                }
            }
        }, false);
    }

    /**
     * 遍历方块位置周围及所在区块节（对象化，替代原 long 打包回调）
     */
    public static void aroundAndAtBlockPos(final BlockPos blockPos, final Consumer<SectionPos> sectionConsumer) {
        aroundAndAtBlockPos(blockPos.getX(), blockPos.getY(), blockPos.getZ(), sectionConsumer);
    }

    public static void aroundAndAtBlockPos(final long blockX, final long blockY, final long blockZ, final Consumer<SectionPos> sectionConsumer) {
        long minSectionX = blockToSectionCoord(blockX - 1L);
        long maxSectionX = blockToSectionCoord(blockX + 1L);
        long minSectionY = blockToSectionCoord(blockY - 1L);
        long maxSectionY = blockToSectionCoord(blockY + 1L);
        long minSectionZ = blockToSectionCoord(blockZ - 1L);
        long maxSectionZ = blockToSectionCoord(blockZ + 1L);
        if (minSectionX == maxSectionX && minSectionY == maxSectionY && minSectionZ == maxSectionZ) {
            sectionConsumer.accept(of(minSectionX, minSectionY, minSectionZ));
        } else {
            for (long sectionX = minSectionX; sectionX <= maxSectionX; sectionX++) {
                for (long sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
                    for (long sectionZ = minSectionZ; sectionZ <= maxSectionZ; sectionZ++) {
                        sectionConsumer.accept(of(sectionX, sectionY, sectionZ));
                    }
                }
            }
        }
    }

    // ═══════════ 256-bit 适配（MCRe NoiseFarlands） ═══════════

    /** 区块节坐标精确转 Int256 */
    public Int256 x256() {
        return Int256.of(this.x());
    }

    public Int256 y256() {
        return Int256.of(this.y());
    }

    public Int256 z256() {
        return Int256.of(this.z());
    }

    /** 转 256-bit 向量（方块坐标级） */
    public Vec3d256 to256() {
        return Vec3d256.ofInt(Int256.of(this.minBlockX()), Int256.of(this.minBlockY()), Int256.of(this.minBlockZ()));
    }

    /** 256-bit 向量 → 区块节（floor 到 16 对齐） */
    public static SectionPos from256(final Vec3d256 pos) {
        return of(
            blockToSectionCoord(pos.x.floor().longValue()),
            blockToSectionCoord(pos.y.floor().longValue()),
            blockToSectionCoord(pos.z.floor().longValue())
        );
    }
}
