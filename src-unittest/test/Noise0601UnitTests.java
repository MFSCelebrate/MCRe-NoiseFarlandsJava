package test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.MinecraftTools.terrain.pipeline.mcbedrock0601.Mt19937Random;
import net.MinecraftTools.terrain.pipeline.mcbedrock0601.noise.ImprovedNoise0601;
import net.MinecraftTools.terrain.pipeline.mcbedrock0601.noise.PerlinNoise0601;
import net.MinecraftTools.terrain.pipeline.mcbedrock0601.noise.PerlinSimplexNoise0601;

/**
 * 由 {@code tmp/Noise0601Test.java} 迁移而来（2026-11-17）。
 * <p>原脚本是 {@code main()} + PASS/FAIL 计数式自检；此处<b>完整保留原始逻辑与期望值</b>，
 * 只把外壳换成 JUnit：脚本输出被捕获，出现失败标记即断言失败。
 */
class Noise0601UnitTests {


    static int pass = 0;
    static int fail = 0;

    static void check(String name, boolean ok) {
        if (ok) { pass++; System.out.println("[PASS] " + name); }
        else { fail++; System.out.println("[FAIL] " + name); }
    }

    @Test
    @DisplayName("0.6.1 噪声（MT19937 + Perlin）逐位复刻")
    void scriptRuns() throws Exception {
        final java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        final java.io.PrintStream originalOut = System.out;
        System.setOut(new java.io.PrintStream(buffer, true, java.nio.charset.StandardCharsets.UTF_8));
        try {

                // ===== 1. MT19937 官方测试向量（mt19937ar：init_genrand(5489) 前 5 个输出）=====
                Mt19937Random mt = new Mt19937Random(5489L);
                long[] expected = {3499211612L, 581869302L, 3890346734L, 3586334585L, 545404204L};
                boolean vectorOk = true;
                StringBuilder got = new StringBuilder();
                for (int i = 0; i < 5; i++) {
                    long v = Integer.toUnsignedLong(mt.genrandInt32());
                    got.append(v).append(i < 4 ? "," : "");
                    if (v != expected[i]) vectorOk = false;
                }
                check("MT19937 官方向量 seed=5489（" + got + "）", vectorOk);

                // ===== 2. MT 确定性 + seed 低 32 位截断 =====
                Mt19937Random a = new Mt19937Random(12345L);
                Mt19937Random b = new Mt19937Random(12345L);
                boolean det = true;
                for (int i = 0; i < 1000; i++) {
                    if (a.genrandInt32() != b.genrandInt32()) { det = false; break; }
                }
                check("MT 同 seed 序列逐位一致（1000 个）", det);

                // C++ long 32 位：seed=0x1FFFFFFFF 与 seed=0xFFFFFFFF 应同序列（低 32 位截断）
                Mt19937Random hi = new Mt19937Random(0x1FFFFFFFFL);
                Mt19937Random lo = new Mt19937Random(0xFFFFFFFFL);
                boolean trunc = hi.genrandInt32() == lo.genrandInt32();
                check("seed 低 32 位截断（0x1FFFFFFFF ≡ 0xFFFFFFFF）", trunc);

                // ===== 3. nextXxx 值域 =====
                Mt19937Random r = new Mt19937Random(987654321L);
                boolean ranges = true;
                for (int i = 0; i < 100000; i++) {
                    int ni = r.nextInt();
                    if (ni < 0 || ni > Integer.MAX_VALUE) { ranges = false; break; }
                    int n5 = r.nextInt(5);
                    if (n5 < 0 || n5 >= 5) { ranges = false; break; }
                    float f = r.nextFloat();
                    if (f < 0.0f || f >= 1.0f) { ranges = false; break; }
                    double d = r.nextDouble();
                    if (d < 0.0 || d >= 1.0) { ranges = false; break; }
                }
                check("nextInt/nextInt(n)/nextFloat/nextDouble 值域（10 万次）", ranges);

                // ===== 4. ImprovedNoise0601 确定性 + 值域 =====
                ImprovedNoise0601 n1 = new ImprovedNoise0601(new Mt19937Random(42L));
                ImprovedNoise0601 n2 = new ImprovedNoise0601(new Mt19937Random(42L));
                boolean ndet = true;
                boolean nrange = true;
                float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
                for (int i = 0; i < 5000; i++) {
                    float x = i * 0.137f, y = i * 0.311f, z = i * 0.727f;
                    float v1 = n1.noise(x, y, z);
                    float v2 = n2.noise(x, y, z);
                    if (Float.floatToRawIntBits(v1) != Float.floatToRawIntBits(v2)) { ndet = false; break; }
                    if (Float.isNaN(v1) || Float.isInfinite(v1)) { nrange = false; break; }
                    min = Math.min(min, v1); max = Math.max(max, v1);
                }
                check("ImprovedNoise0601 同 seed 逐位一致（5000 采样）", ndet);
                check("ImprovedNoise0601 有限值域（[" + min + ", " + max + "]）", nrange);

                // ===== 5. PerlinNoise0601.getRegion：确定性 + getHeights 调用形状 =====
                PerlinNoise0601 p1 = new PerlinNoise0601(new Mt19937Random(7L), 16);
                PerlinNoise0601 p2 = new PerlinNoise0601(new Mt19937Random(7L), 16);
                float[] buf1 = new float[5 * 17 * 5];
                float[] buf2 = new float[5 * 17 * 5];
                p1.getRegion(buf1, 100, 0, 200, 5, 17, 5, 684.412f, 684.412f, 684.412f);
                p2.getRegion(buf2, 100, 0, 200, 5, 17, 5, 684.412f, 684.412f, 684.412f);
                boolean pdet = true;
                for (int i = 0; i < buf1.length; i++) {
                    if (Float.floatToRawIntBits(buf1[i]) != Float.floatToRawIntBits(buf2[i])) { pdet = false; break; }
                }
                check("PerlinNoise0601.getRegion 16层 5x17x5 确定性", pdet);

                // 复用 buffer（PE 语义：非 null 不重建）——第二次调用结果仍一致
                p2.getRegion(buf2, 100, 0, 200, 5, 17, 5, 684.412f, 684.412f, 684.412f);
                boolean reuse = true;
                for (int i = 0; i < buf1.length; i++) {
                    if (Float.floatToRawIntBits(buf1[i]) != Float.floatToRawIntBits(buf2[i])) { reuse = false; break; }
                }
                check("getRegion 复用 buffer 归零语义", reuse);

                // ===== 6. RandomLevelSource 的 8 实例配置（随机流消耗顺序：复刻完整前缀再对照）=====
                Mt19937Random rs = new Mt19937Random(20260601L);
                new PerlinNoise0601(rs, 16); // lperlinNoise1
                new PerlinNoise0601(rs, 16); // lperlinNoise2
                new PerlinNoise0601(rs, 8);  // perlinNoise1
                new PerlinNoise0601(rs, 4);  // perlinNoise2
                new PerlinNoise0601(rs, 4);  // perlinNoise3
                new PerlinNoise0601(rs, 10); // scaleNoise
                new PerlinNoise0601(rs, 16); // depthNoise
                PerlinNoise0601 forest = new PerlinNoise0601(rs, 8); // forestNoise（构造 8 层，照源码）
                float fv = forest.getValue(12.5f, 7.25f);

                Mt19937Random rs2 = new Mt19937Random(20260601L);
                new PerlinNoise0601(rs2, 16);
                new PerlinNoise0601(rs2, 16);
                new PerlinNoise0601(rs2, 8);
                new PerlinNoise0601(rs2, 4);
                new PerlinNoise0601(rs2, 4);
                new PerlinNoise0601(rs2, 10);
                new PerlinNoise0601(rs2, 16);
                PerlinNoise0601 forest2 = new PerlinNoise0601(rs2, 8);
                float fv2 = forest2.getValue(12.5f, 7.25f);
                boolean forestOk = Float.floatToRawIntBits(fv) == Float.floatToRawIntBits(fv2);
                check("8 实例顺序消耗随机流后 forestNoise 可复现", forestOk);

                // ===== 7. PerlinSimplexNoise0601 确定性 + 值域 =====
                PerlinSimplexNoise0601 s1 = new PerlinSimplexNoise0601(new Mt19937Random(999L), 4);
                PerlinSimplexNoise0601 s2 = new PerlinSimplexNoise0601(new Mt19937Random(999L), 4);
                float[] sb1 = new float[16 * 16];
                float[] sb2 = new float[16 * 16];
                s1.getRegion(sb1, 1000, -2000, 16, 16, 2.0f / 80.0f, 2.0f / 80.0f, 0.25f);
                s2.getRegion(sb2, 1000, -2000, 16, 16, 2.0f / 80.0f, 2.0f / 80.0f, 0.25f);
                boolean sdet = true;
                float smin = Float.MAX_VALUE, smax = -Float.MAX_VALUE;
                for (int i = 0; i < sb1.length; i++) {
                    if (Float.floatToRawIntBits(sb1[i]) != Float.floatToRawIntBits(sb2[i])) { sdet = false; break; }
                    smin = Math.min(smin, sb1[i]); smax = Math.max(smax, sb1[i]);
                }
                check("PerlinSimplexNoise0601 温度图 16x16 确定性", sdet);
                check("温度图有限值域（[" + smin + ", " + smax + "]，理论界 ±1.328）",
                      smin >= -1.4f && smax <= 1.4f);

                System.out.println("======== " + pass + " PASS / " + fail + " FAIL ========");
        
    
        } finally {
            System.setOut(originalOut);
        }

        final String output = buffer.toString(java.nio.charset.StandardCharsets.UTF_8);
        final String meaningful = output.replaceAll("(?i)0\\s*(项|个)?\\s*FAIL", "");
        assertFalse(meaningful.contains("[FAIL]"), "脚本自检出现 [FAIL]：\n" + output);
    }

}
