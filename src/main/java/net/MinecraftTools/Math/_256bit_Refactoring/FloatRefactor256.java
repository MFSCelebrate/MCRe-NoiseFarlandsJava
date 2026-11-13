package net.MinecraftTools.Math._256bit_Refactoring;

import java.math.BigInteger;
import java.util.Objects;

/**
 * 🔧 FloatRefactor256——256-bit 二进制浮点（MCRe 重构版第三版）。
 *
 * <p>位布局：
 * <pre>
 *   bit255      | 符号
 *   bit236..254 | 19 位偏置指数（偏置 262143；0 = 次正规/±0，全 1 = Inf/NaN）
 *   bit0..235   | 236 位小数字段（正规数补隐含位后 237 位精度）
 * </pre>
 * 值 = (-1)^s · m · 2^(E-262143-236)，m ∈ [2^236, 2^237) 规格化。
 * 正规指数范围 ±262142，最小次正规 2^-262378（QMIN = -262378）。
 *
 * <p>设计原则（见 {@code package-info.java}）：
 * <ul>
 *   <li><b>字段不拆</b>：指数与尾数在同一条连续位流中，提取靠 shift/mask——旧版 Float256
 *       "b 字段高 16 位拆指数"的跨域掩码陷阱（bit48 污染 → ×2，10 bug 之首）在此布局下
 *       不存在；</li>
 *   <li><b>单点收口</b>：一切舍入结果走 {@link #packRounded}（进位归一化 + 指数钳制 + 组装
 *       一条路），一切精确二进有理数走 {@link #fromDyadic}（GRS + packRounded）；</li>
 *   <li><b>SoftFloat 风格工作区</b>：加减对齐用有界 long 工作区（尾数域 + guard/round/sticky），
 *       不随指数差扩张；</li>
 *   <li><b>零 BigDecimal</b>：无缓存字段（旧版 cachedBigDecimal 死重 8B/实例根除），
 *       十进制输出按需现算。</li>
 * </ul>
 *
 * <p>结构复刻；尾数乘法 = {@link IntRefactorWords} 部分积；除法 = 同包 Knuth D；sqrt = 精确快路径
 * （{@link IntRefactorWords#sqrtUnsignedLong}）+ Newton 迭代（{@code IntRefactor256.divide}）。
 * 全部经 JDK BigDecimal/BigInteger/double 位语义强对照（tmp/FloatRefactor256Check.java）。
 */
public final class FloatRefactor256 implements Comparable<FloatRefactor256> {

    // ═══════════ 布局常量 ═══════════

    private static final int BIAS = 262143;
    /** 最小次正规指数（值 = m · 2^QMIN，m ∈ [1, 2^236)）。 */
    private static final int QMIN = -262378;
    private static final long FRACTION_MASK = 0x0000_0fff_ffff_ffffL; // l0 低 44 位
    private static final long HIDDEN_BIT = 0x0000_1000_0000_0000L;    // bit44（隐含位位置）
    private static final long INFINITY_TOP = 0x7fff_f000_0000_0000L;
    private static final long NAN_TOP = 0x7fff_f800_0000_0000L;
    /** 正规指数范围。 */
    public static final int MAX_EXPONENT = 262143;
    public static final int MIN_EXPONENT = -262142;
    /** 有效精度（含隐含位）。 */
    public static final int PRECISION = 237;

    // ═══════════ 特殊值 ═══════════

    public static final FloatRefactor256 ZERO = new FloatRefactor256(0, 0, 0, 0);
    public static final FloatRefactor256 NEGATIVE_ZERO = new FloatRefactor256(Long.MIN_VALUE, 0, 0, 0);
    public static final FloatRefactor256 ONE = new FloatRefactor256(0x3fff_f000_0000_0000L, 0, 0, 0);
    /** 最小非零正数 2^-262378。 */
    public static final FloatRefactor256 MIN_VALUE = new FloatRefactor256(0, 0, 0, 1);
    /** 最小正规正数 2^-262142。 */
    public static final FloatRefactor256 MIN_NORMAL = new FloatRefactor256(HIDDEN_BIT, 0, 0, 0);
    /** 最大有限正数 (2^237-1)·2^(2046-262143-236)。 */
    public static final FloatRefactor256 MAX_VALUE = new FloatRefactor256(0x7fff_efff_ffff_ffffL, -1, -1, -1);
    public static final FloatRefactor256 POSITIVE_INFINITY = new FloatRefactor256(INFINITY_TOP, 0, 0, 0);
    public static final FloatRefactor256 NEGATIVE_INFINITY = new FloatRefactor256(INFINITY_TOP | Long.MIN_VALUE, 0, 0, 0);
    /** 规范 quiet NaN（尾数最高位 = 1），数值运算产生的 NaN 统一使用该编码。 */
    public static final FloatRefactor256 NaN = new FloatRefactor256(NAN_TOP, 0, 0, 0);

    // ═══════════ 字段 ═══════════

    private final long l0;
    private final long l1;
    private final long l2;
    private final long l3;

    private FloatRefactor256(final long l0, final long l1, final long l2, final long l3) {
        this.l0 = l0;
        this.l1 = l1;
        this.l2 = l2;
        this.l3 = l3;
    }

    /** 按大端序保存完整原始编码（含 NaN 符号/payload），不执行数值转换。 */
    public static FloatRefactor256 fromRawBits(final long l0, final long l1, final long l2, final long l3) {
        return new FloatRefactor256(l0, l1, l2, l3);
    }

    /** 从恰好 32 字节大端序原始编码恢复（所有位模式合法；与 toBytes 逐位互逆）。 */
    public static FloatRefactor256 of(final byte[] bytes) {
        Objects.requireNonNull(bytes);
        if (bytes.length != 32) {
            throw new IllegalArgumentException("bytes must contain exactly 32 elements");
        }
        long word = 0;
        for (int index = 0; index < 8; index++) {
            word = (word << 8) | (bytes[index] & 0xffL);
        }
        final long a0 = word;
        word = 0;
        for (int index = 8; index < 16; index++) {
            word = (word << 8) | (bytes[index] & 0xffL);
        }
        final long a1 = word;
        word = 0;
        for (int index = 16; index < 24; index++) {
            word = (word << 8) | (bytes[index] & 0xffL);
        }
        final long a2 = word;
        word = 0;
        for (int index = 24; index < 32; index++) {
            word = (word << 8) | (bytes[index] & 0xffL);
        }
        return new FloatRefactor256(a0, a1, a2, word);
    }

