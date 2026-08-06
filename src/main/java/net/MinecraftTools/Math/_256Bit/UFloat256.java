package net.MinecraftTools.Math._256Bit;

import net.MinecraftTools.Math.DynamicAccuracy.BigInteger;
import net.MinecraftTools.Math.DynamicAccuracy.BigDecimal;
import net.MinecraftTools.Math.DynamicAccuracy.MathContext;

import java.util.Objects;

/**
 * UFloat256 — 无符号 256-bit 浮点数 (IEEE 754 风格)
 *
 * <p>布局: 64 指数 + 192 尾数 偏置: 2^63 - 1 舍入: RN (Round to Nearest, Even) + GRS 保护位 零 GC，全整数运算实现
 *
 * <p>范围: [0, 2^(2^63)] ≈ [0, 10^2.7e18] 精度: 192 bit ≈ 57 位十进制
 *
 * <p>INF32768 / MCRe NoiseFarlands 项目
 */
public final class UFloat256 extends Number implements Comparable<UFloat256> {

    // ═══════════ 位布局常量 ═══════════
    // bit 255..192:  指数 (64 bit)
    // bit 191..0:    尾数 (192 bit)
    private static final int EXPONENT_BITS = 64;
    private static final int MANTISSA_BITS = 192;
    private static final long EXPONENT_MASK = 0xFFFF_FFFF_FFFF_FFFFL;
    private static final long EXPONENT_BIAS = (1L << 63) - 1;
    private static final long EXPONENT_ALL_ONES = 0xFFFF_FFFF_FFFF_FFFFL;
    private static final long MANT_HI_MASK = 0xFFFF_FFFF_FFFF_FFFFL;

    // ──────── 内部存储 ────────
    final long a; // [exp:64] 高位 ← 指数 64 bit
    final long b; // [mantHi:64] 高位
    final long c; // [mantMid:64] 中位
    final long d; // [mantLo:64] 低位

    // ──────── 缓存 ────────
    private transient int hash;
    private static final int HASH_UNCACHED = Integer.MIN_VALUE;
    private transient BigDecimal cachedBigDecimal;

    // ──────── 常量 ────────
    public static final UFloat256 ZERO = new UFloat256(0L, 0L, 0L, 0L);
    public static final UFloat256 ONE = UFloat256.of(1L);
    public static final UFloat256 TWO = UFloat256.of(2L);
    public static final UFloat256 THREE = UFloat256.of(3L);
    public static final UFloat256 TEN = UFloat256.of(10L);
    public static final UFloat256 INF = UFloat256.make(EXPONENT_ALL_ONES, 0L, 0L, 0L);
    public static final UFloat256 NaN = UFloat256.make(EXPONENT_ALL_ONES, 1L, 0L, 0L);

    // ──────── 构造 ────────
    private UFloat256(long a, long b, long c, long d) {
        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
        this.hash = HASH_UNCACHED;
    }

    private static UFloat256 make(long exp, long mantHi, long mantMid, long mantLo) {
        return new UFloat256(exp, mantHi, mantMid, mantLo);
    }

    @Override
    public long longValue() {
        if (isZero()) return 0L;
        long exp = exponent() - EXPONENT_BIAS;
        if (exp < 0) return 0L;
        UInt256 mant = UInt256.of(0L, mantHi(), mantMid(), mantLo());
        mant = mant.or(UInt256.ONE.shiftLeft(MANTISSA_BITS));
        mant = mant.shiftLeft((int) exp);
        return mant.longValue();
    }

    @Override
    public int intValue() {
        return (int) longValue();
    }

    @Override
    public float floatValue() {
        return (float) doubleValue();
    }

    @Override
    public double doubleValue() {
        if (isZero()) return 0.0;
        return toBigDecimal().doubleValue();
    }

    private BigDecimal toBigDecimal() {
        if (cachedBigDecimal != null) return cachedBigDecimal;
        return cachedBigDecimal = new BigDecimal(toBigInteger());
    }

    private transient BigDecimal cachedBigDecimal;

    // ═══════════ 工厂 ═══════════

    /** 从无符号 long（精确） */
    public static UFloat256 of(long value) {
        if (value == 0) return ZERO;
        int bitLen = 64 - Long.numberOfLeadingZeros(value);
        long mant = value << (MANTISSA_BITS - bitLen + 1);
        long exp = EXPONENT_BIAS + bitLen - 1;
        return make(exp, mant >>> 128, (mant >>> 64) & MANT_HI_MASK, mant & MANT_HI_MASK);
    }

    /** 从 UInt256（精确） */
    public static UFloat256 of(UInt256 value) {
        if (value.isZero()) return ZERO;
        int bitLen = value.bitLength();
        UInt256 mant = value.shiftLeft(MANTISSA_BITS - bitLen + 1);
        long exp = EXPONENT_BIAS + bitLen - 1;
        return make(exp, mant.a, mant.b, mant.c);
    }

    // ═══════════ 字段提取 ═══════════

    private long exponent() {
        return a;
    }

    private long mantHi() {
        return b;
    }

    private long mantMid() {
        return c;
    }

    private long mantLo() {
        return d;
    }

