package net.MinecraftTools.Math._256Bit;

import net.MinecraftTools.Math.DynamicAccuracy.BigInteger;
import net.MinecraftTools.Math.DynamicAccuracy.BigDecimal;

import java.util.Arrays;
import java.util.Objects;

/**
 * Int256 — 有符号 256-bit 整数（补码）
 *
 * <p>内部: long[4] = {hiHi, hiLo, loHi, loLo}，最高位 = 符号 范围: [-2^255, 2^255 - 1] 零 GC，全 long 运算，无
 * BigInteger 依赖（除 toString）
 *
 * <p>INF32768 / MCRe NoiseFarlands 项目
 */
public final class Int256 extends Number implements Comparable<Int256> {

    // ──────── 内部存储 (immutable) ────────
    final long a; // bits 255..192
    final long b; // bits 191..128
    final long c; // bits 127..64
    final long d; // bits 63..0

    // ──────── 缓存 ────────
    private transient Int256 cachedNegate;
    private transient BigInteger cachedBigInteger;
    private transient int hash;
    private static final int HASH_UNCACHED = Integer.MIN_VALUE;

    // ──────── 常量 ────────
    public static final Int256 ZERO = new Int256(0L, 0L, 0L, 0L);
    public static final Int256 ONE = new Int256(0L, 0L, 0L, 1L);
    public static final Int256 TWO = new Int256(0L, 0L, 0L, 2L);
    public static final Int256 THREE = new Int256(0L, 0L, 0L, 3L);
    public static final Int256 FOUR = new Int256(0L, 0L, 0L, 4L);
    public static final Int256 FIVE = new Int256(0L, 0L, 0L, 5L);
    public static final Int256 TEN = new Int256(0L, 0L, 0L, 10L);
    public static final Int256 MINUS_ONE = new Int256(-1L, -1L, -1L, -1L);
    public static final Int256 NEG_ONE = MINUS_ONE;

    // ──────── 内联工具 ────────
    private static final long LONG_MASK = 0xFFFF_FFFF_FFFF_FFFFL;

    // ──────── 小值缓存 (-128..127) ────────
    private static final Int256[] SMALL = new Int256[256];
    private static final int SMALL_OFF = 128;

    static {
        for (int i = -128; i <= 127; i++) {
            long fill = (i < 0) ? -1L : 0L;
            SMALL[i + SMALL_OFF] = new Int256(fill, fill, fill, (long) i);
        }
    }

    // ──────── 构造（私有，不可变）────────
    private Int256(long a, long b, long c, long d) {
        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
        this.hash = HASH_NOT_CACHED;
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
        // 通过 BigDecimal 转换保持精度
        return toBigDecimal().doubleValue();
    }

    /** 转 BigDecimal（内部用） */
    private BigDecimal toBigDecimal() {
        if (cachedBigDecimal != null) return cachedBigDecimal;
        return cachedBigDecimal = new BigDecimal(toBigInteger());
    }

    private transient BigDecimal cachedBigDecimal;

    // ──────── 工厂 ────────

    /** 从 long（符号扩展） */
    public static Int256 of(long value) {
        if (value >= -128 && value <= 127) return SMALL[(int) value + SMALL_OFF];
        long fill = (value < 0) ? -1L : 0L;
        return new Int256(fill, fill, fill, value);
    }

    /** 从 4 个无符号 long（[0] 最高） */
    public static Int256 of(long w0, long w1, long w2, long w3) {
        return new Int256(w0, w1, w2, w3);
    }

    /** 大端 32 字节 */
    public static Int256 of(byte[] bytes) {
        if (bytes.length != 32) throw new IllegalArgumentException("need 32 bytes");
        return new Int256(aggregate(bytes, 0), aggregate(bytes, 8),
        aggregate(bytes, 16), aggregate(bytes, 24));
    }

    private static long aggregate(byte[] b, int off) {
        return ((long) (b[off] & 0xFF) << 56) | ((long) (b[off + 1] & 0xFF) << 48)
                | ((long) (b[off + 2] & 0xFF) << 40) | ((long) (b[off + 3] & 0xFF) << 32)
                | ((long) (b[off + 4] & 0xFF) << 24) | ((long) (b[off + 5] & 0xFF) << 16)
                | ((long) (b[off + 6] & 0xFF) << 8) | (long) (b[off + 7] & 0xFF);
    }

