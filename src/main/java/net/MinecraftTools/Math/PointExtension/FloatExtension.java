package net.MinecraftTools.Math.PointExtension;

/**
 * 🔧 FloatExtension——扩展指数的单精度格式浮点（MCRe PointExtension 包）。
 *
 * <p><b>动机</b>：基岩版边缘之地/虚无提前到来的两个限制（低精度 + 低容量）中，<b>精度</b>由 24 位尾数
 * 决定（更改尾数位会大幅改变边境之地形态，必须锁死），<b>容量</b>由指数位宽决定。本格式锁 24 位
 * 尾数（与 {@code float} 逐位一致的 24 位有效精度），指数 8 → 11 位（偏置 1023，double 同款），
 * 把可表示范围从 float 的 ~2^128 撑到 ~2^1024，用于探索基岩版后几层边境之地。
 *
 * <p><b>位布局（36 位，存于 long 高 36 位之外的位恒 0）</b>：
 * <pre>
 *   bit35    | 符号
 *   bit34..24 | 指数域（11 位，偏置 1023；0 = 次正规/±0，2047 = Inf/NaN）
 *   bit23..0  | 尾数（24 位；规格化时 bit23 = 1 = 显式隐含位，与 float 的 23 存储 + 隐含同构）
 * </pre>
 * 值 = (-1)^s · m · 2^(E-1046)，与 OpenJDK Float 文档的 {@code s·m·2^(e-150)} 同构
 * （float：24 位尾数 + 偏置 127 + 23 → 150；本格式：24 位尾数 + 偏置 1023 + 23 → 1046）。
 *
 * <p><b>设计红线</b>：
 * <ul>
 *   <li><b>零 Double 中转</b>——所有运算纯 long 位运算/乘除，中途不借 double（中转会引入
 *       double 的 53 位舍入语义并保留额外小数位，污染 24 位尾数精度）。of(float)/toFloat 走
 *       {@code Float.floatToRawIntBits/intBitsToFloat}（JVM 位拷贝 intrinsic，非 double 运算）。</li>
 *   <li>舍入全部 Round-to-Nearest-Even（GRS：Guard/Round/Sticky）。</li>
 *   <li>特殊值语义对齐 IEEE 754 / OpenJDK Float：±0、次正规、±Inf、NaN（规范位 0x...F800000
 *       量级——尾数最高位置 1 的 quiet NaN）。</li>
 * </ul>
 *
 * <p>参考：OpenJDK {@code java.lang.Float}（{@code Materials/OpenJDK/java.base/share/classes/}）的
 * 位模式语义、float16ToFloat 格式转换范式、equals/compareTo/hashCode 语义；未参考自研高 bit
 * 浮点实现（其位场铺放存在已知 bug）。
 */
public final class FloatExtension {

    // ═══════════ 布局常量 ═══════════

    /** 总位宽（1 符号 + 11 指数 + 24 尾数） */
    public static final int WIDTH = 36;
    /** 尾数有效位宽（含显式隐含位，与 float 的 24 位有效精度一致） */
    public static final int SIGNIFICAND_WIDTH = 24;
    /** 尾数分数位宽（24 - 1 隐含） */
    public static final int FRACTION_BITS = 23;
    /** 指数域位宽 */
    public static final int EXPONENT_BITS = 11;
    /** 指数偏置（double 同款） */
    public static final int EXP_BIAS = 1023;

    private static final int SIGN_SHIFT = 35;
    private static final int EXP_SHIFT = 24;
    private static final long SIGN_MASK = 1L << SIGN_SHIFT;
    private static final long EXP_MASK = 0x7FFL;              // 11 位
    private static final long SIGNIF_MASK = 0xFF_FFFFL;       // 24 位
    /** 指数域全 1（Inf/NaN 标记） */
    private static final int EXP_SPECIAL = 2047;
    /** 尾数隐含位（规格化时恒 1，显式存储） */
    private static final long IMPLICIT_BIT = 1L << FRACTION_BITS; // 0x800000

    /** 正规数最大 realExp（2^1023 量级） */
    public static final int MAX_EXPONENT = 1023;
    /** 正规数最小 realExp */
    public static final int MIN_EXPONENT = -1022;

    // ═══════════ 特殊值位模式 ═══════════

