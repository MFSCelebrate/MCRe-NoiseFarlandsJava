package test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.MinecraftTools.precision.PrecisePosition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 🔧 MCRe 单元测试：<b>高精度位置（{@link PrecisePosition}）</b>的语义。
 *
 * <p>守住三件事：
 * <ol>
 *   <li><b>往返精确</b>：{@code of(double).x()} 必须与原 double 逐位相同（否则普通坐标都会漂）；</li>
 *   <li><b>累加不失真</b>：1e46 处加 0.1，double 会归零，而高精度位置必须保留小数；</li>
 *   <li><b>投影回填保护</b>：投影相同的回填不得把精确值降级。</li>
 * </ol>
 */
class PrecisePositionUnitTests {

    @Test
    @DisplayName("往返精确：of(double) 的投影必须与原 double 逐位相同")
    void roundTripIsExact() {
        final double[] values = {
                0.1, 1.0 / 3.0, 1e15, 1e20, 1e30, 1e46, 1e51, 1e60, Math.PI, 1.2345678901234567e46,
        };

        for (final double value : values) {
            final PrecisePosition precise = PrecisePosition.of(value, value, value);
            assertEquals(
                    Double.doubleToRawLongBits(value),
                    Double.doubleToRawLongBits(precise.x()),
                    "往返不精确: " + value
            );
        }
    }

    @Test
    @DisplayName("累加不失真：1e46 处加 0.1，double 归零而高精度位置保留小数")
    void accumulationKeepsFraction() {
        final double base = 1.2345678901234567e46;

        // 对照：double 在这个量级加 0.1 直接归零
        assertEquals(base, base + 0.1, "double 在 1e46 处加 0.1 本应归零（对照组）");

        final PrecisePosition precise = PrecisePosition.of(base, base, base);
        precise.add(0.1, 0.1, 0.1);

        // 出口仍是 double → 投影在 1e46 处必然舍掉小数（这是设计，不是 bug）
        assertEquals(base, precise.x(), "double 出口的投影应与原值相同（出口语义不变）");

        // 精度活在精确值里：小数必须留住
        assertTrue(
                precise.exactX().toExactString().contains(".1"),
                "精确值应含小数部分，实际: " + precise.exactX().toExactString()
        );

        // 再累加一次：两次的小数都要保住（这才是「精度不丢」的真体现）
        precise.add(0.1, 0.1, 0.1);
        assertTrue(
                precise.exactX().toExactString().contains(".2"),
                "连续两次累加后精确值应含 .2，实际: " + precise.exactX().toExactString()
        );
    }

    @Test
    @DisplayName("投影回填保护：投影相同的写入不得降级精确值")
    void projectionWriteBackKeepsExactValue() {
        final PrecisePosition precise = PrecisePosition.of(1e46, 1e46, 1e46);
        precise.add(0.25, 0.25, 0.25);

        final String before = precise.exactX().toExactString();

        // 模拟 vanilla 的「把 double 投影原样回填」（reapplyPosition / snapTo / teleportSetPosition）
        precise.set(precise.x(), precise.y(), precise.z());

        assertEquals(before, precise.exactX().toExactString(), "投影回填把精确值降级了");
    }

    @Test
    @DisplayName("旧位置保留精确值：setOldFromCurrent 应复制精确真值")
    void oldPositionKeepsExactValue() {
        final PrecisePosition precise = PrecisePosition.of(1e46, 1e46, 1e46);
        precise.add(0.5, 0.5, 0.5);
        precise.setOldFromCurrent();

        assertEquals(
                precise.exactX().toExactString(),
                precise.oldExactX().toExactString(),
                "旧位置应与当前精确值一致"
        );
    }

    @Test
    @DisplayName("帧锚点阈值：|坐标| < 1e12 时锚点为 0、局部等于投影（逐位不变）")
    void frameAnchorStaysZeroBelowThreshold() {
        final PrecisePosition precise = PrecisePosition.of(12345.5, -6789.25, 1e11);

        for (int axis = 0; axis < 3; axis++) {
            assertTrue(precise.anchor(axis).isZero(), "阈值内锚点应为 0（axis=" + axis + "）");
        }

        assertEquals(12345.5, precise.local(0), "阈值内局部应等于世界值");
        assertEquals(-6789.25, precise.local(1), "阈值内局部应等于世界值");
    }

    @Test
    @DisplayName("帧锚点超阈值：锚点为精确下取整、局部落在 [0,1)")
    void frameAnchorFloorsBeyondThreshold() {
        final double big = 1.2345678901234567e46;
        final PrecisePosition precise = PrecisePosition.of(big, big, big);

        for (int axis = 0; axis < 3; axis++) {
            assertTrue(!precise.anchor(axis).isZero(), "超阈值锚点不应为 0（axis=" + axis + "）");
            final double local = precise.local(axis);
            assertTrue(local >= 0.0 && local < 1.0, "局部应落在 [0,1)，实际: " + local);
        }
    }

    @Test
    @DisplayName("帧内位移：两个世界量级坐标相减不被 ulp 吞掉（double 直接相减会丢）")
    void frameDeltaSurvivesHugeCoordinates() {
        final double base = 1.2345678901234567e46;

        // 对照：两个 double 在这个量级相减 → 位移被吞成 0
        assertEquals(0.0, (base + 0.5) - base, "double 直接相减在本应丢位移（对照组）");

        // 帧内：同一对象内「旧位置 → 当前位置」的位移（锚点差 + 局部差）
        final PrecisePosition precise = PrecisePosition.of(base, base, base);
        precise.setOldFromCurrent();          // 旧位置 = base
        precise.add(0.25, 0.25, 0.25);        // 当前位置 = base + 0.25

        assertTrue(precise.frameDelta(0) != 0.0, "帧内位移不应为 0");
        assertEquals(0.25, precise.frameDelta(0), 1.0E-9, "帧内位移应等于 0.25");
    }
}