    // ──────── 核心运算 ────────

    /* ==================== 加法 ==================== */
    public Int256 add(Int256 o) {
        long s0 = a + o.a;
        long c0 = (unsignedLessThan(s0, a) ? 1L : 0L);
        long s1 = b + o.b + c0;
        long c1 = (c0 != 0 && unsignedLessOrEqual(s1, b)) || (c0 == 0 && unsignedLessThan(s1, b)) ? 1L : 0L;
        long s2 = c + o.c + c1;
        long c2 = (c1 != 0 && unsignedLessOrEqual(s2, c)) || (c1 == 0 && unsignedLessThan(s2, c)) ? 1L : 0L;
        long s3 = d + o.d + c2;
        return new Int256(s0, s1, s2, s3);
    }

    /* ==================== 减法 ==================== */
    public Int256 subtract(Int256 o) {
        long s0 = a - o.a;
        long b0 = unsignedGreaterThan(s0, a) ? 1L : 0L;
        long s1 = b - o.b - b0;
        long b1 = (b0 != 0 && unsignedGreaterOrEqual(s1, b)) || (b0 == 0 && unsignedGreaterThan(s1, b)) ? 1L : 0L;
        long s2 = c - o.c - b1;
        long b2 = (b1 != 0 && unsignedGreaterOrEqual(s2, c)) || (b1 == 0 && unsignedGreaterThan(s2, c)) ? 1L : 0L;
        long s3 = d - o.d - b2;
        return new Int256(s0, s1, s2, s3);
    }

    /* ==================== 乘法 ==================== */
    public Int256 multiply(Int256 o) {
        // Karatsuba 启发 — 分 4 块
        long x0 = d;
        long x1 = c;
        long x2 = b;
        long x3 = a;
        long y0 = o.d;
        long y1 = o.c;
        long y2 = o.b;
        long y3 = o.a;

        // 乘积的低 4 字（256 bit）
        long p0, p1, p2, p3;
        long carry;

        // dword 0: x0*y0
        p0 = x0 * y0;
        long hi0 = Math.unsignedMultiplyHigh(x0, y0);

        // dword 1: x0*y1 + x1*y0 + hi0
        long sum1 = hi0;
        sum1 = unsignedAdd(sum1, x0 * y1);
        sum1 = unsignedAdd(sum1, x1 * y0);
        p1 = sum1;
        long hi1 = (sum1 < hi0 ? 1L : 0L) + Math.unsignedMultiplyHigh(x0, y1) + Math.unsignedMultiplyHigh(x1, y0);

        // dword 2: x0*y2 + x1*y1 + x2*y0 + hi1
        long sum2 = hi1;
        sum2 = unsignedAdd(sum2, x0 * y2);
        sum2 = unsignedAdd(sum2, x1 * y1);
        sum2 = unsignedAdd(sum2, x2 * y0);
        p2 = sum2;
        long hi2 = Math.unsignedMultiplyHigh(x0, y2) + Math.unsignedMultiplyHigh(x1, y1) + Math.unsignedMultiplyHigh(x2, y0);

        // dword 3: x0*y3 + x1*y2 + x2*y1 + x3*y0 + hi2
        long sum3 = hi2;
        sum3 = unsignedAdd(sum3, x0 * y3);
        sum3 = unsignedAdd(sum3, x1 * y2);
        sum3 = unsignedAdd(sum3, x2 * y1);
        sum3 = unsignedAdd(sum3, x3 * y0);
        p3 = sum3;

        return new Int256(p3, p2, p1, p0);
    }

    /* ==================== 除法 ==================== */
    public Int256 divide(Int256 divisor) {
        if (divisor.isZero()) throw new ArithmeticException("/ by zero");
        if (this.isZero()) return ZERO;
        if (divisor.equals(ONE)) return this;
        if (divisor.equals(MINUS_ONE)) return this.negate();

        boolean neg = (signum() < 0) ^ (divisor.signum() < 0);
        Int256 num = this.abs();
        Int256 den = divisor.abs();

        if (num.compareTo(den) < 0) return ZERO;

        // 二分搜索商
        Int256 low = ZERO;
        Int256 high = num;
        while (low.compareTo(high) <= 0) {
            Int256 mid = low.add(high).shiftRight(1);
            Int256 prod = mid.multiply(den);
            int cmp = prod.compareTo(num);
            if (cmp == 0) {
                return neg ? mid.negate() : mid;
            } else if (cmp < 0) {
                low = mid.add(ONE);
            } else {
                high = mid.subtract(ONE);
            }
        }
        return neg ? high.negate() : high;
    }