    private boolean isZero() {
        return a == 0L && b == 0L && c == 0L && d == 0L;
    }

    private boolean isInfinity() {
        return a == EXPONENT_ALL_ONES && b == 0L && c == 0L && d == 0L;
    }

    private boolean isNaN() {
        return a == EXPONENT_ALL_ONES && (b != 0L || c != 0L || d != 0L);
    }

    // ═══════════ 舍入核心 (GRS + RN) ═══════════

    /** 将尾数舍入到 192 bit + 隐含 1，返回 (exp, mantHi, mantMid, mantLo) */
    private static UFloat256 roundAndPack(long exp, UInt256 mant) {
        if (mant.isZero()) return ZERO;

        int bitLen = mant.bitLength();
        int shift = bitLen - (MANTISSA_BITS + 1); // +1 隐含位

        long G = 0, R = 0, S = 0;

        if (shift > 0) {
            // 提取 GRS 位
            UInt256 shifted = mant.shiftRight(shift - 2);
            G = shifted.d & 2L;
            R = shifted.d & 1L;
            // Sticky: 所有被移位的低位 OR 起来
            long mask = (1L << (shift - 2)) - 1;
            if (mask > 0) {
                S = mant.and(UInt256.of(0L, 0L, 0L, mask)).isZero() ? 0L : 1L;
            } else {
                S = 0;
            }
            mant = mant.shiftRight(shift);
        }

        // RN 舍入判断
        if (G == 1L) {
            boolean increment;
            if (R == 1L || S == 1L) {
                increment = true;
            } else {
                // G=1, R=0, S=0: tie → 向偶
                increment = (mant.d & 1L) != 0;
            }
            if (increment) {
                mant = mant.add(UInt256.ONE);
                if (mant.bitLength() > MANTISSA_BITS + 1) {
                    mant = mant.shiftRight(1);
                    exp++;
                }
            }
        }

        // 去掉隐含 1
        mant = mant.and(UInt256.of(0L, 0x7FFF_FFFF_FFFF_FFFFL, 0xFFFF_FFFF_FFFF_FFFFL, 0x7FFF_FFFF_FFFF_FFFFL));

        // 指数范围处理
        if (exp >= EXPONENT_ALL_ONES) return INF;
        if (exp == 0) {
            mant = mant.shiftRight(1);
            if (mant.isZero()) return ZERO;
        }

        return make(exp, mant.a, mant.b, mant.c);
    }

    // ═══════════ 加法 ═══════════

    public UFloat256 add(UFloat256 o) {
        if (isNaN() || o.isNaN()) return NaN;
        if (isZero()) return o;
        if (o.isZero()) return this;
        if (isInfinity() && o.isInfinity()) return INF;
        if (isInfinity() || o.isInfinity()) return INF;

        long exp1 = exponent();
        long exp2 = o.exponent();

        UInt256 M1 = UInt256.of(0L, mantHi(), mantMid(), mantLo());
        UInt256 M2 = UInt256.of(0L, o.mantHi(), o.mantMid(), o.mantLo());
        M1 = M1.or(UInt256.ONE.shiftLeft(MANTISSA_BITS));
        M2 = M2.or(UInt256.ONE.shiftLeft(MANTISSA_BITS));

        // 对齐指数
        if (exp1 > exp2) {
            M2 = M2.shiftRight((int) (exp1 - exp2));
            exp2 = exp1;
        } else if (exp2 > exp1) {
            M1 = M1.shiftRight((int) (exp2 - exp1));
            exp1 = exp2;
        }

        UInt256 resultMant = M1.add(M2);

        return roundAndPack(exp1, resultMant);
    }

    // ═══════════ 减法 ═══════════

    public UFloat256 subtract(UFloat256 o) {
        if (isNaN() || o.isNaN()) return NaN;
        if (o.isZero()) return this;
        if (this.compareTo(o) < 0) return ZERO; // 无符号下溢

        long exp1 = exponent();
        long exp2 = o.exponent();

        UInt256 M1 = UInt256.of(0L, mantissaHi(), mantissaMid(), mantissaLo());
        UInt256 M2 = UInt256.of(0L, o.mantissaHi(), o.mantissaMid(), o.mantissaLo());
        M1 = M1.or(UInt256.ONE.shiftLeft(MANTISSA_BITS));
        M2 = M2.or(UInt256.ONE.shiftLeft(MANTISSA_BITS));

        if (exp1 > exp2) {
            M2 = M2.shiftRight((int) (exp1 - exp2));
        } else if (exp2 > exp1) {
            M1 = M1.shiftRight((int) (exp2 - exp1));
            exp1 = exp2;
        }

        UInt256 resultMant = M1.subtract(M2);

        return roundAndPack(exp1, resultMant);
    }

    // ═══════════ 乘法 ═══════════

