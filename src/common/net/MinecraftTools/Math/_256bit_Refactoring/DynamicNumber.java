package net.MinecraftTools.Math._256bit_Refactoring;

import java.math.BigInteger;
import java.util.Objects;

/**
 * 🔧 DynamicNumber——可切换数据类型的统一数字容器（MCRe 重构版第三版，INF32768 方案核心）。
 *
 * <p>设计意图（INF32768 原话）：“做一个类，比如叫 DynamicNumber，核心是一个数字（Number 对象），
 * 可以预设 Long, 你的 Int256, BigInteger 等，并将需要坐标运算的都装到这里。同时给一个配置，
 * 让用户可以选择使用哪种数据类型。”
 *
 * <p>MCRe 语境：BlockPos/SectionPos/Vec3 已 Long 化（现有链零开销），边境之地探索需要 256 位
 * 坐标运算，超大世界/测试参考需要 BigInteger 兜底。本容器让三者对上层透明：
 * <ul>
 *   <li><b>{@link Type#LONG}</b>：现有 long 链的原生表示，运算走 JDK 原生 long——零分配零分支，
 *       默认配置，保证现有行为逐位不变；</li>
 *   <li><b>{@link Type#INT256}</b>：{@link IntRefactor256} 补码 256 位——边境之地坐标运算
 *       （±2^255 覆盖原版边缘之地绰绰有余）；</li>
 *   <li><b>{@link Type#BIG_INTEGER}</b>：JDK BigInteger 兜底——测试参考/超大世界。</li>
 * </ul>
 *
 * <p><b>语义规则</b>：
 * <ol>
 *   <li>实例不可变，构造时按<b>显式类型或当前 {@link #getDefaultType()}</b> 归一化存储；</li>
 *   <li>运算结果类型 = 运算时的 {@link #getDefaultType()}，输入按需提升（LONG→INT256→BIG_INTEGER
 *       无损；反向提升超域抛 {@link ArithmeticException}——<b>不静默回绕</b>，容器层保证坐标运算
 *       不会悄悄损坏）；</li>
 *   <li>整数除法截断向零（与 JDK long / BigInteger.divide 一致）；</li>
 *   <li>equals/compareTo 按<b>数值</b>（跨类型可比：LONG 5 等于 INT256 5）。</li>
 * </ol>
 *
 * <p>线程安全：实例不可变；defaultType 为 volatile 静态，运行期可切换（切换不影响已构造实例的
 * 存储表示，只影响后续运算的目标类型）。
 */
public final class DynamicNumber extends Number {

    /** 数据类型（精度/容量递增）。 */
    public enum Type {
        /** 64 位 long（现有链原生，零开销）。 */
        LONG,
        /** 256 位补码（边境之地坐标运算）。 */
        INT256,
        /** 任意精度（兜底/测试参考）。 */
        BIG_INTEGER
    }

    // ═══════════ 全局配置 ═══════════

    private static volatile Type defaultType = Type.LONG;

