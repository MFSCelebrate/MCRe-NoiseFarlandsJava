package net.MinecraftTools.Math._256Bit;

import net.MinecraftTools.Math.DynamicAccuracy.BigInteger;
import net.MinecraftTools.Math.DynamicAccuracy.BigDecimal;
import net.MinecraftTools.Math.DynamicAccuracy.MathContext;

/**
 * Float256 — 有符号 256-bit 浮点数 (IEEE 754 风格)
 *
 * <p>布局: 1 符号 + 79 指数 + 176 尾数，偏置 2^78-1，RN(GRS) 向偶舍入
 * 范围: [-2^(2^77), 2^(2^77)] 精度: 176 bit ≈ 52 位十进制
 *
 * <p>位映射（连续）:
 * <pre>
 *   a = [sign:1][expHi:63]
 *   b = [expLo:16][mantHi:48]
 *   c = [mantMid:64]
 *   d = [mantLo:64]
 * </pre>
 * 指数 = (expHi &lt;&lt; 16) | expLo (79 bit)，79-bit 运算用 (expHi, expLo) 对手写，零 GC。
 *
 * <p>INF32768 / MCRe NoiseFarlands 项目
 */
public final class Float256 extends Number implements Comparable<Float256> {

    // ═══════════ 位布局常量 ═══════════
    private static final int EXP_BITS = 79;
    private static final int MANT_BITS = 176;
    private static final int MANT_IMPLIED = MANT_BITS + 1; // 177（含隐含位）

    private static final long SIGN_MASK = 0x8000_0000_0000_0000L;          // a 的 bit63
    private static final long EXP_HI_MASK = 0x7FFF_FFFF_FFFF_FFFFL;         // a 低 63 bit
    private static final long EXP_LO_MASK = 0xFFFF_0000_0000_0000L;         // b 高 16 bit
    private static final long MANT_HI_MASK = 0x0000_FFFF_FFFF_FFFFL;        // b 低 48 bit

    // 偏置 2^78-1 = 78 个 1 → (expHi=62 个 1, expLo=16 个 1)
    private static final long BIAS_HI = 0x3FFF_FFFF_FFFF_FFFFL;
    private static final long BIAS_LO = 0xFFFFL;
    // 指数全 1（79 个 1）= NaN/Inf 哨兵
    private static final long EXP_ALL_HI = 0x7FFF_FFFF_FFFF_FFFFL;
    private static final long EXP_ALL_LO = 0xFFFFL;

    // ──────── 内部存储 ────────
    final long a; // [sign:1][expHi:63]
    final long b; // [expLo:16][mantHi:48]
    final long c; // [mantMid:64]
    final long d; // [mantLo:64]

    // ──────── 缓存 ────────
    private transient int hash;
    private static final int HASH_NOT_CACHED = Integer.MIN_VALUE;
    private transient BigDecimal cachedBigDecimal;

    // ──────── 常量 ────────
    public static final Float256 ZERO = new Float256(0L, 0L, 0L, 0L);
    public static final Float256 ONE = make(BIAS_HI, BIAS_LO, 0L, 0L, 0L, 1);
    // 2 = 1.0 × 2^1，指数 = BIAS+1 = 2^78 → (expHi=2^62, expLo=0)
    public static final Float256 TWO = make(0x4000_0000_0000_0000L, 0L, 0L, 0L, 0L, 1);
    public static final Float256 THREE = of(3L);
    public static final Float256 TEN = of(10L);
    public static final Float256 MINUS_ONE = of(-1L);
    public static final Float256 NaN = make(EXP_ALL_HI, EXP_ALL_LO, 0L, 0L, 1L, 1);
    public static final Float256 POS_INF = make(EXP_ALL_HI, EXP_ALL_LO, 0L, 0L, 0L, 1);
    public static final Float256 NEG_INF = make(EXP_ALL_HI, EXP_ALL_LO, 0L, 0L, 0L, -1);

