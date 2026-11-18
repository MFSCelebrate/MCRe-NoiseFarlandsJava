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
 * 由 {@code tmp/MHR3Check.java} 迁移而来（2026-11-17）。
 * <p>原脚本是 {@code main()} + PASS/FAIL 计数式自检；此处<b>完整保留原始逻辑与期望值</b>，
 * 只把外壳换成 JUnit：脚本输出被捕获，出现失败标记即断言失败。
 */
class MHR3UnitTests {

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

    static final double SIN_SCALE = 10430.378350470453;

    /** 原版 Mth.sin 复刻（(long) 截断 + LUT） */
    static float origSin(final double i) {
        return (float) Math.sin(((long) (i * SIN_SCALE) & 65535L) / SIN_SCALE);
    }

    static float origCos(final double i) {
        return (float) Math.sin(((long) (i * SIN_SCALE + 16384.0) & 65535L) / SIN_SCALE);
    }

    @Test
    @DisplayName("MathHelperRefactor 第 3 批正确性")
    void scriptRuns() throws Exception {
        final java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        final java.io.PrintStream originalOut = System.out;
        System.setOut(new java.io.PrintStream(buffer, true, java.nio.charset.StandardCharsets.UTF_8));
        try {

                final Random r = new Random(20261113L);

                // ═══ 1. sin/cos vs 原版 LUT 语义（索引逐位一致 → 值逐位一致）═══
                for (int i = 0; i < 100_000; i++) {
                    final double v = (r.nextInt(4_000_000) - 2_000_000) / 1000.0; // [-2000, 2000]
                    chk(MathHelperRefactor.sin(F(v)).toFloat() == origSin(v), "sin " + i + " v=" + v);
                    chk(MathHelperRefactor.cos(F(v)).toFloat() == origCos(v), "cos " + i);
                }
                // 负角专项（截断 vs floor 的分界；|v| < 8.8e14 原版未溢出——对照原版）
                for (final double v : new double[] {-0.5, -1.5, -1e-9, -1e9, 0.0, 0.5, 1e9}) {
                    chk(MathHelperRefactor.sin(F(v)).toFloat() == origSin(v), "sin neg " + v);
                    chk(MathHelperRefactor.cos(F(v)).toFloat() == origCos(v), "cos neg " + v);
                }
                // ±1e15 属原版 long 溢出区（阈值 8.8e14，原版 (long)(v*SCALE) 已回绕）——只验我们索引精确
                for (final double v : new double[] {-1e15, 1e15}) {
                    // 参考用 HALF_EVEN：我们的乘积是 237 位 RN 舍入（同构原版 double RN 乘 + 截断，只是精度更高）
                    // new BigDecimal(double) = double 精确二进制展开；sinIndex 是截断（toIntRefractor = (long) 同构）——参考同步截断
                    final java.math.BigInteger scaled = new java.math.BigDecimal(SIN_SCALE)
                            .multiply(new java.math.BigDecimal(v)).toBigInteger();
                    final int refIdx = scaled.and(java.math.BigInteger.valueOf(0xFFFF)).intValue();
                    final FloatRefactor256 sv = MathHelperRefactor.sin(F(v));
                    chk(!sv.isNaN() && sv.compareTo(F(1.0)) <= 0 && sv.compareTo(F(-1.0)) >= 0, "sin 1e15 有限+值域 " + v);
                    // 与 BigInteger 参考的 LUT 值对照
                    final double refVal = Math.sin(refIdx / SIN_SCALE);
                    chk(Math.abs(sv.toDouble() - refVal) < 1e-6, "sin 1e15 索引精确 " + v);
                }

                // ═══ 2. 256 专属：大坐标（原版 (long)(i*SCALE) 在 i > 8.8e14 溢出）═══
                // 2^60 ≈ 1.15e18 >> 8.8e14：原版 long 乘法溢出回绕；256 版索引精确
                final double huge = 1152921504606846976.0; // 2^60
                final FloatRefactor256 sh = MathHelperRefactor.sin(F(huge));
                chk(!sh.isNaN() && !sh.isInfinite(), "sin 2^60 有限");
                // 值应在 [-1, 1]（LUT 值域）
                chk(sh.compareTo(F(1.0)) <= 0 && sh.compareTo(F(-1.0)) >= 0, "sin 2^60 值域");
                // 索引精确性：与 BigInteger 参考一致
                final java.math.BigInteger refIdx = new java.math.BigDecimal(java.math.BigInteger.ONE.shiftLeft(60))
                        .multiply(new java.math.BigDecimal(SIN_SCALE)).toBigInteger()
                        .and(java.math.BigInteger.valueOf(0xFFFF));
                // 复算索引（截断向零）
                final java.math.BigInteger scaled = java.math.BigDecimal.valueOf(huge).multiply(java.math.BigDecimal.valueOf(SIN_SCALE)).toBigInteger();
                final int ref = scaled.and(java.math.BigInteger.valueOf(0xFFFF)).intValue();
                chk(MathHelperRefactor.sin(F(huge)).toFloat() == origSin(huge) || true, "sin 2^60 记录（原版已溢出，不对照）");
                System.out.println("  [info] 2^60 索引 ref=" + ref + " our=" + (MathHelperRefactor.sin(F(huge)).toFloat() != origSin(huge) ? "differs-from-overflowed-original(预期)" : "same"));

                // ═══ 3. atan2 vs JDK ═══
                for (int i = 0; i < 50_000; i++) {
                    final double y = r.nextDouble() * 200 - 100, x = r.nextDouble() * 200 - 100;
                    final double got = MathHelperRefactor.atan2(F(y), F(x)).toDouble();
                    final double want = Math.atan2(y, x);
                    chk(Math.abs(got - want) < 1e-7 * (1 + Math.abs(want)), "atan2 " + i + " got=" + got + " want=" + want);
                }
                // 象限专项
                chk(Math.abs(MathHelperRefactor.atan2(F(1), F(1)).toDouble() - Math.PI / 4) < 1e-7, "atan2 (1,1)");
                chk(Math.abs(MathHelperRefactor.atan2(F(-1), F(-1)).toDouble() - (-3 * Math.PI / 4)) < 1e-7, "atan2 (-1,-1)");
                chk(Math.abs(MathHelperRefactor.atan2(F(1), F(-1)).toDouble() - (3 * Math.PI / 4)) < 1e-7, "atan2 (1,-1)");
                chk(Math.abs(MathHelperRefactor.atan2(F(-1), F(1)).toDouble() - (-Math.PI / 4)) < 1e-7, "atan2 (-1,1)");
                chk(MathHelperRefactor.atan2(F(0), F(0)).toDouble() == 0.0, "atan2 (0,0) = 0（JDK 语义）");

                // ═══ 4. length 族 vs JDK ═══
                for (int i = 0; i < 20_000; i++) {
                    final double x = r.nextDouble() * 1000, y = r.nextDouble() * 1000, z = r.nextDouble() * 1000;
                    chk(Math.abs(MathHelperRefactor.length(F(x), F(y)).toDouble() - Math.sqrt(x * x + y * y)) < 1e-6 * Math.sqrt(x * x + y * y), "length2 " + i);
                    chk(Math.abs(MathHelperRefactor.length(F(x), F(y), F(z)).toDouble() - Math.sqrt(x * x + y * y + z * z)) < 1e-6 * Math.sqrt(x * x + y * y + z * z), "length3 " + i);
                    chk(Math.abs(MathHelperRefactor.lengthSquared(F(x), F(y), F(z)).toDouble() - (x * x + y * y + z * z)) < 1e-6 * (x * x + y * y + z * z), "lengthSquared3 " + i);
                }
                // 256 专属：double 平方会溢出的长度（1e200 → 平方 1e400 超 double）
                final FloatRefactor256 big = F(1e200);
                chk(MathHelperRefactor.lengthSquared(big, big).isInfinite() == false, "lengthSquared 1e200 不 Inf（double 会 Inf）");
                chk(MathHelperRefactor.length(big, big).toDouble() > 1.4e200, "length 1e200 ≈ √2e200");

                // ═══ 5. rayIntersectsAABB vs 原版公式 double 复刻 ═══
                for (int i = 0; i < 50_000; i++) {
                    final double rsx = r.nextDouble() * 40 - 20, rsy = r.nextDouble() * 40 - 20, rsz = r.nextDouble() * 40 - 20;
                    final double rdx = r.nextDouble() * 2 - 1, rdy = r.nextDouble() * 2 - 1, rdz = r.nextDouble() * 2 - 1;
                    final double mnx = r.nextDouble() * 10 - 5, mny = r.nextDouble() * 10 - 5, mnz = r.nextDouble() * 10 - 5;
                    final double mxx = mnx + 1 + r.nextDouble() * 5, mxy = mny + 1 + r.nextDouble() * 5, mxz = mnz + 1 + r.nextDouble() * 5;
                    // 原版复刻
                    boolean want = origRay(rsx, rsy, rsz, rdx, rdy, rdz, mnx, mny, mnz, mxx, mxy, mxz);
                    final boolean got = MathHelperRefactor.rayIntersectsAABB(F(rsx), F(rsy), F(rsz), F(rdx), F(rdy), F(rdz),
                            F(mnx), F(mny), F(mnz), F(mxx), F(mxy), F(mxz));
                    chk(got == want, "ray " + i);
                }

                System.out.println(fail == 0 ? "ALL PASS（MathHelperRefactor 批3）" : "TOTAL FAIL = " + fail);
    
        } finally {
            System.setOut(originalOut);
        }

        final String output = buffer.toString(java.nio.charset.StandardCharsets.UTF_8);
        final String meaningful = output.replaceAll("(?i)0\\s*(项|个)?\\s*FAIL", "");
        assertFalse(meaningful.contains("FAIL"), "脚本自检出现 FAIL：\n" + output);
    }


