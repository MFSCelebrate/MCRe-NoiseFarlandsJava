package net.MinecraftTools.Math._256bit_Refactoring;

import java.math.BigInteger;
import java.util.Objects;

/**
 * 🔧 IntRefactor256——256-bit 有符号整数（补码，MCRe 重构版第三版）。
 *
 * <p>设计原则（见 {@code package-info.java}）：
 * <ul>
 *   <li><b>纯 long 字段</b>：l0（最高组含符号位）~ l3（最低组）大端四个 long，共 48 字节，
 *       无 int[] 数组、无 BigDecimal 缓存——旧版 Int256 的缓存膨胀（用后可达 256B）根除；</li>
 *   <li><b>热路径手写展开</b>：add/subtract 进位链、multiply 部分积全展开，零数组零循环；</li>
 *   <li><b>Knuth Algorithm D 除法</b>：32-bit limb 教科书移植（试商 long 单步无溢出），
 *       收口于同包 {@link IntRefactorWords}；</li>
 *   <li><b>溢出回绕</b>（不抛异常）：算术保留数学结果的低 256 位，按补码解释——与
 *       Long2 {@code Int256} 语义一致；MIN_VALUE / -1 = MIN_VALUE（回绕）。</li>
 * </ul>
 *
 * <p>进位链与 {@code Math.unsignedMultiplyHigh} 部分积（补码乘保留低 256 位时可按无符号分组
 * 累加，符号天然正确）；toFloat/toDouble/of(double) = 同源幅值提取 + GRS 最近偶数舍入。
 * 全部经 JDK BigInteger / double 位语义强对照测试（tmp/IntRefactor256Check.java）。
 */
public final class IntRefactor256 implements Comparable<IntRefactor256> {

    // ═══════════ 字段与常量 ═══════════

    private final long l0;
    private final long l1;
    private final long l2;
    private final long l3;

    private IntRefactor256(final long l0, final long l1, final long l2, final long l3) {
        this.l0 = l0;
        this.l1 = l1;
        this.l2 = l2;
        this.l3 = l3;
    }

    /** 数值 2^255-1。 */
    public static final IntRefactor256 MAX_VALUE = new IntRefactor256(Long.MAX_VALUE, -1L, -1L, -1L);
    /** 数值 -2^255。 */
    public static final IntRefactor256 MIN_VALUE = new IntRefactor256(Long.MIN_VALUE, 0L, 0L, 0L);
    /** 零。 */
    public static final IntRefactor256 ZERO = new IntRefactor256(0L, 0L, 0L, 0L);
    /** 一。 */
    public static final IntRefactor256 ONE = new IntRefactor256(0L, 0L, 0L, 1L);

    // ═══════════ 工厂 ═══════════

    /** 有符号 long 数值扩展（高位符号扩展）。 */
    public static IntRefactor256 of(final long value) {
        return new IntRefactor256(value >> 63, value >> 63, value >> 63, value);
    }

    /** 按大端序拼接四个 long 的原始位模式（l0 最高含符号位，各参数符号不改变位权）。 */
    public static IntRefactor256 of(final long l0, final long l1, final long l2, final long l3) {
        return new IntRefactor256(l0, l1, l2, l3);
    }

    /** 按大端序从恰好四个 long 的原始位模式构造（数组首元素最高组）。 */
    public static IntRefactor256 of(final long[] longs) {
        Objects.requireNonNull(longs);
        if (longs.length != 4) {
            throw new IllegalArgumentException("parameter 'longs' must contain exactly 4 elements");
        }
        return new IntRefactor256(longs[0], longs[1], longs[2], longs[3]);
    }

    /** 从恰好 32 字节大端序补码位模式恢复（索引 0 最高有效字节；与 toBytes 逐位互逆）。 */
    public static IntRefactor256 of(final byte[] bytes) {
        Objects.requireNonNull(bytes);
        if (bytes.length != 32) {
            throw new IllegalArgumentException("bytes must contain exactly 32 elements");
        }
        return new IntRefactor256(readRawLong(bytes, 0), readRawLong(bytes, 8),
                readRawLong(bytes, 16), readRawLong(bytes, 24));
    }

    private static long readRawLong(final byte[] bytes, final int offset) {
        long word = 0;
        for (int index = 0; index < 8; index++) {
            word = (word << 8) | (bytes[offset + index] & 0xffL);
        }
        return word;
    }

