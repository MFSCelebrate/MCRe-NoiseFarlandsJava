package net.MinecraftTools.Math._256Bit;

import net.MinecraftTools.Math.DynamicAccuracy.BigInteger;
import net.MinecraftTools.Math.DynamicAccuracy.BigDecimal;

import java.util.Objects;

/**
 * UInt256 — 无符号 256-bit 整数
 *
 * <p>内部: long[4] = {hiHi, hiLo, loHi, loLo}，纯无符号解释 范围: [0, 2^256 - 1] 零 GC，全 long 运算，无 BigInteger
 * 依赖（除 toString）
 *
 * <p>用途: 块坐标映射、哈希、距离计算、无符号噪声
 *
 * <p>INF32768 / MCRe NoiseFarlands 项目
 */
public final class UInt256 extends Number implements Comparable<UInt256> {

    // ──────── 内部存储 (immutable) ────────
    final long a; // bits 255..192
    final long b; // bits 191..128
    final long c; // bits 127..64
    final long d; // bits 63..0

    // ──────── 缓存 ────────
    private transient BigInteger cachedBigInteger;
    private transient int hash;
    private static final int HASH_UNCACHED = Integer.MIN_VALUE;

    // ──────── 常量 ────────
    public static final UInt256 ZERO = new UInt256(0L, 0L, 0L, 0L);
    public static final UInt256 ONE = new UInt256(0L, 0L, 0L, 1L);
    public static final UInt256 TWO = new UInt256(0L, 0L, 0L, 2L);
    public static final UInt256 THREE = new UInt256(0L, 0L, 0L, 3L);
    public static final UInt256 FOUR = new UInt256(0L, 0L, 0L, 4L);
    public static final UInt256 FIVE = new UInt256(0L, 0L, 0L, 5L);
    public static final UInt256 TEN = new UInt256(0L, 0L, 0L, 10L);
    public static final UInt256 MAX_VALUE = new UInt256(-1L, -1L, -1L, -1L);

    // ──────── 小值缓存 ────────
    private static final UInt256[] SMALL = new UInt256[256];

    static {
        for (int i = 0; i < 256; i++) {
            SMALL[i] = new UInt256(0L, 0L, 0L, (long) i);
        }
    }