    // ──────── 构造 ────────
    private Float256(long a, long b, long c, long d) {
        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
        this.hash = HASH_NOT_CACHED;
    }

    /** 底层构造：raw 位模式（sign: ±1，expHi/expLo 为 79-bit 指数的拆对） */
    static Float256 make(long expHi, long expLo, long mantHi, long mantMid, long mantLo, int sign) {
        long a = (sign < 0 ? SIGN_MASK : 0L) | (expHi & EXP_HI_MASK);
        long b = ((expLo & 0xFFFF) << 48) | (mantHi & MANT_HI_MASK);
        return new Float256(a, b, mantMid, mantLo);
    }

    // ═══════════ 字段提取 ═══════════

    private long expHi() { return a & EXP_HI_MASK; }   // 63 bit
    private long expLo() { return b >>> 48; }           // 16 bit
    private long mantHi() { return b & MANT_HI_MASK; }  // 48 bit
    private long mantMid() { return c; }
    private long mantLo() { return d; }

    public int signum() {
        return (a & SIGN_MASK) != 0 ? -1 : 1;
    }

    /** 实际指数（有符号，64-bit 补码）：exp - BIAS，即 2 的幂次 */
    private long realExponent() {
        long dLo = expLo() - BIAS_LO;
        long borrow = (dLo < 0) ? 1L : 0L;
        long dHi = expHi() - BIAS_HI - borrow;
        return (dHi << 16) | (dLo & 0xFFFF);
    }

    private boolean expIsAll() {
        return expHi() == EXP_ALL_HI && expLo() == EXP_ALL_LO;
    }

    public boolean isZero() {
        return a == 0L && b == 0L && c == 0L && d == 0L;
    }

    public boolean isNaN() {
        return expIsAll() && (mantHi() != 0 || mantMid() != 0 || mantLo() != 0);
    }

    public boolean isInfinity() {
        return expIsAll() && mantHi() == 0 && mantMid() == 0 && mantLo() == 0;
    }

    public boolean isFinite() {
        return !isNaN() && !isInfinity();
    }

    // ═══════════ 79-bit 指数算术（零分配） ═══════════

    /** exp1 + exp2（79-bit 无符号加法，溢出自然进位到 long 高位） */
    private static long[] expAdd(long hi1, long lo1, long hi2, long lo2) {
        long lo = lo1 + lo2;
        long hi = hi1 + hi2 + (lo >>> 16);
        return new long[]{hi, lo & 0xFFFF};
    }

    /** exp1 - exp2（要求 exp1 >= exp2） */
    private static long[] expSub(long hi1, long lo1, long hi2, long lo2) {
        long lo = lo1 - lo2;
        long borrow = (lo < 0) ? 1L : 0L;
        long hi = hi1 - hi2 - borrow;
        return new long[]{hi, lo & 0xFFFF};
    }

    /** 79-bit 无符号比较：返回 -1/0/1 */
    private static int expCmp(long hi1, long lo1, long hi2, long lo2) {
        if (hi1 != hi2) return Long.compareUnsigned(hi1, hi2) < 0 ? -1 : 1;
        if (lo1 != lo2) return Long.compareUnsigned(lo1, lo2) < 0 ? -1 : 1;
        return 0;
    }

    /** 指数加 delta（有符号 long，模 2^64 借位自动传播），下溢→0，上溢→Inf */
    private Float256 scaleExp(long delta) {
        long lo = expLo() + (delta & 0xFFFF);
        long hi = expHi() + (delta >>> 16) + (lo >>> 16);
        lo &= 0xFFFF;
        if (hi < 0) return ZERO;
        if (hi > EXP_ALL_HI || (hi == EXP_ALL_HI && lo >= EXP_ALL_LO)) {
            return signum() < 0 ? NEG_INF : POS_INF;
        }
        return make(hi, lo, mantHi(), mantMid(), mantLo(), signum());
    }

    // ═══════════ 工厂方法 ═══════════