    /** 从 JDK BigInteger 转换（超范围截断低 256 位，回绕语义）。 */
    public static IntRefactor256 of(final BigInteger value) {
        Objects.requireNonNull(value);
        return new IntRefactor256(
                value.shiftRight(192).longValue(),
                value.shiftRight(128).longValue(),
                value.shiftRight(64).longValue(),
                value.longValue());
    }

    /** 按 Int256 的截断/钳制规则从 double 构造（NaN → 0；|值| ≥ 2^255 按符号钳制）。 */
    public static IntRefactor256 of(final double value) {
        final long raw = Double.doubleToRawLongBits(value);
        final int exponent = (int) ((raw >>> 52) & 0x7ffL);
        final long fraction = raw & 0x000f_ffff_ffff_ffffL;
        final boolean negative = raw < 0L;

        long a0 = 0L, a1 = 0L, a2 = 0L, a3 = 0L;

        if (exponent >= 1023 + 255) {
            // 绝对值达到 2^255 的有限值及无穷按符号钳制，负的 2^255 恰好为最小值；NaN 保留初始零。
            if (exponent != 0x7ff || fraction == 0L) {
                if (negative) {
                    a0 = Long.MIN_VALUE;
                } else {
                    a0 = Long.MAX_VALUE;
                    a1 = a2 = a3 = -1L;
                }
            }
        } else if (exponent >= 1023) {
            // 绝对值至少为一的有限 double 有隐含的最高位，补齐后得到 53-bit significand。
            final long significand = (1L << 52) | fraction;
            final int e = exponent - 1023;

            if (e <= 52) {
                a3 = significand >>> (52 - e);
            } else {
                final int shift = e - 52;
                final int n = shift & 63;
                final long low = significand << n;
                final long high = n == 0 ? 0L : significand >>> (64 - n);

                switch (shift >>> 6) {
                    case 0 -> { a3 = low; a2 = high; }
                    case 1 -> { a2 = low; a1 = high; }
                    case 2 -> { a1 = low; a0 = high; }
                    case 3 -> a0 = low;
                }
            }

            // 按补码取负，低组全零时才向高组传递加一进位。
            if (negative) {
                final long b3 = a3, b2 = a2, b1 = a1;
                a3 = -b3;
                a2 = ~b2 + (b3 == 0L ? 1L : 0L);
                a1 = ~b1 + ((b3 | b2) == 0L ? 1L : 0L);
                a0 = ~a0 + ((b3 | b2 | b1) == 0L ? 1L : 0L);
            }
        }

        return new IntRefactor256(a0, a1, a2, a3);
    }

    /** float 的有限值可由 double 精确表示，扩宽后沿用相同规则。 */
    public static IntRefactor256 of(final float value) {
        return of((double) value);
    }

    /** 按十进制数值解析（超出范围回绕；语法非法抛 NumberFormatException）。 */
    public static IntRefactor256 parseInt(final String value) {
        Objects.requireNonNull(value);
        return of(new BigInteger(value.trim()));
    }

    /** 按十六进制原始位模式解析（接受 0x 前缀；超出 64 位 hex 字符截断低 256 位）。 */
    public static IntRefactor256 parseHexInt(final String value) {
        Objects.requireNonNull(value);
        String hex = value.startsWith("0x") || value.startsWith("0X") ? value.substring(2) : value;
        if (hex.isEmpty() || hex.length() > 64) {
            throw new NumberFormatException("Invalid hexadecimal value: " + value);
        }
        for (int index = 0; index < hex.length(); index++) {
            if (Character.digit(hex.charAt(index), 16) < 0) {
                throw new NumberFormatException("Invalid hexadecimal value: " + value);
            }
        }
        hex = "0".repeat(64 - hex.length()) + hex;
        return new IntRefactor256(
                Long.parseUnsignedLong(hex.substring(0, 16), 16),
                Long.parseUnsignedLong(hex.substring(16, 32), 16),
                Long.parseUnsignedLong(hex.substring(32, 48), 16),
                Long.parseUnsignedLong(hex.substring(48, 64), 16));
    }

    // ═══════════ 位访问 ═══════════

    /** 大端序分组读取（0 = 最高组含符号位，3 = 最低组）。 */
    public long getLong(final int group) {
        return switch (group) {
            case 0 -> l0;
            case 1 -> l1;
            case 2 -> l2;
            case 3 -> l3;
            default -> throw new IndexOutOfBoundsException("group must be 0..3");
        };
    }

