package net.MinecraftTools.terrain.pipeline.mcbedrockgen.noise;

/*
 * 🔧 MCRe MCBedrockGenPipeline 噪声底层副本（2026-10-24 快照）——主线改革前的独立语义锁定版。
 */

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import net.minecraft.client.gui.screens.worldselection.WorldMainSettingScreen;

public class SimplexNoise {
    protected static final int[][] GRADIENT = new int[][]{
            {1, 1, 0},
            {-1, 1, 0},
            {1, -1, 0},
            {-1, -1, 0},
            {1, 0, 1},
            {-1, 0, 1},
            {1, 0, -1},
            {-1, 0, -1},
            {0, 1, 1},
            {0, -1, 1},
            {0, 1, -1},
            {0, -1, -1},
            {1, 1, 0},
            {0, -1, 1},
            {-1, 1, 0},
            {0, -1, -1}
    };
    private static final double SQRT_3 = Math.sqrt(3.0);
    private static final double F2 = 0.5 * (SQRT_3 - 1.0);
    private static final double G2 = (3.0 - SQRT_3) / 6.0;
    private final int[] p = new int[512];
    public final double xo;
    public final double yo;
    public final double zo;

    public SimplexNoise(final RandomSource random) {
        this.xo = random.nextDouble() * 256.0;
        this.yo = random.nextDouble() * 256.0;
        this.zo = random.nextDouble() * 256.0;
        int i = 0;

        while (i < 256) {
            this.p[i] = i++;
        }

        for (int ix = 0; ix < 256; ix++) {
            int offset = random.nextInt(256 - ix);
            int tmp = this.p[ix];
            this.p[ix] = this.p[offset + ix];
            this.p[offset + ix] = tmp;
        }
    }

    private int p(final int x) {
        return this.p[x & 0xFF];
    }

    protected static double dot(final int[] g, final double x, final double y, final double z) {

        // Bedrock 模式：强转 float 计算点积，结果截断
        float fx = (float) x;
        float fy = (float) y;
        float fz = (float) z;
        return (float) (g[0] * fx + g[1] * fy + g[2] * fz);
    }

    private double getCornerNoise3D(final int index, final double x, final double y, final double z, final double base) {

        float fx = (float) x;
        float fy = (float) y;
        float fz = (float) z;
        float fBase = (float) base;

        float t0 = fBase - fx * fx - fy * fy - fz * fz;
        float n0;
        if (t0 < 0.0f) {
            n0 = 0.0f;
        } else {
            float t0Sq = t0 * t0;
            n0 = t0Sq * t0Sq * (float) dot(GRADIENT[index], (double) fx, (double) fy, (double) fz);
        }
        return (float) n0;
    }

    public double getValue(final double xin, final double yin) {

        float fx = (float) xin;
        float fy = (float) yin;

        float fF2 = (float) F2;
        float fG2 = (float) G2;

        float s = (fx + fy) * fF2;
        int i = Mth.floor(fx + s);
        int j = Mth.floor(fy + s);
        float t = (i + j) * fG2;
        float X0 = i - t;
        float Y0 = j - t;
        float x0 = fx - X0;
        float y0 = fy - Y0;
        int i1, j1;
        if (x0 > y0) {
            i1 = 1;
            j1 = 0;
        } else {
            i1 = 0;
            j1 = 1;
        }

        float x1 = x0 - i1 + fG2;
        float y1 = y0 - j1 + fG2;
        float x2 = x0 - 1.0f + 2.0f * fG2;
        float y2 = y0 - 1.0f + 2.0f * fG2;

        int ii = i & 0xFF;
        int jj = j & 0xFF;
        int gi0 = this.p(ii + this.p(jj)) % 12;
        int gi1 = this.p(ii + i1 + this.p(jj + j1)) % 12;
        int gi2 = this.p(ii + 1 + this.p(jj + 1)) % 12;

        float n0 = (float) this.getCornerNoise3D(gi0, (double) x0, (double) y0, 0.0, 0.5);
        float n1 = (float) this.getCornerNoise3D(gi1, (double) x1, (double) y1, 0.0, 0.5);
        float n2 = (float) this.getCornerNoise3D(gi2, (double) x2, (double) y2, 0.0, 0.5);

        float result = 70.0f * (n0 + n1 + n2);
        return (float) result;
    }

    public double getValue(final double xin, final double yin, final double zin) {

        // === Bedrock 模式：全 float 精度 ===
        float fx = (float) xin;
        float fy = (float) yin;
        float fz = (float) zin;

        float F3 = 0.3333333333333333f;
        float G3 = 0.16666666666666666f;

        float s = (fx + fy + fz) * F3;
        int i = Mth.floor(fx + s);
        int j = Mth.floor(fy + s);
        int k = Mth.floor(fz + s);

        float t = (i + j + k) * G3;
        float X0 = i - t;
        float Y0 = j - t;
        float Z0 = k - t;
        float x0 = fx - X0;
        float y0 = fy - Y0;
        float z0 = fz - Z0;

        int i1, j1, k1, i2, j2, k2;
        if (x0 >= y0) {
            if (y0 >= z0) {
                i1 = 1;
                j1 = 0;
                k1 = 0;
                i2 = 1;
                j2 = 1;
                k2 = 0;
            } else if (x0 >= z0) {
                i1 = 1;
                j1 = 0;
                k1 = 0;
                i2 = 1;
                j2 = 0;
                k2 = 1;
            } else {
                i1 = 0;
                j1 = 0;
                k1 = 1;
                i2 = 1;
                j2 = 0;
                k2 = 1;
            }
        } else {
            if (y0 < z0) {
                i1 = 0;
                j1 = 0;
                k1 = 1;
                i2 = 0;
                j2 = 1;
                k2 = 1;
            } else if (x0 < z0) {
                i1 = 0;
                j1 = 1;
                k1 = 0;
                i2 = 0;
                j2 = 1;
                k2 = 1;
            } else {
                i1 = 0;
                j1 = 1;
                k1 = 0;
                i2 = 1;
                j2 = 1;
                k2 = 0;
            }
        }

        float x1 = x0 - i1 + G3;
        float y1 = y0 - j1 + G3;
        float z1 = z0 - k1 + G3;
        float x2 = x0 - i2 + 2.0f * G3;
        float y2 = y0 - j2 + 2.0f * G3;
        float z2 = z0 - k2 + 2.0f * G3;
        float x3 = x0 - 1.0f + 0.5f;
        float y3 = y0 - 1.0f + 0.5f;
        float z3 = z0 - 1.0f + 0.5f;

        int ii = i & 0xFF;
        int jj = j & 0xFF;
        int kk = k & 0xFF;
        int gi0 = this.p(ii + this.p(jj + this.p(kk))) % 12;
        int gi1 = this.p(ii + i1 + this.p(jj + j1 + this.p(kk + k1))) % 12;
        int gi2 = this.p(ii + i2 + this.p(jj + j2 + this.p(kk + k2))) % 12;
        int gi3 = this.p(ii + 1 + this.p(jj + 1 + this.p(kk + 1))) % 12;

        float n0 = (float) this.getCornerNoise3D(gi0, (double) x0, (double) y0, (double) z0, 0.6);
        float n1 = (float) this.getCornerNoise3D(gi1, (double) x1, (double) y1, (double) z1, 0.6);
        float n2 = (float) this.getCornerNoise3D(gi2, (double) x2, (double) y2, (double) z2, 0.6);
        float n3 = (float) this.getCornerNoise3D(gi3, (double) x3, (double) y3, (double) z3, 0.6);

        float result = 32.0f * (n0 + n1 + n2 + n3);
        return (float) result;
    }
}