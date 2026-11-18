package test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.MinecraftTools.Math._256bit_Refactoring.FloatRefactor256;
import java.math.BigDecimal;

/**
 * 由 {@code tmp/ToExactCheck.java} 迁移而来（2026-11-17）。
 * <p>原脚本是 {@code main()} + PASS/FAIL 计数式自检；此处<b>完整保留原始逻辑与期望值</b>，
 * 只把外壳换成 JUnit：脚本输出被捕获，出现失败标记即断言失败。
 */
class ToExactUnitTests {


    @Test
    @DisplayName("toExactString：double → 256 → 精确十进制")
    void scriptRuns() throws Exception {
        final java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        final java.io.PrintStream originalOut = System.out;
        System.setOut(new java.io.PrintStream(buffer, true, java.nio.charset.StandardCharsets.UTF_8));
        try {

                int fail = 0;
                final java.util.Random r = new java.util.Random(42);
                final double[] cases = {0.1, 0.2, 0.1 + 0.2, 1.0 / 3.0, Math.PI, 1e-300, 1e300, 2.5, -7.125,
                        Double.MIN_VALUE, Double.MAX_VALUE, 123456.789, 1e-10, 5e-324};
                for (final double v : cases) {
                    final String want = new BigDecimal(v).toPlainString();
                    final String got = FloatRefactor256.of(v).toExactString();
                    if (!want.equals(got)) {
                        fail++;
                        System.out.println("FAIL " + v + " got=" + got + " want=" + want);
                    }
                }
                for (int i = 0; i < 100_000; i++) {
                    final double v = Double.longBitsToDouble(r.nextLong());
                    if (Double.isNaN(v) || Double.isInfinite(v)) continue;
                    if (!new BigDecimal(v).toPlainString().equals(FloatRefactor256.of(v).toExactString())) {
                        fail++;
                        if (fail < 5) System.out.println("FAIL random bits=" + Long.toHexString(Double.doubleToRawLongBits(v)));
                    }
                }
                // 特殊值
                if (!FloatRefactor256.NaN.toExactString().equals("NaN")) fail++;
                if (!FloatRefactor256.POSITIVE_INFINITY.toExactString().equals("Infinity")) fail++;
                if (!FloatRefactor256.MIN_VALUE.toExactString().startsWith("0")) fail++;
                System.out.println(fail == 0 ? "ALL PASS（toExactString 10万+ 精确十进制）" : "TOTAL FAIL = " + fail);
    
        } finally {
            System.setOut(originalOut);
        }

        final String output = buffer.toString(java.nio.charset.StandardCharsets.UTF_8);
        final String meaningful = output.replaceAll("(?i)0\\s*(项|个)?\\s*FAIL", "");
        assertFalse(meaningful.contains("FAIL"), "脚本自检出现 FAIL：\n" + output);
    }

}