    /** 32 字节大端序完整补码（与 getLong(0..3) 顺序一致）。 */
    public byte[] toBytes() {
        final byte[] result = new byte[32];
        for (int group = 0; group < 4; group++) {
            final long word = getLong(group);
            for (int index = 0; index < 8; index++) {
                result[group * 8 + index] = (byte) (word >>> (56 - index * 8));
            }
        }
        return result;
    }

    /** 无符号幅值的位长（0 表示零；MIN_VALUE 为 256）。 */
    public int bitLength() {
        return IntRefactorWords.bitLength(magnitudeWords());
    }

    /** 补码位模式第 index 位（0 = 最低位；越界 false）。 */
    public boolean testBit(final int index) {
        if (index < 0 || index >= 256) {
            return false;
        }
        final int group = index >>> 6;
        return (getLong(3 - group) & (1L << (index & 63))) != 0;
    }

    public boolean isNegative() {
        return l0 < 0L;
    }

    public boolean isZero() {
        return (l0 | l1 | l2 | l3) == 0L;
    }

    public int signum() {
        return isZero() ? 0 : isNegative() ? -1 : 1;
    }

    // ═══════════ 算术 ═══════════

    /** 加法（低 256 位回绕）。 */
    public IntRefactor256 add(final IntRefactor256 other) {
        Objects.requireNonNull(other);
        long r3 = l3 + other.l3;
        long carry = Long.compareUnsigned(r3, l3) < 0 ? 1L : 0L;

        // 结果比原组小表示无符号溢出；带入进位且结果相等时，另一组为全一，同样产生进位。
        long r2 = l2 + other.l2 + carry;
        carry = Long.compareUnsigned(r2, l2) < 0 || (carry != 0 && r2 == l2) ? 1L : 0L;

        long r1 = l1 + other.l1 + carry;
        carry = Long.compareUnsigned(r1, l1) < 0 || (carry != 0 && r1 == l1) ? 1L : 0L;

        long r0 = l0 + other.l0 + carry;

        return new IntRefactor256(r0, r1, r2, r3);
    }

    /** 减法（低 256 位回绕）。 */
    public IntRefactor256 subtract(final IntRefactor256 other) {
        Objects.requireNonNull(other);
        long r3 = l3 - other.l3;
        long borrow = Long.compareUnsigned(l3, other.l3) < 0 ? 1L : 0L;

        // 原组较小时产生借位；两组相等但带入借位时也必须继续向高组借位。
        long r2 = l2 - other.l2 - borrow;
        borrow = Long.compareUnsigned(l2, other.l2) < 0 || (borrow != 0 && l2 == other.l2) ? 1L : 0L;

        long r1 = l1 - other.l1 - borrow;
        borrow = Long.compareUnsigned(l1, other.l1) < 0 || (borrow != 0 && l1 == other.l1) ? 1L : 0L;

        long r0 = l0 - other.l0 - borrow;

        return new IntRefactor256(r0, r1, r2, r3);
    }

    /** 取负（MIN_VALUE 的负数回绕为自身）。 */
    public IntRefactor256 negate() {
        return ZERO.subtract(this);
    }

    /**
     * 乘法（低 256 位回绕）。补码乘法保留低 256 位时可按无符号分组累加部分积，
     * 无须另行处理操作数符号——每组汇总落在该位置的部分积，无符号加法溢出向下一组贡献一。
     */
    public IntRefactor256 multiply(final IntRefactor256 other) {
        Objects.requireNonNull(other);
        long r3 = l3 * other.l3;

        long r2 = Math.unsignedMultiplyHigh(l3, other.l3);
        long carry = 0L;
        long v = l3 * other.l2;
        long t = r2 + v;
        if (Long.compareUnsigned(t, r2) < 0) carry++;
        r2 = t;

        v = l2 * other.l3;
        t = r2 + v;
        if (Long.compareUnsigned(t, r2) < 0) carry++;
        r2 = t;

        long r1 = carry;
        carry = 0L;

        v = Math.unsignedMultiplyHigh(l3, other.l2);
        t = r1 + v;
        if (Long.compareUnsigned(t, r1) < 0) carry++;
        r1 = t;

        v = Math.unsignedMultiplyHigh(l2, other.l3);
        t = r1 + v;
        if (Long.compareUnsigned(t, r1) < 0) carry++;
        r1 = t;

        v = l3 * other.l1;
        t = r1 + v;
        if (Long.compareUnsigned(t, r1) < 0) carry++;
        r1 = t;

        v = l2 * other.l2;
        t = r1 + v;
        if (Long.compareUnsigned(t, r1) < 0) carry++;
        r1 = t;

        v = l1 * other.l3;
        t = r1 + v;
        if (Long.compareUnsigned(t, r1) < 0) carry++;
        r1 = t;

        // 仅累加会影响最高保留组的部分积，更高部分积与最终进位均位于低 256 位之外。
        long r0 = carry
                + Math.unsignedMultiplyHigh(l3, other.l1)
                + Math.unsignedMultiplyHigh(l2, other.l2)
                + Math.unsignedMultiplyHigh(l1, other.l3)
                + l3 * other.l0
                + l2 * other.l1
                + l1 * other.l2
                + l0 * other.l3;

        return new IntRefactor256(r0, r1, r2, r3);
    }

