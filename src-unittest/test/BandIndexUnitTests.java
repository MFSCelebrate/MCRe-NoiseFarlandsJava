package test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 由 {@code tmp/BandIndexTest.java} 迁移而来（2026-11-17）。
 * <p>原脚本是 {@code main()} + PASS/FAIL 计数式自检；此处<b>完整保留原始逻辑与期望值</b>，
 * 只把外壳换成 JUnit：脚本输出被捕获，出现失败标记即断言失败。
 */
class BandIndexUnitTests {

    static int pass = 0, fail = 0;
    static void check(String name, boolean cond) {
        if (cond) { pass++; System.out.println("PASS " + name); }
        else { fail++; System.out.println("FAIL " + name); }
    }

    // 防护版索引（fixChunkOutOfBounds 路径）
    static int protectedIndex(int y, int offset, int len) {
        return Math.floorMod(y + offset, len); // 修复版：纯 floorMod（与 getBand 一致）
    }

    // 原版索引
    static int vanillaIndex(int y, int offset, int len) {
        return (y + offset + len) % len;
    }

    @Test
    @DisplayName("分带索引：floorMod 防护与原版环形语义一致")
    void scriptRuns() throws Exception {
        final java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        final java.io.PrintStream originalOut = System.out;
        System.setOut(new java.io.PrintStream(buffer, true, java.nio.charset.StandardCharsets.UTF_8));
        try {

                int LEN = 192;
                double INF = Double.POSITIVE_INFINITY;

                // 1. 正常 offset（±4）：两路径一致且合法
                boolean allSame = true;
                for (int y = 0; y <= 384; y += 7) {
                    for (int offset = -4; offset <= 4; offset += 2) {
                        int a = protectedIndex(y, offset, LEN);
                        int b = vanillaIndex(y, offset, LEN);
                        if (a != b || a < 0 || a >= LEN) allSame = false;
                    }
                }
                check("正常 offset 场景两路径全部一致且索引合法", allSame);

                // 2. 野值 offset（-294，实测崩溃场景 y+offset+192=-2 → % 返回 -2 越界）
                int a2 = protectedIndex(100, -294, LEN);
                check("野值 offset=-294 防护后索引合法（floorMod=-2+192=190）", a2 >= 0 && a2 < LEN);
                boolean vanillaBad = false;
                try {
                    int b2 = vanillaIndex(100, -294, LEN);
                    if (b2 < 0 || b2 >= LEN) vanillaBad = true;
                } catch (Exception e) { vanillaBad = true; }
                check("野值 offset=-294 原版 % 越界（崩）", vanillaBad);

                // 3. Inf 场景：round(+Inf)=Long.MAX → (int) 截断
                int offsetInf = (int) Math.round(INF * 4.0);
                int a3 = protectedIndex(50, offsetInf, LEN);
                check("Inf → (int) 截断 → 防护后索引合法", a3 >= 0 && a3 < LEN);

                // 4. NaN 场景：round(NaN)=0
                int offsetNaN = (int) Math.round(Double.NaN * 4.0);
                check("NaN → round=0 → offset=0", offsetNaN == 0);

                // 5. clamp 边界
                check("clamp(+Inf,-len,len)=+len", Math.max(-LEN, Math.min(Double.POSITIVE_INFINITY, LEN)) == LEN);
                check("clamp(-1e18,-len,len)=-len", Math.max(-LEN, Math.min(-1.0e18, LEN)) == -LEN);

                // 6. 大正 offset（+294）：原版本就是合法环形取模，防护（纯 floorMod）与原版一致
                int a6 = protectedIndex(100, 294, LEN);
                int b6 = vanillaIndex(100, 294, LEN);
                check("正 offset=+294 防护与原版一致（环形语义）", a6 == b6 && a6 >= 0 && a6 < LEN);
        
    
        } finally {
            System.setOut(originalOut);
        }

        final String output = buffer.toString(java.nio.charset.StandardCharsets.UTF_8);
        final String meaningful = output.replaceAll("(?i)0\\s*(项|个)?\\s*FAIL", "");
        assertFalse(meaningful.contains("FAIL"), "脚本自检出现 FAIL：\n" + output);
    }

}
