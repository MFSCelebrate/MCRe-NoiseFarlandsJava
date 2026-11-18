package test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.MinecraftTools.Math.PointExtension.FloatExtension;
import java.math.BigInteger;
import java.util.Random;

/**
 * 由 {@code tmp/FloatExtensionCheck.java} 迁移而来（2026-11-17）。
 * <p>原脚本是 {@code main()} + PASS/FAIL 计数式自检；此处<b>完整保留原始逻辑与期望值</b>，
 * 只把外壳换成 JUnit：脚本输出被捕获，出现失败标记即断言失败。
 */
class FloatExtensionUnitTests {

    static int fail = 0;

    static void chk(final boolean ok, final String tag) {
        if (!ok) {
            fail++;
            if (fail <= 25) System.out.println("FAIL " + tag);
        }
    }

    static final long IMPLICIT = 1L << 23;
    static final long SIGNIF_MASK = 0xFF_FFFFL;

    /** 二进制 RN 参考：精确值 p·2^e2 → 24 位有效 → 36 位模式 */
    static long refPack(BigInteger p, long e2, boolean neg) {
        if (p.signum() == 0) {
            return 0L; // 相消 → +0（IEEE）
        }
        int bl = p.bitLength();
        int k = bl - 24;
        BigInteger kept;
        long realExp;
        if (k > 0) {
            kept = p.shiftRight(k);
            boolean g = p.testBit(k - 1);
            boolean rr = k >= 2 && p.testBit(k - 2);
            boolean sticky = k >= 3 && !p.and(BigInteger.ONE.shiftLeft(k - 2).subtract(BigInteger.ONE)).equals(BigInteger.ZERO);
            if (g && (rr || sticky || kept.testBit(0))) kept = kept.add(BigInteger.ONE);
            realExp = e2 + bl - 1;
            if (kept.bitLength() > 24) { kept = kept.shiftRight(1); realExp = e2 + bl; }
        } else if (k < 0) {
            kept = p.shiftLeft(-k);
            realExp = e2 + bl - 1;
        } else {
            kept = p;
            realExp = e2 + bl - 1;
        }
        return (neg ? 1L << 35 : 0L) | ((realExp + 1023L) << 24) | kept.longValueExact();
    }