    /** 从 double（精确：IEEE 754 位模式转换，53-bit 尾数完整保留） */
    public static Float256 of(double value) {
        if (value == 0.0) return ZERO;
        if (Double.isNaN(value)) return NaN;
        if (Double.isInfinite(value)) return value > 0 ? POS_INF : NEG_INF;
        long bits = Double.doubleToRawLongBits(value);
        boolean neg = (bits >>> 63) != 0;
        int expBits = (int) ((bits >>> 52) & 0x7FF);
        long mantBits = bits & 0xFFFF_FFFF_FFFFFL;
        if (expBits == 0) {
            // 次正规: mantBits × 2^-1074
            if (mantBits == 0) return ZERO;
            Float256 f = of(Int256.of(mantBits));
            if (neg) f = f.negate();
            return f.scaleExp(-1074);
        }
        if (expBits == 0x7FF) {
            return mantBits == 0 ? (neg ? NEG_INF : POS_INF) : NaN;
        }
        // 正规: (2^52 | mantBits) × 2^(expBits - 1023 - 52)
        Int256 mant = Int256.of(0, 0, 0, mantBits).or(Int256.of(0, 0, 1L << 52, 0L));
        Float256 f = of(mant);
        if (neg) f = f.negate();
        return f.scaleExp(expBits - 1075);
    }

    /** 从 long（精确） */
    public static Float256 of(long value) {
        if (value == 0) return ZERO;
        return of(Int256.of(value));
    }

    /** 从 Int256（精确） */
    public static Float256 of(Int256 value) {
        if (value.isZero()) return ZERO;
        boolean neg = value.isNegative();
        Int256 abs = neg ? value.negate() : value;
        int bitLen = abs.bitLength();
        // 最高位移到 bit176（隐含位位置）
        Int256 mant = abs.shiftLeft(MANT_IMPLIED - bitLen);
        long expHi, expLo;
        if (bitLen == 1) {
            expHi = BIAS_HI;
            expLo = BIAS_LO;
        } else {
            expHi = 0x4000_0000_0000_0000L; // 2^62 = 2^78 >>> 16
            expLo = bitLen - 2;
        }
        long mantHi = mant.b & MANT_HI_MASK;
        return make(expHi, expLo, mantHi, mant.c, mant.d, neg ? -1 : 1);
    }

    /** 从 UInt256（精确） */
    public static Float256 of(UInt256 value) {
        if (value.isZero()) return ZERO;
        int bitLen = value.bitLength();
        UInt256 mant = value.shiftLeft(MANT_IMPLIED - bitLen);
        long expHi, expLo;
        if (bitLen == 1) {
            expHi = BIAS_HI;
            expLo = BIAS_LO;
        } else {
            expHi = 0x4000_0000_0000_0000L;
            expLo = bitLen - 2;
        }
        return make(expHi, expLo, mant.b & MANT_HI_MASK, mant.c, mant.d, 1);
    }

    /** 从 BigInteger（精确；超 256 bit 时截断高 256 bit 并补偿指数） */
    public static Float256 of(BigInteger value) {
        if (value.signum() == 0) return ZERO;
        boolean neg = value.signum() < 0;
        BigInteger abs = value.abs();
        int bitLen = abs.bitLength();
        if (bitLen <= 256) {
            Float256 f = of(Int256.of(abs));
            return neg ? f.negate() : f;
        }
        // 取高 256 bit，指数补偿 bitLen-256
        Int256 top = Int256.of(abs.shiftRight(bitLen - 256));
        Float256 f = of(top);
        if (neg) f = f.negate();
        return f.scaleExp(bitLen - 256);
    }

    /** 从 BigDecimal（scale 按 log2(10) 近似调整指数） */
    public static Float256 of(BigDecimal value) {
        if (value.signum() == 0) return ZERO;
        boolean neg = value.signum() < 0;
        Float256 f = of(value.unscaledValue().abs());
        if (neg) f = f.negate();
        int scale = value.scale();
        if (scale != 0) {
            f = f.scaleExp(-(long) (scale * 3.321928094887362)); // * log2(10)
        }
        return f;
    }

