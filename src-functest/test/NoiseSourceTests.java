package test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * 由 {@code tmp/NoiseSourceTest.java} 迁移而来（2026-11-17）。
 * <p>原脚本是 {@code main()} + PASS/FAIL 计数式自检；此处<b>完整保留原始逻辑与期望值</b>，
 * 只把外壳换成 JUnit：脚本输出被捕获，出现失败标记即断言失败。
 */
class NoiseSourceTests {


    private static final NormalNoise.NoiseParameters TERRAIN_PARAMS =
        new NormalNoise.NoiseParameters(-9, List.of(1.0, 1.0, 1.0, 0.5)); // overworld terrain 同款量级

    private static NormalNoise rebuild(final long seed, final String keyName) {
        // 照 Noises.instantiate：根 factory.fromHashOf(key) → NormalNoise.create
        final PositionalRandomFactory factory = new LegacyRandomSource(seed).forkPositional();
        return NormalNoise.create(factory.fromHashOf(Identifier.withDefaultNamespace(keyName)), TERRAIN_PARAMS);
    }

    @Test
    @DisplayName("噪声源可重建且确定性（seed/参数敏感）")
    void scriptRuns() throws Exception {
        final java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        final java.io.PrintStream originalOut = System.out;
        System.setOut(new java.io.PrintStream(buffer, true, java.nio.charset.StandardCharsets.UTF_8));
        try {

                int failures = 0;

                // ── 1. 重建确定性：同 seed 同 key 两次实例 → 逐位一致 ──
                {
                    final NormalNoise a = rebuild(12345L, "terrain");
                    final NormalNoise b = rebuild(12345L, "terrain");
                    boolean same = true;
                    for (int i = 0; i < 4096; i++) {
                        final double x = i * 17.31, y = i * 8.77, z = i * 3.13;
                        if (Double.doubleToRawLongBits(a.getValue(x, y, z)) != Double.doubleToRawLongBits(b.getValue(x, y, z))) {
                            same = false;
                            break;
                        }
                    }
                    System.out.println("[1] 重建确定性（同 seed 同 key 逐位一致 4096 采样）: " + (same ? "PASS" : "FAIL"));
                    if (!same) failures++;
                }

                // ── 2. key 区分度：不同 key 同 seed → 不同随机流（实例不同源）──
                {
                    final NormalNoise terrain = rebuild(12345L, "terrain");
                    final NormalNoise shifted = rebuild(12345L, "shift");
                    final double v1 = terrain.getValue(100.5, 64.25, -200.75);
                    final double v2 = shifted.getValue(100.5, 64.25, -200.75);
                    final boolean distinct = Double.doubleToRawLongBits(v1) != Double.doubleToRawLongBits(v2);
                    System.out.println("[2] key 区分度（terrain vs shift 不同值）: " + (distinct ? "PASS" : "FAIL")
                        + " (v1=" + v1 + ", v2=" + v2 + ")");
                    if (!distinct) failures++;
                }

                // ── 3. seed 区分度：同 key 不同 seed → 不同值（世界隔离）──
                {
                    final NormalNoise s1 = rebuild(1L, "terrain");
                    final NormalNoise s2 = rebuild(2L, "terrain");
                    final double v1 = s1.getValue(55.5, 33.25, -11.75);
                    final double v2 = s2.getValue(55.5, 33.25, -11.75);
                    final boolean distinct = Double.doubleToRawLongBits(v1) != Double.doubleToRawLongBits(v2);
                    System.out.println("[3] seed 区分度（seed=1 vs seed=2 不同值）: " + (distinct ? "PASS" : "FAIL")
                        + " (v1=" + v1 + ", v2=" + v2 + ")");
                    if (!distinct) failures++;
                }

                // ── 4. 参数敏感性：不同 NoiseParameters → 不同实例行为（自定义底层的基础）──
                {
                    final PositionalRandomFactory factory = new LegacyRandomSource(12345L).forkPositional();
                    final NormalNoise p1 = NormalNoise.create(
                        factory.fromHashOf(Identifier.withDefaultNamespace("terrain")),
                        new NormalNoise.NoiseParameters(-9, List.of(1.0, 1.0, 1.0, 0.5)));
                    final NormalNoise p2 = NormalNoise.create(
                        factory.fromHashOf(Identifier.withDefaultNamespace("terrain")),
                        new NormalNoise.NoiseParameters(-9, List.of(2.0, 2.0, 2.0, 1.0))); // 振幅 ×2
                    final double v1 = p1.getValue(10.5, 20.25, 30.75);
                    final double v2 = p2.getValue(10.5, 20.25, 30.75);
                    final boolean scaled = Math.abs(v2 - v1) > 1e-9 && Double.doubleToRawLongBits(v1) != Double.doubleToRawLongBits(v2);
                    System.out.println("[4] 参数敏感性（振幅×2 值不同）: " + (scaled ? "PASS" : "FAIL")
                        + " (v1=" + v1 + ", v2=" + v2 + ")");
                    if (!scaled) failures++;
                }

                System.out.println(failures == 0 ? "== ALL PASS ==" : "== " + failures + " FAILURES ==");
                if (failures > 0) {
            
                }
    
        } finally {
            System.setOut(originalOut);
        }

        final String output = buffer.toString(java.nio.charset.StandardCharsets.UTF_8);
        final String meaningful = output.replaceAll("(?i)0\\s*(项|个)?\\s*FAIL", "");
        assertFalse(meaningful.contains("FAIL"), "脚本自检出现 FAIL：\n" + output);
    }

}
