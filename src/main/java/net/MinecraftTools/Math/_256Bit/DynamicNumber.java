package net.MinecraftTools.Math._256Bit;

import net.MinecraftTools.Math.DynamicAccuracy.BigInteger;
import net.MinecraftTools.Math.DynamicAccuracy.BigDecimal;

import java.util.Objects;

/**
 * DynamicNumber — 统一数字容器
 * 
 * 包装 6 种数字类型：Long, Int256, UInt256, Float256, UFloat256, BigInteger
 * 根据 PrecisionConfig 自动选择运算精度
 * 
 * 所有运算返回新的 DynamicNumber，不可变
 * 
 * INF32768 / MCRe NoiseFarlands 项目
 */
public final class DynamicNumber extends Number implements Comparable<DynamicNumber> {

    // ═══════════ 内部存储 ═══════════
    private final Object value;
    private final NumberType type;

    // ──────── 缓存 ────────
    private transient int hash;
    private static final int HASH_UNCACHED = Integer.MIN_VALUE;

    // ──────── 常量 ────────
    public static final DynamicNumber ZERO = new DynamicNumber(NumberType.LONG, 0L);
    public static final DynamicNumber ONE  = new DynamicNumber(NumberType.LONG, 1L);
    public static final DynamicNumber TWO  = new DynamicNumber(NumberType.LONG, 2L);
    public static final DynamicNumber TEN  = new DynamicNumber(NumberType.LONG, 10L);

    // ──────── 构造 ────────
    private DynamicNumber(NumberType type, Object value) {
        this.type = type;
        this.value = value;
        this.hash = HASH_NOT_CACHED;
    }

    // ═══════════ 工厂方法 ═══════════

    /** 从 long (快速路径) */
    public static DynamicNumber of(long val) {
        if (val == 0) return ZERO;
        if (val == 1) return ONE;
        if (val == 2) return TWO;
        if (val == 10) return TEN;
        return new DynamicNumber(NumberType.LONG, val);
    }

    /** 从 double（先转 Float256） */
    public static DynamicNumber of(double val) {
        if (val == 0.0) return ZERO;
        if (val == 1.0) return ONE;
        if (Double.isNaN(val) || Double.isInfinite(val))
            throw new IllegalArgumentException("Cannot convert NaN/Inf to DynamicNumber");
        return new DynamicNumber(NumberType.FLOAT256, Float256.of(val));
    }

    /** 从 Int256 */
    public static DynamicNumber of(Int256 val) {
        return new DynamicNumber(NumberType.INT256, val);
    }

    /** 从 UInt256 */
    public static DynamicNumber of(UInt256 val) {
        return new DynamicNumber(NumberType.UINT256, val);
    }

    /** 从 Float256 */
    public static DynamicNumber of(Float256 val) {
        return new DynamicNumber(NumberType.FLOAT256, val);
    }

    /** 从 UFloat256 */
    public static DynamicNumber of(UFloat256 val) {
        return new DynamicNumber(NumberType.UFLOAT256, val);
    }

    /** 从 BigInteger (我们的优化版) */
    public static DynamicNumber of(BigInteger val) {
        return new DynamicNumber(NumberType.BIGINTEGER, val);
    }

    /** 从 BigDecimal */
    public static DynamicNumber of(BigDecimal val) {
        return of(val.toBigInteger());
    }

    // ═══════════ 类型查询 ═══════════

    public NumberType type() { return type; }

    public boolean isLong()      { return type == NumberType.LONG; }
    public boolean isInt256()    { return type == NumberType.INT256; }
    public boolean isUInt256()   { return type == NumberType.UINT256; }
    public boolean isFloat256()  { return type == NumberType.FLOAT256; }
    public boolean isUFloat256() { return type == NumberType.UFLOAT256; }
    public boolean isBigInt()    { return type == NumberType.BIGINTEGER; }

    // ═══════════ 提取 ═══════════

    public long toLong() {
        return switch (type) {
            case LONG       -> (long) value;
            case INT256     -> ((Int256) value).longValue();
            case UINT256    -> ((UInt256) value).longValue();
            case FLOAT256   -> ((Float256) value).longValue();
            case UFLOAT256  -> ((UFloat256) value).longValue();
            case BIGINTEGER -> ((BigInteger) value).longValue();
        };
    }

    public Int256 toInt256() {
        return switch (type) {
            case LONG       -> Int256.of((long) value);
            case INT256     -> (Int256) value;
            case UINT256    -> ((UInt256) value).toInt256();
            case FLOAT256, UFLOAT256 -> Int256.of(toLong());
            case BIGINTEGER -> Int256.of(((BigInteger) value).toByteArray());
        };
    }

