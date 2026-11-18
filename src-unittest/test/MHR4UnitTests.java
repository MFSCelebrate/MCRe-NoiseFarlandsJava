package test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.MinecraftTools.Math._256bit_Refactoring.FloatRefactor256;
import net.MinecraftTools.Math._256bit_Refactoring.IntRefactor256;
import net.MinecraftTools.Math._256bit_Refactoring.MathHelperRefactor;
import java.util.Random;
import java.util.UUID;

/**
 * 由 {@code tmp/MHR4Check.java} 迁移而来（2026-11-17）。
 * <p>原脚本是 {@code main()} + PASS/FAIL 计数式自检；此处<b>完整保留原始逻辑与期望值</b>，
 * 只把外壳换成 JUnit：脚本输出被捕获，出现失败标记即断言失败。
 */
class MHR4UnitTests {

    static int fail = 0;

    static void chk(final boolean ok, final String tag) {
        if (!ok) {
            fail++;
            if (fail <= 15) System.out.println("FAIL " + tag);
        }
    }

    static final FloatRefactor256 F(final double v) {
        return FloatRefactor256.of(v);
    }

    @Test
    @DisplayName("MathHelperRefactor 第 4 批正确性")
    void scriptRuns() throws Exception {
        final java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        final java.io.PrintStream originalOut = System.out;
        System.setOut(new java.io.PrintStream(buffer, true, java.nio.charset.StandardCharsets.UTF_8));
        try {

                final Random r = new Random(20261113L);

                // ═══ 1. randomBetweenInclusive（区间 ⊆ long 快路径 + 超大区间 mod 路径）═══
                for (int i = 0; i < 20_000; i++) {
                    final long mn = r.nextInt(1000), mx = mn + r.nextInt(1000);
                    final long got = MathHelperRefactor.randomBetweenInclusive(r, IntRefactor256.of(mn), IntRefactor256.of(mx)).toLong();
                    chk(got >= mn && got <= mx, "randomBetweenInclusive 范围 " + i);
                }
                // 统计均匀性抽验（1000 样本覆盖 10 桶）
                final int[] buckets = new int[10];
                for (int i = 0; i < 1000; i++) {
                    buckets[MathHelperRefactor.randomBetweenInclusive(r, IntRefactor256.of(0), IntRefactor256.of(99)).toInt() / 10]++;
                }
                for (int b = 0; b < 10; b++) {
                    chk(buckets[b] > 50 && buckets[b] < 150, "均匀性桶 " + b + " = " + buckets[b]);
                }
                // 超大区间（2^200 尺度）
                final java.math.BigInteger bigMin = java.math.BigInteger.ONE.shiftLeft(200);
                final java.math.BigInteger bigMax = bigMin.add(java.math.BigInteger.valueOf(1_000_000));
                for (int i = 0; i < 200; i++) {
                    final java.math.BigInteger got = MathHelperRefactor.randomBetweenInclusive(r,
                            IntRefactor256.of(bigMin), IntRefactor256.of(bigMax)).toBigInteger();
                    chk(got.compareTo(bigMin) >= 0 && got.compareTo(bigMax) <= 0, "超大区间范围 " + i);
                }

                // ═══ 2. randomBetween/normal（FloatRefactor256）═══
                for (int i = 0; i < 20_000; i++) {
                    final double mn = r.nextDouble() * 100, mx = mn + r.nextDouble() * 100;
                    final double got = MathHelperRefactor.randomBetween(r, F(mn), F(mx)).toDouble();
                    chk(got >= mn && got <= mx, "randomBetween 范围 " + i);
                }
                for (int i = 0; i < 20_000; i++) {
                    final double mean = r.nextDouble() * 100, dev = r.nextDouble() * 10;
                    final double got = MathHelperRefactor.normal(r, F(mean), F(dev)).toDouble();
                    chk(Math.abs(got - mean) < dev * 10, "normal 合理 " + i);
                }

                // ═══ 3. createInsecureUUID ═══
                final UUID u1 = MathHelperRefactor.createInsecureUUID(r);
                final UUID u2 = MathHelperRefactor.createInsecureUUID(r);
                chk(u1.version() == 4 && u2.version() == 4, "UUID version 4");
                chk(!u1.equals(u2), "UUID 随机");

                // ═══ 4. packDegrees/unpackDegrees（vs 原版公式）═══
                for (int i = 0; i < 10_000; i++) {
                    final float angle = r.nextFloat() * 360 - 180;
                    final byte packed = MathHelperRefactor.packDegrees(F(angle));
                    final byte want = (byte) (int) Math.floor(angle * 256.0F / 360.0F);
                    chk(packed == want, "packDegrees " + i);
                    final float back = MathHelperRefactor.unpackDegrees(packed).toFloat();
                    chk(back == packed * 360 / 256.0F, "unpackDegrees " + i);
                }

                // ═══ 5. wobble（同 seed 同值 + 幅度 ±1e-7/2）═══
                final FloatRefactor256 coord = F(12345.6789);
                final Random r1 = new Random(1), r2 = new Random(1);
                final FloatRefactor256 w1 = MathHelperRefactor.wobble(r1, coord);
                final FloatRefactor256 w2 = MathHelperRefactor.wobble(r2, coord);
                chk(w1.equals(w2), "wobble 确定性（同 seed）");
                final double amp = 1e-7 / 2;
                chk(Math.abs(w1.toDouble() - coord.toDouble()) <= amp + 1e-12, "wobble 幅度");

                // ═══ 6. smallestSquareSide/getInt ═══
                chk(MathHelperRefactor.smallestSquareSide(0) == 0 && MathHelperRefactor.smallestSquareSide(10) == 4
                        && MathHelperRefactor.smallestSquareSide(17) == 5, "smallestSquareSide");
                chk(MathHelperRefactor.getInt("42", -1) == 42 && MathHelperRefactor.getInt("abc", -1) == -1
                        && MathHelperRefactor.getInt(" 7 ", -1) == 7, "getInt");

                // ═══ 7. DeepSeek 坑回归 ═══
                chk(Double.isNaN(FloatRefactor256.NaN.toDouble()) && Float.isNaN(FloatRefactor256.NaN.toFloat()), "NaN 保持（回归）");
                chk(Double.isNaN(FloatRefactor256.NaN.negate().toDouble()), "-NaN 保持（回归）");
                chk(FloatRefactor256.POSITIVE_INFINITY.toDouble() == Double.POSITIVE_INFINITY, "Inf 保持（回归）");
                final long t0 = System.nanoTime();
                FloatRefactor256.MIN_VALUE.toString();
                chk(System.nanoTime() - t0 < 100_000_000, "toString 次正态快（回归）");

                System.out.println(fail == 0 ? "ALL PASS（MathHelperRefactor 批4 + DeepSeek 回归）" : "TOTAL FAIL = " + fail);
    
        } finally {
            System.setOut(originalOut);
        }

        final String output = buffer.toString(java.nio.charset.StandardCharsets.UTF_8);
        final String meaningful = output.replaceAll("(?i)0\\s*(项|个)?\\s*FAIL", "");
        assertFalse(meaningful.contains("FAIL"), "脚本自检出现 FAIL：\n" + output);
    }

}