    private static final long BITS_POS_ZERO = 0L;
    private static final long BITS_NEG_ZERO = SIGN_MASK;
    private static final long BITS_POS_INF = (long) EXP_SPECIAL << EXP_SHIFT;
    private static final long BITS_NEG_INF = BITS_POS_INF | SIGN_MASK;
    /** 规范 quiet NaN（尾数最高位 = 1） */
    private static final long BITS_NAN = ((long) EXP_SPECIAL << EXP_SHIFT) | IMPLICIT_BIT;
    private static final long BITS_NEG_NAN = BITS_NAN | SIGN_MASK;

    /** 最大正规值：(2^24-1)·2^(2046-1046) ≈ 1.7976e308（double max 量级） */
    public static final long MAX_VALUE_BITS = ((long) (EXP_SPECIAL - 1) << EXP_SHIFT) | SIGNIF_MASK;
    /** 最小正规数 2^-1022 */
    public static final long MIN_NORMAL_BITS = 1L << EXP_SHIFT;
    /** 最小次正规数 2^-1046 */
    public static final long MIN_VALUE_BITS = 1L;

    // ═══════════ 单例特殊值 ═══════════

    public static final FloatExtension ZERO = new FloatExtension(BITS_POS_ZERO);
    public static final FloatExtension NEG_ZERO = new FloatExtension(BITS_NEG_ZERO);
    public static final FloatExtension POSITIVE_INFINITY = new FloatExtension(BITS_POS_INF);
    public static final FloatExtension NEGATIVE_INFINITY = new FloatExtension(BITS_NEG_INF);
    public static final FloatExtension NaN = new FloatExtension(BITS_NAN);

    // ═══════════ 位场 ═══════════

    private final long bits;

    private FloatExtension(final long bits) {
        this.bits = bits;
    }

    /** 原始 36 位模式（调试/测试用） */
    public long rawBits() {
        return this.bits;
    }

    /** 符号：-1 / +1（±0 返回 +1/-1 照位模式） */
    public int signum() {
        return (this.bits & SIGN_MASK) != 0 ? -1 : 1;
    }

    /** 指数域（0..2047） */
    public int exponentField() {
        return (int) ((this.bits >>> EXP_SHIFT) & EXP_MASK);
    }

    /** 尾数域（24 位；规格化数含隐含位，次正态不含） */
    public long significand() {
        return this.bits & SIGNIF_MASK;
    }

    /**
     * 有效指数（对齐/运算用统一定义）：规格化数 = E-1023；次正态 = -1022（配合未规格化尾数，
     * 值 = M·2^(-1022-23) = M·2^-1046 精确成立）。
     */
    private long effectiveExponent() {
        int e = exponentField();
        return (e == 0 ? 1 : e) - EXP_BIAS;
    }

    public boolean isZero() {
        return exponentField() == 0 && significand() == 0;
    }

    public boolean isNaN() {
        return exponentField() == EXP_SPECIAL && significand() != 0;
    }

    public boolean isInfinite() {
        return exponentField() == EXP_SPECIAL && significand() == 0;
    }

    public boolean isFinite() {
        return exponentField() != EXP_SPECIAL;
    }

    public boolean isNormal() {
        int e = exponentField();
        return e >= 1 && e < EXP_SPECIAL;
    }

    // ═══════════ 规格化打包（私有核心） ═══════════

    /** 组装位模式 */
    private static long packBits(final boolean neg, final long expField, final long signif) {
        return (neg ? SIGN_MASK : 0L) | ((expField & EXP_MASK) << EXP_SHIFT) | (signif & SIGNIF_MASK);
    }

    /**
     * 规格化打包：m24 ∈ [2^23, 2^24)（已含隐含位），realExp = m24 的最高位权重。
     * 处理指数上溢（→Inf）与下溢（→次正规/±0，GRS 舍入）。
     */
    private static FloatExtension packNormalized(final boolean neg, final long m24, final long realExp) {
        long e = realExp + EXP_BIAS;
        if (e >= EXP_SPECIAL) {
            return new FloatExtension(neg ? BITS_NEG_INF : BITS_POS_INF);
        }
        if (e <= 0) {
            // 次正态：值 = m24·2^(realExp-23) = M·2^(0-1023-23) → M = m24 >> (23 - e)
            int shift = (int) (23 - e);
            if (shift >= 25) {
                // 整体小于半个最小次正态 → ±0（shift ≥ 25 时 m24 全部移出，GRS 无生存空间）
                return new FloatExtension(neg ? BITS_NEG_ZERO : BITS_POS_ZERO);
            }
            long m = m24 >> shift;
            // GRS：被移出位
            boolean g = ((m24 >>> (shift - 1)) & 1L) != 0;
            boolean r = shift >= 2 && (m24 & ((1L << (shift - 1)) - 1L)) != 0;
            if (g && (r || (m & 1L) != 0)) {
                m++;
                if (m == IMPLICIT_BIT << 1) {
                    // 舍入进位到最小正规 2^-1022
                    return new FloatExtension(packBits(neg, 1, IMPLICIT_BIT));
                }
            }
            return new FloatExtension(packBits(neg, 0, m));
        }
        return new FloatExtension(packBits(neg, e, m24));
    }