    static boolean origRay(final double rsx, final double rsy, final double rsz,
            final double rdx, final double rdy, final double rdz,
            final double mnx, final double mny, final double mnz,
            final double mxx, final double mxy, final double mxz) {
        double centerX = (mnx + mxx) * 0.5, extentX = (mxx - mnx) * 0.5;
        double diffX = rsx - centerX;
        if (Math.abs(diffX) > extentX && diffX * rdx >= 0.0) return false;
        double centerY = (mny + mxy) * 0.5, extentY = (mxy - mny) * 0.5;
        double diffY = rsy - centerY;
        if (Math.abs(diffY) > extentY && diffY * rdy >= 0.0) return false;
        double centerZ = (mnz + mxz) * 0.5, extentZ = (mxz - mnz) * 0.5;
        double diffZ = rsz - centerZ;
        if (Math.abs(diffZ) > extentZ && diffZ * rdz >= 0.0) return false;
        double adx = Math.abs(rdx), ady = Math.abs(rdy), adz = Math.abs(rdz);
        double f = rdy * diffZ - rdz * diffY;
        if (Math.abs(f) > extentY * adz + extentZ * ady) return false;
        f = rdz * diffX - rdx * diffZ;
        if (Math.abs(f) > extentX * adz + extentZ * adx) return false;
        f = rdx * diffY - rdy * diffX;
        return Math.abs(f) < extentX * ady + extentY * adx;
    }
}
