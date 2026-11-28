package test;

import net.MinecraftTools.render.BedrockFloatVertex;
import org.junit.jupiter.api.Test;

/**
 * 🔧 MCRe「基岩版 条纹之地与切片之地」单元测试。
 *
 * <p>参照物：携带版 0.6.1 {@code client/renderer/TileRenderer.cpp}
 * （行 47-49 的 {@code float xf = (float)x;} + 行 1810-1811 等 6 个 {@code renderFace*} 的
 * {@code float x0 = x + tt->xx0; float x1 = x + tt->xx1;}）。
 *
 * <p>测试目标：
 * <ol>
 *   <li>正常区间（< 2^23）面宽恒为 1 → 开关开启对近处零影响；</li>
 *   <li>2^24 = 16777216 起面宽序列与携带版实测逐位一致（周期 3 的条纹）；</li>
 *   <li>2^25 起面宽按 4/8/16 跳变（切片之地）；</li>
 *   <li>局部坐标与携带版「绝对 float32 角点 + float32(-区块原点)」逐位相同；</li>
 *   <li>区块原点的减法不引入二次舍入。</li>
 * </ol>
 */
public class BedrockFloatVertexUnitTests {

    /** 携带版 TileRenderer 实测：2^24 起 12 格的 X 轴面宽序列（周期 3） */
    private static final float[] BEDROCK_STRIPE_PATTERN = {0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 0.0F, 2.0F, 0.0F};

    /** 携带版写法：顶点 = float32(绝对坐标) + 角点 + float32(-区块原点) */
    private static float bedrockReferenceVertex(final long absBlockCoord, final float corner, final long originBlockCoord) {
        final float absolute = (float) absBlockCoord + corner;
        return absolute + (float) (-originBlockCoord);
    }

    @Test
    public void normalRangeKeepsUnitFaceWidth() {
        final long origin = 1L << 24; // 区块原点（16 的倍数 → float32 精确可表示）
        for (long x = (1L << 20); x < (1L << 23); x += 4096) {
            final float width = BedrockFloatVertex.localFaceWidth(x, (float) origin);
            if (width != 1.0F) {
                throw new AssertionError("正常区间面宽应为 1，实际 " + width + " @ x=" + x);
            }
        }
    }

    @Test
    public void stripePatternMatchesBedrockReference() {
        final long origin = 1L << 24;
        final float originFloat = (float) origin;
        for (int i = 0; i < BEDROCK_STRIPE_PATTERN.length; i++) {
            final long x = origin + i;
            final float width = BedrockFloatVertex.localFaceWidth(x, originFloat);
            if (width != BEDROCK_STRIPE_PATTERN[i]) {
                throw new AssertionError(
                    "条纹序列不匹配 @ x=" + x + "：期望 " + BEDROCK_STRIPE_PATTERN[i] + " 实际 " + width
                );
            }
        }
    }

    @Test
    public void slicedLandsAppearAbove2Pow25() {
        final long origin = 1L << 25;
        final float originFloat = (float) origin;
        for (int i = 0; i < 12; i++) {
            final long x = origin + i;
            final float width = BedrockFloatVertex.localFaceWidth(x, originFloat);
            // ULP = 4 → 面宽只能是 0 / 4 / 8 … 的整数倍
            if (width != 0.0F && width != 4.0F && width != 8.0F) {
                throw new AssertionError("切片区间面宽应为 4 的整数倍，实际 " + width + " @ x=" + x);
            }
            if (width == 1.0F) {
                throw new AssertionError("切片区间不应出现宽度 1 的面 @ x=" + x);
            }
        }
    }

    @Test
    public void localCornerIsBitIdenticalToBedrockReference() {
        final long origin = 1L << 24;
        final float originFloat = (float) origin;
        for (int i = -4; i < 12; i++) {
            final long x = origin + i;
            for (final float corner : new float[] {0.0F, 1.0F, 0.5F, 0.001F}) {
                final float ours = BedrockFloatVertex.localCorner(x, corner, originFloat);
                final float reference = bedrockReferenceVertex(x, corner, origin);
                if (Float.floatToRawIntBits(ours) != Float.floatToRawIntBits(reference)) {
                    throw new AssertionError(
                        "与携带版不一致 @ x=" + x + " corner=" + corner + "：我们 " + ours + " 携带版 " + reference
                    );
                }
            }
        }
    }

    @Test
    public void originSubtractionIntroducesNoSecondRounding() {
        final long origin = 1L << 24;
        final float originFloat = (float) origin;
        for (int i = -4; i < 12; i++) {
            final long x = origin + i;
            final float ours = BedrockFloatVertex.localCorner(x, 1.0F, originFloat);
            // 携带版语义：角点先在 float32 绝对量级上相加（这一步【就该】被量化），
            // 之后减去区块原点必须是【精确】的 —— 不能再引入二次舍入。
            final float quantizedCorner = BedrockFloatVertex.quantizeBlockCoord(x) + 1.0F;
            final double exact = (double) quantizedCorner - (double) origin;
            if ((double) ours != exact) {
                throw new AssertionError("区块原点减法引入二次舍入 @ x=" + x + "：我们 " + ours + " 精确 " + exact);
            }
        }
    }

    @Test
    public void quantizationWorksBeyondIntRange() {
        // 超出 32 位 int 的坐标（我们的世界用 long）→ 仍按 float32 量化
        final long origin = 1L << 40;
        final float originFloat = (float) origin;
        final float width = BedrockFloatVertex.localFaceWidth(origin + 3L, originFloat);
        // ULP(2^40) = 131072 → 面宽是它的整数倍
        if (width == 1.0F) {
            throw new AssertionError("超出 int 范围后仍应发生量化，实际面宽 " + width);
        }
        if (width % 131072.0F != 0.0F) {
            throw new AssertionError("面宽应是 ULP 的整数倍，实际 " + width);
        }
    }
}
