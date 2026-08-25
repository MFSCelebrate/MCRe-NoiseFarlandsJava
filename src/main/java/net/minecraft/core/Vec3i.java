package net.minecraft.core;

import com.google.common.base.MoreObjects;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.netty.buffer.ByteBuf;
import java.util.stream.LongStream;
import javax.annotation.concurrent.Immutable;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.joml.Vector3L;

/**
 * Vec3i — 三维整数坐标（MCRe NoiseFarlands 全面 Long 化版）
 *
 * <p>原版字段为 int（±2^31 限制），far lands 探索在 2^31 后溢出。本版：x/y/z 全部升级为
 * long，突破 2^31 边界；序列化（CODEC/STREAM_CODEC）改用 LONG_STREAM + VAR_LONG；
 * 距离/比较/hash 适配 long（用 {@link Long#hashCode} / {@link Long#compare} 防溢出）；
 * joml 互转从 {@code Vector3i}（int）切到 {@code Vector3L}（long）。
 *
 * <p><b>API 破坏性变更</b>（相对 vanilla）：
 * <ul>
 *   <li>{@link #getX()}/{@link #getY()}/{@link #getZ()} 返回 <b>long</b>（不是 int）</li>
 *   <li>{@link #offset}/{@link #multiply}/{@link #relative}/{@link #above}/... 等所有步长参数为 <b>long</b</li>
 *   <li>{@link #distManhattan}/{@link #distChessboard} 返回 <b>long</b</li>
 *   <li>{@link #get(Direction.Axis)} 返回 <b>long</b</li>
 *   <li>{@link #toMutable()} 返回 {@link Vector3L}（不再是 {@code Vector3i}）</li>
 *</ul>
 */
