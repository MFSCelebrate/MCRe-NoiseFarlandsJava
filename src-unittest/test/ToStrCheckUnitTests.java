package test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.MinecraftTools.Math._256bit_Refactoring.IntRefactor256;
import java.math.BigInteger;
import java.util.Random;

/**
 * 由 {@code tmp/ToStrCheck.java} 迁移而来（2026-11-17）。
 * <p>原脚本是 {@code main()} + PASS/FAIL 计数式自检；此处<b>完整保留原始逻辑与期望值</b>，
 * 只把外壳换成 JUnit：脚本输出被捕获，出现失败标记即断言失败。
 */
class ToStrCheckUnitTests {


    @Test
    @DisplayName("IntRefactor256 进制输出正确性")
    void scriptRuns() throws Exception {
        final java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        final java.io.PrintStream originalOut = System.out;
        System.setOut(new java.io.PrintStream(buffer, true, java.nio.charset.StandardCharsets.UTF_8));
        try {

                final Random r = new Random(7);
                int fail = 0;
                for (int i = 0; i < 100_000; i++) {
                    final long[] l = {r.nextLong(), r.nextLong(), r.nextLong(), r.nextLong()};
                    final IntRefactor256 v = IntRefactor256.of(l[0], l[1], l[2], l[3]);
                    final String want = v.toBigInteger().toString();
                    final String got = v.toString();
                    if (!want.equals(got)) {
                        fail++;
                        if (fail <= 3) System.out.println("FAIL got=" + got + " want=" + want);
                    }
                }
                // 边界值
                final long[] edges = {0, 1, -1, Long.MAX_VALUE, Long.MIN_VALUE};
                for (final long e : edges) {
                    final IntRefactor256 v = IntRefactor256.of(e);
                    if (!v.toBigInteger().toString().equals(v.toString())) {
                        fail++;
                        System.out.println("FAIL edge " + e);
                    }
                }
                final IntRefactor256 max = IntRefactor256.MAX_VALUE, min = IntRefactor256.MIN_VALUE;
                if (!max.toBigInteger().toString().equals(max.toString())) fail++;
                if (!min.toBigInteger().toString().equals(min.toString())) fail++;
                System.out.println(fail == 0 ? "ALL PASS（toString 10万+边界 逐字符）" : "TOTAL FAIL = " + fail);
    
        } finally {
            System.setOut(originalOut);
        }

        final String output = buffer.toString(java.nio.charset.StandardCharsets.UTF_8);
        final String meaningful = output.replaceAll("(?i)0\\s*(项|个)?\\s*FAIL", "");
        assertFalse(meaningful.contains("FAIL"), "脚本自检出现 FAIL：\n" + output);
    }

}