    /** 除法（截断向零；除零抛 ArithmeticException；MIN_VALUE / -1 回绕为 MIN_VALUE）。 */
    public IntRefactor256 divide(final IntRefactor256 other) {
        Objects.requireNonNull(other);
        if (other.isZero()) {
            throw new ArithmeticException("BigInteger divide by zero");
        }
        final long[] quotient = IntRefactorWords.divide(magnitudeWords(), other.magnitudeWords()).quotient();
        return fromMagnitude(isNegative() != other.isNegative(), quotient);
    }

    /** 余数（符号同被除数，截断向零；除零抛 ArithmeticException）。 */
    public IntRefactor256 remainder(final IntRefactor256 other) {
        Objects.requireNonNull(other);
        if (other.isZero()) {
            throw new ArithmeticException("BigInteger divide by zero");
        }
        final long[] remainder = IntRefactorWords.remainder(magnitudeWords(), other.magnitudeWords());
        return fromMagnitude(isNegative(), remainder);
    }

    // ═══════════ 移位与位运算（补码语义） ═══════════

    /**
     * 算术左移（保留低 256 位，回绕语义）。count ≥ 256 → ZERO；
     * count < 0 反向为右移（对齐 JDK BigInteger.shiftLeft 的负参语义）。
     */
    public IntRefactor256 shiftLeft(final int count) {
        if (count == 0) {
            return this;
        }
        if (count < 0) {
            return count == Integer.MIN_VALUE ? ZERO : shiftRight(-count);
        }
        if (count >= 256) {
            return ZERO;
        }
        final int whole = count >>> 6, bits = count & 63;
        // 小端工作排列：src[0]/dst[0] = 最低组（l3）；构造时反转回大端 l0..l3
        final long[] src = {l3, l2, l1, l0};
        final long[] dst = new long[4];
        for (int i = 0; i < 4; i++) {
            final int srcIdx = i - whole;
            long v = 0L;
            if (srcIdx >= 0) {
                v = src[srcIdx] << bits;
                if (bits != 0 && srcIdx - 1 >= 0) {
                    v |= src[srcIdx - 1] >>> (64 - bits);
                }
            }
            dst[i] = v;
        }
        return new IntRefactor256(dst[3], dst[2], dst[1], dst[0]);
    }

    /**
     * 算术右移（符号扩展）。count ≥ 256 → 负数为全一（-1）、非负为 ZERO；
     * count < 0 反向为左移。
     */
    public IntRefactor256 shiftRight(final int count) {
        if (count == 0) {
            return this;
        }
        if (count < 0) {
            return count == Integer.MIN_VALUE ? (isNegative() ? of(-1L) : ZERO) : shiftLeft(-count);
        }
        if (count >= 256) {
            return isNegative() ? of(-1L) : ZERO;
        }
        final int whole = count >>> 6, bits = count & 63;
        // 小端工作排列：src[0]/dst[0] = 最低组（l3）；构造时反转回大端 l0..l3
        final long[] src = {l3, l2, l1, l0};
        final long signFill = l0 < 0L ? -1L : 0L;
        final long[] dst = new long[4];
        for (int i = 0; i < 4; i++) {
            final int srcIdx = i + whole;
            long v = srcIdx < 4 ? src[srcIdx] >>> bits : signFill;
            if (bits != 0) {
                v |= srcIdx + 1 < 4 ? src[srcIdx + 1] << (64 - bits) : (signFill << (64 - bits));
            }
            dst[i] = v;
        }
        return new IntRefactor256(dst[3], dst[2], dst[1], dst[0]);
    }