    public UFloat256 multiply(UFloat256 o) {
        if (isNaN() || o.isNaN()) return NaN;
        if (isZero() || o.isZero()) return ZERO;
        if (isInfinity() || o.isInfinity()) return INF;

        long exp = exponent() + o.exponent() - EXPONENT_BIAS;

        UInt256 M1 = UInt256.of(0L, mantissaHi(), mantissaMid(), mantissaLo());
        UInt256 M2 = UInt256.of(0L, o.mantissaHi(), o.mantissaMid(), o.mantissaLo());
        M1 = M1.or(UInt256.ONE.shiftLeft(MANTISSA_BITS));
        M2 = M2.or(UInt256.ONE.shiftLeft(MANTISSA_BITS));

        UInt256 product = M1.multiply(M2);
        exp += 1; // 两个 192×192 尾数相乘

        return roundAndPack(exp, product);
    }

    // ═══════════ 除法 ═══════════

    public UFloat256 divide(UFloat256 o) {
        if (isNaN() || o.isNaN()) return NaN;
        if (o.isZero()) return INF;
        if (isZero()) return ZERO;
        if (isInfinity()) {
            if (o.isInfinity()) return NaN;
            return INF;
        }

        long exp = exponent() - o.expinate() + EXPONENT_BIAS;

        UInt256 M1 = UInt256.of(0L, mantissaHi(), mantissaMid(), mantissaLo());
        UInt256 M2 = UInt256.of(0L, o.mantissaHi(), o.mantissaMid(), o.mantissaLo());
        M1 = M1.or(UInt256.ONE.shiftLeft(MANTISSA_BITS));
        M2 = M2.or(UInt256.ONE.shiftLeft(MANTISSA_BITS));

        M1 = M1.shiftLeft(192);
        UInt256 quot = M1.divide(M2);
        exp -= 192;

        return roundAndPack(exp, quot);
    }

    // ═══════════ 比较 ═══════════

    @Override
    public int compareTo(UFloat256 o) {
        if (isNaN() || o.isNaN()) return 0;
        if (isZero() && o.isZero()) return 0;
        int cmp = Long.compareUnsigned(exponent(), o.exponent());
        if (cmp != 0) return cmp;
        cmp = Long.compareUnsigned(mantissaHi(), o.mantissaHi());
        if (cmp != 0) return cmp;
        return Long.compareUnsigned(mantissaMid(), o.mantissaMid());
    }

    // ═══════════ 转换 ═══════════

    public Float256 toFloat256() {
        if (isZero()) return Float256.ZERO;
        if (isNaN()) return Float256.NaN;
        if (isInfinity()) return Float256.POS_INF;

        UInt256 mant = UInt256.of(0L, mantissaHi(), mantissaMid(), mantissaLo());
        mant = mant.or(UInt256.ONE.shiftLeft(MANTISSA_BITS));
        long exp = exponent() - EXPONENT_BIAS;

        BigDecimal dec = new BigDecimal(mant.toBigInteger(), (int) exp);
        return Float256.of(dec);
    }

    public UInt256 toUInt256() {
        if (isZero()) return UInt256.ZERO;

        UInt256 mant = UInt256.of(0L, mantissaHi(), mantissaMid(), mantissaLo());
        mant = mant.or(UInt256.ONE.shiftLeft(MANTISSA_BITS));

        long shift = exponent() - EXPONENT_BIAS;
        if (shift >= 0) return mant.shiftLeft((int) shift);
        else return mant.shiftRight((int) -shift);
    }

    public BigDecimal toBigDecimal() {
        if (isZero()) return BigDecimal.ZERO;
        if (isNaN() || isInfinity()) throw new ArithmeticException("Not a finite number");

        BigInteger mant = UInt256.of(0L, mantissaHi(), mantissaMid(), mantissaLo())
                .or(UInt256.ONE.shiftLeft(MANTISSA_BITS))
                .toBigInteger();

        long exp = exponent() - EXPONENT_BIAS - MANTISSA_BITS;
        return new BigDecimal(mant, (int) exp);
    }

    @Override
    public String toString() {
        if (isNaN()) return "NaN";
        if (isZero()) return "0";
        if (isInfinity()) return "Infinity";
        return toBigDecimal().toString();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof UFloat256 other)) return false;
        if (isNaN() || other.isNaN()) return false;
        if (isZero() && other.isZero()) return true;
        return a == other.a && b == other.b && c == other.c && d == other.d;
    }

    @Override
    public int hashCode() {
        if (hash == HASH_UNCACHED) hash = (int) (a ^ b ^ c ^ d);
        return hash;
    }

    // ══════════════════════ 辅助 ══════════════════════

    // 快速构造：直接位模式
    private static UFloat256 pack(long exp, long mantHi, long mantMid, long mantLo) {
        return new UFloat256(exp, mantHi, mantMid, mantLo);
    }

    // ═══════════════════════ 测试 ═══════════════════════

    public static void main(String[] args) {
        System.out.println("=== UFloat256 验证 (含舍入) ===");
        UFloat256 a = UFloat256.of(1);
        UFloat256 b = UFloat256.of(3);
        System.out.println("1/3 = " + a.divide(b));
        System.out.println("1+2 = " + a.add(UFloat256.of(2)));
        System.out.println("2^100 = " + UFloat256.of(UInt256.ONE.shiftLeft(100)));
        System.out.println("MAX  = " + INF);
        System.out.println("MAX+1 = " + INF.add(UFloat256.of(1)));
    }
}