    // ═══════════ 舍入核心 (GRS + RN) ═══════════

    /** 将 mant（任意精度整数）舍入到 177 bit（含隐含位），RN 向偶舍入，打包为 Float256 */
    private static Float256 roundAndPack(long expHi, long expLo, Int256 mant, int sign) {
        if (mant.isZero()) return ZERO;

        int bitLen = mant.bitLength();
        int shift = bitLen - MANT_IMPLIED; // 需要右移的位数

        if (shift > 0) {
            // GRS 提取
            long G = mant.testBit(shift - 1) ? 1L : 0L;
            long R = shift >= 2 && mant.testBit(shift - 2) ? 1L : 0L;
            long S = (shift >= 2 && !mant.and(mant.maskBelow(shift - 2)).isZero()) ? 1L : 0L;
            mant = mant.shiftRight(shift);
            // 归一化：指数 + shift（79-bit）
            expLo += shift;
            expHi += expLo >>> 16;
            expLo &= 0xFFFF;
            // RN 向偶舍入
            boolean increment = G == 1 && (R == 1 || S == 1 || mant.lowBit() == 1);
            if (increment) {
                mant = mant.add(Int256.ONE);
                if (mant.bitLength() > MANT_IMPLIED) {
                    mant = mant.shiftRight(1);
                    // exp++
                    expLo++;
                    if (expLo > 0xFFFF) {
                        expLo = 0;
                        expHi++;
                    }
                }
            }
        } else if (shift < 0) {
            // 理论上不会发生（调用方保证归一化），防御性左移
            mant = mant.shiftLeft(-shift);
            expLo += shift; // shift 为负
            if (expLo < 0) {
                expHi--;
                expLo += 0x10000;
            }
            expLo &= 0xFFFF;
        }

        // 去掉隐含位：只保留低 176 bit
        mant = mant.and(mant.maskBelow(MANT_BITS));

        // 指数上溢 → Inf（exp 编码 ≥ 2^79-1）
        if (expHi > EXP_ALL_HI || (expHi == EXP_ALL_HI && expLo >= EXP_ALL_LO)) {
            return sign < 0 ? NEG_INF : POS_INF;
        }
        // exp 编码是 79-bit 无符号，任意值（含 exp < BIAS 的小数指数）都合法，无需次正规处理

        return make(expHi, expLo, mant.b & MANT_HI_MASK, mant.c, mant.d, sign);
    }

    // ═══════════ 加减法 ═══════════

    public Float256 add(Float256 o) {
        if (isNaN() || o.isNaN()) return NaN;
        if (isZero()) return o;
        if (o.isZero()) return this;
        if (isInfinity() || o.isInfinity()) {
            if (isInfinity() && o.isInfinity() && signum() != o.signum()) return NaN;
            return isInfinity() ? this : o;
        }

        long expHi1 = expHi(), expLo1 = expLo();
        long expHi2 = o.expHi(), expLo2 = o.expLo();
        int sign1 = signum();
        int sign2 = o.signum();

        Int256 M1 = mantissaWithImplied();
        Int256 M2 = o.mantissaWithImplied();

        // 对齐指数
        int cmp = expCmp(expHi1, expLo1, expHi2, expLo2);
        if (cmp > 0) {
            int diff = expDiff(expHi1, expLo1, expHi2, expLo2);
            M2 = diff > 300 ? Int256.ZERO : M2.shiftRight(diff);
        } else if (cmp < 0) {
            int diff = expDiff(expHi2, expLo2, expHi1, expLo1);
            M1 = diff > 300 ? Int256.ZERO : M1.shiftRight(diff);
            expHi1 = expHi2;
            expLo1 = expLo2;
        }

        Int256 resultMant;
        int resultSign;
        if (sign1 == sign2) {
            resultMant = M1.add(M2);
            resultSign = sign1;
        } else {
            int mc = M1.compareTo(M2);
            if (mc >= 0) {
                resultMant = M1.subtract(M2);
                resultSign = sign1;
            } else {
                resultMant = M2.subtract(M1);
                resultSign = sign2;
            }
        }

        return roundAndPack(expHi1, expLo1, resultMant, resultSign);
    }

