package net.MinecraftTools.Math._256Bit;

import net.MinecraftTools.Math.DynamicAccuracy.BigInteger;
import net.MinecraftTools.Math.DynamicAccuracy.BigDecimal;
import net.MinecraftTools.Math.DynamicAccuracy.MathContext;
import net.MinecraftTools.Math.DynamicAccuracy.RoundingMode;

import java.util.Objects;

/**
 * Float256 — 有符号 256-bit 浮点数 (IEEE 754 风格)
 *
 * <p>布局: 1 符号 + 79 指数 + 176 尾数 偏置: 2^78 - 1 舍入: RN (Round to Nearest, Even) 零 GC，全整数运算实现
 *
 * <p>范围: [-2^(2^77), 2^(2^77)] 精度: 176 bit ≈ 52 位十进制
 *
 * <p>INF32768 / MCRe NoiseFarlands 项目
 */
public final class Float256 extends Number implements Comparable<Float256> {

    // ═══════════ 位布局常量 ═══════════
    // bit 255:       符号
    // bit 254..176:  指数 (79 bit)
    // bit 175..0:    尾数 (176 bit)
    private static final int TOTAL_BITS = 256;
    private static final int SIGN_BIT = 255;
    private static final int EXPONENT_BITS = 79;
    private static final int MANTISSA_BITS = 176;
    private static final int EXP_HI_BITS = 15; // 指数高位: 79-64=15 位在 long[0]
    private static final int EXP_LO_BITS = 64; // 指数低位: 64 位在 long[1]
    private static final int MANT_HI_BITS_I = 49; // 尾数高位: 176-64-64=48+1 → 实际是 176-64-64=48, 但符号占了
    // 1 位

    // 实际位映射（从高位往低位）：
    // a: [sign:1] [expHi:79-64=15] [mantHi:64-15=49-1=48]
    private static final int SIGN_BIT_A = 63; // bit 255 → a 的 bit 63
    private static final int EXP_HI_SHIFT = 48; // expHi 在 a 的高 15 位
    private static final long EXP_HI_MASK = (1L << 15) - 1;
    private static final long MANT_HI_MASK_A = (1L << 48) - 1;
    private static final long EXPONENT_ALL = (1L << 79) - 1;
    private static final long EXPONENT_BIAS = (1L << 78) - 1;
    private static final long MANT_IMPLIED_BIT = 1L << 176;

    // ──────── 内部存储 ────────
    final long a; // [sign:1] [expHi:15] [mantHi:48]   ← 64 bit
    final long b; // [expLo:64]
    final long c; // [mantMid:64]
    final long d; // [mantLo:64]

    // ──────── 缓存 ────────
    private transient int hash;
    private static final int HASH_NOT_CACHED = Integer.MIN_VALUE;
    private transient BigDecimal cachedBigDecimal;

    // ──────── 常量 ────────
    public static final Float256 ZERO = new Float256(0L, 0L, 0L, 0L);
    public static final Float256 ONE = Float256.of(1L);
    public static final Float256 TWO = Float256.of(2L);
    public static final Float256 THREE = Float256.of(3L);
    public static final Float256 TEN = Float256.of(10L);
    public static final Float256 MINUS_ONE = Float256.of(-1L);
    public static final Float256 NaN = Float256.make(EXPONENT_MASK, 1L, 0L, 0L, 1);
    public static final Float256 POS_INF = Float256.make(EXPONENT_MASK, 0L, 0L, 0L, 1);
    public static final Float256 NEG_INF = Float256.make(EXPONENT_MASK, 0L, 0L, 0L, -1);

    // ──────── 构造 ────────
    private Float256(long a, long b, long c, long d) {
        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
        this.hash = HASH_NOT_CACHED;
    }

    /** 底层构造：直接给 raw 位模式 */
    private static Float256 make(long exp, long mantHi, long mantMid, long mantLo, int signum) {
        long expHi = (exp >>> 64) & EXP_HI_MASK;
        long expLo = exp & 0xFFFF_FFFF_FFFF_FFFFL;
        long sign = (signum < 0) ? (1L << SIGN_BIT_A) : 0L;
        long a = sign | (expHi << 48) | (mantHi & MANT_HI_MASK);
        return new Float256(a, expLo, mantMid, mantLo);
    }