    /** 按位与。 */
    public IntRefactor256 and(final IntRefactor256 other) {
        Objects.requireNonNull(other);
        return new IntRefactor256(l0 & other.l0, l1 & other.l1, l2 & other.l2, l3 & other.l3);
    }

    /** 按位或。 */
    public IntRefactor256 or(final IntRefactor256 other) {
        Objects.requireNonNull(other);
        return new IntRefactor256(l0 | other.l0, l1 | other.l1, l2 | other.l2, l3 | other.l3);
    }

    /** 按位异或。 */
    public IntRefactor256 xor(final IntRefactor256 other) {
        Objects.requireNonNull(other);
        return new IntRefactor256(l0 ^ other.l0, l1 ^ other.l1, l2 ^ other.l2, l3 ^ other.l3);
    }

    /** 按位取反。 */
    public IntRefactor256 not() {
        return new IntRefactor256(~l0, ~l1, ~l2, ~l3);
    }

    /** andNot（this & ~other）。 */
    public IntRefactor256 andNot(final IntRefactor256 other) {
        Objects.requireNonNull(other);
        return new IntRefactor256(l0 & ~other.l0, l1 & ~other.l1, l2 & ~other.l2, l3 & ~other.l3);
    }

    // ═══════════ 转换 ═══════════

    /** 截断低 64 位。 */
    public long toLong() {
        return l3;
    }

    /** 截断低 32 位。 */
    public int toInt() {
        return (int) l3;
    }

    /** 转换到 float（GRS 最近偶数舍入；|值| ≥ 2^128 必定上溢）。 */
    public float toFloat() {
        final boolean negative = l0 < 0L;
        long a0 = l0, a1 = l1, a2 = l2, a3 = l3;
        if (negative) {
            a3 = -l3;
            a2 = ~l2 + (l3 == 0L ? 1L : 0L);
            a1 = ~l1 + ((l3 | l2) == 0L ? 1L : 0L);
            a0 = ~l0 + ((l3 | l2 | l1) == 0L ? 1L : 0L);
        }
        // 幅值达到 2^128 时必定上溢；较小值仍须舍入后决定是否上溢。
        if ((a0 | a1) != 0L) {
            return negative ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
        }
        if ((a2 | a3) == 0L) {
            return 0.0f;
        }
        final long top = a2 != 0L ? a2 : a3;
        final long next = a2 != 0L ? a3 : 0L;
        final int offset = a2 != 0L ? 64 : 0;
        final int leading = Long.numberOfLeadingZeros(top);
        long normalized = top << leading;
        if (leading != 0) {
            normalized |= next >>> (64 - leading);
        }
        int significand = (int) (normalized >>> 40);
        final boolean guard = (normalized & (1L << 39)) != 0L;
        final boolean sticky = (normalized & ((1L << 39) - 1)) != 0L || (next << leading) != 0L;
        if (guard && (sticky || (significand & 1) != 0)) {
            significand++;
        }
        // 舍入后的 significand 最多为 2^24，可由 float 精确接收；二次幂缩放仅可能发生上溢。
        final float result = Math.scalb((float) significand, offset + 40 - leading);
        return negative ? -result : result;
    }