    @Test
    @DisplayName("FloatExtension（36 位浮点）正确性")
    void scriptRuns() throws Exception {
        final java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        final java.io.PrintStream originalOut = System.out;
        System.setOut(new java.io.PrintStream(buffer, true, java.nio.charset.StandardCharsets.UTF_8));
        try {

                final Random r = new Random(20261112L);

                // ═══ A. of(long) 全指数扫描（2^0 ~ 2^62）+ 大指数链到 MAX_VALUE ═══
                for (int k = 0; k <= 62; k++) {
                    FloatExtension f = FloatExtension.of(1L << k);
                    chk(f.exponentField() == k + 1023, "of(2^" + k + ") E");
                    chk(f.significand() == IMPLICIT, "of(2^" + k + ") m");
                    chk(f.signum() > 0, "of(2^" + k + ") sign");
                }
                // toFloat 边界：k<=127 有限精确，k>=128 → +Inf
                for (int k : new int[] {0, 23, 24, 100, 126, 127}) {
                    chk(FloatExtension.of(1L << k).toFloat() == (float) (1L << k), "of(2^" + k + ").toFloat");
                }
                chk(Float.floatToRawIntBits(FloatExtension.of(1L << 127).toFloat()) == Float.floatToRawIntBits((float) (1L << 127)), "2^127 toFloat 有限");
                // 大指数链：2^62 →(×2^10 93 次) 2^992 → ×2^8 → 2^1000 → ×(2^24-1) = MAX_VALUE
                FloatExtension big = FloatExtension.of(1L << 62);
                int k = 62;
                while (k < 992) { big = big.multiply(FloatExtension.of(1L << 10)); k += 10; chk(big.exponentField() == k + 1023, "chain 2^" + k); }
                big = big.multiply(FloatExtension.of(1L << 8)); k += 8;
                chk(k == 1000 && big.exponentField() == 2023, "chain 2^1000");
                FloatExtension max = big.multiply(FloatExtension.of((1L << 24) - 1));
                chk(max.rawBits() == FloatExtension.MAX_VALUE_BITS, "MAX_VALUE bits got=0x" + Long.toHexString(max.rawBits()));
                chk(max.toFloat() == Float.POSITIVE_INFINITY, "MAX toFloat Inf");
                // 2^128 ~ 2^1024 区间抽查（float 全 Inf，FE 精确）——链推进避免中途溢出（×2^10 只在 kk+10<=e 时）
                for (int e : new int[] {128, 200, 500, 800, 1000, 1023}) {
                    FloatExtension p2 = FloatExtension.of(1L << 62);
                    int kk = 62;
                    while (kk + 10 <= e) { p2 = p2.multiply(FloatExtension.of(1L << 10)); kk += 10; }
                    if (kk < e) { p2 = p2.multiply(FloatExtension.of(1L << (e - kk))); kk = e; }
                    chk(p2.exponentField() == e + 1023 && p2.significand() == IMPLICIT, "2^" + e + " 精确");
                    chk(p2.toFloat() == Float.POSITIVE_INFINITY, "2^" + e + " toFloat Inf");
                    chk(p2.isFinite() && p2.isNormal(), "2^" + e + " 正规有限");
                }
                // 负侧：-2^1000
                FloatExtension negBig = big.negate();
                chk(negBig.signum() < 0 && negBig.exponentField() == 2023 && negBig.significand() == IMPLICIT, "-2^1000 位模式");

                // ═══ B. of(float) 往返（全位模式随机，NaN 只验 isNaN）═══
                for (int i = 0; i < 200_000; i++) {
                    int bits = r.nextInt();
                    float f = Float.intBitsToFloat(bits);
                    FloatExtension fe = FloatExtension.of(f);
                    if (Float.isNaN(f)) {
                        chk(fe.isNaN(), "of(NaN) isNaN");
                        continue;
                    }
                    chk(Float.floatToRawIntBits(fe.toFloat()) == bits, "of(float) rt bits=0x" + Integer.toHexString(bits));
                }

                // ═══ C. 尾数语义 vs float：of(v).toFloat() == (float) v（随机 long）═══
                for (int i = 0; i < 200_000; i++) {
                    long v = r.nextLong();
                    float want = (float) v;
                    float got = FloatExtension.of(v).toFloat();
                    chk(Float.floatToRawIntBits(got) == Float.floatToRawIntBits(want), "long->float RN v=" + v);
                }
                // 2^24 跳步（ULP=1 → ULP=2 边界）
                chk(FloatExtension.of((1L << 24) + 1).longValue() == (1L << 24), "2^24+1 RN-even");
                chk(FloatExtension.of((1L << 24) + 3).longValue() == (1L << 24) + 4, "2^24+3 RN");
                // of(Long.MAX_VALUE) RN 后 ≈ 2^63（24 位装不下 63 位）→ longValue 截断回绕 MIN（数学正确，非 bug）
                chk(FloatExtension.of(Long.MAX_VALUE).toFloat() == (float) Long.MAX_VALUE, "MAX toFloat");
                chk(FloatExtension.of(Long.MIN_VALUE).longValue() == Long.MIN_VALUE, "MIN longValue");

                // ═══ D. 算术 vs float 原生（同构域逐位）═══
                for (int i = 0; i < 100_000; i++) {
                    int e = 10 + r.nextInt(100);           // [10,109]
                    int e2 = e + (r.nextInt(3) - 1);       // 同量级或 ±1
                    float a = Float.intBitsToFloat(((e + 127) << 23) | (r.nextInt() & 0x7F_FFFF));
                    float b = Float.intBitsToFloat(((e2 + 127) << 23) | (r.nextInt() & 0x7F_FFFF));
                    FloatExtension fa = FloatExtension.of(a);
                    FloatExtension fb = FloatExtension.of(b);
                    chk(Float.floatToRawIntBits(fa.add(fb).toFloat()) == Float.floatToRawIntBits(a + b), "add vs float");
                    chk(Float.floatToRawIntBits(fa.subtract(fb).toFloat()) == Float.floatToRawIntBits(a - b), "sub vs float");
                    int em = 20 + r.nextInt(60);
                    float c = Float.intBitsToFloat(((em + 127) << 23) | (r.nextInt() & 0x7F_FFFF));
                    FloatExtension fc = FloatExtension.of(c);
                    chk(Float.floatToRawIntBits(fa.multiply(fc).toFloat()) == Float.floatToRawIntBits(a * c), "mul vs float");
                    chk(Float.floatToRawIntBits(fa.divide(fc).toFloat()) == Float.floatToRawIntBits(a / c), "div vs float");
                }

                // ═══ E. 大指数域 vs BigInteger 精确参考（正规域随机 (E,m)）═══
                for (int i = 0; i < 100_000; i++) {
                    int e = -500 + r.nextInt(1000);
                    long m1 = IMPLICIT | (r.nextInt() & 0x7F_FFFF);
                    long m2 = IMPLICIT | (r.nextInt() & 0x7F_FFFF);
                    boolean neg1 = r.nextBoolean(), neg2 = r.nextBoolean();
                    FloatExtension a = FloatExtension.fromRawBits((neg1 ? 1L << 35 : 0) | ((long) (e + 1023) << 24) | m1);
                    FloatExtension b = FloatExtension.fromRawBits((neg2 ? 1L << 35 : 0) | ((long) (e + 1023) << 24) | m2);
                    BigInteger x = BigInteger.valueOf(m1), y = BigInteger.valueOf(m2);
                    boolean m1Big = x.compareTo(y) >= 0;
                    // add/sub 参考（值 = (s1·m1 ± s2·m2)·2^(e-23)）
                    long wantAdd, wantSub;
                    if (neg1 == neg2) {
                        wantAdd = refPack(x.add(y), e - 23, neg1);                           // 同号加
                        wantSub = refPack(x.subtract(y).abs(), e - 23, m1Big ? neg1 : !neg1); // 同号 sub：大幅值小时符号翻转
                    } else {
                        wantAdd = refPack(x.subtract(y).abs(), e - 23, m1Big ? neg1 : neg2); // 异号加大者符号
                        wantSub = refPack(x.add(y), e - 23, neg1);                       // 异号 sub = 同号加(neg1)
                    }
                    chk(a.add(b).rawBits() == wantAdd, "BE add e=" + e + " m1=" + m1 + " m2=" + m2 + " n=" + neg1 + neg2);
                    chk(a.subtract(b).rawBits() == wantSub, "BE sub e=" + e + " m1=" + m1 + " m2=" + m2 + " n=" + neg1 + neg2);
                    // mul 参考（积 = m1·m2·2^(2e-46)）
                    long wantMul = refPack(x.multiply(y), 2L * e - 46, neg1 ^ neg2);
                    chk(a.multiply(b).rawBits() == wantMul, "BE mul e=" + e + " m1=" + m1 + " m2=" + m2);
                    // 相消 → +0
                    if (m1 == m2 && neg1 != neg2) chk(a.add(b).isZero() && a.add(b).signum() > 0, "BE cancel +0");
                }

                // ═══ F. 特殊值 + 全序 ═══
                FloatExtension pinf = FloatExtension.POSITIVE_INFINITY;
                FloatExtension ninf = FloatExtension.NEGATIVE_INFINITY;
                FloatExtension zero = FloatExtension.ZERO;
                FloatExtension nzero = FloatExtension.NEG_ZERO;
                FloatExtension nan = FloatExtension.NaN;
                chk(zero.multiply(pinf).isNaN(), "0*Inf");
                chk(zero.divide(zero).isNaN(), "0/0");
                chk(pinf.subtract(pinf).isNaN(), "Inf-Inf");
                chk(pinf.divide(pinf).isNaN(), "Inf/Inf");
                chk(pinf.add(pinf) == pinf, "Inf+Inf");
                chk(ninf.add(ninf) == ninf, "-Inf+-Inf");
                chk(pinf.add(ninf).isNaN(), "Inf+-Inf");
                chk(zero.add(zero).signum() > 0, "+0++0=+0");
                chk(nzero.add(nzero).signum() < 0, "-0+-0=-0");
                chk(zero.add(nzero).signum() > 0, "+0+-0=+0(IEEE)");
                chk(nzero.negate().signum() > 0, "-(-0)=+0");
                chk(FloatExtension.of(-5f).divide(zero) == ninf, "x/0=-Inf");
                chk(FloatExtension.of(5f).divide(zero) == pinf, "x/0=+Inf");
                // 全序：NaN > +Inf > +max > +min > +0 == -0 > -min > -max > -Inf > -NaN
                FloatExtension pmax = FloatExtension.fromRawBits(FloatExtension.MAX_VALUE_BITS);
                FloatExtension pmin = FloatExtension.fromRawBits(FloatExtension.MIN_VALUE_BITS);
                chk(nan.compareTo(pinf) > 0 && pinf.compareTo(pmax) > 0 && pmax.compareTo(zero) > 0, "order pos");
                chk(zero.compareTo(nzero) == 0 && zero.compareTo(pmin) < 0 && pmin.compareTo(ninf) > 0 && ninf.compareTo(nan) < 0, "order zero/neg");
                chk(nan.compareTo(nan) == 0, "NaN cmp 0");
                chk(nan.equals(nan) && !zero.equals(nzero), "equals NaN/-0");
                // 次正态属性
                chk(pmin.isFinite() && !pmin.isNormal() && pmin.longValue() == 0, "MIN_VALUE subnormal");
                FloatExtension minNormal = FloatExtension.fromRawBits(FloatExtension.MIN_NORMAL_BITS);
                chk(minNormal.isNormal() && minNormal.exponentField() == 1, "MIN_NORMAL");
                chk(minNormal.toFloat() == 0.0f, "MIN_NORMAL toFloat 0（float 次正态界以下）");

                // ═══ G. 速度 vs OpenJDK Float ═══
                speedBench();

                System.out.println(fail == 0 ? "ALL PASS（A~F 正确性全绿）" : "TOTAL FAIL = " + fail);
    
        } finally {
            System.setOut(originalOut);
        }

        final String output = buffer.toString(java.nio.charset.StandardCharsets.UTF_8);
        final String meaningful = output.replaceAll("(?i)0\\s*(项|个)?\\s*FAIL", "");
        assertFalse(meaningful.contains("FAIL"), "脚本自检出现 FAIL：\n" + output);
    }