    /** 大端序原始分组读取（0 = 最高组）。 */
    public long getRawLong(final int index) {
        return switch (index) {
            case 0 -> l0;
            case 1 -> l1;
            case 2 -> l2;
            case 3 -> l3;
            default -> throw new IndexOutOfBoundsException("index must be 0..3");
        };
    }

    /** 32 字节大端序完整原始编码（含 NaN 位模式）。 */
    public byte[] toBytes() {
        final byte[] result = new byte[32];
        for (int group = 0; group < 4; group++) {
            final long word = getRawLong(group);
            for (int index = 0; index < 8; index++) {
                result[group * 8 + index] = (byte) (word >>> (56 - index * 8));
            }
        }
        return result;
    }

    // ═══════════ 工厂 ═══════════

    /** 从 long 精确构造（经 IntRefactor256 幅值路径）。 */
    public static FloatRefactor256 of(final long value) {
        return of(IntRefactor256.of(value));
    }

    /** 从 int 精确构造。 */
    public static FloatRefactor256 of(final int value) {
        return of((long) value);
    }

    /** 从 float 精确构造（24 位尾数 ⊂ 237 位，零损失）。 */
    public static FloatRefactor256 of(final float value) {
        return of((double) value);
    }

    /** 从 double 精确构造（53 位尾数 ⊂ 237 位，零损失；位搬运 + 指数重偏置）。 */
    public static FloatRefactor256 of(final double value) {
        final long raw = Double.doubleToRawLongBits(value);
        final boolean negative = raw < 0L;
        final int exponent = (int) ((raw >>> 52) & 0x7ffL);
        final long fraction = raw & 0x000f_ffff_ffff_ffffL;
        return fromPrimitive(negative, exponent, fraction);
    }

    /** IEEE 二进制互转：exponent/fraction 为源格式指数域与 52 位分数（double 语义）。 */
    private static FloatRefactor256 fromPrimitive(final boolean negative, final int exponent, final long fraction) {
        if (exponent == 0x7ff) {
            return fraction == 0 ? infinity(negative) : (negative ? new FloatRefactor256(NAN_TOP | Long.MIN_VALUE, 0, 0, 0) : NaN);
        }
        if (exponent == 0 && fraction == 0) {
            return zero(negative);
        }
        // 规范化统一走 fromDyadic：值 = m · 2^(e-1075)（double 语义，m 含次正规的 52 位分数）
        // m ≤ 2^53 是整数值，直接作低组前幅值（fromDyadic 的 quantum 逻辑负责对齐与 GRS）
        final long m = exponent == 0 ? (fraction << 1) : (1L << 52) | fraction;
        final long scale = exponent - 1075L;
        return fromDyadic(negative, new long[] {m, 0L, 0L, 0L}, scale);
    }

    /** 从 IntRefactor256 构造（GRS 最近偶数舍入）。 */
    public static FloatRefactor256 of(final IntRefactor256 value) {
        Objects.requireNonNull(value);
        if (value.isZero()) {
            return ZERO;
        }
        final boolean negative = value.isNegative();
        // 幅值低组前四组（MIN_VALUE 的 2^255 在四组内）
        final long[] magnitude = magnitudeOf(value);
        return fromDyadic(negative, magnitude, 0L);
    }

    /** 同值构造（浮点不可变，直接返回）。 */
    public static FloatRefactor256 of(final FloatRefactor256 value) {
        return value;
    }

    /** 从 BigInteger 构造（经 IntRefactor256；超出 256 位补码域按回绕语义，调用方责任）。 */
    public static FloatRefactor256 of(final BigInteger value) {
        return of(IntRefactor256.of(value));
    }