    public Int256 remainder(Int256 divisor) {
        Int256 q = this.divide(divisor);
        return this.subtract(q.multiply(divisor));
    }

    /* ==================== 取负数 ==================== */
    public Int256 negate() {
        if (cachedNegate != null) return cachedNegate;
        Int256 not = new Int256(~a, ~b, ~c, ~d);
        cachedNegate = not.add(ONE);
        return cachedNegate;
    }

    /* ==================== 绝对值 ==================== */
    public Int256 abs() {
        return isNegative() ? negate() : this;
    }

    /* ==================== 移位 ==================== */
    public Int256 shiftLeft(int n) {
        if (n == 0) return this;
        if (n < 0) return shiftRight(-n);
        if (n >= 256) return ZERO;

        int w = n / 64;
        int b = n % 64;
        if (b == 0) {
            return switch (w) {
                case 0 -> this;
                case 1 -> new Int256(b, c, d, 0L);
                case 2 -> new Int256(c, d, 0L, 0L);
                case 3 -> new Int256(d, 0L, 0L, 0L);
                default -> ZERO;
            };
        }
        int r = 64 - b;
        long[] arr = {0L, 0L, 0L, 0L};
        long[] src = {a, b, c, d};
        for (int i = 0; i < 4 - w; i++) {
            arr[i] = src[i + w] << b;
            if (i + w + 1 < 4) arr[i] |= src[i + w + 1] >>> r;
        }
        return new Int256(arr[0], arr[1], arr[2], arr[3]);
    }

    public Int256 shiftRight(int n) {
        if (n == 0) return this;
        if (n < 0) return shiftLeft(-n);
        if (n >= 256) return isNegative() ? MINUS_ONE : ZERO;

        long fill = isNegative() ? -1L : 0L;
        int w = n / 64;
        int b = n % 64;
        if (b == 0) {
            return switch (w) {
                case 0 -> this;
                case 1 -> new Int256(fill, a, b, c);
                case 2 -> new Int256(fill, fill, a, b);
                case 3 -> new Int256(fill, fill, fill, a);
                default -> new Int256(fill, fill, fill, fill);
            };
        }
        int l = 64 - b;
        long[] src = {a, b, c, d};
        long[] arr = new long[4];
        for (int i = 0; i < 4; i++) {
            long high = (i < w) ? fill : src[i - w];
            long low = (i + 1 - w) < 4 ? src[i + 1 - w] : fill;
            arr[i] = (high >>> b) | (low << l);
        }
        return new Int256(arr[0], arr[1], arr[2], arr[3]);
    }

    /* ==================== 位运算 ==================== */
    public Int256 and(Int256 o) {
        return new Int256(a & o.a, b & o.b, c & o.c, d & o.d);
    }

    public Int256 or(Int256 o) {
        return new Int256(a | o.a, b | o.b, c | o.c, d | o.d);
    }

    public Int256 xor(Int256 o) {
        return new Int256(a ^ o.a, b ^ o.b, c ^ o.c, d ^ o.d);
    }

    public Int256 not() {
        return new Int256(~a, ~b, ~c, ~d);
    }

    /* ==================== 比较 ==================== */
    @Override
    public int compareTo(Int256 o) {
        if (unsignedCmp(a, o.a) != 0) return a < o.a ? -1 : 1;
        if (unsignedCmp(b, o.b) != 0) return b < o.b ? -1 : 1;
        if (unsignedCmp(c, o.c) != 0) return c < o.c ? -1 : 1;
        if (unsignedCmp(d, o.d) != 0) return d < o.d ? -1 : 1;
        return 0;
    }

    public boolean isNegative() {
        return a < 0;
    }

