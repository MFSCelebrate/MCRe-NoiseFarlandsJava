package test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import net.MinecraftTools.vectorization.VectorMath;

/**
 * 由 {@code tmp/VectorMathCheck.java} 迁移而来（2026-11-17）。
 * <p>原脚本是 {@code main()} + PASS/FAIL 计数式自检；此处<b>完整保留原始逻辑与期望值</b>，
 * 只把外壳换成 JUnit：脚本输出被捕获，出现失败标记即断言失败。
 */
class VectorMathUnitTests {


    @Test
    @DisplayName("向量化后端逐位一致（bit-identical）")
    void scriptRuns() throws Exception {
        final java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        final java.io.PrintStream originalOut = System.out;
        System.setOut(new java.io.PrintStream(buffer, true, java.nio.charset.StandardCharsets.UTF_8));
        try {

                System.out.println("backend(before install) = " + VectorMath.backendName());
                final boolean installed = VectorMath.installVectorBackend();
                System.out.println("install jdk-vector = " + installed + "  backend = " + VectorMath.backendName());

                final int n = 4096;
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

                // 标量基准（手写，与库内标量后端同式）
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
                System.out.printf("lerp3: %d/%d 点不一致, maxDiff=%s%n", mismatch, n, maxDiff);

                // 吞吐
                final int rounds = 2000;
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
                final long scalarNs = System.nanoTime() - t0;

                t0 = System.nanoTime();
                for (int r = 0; r < rounds; r++) {
                    VectorMath.lerp3(fx, fy, fz, corners[0], corners[1], corners[2], corners[3], corners[4], corners[5], corners[6], corners[7], actual, n);
                }
                final long vectorNs = System.nanoTime() - t0;

                final double perOpScalar = (double) scalarNs / (rounds * (double) n);
                final double perOpVector = (double) vectorNs / (rounds * (double) n);
                System.out.printf("lerp3 throughput: scalar %.3f ns/point, %s %.3f ns/point, speedup %.2fx%n",
                    perOpScalar, VectorMath.backendName(), perOpVector, perOpScalar / perOpVector);
                System.out.println(mismatch == 0 ? "RESULT=PASS (bit-identical)" : "RESULT=CHECK (mismatch " + mismatch + ")");
    
        } finally {
            System.setOut(originalOut);
        }

        final String output = buffer.toString(java.nio.charset.StandardCharsets.UTF_8);
        final String meaningful = output.replaceAll("(?i)0\\s*(项|个)?\\s*FAIL", "");
        assertFalse(meaningful.contains("FAIL"), "脚本自检出现 FAIL：\n" + output);
        assertFalse(meaningful.contains("RESULT=CHECK"), "脚本自检出现 RESULT=CHECK：\n" + output);
        assertTrue(output.contains("RESULT=PASS"), "脚本未输出 RESULT=PASS：\n" + output);
    }

}
