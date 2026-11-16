package net.MinecraftTools.terrain.pipeline.mcbedrock0601.noise;

import net.MinecraftTools.terrain.pipeline.mcbedrock0601.Mt19937Random;

/**
 * 🔧 MCBedrock0601GenPipeline：SimplexNoise 内核 float 版。
 *
 * <p>⚠️ PE 0.6.1 源码树缺失 PerlinSimplexNoise/SimplexNoise 文件（0.6.1 时代快照不完整——
 * 大佬确认"那时候源码本身没有"），逐位复刻不可行。本类按 26.2 的
 * {@code net.MinecraftTools.terrain.pipeline.mcbedrockgen.noise.SimplexNoise} 算法结构
 * 转 float 内核 + {@link Mt19937Random} 驱动复刻；偏移用 nextFloat×256 对齐 PE 的
 * ImprovedNoise.init 精神。群系分布宏观走势一致，细节噪声与真机可能有差（已向大佬报备）。
 */
public final class SimplexNoise0601 {

    private static final int[][] GRADIENT = {
        {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0},
        {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
        {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1},
        {1, 1, 0}, {0, -1, 1}, {-1, 1, 0}, {0, -1, -1}
    };
    private static final float F2 = 0.5f * ((float) Math.sqrt(3.0) - 1.0f);
    private static final float G2 = (3.0f - (float) Math.sqrt(3.0)) / 6.0f;

    private final int[] p = new int[512];
    public final float xo;
    public final float yo;
    public final float zo;

    public SimplexNoise0601(final Mt19937Random random) {
        this.xo = random.nextFloat() * 256.0f;
        this.yo = random.nextFloat() * 256.0f;
        this.zo = random.nextFloat() * 256.0f;
        for (int i = 0; i < 256; i++) {
            this.p[i] = i;
        }
        for (int i = 0; i < 256; i++) {
            final int j = random.nextInt(256 - i) + i;
            final int tmp = this.p[i];
            this.p[i] = this.p[j];
            this.p[j] = tmp;
            this.p[i + 256] = this.p[i];
        }
    }

    private int pIndex(final int x) {
        return this.p[x & 0xFF];
    }

    /** 2D simplex（照 26.2 副本 getValue(xin, yin) 的 float 运算结构；偏移字段与原版一致不参与求值） */
    public float getValue(float fx, float fy) {
        final float s = (fx + fy) * F2;
        final int i = floor(fx + s);
        final int j = floor(fy + s);
        final float t = (i + j) * G2;
        final float x0 = fx - (i - t);
        final float y0 = fy - (j - t);
        final int i1;
        final int j1;
        if (x0 > y0) {
            i1 = 1;
            j1 = 0;
        } else {
            i1 = 0;
            j1 = 1;
        }
        final float x1 = x0 - i1 + G2;
        final float y1 = y0 - j1 + G2;
        final float x2 = x0 - 1.0f + 2.0f * G2;
        final float y2 = y0 - 1.0f + 2.0f * G2;

        final int ii = i & 0xFF;
        final int jj = j & 0xFF;
        final int gi0 = this.pIndex(ii + this.pIndex(jj)) % 12;
        final int gi1 = this.pIndex(ii + i1 + this.pIndex(jj + j1)) % 12;
        final int gi2 = this.pIndex(ii + 1 + this.pIndex(jj + 1)) % 12;

        final float n0 = corner(gi0, x0, y0);
        final float n1 = corner(gi1, x1, y1);
        final float n2 = corner(gi2, x2, y2);
        return 70.0f * (n0 + n1 + n2);
    }

    /** 3D 角点公式 z=0、base=0.5 的 float 化（照副本 getCornerNoise3D） */
    private static float corner(final int gi, final float x, final float y) {
        final float t = 0.5f - x * x - y * y;
        if (t < 0.0f) {
            return 0.0f;
        }
        final int[] g = GRADIENT[gi];
        final float dot = g[0] * x + g[1] * y;
        final float t2 = t * t;
        return t2 * t2 * dot;
    }

    private static int floor(final float v) {
        final int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
