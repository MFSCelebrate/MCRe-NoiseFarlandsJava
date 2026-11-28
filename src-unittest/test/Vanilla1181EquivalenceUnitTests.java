package test;

import org.junit.jupiter.api.Test;

/**
 * 🔧 MCRe「1.18.1 地形语义等价性」单元测试（纯数学，不依赖 Minecraft 运行时）。
 *
 * <p>钉住 {@code MCVanilla1_18_1Pipeline} 注释与 Wiki 里声称的 1.18.1 ↔ 26.2 等价关系：
 * <ol>
 *   <li>1.18.1 的初始密度分段 {@code g × (g > 0 ? 4 : 1)} ≡ 26.2 的 {@code 4 × quarterNegative(g)}；</li>
 *   <li>1.18.1 的 depth {@code (1 − Y/128) + offset} 在 Y∈[−64,320] 内 ≡ 26.2 的
 *       {@code yClampedGradient(−64,320,1.5,−1.5) + offset}（窗外发散）；</li>
 *   <li>1.18.1 的 jaggedness 合成 {@code h>0 ? d×h : d/2×h} ≡ 26.2 的 {@code d × halfNegative(h)}；</li>
 *   <li>1.18.1 的 {@code peaksAndValleys}（float 常量）与 26.2 的节点版（double 常量）存在末位 ulp 差异
 *       → 管线必须用 float 版。</li>
 * </ol>
 */
public class Vanilla1181EquivalenceUnitTests {

    /** 1.18.1 NoiseSampler.calculateBaseNoise 的分段放大 */
    private static double legacyPiecewise(final double g) {
        return g * (g > 0.0 ? 4.0 : 1.0);
    }

    /** 26.2 的 4 × quarterNegative(g) */
    private static double modernQuarterNegative(final double g) {
        final double quarterNegative = g < 0.0 ? g / 4.0 : g;
        return 4.0 * quarterNegative;
    }

    /** 1.18.1 NoiseSampler.computeBaseDensity 的 depth（不含 offset） */
    private static double legacyDepth(final int blockY) {
        return 1.0 - blockY / 128.0;
    }

    /** 26.2 的 yClampedGradient(-64, 320, 1.5, -1.5) */
    private static double modernDepthGradient(final int blockY) {
        final double t = Math.min(1.0, Math.max(0.0, (blockY + 64.0) / 384.0));
        return 1.5 + (1.5 - (-1.5)) * t * -1.0;
    }

    /** 1.18.1 TerrainShaper.peaksAndValleys（float 常量） */
    private static float legacyPeaksAndValleys(final float weirdness) {
        return -(Math.abs(Math.abs(weirdness) - 0.6666667F) - 0.33333334F) * 3.0F;
    }

    /** 26.2 节点版（double 常量） */
    private static double modernPeaksAndValleys(final double weirdness) {
        return -((Math.abs(Math.abs(weirdness) - 0.6666666666666666) - 0.3333333333333333) * 3.0);
    }

    @Test
    public void initialDensityPiecewiseEqualsQuarterNegative() {
        for (final double g : new double[] {-1000.0, -8.0, -1.0, -0.25, -1.0E-9, 0.0, 1.0E-9, 0.25, 1.0, 8.0, 1000.0}) {
            final double legacy = legacyPiecewise(g);
            final double modern = modernQuarterNegative(g);
            if (Double.doubleToRawLongBits(legacy) != Double.doubleToRawLongBits(modern)) {
                throw new AssertionError("初始密度分段不等价 @ g=" + g + "：1.18.1 " + legacy + " / 26.2 " + modern);
            }
        }
    }

    @Test
    public void depthEqualsLinearGradientInsideWorldHeight() {
        for (int y = -64; y <= 320; y++) {
            final double legacy = legacyDepth(y);
            final double modern = modernDepthGradient(y);
            if (Math.abs(legacy - modern) > 1.0E-12) {
                throw new AssertionError("正常高度区间 depth 不等价 @ Y=" + y + "：1.18.1 " + legacy + " / 26.2 " + modern);
            }
        }
    }

    @Test
    public void depthDivergesOutsideWorldHeight() {
        // 窗外：1.18.1 继续线性延伸，26.2 被钳制 → 这正是 1.18.1 管线要用原版 depth 的原因
        final double legacyAbove = legacyDepth(320 + 64);
        final double modernAbove = modernDepthGradient(320 + 64);
        if (Math.abs(legacyAbove - modernAbove) < 0.25) {
            throw new AssertionError("窗外 depth 应发散（1.18.1 线性 vs 26.2 钳制），实测 1.18.1 " + legacyAbove + " / 26.2 " + modernAbove);
        }

        final double legacyBelow = legacyDepth(-64 - 64);
        final double modernBelow = modernDepthGradient(-64 - 64);
        if (Math.abs(legacyBelow - modernBelow) < 0.25) {
            throw new AssertionError("窗外 depth 应发散（1.18.1 线性 vs 26.2 钳制），实测 1.18.1 " + legacyBelow + " / 26.2 " + modernBelow);
        }
    }

    @Test
    public void jaggednessSynthesisMatchesHalfNegative() {
        for (final double h : new double[] {-1.0, -0.5, -1.0E-9, 0.0, 1.0E-9, 0.5, 1.0}) {
            for (final double d : new double[] {-2.0, -0.3, 0.0, 0.3, 2.0}) {
                final double legacy = h > 0.0 ? d * h : d / 2.0 * h;
                final double halfNegative = h < 0.0 ? h / 2.0 : h;
                final double modern = d * halfNegative;
                if (Double.doubleToRawLongBits(legacy) != Double.doubleToRawLongBits(modern)) {
                    throw new AssertionError("jaggedness 合成不等价 @ h=" + h + " d=" + d + "：1.18.1 " + legacy + " / 26.2 " + modern);
                }
            }
        }
    }

    @Test
    public void peaksAndValleysFloatVersusDoubleHasUlpDifference() {
        // 记录 float 版与 double 版的差异（管线用 float 版 → 与 1.18.1 逐位一致）
        int differing = 0;
        float maxUlp = 0.0F;
        for (int i = -1000; i <= 1000; i++) {
            final float w = i / 1000.0F;
            final float legacy = legacyPeaksAndValleys(w);
            final double modern = modernPeaksAndValleys(w);
            if (Float.floatToRawIntBits(legacy) != (float) modern) {
                differing++;
                maxUlp = Math.max(maxUlp, Math.abs(legacy - (float) modern));
            }
        }

        if (differing == 0) {
            throw new AssertionError("预期 float 版与 double 版存在末位差异（若完全一致则可简化节点实现）");
        }

        if (maxUlp > 1.0E-5F) {
            throw new AssertionError("差异过大（应仅为末位 ulp 量级），实测 max=" + maxUlp);
        }
    }

    @Test
    public void peaksAndValleysFloatMatchesLegacyExactly() {
        // float 版必须与 1.18.1 的 float 公式逐位一致（回归守卫）
        for (int i = -1000; i <= 1000; i++) {
            final float w = i / 1000.0F;
            final float expected = -(Math.abs(Math.abs(w) - 0.6666667F) - 0.33333334F) * 3.0F;
            final float actual = legacyPeaksAndValleys(w);
            if (Float.floatToRawIntBits(expected) != Float.floatToRawIntBits(actual)) {
                throw new AssertionError("peaksAndValleys float 版与 1.18.1 公式不一致 @ w=" + w);
            }
        }
    }
}
