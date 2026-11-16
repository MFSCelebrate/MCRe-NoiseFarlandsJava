package net.MinecraftTools.vectorization;

import java.util.Arrays;
import java.util.Random;

/**
 * 🔧 MCRe 向量化自测（启动时自动跑一次，输出到日志）——**真机数字的唯一来源**。
 *
 * <p>为什么必须内置：proot 模拟环境的 JIT 无法真实向量化（Vector API 会退化成逐 lane 解释执行，
 * 实测反而慢 4 倍）→ **只有真机（Android/桌面）跑出来的数字才有意义**。
 *
 * <p>输出内容：①SIMD 与标量后端的<b>逐位一致性</b>（必须 0 差异，否则不能上线）
 * ②两个后端的吞吐对比（ns/point）③当前后端与 species 宽度。
 */
public final class VectorizationSelfTest {

    private VectorizationSelfTest() {
    }

    /** 跑一次自测并返回可打印的报告（多行）。 */
    public static String run() {
        final StringBuilder sb = new StringBuilder();
        final int n = 4096;
        final int rounds = 400;
        final Random random = new Random(20261115L);
        final float[] fx = new float[n];
        final float[] fy = new float[n];
        final float[] fz = new float[n];
        final float[][] corners = new float[8][n];

        for (int i = 0; i < n; i++) {
            fx[i] = random.nextFloat();
            fy[i] = random.nextFloat();
            fz[i] = random.nextFloat();

            for (final float[] corner : corners) {
                corner[i] = random.nextFloat() * 20.0f - 10.0f;
            }
        }

        // 标量基准（与 VectorMath.ScalarOps 同式）
        final float[] expected = new float[n];

        for (int i = 0; i < n; i++) {
            final float v00 = corners[0][i] + fx[i] * (corners[1][i] - corners[0][i]);
            final float v10 = corners[2][i] + fx[i] * (corners[3][i] - corners[2][i]);
            final float v01 = corners[4][i] + fx[i] * (corners[5][i] - corners[4][i]);
            final float v11 = corners[6][i] + fx[i] * (corners[7][i] - corners[6][i]);
            final float v0 = v00 + fy[i] * (v10 - v00);
            final float v1 = v01 + fy[i] * (v11 - v01);
            expected[i] = v0 + fz[i] * (v1 - v0);
        }

        final float[] actual = new float[n];
        VectorMath.lerp3(fx, fy, fz, corners[0], corners[1], corners[2], corners[3], corners[4], corners[5], corners[6], corners[7], actual, n);

        int mismatch = 0;
        float maxDiff = 0.0f;

        for (int i = 0; i < n; i++) {
            if (actual[i] != expected[i]) {
                mismatch++;
                maxDiff = Math.max(maxDiff, Math.abs(actual[i] - expected[i]));
            }
        }

        // 标量吞吐
        long t0 = System.nanoTime();

        for (int r = 0; r < rounds; r++) {
            for (int i = 0; i < n; i++) {
                final float v00 = corners[0][i] + fx[i] * (corners[1][i] - corners[0][i]);
                final float v10 = corners[2][i] + fx[i] * (corners[3][i] - corners[2][i]);
                final float v01 = corners[4][i] + fx[i] * (corners[5][i] - corners[4][i]);
                final float v11 = corners[6][i] + fx[i] * (corners[7][i] - corners[6][i]);
                final float v0 = v00 + fy[i] * (v10 - v00);
                final float v1 = v01 + fy[i] * (v11 - v01);
                actual[i] = v0 + fz[i] * (v1 - v0);
            }
        }

        final double scalarNs = (double) (System.nanoTime() - t0) / (rounds * (double) n);

        // 向量化吞吐
        t0 = System.nanoTime();

        for (int r = 0; r < rounds; r++) {
            VectorMath.lerp3(fx, fy, fz, corners[0], corners[1], corners[2], corners[3], corners[4], corners[5], corners[6], corners[7], actual, n);
        }

        final double vectorNs = (double) (System.nanoTime() - t0) / (rounds * (double) n);
        sb.append("================ MCRe Vectorization Self-Test ================\n");
        sb.append("  backend       : ").append(VectorMath.backendName()).append('\n');
        sb.append("  bit-identical : ").append(mismatch == 0 ? "YES (0/" + n + " differ)" : "NO (" + mismatch + "/" + n + " differ, maxDiff=" + maxDiff + ")").append('\n');
        sb.append(String.format(LocaleHolder.ROOT, "  lerp3 scalar   : %.3f ns/point%n", scalarNs));
        sb.append(String.format(LocaleHolder.ROOT, "  lerp3 vector   : %.3f ns/point%n", vectorNs));
        sb.append(String.format(LocaleHolder.ROOT, "  speedup       : %.2fx%n", scalarNs / Math.max(vectorNs, 1.0E-9)));
        sb.append("  NOTE: lerp3 reads 11 float arrays -> memory-bound; real gains need FUSED batch kernels.\n");
        sb.append("===============================================================");
        return sb.toString();
    }

    /** 避免额外 import 噪声的小工具（等价 Locale.ROOT）。 */
    private static final class LocaleHolder {

        static final java.util.Locale ROOT = java.util.Locale.ROOT;
    }

    static {
        // 触发电视化后端的类加载（未加 --add-modules 时这里不会崩：安装已在上游完成）
        Arrays.hashCode(new float[0]);
    }
}