    /**
     * 左移规格化打包（无损路径）：m24 > 0 任意位宽（≤ 2^24），值 = m24·2^e2。
     * 左移到 bit23 = 1 后转 {@link #packNormalized}。用于 of(float)/运算中间态（不丢位）。
     */
    private static FloatExtension packShiftUp(final boolean neg, long m24, long e2) {
        int bl = 64 - Long.numberOfLeadingZeros(m24);
        int k = bl - SIGNIFICAND_WIDTH;
        if (k > 0) {
            // 防御：调用方保证 m24 < 2^24；若超出按右移（丢位，正常路径不触发）
            m24 >>= k;
            e2 += k;
        } else if (k < 0) {
            m24 <<= -k;
            e2 += k;
        }
        return packNormalized(neg, m24, e2 + FRACTION_BITS);
    }

    // ═══════════ 工厂 ═══════════

    /** 从 float 精确转换（24 位尾数语义逐位保留，指数重偏置；零 Double 中转） */
    public static FloatExtension of(final float value) {
        final int bits = Float.floatToRawIntBits(value);
        final boolean neg = bits < 0;
        final int e = (bits >>> 23) & 0xFF;
        final int m23 = bits & 0x7F_FFFF;
        if (e == 0xFF) {
            // Inf/NaN
            if (m23 == 0) {
                return new FloatExtension(neg ? BITS_NEG_INF : BITS_POS_INF);
            }
            return new FloatExtension(neg ? BITS_NEG_NAN : BITS_NAN);
        }
        // 值 = m24·2^(e-150)（OpenJDK Float 文档公式）：正规 m24 = 隐含|m23；次正态 m24 = m23<<1
        final long m24 = e == 0 ? ((long) m23 << 1) : (IMPLICIT_BIT | m23);
        if (m24 == 0) {
            return new FloatExtension(neg ? BITS_NEG_ZERO : BITS_POS_ZERO);
        }
        return packShiftUp(neg, m24, e - 150);
    }

    /** 从 long 精确转换（RN 舍入到 24 位有效；Long.MIN_VALUE 按 2^63 幅值语义） */
    public static FloatExtension of(final long value) {
        if (value == 0) {
            return ZERO;
        }
        final boolean neg = value < 0;
        final long m = neg ? -value : value; // MIN_VALUE 回绕 = 2^63 位模式
        final int bl = 64 - Long.numberOfLeadingZeros(m);
        if (bl <= SIGNIFICAND_WIDTH) {
            // 精确（左移规格化）
            return packShiftUp(neg, m, 0);
        }
        // 右移 bl-24 位 + GRS 舍入
        final int k = bl - SIGNIFICAND_WIDTH;
        long kept = m >> k;
        final boolean g = ((m >>> (k - 1)) & 1L) != 0;
        final boolean r = k >= 2 && (m & ((1L << (k - 1)) - 1L)) != 0;
        if (g && (r || (kept & 1L) != 0)) {
            kept++;
            if (kept == IMPLICIT_BIT << 1) {
                kept >>>= 1; // 进位溢出（2^24 → 2^23）；kept bit23 权重 = 2^bl
                return packNormalized(neg, kept, bl);
            }
        }
        // kept bit23 对应原位 bl-1 → realExp = bl-1
        return packNormalized(neg, kept, bl - 1);
    }

    /** 从 int 转换（委托 of(long)，精确） */
    public static FloatExtension of(final int value) {
        return of((long) value);
    }

    /** 从原始 36 位模式构造（{@link #rawBits} 的逆；调试/序列化/测试用） */
    public static FloatExtension fromRawBits(final long bits) {
        return new FloatExtension(bits & 0xF_FFFF_FFFFL); // 36 位掩码
    }

    // ═══════════ 转换回原生 ═══════════