    public UInt256 toUInt256() {
        return switch (type) {
            case LONG       -> UInt256.of((long) value);
            case INT256     -> UInt256.fromInt256((Int256) value);
            case UINT256    -> (UInt256) value;
            case FLOAT256, UFLOAT256 -> UInt256.of(toLong());
            case BIGINTEGER -> UInt256.of(((BigInteger) value).toByteArray());
        };
    }

    public Float256 toFloat256() {
        return switch (type) {
            case LONG       -> Float256.of((long) value);
            case INT256     -> Float256.of((Int256) value);
            case UINT256    -> Float256.of(((UInt256) value).toBigIndia());
            case FLOAT256   -> (Float256) value;
            case UFLOAT256  -> ((UFloat256) value).toFloat256();
            case BIGINTEGER -> Float256.of((BigInteger) value);
        };
    }

    public UFloat256 toUFloat256() {
        return switch (type) {
            case LONG       -> UFloat256.of((long) value);
            case INT256     -> UFloat256.of(((Int256) value).abs());
            case UINT256    -> UFloat256.of((UInt256) value);
            case FLOAT256   -> UFloat256.of(((Float256) value).abs());
            case UFLOAT256  -> (UFloat256) value;
            case BIGINTEGER -> UFloat256.of(((BigInteger) value).abs());
        };
    }

    public BigInteger toBigInteger() {
        return switch (type) {
            case LONG       -> BigInteger.valueOf((long) value);
            case INT256     -> ((Int256) value).toBigInteger();
            case UINT256    -> ((UInt256) value).toBigInteger();
            case FLOAT256   -> ((Float256) value).toBigDecimal().toBigInteger();
            case UFLOAT256  -> ((UFloat256) value).toBigDecimal().toBigInteger();
            case BIGINTEGER -> (BigInteger) value;
        };
    }

    // ═══════════ 核心运算 ═══════════

    public DynamicNumber add(DynamicNumber o) {
        NumberType resultType = ResultType(type, o.type);
        return switch (resultType) {
            case LONG       -> of(toLong() + o.toLong());
            case INT256     -> of(toInt256().add(o.toInt256()));
            case UINT256    -> of(toUInt256().add(o.toUInt256()));
            case FLOAT256   -> of(toFloat256().add(o.toFloat256()));
            case UFLOAT256  -> of(toUFloat256().add(o.toUFloat256()));
            case BIGINTEGER -> of(toBigInteger().add(o.toBigInteger()));
        };
    }

    public DynamicNumber subtract(DynamicNumber other) {
        NumberType resultType = ResultType(type, o.type);
        return switch (resultType) {
            case LONG       -> DynamicNumber.of(toLong() - o.toLong());
            case INT256     -> DynamicNumber.of(toInt256().subtract(o.toInt256()));
            case UINT256    -> DynamicNumber.of(toUInt256().subtract(o.toUInt256()));
            case FLOAT256   -> DynamicNumber.of(toFloat256().subtract(o.toFloat256()));
            case UFLOAT256  -> DynamicNumber.of(toUFloat256().subtract(o.toUFloat256()));
            case BIGINTEGER -> DynamicNumber.of(toBigInteger().subtract(o.toBigInteger()));
        };
    }

    public DynamicNumber multiply(DynamicNumber other) {
        NumberType resultType = ResultType(type, o.type);
        return switch (resultType) {
            case LONG       -> DynamicNumber.of(toLong() * o.toLong());
            case INT256     -> DynamicNumber.of(toInt256().multiply(o.toInt256()));
            case UINT256    -> DynamicNumber.of(toUInt256().multiply(o.toUInt256()));
            case FLOAT256   -> DynamicNumber.of(toFloat256().multiply(o.toFloat256()));
            case UFLOAT256  -> DynamicNumber.of(toUFloat256().multiply(o.toUFloat256()));
            case BIGINTEGER -> DynamicNumber.of(toBigInteger().multiply(o.toBigInteger()));
        };
    }

    public DynamicNumber divide(DynamicNumber other) {
        NumberType resultType = ResultType(type, o.type);
        return switch (resultType) {
            case LONG       -> of((double) toLong() / o.toLong());
            case INT256     -> of(toInt256().divide(o.toInt256()));
            case UINT256    -> of(toUInt256().divide(o.toUInt256()));
            case FLOAT256   -> of(toFloat256().divide(o.toFloat256()));
            case UFLOAT256  -> of(toUFloat256().divide(o.toUFloat256()));
            case BIGINTEGER -> of(toBigInteger().divide(o.toBigInteger()));
        };
    }