    public Float256 subtract(Float256 o) {
        if (isNaN() || o.isNaN()) return NaN;
        return add(o.negate());
    }

    /** |exp1 - exp2|，截断为 int（>300 时调用方按 0 处理） */
    private static int expDiff(long hi1, long lo1, long hi2, long lo2) {
        if (hi1 != hi2) {
            // 高位差已远超 300
            return 301;
        }
        long dLo = lo1 - lo2;
        long borrow = (dLo < 0) ? 1L : 0L;
        long dHi = hi1 - hi2 - borrow;
        long diff = (dHi << 16) | (dLo & 0xFFFF); // 无符号 79-bit 差
        return diff > 300 ? 301 : (int) diff;
    }

    /** 尾数 + 隐含位（Int256，隐含位在 bit176） */
    private Int256 mantissaWithImplied() {
        return Int256.of(0L, mantHi(), mantMid(), mantLo())
                .or(Int256.of(0L, 1L << 48, 0L, 0L));
    }

    // ═════════ 乘法 ═════════
    public Float256 multiply(Float256 o) {
        if (isNaN() || o.isNaN()) return NaN;
        if (isZero() || o.isZero()) return ZERO;
        if (isInfinity() || o.isInfinity()) {
            if (isZero() || o.isZero()) return NaN;
            return (signum() == o.signum()) ? POS_INF : NEG_INF;
        }

        // E_in = E1 + E2 - BIAS（80-bit 无符号运算，roundAndPack 自动补偿归一化 shift）
        long tLo = expLo() + o.expLo();
        long tHi = expHi() + o.expHi() + (tLo >>> 16); // 64-bit 无符号（可含 79-bit 溢出位）
        tLo &= 0xFFFF;
        // T < BIAS → 下溢（极小值归零）
        if (Long.compareUnsigned(tHi, BIAS_HI) < 0) return ZERO;
        if (tHi == BIAS_HI && Long.compareUnsigned(tLo, BIAS_LO) < 0) return ZERO;
        long eLo = tLo - BIAS_LO;
        long borrow = (eLo < 0) ? 1L : 0L;
        eLo &= 0xFFFF;
        long eHi = tHi - BIAS_HI - borrow; // 无符号（≥0）
        if (eHi < 0) { // 无符号 ≥ 2^63 → E ≥ 2^79 → 上溢
            return (signum() != o.signum()) ? NEG_INF : POS_INF;
        }

        int resultSign = signum() * o.signum();

        Int256 M1 = mantissaWithImplied();
        Int256 M2 = o.mantissaWithImplied();
        Int256 product = M1.multiply(M2); // 176×176+隐含 → 352 bit

        return roundAndPack(eHi, eLo, product, resultSign);
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

        // E_in = E1 - E2 + BIAS - 1（分 E1≥E2 / E1<E2 两路，防 64-bit 溢出误判）
        int resultSign = signum() * o.signum();
        long eHi, eLo;
        if (expCmp(expHi(), expLo(), o.expHi(), o.expLo()) >= 0) {
            // E1 >= E2：E = (E1-E2) + BIAS - 1，只可能上溢
            long dLo = expLo() - o.expLo();
            long borrow = (dLo < 0) ? 1L : 0L;
            dLo &= 0xFFFF;
            long dHi = expHi() - o.expHi() - borrow; // 无符号差
            eLo = dLo + BIAS_LO - 1;
            long carry = eLo >>> 16;
            eLo &= 0xFFFF;
            eHi = dHi + BIAS_HI + carry;
            if (Long.compareUnsigned(eHi, EXP_ALL_HI) > 0) { // E ≥ 2^79 → 上溢
                return resultSign < 0 ? NEG_INF : POS_INF;
            }
        } else {
            // E1 < E2：E = BIAS - 1 - (E2-E1)，只可能下溢
            long dLo = o.expLo() - expLo();
            long borrow = (dLo < 0) ? 1L : 0L;
            dLo &= 0xFFFF;
            long dHi = o.expHi() - expHi() - borrow; // 无符号差
            // 下溢: E2-E1 > BIAS-1 = 2^78-2
            if (Long.compareUnsigned(dHi, BIAS_HI) > 0) return ZERO;
            if (dHi == BIAS_HI && Long.compareUnsigned(dLo, BIAS_LO - 1) > 0) return ZERO;
            eLo = BIAS_LO - 1 - dLo;
            long borrow2 = (eLo < 0) ? 1L : 0L;
            eLo &= 0xFFFF;
            eHi = BIAS_HI - dHi - borrow2;
        }

        Int256 M1 = mantissaWithImplied();
        Int256 M2 = o.mantissaWithImplied();
        // 商精度：M1 左移 177 位保证商有 176+1 bit 精度
        M1 = M1.shiftLeft(MANT_IMPLIED);
        Int256 quot = M1.divide(M2);

        return roundAndPack(eHi, eLo, quot, resultSign);
    }