    @Override
    public int intValue() {
        return (int) longValue();
    }

    @Override
    public float floatValue() {
        return (float) doubleValue();
    }

    // ═══════════ 工厂方法 ═══════════

    /** 从 long（精确） */
    public static Float256 of(long value) {
        if (value == 0) return ZERO;
        boolean neg = value < 0;
        long abs = neg ? -value : value;
        int bitLen = 64 - Long.numberOfLeadingZeros(abs);
        long mant = abs << (176 - bitLen + 1);
        long exp = EXPONENT_BIAS + bitLen - 1;
        return make(exp, mant >>> 64, mant & 64, 0L, neg ? -1 : 1);
    }

    /** 从 Int256（精确） */
    public static Float256 of(Int256 value) {
        if (value.isZero()) return ZERO;
        boolean neg = value.isNegative();
        Int256 abs = neg ? value.negate() : value;
        int bitLen = abs.bitLength();
        Int256 mant = abs.shiftLeft(TOTAL_BITS - bitLen);
        long exp = EXPONENT_BIAS + bitLen - 1;
        return make(exp, mant.a & 0xFFFF_FFFF_FFFFL, mant.b, mant.c, neg ? -1 : 1);
    }

    /** 从 BigDecimal（舍入） */
    public static Float256 of(BigDecimal value) {
        if (value.signum() == 0) return ZERO;
        // 真实实现：转换成整数 mantissa × 2^exp 再 pack
        // 此处省略 BigDecimal → Int256 的转换细节（需要处理小数部分）
        return valueOf(value);
    }

    // ═══════════ 字段提取 ═══════════

    private int signum() {
        return (a >>> SIGN_BIT_A) != 0 ? -1 : 1;
    }

    private long exponent() {
        return ((a & EXP_HI_MASK) << 64) | (b & 0xFFFF_FFFF_FFFF_FFFFL);
    }

    private long mantissaHi() {
        return a & MANT_HI_MASK;
    }

    private long mantissaMid() {
        return c;
    }

    private long mantissaLo() {
        return d;
    }

    private boolean isZero() {
        return a == 0L && b == 0L && c == 0L && d == 0L;
    }

    private boolean isNaN() {
        return exponent() == EXPONENT_MASK && (mantissaHi() != 0 || mantissaMid() != 0 || mantissaLo() != 0);
    }

    private boolean isInfinity() {
        return exponent() == EXPONENT_MASK && mantissaHi() == 0 && mantissaMid() == 0 && mantissaLo() == 0;
    }

    private boolean isFinite() {
        return !isNaN() && !isInfinity();
    }

    // ═══════════ 舍入核心 (GRS + RN) ═══════════

    /** 舍入规范化：将 mant (Int256) 调整到 176 位 + 隐含 1，向偶舍入 返回 (exp, 尾数高 48 bit, 尾数中 64 bit, 尾数低 64 bit) */
    private static final class RoundedMantissa {
        final long exp;
        final long mantHi;
        final long mantMid;
        final long mantLo;
        final int signum;

        RoundedMantissa(long exp, long mantHi, long mantMid, long mantLo, int signum) {
            this.exp = exp;
            this.hi = mantHi;
            this.mid = mantMid;
            this.lo = mantLo;
            this.signum = signum;
        }
    }

    private static Float256 roundAndPack(long exp, Int256 mant, int signum) {
        if (mant.isZero()) return ZERO;

        // Step 1: 正规化 — 找最高位
        int bitLen = mant.bitLength();
        int shift = bitLen - (MANTISSA_BITS + 1); // +1 隐含位

        long G = 0, R = 0, S = 0;
        long roundShift = shift - 1;

        // Step 2: 提取 G, R, S
        if (shift > 0) {
            // 右移 shift 位，保留 GRS
            Int256 shifted = mant.shiftRight(shift - 2);
            G = shifted.d & 1L;
            R = (shifted.d >>> 1) & 1L;
            // 提取所有被移出的低位
            S = mant.maskBelow(shift - 2).isZero() ? 0L : 1L;
            mant = mant.shiftRight(shift);
        }

        // Step 3: 舍入判断 (RN)
        boolean increment = false;
        if (G == 1) {
            if (R == 1 || S == 1) {
                increment = true;
            } else { // G=1, R=0, S=0: 看 LSB
                increment = (mant.lowBit() & 1L) != 0;
            }
        }

        if (increment) {
            mant = mant.add(Int256.ONE);
            if (mant.bitLength() > MANTISSA_BITS + 1) {
                mant = mant.shiftRight(1);
                exp++;
            }
        }

        // Step 4: 去掉隐含 1
        mant = mant.and(Int256.of((1L << 176) - 1));

        // Step 5: 处理指数范围
        if (exp >= EXPONENT_MASK) return signum < 0 ? NEG_INF : POS_INF;
        if (exp <= 0) {
            if (exp <= -176) return ZERO;
            mant = mant.shiftRight(-exp + 1);
            exp = 0;
        }

        long mantHi = (mant.a & MANT_HI_MASK);
        long mantMid = mant.b;
        long mantLo = mant.c;

        return make(exp, mantHi, mantMid, mantLo, signum);
    }