    // ═══════════ 位运算 ═══════════

    public DynamicNumber shiftLeft(int n) {
        return switch (type) {
            case LONG       -> DynamicNumber.of((long) value << n);
            case INT256     -> DynamicNumber.of(((Int256) value).shiftLeft(n));
            case UINT256    -> DynamicNumber.of(((UInt256) value).shiftLeft(n));
            case BIGINTEGER -> DynamicNumber.of(((BigInteger) value).shiftLeft(n));
            default -> this;
        };
    }

    public DynamicNumber shiftRight(int n) {
        return switch (type) {
            case LONG       -> DynamicNumber.of((long) value >> n);
            case INT256     -> DynamicNumber.of(((Int256) value).shiftRight(n));
            case UINT256    -> DynamicNumber.of(((UInt256) value).shiftRight(n));
            case BIGINTEGER -> DynamicNumber.of(((BigInteger) value).shiftRight(n));
            default -> this;
        };
    }

    // ═══════════ 比较 ═══════════

    @Override
    public int compareTo(DynamicNumber o) {
        NumberType common = ResultType(type, o.type);
        return switch (common) {
            case LONG       -> Long.compare(toLong(), o.toLong());
            case INT256     -> toInt256().compareTo(o.toInt256());
            case UINT256    -> toUInt256().compareTo(o.toUInt256());
            case FLOAT256   -> toFloat256().compareTo(o.toFloat256());
            case UFLOAT256  -> toUFloat256().compareTo(o.toUFloat256());
            case BIGINTEGER -> toBigInteger().compareTo(o.toBigInteger());
        };
    }

    // ═══════════ Java Number 接口 ═══════════

    @Override public long longValue()   { return toLong(); }
    @Override public int  intValue()    { return (int) toLong(); }
    @Override public double doubleValue() { return toFloat256().doubleValue(); }
    @Override public float floatValue()   { return (float) toFloat256().doubleValue(); }

    @Override public String toString() {
        return switch (type) {
            case LONG       -> Long.toString((long) value);
            case INT256     -> ((Int256) value).toString();
            case UINT256    -> ((UInt256) value).toString();
            case FLOAT256   -> ((Float256) value).toString();
            case UFLOAT256  -> ((UFloat256) value).toString();
            case BIGINTEGER -> ((BigInteger) value).toString();
        };
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof DynamicNumber other)) return false;
        return compareTo(other) == 0;
    }

    @Override public int hashCode() {
        if (hash == HASH_NOT_CACHED) hash = toBigInteger().hashCode();
        return hash;
    }

    // ═══════════ 类型提升 ═══════════

    private static NumberType ResultType(NumberType a, NumberType b) {
        if (a == b) return a;
        // 提升规则：Long < Int256 < UInt256 < Float256 < UFloat256 < BigInteger
        int rankA = rank(a);
        int rankB = rank(b);
        return rankA >= rankB ? a : b;
    }

    private static int rank(NumberType t) {
        return switch (t) {
            case LONG       -> 0;
            case INT256     -> 1;
            case UINT256    -> 2;
            case FLOAT256   -> 3;
            case UFLOAT256  -> 4;
            case BIGINTEGER -> 5;
        };
    }

    // ══════════════════════ 配置接口 ══════════════════════

    /**
     * 连接到 PrecisionConfig（后续实现）
     */
    public DynamicNumber convertTo(NumberType targetType) {
        return switch (targetType) {
            case LONG       -> DynamicNumber.of(toLong());
            case INT256     -> DynamicNumber.of(toInt256());
            case UINT256    -> DynamicNumber.of(toUInt256());
            case FLOAT256   -> DynamicNumber.of(toFloat256());
            case UFLOAT256  -> DynamicNumber.of(toUFloat256());
            case BIGINTEGER -> DynamicNumber.of(toBigInteger());
        };
    }

    // ═══════════════════════ 测试 ═══════════════════════

    public static void main(String[] args) {
        System.out.println("=== DynamicNumber 验证 ===");
        DynamicNumber a = DynamicNumber.of(100_000);
        DynamicNumber b = DynamicNumber.of(Int256.ONE.shiftLeft(72));
        System.out.println("a (long) = " + a);
        System.out.println("b (Int256) = " + b);
        System.out.println("a + b = " + a.add(b));
        System.out.println("a * b = " + a.multiply(b));
        System.out.println("type of a + b = " + a.add(b).type());
    }
}