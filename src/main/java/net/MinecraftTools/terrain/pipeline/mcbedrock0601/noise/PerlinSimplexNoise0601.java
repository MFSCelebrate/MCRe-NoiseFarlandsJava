package net.MinecraftTools.terrain.pipeline.mcbedrock0601.noise;

import net.MinecraftTools.terrain.pipeline.mcbedrock0601.Mt19937Random;

import java.util.Arrays;

/**
 * 🔧 MCBedrock0601GenPipeline：PerlinSimplexNoise 堆复刻。
 *
 * <p>⚠️ PE 0.6.1 源码树缺失该文件（大佬确认"那时候源码本身没有"），无法逐位复刻。
 * 按 BiomeSource 的调用方式（getRegion(buffer, x, z, w, h, xScale, zScale, pow)）+
 * Java 1.13 PerlinSimplexNoise 的堆语义复刻：<b>i 层频率 = scale×2^i（递增）、振幅 = pow^i（递减）</b>
 * ——基频主导 + 高频微抖（温度图平滑）。pow 取值由 BiomeSource 传入（0.25/0.3333/0.588）。
 * 随机流消耗与 PE 的 ImprovedNoise 同款：每个 SimplexNoise0601 消耗 xo/yo/zo 三次 nextFloat + 256 次 nextInt。
 */
public final class PerlinSimplexNoise0601 {

    private final SimplexNoise0601[] noiseLevels;

    public PerlinSimplexNoise0601(final Mt19937Random random, final int levels) {
        this.noiseLevels = new SimplexNoise0601[levels];
        for (int i = 0; i < levels; i++) {
            this.noiseLevels[i] = new SimplexNoise0601(random);
        }
    }

    /** 单点堆（与 getRegion 同公式：Σ simplex(x×xScale×2^i, z×zScale×2^i)×pow^i）——密度函数/群系判定用 */
    public float getStacked(final float x, final float z, final float xScale, final float zScale, final float pow) {
        float value = 0.0f;
        float amp = 1.0f;
        float freq = 1.0f;
        for (final SimplexNoise0601 level : this.noiseLevels) {
            value += level.getValue(x * xScale * freq, z * zScale * freq) * amp;
            amp *= pow;
            freq *= 2.0f;
        }
        return value;
    }

    /**
     * 区域采样累加（照 BiomeSource 用法）：getBiomeBlock 用
     * getRegion(temperatures, x, z, w, h, 2/80, 2/80, 0.25f) 等。
     */
    public float[] getRegion(float[] buffer, final float x, final float z, final int xSize, final int zSize,
                             final float xScale, final float zScale, final float pow) {
        final int size = xSize * zSize;
        if (buffer == null) {
            buffer = new float[size];
        }
        Arrays.fill(buffer, 0, size, 0.0f);
        float amp = 1.0f;
        float freq = 1.0f;
        for (final SimplexNoise0601 level : this.noiseLevels) {
            int pp = 0;
            for (int xx = 0; xx < xSize; xx++) {
                for (int zz = 0; zz < zSize; zz++) {
                    buffer[pp++] += level.getValue((x + xx) * xScale * freq, (z + zz) * zScale * freq) * amp;
                }
            }
            amp *= pow;
            freq *= 2.0f;
        }
        return buffer;
    }
}
