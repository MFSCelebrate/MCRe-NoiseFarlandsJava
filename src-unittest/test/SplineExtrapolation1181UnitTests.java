package test;

import net.MinecraftTools.terrain.pipeline.SplineExtrapolation1181;
import org.junit.jupiter.api.Test;

/**
 * 🔧 MCRe「1.18.1 样条外推」单元测试。
 *
 * <p>参照物：Minecraft 1.18.1 的 {@code CubicSpline} 端点外推公式
 * 「端点值 + 端点斜率 × (输入 − 端点位置)」（无 1.19.2+ 的 linearExtend 保护）。
 */
public class SplineExtrapolation1181UnitTests {

    /** 1.19.2+（26.2 现版）的捷径：零斜率直接返回端点值。 */
    private static float modernExtend(final float input, final float location, final float value, final float derivative) {
        return derivative == 0.0F ? value : value + derivative * (input - location);
    }

    @Test
    public void zeroSlopeWithInfinityBecomesNaN() {
        // ★ 1.18.1 的核心行为：0 × Infinity = NaN（文章里「渐消之地」崩坏的末环）
        final float result = SplineExtrapolation1181.extend(Float.POSITIVE_INFINITY, 0.0F, 1.5F, 0.0F);
        if (!Float.isNaN(result)) {
            throw new AssertionError("1.18.1 语义下 0 斜率 × Infinity 应为 NaN，实际 " + result);
        }

        final float negative = SplineExtrapolation1181.extend(Float.NEGATIVE_INFINITY, 0.0F, -1.5F, 0.0F);
        if (!Float.isNaN(negative)) {
            throw new AssertionError("1.18.1 语义下 0 斜率 × -Infinity 应为 NaN，实际 " + negative);
        }
    }

    @Test
    public void modernShortcutKeepsFiniteValue() {
        // 对照：26.2 现版（linearExtend）零斜率直接返回端点值 → 有限
        final float modern = modernExtend(Float.POSITIVE_INFINITY, 0.0F, 1.5F, 0.0F);
        if (modern != 1.5F) {
            throw new AssertionError("26.2 语义下零斜率应返回端点值 1.5，实际 " + modern);
        }

        if (Float.isNaN(modern)) {
            throw new AssertionError("26.2 语义不应产生 NaN");
        }
    }

    @Test
    public void zeroSlopeWithFiniteInputKeepsEndpointValue() {
        // 有限输入下两种语义完全一致（0 × 有限 = 0）
        for (final float input : new float[] {-100.0F, -1.0F, 0.0F, 1.0F, 100.0F}) {
            final float legacy = SplineExtrapolation1181.extend(input, 0.25F, 2.0F, 0.0F);
            final float modern = modernExtend(input, 0.25F, 2.0F, 0.0F);
            if (legacy != modern) {
                throw new AssertionError("有限输入下两种语义应一致 @ input=" + input + "：1.18.1 " + legacy + " / 26.2 " + modern);
            }
        }
    }

    @Test
    public void nonZeroSlopeMatchesModernSemantics() {
        // 非零斜率：两种语义本来就一样（捷径只在斜率为零时生效）
        for (final float input : new float[] {-100.0F, -1.0F, 0.0F, 1.0F, 100.0F, Float.POSITIVE_INFINITY}) {
            final float legacy = SplineExtrapolation1181.extend(input, 0.5F, 3.0F, 0.25F);
            final float modern = modernExtend(input, 0.5F, 3.0F, 0.25F);
            if (Float.floatToRawIntBits(legacy) != Float.floatToRawIntBits(modern)) {
                throw new AssertionError("非零斜率下两种语义应逐位一致 @ input=" + input + "：1.18.1 " + legacy + " / 26.2 " + modern);
            }
        }
    }

    @Test
    public void extrapolationFormulaIsExact() {
        // 公式本身：value + derivative * (input - location)
        final float value = 2.5F;
        final float location = -0.75F;
        final float derivative = 0.125F;
        final float input = 3.25F;
        final float expected = value + derivative * (input - location);
        final float actual = SplineExtrapolation1181.extend(input, location, value, derivative);
        if (Float.floatToRawIntBits(expected) != Float.floatToRawIntBits(actual)) {
            throw new AssertionError("外推公式不符：期望 " + expected + " 实际 " + actual);
        }
    }
}
