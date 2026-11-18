package test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import net.minecraft.util.MathUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.MinecraftTools.terrain.pipeline.mcbedrockgen.density.FloatDensity;

/**
 * 由 {@code tmp/DiagFloatChainMath.java} 迁移而来（2026-11-17）。
 * <p>原脚本是 {@code main()} + PASS/FAIL 计数式自检；此处<b>完整保留原始逻辑与期望值</b>，
 * 只把外壳换成 JUnit：脚本输出被捕获，出现失败标记即断言失败。
 */
class DiagFloatChainMathTests {


    private static int fails = 0;
    private static int checks = 0;

    @Test
    @DisplayName("Float 密度链与主线逐点对照（20 采样 + canary）")
    void scriptRuns() throws Exception {
        final java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        final java.io.PrintStream originalOut = System.out;
        System.setOut(new java.io.PrintStream(buffer, true, java.nio.charset.StandardCharsets.UTF_8));
        try {

                // 主线 DensityFunctions 静态初始化依赖注册表 → 必须先 bootstrap（0.6.1 管线的同款前置）
                net.minecraft.SharedConstants.tryDetectVersion();
                net.minecraft.server.Bootstrap.bootStrap();
                final int X = 37;
                final int Y = 64;
                final int Z = -91;
                final DensityFunction.FunctionContext ctx = new DensityFunction.SinglePointContext(X, Y, Z);

                // ① add
                check("add", DensityFunctions.add(DensityFunctions.constant(1.5), DensityFunctions.constant(-0.25)).compute(ctx),
                        new FloatDensity.AddF(new FloatDensity.ConstantF(1.5f), new FloatDensity.ConstantF(-0.25f)).compute(X, Y, Z), 1.0E-6);

                // ② mul
                check("mul", DensityFunctions.mul(DensityFunctions.constant(1.5), DensityFunctions.constant(-0.25)).compute(ctx),
                        new FloatDensity.MulF(new FloatDensity.ConstantF(1.5f), new FloatDensity.ConstantF(-0.25f)).compute(X, Y, Z), 1.0E-6);

                // ③ min / max
                check("min", DensityFunctions.min(DensityFunctions.constant(1.5), DensityFunctions.constant(-0.25)).compute(ctx),
                        new FloatDensity.MinF(new FloatDensity.ConstantF(1.5f), new FloatDensity.ConstantF(-0.25f)).compute(X, Y, Z), 1.0E-6);
                check("max", DensityFunctions.max(DensityFunctions.constant(1.5), DensityFunctions.constant(-0.25)).compute(ctx),
                        new FloatDensity.MaxF(new FloatDensity.ConstantF(1.5f), new FloatDensity.ConstantF(-0.25f)).compute(X, Y, Z), 1.0E-6);

                // ④ clamp
                check("clamp", DensityFunctions.constant(3.0).clamp(-1.0, 1.0).compute(ctx),
                        new FloatDensity.ClampF(new FloatDensity.ConstantF(3.0f), -1.0f, 1.0f).compute(X, Y, Z), 1.0E-6);

                // ⑤ range_choice
                check("rangeChoice(in)", DensityFunctions.rangeChoice(DensityFunctions.constant(0.5), -1.0, 1.0,
                        DensityFunctions.constant(7.0), DensityFunctions.constant(9.0)).compute(ctx),
                        new FloatDensity.RangeChoiceF(new FloatDensity.ConstantF(0.5f), -1.0f, 1.0f,
                                new FloatDensity.ConstantF(7.0f), new FloatDensity.ConstantF(9.0f)).compute(X, Y, Z), 1.0E-6);
                check("rangeChoice(out)", DensityFunctions.rangeChoice(DensityFunctions.constant(4.5), -1.0, 1.0,
                        DensityFunctions.constant(7.0), DensityFunctions.constant(9.0)).compute(ctx),
                        new FloatDensity.RangeChoiceF(new FloatDensity.ConstantF(4.5f), -1.0f, 1.0f,
                                new FloatDensity.ConstantF(7.0f), new FloatDensity.ConstantF(9.0f)).compute(X, Y, Z), 1.0E-6);

                // ⑥ y_clamped_gradient
                check("yClampedGradient", DensityFunctions.yClampedGradient(-64, 320, 1.5, -1.5).compute(ctx),
                        new FloatDensity.YClampedGradientF(-64, 320, 1.5f, -1.5f).compute(X, Y, Z), 1.0E-6);

                // ⑥b lerp（主线 lerp(alpha, first, second) = first + alpha×(second-first)）
                check("lerp", DensityFunctions.lerp(DensityFunctions.constant(0.25), DensityFunctions.constant(1.0), DensityFunctions.constant(3.0)).compute(ctx),
                        new FloatDensity.LerpF(new FloatDensity.ConstantF(0.25f), new FloatDensity.ConstantF(1.0f), new FloatDensity.ConstantF(3.0f)).compute(X, Y, Z), 1.0E-6);
                check("lerp(p=1)", DensityFunctions.lerp(DensityFunctions.constant(1.0), DensityFunctions.constant(1.0), DensityFunctions.constant(3.0)).compute(ctx),
                        new FloatDensity.LerpF(new FloatDensity.ConstantF(1.0f), new FloatDensity.ConstantF(1.0f), new FloatDensity.ConstantF(3.0f)).compute(X, Y, Z), 1.0E-6);

                // ⑦ squeeze
                check("squeeze(0.7)", DensityFunctions.constant(0.7).squeeze().compute(ctx),
                        new FloatDensity.SqueezeF(new FloatDensity.ConstantF(0.7f)).compute(X, Y, Z), 1.0E-6);
                check("squeeze(-1.7)", DensityFunctions.constant(-1.7).squeeze().compute(ctx),
                        new FloatDensity.SqueezeF(new FloatDensity.ConstantF(-1.7f)).compute(X, Y, Z), 1.0E-6);

                // ⑧ find_top_surface
                check("findTopSurface", DensityFunctions.findTopSurface(DensityFunctions.constant(1.0),
                                DensityFunctions.constant(300.0), -64, 8).compute(ctx),
                        new FloatDensity.FindTopSurfaceF(new FloatDensity.ConstantF(1.0f), new FloatDensity.ConstantF(300.0f), -64, 8).compute(X, Y, Z), 1.0E-6);
                check("findTopSurface(neg)", DensityFunctions.findTopSurface(DensityFunctions.constant(-1.0),
                                DensityFunctions.constant(300.0), -64, 8).compute(ctx),
                        new FloatDensity.FindTopSurfaceF(new FloatDensity.ConstantF(-1.0f), new FloatDensity.ConstantF(300.0f), -64, 8).compute(X, Y, Z), 1.0E-6);

                // ⑨ interval_select
                final DoubleArrayList thresholds = new DoubleArrayList(new double[] { -0.5, 0.0, 0.5 });
                final List<DensityFunction> funcs = ImmutableList.of(
                        DensityFunctions.constant(1.0), DensityFunctions.constant(2.0),
                        DensityFunctions.constant(3.0), DensityFunctions.constant(4.0));
                check("intervalSelect(-0.75)", DensityFunctions.intervalSelect(DensityFunctions.constant(-0.75), thresholds, funcs).compute(ctx),
                        intervalSelect(new FloatDensity.ConstantF(-0.75f), new float[] { -0.5f, 0.0f, 0.5f },
                                new FloatDensity.ConstantF(1.0f), new FloatDensity.ConstantF(2.0f),
                                new FloatDensity.ConstantF(3.0f), new FloatDensity.ConstantF(4.0f)).compute(X, Y, Z), 1.0E-6);
                check("intervalSelect(0.25)", DensityFunctions.intervalSelect(DensityFunctions.constant(0.25), thresholds, funcs).compute(ctx),
                        intervalSelect(new FloatDensity.ConstantF(0.25f), new float[] { -0.5f, 0.0f, 0.5f },
                                new FloatDensity.ConstantF(1.0f), new FloatDensity.ConstantF(2.0f),
                                new FloatDensity.ConstantF(3.0f), new FloatDensity.ConstantF(4.0f)).compute(X, Y, Z), 1.0E-6);
                check("intervalSelect(0.75)", DensityFunctions.intervalSelect(DensityFunctions.constant(0.75), thresholds, funcs).compute(ctx),
                        intervalSelect(new FloatDensity.ConstantF(0.75f), new float[] { -0.5f, 0.0f, 0.5f },
                                new FloatDensity.ConstantF(1.0f), new FloatDensity.ConstantF(2.0f),
                                new FloatDensity.ConstantF(3.0f), new FloatDensity.ConstantF(4.0f)).compute(X, Y, Z), 1.0E-6);

                // ⑩ 插值三线性：主线 Mth.lerp3 vs 本家族 InterpolatedF
                // ⚠️ source 必须用**非线性**（位置哈希）——线性函数对"角点位置错误"免疫（线性外推仍成立），
                //    初版 Diag 用线性 source → 漏掉了 Z 角点偏移 16 格的真 bug（实测地表竖直条纹 + 洞穴爆炸）。
                final FloatDensity.F hashed = (x, y, z) -> hashVal(x, y, z);
                final float interp = new FloatDensity.InterpolatedF(hashed).compute(X, Y, Z);
                final int cellX = Math.floorDiv(X, 4);
                final int cellY = Math.floorDiv(Y, 8);
                final int cellZ = Math.floorDiv(Z, 4);
                final double fx = (X - cellX * 4) / 4.0;
                final double fy = (Y - cellY * 8) / 8.0;
                final double fz = (Z - cellZ * 4) / 4.0;
                // 角点序 nXYZ：bit0=x(+4)、bit1=y(+8)、bit2=z(+4)
                final double[] corners = new double[8];
                for (int i = 0; i < 8; i++) {
                    final int dx = (i & 1) * 4;
                    final int dy = ((i >> 1) & 1) * 8;
                    final int dz = ((i >> 2) & 1) * 4;
                    corners[i] = hashVal(cellX * 4 + dx, cellY * 8 + dy, cellZ * 4 + dz);
                }
                // 主线 Mth.lerp3 参数序：n000, n100, n010, n110, n001, n101, n011, n111
                check("lerp3(nXYZ)", Mth.lerp3(fx, fy, fz, corners[0], corners[1], corners[2], corners[3], corners[4], corners[5], corners[6], corners[7]), interp, 1.0E-5);
                // 反序对照组（若这组也吻合说明角点序退化，需人工确认）
                check("lerp3(alt-order)", Mth.lerp3(fx, fy, fz, corners[0], corners[4], corners[2], corners[6], corners[1], corners[5], corners[3], corners[7]), interp, 1.0E-5);

                // 反证 canary：旧 bug（Z 角点偏移 16 格）必须与正确值明显不同 → 证明本 Diag 对角点位置敏感
                final float[] buggyCorners = new float[8];
                for (int i = 0; i < 8; i++) {
                    final int dx = (i & 1) * 4;
                    final int dy = ((i >> 1) & 1) * 8;
                    final int dz = ((i >> 2) & 1) * 16;
                    buggyCorners[i] = hashVal(cellX * 4 + dx, cellY * 8 + dy, cellZ * 4 + dz);
                }
                final double buggyDiff = Math.abs(lerp3Float(fx, fy, fz, buggyCorners) - interp);
                checks++;
                if (buggyDiff <= 1.0E-4) {
                    fails++;
                    System.out.printf("%-26s canary FAILED（buggy 与正确值几乎相同 → Diag 对角点位置不敏感）%n", "canary(buggyZ!=ok)");
                } else {
                    System.out.printf("%-26s canary OK（buggy diff=%.4f → Diag 能抓角点位置错）%n", "canary(buggyZ!=ok)", buggyDiff);
                }

                // ⑪ 天空网格开关：插值语义（MathUtil.lerp vs Mth.lerp）+ 钳制语义（MathUtil.clamp vs Mth.clamp）
                final float inf = Float.POSITIVE_INFINITY;
                check("skyLerp(delta0,Inf)", MathUtil.lerp(0.0, inf, -inf),
                        FloatDensity.lerpWithMode(0.0f, inf, -inf, true), 0.0);
                check("skyLerp(delta1,Inf)", MathUtil.lerp(1.0, inf, -inf),
                        FloatDensity.lerpWithMode(1.0f, inf, -inf, true), 0.0);
                check("skyLerp(mid,Inf)=NaN", Double.isNaN(MathUtil.lerp(0.5, inf, -inf)) ? 1.0f : 0.0f,
                        Double.isNaN(FloatDensity.lerpWithMode(0.5f, inf, -inf, true)) ? 1.0f : 0.0f, 0.0);
                check("skyLerp(mid,finite)", MathUtil.lerp(0.5, 2.0, 6.0),
                        FloatDensity.lerpWithMode(0.5f, 2.0f, 6.0f, true), 1.0E-6);
                check("skyLerp(equal)", MathUtil.lerp(0.5, 3.0, 3.0),
                        FloatDensity.lerpWithMode(0.5f, 3.0f, 3.0f, true), 1.0E-6);
                check("noSkyLerp(mid,Inf)=NaN", Double.isNaN(Mth.lerp(0.5, inf, -inf)) ? 1.0f : 0.0f,
                        Double.isNaN(FloatDensity.lerpWithMode(0.5f, inf, -inf, false)) ? 1.0f : 0.0f, 0.0);
                check("noSkyLerp(delta0,Inf)=NaN", Double.isNaN(Mth.lerp(0.0, inf, -inf)) ? 1.0f : 0.0f,
                        Double.isNaN(FloatDensity.lerpWithMode(0.0f, inf, -inf, false)) ? 1.0f : 0.0f, 0.0);
                check("forceClamp(Inf)", MathUtil.clamp(inf, -1.0, 1.0),
                        FloatDensity.clampWithMode(inf, -1.0f, 1.0f, true), 0.0);
                check("noForceClamp(Inf)", Mth.clamp(inf, -1.0, 1.0),
                        FloatDensity.clampWithMode(inf, -1.0f, 1.0f, false), 0.0);

                System.out.printf("%n=== Diag 结果：%d 项检查，%d 项 FAIL ===%n", checks, fails);
                System.out.println(fails > 0 ? "RESULT=FAIL" : "RESULT=PASS");
    
        } finally {
            System.setOut(originalOut);
        }

        final String output = buffer.toString(java.nio.charset.StandardCharsets.UTF_8);
        final String meaningful = output.replaceAll("(?i)0\\s*(项|个)?\\s*FAIL", "");
        assertFalse(meaningful.contains("FAIL"), "脚本自检出现 FAIL：\n" + output);
    }