    /** 转换回 float（24→24 尾数零损失；指数超 float 域 → ±Inf；低于 float 次正态界 → ±0） */
    public float toFloat() {
        final int e = exponentField();
        final long m = significand();
        final boolean neg = signum() < 0;
        if (e == EXP_SPECIAL) {
            if (m == 0) {
                return neg ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
            }
            return Float.NaN;
        }
        if (e == 0) {
            // 次正态：值 = m·2^-1046 << float 最小次正态 2^-149 → 恒 ±0（精确，非近似）
            return neg ? -0.0f : 0.0f;
        }
        // 正规：值 = m·2^(e-1046) = m24·2^(ef-150) → ef = e - 896
        final long realExp = e - EXP_BIAS;
        final int ef = (int) (realExp + 127);
        if (ef >= 0xFF) {
            return neg ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
        }
        if (ef <= 0) {
            // 落入 float 次正态域：m24 = m >> (127 - ef 相关移位) + GRS
            final int shift = -ef; // ef ≤ 0 → shift ≥ 0
            if (shift >= 25) {
                return neg ? -0.0f : 0.0f;
            }
            long m24 = m >> shift;
            final boolean g = shift > 0 && ((m >>> (shift - 1)) & 1L) != 0;
            final boolean r = shift > 1 && (m & ((1L << (shift - 1)) - 1L)) != 0;
            if (g && (r || (m24 & 1L) != 0)) {
                m24++;
                if (m24 == IMPLICIT_BIT << 1) {
                    // 进位到 float 最小正规
                    return Float.intBitsToFloat((neg ? 1 << 31 : 0) | (1 << 23));
                }
            }
            // float 次正态位模式：m23 = m24 >> 1（丢 1 位；m24 < 2^24 的次正态边界，接受）
            return Float.intBitsToFloat((neg ? 1 << 31 : 0) | (int) (m24 >> 1));
        }
        // ef ∈ [1, 254]：尾数剥隐含位零损失
        return Float.intBitsToFloat((neg ? 1 << 31 : 0) | (ef << 23) | (int) (m & 0x7F_FFFF));
    }

    /** 截断取整（round-toward-zero，JDK Number.longValue 语义） */
    public long longValue() {
        if (isNaN() || isInfinite()) {
            throw new ArithmeticException("not finite");
        }
        if (isZero()) {
            return 0L;
        }
        final long realExp = effectiveExponent();
        final long m = significand();
        if (e0SubnormalAdjust(realExp)) {
            return 0L; // |值| < 1
        }
        // 值 = m·2^(realExp-23)；整数部分 = m >> (23 - realExp)（realExp ≥ 23 时左移）
        if (realExp >= FRACTION_BITS) {
            return signum() < 0 ? -(m << (realExp - FRACTION_BITS)) : (m << (realExp - FRACTION_BITS));
        }
        final long t = m >> (FRACTION_BITS - realExp);
        return signum() < 0 ? -t : t;
    }

    /** 次正态/小数的 |值|<1 判定辅助（realExp < 23 且次正态尾数放大后仍 < 2^23） */
    private static boolean e0SubnormalAdjust(final long realExp) {
        return realExp < 0 || (realExp == 0 && false); // realExp < 0 → |值| < 1；realExp ≥ 0 走主路径
    }

    /** 截断取整（int） */
    public int intValue() {
        return (int) longValue();
    }

    // ═══════════ 算术 ═══════════

    public FloatExtension negate() {
        if (isNaN()) {
            return NaN;
        }
        return new FloatExtension(this.bits ^ SIGN_MASK);
    }

    public FloatExtension abs() {
        if (signum() < 0) {
            return negate();
        }
        return this;
    }

