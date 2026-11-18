package test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.MinecraftTools.Math._256bit_Refactoring.FloatRefactor256;
import net.MinecraftTools.Math._256bit_Refactoring.IntRefactor256;
import net.MinecraftTools.Math._256bit_Refactoring.MathHelperRefactor;
import java.util.Random;

/**
 * 由 {@code tmp/MHR2Check.java} 迁移而来（2026-11-17）。
 * <p>原脚本是 {@code main()} + PASS/FAIL 计数式自检；此处<b>完整保留原始逻辑与期望值</b>，
 * 只把外壳换成 JUnit：脚本输出被捕获，出现失败标记即断言失败。
 */
class MHR2UnitTests {

    static int fail = 0;

    static void chk(final boolean ok, final String tag) {
        if (!ok) {
            fail++;
            if (fail <= 20) System.out.println("FAIL " + tag);
        }
    }

    static final FloatRefactor256 F(final double v) {
        return FloatRefactor256.of(v);
    }

    /** 原版 wrapDegrees(double) 复刻 */
    static double origWrap(final double angle) {
        double n = angle % 360.0;
        if (n >= 180.0) n -= 360.0;
        if (n < -180.0) n += 360.0;
        return n;
    }

    @Test
    @DisplayName("MathHelperRefactor 第 2 批正确性")
    void scriptRuns() throws Exception {
        final java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        final java.io.PrintStream originalOut = System.out;
        System.setOut(new java.io.PrintStream(buffer, true, java.nio.charset.StandardCharsets.UTF_8));
        try {

                final Random r = new Random(20261113L);

                // ═══ 1. lerp 家族 vs double 公式 ═══
                for (int i = 0; i < 20_000; i++) {
                    final double a = r.nextDouble(), p0 = r.nextDouble() * 100, p1 = r.nextDouble() * 100;
                    chk(Math.abs(MathHelperRefactor.lerp(F(a), F(p0), F(p1)).toDouble() - (p0 + a * (p1 - p0))) < 1e-9 * (1 + Math.abs(p1)), "lerp " + i);
                }
                for (int i = 0; i < 10_000; i++) {
                    final double a1 = r.nextDouble(), a2 = r.nextDouble();
                    final double x00 = r.nextDouble(), x10 = r.nextDouble(), x01 = r.nextDouble(), x11 = r.nextDouble();
                    final double want = (x00 + a2 * ((x10 + a1 * (x10 - x00)) - (x00 + a1 * (x00 - x00)))) ;
                    // 直接复刻 lerp2 结构
                    final double l1 = x00 + a1 * (x10 - x00), l2 = x01 + a1 * (x11 - x01);
                    final double want2 = l1 + a2 * (l2 - l1);
                    chk(Math.abs(MathHelperRefactor.lerp2(F(a1), F(a2), F(x00), F(x10), F(x01), F(x11)).toDouble() - want2) < 1e-9 * (1 + Math.abs(x11)), "lerp2 " + i);
                }
                // clampedLerp
                chk(MathHelperRefactor.clampedLerp(F(-0.5), F(10), F(20)).toDouble() == 10.0, "clampedLerp <0");
                chk(MathHelperRefactor.clampedLerp(F(1.5), F(10), F(20)).toDouble() == 20.0, "clampedLerp >1");
                chk(MathHelperRefactor.clampedLerp(F(0.5), F(10), F(20)).toDouble() == 15.0, "clampedLerp mid");
                // inverseLerp / map / clampedMap
                chk(Math.abs(MathHelperRefactor.inverseLerp(F(15), F(10), F(20)).toDouble() - 0.5) < 1e-12, "inverseLerp");
                chk(Math.abs(MathHelperRefactor.map(F(0.5), F(0), F(1), F(100), F(200)).toDouble() - 150) < 1e-9, "map");
                chk(MathHelperRefactor.clampedMap(F(2.0), F(0), F(1), F(100), F(200)).toDouble() == 200.0, "clampedMap 钳制");
                // smoothstep
                for (final double x : new double[] {0.0, 0.25, 0.5, 0.75, 1.0}) {
                    final double want = x * x * x * (x * (x * 6.0 - 15.0) + 10.0);
                    chk(Math.abs(MathHelperRefactor.smoothstep(F(x)).toDouble() - want) < 1e-12, "smoothstep " + x);
                    final double wd = 30.0 * x * x * (x - 1.0) * (x - 1.0);
                    chk(Math.abs(MathHelperRefactor.smoothstepDerivative(F(x)).toDouble() - wd) < 1e-12, "smoothstepDeriv " + x);
                }
                // catmullrom vs double 公式
                for (int i = 0; i < 5_000; i++) {
                    final double a = r.nextDouble(), p0 = r.nextDouble(), p1 = r.nextDouble(), p2 = r.nextDouble(), p3 = r.nextDouble();
                    final double want = 0.5 * (2 * p1 + (p2 - p0) * a + (2 * p0 - 5 * p1 + 4 * p2 - p3) * a * a + (3 * p1 - p0 - 3 * p2 + p3) * a * a * a);
                    chk(Math.abs(MathHelperRefactor.catmullrom(F(a), F(p0), F(p1), F(p2), F(p3)).toDouble() - want) < 1e-9 * (1 + Math.abs(want)), "catmullrom " + i);
                }
                // rotLerp / rotLerpRad
                for (int i = 0; i < 5_000; i++) {
                    final double a = r.nextDouble(), from = r.nextDouble() * 720 - 360, to = r.nextDouble() * 720 - 360;
                    final double wantRot = from + a * origWrap(to - from);
                    chk(Math.abs(MathHelperRefactor.rotLerp(F(a), F(from), F(to)).toDouble() - wantRot) < 1e-9, "rotLerp " + i);
                    double diff = to - from;
                    while (diff < -Math.PI) diff += Math.PI * 2;
                    while (diff >= Math.PI) diff -= Math.PI * 2;
                    final double wantRad = from + a * diff;
                    chk(Math.abs(MathHelperRefactor.rotLerpRad(F(a), F(from), F(to)).toDouble() - wantRad) < 1e-9, "rotLerpRad " + i);
                }

                // ═══ 2. wrapDegrees 四形态 ═══
                // 2.1 整数版 vs 原版 int 公式
                for (int i = 0; i < 50_000; i++) {
                    final int v = r.nextInt();
                    int n = v % 360;
                    if (n >= 180) n -= 360;
                    if (n < -180) n += 360;
                    chk(MathHelperRefactor.wrapDegrees(IntRefactor256.of(v)).toInt() == n, "wrapDegrees int " + i);
                }
                // 2.2 浮点版 vs 原版 double 公式
                for (int i = 0; i < 20_000; i++) {
                    final double v = (r.nextInt(720_000) - 360_000) / 1000.0; // [-360, 360]
                    chk(Math.abs(MathHelperRefactor.wrapDegrees(F(v)).toDouble() - origWrap(v)) < 1e-9, "wrapDegrees float " + i);
                }
                // 2.3 wrapDegrees90
                for (int i = 0; i < 10_000; i++) {
                    final double v = (r.nextInt(180_000) - 90_000) / 1000.0;
                    double n = v % 90.0;
                    if (n >= 45.0) n -= 90.0;
                    if (n < -45.0) n += 90.0;
                    chk(Math.abs(MathHelperRefactor.wrapDegrees90(F(v)).toDouble() - n) < 1e-9, "wrapDegrees90 " + i);
                }
                // 2.4 degreesDifference / rotateIfNecessary / approach
                for (int i = 0; i < 10_000; i++) {
                    final double from = r.nextDouble() * 720 - 360, to = r.nextDouble() * 720 - 360;
                    chk(Math.abs(MathHelperRefactor.degreesDifference(F(from), F(to)).toDouble() - origWrap(to - from)) < 1e-9, "degDiff " + i);
                    final double maxDiff = r.nextDouble() * 90;
                    final double delta = origWrap(to - from);
                    final double wantRot = to - Math.max(-maxDiff, Math.min(maxDiff, delta));
                    chk(Math.abs(MathHelperRefactor.rotateIfNecessary(F(from), F(to), F(maxDiff)).toDouble() - wantRot) < 1e-9, "rotateIfNecessary " + i);
                }
                for (int i = 0; i < 10_000; i++) {
                    final double cur = r.nextDouble() * 100, tgt = r.nextDouble() * 100, inc = r.nextDouble() * 50;
                    // 原版 clamp(v,min,max) = v<min ? min : min(v,max)：cur<tgt → clamp(cur+inc,cur,tgt)；否则 clamp(cur-inc,tgt,cur)
                    final double want = cur < tgt
                            ? (cur + inc > tgt ? tgt : cur + inc)
                            : (cur - inc < tgt ? tgt : cur - inc);
                    chk(Math.abs(MathHelperRefactor.approach(F(cur), F(tgt), F(inc)).toDouble() - want) < 1e-9, "approach " + i);
                    chk(Math.abs(MathHelperRefactor.approachDegrees(F(cur), F(tgt), F(inc)).toDouble()
                            - MathHelperRefactor.approach(F(cur), F(cur + origWrap(tgt - cur)), F(inc)).toDouble()) < 1e-9, "approachDegrees " + i);
                }
                // triangleWave
                for (int i = 0; i < 10_000; i++) {
                    final double idx = r.nextDouble() * 10, per = 1.0 + r.nextDouble() * 4;
                    final double want = (Math.abs(idx % per - per * 0.5) - per * 0.25) / (per * 0.25);
                    chk(Math.abs(MathHelperRefactor.triangleWave(F(idx), F(per)).toDouble() - want) < 1e-9, "triangleWave " + i);
                }

                // ═══ 3. 256 专属大值场景（原版 int/double 溢出或精度崩） ═══
                // wrapDegrees 大整数角度（原版 int 溢出）
                chk(MathHelperRefactor.wrapDegrees(IntRefactor256.of(1_000_000_000_000L)).toBigInteger()
                        .equals(java.math.BigInteger.valueOf(1_000_000_000_000L % 360 == 280 ? 280 - 360 : 280)), "wrapDegrees 1e12");
                // wrapDegrees 256 位巨型角度
                final IntRefactor256 hugeAngle = IntRefactor256.of(java.math.BigInteger.ONE.shiftLeft(200));
                final IntRefactor256 wrapped = MathHelperRefactor.wrapDegrees(hugeAngle);
                chk(wrapped.compareTo(IntRefactor256.of(-180)) >= 0 && wrapped.compareTo(IntRefactor256.of(180)) < 0, "wrapDegrees 2^200 区间");
                // 与 BigInteger 参考一致
                final java.math.BigInteger ref = hugeAngle.toBigInteger().mod(java.math.BigInteger.valueOf(360));
                final java.math.BigInteger refNorm = ref.compareTo(java.math.BigInteger.valueOf(180)) >= 0
                        ? ref.subtract(java.math.BigInteger.valueOf(360)) : ref;
                chk(wrapped.toBigInteger().equals(refNorm), "wrapDegrees 2^200 值");
                // lerp 大坐标（地形插值场景）
                final FloatRefactor256 bigP0 = F(1e18), bigP1 = F(1e18 + 1000);
                chk(MathHelperRefactor.lerp(F(0.5), bigP0, bigP1).toDouble() > 1e18, "lerp 1e18");
                // smoothstep 边界
                chk(MathHelperRefactor.smoothstep(F(0.0)).toDouble() == 0.0 && MathHelperRefactor.smoothstep(F(1.0)).toDouble() == 1.0, "smoothstep 端点");

                System.out.println(fail == 0 ? "ALL PASS（MathHelperRefactor 批2）" : "TOTAL FAIL = " + fail);
    
        } finally {
            System.setOut(originalOut);
        }

        final String output = buffer.toString(java.nio.charset.StandardCharsets.UTF_8);
        final String meaningful = output.replaceAll("(?i)0\\s*(项|个)?\\s*FAIL", "");
        assertFalse(meaningful.contains("FAIL"), "脚本自检出现 FAIL：\n" + output);
    }

}
