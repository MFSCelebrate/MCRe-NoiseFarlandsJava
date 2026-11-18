package test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.MinecraftTools.terrain.pipeline.mcbedrock0601.Bedrock0601NoiseSystem;
import net.MinecraftTools.terrain.pipeline.mcbedrock0601.TerrainDensity0601;

/**
 * 由 {@code tmp/Diag0601Domain.java} 迁移而来（2026-11-17）。
 * <p>原脚本是 {@code main()} + PASS/FAIL 计数式自检；此处<b>完整保留原始逻辑与期望值</b>，
 * 只把外壳换成 JUnit：脚本输出被捕获，出现失败标记即断言失败。
 */
class Diag0601DomainUnitTests {

    static int fail = 0;

    @Test
    @DisplayName("0.6.1 管线噪声域自检")
    void scriptRuns() throws Exception {
        final java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        final java.io.PrintStream originalOut = System.out;
        System.setOut(new java.io.PrintStream(buffer, true, java.nio.charset.StandardCharsets.UTF_8));
        try {

                net.minecraft.SharedConstants.tryDetectVersion();
                net.minecraft.server.Bootstrap.bootStrap();
                final TerrainDensity0601 density = new TerrainDensity0601(new Bedrock0601NoiseSystem(12345L));

                System.out.println("== 域外扫描（y<0 / y>=128，含远坐标，skyGrid 默认开）==");
                int[] ys = {-64, -30, -1, 128, 129, 130, 150, 200, 256, 319};
                long[] xs = {0, 100, 100_000, 1_000_000_000L, 100_000_000_000L, 1_000_000_000_000_000L};
                for (int y : ys) {
                    for (long x : xs) {
                        float v = density.heightValue((int) x, y, (int) (x >> 3));
                        if (v != -1.0f) {
                            fail++;
                            System.out.println("FAIL 域外 y=" + y + " x=" + x + " got=" + v);
                        }
                    }
                }
                System.out.println("域外 60 采样点（10 y × 6 x）" + (fail == 0 ? "全 -1 PASS" : "有 FAIL"));

                System.out.println("== 域内正常值（y 0~127，应随坐标变化非恒定）==");
                int distinct = 0;
                float prev = Float.NaN;
                for (int y : new int[] {0, 32, 64, 100, 127}) {
                    for (long x : new long[] {0, 100, 1000, 100_000}) {
                        float v = density.heightValue((int) x, y, 200);
                        if (Float.compare(v, prev) != 0) distinct++;
                        prev = v;
                        if (v == -1.0f && y != 0) {
                            // y=0 近基岩层允许 -1；其余域内点恒 -1 可疑
                            System.out.println("note 域内 y=" + y + " x=" + x + " = -1（低点可能合法）");
                        }
                    }
                }
                System.out.println("域内 20 采样点 distinct=" + distinct + "（>1 = 正常地形值）" + (distinct > 1 ? " PASS" : " FAIL"));
                if (distinct <= 1) fail++;

                System.out.println("== 域内远坐标（skyGrid 开：域内路径未动，值形态记录——Inf 需 limitReturnValue=false 或缩放路径）==");
                for (long x : new long[] {1_000_000_000_000L, 10_000_000_000_000L, 100_000_000_000_000L}) {
                    for (int y : new int[] {16, 64, 100}) {
                        float v = density.heightValue((int) x, y, (int) (x >> 5));
                        System.out.println("  x=" + x + " y=" + y + " -> " + v);
                    }
                }

                System.out.println("== 128 边界两侧连续性 ==");
                float v127 = density.heightValue(500, 127, 500);
                float v128 = density.heightValue(500, 128, 500);
                System.out.println("y=127 -> " + v127 + " | y=128 -> " + v128 + (v128 == -1.0f ? " PASS（边界外即虚空）" : " FAIL"));
                if (v128 != -1.0f) fail++;

                System.out.println(fail == 0 ? "ALL PASS（域外恒虚空 + 域内网格保留）" : "TOTAL FAIL = " + fail);
    
        } finally {
            System.setOut(originalOut);
        }

        final String output = buffer.toString(java.nio.charset.StandardCharsets.UTF_8);
        final String meaningful = output.replaceAll("(?i)0\\s*(项|个)?\\s*FAIL", "");
        assertFalse(meaningful.contains("FAIL"), "脚本自检出现 FAIL：\n" + output);
    }

}