    public boolean isZero() {
        return a == 0 && b == 0 && c == 0 && d == 0;
    }

    public int signum() {
        return isZero() ? 0 : (a < 0 ? -1 : 1);
    }

    /* ==================== 位判断 ==================== */
    public boolean testBit(int n) {
        return (component(n / 64) & (1L << (n & 63))) != 0;
    }

    public int bitLength() {
        if (isZero()) return 0;
        if (isNegative()) {
            // 补码负数：取反后的 bitLength
            Int256 abs = negate();
            if (abs.a != 0) return 256 - Long.numberOfLeadingZeros(abs.a);
            if (abs.b != 0) return 192 - Long.numberOfLeadingZeros(abs.b);
            if (abs.c != 0) return 128 - Long.numberOfLeadingZeros(abs.c);
            return 64 - Long.numberOfLeadingZeros(abs.d);
        }
        // 正数
        if (a != 0) return 256 - Long.numberOfLeadingZeros(a);
        if (b != 0) return 192 - Long.numberOfLeadingZeros(b);
        if (c != 0) return 128 - Long.numberOfLeadingZeros(c);
        return 64 - Long.numberOfLeadingZeros(d);
    }

    /* ==================== 转换 ==================== */
    public long longValue() {
        if (a == 0L && b == 0L && c == 0L && d >= 0) return d;
        if (a == -1L && b == -1L && c == -1L) return d;
        throw new ArithmeticException("Int256 out of long range");
    }

    public BigInteger toBigInteger() {
        if (cachedBigInteger != null) return cachedBigInteger;
        if (a == 0L && b == 0L && c == 0L && d >= 0) {
            return cachedBigInteger = BigInteger.valueOf(d);
        }
        return cachedBigInteger = new BigInteger(toByteArray());
    }

    public byte[] toByteArray() {
        byte[] buf = new byte[32];
        putLong(buf, 0, a);
        putLong(buf, 8, b);
        putLong(buf, 16, c);
        putLong(buf, 24, d);
        return buf;
    }

    private static void putLong(byte[] arr, int off, long val) {
        arr[off] = (byte) (val >>> 56);
        arr[off + 1] = (byte) (val >>> 48);
        arr[off + 2] = (byte) (val >>> 40);
        arr[off + 3] = (byte) (val >>> 32);
        arr[off + 4] = (byte) (val >>> 24);
        arr[off + 5] = (byte) (val >>> 16);
        arr[off + 6] = (byte) (val >>> 8);
        arr[off + 7] = (byte) (val);
    }

    @Override
    public String toString() {
        return toBigInteger().toString();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Int256 other)) return false;
        return a == other.a && b == other.b && c == other.c && d == other.d;
    }

    @Override
    public int hashCode() {
        if (hash == HASH_NOT_CACHED) hash = (int) (a ^ b ^ c ^ d);
        return hash;
    }

    // ──────────── 内联算术助手 ────────────
    private static boolean unsignedLessThan(long x, long y) {
        return Long.compareUnsigned(x, y) < 0;
    }

    private static boolean unsignedLessOrEqual(long x, long y) {
        return Long.compareUnsigned(x, y) >= 0;
    }

    private static boolean unsignedGreaterThan(long x, long y) {
        return Long.compareUnsigned(x, y) > 0;
    }

    private static boolean unsignedGreaterOrEqual(long x, long y) {
        return Long.compareUnsigned(x, y) >= 0;
    }

    private static int unsignedCmp(long x, long y) {
        return Long.compareUnsigned(x, y);
    }

    private static long unsignedAdd(long a, long b) {
        long sum = a + b;
        return sum;
    }

    // ══════════════════════ 测试 ══════════════════════
    public static void main(String[] args) {
        System.out.println("=== Int256 测试 ===");
        System.out.println("ZERO = " + ZERO);
        System.out.println("ONE  = " + ONE);
        System.out.println("MAX_LONG + 1 = " + of(Long.MAX_VALUE).add(ONE));
        System.out.println("2 * MAX_LONG = " + of(Long.MAX_VALUE).multiply(TWO));
        System.out.println("(2^128) = " + ONE.shiftLeft(128));
        System.out.println("(2^128)/2 = " + ONE.shiftLeft(128).divide(TWO));
    }
}