    private static FloatDensity.F intervalSelect(final FloatDensity.F input, final float[] thresholds, final FloatDensity.F... functions) {
        return (x, y, z) -> {
            final float v = input.compute(x, y, z);
            for (int i = 0; i < thresholds.length; i++) {
                if (v < thresholds[i]) {
                    return functions[i].compute(x, y, z);
                }
            }
            return functions[functions.length - 1].compute(x, y, z);
        };
    }

    /** 与 FloatDensity.InterpolatedF 同款三线性（用于 canary 对照）。 */
    private static float lerp3Float(final double fx, final double fy, final double fz, final float[] c) {
        final float v00 = (float) (c[0] + fx * (c[1] - c[0]));
        final float v10 = (float) (c[2] + fx * (c[3] - c[2]));
        final float v01 = (float) (c[4] + fx * (c[5] - c[4]));
        final float v11 = (float) (c[6] + fx * (c[7] - c[6]));
        final float v0 = (float) (v00 + fy * (v10 - v00));
        final float v1 = (float) (v01 + fy * (v11 - v01));
        return (float) (v0 + fz * (v1 - v0));
    }

    /** 位置哈希（非线性）：用于验证插值角点位置——线性函数对角点偏移免疫。 */
    private static float hashVal(final int x, final int y, final int z) {
        long h = (long) x * 0x9E3779B97F4A7C15L ^ (long) y * 0xC2B2AE3D27D4EB4FL ^ (long) z * 0x165667B19E3779F9L;
        h ^= (h >>> 29);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 32);
        return ((h >>> 40) & 0xFFFFFF) / 16777216.0f - 0.5f;
    }

    private static void check(final String name, final double mainline, final float mine, final double tolerance) {
        checks++;
        final boolean same = (Double.isNaN(mainline) && Double.isNaN(mine))
                || (Double.isInfinite(mainline) && Double.isInfinite(mine) && Math.signum(mainline) == Math.signum(mine));
        final double diff = same ? 0.0 : Math.abs(mainline - mine);
        final boolean ok = same || diff <= tolerance;
        if (!ok) {
            fails++;
        }
        System.out.printf("%-26s main=%.8f  float=%.8f  diff=%.3e  %s%n", name, mainline, mine, diff, ok ? "OK" : "FAIL");
    }
}