@Immutable
public class Vec3i implements Comparable<Vec3i> {
    public static final Codec<Vec3i> CODEC = Codec.LONG_STREAM
        .comapFlatMap(
            input -> Util.fixedSize(input, 3).map(longs -> new Vec3i(longs[0], longs[1], longs[2])),
            pos -> LongStream.of(pos.getX(), pos.getY(), pos.getZ())
        );
    public static final StreamCodec<ByteBuf, Vec3i> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_LONG,
        Vec3i::getX,
        ByteBufCodecs.VAR_LONG,
        Vec3i::getY,
        ByteBufCodecs.VAR_LONG,
        Vec3i::getZ,
        Vec3i::new
    );
    public static final Vec3i ZERO = new Vec3i(0L, 0L, 0L);
    private long x;
    private long y;
    private long z;

    /**
     * MCRe：原 {@code offsetCodec(int maxOffsetPerAxis)} 已升级为 long。 调用方传入 int 字面量
     * 会自动提升，但需注意远距离（≥ 2^31）偏移现在可正确序列化/校验。
     */
    public static Codec<Vec3i> offsetCodec(final long maxOffsetPerAxis) {
        return CODEC.validate(
            value -> Math.abs(value.getX()) < maxOffsetPerAxis && Math.abs(value.getY()) < maxOffsetPerAxis && Math.abs(value.getZ()) < maxOffsetPerAxis
                ? DataResult.success(value)
                : DataResult.error(() -> "Position out of range, expected at most " + maxOffsetPerAxis + ": " + value)
        );
    }

    public Vec3i(final long x, final long y, final long z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        } else {
            return !(o instanceof Vec3i vec3i)
                ? false
                : this.getX() == vec3i.getX() && this.getY() == vec3i.getY() && this.getZ() == vec3i.getZ();
        }
    }

    /**
     * MCRe：long 化后的混合哈希 —— 用 {@link Long#hashCode} 保证 |x| ≥ 2^31 时仍能均匀散列，
     * 避免 int hashCode 在大坐标空间退化（碰撞集中在低 32 位）。
     */
    @Override
    public int hashCode() {
        long h = this.getX();
        h = h * 31L + this.getY();
        h = h * 31L + this.getZ();
        return Long.hashCode(h);
    }

    /**
     * MCRe：用 {@link Long#compare} 防减法溢出（{@code int - int} 在 ±2^31 处会绕回）。
     * 顺序：先 Y，再 Z，最后 X（与原版语义一致）。
     */
    @Override
    public int compareTo(final Vec3i pos) {
        int cmpY = Long.compare(this.getY(), pos.getY());
        if (cmpY != 0) {
            return cmpY;
        }
        int cmpZ = Long.compare(this.getZ(), pos.getZ());
        if (cmpZ != 0) {
            return cmpZ;
        }
        return Long.compare(this.getX(), pos.getX());
    }

    public long getX() {
        return this.x;
    }

    public long getY() {
        return this.y;
    }

    public long getZ() {
        return this.z;
    }

    protected Vec3i setX(final long x) {
        this.x = x;
        return this;
    }

    protected Vec3i setY(final long y) {
        this.y = y;
        return this;
    }

    protected Vec3i setZ(final long z) {
        this.z = z;
        return this;
    }

    public Vec3i offset(final long x, final long y, final long z) {
        return x == 0L && y == 0L && z == 0L ? this : new Vec3i(this.getX() + x, this.getY() + y, this.getZ() + z);
    }

    public Vec3i offset(final Vec3i vec) {
        return this.offset(vec.getX(), vec.getY(), vec.getZ());
    }

    public Vec3i subtract(final Vec3i vec) {
        return this.offset(-vec.getX(), -vec.getY(), -vec.getZ());
    }

    public Vec3i multiply(final long scale) {
        if (scale == 1L) {
            return this;
        } else {
            return scale == 0L ? ZERO : new Vec3i(this.getX() * scale, this.getY() * scale, this.getZ() * scale);
        }
    }

    public Vec3i multiply(final long xScale, final long yScale, final long zScale) {
        return new Vec3i(this.getX() * xScale, this.getY() * yScale, this.getZ() * zScale);
    }

    public Vec3i above() {
        return this.above(1L);
    }

    public Vec3i above(final long steps) {
        return this.relative(Direction.UP, steps);
    }

    public Vec3i below() {
        return this.below(1L);
    }

    public Vec3i below(final long steps) {
        return this.relative(Direction.DOWN, steps);
    }

    public Vec3i north() {
        return this.north(1L);
    }

    public Vec3i north(final long steps) {
        return this.relative(Direction.NORTH, steps);
    }

    public Vec3i south() {
        return this.south(1L);
    }

    public Vec3i south(final long steps) {
        return this.relative(Direction.SOUTH, steps);
    }

    public Vec3i west() {
        return this.west(1L);
    }

    public Vec3i west(final long steps) {
        return this.relative(Direction.WEST, steps);
    }

    public Vec3i east() {
        return this.east(1L);
    }

    public Vec3i east(final long steps) {
        return this.relative(Direction.EAST, steps);
    }

    public Vec3i relative(final Direction direction) {
        return this.relative(direction, 1L);
    }

    public Vec3i relative(final Direction direction, final long steps) {
        return steps == 0L
            ? this
            : new Vec3i(
                this.getX() + (long) direction.getStepX() * steps,
                this.getY() + (long) direction.getStepY() * steps,
                this.getZ() + (long) direction.getStepZ() * steps
            );
    }

    public Vec3i relative(final Direction.Axis axis, final long steps) {
        if (steps == 0L) {
            return this;
        }

        long xStep = axis == Direction.Axis.X ? steps : 0L;
        long yStep = axis == Direction.Axis.Y ? steps : 0L;
        long zStep = axis == Direction.Axis.Z ? steps : 0L;
        return new Vec3i(this.getX() + xStep, this.getY() + yStep, this.getZ() + zStep);
    }

    public Vec3i cross(final Vec3i upVector) {
        return new Vec3i(
            this.getY() * upVector.getZ() - this.getZ() * upVector.getY(),
            this.getZ() * upVector.getX() - this.getX() * upVector.getZ(),
            this.getX() * upVector.getY() - this.getY() * upVector.getX()
        );
    }

    public boolean closerThan(final Vec3i pos, final double distance) {
        return this.distSqr(pos) < Mth.square(distance);
    }

    public boolean closerToCenterThan(final Position pos, final double distance) {
        return this.distToCenterSqr(pos) < Mth.square(distance);
    }

    /**
     * MCRe：距离平方仍为 double —— 横跨 2^31 时 double 精度足以容纳。
     * 长坐标下 {@code (longA - longB)} 转 double 可能损失最低位（约 ±1），但平方后取比较阈值
     * 仍合理（玩家坐标比较是近似匹配）。
     */
    public double distSqr(final Vec3i pos) {
        return this.distToLowCornerSqr(pos.getX(), pos.getY(), pos.getZ());
    }

    public double distToCenterSqr(final Position pos) {
        return this.distToCenterSqr(pos.x(), pos.y(), pos.z());
    }

    public double distToCenterSqr(final double x, final double y, final double z) {
        double dx = this.getX() + 0.5 - x;
        double dy = this.getY() + 0.5 - y;
        double dz = this.getZ() + 0.5 - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public double distToLowCornerSqr(final double x, final double y, final double z) {
        double dx = this.getX() - x;
        double dy = this.getY() - y;
        double dz = this.getZ() - z;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * MCRe：原版返回 int，超大坐标（≥ 2^31）下曼哈顿距离会溢出。本版返回 long。
     */
    public long distManhattan(final Vec3i pos) {
        long xd = Math.abs(pos.getX() - this.getX());
        long yd = Math.abs(pos.getY() - this.getY());
        long zd = Math.abs(pos.getZ() - this.getZ());
        return xd + yd + zd;
    }

    /**
     * MCRe：原版返回 int，超大坐标下溢出。本版返回 long。
     */
    public long distChessboard(final Vec3i pos) {
        long xd = Math.abs(this.getX() - pos.getX());
        long yd = Math.abs(this.getY() - pos.getY());
        long zd = Math.abs(this.getZ() - pos.getZ());
        return Math.max(Math.max(xd, yd), zd);
    }

    /**
     * MCRe：原版返回 int。long 化后需调用 {@link Direction.Axis#choose(long, long, long)}。
     */
    public long get(final Direction.Axis axis) {
        return axis.choose(this.x, this.y, this.z);
    }

    /**
     * MCRe：joml 适配从 {@code Vector3i}（int）切到 {@code Vector3L}（long），保留 64 位精度。
     * 注意：joml 的 {@link Vector3L} 构造函数只接受 int，没有 long 版构造；
     * 必须用 {@code new Vector3L().set(this.x, this.y, this.z)}。
     * {@link Vector3L} 的 API 与 {@code Vector3i} 不完全兼容（如 {@code Vector3L} 没有
     * {@code mul} 等向量运算方法直接对应），调用方需自行适配。
     */
    public Vector3L toMutable() {
        return new Vector3L().set(this.x, this.y, this.z);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("x", this.getX()).add("y", this.getY()).add("z", this.getZ()).toString();
    }

    public String toShortString() {
        return this.getX() + ", " + this.getY() + ", " + this.getZ();
    }
}