    // ──────── 构造（私有）────────
    private UInt256(long a, long b, long c, long d) {
        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
        this.hash = HASH_UNCACHED;
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

    // ──────── 工厂 ────────

    /** 从无符号 long（零扩展） */
    public static UInt256 of(long value) {
        if (value >= 0 && value < 256) return SMALL[(int) value];
        return new UInt256(0L, 0L, 0L, value);
    }

    /** 从 4 个无符号 long（[0] 最高） */
    public static UInt256 of(long a, long b, long c, long d) {
        return new UInt256(a, b, c, d);
    }

    /** 大端 32 字节 */
    public static UInt256 of(byte[] bytes) {
        if (bytes.length != 32) throw new IllegalArgumentException("need 32 bytes");
        return new UInt256(
        ((long) (bytes[0] & 0xFF) << 56) | ((long) (bytes[1] & 0xFF) << 48)
                | ((long) (bytes[2] & 0xFF) << 40) | ((long) (bytes[3] & 0xFF) << 32)
                | ((long) (bytes[4] & 0xFF) << 24) | ((long) (bytes[5] & 0xFF) << 16)
                | ((long) (bytes[6] & 0xFF) << 8) | (long) (bytes[7] & 0xFF),
        ((long) (bytes[8] & 0xFF) << 56) | ((long) (bytes[9] & 0xFF) << 48)
                | ((long) (bytes[10] & 0xFF) << 40) | ((long) (bytes[11] & 0xFF) << 32)
                | ((long) (bytes[12] & 0xFF) << 24) | ((long) (bytes[13] & 0xFF) << 16)
                | ((long) (bytes[14] & 0xFF) << 8) | (long) (bytes[15] & 0xFF),
        ((long) (bytes[16] & 0xFF) << 56) | ((long) (bytes[17] & 0xFF) << 48)
                | ((long) (bytes[18] & 0xFF) << 40) | ((long) (bytes[19] & 0xFF) << 32)
                | ((long) (bytes[20] & 0xFF) << 24) | ((long) (bytes[21] & 0xFF) << 16)
                | ((long) (bytes[22] & 0xFF) << 8) | (long) (bytes[23] & 0xFF),
        ((long) (bytes[24] & 0xFF) << 56) | ((long) (bytes[25] & 0xFF) << 48)
                | ((long) (bytes[26] & 0xFF) << 40) | ((long) (bytes[27] & 0xFF) << 32)
                | ((long) (bytes[28] & 0xFF) << 24) | ((long) (bytes[29] & 0xFF) << 16)
                | ((long) (bytes[30] & 0xFF) << 8) | (long) (bytes[31] & 0xFF));
    }

    /** 从 Int256 按位模式转换（符号位变数值） */
    public static UInt256 fromInt256(Int256 val) {
        return new UInt256(val.a, val.b, val.c, val.d);
    }

    // ──────── 核心运算 ────────

    /* ==================== 加法 ==================== */
    public UInt256 add(UInt256 o) {
        long d0 = d + o.d;
        long c0 = (Long.compareUnsigned(d0, d) < 0) ? 1L : 0L;
        long c1 = c + o.c + c0;
        long c2 = (c0 != 0 && Long.compareUnsigned(c1, c) <= 0) ||
                        (c0 == 0 && Long.compareUnsigned(c1, c) < 0) ? 1L : 0L;
        long b1 = b + o.b + c2;
        long c3 = (c2 != 0 && Long.compareUnsigned(b1, b) <= 0) ||
                        (c2 == 0 && Long.compareUnsigned(b1, b) < 0) ? 1L : 0L;
        long a1 = a + o.a + c3;
        return new UInt256(a1, b1, c1, d0);
    }

    /* ==================== 减法 ==================== */
    public UInt256 subtract(UInt256 o) {
        long d0 = d - o.d;
        long b0 = Long.compareUnsigned(d, o.d) < 0 ? 1L : 0L;
        long c1 = c - o.c - b0;
        long b1 = (b0 != 0 && Long.compareUnsigned(c, c1) <= 0) ||
                        (b0 == 0 && Long.compareUnsigned(c, c1) < 0) ? 1L : 0L;
        long b1val = b - o.b - b1;
        long b2 = (b1 != 0 && Long.compareUnsigned(b, b1val) <= 0) ||
                        (b1 == 0 && Long.compareUnsigned(b, b1val) < 0) ? 1L : 0L;
        long a1 = a - o.a - b2;
        return new UInt256(a1, b1val, c1, d0);
    }

    /* ==================== 乘法 ==================== */
    public UInt256 multiply(UInt256 o) {
        // 与 Int256 相同——无符号乘法的低 256 bit 天然正确
        long p0 = d * o.d;
        long hi0 = Math.unsignedMultiplyHigh(d, o.d);

        long sum1 = hi0;
        sum1 += d * o.c;
        sum1 += c * o.d;
        long p1 = sum1;
        long hi1 = Math.unsignedMultiplyHigh(d, o.c) + Math.unsignedMultiplyHigh(c, o.d)
                + (Long.compareUnsigned(sum1, hi0) < 0 ? 1L : 0L);

        long sum2 = hi1;
        sum2 += d * o.b;
        sum2 += c * o.c;
        sum2 += b * o.d;
        long p2 = sum2;
        long hi2 = Math.unsignedMultiplyHigh(d, o.b) + Math.unsignedMultiplyHigh(c, o.c)
                + Math.unsignedMultiplyHigh(b, o.d);

        long sum3 = hi2;
        sum3 += d * o.a;
        sum3 += c * o.b;
        sum3 += b * o.c;
        sum3 += a * o.d;
        long p3 = sum3;

        return new UInt256(p3, p3_hi, p2, p1, p0);
    }

    /* ==================== 除法 ==================== */
    public UInt256 divide(UInt256 divisor) {
        if (divisor.isZero()) throw new ArithmeticException("/ by zero");
        if (this.isZero()) return ZERO;
        if (divisor.equals(ONE)) return this;
        if (this.compareTo(divisor) < 0) return ZERO;

        // 二分搜索
        UInt256 low = ZERO;
        UInt256 high = this;
        while (low.compareTo(high) <= 0) {
            UInt256 mid = low.add(high).shiftRight(1);
            UInt256 prod = mid.multiply(divisor);
            int cmp = prod.compareTo(this);
            if (cmp == 0) return mid;
            else if (cmp < 0) low = mid.add(ONE);
            else high = mid.subtract(ONE);
        }
        return high;
    }

    public UInt256 remainder(UInt256 divisor) {
        return this.subtract(this.divide(divisor).multiply(divisor));
    }

    /* ==================== 移位 ==================== */
    public UInt256 shiftLeft(int n) {
        if (n == 0) return this;
        if (n >= 256) return ZERO;
        if (n < 0) return shiftRight(-n);
        int w = n / 64;
        int b = n % 64;
        if (b == 0) {
            return switch (w) {
                case 0 -> this;
                case 1 -> new UInt256(b, c, d, 0L);
                case 2 -> new UInt256(c, d, 0L, 0L);
                case 3 -> new UInt256(d, 0L, 0L, 0L);
                default -> ZERO;
            };
        }
        int r = 64 - b;
        long[] arr = new long[4];
        long[] src = {a, b, c, d};
        for (int i = 0; i < 4 - w; i++) {
            arr[i] = src[i + w] << b;
            if (i + w + 1 < 4) arr[i] |= src[i + w + 1] >>> r;
        }
        return new UInt256(arr[0], arr[1], arr[2], arr[3]);
    }

    public UInt256 shiftRight(int n) {
        if (n == 0) return this;
        if (n >= 256) return ZERO;
        if (n < 0) return shiftLeft(-n);
        int w = n / 64;
        int b = n % 64;
        if (b == 0) {
            return switch (w) {
                case 0 -> this;
                case 1 -> new UInt256(0L, a, b, c);
                case 2 -> new UInt256(0L, 0L, a, b);
                case 3 -> new UInt256(0L, 0L, 0L, a);
                default -> ZERO;
            };
        }
        int l = 64 - b;
        long[] src = {a, b, c, d};
        long[] arr = new long[4];
        for (int i = 0; i < 4; i++) {
            long high = (i < w) ? 0L : src[i - w];
            long low = (i + 1 - w) < 4 ? src[i + 1 - w] : 0L;
            arr[i] = (high >>> b) | (low << l);
        }
        return new UInt256(arr[0], arr[1], arr[2], arr[3]);
    }

    /* ==================== 位运算 ==================== */
    public UInt256 and(UInt256 o) {
        return new UInt256(a & o.a, b & o.b, c & o.c, d & o.d);
    }

    public UInt256 or(UInt256 o) {
        return new UInt256(a | o.a, b | o.b, c | o.c, d | o.d);
    }

    public UInt256 xor(UInt256 o) {
        return new UInt256(a ^ o.a, b ^ o.b, c ^ o.c, d ^ o.d);
    }

    public UInt256 not() {
        return new UInt256(~a, ~b, ~c, ~d);
    }

    /* ==================== 比较 ==================== */
    @Override
    public int compareTo(UInt256 o) {
        int c = Long.compareUnsigned(a, o.a);
        if (c != 0) return c;
        c = Long.compareUnsigned(b, o.b);
        if (c != 0) return c;
        c = Long.compareUnsigned(c, o.c);
        if (c != 0) return c;
        return Long.compareUnsigned(d, o.d);
    }

    public boolean isZero() {
        return a == 0 && b == 0 && c == 0 && d == 0;
    }

    /* ==================== 转换 ==================== */
    public long longValue() {
        if (a == 0 && b == 0 && c == 0 && d >= 0) return d;
        throw new ArithmeticException("UInt256 out of long range");
    }

    public Int256 toInt256() {
        return Int256.of(a, b, c, d);
    }

    public BigInteger toBigInteger() {
        if (cachedBigInteger != null) return cachedBigInteger;
        if (a == 0L && b == 0L && c == 0L && d >= 0) {
            return cachedBigInteger = BigInteger.valueOf(d);
        }
        return cachedBigInteger = new BigInteger(1, toByteArray());
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
        if (!(o instanceof UInt256 other)) return false;
        return a == other.a && b == other.b && c == other.c && d == other.d;
    }

    @Override
    public int hashCode() {
        if (hash == HASH_UNCACHED) hash = (int) (a ^ b ^ c ^ d);
        return hash;
    }

    // ══════════════════════ 测试 ══════════════════════
    public static void main(String[] args) {
        System.out.println("=== UInt256 测试 ===");
        System.out.println("ZERO = " + ZERO);
        System.out.println("MAX  = " + MAX_VALUE);
        System.out.println("MAX + 1 = " + MAX_VALUE.add(ONE));
        System.out.println("2^128   = " + ONE.shiftLeft(128));
        System.out.println("2^128/2 = " + ONE.shiftLeft(128).divide(TWO));
        System.out.println("(2^128 - 1) * 2 = " + MAX_VALUE.shiftRight(128).multiply(TWO));
        System.out.println("1000 * 1000 = " + UInt256.of(1000).multiply(UInt256.of(1000)));
    }
}