    /** 转换到 double（GRS 最近偶数舍入）。 */
    public double toDouble() {
        final boolean negative = l0 < 0L;
        long a0 = l0, a1 = l1, a2 = l2, a3 = l3;
        if (negative) {
            a3 = -l3;
            a2 = ~l2 + (l3 == 0L ? 1L : 0L);
            a1 = ~l1 + ((l3 | l2) == 0L ? 1L : 0L);
            a0 = ~l0 + ((l3 | l2 | l1) == 0L ? 1L : 0L);
        }
        if ((a0 | a1 | a2 | a3) == 0L) {
            return 0.0;
        }
        // top 是最高非零组，next 补充其后的有效位，rest 汇总更低组以判断舍入余量是否非零。
        final long top;
        final long next;
        final long rest;
        final int offset;
        if (a0 != 0L) {
            top = a0; next = a1; rest = a2 | a3; offset = 192;
        } else if (a1 != 0L) {
            top = a1; next = a2; rest = a3; offset = 128;
        } else if (a2 != 0L) {
            top = a2; next = a3; rest = 0L; offset = 64;
        } else {
            top = a3; next = 0L; rest = 0L; offset = 0;
        }
        // 将最高有效 bit 对齐至 bit 63，使 53 位保留值与后续舍入位的位置固定。
        final int leading = Long.numberOfLeadingZeros(top);
        long normalized = top << leading;
        if (leading != 0) {
            normalized |= next >>> (64 - leading);
        }
        long significand = normalized >>> 11;
        // guard 是第一位舍弃位，sticky 表示其后的舍弃位是否存在一，包括未并入 normalized 的低位。
        final boolean guard = (normalized & (1L << 10)) != 0L;
        final boolean sticky = (normalized & 0x3ffL) != 0L || (next << leading) != 0L || rest != 0L;
        // 大于半单位时进位；恰好半单位时只对奇数进位，使保留结果为偶数。
        if (guard && (sticky || (significand & 1L) != 0L)) {
            significand++;
        }
        final double result = Math.scalb((double) significand, offset + 11 - leading);
        return negative ? -result : result;
    }

    /** 十进制字符串（负号 + 无符号幅值分解）。 */
    @Override
    public String toString() {
        return toBigInteger().toString();
    }

    /** 64 位十六进制原始位模式（含符号位，无 0x 前缀）。 */
    public String toHexString() {
        return "%016x%016x%016x%016x".formatted(l0, l1, l2, l3);
    }

    /** 转到 JDK BigInteger（大端补码，精确）。 */
    public BigInteger toBigInteger() {
        return new BigInteger(toBytes());
    }

    // ═══════════ 比较 / 相等 / 哈希 ═══════════

    /**
     * 有符号数值全序比较。最高组决定符号，其余组仅表示非负位权，须按无符号顺序逐组比较。
     */
    @Override
    public int compareTo(final IntRefactor256 other) {
        int cmp = Long.compare(l0, other.l0);
        if (cmp != 0) {
            return cmp;
        }
        cmp = Long.compareUnsigned(l1, other.l1);
        if (cmp != 0) {
            return cmp;
        }
        cmp = Long.compareUnsigned(l2, other.l2);
        if (cmp != 0) {
            return cmp;
        }
        return Long.compareUnsigned(l3, other.l3);
    }

    @Override
    public boolean equals(final Object obj) {
        return obj instanceof IntRefactor256 other
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

    // ═══════════ 内部 ═══════════
    
    /** 包内：从低组前无符号幅值组装补码（FloatRefactor256.toIntRefactor256 用）。 */
    static IntRefactor256 fromMagnitudeWordsPublic(final boolean negative, final long[] words) {
        return fromMagnitude(negative, words);
    }

    /** 无符号幅值（低组前 long[4]；MIN_VALUE 的幅值 2^255 = {0,0,0,MIN}，四组内可表示）。 */
    private long[] magnitudeWords() {
        if (!isNegative()) {
            return new long[] {l3, l2, l1, l0};
        }
        // 补码取负（手写展开进位链）。
        final long a3 = -l3;
        final long a2 = ~l2 + (l3 == 0L ? 1L : 0L);
        final long a1 = ~l1 + ((l3 | l2) == 0L ? 1L : 0L);
        final long a0 = ~l0 + ((l3 | l2 | l1) == 0L ? 1L : 0L);
        return new long[] {a3, a2, a1, a0};
    }

    /** 从低组前无符号幅值组装补码（负数取补码；低组全零时才向高组传递加一进位）。 */
    private static IntRefactor256 fromMagnitude(final boolean negative, final long[] words) {
        long a3 = IntRefactorWords.word(words, 0);
        long a2 = IntRefactorWords.word(words, 1);
        long a1 = IntRefactorWords.word(words, 2);
        long a0 = IntRefactorWords.word(words, 3);
        if (negative) {
            final long b3 = a3, b2 = a2, b1 = a1;
            a3 = -b3;
            a2 = ~b2 + (b3 == 0L ? 1L : 0L);
            a1 = ~b1 + ((b3 | b2) == 0L ? 1L : 0L);
            a0 = ~a0 + ((b3 | b2 | b1) == 0L ? 1L : 0L);
        }
        return new IntRefactor256(a0, a1, a2, a3);
    }
}