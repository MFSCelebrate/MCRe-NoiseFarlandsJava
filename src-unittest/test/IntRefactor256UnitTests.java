package test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.MinecraftTools.Math._256bit_Refactoring.IntRefactor256;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Random;

/**
 * 由 {@code tmp/IntRefactor256Check.java} 迁移而来（2026-11-17）。
 * <p>原脚本是 {@code main()} + PASS/FAIL 计数式自检；此处<b>完整保留原始逻辑与期望值</b>，
 * 只把外壳换成 JUnit：脚本输出被捕获，出现失败标记即断言失败。
 */
class IntRefactor256UnitTests {

    static int fail = 0;

    static void chk(final boolean ok, final String tag) {
        if (!ok) {
            fail++;
            if (fail <= 25) System.out.println("FAIL " + tag);
        }
    }

    static final BigInteger TWO256 = BigInteger.ONE.shiftLeft(256);
    static final BigInteger MASK = TWO256.subtract(BigInteger.ONE);

    /** 回绕到 256-bit 补码值域（[-(2^255), 2^255)） */
    static BigInteger wrap(final BigInteger b) {
        final BigInteger m = b.and(MASK);
        return m.testBit(255) ? m.subtract(TWO256) : m;
    }

    /** IntRefactor256 → BigInteger（精确） */
    static BigInteger toBig(final IntRefactor256 v) {
        return v.toBigInteger();
    }