    // ═════════ 取负 / 绝对值 ═════════
    public Float256 negate() {
        if (isZero()) return ZERO;
        if (isNaN()) return NaN;
        return new Float256(a ^ SIGN_MASK, b, c, d);
    }

    public Float256 abs() {
        if (isZero()) return ZERO;
        if (isNaN()) return NaN;
        return new Float256(a & ~SIGN_MASK, b, c, d);
    }

    /** 直接位转换：尾数 176→192 bit（左移 16 扩充）+ 指数 re-bias（-2^78+2^63） */
    public UFloat256 toUFloat256() {
        if (isZero()) return UFloat256.ZERO;
        if (isNaN()) return UFloat256.NaN;
        if (isInfinity()) return signum() < 0 ? UFloat256.NaN : UFloat256.INF;
        if (signum() < 0) throw new IllegalArgumentException("UFloat256 cannot be negative");
        // 指数 re-bias: e_uf = e_f - (2^78 - 2^63)，2^78-2^63 = 0x3FFF_8000_0000_0000_0000
        long hi = expHi() - 0x3FFF_8000_0000_0000L; // 常数高 63 位
        if (hi < 0) return UFloat256.ZERO;           // 指数过小 → 0
        if (hi > 0xFFFF_FFFF_FFFFL) return UFloat256.INF; // e_uf ≥ 2^64
        long euf = (hi << 16) | expLo();             // 64-bit 无符号
        // 尾数 176 → 192：左移 16 bit 零损失扩充
        long mHi = (mantHi() << 16) | (mantMid() >>> 48);
        long mMid = (mantMid() << 16) | (mantLo() >>> 48);
        long mLo = mantLo() << 16;
        return UFloat256.make(euf, mHi, mMid, mLo);
    }

    // ═════════ 平方根 ═════════
    public Float256 sqrt() {
        if (signum() < 0) return NaN;
        if (isZero()) return ZERO;
        if (isNaN()) return NaN;
        if (isInfinity()) return POS_INF;
        // double 近似起步（对坐标运算足够）
        return of(Math.sqrt(doubleValue()));
    }