    /** IntRefactor256 值域边界（容器层域检查用）。 */
    private static final BigInteger INT256_MIN = BigInteger.ONE.shiftLeft(255).negate();
    private static final BigInteger INT256_MAX = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.ONE);
    /** long 值域边界。 */
    private static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    /** 设置全局默认数据类型（影响后续工厂与运算的目标类型）。 */
    public static void setDefaultType(final Type type) {
        defaultType = Objects.requireNonNull(type);
    }

    /** 当前全局默认数据类型。 */
    public static Type getDefaultType() {
        return defaultType;
    }

    // ═══════════ 字段（三选一，type 指示哪个有效） ═══════════

    private final Type type;
    private final long longValue;
    private final IntRefactor256 int256;
    private final BigInteger big;

    private DynamicNumber(final Type type, final long longValue, final IntRefactor256 int256, final BigInteger big) {
        this.type = type;
        this.longValue = longValue;
        this.int256 = int256;
        this.big = big;
    }

    // ═══════════ 工厂（按显式类型或默认类型归一化） ═══════════

    /** 按默认类型存值（超域抛 ArithmeticException）。 */
    public static DynamicNumber of(final long value) {
        return of(defaultType, value);
    }

    /** 按显式类型存 long 值（LONG 精确；INT256/BIG_INTEGER 无损扩宽）。 */
    public static DynamicNumber of(final Type type, final long value) {
        return switch (Objects.requireNonNull(type)) {
            case LONG -> new DynamicNumber(Type.LONG, value, null, null);
            case INT256 -> new DynamicNumber(Type.INT256, 0L, IntRefactor256.of(value), null);
            case BIG_INTEGER -> new DynamicNumber(Type.BIG_INTEGER, 0L, null, BigInteger.valueOf(value));
        };
    }

    /** 按默认类型存 IntRefactor256 值（LONG 配置超 long 域抛；BIG_INTEGER 无损）。 */
    public static DynamicNumber of(final IntRefactor256 value) {
        return of(defaultType, value);
    }

    public static DynamicNumber of(final Type type, final IntRefactor256 value) {
        Objects.requireNonNull(value);
        return switch (Objects.requireNonNull(type)) {
            case LONG -> {
                final BigInteger exact = value.toBigInteger();
                if (exact.compareTo(LONG_MIN) < 0 || exact.compareTo(LONG_MAX) > 0) {
                    throw new ArithmeticException("value out of long range for DynamicNumber Type.LONG");
                }
                yield new DynamicNumber(Type.LONG, exact.longValue(), null, null);
            }
            case INT256 -> new DynamicNumber(Type.INT256, 0L, value, null);
            case BIG_INTEGER -> new DynamicNumber(Type.BIG_INTEGER, 0L, null, value.toBigInteger());
        };
    }

    /** 按默认类型存 BigInteger 值（LONG 配置超 long 域抛；INT256 超 256 位补码域抛）。 */
    public static DynamicNumber of(final BigInteger value) {
        return of(defaultType, value);
    }

    public static DynamicNumber of(final Type type, final BigInteger value) {
        Objects.requireNonNull(value);
        return switch (Objects.requireNonNull(type)) {
            case LONG -> {
                if (value.compareTo(LONG_MIN) < 0 || value.compareTo(LONG_MAX) > 0) {
                    throw new ArithmeticException("value out of long range for DynamicNumber Type.LONG");
                }
                yield new DynamicNumber(Type.LONG, value.longValue(), null, null);
            }
            case INT256 -> {
                // 容器层显式检查 256 位补码域 [-2^255, 2^255-1]（IntRefactor256.of 是回绕语义，不静默损坏）
                if (value.compareTo(INT256_MIN) < 0 || value.compareTo(INT256_MAX) > 0) {
                    throw new ArithmeticException("value out of IntRefactor256 range");
                }
                yield new DynamicNumber(Type.INT256, 0L, IntRefactor256.of(value), null);
            }
            case BIG_INTEGER -> new DynamicNumber(Type.BIG_INTEGER, 0L, null, value);
        };
    }

    /** 复制到默认类型（跨配置迁移用）。 */
    public static DynamicNumber of(final DynamicNumber value) {
        Objects.requireNonNull(value);
        return of(defaultType, value.toBigInteger());
    }

    /** 显式类型迁移。 */
    public static DynamicNumber of(final Type type, final DynamicNumber value) {
        Objects.requireNonNull(value);
        return of(type, value.toBigInteger());
    }

    // ═══════════ 访问 ═══════════

    /** 本实例的存储类型。 */
    public Type type() {
        return type;
    }

    /** 精确值（BigInteger，无损导出——测试参考/跨类型桥）。 */
    public BigInteger toBigInteger() {
        return switch (type) {
            case LONG -> BigInteger.valueOf(longValue);
            case INT256 -> int256.toBigInteger();
            case BIG_INTEGER -> big;
        };
    }

    /** 转 IntRefactor256（LONG/BIG_INTEGER 超 256 位补码域抛）。 */
    public IntRefactor256 toIntRefactor256() {
        return switch (type) {
            case LONG -> IntRefactor256.of(longValue);
            case INT256 -> int256;
            case BIG_INTEGER -> of(Type.INT256, big).int256;
        };
    }

    public boolean isNegative() {
        return signum() < 0;
    }

    public boolean isZero() {
        return signum() == 0;
    }

    public int signum() {
        return switch (type) {
            case LONG -> Long.signum(longValue);
            case INT256 -> int256.signum();
            case BIG_INTEGER -> big.signum();
        };
    }

    // ═══════════ 运算（结果类型 = 当前默认类型） ═══════════

    public DynamicNumber add(final DynamicNumber o) {
        Objects.requireNonNull(o);
        return switch (defaultType) {
            case LONG -> of(Type.LONG, toLongChecked() + o.toLongChecked());
            case INT256 -> of(Type.INT256, toIntRefactor256().add(o.toIntRefactor256()));
            case BIG_INTEGER -> of(Type.BIG_INTEGER, toBigInteger().add(o.toBigInteger()));
        };
    }

    public DynamicNumber subtract(final DynamicNumber o) {
        Objects.requireNonNull(o);
        return switch (defaultType) {
            case LONG -> of(Type.LONG, toLongChecked() - o.toLongChecked());
            case INT256 -> of(Type.INT256, toIntRefactor256().subtract(o.toIntRefactor256()));
            case BIG_INTEGER -> of(Type.BIG_INTEGER, toBigInteger().subtract(o.toBigInteger()));
        };
    }

    public DynamicNumber multiply(final DynamicNumber o) {
        Objects.requireNonNull(o);
        return switch (defaultType) {
            case LONG -> of(Type.LONG, Math.multiplyExact(toLongChecked(), o.toLongChecked()));
            case INT256 -> {
                // 位宽和 ≤ 255 时 256 位内精确（IntRefactor256.multiply 不回绕）走快路径；
                // 否则精确乘积必超域——走 BigInteger 由 of 的域检查明确抛出不静默回绕
                final BigInteger pa = toBigInteger().abs(), pb = o.toBigInteger().abs();
                if (pa.bitLength() + pb.bitLength() <= 255) {
                    yield of(Type.INT256, toIntRefactor256().multiply(o.toIntRefactor256()));
                }
                yield of(Type.INT256, toBigInteger().multiply(o.toBigInteger()));
            }
            case BIG_INTEGER -> of(Type.BIG_INTEGER, toBigInteger().multiply(o.toBigInteger()));
        };
    }

    /** 整数除法（截断向零；除零抛 ArithmeticException）。 */
    public DynamicNumber divide(final DynamicNumber o) {
        Objects.requireNonNull(o);
        if (o.isZero()) {
            throw new ArithmeticException("DynamicNumber divide by zero");
        }
        return switch (defaultType) {
            case LONG -> of(Type.LONG, toLongChecked() / o.toLongChecked());
            case INT256 -> of(Type.INT256, toIntRefactor256().divide(o.toIntRefactor256()));
            case BIG_INTEGER -> of(Type.BIG_INTEGER, toBigInteger().divide(o.toBigInteger()));
        };
    }

    public DynamicNumber negate() {
        return switch (type) {
            case LONG -> of(Type.LONG, -longValue);
            case INT256 -> of(Type.INT256, int256.negate());
            case BIG_INTEGER -> of(Type.BIG_INTEGER, big.negate());
        };
    }

    public DynamicNumber abs() {
        return isNegative() ? negate() : this;
    }

    // ═══════════ Number 契约 ═══════════

    @Override
    public int intValue() {
        return toBigInteger().intValue();
    }

    @Override
    public long longValue() {
        return toLongChecked();
    }

    @Override
    public float floatValue() {
        return toBigInteger().floatValue();
    }

    @Override
    public double doubleValue() {
        return toBigInteger().doubleValue();
    }

    /** long 精确导出（超域抛——LONG 配置运算的溢出防线）。 */
    private long toLongChecked() {
        if (type == Type.LONG) {
            return longValue;
        }
        final BigInteger exact = toBigInteger();
        if (exact.compareTo(LONG_MIN) < 0 || exact.compareTo(LONG_MAX) > 0) {
            throw new ArithmeticException("DynamicNumber value out of long range");
        }
        return exact.longValue();
    }

    // ═══════════ 比较 / 相等 / 哈希 ═══════════

    /** 数值全序（跨类型可比）。 */
    public int compareTo(final DynamicNumber o) {
        Objects.requireNonNull(o);
        return toBigInteger().compareTo(o.toBigInteger());
    }

    /** 数值相等（跨类型：LONG 5 等于 INT256 5）。 */
    @Override
    public boolean equals(final Object obj) {
        return obj instanceof DynamicNumber other && toBigInteger().compareTo(other.toBigInteger()) == 0;
    }

    @Override
    public int hashCode() {
        return toBigInteger().hashCode();
    }

    @Override
    public String toString() {
        return "DynamicNumber[" + type + "](" + toBigInteger() + ")";
    }
}