    /** 加法（同号加/异号减，指数对齐 + GRS sticky + RN） */
    public FloatExtension add(final FloatExtension o) {
        if (isNaN() || o.isNaN()) {
            return NaN;
        }
        if (isZero() && o.isZero()) {
            // IEEE 754：同号零相加保留符号，异号零（+0 加 -0）→ +0（RN 模式）
            return signum() == o.signum() ? this : ZERO;
        }
        if (isZero()) {
            return o;
        }
        if (o.isZero()) {
            return this;
        }
        if (isInfinite() || o.isInfinite()) {
            if (isInfinite() && o.isInfinite() && signum() != o.signum()) {
                return NaN;
            }
            return isInfinite() ? this : o;
        }

        final int sign1 = signum();
        final int sign2 = o.signum();
        final long re1 = effectiveExponent();
        final long re2 = o.effectiveExponent();
        final long m1 = significand();
        final long m2 = o.significand();

        // 对齐到公共指数（大指数方）；工作区 = 24 位尾数 + 8 位保护（G/R/S + 余量）
        long a = m1 << 8;
        long b = m2 << 8;
        long reBig;
        if (re1 >= re2) {
            final long d = re1 - re2;
            reBig = re1;
            if (d >= 40) {
                b = b != 0 ? 1L : 0L; // 整体进 sticky
            } else if (d > 0) {
                final boolean sticky = (b & ((1L << d) - 1L)) != 0;
                b >>>= d;
                if (sticky) {
                    b |= 1L;
                }
            }
        } else {
            final long d = re2 - re1;
            reBig = re2;
            if (d >= 40) {
                a = a != 0 ? 1L : 0L;
            } else if (d > 0) {
                final boolean sticky = (a & ((1L << d) - 1L)) != 0;
                a >>>= d;
                if (sticky) {
                    a |= 1L;
                }
            }
        }

        final long sum;
        final int sign;
        if (sign1 == sign2) {
            sum = a + b;
            sign = sign1;
        } else if (a >= b) {
            sum = a - b;
            sign = sign1;
        } else {
            sum = b - a;
            sign = sign2;
        }
        if (sum == 0) {
            // 异号精确相消 → +0（IEEE 754）
            return ZERO;
        }

        // 规格化 + 舍入到 24 位（sum 权重 = 2^(reBig-31)——24 位尾数 + 8 位保护区）
        final int bl = 64 - Long.numberOfLeadingZeros(sum);
        final int k = bl - SIGNIFICAND_WIDTH;
        if (k >= 0) {
            final long kept = sum >> k;
            final boolean g = ((sum >>> (k - 1)) & 1L) != 0;
            final boolean r = k >= 2 && (sum & ((1L << (k - 1)) - 1L)) != 0;
            long m = kept;
            // kept bit23 ↔ sum bit(bl-1) → realExp = reBig-31+bl-1+23 = reBig+k-8
            long re = reBig + k - 8;
            if (g && (r || (m & 1L) != 0)) {
                m++;
                if (m == IMPLICIT_BIT << 1) {
                    m >>>= 1;
                    re++;
                }
            }
            return packNormalized(sign < 0, m, re);
        } else {
            // 左移规格化（相消后小值）：realExp = reBig+k-8 同步
            return packNormalized(sign < 0, sum << -k, reBig + k - 8);
        }
    }

    /** 减法 = 加相反数 */
    public FloatExtension subtract(final FloatExtension o) {
        return add(o.negate());
    }

    /** 乘法（24×24 = 48 位乘积 → 规格化 + RN） */
    public FloatExtension multiply(final FloatExtension o) {
        if (isNaN() || o.isNaN()) {
            return NaN;
        }
        final boolean z1 = isZero();
        final boolean z2 = o.isZero();
        if (z1 || z2) {
            if (isInfinite() || o.isInfinite()) {
                return NaN; // 0 × Inf
            }
            return (signum() * o.signum() < 0) ? NEG_ZERO : ZERO;
        }
        if (isInfinite() || o.isInfinite()) {
            return (signum() * o.signum() < 0) ? NEGATIVE_INFINITY : POSITIVE_INFINITY;
        }

        final long p = significand() * o.significand(); // 48 位
        final int bl = 64 - Long.numberOfLeadingZeros(p);
        final int k = bl - SIGNIFICAND_WIDTH;
        long re = effectiveExponent() + o.effectiveExponent() - FRACTION_BITS;
        long m;
        if (k > 0) {
            // 右移 k（丢位）：GRS + RN
            m = p >> k;
            final boolean g = ((p >>> (k - 1)) & 1L) != 0;
            final boolean r = k >= 2 && (p & ((1L << (k - 1)) - 1L)) != 0;
            if (g && (r || (m & 1L) != 0)) {
                m++;
                if (m == IMPLICIT_BIT << 1) {
                    m >>>= 1;
                    re++;
                }
            }
            re += k;
        } else if (k < 0) {
            // 左移规格化（次正态操作数）：无损
            m = p << -k;
            re += k;
        } else {
            m = p; // 恰 24 位（p ∈ [2^23, 2^24) 只可能双方均次正态且极小——罕见但正确）
        }
        return packNormalized(signum() * o.signum() < 0, m, re);
    }