    /**
     * 精确整数值导出（尾数 × 2^scale，小数部分截断；scale 极大时结果巨大——调用方责任）。
     * 次正态/正规一致；NaN/Inf 抛 ArithmeticException。
     */
    public BigInteger toBigInteger() {
        if (!isFinite()) {
            throw new ArithmeticException("not finite");
        }
        if (isZero()) {
            return BigInteger.ZERO;
        }
        final long[] mag = magnitude();
        BigInteger m = BigInteger.ZERO;
        for (int i = mag.length - 1; i >= 0; i--) {
            m = m.shiftLeft(64).or(BigInteger.valueOf(mag[i]).and(BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE)));
        }
        final long scale = scaleExponent();
        final int clamped = (int) Math.min(Math.abs(scale), 1 << 20);
        return scale >= 0 ? m.shiftLeft(clamped) : m.shiftRight(clamped);
    }

    // ═══════════ 谓词 ═══════════

    public boolean signBit() {
        return l0 < 0L;
    }

    public int signum() {
        if (isNaN()) {
            return 0; // 比较器单独处理 NaN
        }
        final boolean zero = isZero();
        if (zero) {
            return signBit() ? -1 : 1; // ±0 有符号
        }
        return signBit() ? -1 : 1;
    }

    public boolean isNaN() {
        return (l0 & INFINITY_TOP) == INFINITY_TOP && (l0 & FRACTION_MASK) != 0;
    }

    public boolean isInfinite() {
        return (l0 & INFINITY_TOP) == INFINITY_TOP && (l0 & FRACTION_MASK) == 0;
    }

    public boolean isFinite() {
        return (l0 & INFINITY_TOP) != INFINITY_TOP;
    }

    public boolean isZero() {
        return (l0 & 0x7fff_ffff_ffff_ffffL) == 0 && (l1 | l2 | l3) == 0;
    }

    /** 正规数（指数域非 0 非全 1）。 */
    public boolean isNormal() {
        final long encoded = encodedExponent();
        return encoded != 0 && encoded != 0x7ffffL;
    }

    /** 次正规数（指数域为 0 且尾数非零）。 */
    public boolean isSubnormal() {
        return encodedExponent() == 0 && !isZero();
    }

    private long encodedExponent() {
        return (l0 >>> 44) & 0x7ffffL;
    }

    private static FloatRefactor256 zero(final boolean negative) {
        return negative ? NEGATIVE_ZERO : ZERO;
    }

    private static FloatRefactor256 infinity(final boolean negative) {
        return negative ? NEGATIVE_INFINITY : POSITIVE_INFINITY;
    }

    // ═══════════ 幅值辅助（低组前约定） ═══════════

    /** 指数域（0..0x7ffff）。 */
    private int encodedExponentInt() {
        return (int) encodedExponent();
    }

    /** 内部按低组前读取有限幅值（含隐含位），超出四组补零。 */
    private long magnitudeWord(final int index) {
        return switch (index) {
            case 0 -> l3;
            case 1 -> l2;
            case 2 -> l1;
            case 3 -> (l0 & FRACTION_MASK) | (encodedExponent() == 0 ? 0 : HIDDEN_BIT);
            default -> 0L;
        };
    }

    /** 幅值（低组前 long[4]，已 trim 由调用方按需）。 */
    private long[] magnitude() {
        return IntRefactorWords.trim(new long[] {l3, l2, l1, magnitudeWord(3)});
    }

    /**
     * 幅值（低组前 4 组，<b>不 trim</b>）——供 multiply 使用：
     * IntRefactorWords.multiply 的结果容量是 a.length+b.length（满位宽语义），trim 会把高零组删掉
     * 导致容量不足、真积高位丢失（Binary256 的 multiplyMagnitude 按位长算容量同理）。
     */
    private long[] magnitudeFull() {
        return new long[] {l3, l2, l1, magnitudeWord(3)};
    }

    /** 调用方保证有限非零；次正规按最高非零 bit 计算实际指数。 */
    private long magnitudeExponent() {
        final int encoded = encodedExponentInt();
        if (encoded != 0) {
            return encoded - BIAS;
        }
        final long top = l0 & FRACTION_MASK;
        final int length = top != 0 ? 256 - Long.numberOfLeadingZeros(top)
                : l1 != 0 ? 192 - Long.numberOfLeadingZeros(l1)
                : l2 != 0 ? 128 - Long.numberOfLeadingZeros(l2) : 64 - Long.numberOfLeadingZeros(l3);
        return QMIN + length - 1L;
    }

    /** 尾数最低位的位权（scaleExponent）：正规 = E - BIAS - 236；次正规 = QMIN。 */
    private long scaleExponent() {
        final int encoded = encodedExponentInt();
        return encoded == 0 ? QMIN : encoded - BIAS - 236L;
    }

    /** IntRefactor256 的无符号幅值（低组前）。 */
    private static long[] magnitudeOf(final IntRefactor256 value) {
        final long l3 = value.getLong(3), l2 = value.getLong(2), l1 = value.getLong(1), l0 = value.getLong(0);
        if (l0 >= 0L) {
            return new long[] {l3, l2, l1, l0};
        }
        final long a3 = -l3;
        final long a2 = ~l2 + (l3 == 0L ? 1L : 0L);
        final long a1 = ~l1 + ((l3 | l2) == 0L ? 1L : 0L);
        final long a0 = ~l0 + ((l3 | l2 | l1) == 0L ? 1L : 0L);
        return new long[] {a3, a2, a1, a0};
    }

    // ═══════════ 收口（单点） ═══════════

    /**
     * 最终舍入打包（单点收口）：w0..w3 为低组前 237 位域尾数（规格化时 w3 的 bit44 = 隐含位），
     * scale 为尾数最低位（w0 bit0）的位权，increment 表示舍入加一。
     * 进位归一化 → 长度/指数钳制（上溢 Inf / 次正态 encoded=0）→ 组装。
     */
    private static FloatRefactor256 packRounded(boolean negative, long w0, long w1, long w2, long w3,
            long scale, boolean increment) {
        if (increment && ++w0 == 0 && ++w1 == 0 && ++w2 == 0) {
            w3++;
        }
        if ((w3 & (HIDDEN_BIT << 1)) != 0) {
            w0 = (w0 >>> 1) | (w1 << 63);
            w1 = (w1 >>> 1) | (w2 << 63);
            w2 = (w2 >>> 1) | (w3 << 63);
            w3 >>>= 1;
            scale++;
        }
        final int length = bitLength(w0, w1, w2, w3);
        if (length == 0) {
            return zero(negative);
        }
        final long exponent = scale + length - 1;
        if (exponent > MAX_EXPONENT) {
            return infinity(negative);
        }
        final long encoded = exponent < MIN_EXPONENT ? 0 : exponent + BIAS;
        return new FloatRefactor256((negative ? Long.MIN_VALUE : 0) | (encoded << 44)
                | (w3 & FRACTION_MASK), w2, w1, w0);
    }

    /**
     * 精确二进有理数收口：值 = magnitude · 2^scale（magnitude 低组前任意长）。
     * 在最终位权（精度与次正规下溢的较大约束）上一次性 GRS 舍入后走 {@link #packRounded}。
     */
    private static FloatRefactor256 fromDyadic(final boolean negative, final long[] magnitude, final long scale) {
        final int length = IntRefactorWords.bitLength(magnitude);
        if (length == 0) {
            return zero(negative);
        }
        final long exponent = scale + length - 1;
        if (exponent > MAX_EXPONENT) {
            return infinity(negative);
        }
        if (exponent < QMIN - 1L) {
            return zero(negative);
        }
        final long quantum = Math.max(exponent - 236, QMIN);
        final int discarded = Math.toIntExact(quantum - scale);
        final long low = shiftedWord(magnitude, 0, discarded);
        final boolean increment = discarded > 0 && IntRefactorWords.bit(magnitude, discarded - 1)
                && ((low & 1) != 0 || IntRefactorWords.anyLow(magnitude, discarded - 1));
        return packRounded(negative, low, shiftedWord(magnitude, 1, discarded),
                shiftedWord(magnitude, 2, discarded), shiftedWord(magnitude, 3, discarded), quantum, increment);
    }

    /** 两个低组（w0 最低、w1 次低）的非零精确乘积收口（不分配幅值数组）。 */
    private static FloatRefactor256 fromDyadicWords(final boolean negative, final long w0, final long w1, final long scale) {
        final int length = bitLength(w0, w1, 0, 0);
        final long exponent = scale + length - 1;
        if (exponent > MAX_EXPONENT) {
            return infinity(negative);
        }
        if (exponent < QMIN - 1L) {
            return zero(negative);
        }
        final long quantum = Math.max(exponent - 236, QMIN);
        final int discarded = Math.toIntExact(quantum - scale);
        final long low = shiftedWord(w0, w1, 0, 0, 0, discarded);
        boolean increment = false;
        if (discarded > 0) {
            final int guard = discarded - 1;
            final long word = guard < 64 ? w0 : w1;
            if ((word & (1L << (guard & 63))) != 0) {
                increment = (low & 1) != 0 || (guard < 64
                        ? (w0 & ((1L << guard) - 1)) != 0
                        : w0 != 0 || (w1 & ((1L << (guard - 64)) - 1)) != 0);
            }
        }
        return packRounded(negative, low, shiftedWord(w0, w1, 0, 0, 1, discarded),
                shiftedWord(w0, w1, 0, 0, 2, discarded), shiftedWord(w0, w1, 0, 0, 3, discarded), quantum, increment);
    }

    /** distance 为正时右移，为负时左移；幅值只读，范围外分组补零。 */
    private static long shiftedWord(final long[] magnitude, final int index, final int distance) {
        final int group = index + (distance >> 6), bits = distance & 63;
        final long low = group < 0 ? 0 : IntRefactorWords.word(magnitude, group);
        if (bits == 0) {
            return low;
        }
        final long high = group < -1 ? 0 : IntRefactorWords.word(magnitude, group + 1);
        return (low >>> bits) | (high << (64 - bits));
    }

    private static long shiftedWord(long w0, long w1, long w2, long w3, final int index, final int distance) {
        final int group = index + (distance >> 6), bits = distance & 63;
        final long low = fourWord(w0, w1, w2, w3, group);
        return bits == 0 ? low : (low >>> bits) | (fourWord(w0, w1, w2, w3, group + 1) << (64 - bits));
    }

    private static long fourWord(long w0, long w1, long w2, long w3, final int index) {
        return switch (index) {
            case 0 -> w0;
            case 1 -> w1;
            case 2 -> w2;
            case 3 -> w3;
            default -> 0L;
        };
    }

    private static int bitLength(long w0, long w1, long w2, long w3) {
        return w3 != 0 ? 256 - Long.numberOfLeadingZeros(w3)
                : w2 != 0 ? 192 - Long.numberOfLeadingZeros(w2)
                : w1 != 0 ? 128 - Long.numberOfLeadingZeros(w1) : 64 - Long.numberOfLeadingZeros(w0);
    }
    
    
    // ═══════════ 加法/减法（有界工作区） ═══════════

    /** 加法。 */
    public FloatRefactor256 add(final FloatRefactor256 other) {
        return add(other, false);
    }

    /** 减法。 */
    public FloatRefactor256 subtract(final FloatRefactor256 other) {
        return add(other, true);
    }

    /**
     * 加减合并实现（SoftFloat 风格有界工作区）：
     * 工作区 = 5 long（320 位）= 237 位尾数 + 3 位 GRS + 80 位对齐余量；
     * 指数差 ≥ 240 时小项整体进 sticky（值小于半个保留 ULP），不随指数差扩张。
     */
    private FloatRefactor256 add(final FloatRefactor256 other, final boolean subtract) {
        Objects.requireNonNull(other);
        if (isNaN() || other.isNaN()) {
            return NaN;
        }
        if (isZero() && other.isZero()) {
            // IEEE：同号零保留符号，异号零（+0 加 -0）→ +0
            final boolean sign = subtract ? (signBit() == other.signBit()) : (signBit() && other.signBit());
            return zero(sign);
        }
        if (isZero()) {
            return subtract ? other.negate() : other;
        }
        if (other.isZero()) {
            return this;
        }
        if (isInfinite() || other.isInfinite()) {
            final boolean otherSign = subtract != other.signBit();
            if (isInfinite() && other.isInfinite() && signBit() != otherSign) {
                return NaN;
            }
            return isInfinite() ? this : (otherSign ? NEGATIVE_INFINITY : POSITIVE_INFINITY);
        }

        final boolean sign1 = signBit();
        final boolean sign2 = subtract != other.signBit();
        final long scale1 = scaleExponent();
        final long scale2 = other.scaleExponent();
        final long exp1 = magnitudeExponent();
        final long exp2 = other.magnitudeExponent();

        // 对齐到大指数方；工作区位权基准 = scaleBig - 3（GRS 区）
        final FloatRefactor256 big = exp1 >= exp2 ? this : other;
        final FloatRefactor256 small = exp1 >= exp2 ? other : this;
        final boolean bigSign = exp1 >= exp2 ? sign1 : sign2;
        final boolean smallSign = exp1 >= exp2 ? sign2 : sign1;
        final long scaleBig = exp1 >= exp2 ? scale1 : scale2;
        final int distance = Math.toIntExact(Math.abs(exp1 - exp2));
        final boolean sameSign = bigSign == smallSign;

        // big << 3 → 5 long
        final long[] work = new long[5];
        putShiftedLeft3(work, big.magnitudeWord(0), big.magnitudeWord(1), big.magnitudeWord(2), big.magnitudeWord(3));
        // small << 3 后右移 distance；sticky = 移出位非零（不参与 sum——只进舍入 S 判定：
        // 加法 OR 进 bit0 尚安全（S 偏大无害），但减法会借位穿透污染 G/R，必须拆开）
        final long[] smallShifted = new long[5];
        putShiftedLeft3(smallShifted, small.magnitudeWord(0), small.magnitudeWord(1), small.magnitudeWord(2), small.magnitudeWord(3));
        boolean sticky = false;
        if (distance >= 240) {
            for (final long w : smallShifted) {
                sticky |= w != 0;
            }
            java.util.Arrays.fill(smallShifted, 0L);
            if (!sameSign && sticky) {
                // 减法 ceil 语义：非零小项 >> 240 的上取整为 1（保证 result = 精确换算，无 1 LSB 偏差）
                smallShifted[0] = 1L;
            }
        } else if (distance > 0) {
            sticky = anyLow5(smallShifted, distance);
            if (!sameSign) {
                // 减法 trailing-ones：(A-B)>>n = A>>n - ceil(B/2^n)——低 distance 位填 1 再右移即 ceil；
                // 若用 floor，result 会比精确换算大 1 LSB，进位穿透把 G/R/S 全翻（实测 add 差 1ulp 之根因）
                addLowOnes(smallShifted, distance);
            }
            shiftRightWords(smallShifted, distance);
        }

        final long[] result = new long[5];
        final boolean resultSign;
        if (sameSign) {
            addWords(work, smallShifted, result);
            resultSign = bigSign;
        } else {
            final int cmp = compareWords(work, smallShifted);
            if (cmp == 0) {
                return ZERO; // 异号精确相消 → +0
            }
            if (cmp > 0) {
                subtractWords(work, smallShifted, result);
                resultSign = bigSign;
            } else {
                subtractWords(smallShifted, work, result);
                resultSign = smallSign;
            }
        }

        // 从 320 位工作区提取 237 位 + GRS（k = bl - 237）
        final int bl = bitLength5(result);
        final int k = bl - PRECISION;
        final long[] kept = new long[4];
        long increment;
        long keptScale;
        if (k >= 0) {
            shiftRightInto4(result, k, kept);
            final boolean g = bit5(result, k - 1);                    // G = 第一个丢弃位
            final boolean rBit = k >= 2 && bit5(result, k - 2);       // R = 第二个丢弃位（原漏）
            final boolean sBits = k >= 3 && anyLow5(result, k - 2);   // S = 低 k-2 位（bit0..k-3）
            // S 判定 = 结果低位 || 对齐 sticky（sticky 不参与 sum，只在这里生效）
            increment = g && (rBit || sBits || sticky || (kept[0] & 1L) != 0L) ? 1L : 0L;
            keptScale = scaleBig - 3 + k;
        } else {
            // 左移规格化（相消后小值）：result 左移高位补 0 精确，唯 bit0 之下有 small 的
            // sticky 尾巴（frac）——kept bit0 恒偶，frac > 0 即过半 → RN 进位
            shiftLeftInto4(result, -k, kept);
            increment = sticky ? 1L : 0L;
            keptScale = scaleBig - 3 + k;
        }
        return packRounded(resultSign, kept[0], kept[1], kept[2], kept[3], keptScale, increment != 0);
    }

    // ═══════════ 乘法 ═══════════

    /** 乘法（尾数 237×237 = 474 位部分积，fromDyadic 单点收口）。 */
    public FloatRefactor256 multiply(final FloatRefactor256 other) {
        Objects.requireNonNull(other);
        if (isNaN() || other.isNaN()) {
            return NaN;
        }
        if (isZero() || other.isZero()) {
            if (isInfinite() || other.isInfinite()) {
                return NaN; // 0 × Inf
            }
            return zero(signBit() != other.signBit());
        }
        if (isInfinite() || other.isInfinite()) {
            return infinity(signBit() != other.signBit());
        }
        // 不 trim 的 4 组 × 4 组 → 8 组积（474 位 ⊂ 512）——trim 版会丢高位（见 magnitudeFull 注释）
        final long[] product = IntRefactorWords.multiply(magnitudeFull(), other.magnitudeFull());
        return fromDyadic(signBit() != other.signBit(), product, scaleExponent() + other.scaleExponent());
    }

    // ═══════════ 除法 ═══════════

    /** 除法（Knuth D 商 + 余数最近偶数舍入）。 */
    public FloatRefactor256 divide(final FloatRefactor256 other) {
        Objects.requireNonNull(other);
        if (isNaN() || other.isNaN()) {
            return NaN;
        }
        if (other.isZero()) {
            if (isZero()) {
                return NaN; // 0/0
            }
            return infinity(signBit() != other.signBit());
        }
        if (isZero()) {
            return zero(signBit() != other.signBit());
        }
        if (isInfinite()) {
            if (other.isInfinite()) {
                return NaN; // Inf/Inf
            }
            return infinity(signBit() != other.signBit());
        }
        if (other.isInfinite()) {
            return zero(signBit() != other.signBit());
        }
        final boolean negative = signBit() != other.signBit();
        final long[] a = magnitude();
        final long[] b = other.magnitude();
        final long scale = scaleExponent() - other.scaleExponent();
        // 商的指数估计：幅值位宽差 + 比较调整（a < b 时少一位）
        int ratioExponent = IntRefactorWords.bitLength(a) - IntRefactorWords.bitLength(b);
        final int comparison = ratioExponent >= 0
                ? IntRefactorWords.compare(a, IntRefactorWords.left(b, ratioExponent))
                : IntRefactorWords.compare(IntRefactorWords.left(a, -ratioExponent), b);
        if (comparison < 0) {
            ratioExponent--;
        }
        final long exponent = scale + ratioExponent;
        if (exponent > MAX_EXPONENT) {
            return infinity(negative);
        }
        if (exponent < QMIN - 1L) {
            return zero(negative);
        }
        // 对齐到 quantum（商的最低位权重）：shift ≥ 0 移被除数，< 0 移除数（等效放大除数）
        final long quantum = Math.max(exponent - 236, QMIN);
        final long shift = scale - quantum;
        final long[] aa = shift >= 0 ? IntRefactorWords.left(a, Math.toIntExact(shift)) : a;
        final long[] bb = shift >= 0 ? b : IntRefactorWords.left(b, Math.toIntExact(-shift));
        // 商恒 237 位；RN：half = 2R vs bb（tie 看商最低位奇偶）
        final IntRefactorWords.Division division = IntRefactorWords.divide(aa, bb);
        final long[] result = division.quotient();
        final int half = IntRefactorWords.compare(IntRefactorWords.left(division.remainder(), 1), bb);
        return packRounded(negative, IntRefactorWords.word(result, 0), IntRefactorWords.word(result, 1),
                IntRefactorWords.word(result, 2), IntRefactorWords.word(result, 3), quantum,
                half > 0 || (half == 0 && IntRefactorWords.bit(result, 0)));
    }

    // ═══════════ 平方根 ═══════════

    /**
     * 平方根（正有限；RN 舍入——开方中点永不可达，increment = 余数 > 根）。
     * 精确快路径（幅值 ≤ 64 位完全平方）→ Newton（words 级，IntRefactorWords.divide 迭代）。
     */
    public FloatRefactor256 sqrt() {
        if (isNaN() || (signBit() && !isZero())) {
            return NaN;
        }
        if (isZero() || isInfinite()) {
            return this;
        }
        long[] mag = magnitude();
        long scale = scaleExponent();
        // 指数调偶（值不变：mag << 1, scale - 1）
        if ((scale & 1) != 0) {
            mag = IntRefactorWords.left(mag, 1);
            scale--;
        }
        // 精确快路径
        if (mag.length <= 1) {
            final long m = mag.length == 0 ? 0L : mag[0];
            if (m != 0L) {
                final long root = IntRefactorWords.sqrtUnsignedLong(m);
                if (root * root == m) {
                    return fromDyadic(false, new long[] {root, 0L, 0L, 0L}, scale / 2);
                }
            }
        }
        // Newton：N = mag << s（位长补足到 474，s 与 scale 同奇偶保证 (scale-s) 偶）
        final int bl = IntRefactorWords.bitLength(mag);
        int s = 2 * PRECISION - bl;
        if (s < 0) {
            s = 0;
        }
        if (((s ^ (int) Math.min(scale, Integer.MAX_VALUE)) & 1) != 0) {
            s++;
        }
        final long[] n = s == 0 ? mag : IntRefactorWords.left(mag, s);
        final IntRefactorWords.Division rootAndRemainder = sqrtWords(n);
        final long[] root = rootAndRemainder.quotient();
        final long[] remainder = rootAndRemainder.remainder();
        // RN：真根 = root + f（f ∈ [0,1)），f > 0.5 ⟺ 余数 > 根（开方无精确 tie）。
        // 让出低 2 位作 G/R：fBits = (r>root ? 0b11 : (r!=0 ? 0b01 : 0b00))——
        // f>0.5 → G=1,S=1 恒进位；0<f<0.5 → G=0,S=1 不进；f=0 → 精确。
        final int fBits = IntRefactorWords.compare(remainder, root) > 0 ? 3 : (remainder.length != 0 ? 1 : 0);
        final long[] shifted = IntRefactorWords.left(root, 2);
        shifted[0] |= fBits;
        return fromDyadic(false, shifted, (scale - s) / 2 - 2);
    }

    /** floor(sqrt(n))（n 低组前非零）：x0 = 2^ceil(bl/2) 上界起 Newton，返回根与余数 n - root²。 */
    private static IntRefactorWords.Division sqrtWords(final long[] n) {
        final int bl = IntRefactorWords.bitLength(n);
        long[] x = new long[] {1L << ((bl + 1) / 2 % 64)};
        x = IntRefactorWords.left(x, (bl + 1) / 2 - ((bl + 1) / 2 % 64));
        // 上述构造等价于 1 << ceil(bl/2)；直接简化：
        x = IntRefactorWords.left(new long[] {1}, (bl + 1) / 2);
        for (;;) {
            final long[] q = IntRefactorWords.divide(n, x).quotient();
            // x' = (x + q) >> 1
            final long[] sum = IntRefactorWords.add(x, q);
            final long[] next = IntRefactorWords.shiftRight1(sum);
            if (IntRefactorWords.compare(next, x) >= 0) {
                // 收敛：x 即 floor(sqrt(n))；余数 = n - x²
                final long[] square = IntRefactorWords.multiply(x, x);
                final long[] remainder = IntRefactorWords.compare(n, square) >= 0
                        ? IntRefactorWords.subtract(n, square) : new long[0];
                return new IntRefactorWords.Division(x, remainder);
            }
            x = next;
        }
    }

    // ═══════════ 符号/缩放 ═══════════

    /** 取负（NaN 也翻符号位并保留 payload——对齐 JDK 符号 NaN 语义）。 */
    public FloatRefactor256 negate() {
        return new FloatRefactor256(l0 ^ Long.MIN_VALUE, l1, l2, l3);
    }

    /** 绝对值。 */
    public FloatRefactor256 abs() {
        return signBit() ? negate() : this;
    }

    /** 乘 2^scaleFactor（指数域内直接移位；越界走幅值 + fromDyadic）。 */
    public FloatRefactor256 scalb(final long scaleFactor) {
        if (isNaN() || isZero() || isInfinite()) {
            return this;
        }
        final long encoded = encodedExponent();
        if (encoded != 0 && scaleFactor >= 1L - encoded && scaleFactor <= 0x7fffeL - encoded) {
            return new FloatRefactor256(signBit() ? Long.MIN_VALUE : 0
                    | (l0 & FRACTION_MASK) | ((encoded + scaleFactor) << 44), l1, l2, l3);
        }
        return fromDyadic(signBit(), magnitude(), scaleExponent() + scaleFactor);
    }

    // ═══════════ 转换 ═══════════

    /** 转换到 float（GRS 最近偶数；超出 float 范围 → ±Inf；低于 float 次正态界 → ±0）。 */
    public float toFloat() {
        return Float.intBitsToFloat((int) toPrimitiveBits(24, 127, 8));
    }

    /** 转换到 double（GRS 最近偶数）。 */
    public double toDouble() {
        return Double.longBitsToDouble(toPrimitiveBits(53, 1023, 11));
    }

    /**
     * 到 IEEE 二进制编码（调用方保证有限非零）。precision/bias/exponentBits 为目标格式参数；
     * 次正态域用钳制指数（effectiveExp = max(exponent, 1-bias)）统一处理，一次舍入定成败。
     */
    private long toPrimitiveBits(final int precision, final int bias, final int exponentBits) {
        // 🔧 NaN/Inf 提前分支（DeepSeek v4.1 指出）：NaN 的指数域全 1，magnitudeExponent 会算出
        // 262144 超大指数 → 命中下方 Inf 钳制把 NaN 变成 ±Inf（违反 IEEE）——必须先分流
        if (isNaN()) {
            // quiet NaN：指数全 1 + 尾数最高位置 1（double 0x7ff8…/float 0x7fc0… 同构）
            final long sign = signBit() ? 1L << (precision - 1 + exponentBits) : 0L;
            return sign | (((1L << exponentBits) - 1) << (precision - 1)) | (1L << (precision - 2));
        }
        if (isInfinite()) {
            final long sign = signBit() ? 1L << (precision - 1 + exponentBits) : 0L;
            return sign | (((1L << exponentBits) - 1) << (precision - 1));
        }
        final int bl = bitLength(magnitudeWord(3), magnitudeWord(2), magnitudeWord(1), magnitudeWord(0));
        final long exponent = magnitudeExponent(); // 最高位权重
        final long sign = signBit() ? 1L << (precision - 1 + exponentBits) : 0L;
        final long infinity = ((1L << exponentBits) - 1) << (precision - 1);
        final long effectiveExp = Math.max(exponent, 1L - bias);
        // 丢弃位数 = 保留区最低位权重到 scale 的距离：保留区最低位 = effectiveExp - precision + 1
        // （正规时退化为 bl - precision；次正态时钳制到固定 ULP，两种域统一一条公式）
        final int discarded = (int) (effectiveExp - precision + 1 - scaleExponent());
        final long low = shiftedWord(magnitudeWord(0), magnitudeWord(1), magnitudeWord(2), magnitudeWord(3), 0, discarded);
        long kept = low;
        if (discarded > 0) {
            final boolean g = magnitudeBit(discarded - 1);
            final boolean rr = discarded >= 2 && anyLowMagnitude(discarded - 2);
            if (g && (rr || (kept & 1L) != 0L)) {
                kept++;
            }
        }
        long encodedExp = exponent >= 1L - bias ? exponent + bias : 0L; // 次正态：指数域 0 + 纯尾数
        if (kept == (1L << precision)) {
            kept >>>= 1;
            encodedExp++;
        }
        if (encodedExp >= (1L << exponentBits) - 1) {
            return sign | infinity;
        }
        return sign | (encodedExp << (precision - 1)) | (kept & ((1L << (precision - 1)) - 1));
    }

    private boolean magnitudeBit(final int index) {
        return (magnitudeWord(index >>> 6) & (1L << (index & 63))) != 0;
    }

    private boolean anyLowMagnitude(final int count) {
        for (int i = 0; (i << 6) < count; i++) {
            final long mask = (i << 6) + 64 <= count ? -1L : (1L << (count - (i << 6))) - 1;
            if ((magnitudeWord(i) & mask) != 0) {
                return true;
            }
        }
        return false;
    }

    /** 截断到 IntRefactor256（向零；NaN/Inf 抛 ArithmeticException）。 */
    public IntRefactor256 toIntRefactor256() {
        if (isNaN() || isInfinite()) {
            throw new ArithmeticException("not finite");
        }
        if (isZero()) {
            return IntRefactor256.ZERO;
        }
        final long exponent = magnitudeExponent();
        if (exponent < 0) {
            return IntRefactor256.ZERO; // |值| < 1
        }
        // 值 = mag × 2^scale；整数部分：mag 右移 (236 - exponent) 位（负则左移）
        final long shift = 236 - exponent;
        final long[] words = shift <= 0
                ? IntRefactorWords.lowBits(IntRefactorWords.left(magnitude(), (int) -shift), 256)
                : IntRefactorWords.lowBits(IntRefactorWords.shiftRight(magnitude(), (int) shift), 256);
        return IntRefactor256.fromMagnitudeWordsPublic(signBit(), words);
    }

    // ═══════════ 比较 / 相等 / 哈希 ═══════════

    /** 全序（NaN 最大；±0 相等；其余按数值）。 */
    @Override
    public int compareTo(final FloatRefactor256 other) {
        if (isNaN()) {
            return other.isNaN() ? 0 : 1;
        }
        if (other.isNaN()) {
            return -1;
        }
        if (isZero() && other.isZero()) {
            return 0;
        }
        if (signBit() != other.signBit()) {
            return signBit() ? -1 : 1;
        }
        final int cmp = compareMagnitude(other);
        return signBit() ? -cmp : cmp;
    }

    /** 两值均为有限非零同号；只调整比较位权，不执行浮点舍入。 */
    private int compareMagnitude(final FloatRefactor256 other) {
        final long e1 = magnitudeExponent(), e2 = other.magnitudeExponent();
        final int cmp = Long.compare(e1, e2);
        if (cmp != 0) {
            return cmp;
        }
        final int shift = (int) (236 - e1 + scaleExponent());
        final int otherShift = (int) (236 - e2 + other.scaleExponent());
        for (int group = 3; group >= 0; group--) {
            final int comparison = Long.compareUnsigned(alignedMagnitudeWord(group, shift),
                    other.alignedMagnitudeWord(group, otherShift));
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    private long alignedMagnitudeWord(final int group, final int shift) {
        final int whole = shift >>> 6, bits = shift & 63;
        final long word = magnitudeWord(group - whole) << bits;
        return bits == 0 ? word : word | (magnitudeWord(group - whole - 1) >>> (64 - bits));
    }

    /** 位模式相等（NaN 与 NaN 相等，-0.0 ≠ +0.0，复刻 JDK Float.equals）。 */
    @Override
    public boolean equals(final Object obj) {
        return obj instanceof FloatRefactor256 other
                && l0 == other.l0 && l1 == other.l1 && l2 == other.l2 && l3 == other.l3;
    }

    @Override
    public int hashCode() {
        long h = l0;
        h = h * 31 + l1;
        h = h * 31 + l2;
        h = h * 31 + l3;
        return (int) (h ^ (h >>> 32));
    }

    /**
     * 精确十进制字符串（double 输入构造时 52 位尾数在 237 位内 → 完整展开无精度损失；
     * |scale| ≤ 262378 恒在 int 域无需钳制；极端 scale 的展开成本由调用方承担——精确语义优先）。
     */
    public String toExactString() {
        if (isNaN()) {
            return "NaN";
        }
        if (isInfinite()) {
            return signBit() ? "-Infinity" : "Infinity";
        }
        if (isZero()) {
            return signBit() ? "-0.0" : "0.0";
        }
        final long[] mag = magnitude();
        java.math.BigInteger m = java.math.BigInteger.ZERO;
        for (int i = mag.length - 1; i >= 0; i--) {
            m = m.shiftLeft(64).or(java.math.BigInteger.valueOf(mag[i])
                    .and(java.math.BigInteger.ONE.shiftLeft(64).subtract(java.math.BigInteger.ONE)));
        }
        // 手写十进制展开（JDK BigDecimal 无 scaleByPowerOfTwo）：sc ≥ 0 直接移位；sc < 0 时
        // 值 = m / 2^k = m·5^k / 10^k——乘 5^k 后按十进制插小数点（与 toString 同级的显示层依赖）
        final int sc = Math.toIntExact(scaleExponent());
        final java.math.BigInteger digitsBig = sc >= 0
                ? m.shiftLeft(sc)
                : m.multiply(java.math.BigInteger.valueOf(5).pow(-sc));
        String digits = digitsBig.toString();
        if (sc < 0) {
            final int k = -sc;
            if (digits.length() <= k) {
                digits = "0".repeat(k - digits.length() + 1) + digits;
            }
            digits = digits.substring(0, digits.length() - k) + "." + digits.substring(digits.length() - k);
            // 去尾零 + 去小数点（复刻 BigDecimal.toString 语义：1.500 → "1.5"、2.0 → "2"）
            int end = digits.length();
            while (end > 0 && digits.charAt(end - 1) == '0') {
                end--;
            }
            if (end > 0 && digits.charAt(end - 1) == '.') {
                end--;
            }
            digits = digits.substring(0, end);
        }
        return (signBit() ? "-" : "") + digits;
    }

    /** 十进制字符串（调试用；幅值 × 2^scale 现算，不缓存）。 */
    @Override
    public String toString() {
        if (isNaN()) {
            return "NaN";
        }
        if (isInfinite()) {
            return signBit() ? "-Infinity" : "Infinity";
        }
        if (isZero()) {
            return signBit() ? "-0.0" : "0.0";
        }
        final long scale = scaleExponent();
        if (scale > 10_000 || scale < -10_000) {
            // 🔧 极端 scale（次正态等）时 2^scale 的 BigDecimal 展开是天文数字（龟速）——
            // 调试场景走紧凑表示（幅值 hex × 2^scale），不抛异常也不卡
            final java.math.BigInteger magnitude = new java.math.BigInteger(1, magnitudeBytes());
            return (signBit() ? "-" : "") + magnitude.toString(16) + "×2^" + scale;
        }
        final java.math.BigInteger magnitude = new java.math.BigInteger(1, magnitudeBytes());
        final java.math.BigDecimal value = new java.math.BigDecimal(magnitude);
        final java.math.BigDecimal result = scale >= 0
                ? value.multiply(java.math.BigDecimal.TWO.pow(Math.toIntExact(Math.min(scale, 100_000))))
                : value.divide(java.math.BigDecimal.TWO.pow(Math.toIntExact(Math.min(-scale, 100_000))),
                        new java.math.MathContext(80));
        return (signBit() ? "-" : "") + result.unscaledValue().abs() + "E" + result.scale();
    }

    private byte[] magnitudeBytes() {
        // 幅值低组前 → 大端 32 字节正整数
        final long[] words = magnitude();
        final byte[] result = new byte[32];
        for (int i = 0; i < 4; i++) {
            final long word = IntRefactorWords.word(words, 3 - i);
            for (int b = 0; b < 8; b++) {
                result[i * 8 + b] = (byte) (word >>> (56 - b * 8));
            }
        }
        return result;
    }

    // ═══════════ 工作区辅助（5 long / 4 long） ═══════════

    /**
     * 5 long 工作区加"低 count 位全 1"的加数（trailing-ones 的<b>加法</b>语义）——
     * (X + 2^count - 1) >> count = ceil(X / 2^count)：低位非零时加出进位即上取整。
     * 注意：按位 OR 置 1 不产生进位（等于 floor），必须用加。
     */
    private static void addLowOnes(final long[] work, final int count) {
        final int whole = count >>> 6, bits = count & 63;
        final long[] ones = new long[5];
        for (int i = 0; i < whole && i < ones.length; i++) {
            ones[i] = -1L;
        }
        if (bits != 0 && whole < ones.length) {
            ones[whole] = (1L << bits) - 1;
        }
        addWords(work, ones, work);
    }

    /** 把低组前四组幅值左移 3 位写入 5 long 工作区。 */
    private static void putShiftedLeft3(final long[] work, final long w0, final long w1, final long w2, final long w3) {
        work[0] = w0 << 3;
        work[1] = (w1 << 3) | (w0 >>> 61);
        work[2] = (w2 << 3) | (w1 >>> 61);
        work[3] = (w3 << 3) | (w2 >>> 61);
        work[4] = w3 >>> 61;
    }

    /** 5 long 工作区无符号右移 distance 位（0 < distance < 240）。 */
    private static void shiftRightWords(final long[] work, final int distance) {
        final int whole = distance >>> 6, bits = distance & 63;
        // 上一行写法容易出错，改为标准两来源拼接：
        for (int i = 0; i < work.length; i++) {
            final long a = i + whole < work.length ? work[i + whole] >>> bits : 0L;
            final long b = bits != 0 && i + whole + 1 < work.length ? work[i + whole + 1] << (64 - bits) : 0L;
            work[i] = a | b;
        }
    }

    private static void addWords(final long[] a, final long[] b, final long[] result) {
        long carry = 0;
        for (int i = 0; i < result.length; i++) {
            final long sum = a[i] + b[i];
            final long next = sum + carry;
            carry = Long.compareUnsigned(sum, a[i]) < 0 || Long.compareUnsigned(next, sum) < 0 ? 1 : 0;
            result[i] = next;
        }
    }

    private static void subtractWords(final long[] a, final long[] b, final long[] result) {
        long borrow = 0;
        for (int i = 0; i < result.length; i++) {
            final long difference = a[i] - b[i];
            final long next = difference - borrow;
            borrow = Long.compareUnsigned(a[i], b[i]) < 0 || Long.compareUnsigned(difference, borrow) < 0 ? 1 : 0;
            result[i] = next;
        }
    }

    private static int compareWords(final long[] a, final long[] b) {
        for (int i = a.length - 1; i >= 0; i--) {
            final int cmp = Long.compareUnsigned(a[i], b[i]);
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    private static int bitLength5(final long[] work) {
        for (int i = work.length - 1; i >= 0; i--) {
            if (work[i] != 0) {
                return i * 64 + 64 - Long.numberOfLeadingZeros(work[i]);
            }
        }
        return 0;
    }

    private static boolean bit5(final long[] work, final int index) {
        return index >= 0 && (work[index >>> 6] & (1L << (index & 63))) != 0;
    }

    private static boolean anyLow5(final long[] work, final int count) {
        if (count <= 0) {
            return false;
        }
        final int whole = count >>> 6, bits = count & 63;
        for (int i = 0; i < whole; i++) {
            if (work[i] != 0) {
                return true;
            }
        }
        return bits != 0 && whole < work.length && (work[whole] & ((1L << bits) - 1)) != 0;
    }

    /** 5 long 工作区右移 k 位取低 4 组（调用方保证 k ≥ 0 且结果非零）。 */
    private static void shiftRightInto4(final long[] work, final int k, final long[] kept) {
        final int whole = k >>> 6, bits = k & 63;
        for (int i = 0; i < 4; i++) {
            final long a = i + whole < work.length ? work[i + whole] >>> bits : 0L;
            final long b = bits != 0 && i + whole + 1 < work.length ? work[i + whole + 1] << (64 - bits) : 0L;
            kept[i] = a | b;
        }
    }

    /** 5 long 工作区左移 k 位取低 4 组（k ≥ 0，调用方保证高位不丢）。 */
    private static void shiftLeftInto4(final long[] work, final int k, final long[] kept) {
        final int whole = k >>> 6, bits = k & 63;
        for (int i = 0; i < 4; i++) {
            final int src = i - whole;
            long value = 0L;
            if (src >= 0) {
                value = work[src] << bits;
                if (bits != 0 && src - 1 >= 0) {
                    value |= work[src - 1] >>> (64 - bits);
                }
            }
            kept[i] = value;
        }
    }
}