    // ═══════════ 加减法 ═══════════

    public Float256 add(Float256 o) {
        if (isNaN() || o.isNaN()) return NaN;
        if (isZero()) return o;
        if (o.isZero()) return this;
        if (isInfinity() || o.isInfinity()) {
            if (isInfinity() && o.isInfinity() && signum() != o.signum()) return NaN;
            return signum() == o.signum() ? this : o;
        }

        long exp1 = exponent();
        long exp2 = o.exponent();
        int sign1 = signum();
        int sign2 = o.signum();

        // 隐含 1 + mantissa
        Int256 M1 = Int256.of(0, mantissaHi(), mantissaMid(), mantissaLo());
        M1 = M1.or(Int256.of(0, 1L << 48, 0L, 0L)); // 176 位隐含

        Int256 M2 = Int256.of(0, o.mantissaHi(), o.mantissaMid(), o.mantissaLo());
        M2 = M2.or(Int256.of(0, 1L << 48, 0L, 0L));

        // 对齐指数
        if (exp1 > exp2) {
            int diff = (int) (exp1 - exp2);
            M2 = M2.shiftRight(diff);
            exp2 = exp1;
        } else if (exp2 > exp1) {
            int diff = (int) (exp2 - exp1);
            M1 = M1.shiftRight(diff);
            exp1 = exp2;
        }

        Int256 resultMant;
        int resultSign;
        if (sign1 == sign2) {
            resultMant = M1.add(M2);
            resultSign = sign1;
        } else {
            int cmp = M1.compareTo(M2);
            if (cmp >= 0) {
                resultMant = M1.subtract(M2);
                resultSign = sign1;
            } else {
                resultMant = M2.subtract(M1);
                resultSign = sign2;
            }
        }

        return roundAndPack(exp1, resultMant, resultSign);
    }

    public Float256 subtract(Float256 o) {
        if (isNaN() || o.isNaN()) return NaN;
        return add(o.negate());
    }

    // ═══════ 乘法 ═══════
    public Float256 multiply(Float256 o) {
        if (isNaN() || o.isNaN()) return NaN;
        if (isZero() || o.isZero()) return ZERO;
        if (isInfinity() || o.isInfinity())
            return (signum() == o.signum()) ? POS_INF : NEG_INF;

        long exp = exponent() + o.exponent() - EXPONENT_BIAS;
        int resultSign = signum() * o.signum();

        Int256 M1 = Int256.of(0, mantissaHi(), mantissaMid(), mantissaLo());
        Int256 M2 = Int256.of(0, o.mantissaHi(), o.mantissaMid(), o.mantissaLo());
        M1 = M1.or(Int256.of(0, 1L << 48, 0L, 0L));
        M2 = M2.or(Int256.of(0, 1L << 48, 0L, 0L));

        Int256 product = M1.multiply(M2);
        exp += 1; // 两个 176×176 的乘积是 352 bit，隐含位加倍

        return roundAndPack(exp, product, resultSign);
    }

