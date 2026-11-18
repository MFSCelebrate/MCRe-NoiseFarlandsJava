package test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.MinecraftTools.Math._256bit_Refactoring.DynamicNumber;
import net.MinecraftTools.Math._256bit_Refactoring.DynamicNumber.Type;
import java.math.BigInteger;
import java.util.Random;

/**
 * 由 {@code tmp/DynamicNumberCheck.java} 迁移而来（2026-11-17）。
 * <p>原脚本是 {@code main()} + PASS/FAIL 计数式自检；此处<b>完整保留原始逻辑与期望值</b>，
 * 只把外壳换成 JUnit：脚本输出被捕获，出现失败标记即断言失败。
 */
class DynamicNumberUnitTests {

    static int fail = 0;

    static void chk(final boolean ok, final String tag) {
        if (!ok) {
            fail++;
            if (fail <= 20) System.out.println("FAIL " + tag);
        }
    }

    @Test
    @DisplayName("DynamicNumber 正确性自检")
    void scriptRuns() throws Exception {
        final java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        final java.io.PrintStream originalOut = System.out;
        System.setOut(new java.io.PrintStream(buffer, true, java.nio.charset.StandardCharsets.UTF_8));
        try {

                final Random r = new Random(20261113L);
                final BigInteger MASK_LONG = BigInteger.ONE.shiftLeft(64);
                final BigInteger MAX_LONG = BigInteger.ONE.shiftLeft(63).subtract(BigInteger.ONE);
                final BigInteger MIN_LONG = BigInteger.ONE.shiftLeft(63).negate();

                // ═══ 1. 三配置 × 全运算 vs BigInteger 参考 ═══
                for (final Type t : Type.values()) {
                    DynamicNumber.setDefaultType(t);
                    for (int i = 0; i < 20_000; i++) {
                        // 输入容量按配置限制（LONG 配置用 62 位防乘法溢出参考复杂化；INT256/BB 用 200 位）
                        final int bits = t == Type.LONG ? 62 : 200;
                        BigInteger a = new BigInteger(bits, r), b = new BigInteger(bits, r);
                        if (b.signum() == 0) b = BigInteger.ONE;
                        final DynamicNumber x = DynamicNumber.of(a), y = DynamicNumber.of(b);
                        chk(x.type() == t && y.type() == t, "type " + t);
                        chk(x.add(y).toBigInteger().equals(a.add(b)), "add " + t + " " + i);
                        chk(x.subtract(y).toBigInteger().equals(a.subtract(b)), "sub " + t + " " + i);
                        // mul 段输入位宽按配置缩窄（LONG 配置 31×31 防 multiplyExact 溢出——溢出防线本身另有专项）
                        final int mulBits = t == Type.LONG ? 31 : t == Type.INT256 ? 127 : 200;
                        final BigInteger ma = new BigInteger(mulBits, r);
                        final BigInteger mb = new BigInteger(mulBits, r);
                        if (mb.signum() == 0) {
                            continue; // 重掷概率极低，跳过该样本
                        }
                        final DynamicNumber mx = DynamicNumber.of(ma), my = DynamicNumber.of(mb);
                        chk(mx.multiply(my).toBigInteger().equals(ma.multiply(mb)), "mul " + t + " " + i);
                        chk(x.divide(y).toBigInteger().equals(a.divide(b)), "div " + t + " " + i);
                        chk(x.negate().toBigInteger().equals(a.negate()), "neg " + t + " " + i);
                        chk(x.abs().toBigInteger().equals(a.abs()), "abs " + t + " " + i);
                        chk(Integer.signum(x.compareTo(y)) == Integer.signum(a.compareTo(b)), "cmp " + t + " " + i);
                    }
                    // LONG 配置乘法溢出防线（multiplyExact 抛）
                    if (t == Type.LONG) {
                        DynamicNumber.setDefaultType(Type.LONG);
                        final DynamicNumber big1 = DynamicNumber.of(Long.MAX_VALUE);
                        try {
                            big1.multiply(big1);
                            chk(false, "LONG mul 溢出应抛");
                        } catch (final ArithmeticException expected) {
                        }
                    }
                }

                // INT256 配置乘法超域专项（200×200 = 400 位必抛，不静默回绕）
                DynamicNumber.setDefaultType(Type.INT256);
                final DynamicNumber ma200 = DynamicNumber.of(new BigInteger(200, r).setBit(199));
                final DynamicNumber mb200 = DynamicNumber.of(new BigInteger(200, r).setBit(199));
                try {
                    ma200.multiply(mb200);
                    chk(false, "INT256 mul 超域应抛");
                } catch (final ArithmeticException expected) {
                }

                // ═══ 2. 超域防线（LONG 配置 of 超 long 抛；INT256 配置 of 超 256 位抛）═══
                DynamicNumber.setDefaultType(Type.LONG);
                try {
                    DynamicNumber.of(BigInteger.ONE.shiftLeft(64));
                    chk(false, "LONG of(2^64) 应抛");
                } catch (final ArithmeticException expected) {
                }
                chk(DynamicNumber.of(Long.MAX_VALUE).toBigInteger().equals(MAX_LONG), "LONG MAX 边界");
                chk(DynamicNumber.of(Long.MIN_VALUE).toBigInteger().equals(MIN_LONG), "LONG MIN 边界");
                DynamicNumber.setDefaultType(Type.INT256);
                try {
                    DynamicNumber.of(BigInteger.ONE.shiftLeft(255));
                    chk(false, "INT256 of(2^255) 应抛");
                } catch (final ArithmeticException expected) {
                }
                chk(DynamicNumber.of(BigInteger.ONE.shiftLeft(255).subtract(BigInteger.ONE)).toBigInteger()
                        .equals(BigInteger.ONE.shiftLeft(255).subtract(BigInteger.ONE)), "INT256 MAX 边界");
                chk(DynamicNumber.of(BigInteger.ONE.shiftLeft(255).negate()).toBigInteger()
                        .equals(BigInteger.ONE.shiftLeft(255).negate()), "INT256 MIN 边界");
                DynamicNumber.setDefaultType(Type.BIG_INTEGER);
                chk(DynamicNumber.of(BigInteger.ONE.shiftLeft(1000)).toBigInteger().equals(BigInteger.ONE.shiftLeft(1000)), "BB 2^1000");

                // ═══ 3. 显式类型工厂 + 配置切换语义 ═══
                DynamicNumber.setDefaultType(Type.LONG);
                final DynamicNumber e256 = DynamicNumber.of(Type.INT256, 12345L);
                chk(e256.type() == Type.INT256 && e256.toBigInteger().equals(BigInteger.valueOf(12345)), "explicit INT256");
                final DynamicNumber ebb = DynamicNumber.of(Type.BIG_INTEGER, -999L);
                chk(ebb.type() == Type.BIG_INTEGER && ebb.toBigInteger().equals(BigInteger.valueOf(-999)), "explicit BB");
                // 运算目标跟随当前 defaultType（LONG 配置下 INT256 输入运算 → LONG 结果）
                final DynamicNumber sum = e256.add(DynamicNumber.of(1L));
                chk(sum.type() == Type.LONG && sum.toBigInteger().equals(BigInteger.valueOf(12346)), "LONG 配置运算目标");
                // BIG_INTEGER 配置下 LONG 输入运算 → BB 结果
                DynamicNumber.setDefaultType(Type.BIG_INTEGER);
                final DynamicNumber sum2 = e256.add(DynamicNumber.of(1L));
                chk(sum2.type() == Type.BIG_INTEGER && sum2.toBigInteger().equals(BigInteger.valueOf(12346)), "BB 配置运算目标");
                // INT256 配置：输入提升链
                DynamicNumber.setDefaultType(Type.INT256);
                final DynamicNumber big2 = DynamicNumber.of(Type.BIG_INTEGER, 777L);
                chk(big2.add(DynamicNumber.of(1L)).type() == Type.INT256, "INT256 配置提升");
                chk(big2.add(DynamicNumber.of(1L)).toBigInteger().equals(BigInteger.valueOf(778)), "INT256 配置运算值");
                // LONG 配置下 BIG_INTEGER 超域输入运算 → 抛（不静默回绕）
                DynamicNumber.setDefaultType(Type.LONG);
                final DynamicNumber huge = DynamicNumber.of(Type.BIG_INTEGER, BigInteger.ONE.shiftLeft(100));
                try {
                    huge.add(DynamicNumber.of(1L));
                    chk(false, "LONG 配置超域运算应抛");
                } catch (final ArithmeticException expected) {
                }

                // ═══ 4. 跨类型 equals/compareTo/hashCode ═══
                DynamicNumber.setDefaultType(Type.LONG);
                final DynamicNumber a5 = DynamicNumber.of(5L);
                DynamicNumber.setDefaultType(Type.INT256);
                final DynamicNumber b5 = DynamicNumber.of(5L);
                DynamicNumber.setDefaultType(Type.BIG_INTEGER);
                final DynamicNumber c5 = DynamicNumber.of(5L);
                chk(a5.equals(b5) && b5.equals(c5) && a5.equals(c5), "跨类型 equals");
                chk(a5.hashCode() == b5.hashCode() && b5.hashCode() == c5.hashCode(), "跨类型 hashCode");
                chk(a5.compareTo(b5) == 0 && b5.compareTo(c5) == 0, "跨类型 compareTo 0");
                chk(DynamicNumber.of(-1L).compareTo(DynamicNumber.of(1L)) < 0, "cmp 负正");
                chk(!DynamicNumber.of(0L).equals(null), "equals null false");

                // ═══ 5. Number 契约 ═══
                DynamicNumber.setDefaultType(Type.INT256);
                final DynamicNumber n42 = DynamicNumber.of(42L);
                chk(n42.intValue() == 42 && n42.longValue() == 42L && n42.doubleValue() == 42.0 && n42.floatValue() == 42.0f,
                        "Number 契约");
                chk(n42.signum() == 1 && !n42.isNegative() && !n42.isZero(), "signum");
                chk(DynamicNumber.of(0L).isZero() && DynamicNumber.of(-7L).isNegative(), "zero/neg");

                // ═══ 6. 除零 ═══
                try {
                    DynamicNumber.of(1L).divide(DynamicNumber.of(0L));
                    chk(false, "除零应抛");
                } catch (final ArithmeticException expected) {
                }

                // ═══ 7. LONG 配置零开销（vs 原生 long）═══
                DynamicNumber.setDefaultType(Type.LONG);
                final int N = 2048;
                final long[] raw = new long[N];
                final DynamicNumber[] dyn = new DynamicNumber[N];
                for (int i = 0; i < N; i++) {
                    raw[i] = r.nextLong() >> 2;
                    dyn[i] = DynamicNumber.of(raw[i]);
                }
                for (int round = 0; round < 3; round++) {
                    runRaw(raw);
                    runDyn(dyn);
                }
                long bestRaw = Long.MAX_VALUE, bestDyn = Long.MAX_VALUE;
                for (int round = 0; round < 5; round++) {
                    long t = System.nanoTime();
                    runRaw(raw);
                    bestRaw = Math.min(bestRaw, System.nanoTime() - t);
                    t = System.nanoTime();
                    runDyn(dyn);
                    bestDyn = Math.min(bestDyn, System.nanoTime() - t);
                }
                System.out.printf("LONG 配置 add: 原生 long=%.1f  DynamicNumber=%.1f ns/op（含 toBigInteger 结算）%n",
                        bestRaw / 20000.0, bestDyn / 20000.0);

                System.out.println(fail == 0 ? "ALL PASS（DynamicNumber 三配置全链）" : "TOTAL FAIL = " + fail);
    
        } finally {
            System.setOut(originalOut);
        }

        final String output = buffer.toString(java.nio.charset.StandardCharsets.UTF_8);
        final String meaningful = output.replaceAll("(?i)0\\s*(项|个)?\\s*FAIL", "");
        assertFalse(meaningful.contains("FAIL"), "脚本自检出现 FAIL：\n" + output);
    }


    static long runRaw(final long[] a) {
        long s = 0;
        for (int i = 0; i < a.length - 1; i++) {
            s += a[i] + a[i + 1];
        }
        return s;
    }

    static DynamicNumber runDyn(final DynamicNumber[] a) {
        DynamicNumber s = DynamicNumber.of(0L);
        for (int i = 0; i < a.length - 1; i++) {
            s = s.add(a[i].add(a[i + 1]));
        }
        return s;
    }
}