    // ═════════ 比较 ═════════
    @Override
    public int compareTo(Float256 o) {
        if (isNaN() || o.isNaN()) return 0;
        if (isZero() && o.isZero()) return 0;
        if (isInfinity() && o.isInfinity()) return signum() < 0 ? (o.signum() < 0 ? 0 : -1) : (o.signum() < 0 ? 1 : 0);
        if (isInfinity()) return signum() < 0 ? -1 : 1;
        if (o.isInfinity()) return o.signum() < 0 ? 1 : -1;
        if (signum() != o.signum()) return signum() < 0 ? -1 : 1;
        boolean isNeg = signum() < 0;
        int cmp = expCmp(expHi(), expLo(), o.expHi(), o.expLo());
        if (cmp != 0) return isNeg ? -cmp : cmp;
        cmp = Long.compareUnsigned(mantHi(), o.mantHi());
        if (cmp != 0) return isNeg ? -cmp : cmp;
        cmp = Long.compareUnsigned(mantMid(), o.mantMid());
        if (cmp != 0) return isNeg ? -cmp : cmp;
        cmp = Long.compareUnsigned(mantLo(), o.mantLo());
        return isNeg ? -cmp : cmp;
    }

    // ═════════ 转换 ═════════
    @Override
    public double doubleValue() {
        if (isZero()) return 0.0;
        if (isNaN()) return Double.NaN;
        if (isInfinity()) return signum() < 0 ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        return toBigDecimal().doubleValue();
    }

    @Override
    public long longValue() {
        if (isZero()) return 0;
        if (isNaN() || isInfinity()) throw new ArithmeticException("not finite");
        long realExp = realExponent();
        if (realExp < 0) return 0;
        if (realExp > 64) throw new ArithmeticException("Float256 out of long range");
        Int256 mant = mantissaWithImplied();
        Int256 shifted = mant.shiftLeft((int) realExp);
        long result = shifted.longValue();
        return signum() < 0 ? -result : result;
    }

    @Override
    public int intValue() {
        return (int) longValue();
    }

    @Override
    public float floatValue() {
        return (float) doubleValue();
    }

    public BigDecimal toBigDecimal() {
        if (isZero()) return BigDecimal.ZERO;
        if (isNaN() || isInfinity()) throw new ArithmeticException("not finite");
        BigInteger mant = mantissaWithImplied().toBigInteger();
        long realExp = realExponent();
        BigDecimal dec = new BigDecimal(mant, 0);
        if (realExp > 0) {
            if (realExp < 1024) {
                dec = dec.multiply(BigDecimal.valueOf(Math.pow(2, realExp)));
            } else {
                dec = dec.scaleByPowerOfTen((int) (realExp * 0.3010299956639812));
            }
        } else if (realExp < 0) {
            if (realExp > -1024) {
                dec = dec.divide(BigDecimal.valueOf(Math.pow(2, -realExp)), MathContext.DECIMAL128);
            } else {
                dec = dec.scaleByPowerOfTen((int) (realExp * 0.3010299956639812));
            }
        }
        return signum() < 0 ? dec.negate() : dec;
    }

    // ═══════════ 精确取整（返回 Int256，零损失） ═══════════

    /** 向零截断 */
    public Int256 truncate() {
        if (isZero()) return Int256.ZERO;
        if (isNaN() || isInfinity()) throw new ArithmeticException("not finite");
        Int256 v = truncateAbs();
        return signum() < 0 ? v.negate() : v;
    }

    /** 向下取整（-∞ 方向） */
    public Int256 floor() {
        if (isZero()) return Int256.ZERO;
        if (isNaN() || isInfinity()) throw new ArithmeticException("not finite");
        long realExp = realExponent();
        if (realExp >= 0) return truncate();
        Int256 mant = mantissaWithImplied();
        Int256 abs = mant.shiftRight((int) -realExp);
        boolean dropped = !mant.and(mant.maskBelow((int) -realExp)).isZero();
        if (dropped && signum() < 0) abs = abs.add(Int256.ONE);
        return signum() < 0 ? abs.negate() : abs;
    }