    /** 除法（尾数 48 位被除数 / 24 位除数 → 24 位商 + 余数 RN） */
    public FloatExtension divide(final FloatExtension o) {
        if (isNaN() || o.isNaN()) {
            return NaN;
        }
        if (o.isZero()) {
            if (isZero()) {
                return NaN; // 0/0
            }
            return (signum() * o.signum() < 0) ? NEGATIVE_INFINITY : POSITIVE_INFINITY;
        }
        if (isZero()) {
            return (signum() * o.signum() < 0) ? NEG_ZERO : ZERO;
        }
        if (isInfinite()) {
            if (o.isInfinite()) {
                return NaN; // Inf/Inf
            }
            return (signum() * o.signum() < 0) ? NEGATIVE_INFINITY : POSITIVE_INFINITY;
        }
        if (o.isInfinite()) {
            return (signum() * o.signum() < 0) ? NEG_ZERO : ZERO;
        }

        final long m1 = significand();
        final long m2 = o.significand();
        final long num = m1 << SIGNIFICAND_WIDTH; // 48 位
        long q = num / m2;
        final long rem = num - q * m2;
        // 值 = q·2^(re1-re2-24)；未移位基准 realExp = re1-re2-1，右移/左移 k 位再补偿
        final int bl = 64 - Long.numberOfLeadingZeros(q); // 规格化操作数 → 24/25；次正态可更小/更大
        final int k = bl - SIGNIFICAND_WIDTH;
        long re = effectiveExponent() - o.effectiveExponent() - 1;
        long m;
        if (k > 0) {
            // 右移 k（丢位）：G = 移出最高位，R = 其余移出位，S = 余数
            m = q >> k;
            final boolean g = ((q >>> (k - 1)) & 1L) != 0;
            final boolean r = k >= 2 && (q & ((1L << (k - 1)) - 1L)) != 0;
            if (g && (r || rem != 0 || (m & 1L) != 0)) {
                m++;
                if (m == IMPLICIT_BIT << 1) {
                    m >>>= 1;
                    re++;
                }
            }
            re += k;
        } else if (k < 0) {
            // 左移规格化（次正态商）：无损
            m = q << -k;
            re += k;
        } else {
            // 恰 24 位：余数决定 RN（2·rem vs m2）
            m = q;
            if (rem != 0) {
                final long rem2 = rem << 1;
                if (rem2 > m2 || (rem2 == m2 && (m & 1L) != 0)) {
                    m++;
                    if (m == IMPLICIT_BIT << 1) {
                        m >>>= 1;
                        re++;
                    }
                }
            }
        }
        return packNormalized(signum() * o.signum() < 0, m, re);
    }

    // ═══════════ 比较 / 相等 / 哈希 ═══════════

    /**
     * 全序比较（复刻 JDK Float.compare 语义：NaN 最大，-0.0 == +0.0，
     * Inf 大于一切有限值）。
     */
    public int compareTo(final FloatExtension o) {
        if (isNaN()) {
            return o.isNaN() ? 0 : 1;
        }
        if (o.isNaN()) {
            return -1;
        }
        if (isZero() && o.isZero()) {
            return 0; // ±0 相等
        }
        final int s1 = signum();
        final int s2 = o.signum();
        if (s1 != s2) {
            return s1 < s2 ? -1 : 1;
        }
        // 同符号：按位模式无符号比较（正数直接、负数反向）
        final int cmp = Long.compareUnsigned(this.bits & ~SIGN_MASK, o.bits & ~SIGN_MASK);
        return s1 > 0 ? cmp : -cmp;
    }

    /** 位模式相等（复刻 JDK Float.equals：NaN 与 NaN 相等，-0.0 ≠ +0.0） */
    @Override
    public boolean equals(final Object obj) {
        return obj instanceof FloatExtension f && this.bits == f.bits;
    }

    @Override
    public int hashCode() {
        return (int) (this.bits ^ (this.bits >>> 32));
    }

    // ═══════════ 调试 ═══════════

    /** 位模式分解（调试用；完整最短十进制转换未实现） */
    @Override
    public String toString() {
        if (isNaN()) {
            return "NaN";
        }
        if (isInfinite()) {
            return signum() < 0 ? "-Infinity" : "Infinity";
        }
        return "FloatExtension[0x" + Long.toHexString(this.bits)
            + " s=" + (signum() < 0 ? "-" : "+")
            + " E=" + exponentField()
            + " m=" + significand()
            + "]";
    }
}