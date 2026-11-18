package test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.MinecraftTools.Math.DynamicAccuracy.BigDecimal;
import net.MinecraftTools.Math.DynamicAccuracy.BigInteger;
import net.MinecraftTools.Math.DynamicAccuracy.RoundingMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 🔧 MCRe 单元测试样板：<b>不依赖 Minecraft 运行时</b>的纯逻辑回归（自研 DynamicAccuracy 大数库）。
 *
 * <p>归属：{@code src-unittest/test}（单元测试根）——类名以 {@code UnitTests} 结尾。
 * <p>与 {@code src-functest/test}（功能测试根，类名以 {@code Tests} 结尾）同包 {@code test}，
 * 靠类名后缀区分，不会撞类。
 * <p>本类只依赖 JDK + 我们自己的 {@code net.MinecraftTools.Math.DynamicAccuracy} → 秒级、无需资源，
 * 因此进<b>标准 test 源集</b>，每次 {@code gradle build} 自动跑。
 */
class DynamicAccuracyUnitTests {

    @Test
    @DisplayName("BigInteger 基本运算：加 / 乘 / 整除 / 符号 / 进制输出")
    void bigIntegerArithmetic() {
        assertEquals("3", BigInteger.valueOf(1).add(BigInteger.valueOf(2)).toString());
        assertEquals("42", BigInteger.valueOf(6).multiply(BigInteger.valueOf(7)).toString());
        assertEquals("3", BigInteger.valueOf(10).divide(BigInteger.valueOf(3)).toString());
        assertEquals(-1, BigInteger.valueOf(-5).signum());
        assertEquals("ff", BigInteger.valueOf(255).toString(16));
        assertTrue(BigInteger.ZERO.signum() == 0);
    }

    @Test
    @DisplayName("BigDecimal 精确十进制：0.1 + 0.2 = 0.3（double 会得 0.30000000000000004）")
    void bigDecimalIsExact() {
        assertEquals("0.3", BigDecimal.valueOf(0.1).add(BigDecimal.valueOf(0.2)).toString());
    }

    @Test
    @DisplayName("BigDecimal 大数不丢精度：1e12 × 1e12 = 1e24")
    void bigDecimalKeepsPrecision() {
        assertEquals(
                "1000000000000000000000000",
                BigDecimal.valueOf(1000000000000L).multiply(BigDecimal.valueOf(1000000000000L)).toString()
        );
    }

    @Test
    @DisplayName("BigDecimal 舍入与除法：2.5 HALF_UP → 3；1/3 保留 10 位")
    void bigDecimalRoundingAndDivide() {
        assertEquals("3", BigDecimal.valueOf(2.5).setScale(0, RoundingMode.HALF_UP).toString());
        assertEquals(
                "0.3333333333",
                BigDecimal.ONE.divide(BigDecimal.valueOf(3), 10, RoundingMode.HALF_UP).toString()
        );
    }
}