    /** 向上取整（+∞ 方向） */
    public Int256 ceil() {
        if (isZero()) return Int256.ZERO;
        if (isNaN() || isInfinity()) throw new ArithmeticException("not finite");
        long realExp = realExponent();
        if (realExp >= 0) return truncate();
        Int256 mant = mantissaWithImplied();
        Int256 abs = mant.shiftRight((int) -realExp);
        boolean dropped = !mant.and(mant.maskBelow((int) -realExp)).isZero();
        if (dropped && signum() > 0) abs = abs.add(Int256.ONE);
        return signum() < 0 ? abs.negate() : abs;
    }

    /** 四舍五入（half-up，与 Math.round 一致） */
    public Int256 round() {
        return this.add(Float256.of(0.5)).floor();
    }

    /** 绝对值截断（内部用） */
    private Int256 truncateAbs() {
        long realExp = realExponent();
        Int256 mant = mantissaWithImplied();
        if (realExp >= 0) {
            if (realExp > 255) throw new ArithmeticException("Float256 too large for Int256");
            return mant.shiftLeft((int) realExp);
        }
        if (realExp < -255) return Int256.ZERO; // 极小值 → 0
        return mant.shiftRight((int) -realExp);
    }

    /**
     * 精确十进制展开：值 = mant × 2^e → mant × 5^(-e) / 10^(-e)
     * 完整保留 53-bit double 尾数（0.1 → 0.1000000000000000055511151231257827021181583404541015625）
     * 用于调试屏幕坐标显示，尽量减少精度损失
     */
    public String toExactString() {
        if (isZero()) return "0";
        if (isNaN()) return "NaN";
        if (isInfinity()) return signum() < 0 ? "-Infinity" : "Infinity";
        BigInteger m = mantissaWithImplied().toBigInteger();
        long e = realExponent() - MANT_BITS; // 值 = m × 2^e
        StringBuilder sb = new StringBuilder(32);
        if (signum() < 0) sb.append('-');
        if (e >= 0) {
            if (e > 1L << 30) return toBigDecimal().toString(); // 超大指数走近似
            sb.append(m.shiftLeft((int) e));
            return sb.toString();
        }
        long negE = -e;
        if (negE > 1L << 30) return toBigDecimal().toString(); // 超小指数走近似
        // m × 5^k / 10^k
        BigInteger scaled = m.multiply(BigInteger.valueOf(5).pow((int) negE));
        String s = scaled.toString();
        int shift = (int) negE;
        if (s.length() <= shift) {
            sb.append("0.");
            for (int i = 0; i < shift - s.length(); i++) sb.append('0');
            sb.append(s);
        } else {
            sb.append(s, 0, s.length() - shift).append('.').append(s, s.length() - shift, s.length());
        }
        // 去掉小数尾随 0
        int len = sb.length();
        while (len > 0 && sb.charAt(len - 1) == '0') len--;
        if (len > 0 && sb.charAt(len - 1) == '.') len--;
        sb.setLength(len);
        return sb.toString();
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
        System.out.println("1   = " + ONE);
        System.out.println("2   = " + TWO);
        System.out.println("1+2 = " + ONE.add(TWO));
        System.out.println("1/3 = " + ONE.divide(of(3)));
        System.out.println("1/3*3 = " + ONE.divide(of(3)).multiply(of(3)));
        System.out.println("2^100 = " + of(Int256.ONE.shiftLeft(100)));
        System.out.println("0.1+0.2 = " + of(0.1).add(of(0.2)));
        System.out.println("0.1 exact = " + of(0.1).toExactString());
        System.out.println("1.5 exact = " + of(1.5).toExactString());
        System.out.println("12550821.123456789 exact = " + of(12550821.123456789).toExactString());
        System.out.println("sqrt(2) = " + of(2).sqrt());
        System.out.println("Long.MAX = " + of(Long.MAX_VALUE));
        System.out.println("Long.MAX+1 = " + of(Long.MAX_VALUE).add(ONE));
        System.out.println("(-3).abs() = " + of(-3).abs());
        System.out.println("NaN = " + NaN);
        System.out.println("1/0 = " + ONE.divide(ZERO));
        System.out.println("0/0 = " + ZERO.divide(ZERO));
    }
}