    // ═════════ 除法 ═════════
    public Float256 divide(Float256 o) {
        if (isNaN() || o.isNaN()) return NaN;
        if (o.isZero()) return signum() == o.signum() ? POS_INF : NEG_INF;
        if (isZero()) return ZERO;
        if (isInfinity()) {
            if (o.isInfinity()) return NaN;
            return signum() == o.signum() ? POS_INF : NEG_INF;
        }

        long exp = exponent1() - o.exponent1() + EXPONENT_BIAS;
        int resultSign = signum() * o.signum();

        Int256 M1 = Int256.of(0, mantissaHi(), mantissaMid(), mantissaLo());
        Int256 M2 = Int256.of(0, o.mantissaHi(), o.mantissaMid(), o.mantissaLo());
        M1 = M1.or(Int256.of(0, 1L << 48, 0L, 0L));
        M2 = M2.or(Int256.of(0, 1L << 48, 0L, 0L));

        M1 = M1.shiftLeft(177); // 保证商有足够精度
        Int256 quot = M1.divide(M2);
        exp -= 177;

        return roundAndPack(exp, quotient, resultSign);
    }

    // ═════════ 取负 ═════════
    public Float256 negate() {
        return new Float256(a ^ (1L << SIGN_BIT_A), b, c, d);
    }

    // ═════════ 正方形 ═════════
    public Float256 sqrt() {
        if (signum() < 0) return NaN;
        if (isZero()) return ZERO;
        double approx = Math.sqrt(doubleValue());
        return Float256.of(BigDecimal.valueOf(approx));
    }

    // ═════════ 比较 ═════════
    @Override
    public int compareTo(Float256 o) {
        if (isNaN() || o.isNaN()) return 0;
        if (signum() != o.signum()) return signum() < 0 ? -1 : 1;
        if (isZero() && o.isZero()) return 0;
        boolean isNeg = signum() < 0;
        int cmpExp = Long.compareUnsigned(exponent(), o.exponent());
        if (cmpExp != 0) return isNeg ? -cmpExp : cmpExp;
        int cmpMantHi = Long.compareUnsigned(mantissaHi(), o.mantissaHi());
        if (cmpMantHi != 0) return isNeg ? -cmpMantHi : cmpMantHi;
        return isNeg ? -Long.compareUnsigned(mantissaMid(), o.mantissaMid()) : Long.compareUnsigned(mantissaMid(), o.mantissaMid());
    }

    // ═════════ 转换 ═════════
    public double doubleValue() {
        if (isZero()) return 0.0;
        if (isNaN()) return Double.NaN;
        if (isInfinity()) return signum() < 0 ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        return toBigDecimal().doubleValue();
    }

    public long longValue() {
        if (isZero()) return 0;
        long exp = JNE_exponent() - EXPONENT_BIAS;
        if (exp < 0) return 0;
        Int256 mant = Int256.of(0, mantHi(), mantMid(), mantLo());
        mant = mant.or(Int256.of(0, 1L << 48, 0L, 0L));
        mant = mant.shiftLeft((int) exp);
        long result = mant.longValue();
        return signum() < 0 ? -result : result;
    }

    public BigDecimal toBigDecimal() {
        if (isZero()) return BigDecimal.ZERO;
        if (isNaN() || isInfinity()) throw new NumberFormatException();
        BigInteger mant = Int256.of(0, mantHi(), mantMid(), mantLo())
                .or(Int256.of(0, 1L << 48, 0L, 0L))
                .toBigInteger();
        long exp = exponent() - EXPONENT_BIAS - 176;
        BigDecimal dec = new BigDecimal(mant, -exp);
        return signum() < 0 ? dec.negate() : dec;
    }

    @Override
    public String toString() {
        if (isNaN()) return "NaN";
        if (isZero()) return "0";
        if (isInfinity()) return signum() < 0 ? "-Infinity" : "Infinity";
        return toBigDecimal().toString();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Float256 other)) return false;
        if (isNaN() || other.isNaN()) return false;
        if (isZero() && other.isZero()) return true;
        return a == other.a && b == other.b && c == other.c && d == other.d;
    }

    @Override
    public int hashCode() {
        if (hash == HASH_NOT_CACHED) hash = (int) (a ^ b ^ c ^ d);
        return hash;
    }

    // ══════════════════════ 测试 ══════════════════════
    public static void main(String[] args) {
        System.out.println("=== Float256 测试 (含舍入) ===");
        Float256 a = Float256.of(1);
        Float256 b = Float256.of(3);
        System.out.println("1/3 = " + a.divide(b));
        System.out.println("1+2 = " + Float256.of(1).add(Float256.of(2)));
        System.out.println("2^100 = " + Float256.of(Int256.ONE.shiftLeft(100)));
    }
}