    static void speedBench() {
        final int N = 4096;
        final Random r = new Random(7);
        float[] fs = new float[N];
        FloatExtension[] fes = new FloatExtension[N];
        for (int i = 0; i < N; i++) {
            float f = Float.intBitsToFloat(((60 + r.nextInt(40)) << 23) | (r.nextInt() & 0x7F_FFFF));
            fs[i] = f;
            fes[i] = FloatExtension.of(f);
        }
        // warmup
        for (int round = 0; round < 3; round++) {
            runFloat(fs); runFe(fes); runOf(fs); runToFloat(fes);
        }
        long bestFloatAdd = Long.MAX_VALUE, bestFeAdd = Long.MAX_VALUE;
        long bestFloatMul = Long.MAX_VALUE, bestFeMul = Long.MAX_VALUE;
        long bestFeOf = Long.MAX_VALUE, bestRaw = Long.MAX_VALUE, bestFeToFloat = Long.MAX_VALUE;
        for (int round = 0; round < 5; round++) {
            long t = System.nanoTime(); runFloat(fs); bestFloatAdd = Math.min(bestFloatAdd, System.nanoTime() - t);
            t = System.nanoTime(); runFe(fes); bestFeAdd = Math.min(bestFeAdd, System.nanoTime() - t);
            t = System.nanoTime(); runFloatMul(fs); bestFloatMul = Math.min(bestFloatMul, System.nanoTime() - t);
            t = System.nanoTime(); runFeMul(fes); bestFeMul = Math.min(bestFeMul, System.nanoTime() - t);
            t = System.nanoTime(); runOf(fs); bestFeOf = Math.min(bestFeOf, System.nanoTime() - t);
            t = System.nanoTime(); runRaw(fs); bestRaw = Math.min(bestRaw, System.nanoTime() - t);
            t = System.nanoTime(); runToFloat(fes); bestFeToFloat = Math.min(bestFeToFloat, System.nanoTime() - t);
        }
        double ops = 20000.0;
        System.out.printf("速度（ns/op，20000 次/轮取 5 轮最优）：%n");
        System.out.printf("  add : float=%.1f  FloatExtension=%.1f  (%.1fx)%n", bestFloatAdd / ops, bestFeAdd / ops, (double) bestFeAdd / bestFloatAdd);
        System.out.printf("  mul : float=%.1f  FloatExtension=%.1f  (%.1fx)%n", bestFloatMul / ops, bestFeMul / ops, (double) bestFeMul / bestFloatMul);
        System.out.printf("  of(float) : FloatExtension=%.1f  vs Float.floatToRawIntBits=%.1f  (%.1fx)%n", bestFeOf / ops, bestRaw / ops, (double) bestFeOf / bestRaw);
        System.out.printf("  toFloat : FloatExtension=%.1f%n", bestFeToFloat / ops);
    }

    static float runFloat(float[] a) { float s = 0; for (int i = 0; i < a.length - 1; i++) s += a[i] + a[i + 1]; return s; }
    static float runFloatMul(float[] a) { float s = 1; for (int i = 0; i < a.length - 1; i++) s *= a[i] * a[i + 1]; return s; }
    static FloatExtension runFe(FloatExtension[] a) { FloatExtension s = FloatExtension.ZERO; for (int i = 0; i < a.length - 1; i++) s = s.add(a[i].add(a[i + 1])); return s; }
    static FloatExtension runFeMul(FloatExtension[] a) { FloatExtension s = FloatExtension.of(1f); for (int i = 0; i < a.length - 1; i++) s = s.multiply(a[i].multiply(a[i + 1])); return s; }
    static FloatExtension runOf(float[] a) { FloatExtension s = FloatExtension.ZERO; for (int i = 0; i < a.length; i++) s = FloatExtension.of(a[i]); return s; }
    static int runRaw(float[] a) { int s = 0; for (int i = 0; i < a.length; i++) s ^= Float.floatToRawIntBits(a[i]); return s; }
    static float runToFloat(FloatExtension[] a) { float s = 0; for (int i = 0; i < a.length; i++) s += a[i].toFloat(); return s; }
}