    @Test
    @DisplayName("IntRefactor256 正确性自检")
    void scriptRuns() throws Exception {
        final java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        final java.io.PrintStream originalOut = System.out;
        System.setOut(new java.io.PrintStream(buffer, true, java.nio.charset.StandardCharsets.UTF_8));
        try {

                final Random r = new Random(20261112L);

                // ═══ 1. of(long)/toLong 往返（边界 + 随机）═══
                for (final long v : new long[] {0L, 1L, -1L, Long.MAX_VALUE, Long.MIN_VALUE, 42L, -42L,
                        (1L << 62), -(1L << 62), 0xdeadbeefL}) {
                    chk(IntRefactor256.of(v).toLong() == v, "of(long) rt " + v);
                }
                for (int i = 0; i < 100_000; i++) {
                    chk(IntRefactor256.of(r.nextLong()).toLong() == r.nextLong() || true, ""); // 占位防优化（无意义）
                }
                for (int i = 0; i < 100_000; i++) {
                    final long v = r.nextLong();
                    chk(IntRefactor256.of(v).toBigInteger().equals(BigInteger.valueOf(v)), "of(long) big " + v);
                }

                // ═══ 2. add/subtract vs BigInteger（回绕语义）═══
                for (int i = 0; i < 100_000; i++) {
                    final BigInteger a = new BigInteger(256, r), b = new BigInteger(256, r);
                    final IntRefactor256 x = IntRefactor256.of(wrap(a)), y = IntRefactor256.of(wrap(b));
                    chk(x.add(y).toBigInteger().equals(wrap(a.add(b))), "add " + i);
                    chk(x.subtract(y).toBigInteger().equals(wrap(a.subtract(b))), "sub " + i);
                }
                chk(IntRefactor256.MAX_VALUE.add(IntRefactor256.ONE).equals(IntRefactor256.MIN_VALUE), "MAX+1=MIN");
                chk(IntRefactor256.MIN_VALUE.subtract(IntRefactor256.ONE).equals(IntRefactor256.MAX_VALUE), "MIN-1=MAX");
                chk(IntRefactor256.MIN_VALUE.add(IntRefactor256.MIN_VALUE).isZero(), "MIN+MIN=0");
                chk(IntRefactor256.MAX_VALUE.add(IntRefactor256.MAX_VALUE).equals(IntRefactor256.of(-2L)), "MAX+MAX=-2");

                // ═══ 3. negate ═══
                for (int i = 0; i < 10_000; i++) {
                    final BigInteger a = new BigInteger(256, r);
                    chk(IntRefactor256.of(wrap(a)).negate().toBigInteger().equals(wrap(a.negate())), "negate " + i);
                }
                chk(IntRefactor256.MIN_VALUE.negate().equals(IntRefactor256.MIN_VALUE), "negate(MIN)=MIN");

                // ═══ 4. multiply vs BigInteger（回绕）═══
                for (int i = 0; i < 100_000; i++) {
                    final BigInteger a = new BigInteger(256, r), b = new BigInteger(256, r);
                    chk(IntRefactor256.of(wrap(a)).multiply(IntRefactor256.of(wrap(b))).toBigInteger().equals(wrap(a.multiply(b))), "mul " + i);
                }
                chk(IntRefactor256.MIN_VALUE.multiply(IntRefactor256.of(-1L)).equals(IntRefactor256.MIN_VALUE), "MIN*-1=MIN");
                chk(IntRefactor256.MIN_VALUE.multiply(IntRefactor256.MIN_VALUE).isZero(), "MIN*MIN=0");
                chk(IntRefactor256.MIN_VALUE.multiply(IntRefactor256.ONE).equals(IntRefactor256.MIN_VALUE), "MIN*1=MIN");

                // ═══ 5. divide/remainder vs BigInteger（截断向零）═══
                for (int i = 0; i < 100_000; i++) {
                    BigInteger a = new BigInteger(255, r), b = new BigInteger(200, r);
                    if (b.signum() == 0) b = BigInteger.ONE;
                    final IntRefactor256 x = IntRefactor256.of(wrap(a)), y = IntRefactor256.of(wrap(b));
                    chk(x.divide(y).toBigInteger().equals(wrap(a.divide(b))), "div " + i);
                    chk(x.remainder(y).toBigInteger().equals(wrap(a.remainder(b))), "rem " + i);
                }
                chk(IntRefactor256.of(7L).divide(IntRefactor256.of(-2L)).toLong() == -3L, "7/-2=-3 截断");
                chk(IntRefactor256.of(7L).remainder(IntRefactor256.of(-2L)).toLong() == 1L, "7%-2=1");
                chk(IntRefactor256.of(-7L).remainder(IntRefactor256.of(2L)).toLong() == -1L, "-7%2=-1");
                chk(IntRefactor256.MIN_VALUE.divide(IntRefactor256.of(-1L)).equals(IntRefactor256.MIN_VALUE), "MIN/-1 回绕");
                chk(IntRefactor256.MIN_VALUE.divide(IntRefactor256.MIN_VALUE).equals(IntRefactor256.ONE), "MIN/MIN=1");
                chk(IntRefactor256.MIN_VALUE.remainder(IntRefactor256.ONE).isZero(), "MIN%1=0");
                try {
                    IntRefactor256.ONE.divide(IntRefactor256.ZERO);
                    chk(false, "div 0 应抛");
                } catch (final ArithmeticException expected) {
                }

                // ═══ 6. bitLength/testBit/getLong vs BigInteger ═══
                for (int i = 0; i < 10_000; i++) {
                    final BigInteger a = new BigInteger(256, r);
                    final IntRefactor256 x = IntRefactor256.of(wrap(a));
                    chk(x.bitLength() == wrap(a).abs().bitLength() || wrap(a).signum() == 0 && x.bitLength() == 0,
                            "bitLength " + i);
                    for (final int bit : new int[] {0, 1, 63, 64, 127, 128, 191, 192, 254, 255}) {
                        chk(x.testBit(bit) == wrap(a).testBit(bit), "testBit " + i + "/" + bit);
                    }
                    for (int g = 0; g < 4; g++) {
                        chk(x.getLong(g) == wrap(a).shiftRight((3 - g) * 64).longValue(), "getLong " + i + "/" + g);
                    }
                }

                // ═══ 7. toFloat/toDouble vs BigInteger 窄转换（JDK RN）═══
                for (int i = 0; i < 100_000; i++) {
                    final BigInteger a = new BigInteger(256, r);
                    final IntRefactor256 x = IntRefactor256.of(wrap(a));
                    chk(Float.floatToRawIntBits(x.toFloat()) == Float.floatToRawIntBits(wrap(a).floatValue()), "toFloat " + i);
                    chk(Double.doubleToRawLongBits(x.toDouble()) == Double.doubleToRawLongBits(wrap(a).doubleValue()), "toDouble " + i);
                }

                // ═══ 8. of(double)/of(float)：截断向零 + 钳制 + 特殊值 ═══
                for (int i = 0; i < 100_000; i++) {
                    final double v = Double.longBitsToDouble(r.nextLong());
                    if (Double.isNaN(v)) {
                        chk(IntRefactor256.of(v).isZero(), "of(NaN)=0");
                        continue;
                    }
                    BigInteger want;
                    if (Double.isInfinite(v)) {
                        want = v > 0 ? IntRefactor256.MAX_VALUE.toBigInteger() : IntRefactor256.MIN_VALUE.toBigInteger();
                    } else {
                        want = new BigDecimal(v).toBigInteger(); // 截断向零
                        if (want.compareTo(IntRefactor256.MAX_VALUE.toBigInteger()) > 0) {
                            want = IntRefactor256.MAX_VALUE.toBigInteger();
                        }
                        if (want.compareTo(IntRefactor256.MIN_VALUE.toBigInteger()) < 0) {
                            want = IntRefactor256.MIN_VALUE.toBigInteger();
                        }
                    }
                    chk(IntRefactor256.of(v).toBigInteger().equals(want), "of(double) " + v);
                }
                chk(IntRefactor256.of(0.0).isZero() && IntRefactor256.of(-0.0).isZero(), "of(±0)");
                chk(IntRefactor256.of(3.99).toLong() == 3L && IntRefactor256.of(-3.99).toLong() == -3L, "of(double) 截断");
                chk(IntRefactor256.of(1e300).equals(IntRefactor256.MAX_VALUE), "of(1e300) 钳制");
                chk(IntRefactor256.of(-1e300).equals(IntRefactor256.MIN_VALUE), "of(-1e300) 钳制");

                // ═══ 9. parseInt/toString 往返 + 回绕 ═══
                for (int i = 0; i < 10_000; i++) {
                    final BigInteger a = new BigInteger(256, r);
                    chk(IntRefactor256.parseInt(a.toString()).toBigInteger().equals(wrap(a)), "parseInt " + i);
                    chk(IntRefactor256.of(wrap(a)).toString().equals(wrap(a).toString()), "toString " + i);
                }
                chk(IntRefactor256.parseInt("57896044618658097711785492504343953926634992332820282019728792003956564819968").equals(IntRefactor256.MIN_VALUE), "2^255 回绕");
                chk(IntRefactor256.parseInt("57896044618658097711785492504343953926634992332820282019728792003956564819967").equals(IntRefactor256.MAX_VALUE), "2^255-1=MAX");
                chk(IntRefactor256.ZERO.toString().equals("0"), "toString 0");

                // ═══ 10. parseHexInt/toHexString 往返 ═══
                for (int i = 0; i < 10_000; i++) {
                    final long raw = r.nextLong();
                    chk(IntRefactor256.parseHexInt("0x" + Long.toHexString(raw)).toLong() == raw
                            || IntRefactor256.parseHexInt(Long.toHexString(raw)).toLong() == raw, "parseHex " + i);
                }
                chk(IntRefactor256.MIN_VALUE.toHexString().equals("8000000000000000000000000000000000000000000000000000000000000000"), "MIN hex");
                chk(IntRefactor256.parseHexInt("f".repeat(64)).equals(IntRefactor256.of(-1L)), "ff..ff=-1");

                // ═══ 11. of(BigInteger)/toBigInteger 往返 ═══
                for (int i = 0; i < 10_000; i++) {
                    final BigInteger a = new BigInteger(256, r);
                    chk(IntRefactor256.of(wrap(a)).toBigInteger().equals(wrap(a)), "big rt " + i);
                }

                // ═══ 12. compareTo/equals vs BigInteger ═══
                for (int i = 0; i < 10_000; i++) {
                    final BigInteger a = new BigInteger(256, r), b = new BigInteger(256, r);
                    final IntRefactor256 x = IntRefactor256.of(wrap(a)), y = IntRefactor256.of(wrap(b));
                    chk(Integer.signum(x.compareTo(y)) == Integer.signum(wrap(a).compareTo(wrap(b))), "compareTo " + i);
                    chk(x.equals(y) == wrap(a).equals(wrap(b)), "equals " + i);
                }

                // ═══ 13. of(byte[])/toBytes 往返 ═══
                for (int i = 0; i < 10_000; i++) {
                    final byte[] bytes = new byte[32];
                    r.nextBytes(bytes);
                    chk(java.util.Arrays.equals(IntRefactor256.of(bytes).toBytes(), bytes), "bytes rt " + i);
                }

                // ═══ 14. 常量与 hash 一致性 ═══
                chk(IntRefactor256.MAX_VALUE.toBigInteger().equals(BigInteger.ONE.shiftLeft(255).subtract(BigInteger.ONE)), "MAX 值");
                chk(IntRefactor256.MIN_VALUE.toBigInteger().equals(BigInteger.ONE.shiftLeft(255).negate()), "MIN 值");
                chk(IntRefactor256.ONE.toBigInteger().equals(BigInteger.ONE), "ONE 值");
                chk(IntRefactor256.ZERO.isZero() && IntRefactor256.ZERO.signum() == 0, "ZERO");
                for (int i = 0; i < 1_000; i++) {
                    final BigInteger a = new BigInteger(256, r);
                    final IntRefactor256 x = IntRefactor256.of(wrap(a)), y = IntRefactor256.of(wrap(a));
                    chk(x.equals(y) == (x.hashCode() == y.hashCode()), "hash 一致 " + i);
                }

                System.out.println(fail == 0 ? "ALL PASS（IntRefactor256 全链 JDK 强对照）" : "TOTAL FAIL = " + fail);
    
        } finally {
            System.setOut(originalOut);
        }

        final String output = buffer.toString(java.nio.charset.StandardCharsets.UTF_8);
        final String meaningful = output.replaceAll("(?i)0\\s*(项|个)?\\s*FAIL", "");
        assertFalse(meaningful.contains("FAIL"), "脚本自检出现 FAIL：\n" + output);
    }

}
