package net.MinecraftTools.terrain.pipeline.mcbedrock0601.noise;

import net.MinecraftTools.terrain.pipeline.mcbedrock0601.Mt19937Random;

import java.util.Arrays;

/**
 * 🔧 MCBedrock0601GenPipeline：PE 0.6.1 PerlinNoise octave 堆逐位复刻（synth/PerlinNoise.cpp）。
 *
 * <p>堆方向（照 PE 原样，与 Java NoiseGeneratorOctaves 的遍历方向相反但数学同构为标准 fBm）：
 * 第 i 层频率 = scale×pow（pow 从 1 每层 /2 —— 高频在 i=0，低频在 i 大处）、
 * 振幅 = 1/pow（2^i 递增——低频大振幅主导地形）。8 个实例配置由 RandomLevelSource 决定
 * （lperlin1/2=16 层、perlin1=8、perlin2/3=4、scale=10、depth=16、forest=8）。
 */
public final class PerlinNoise0601 {

    private int levels;
    private final float[] amplitudes; // 🔧 批6：振幅列表（null = 全 1 原版语义；1.18-exp 模拟用 [0×6, 1×10]）
    private final ImprovedNoise0601[] noiseLevels;

    public PerlinNoise0601(final Mt19937Random random, final int levels) {
        this(random, levels, null);
    }

    public PerlinNoise0601(final Mt19937Random random, final int levels, final float[] amplitudes) {
        this.levels = levels;
        this.amplitudes = amplitudes;
        this.noiseLevels = new ImprovedNoise0601[levels];
        for (int i = 0; i < levels; i++) {
            this.noiseLevels[i] = new ImprovedNoise0601(random);
        }
    }

    private float amplitude(final int i) {
        return this.amplitudes != null ? this.amplitudes[i] : 1.0f;
    }

    /** 单点堆（PE 同款：频率 ×pow 递减 + 振幅 /pow 递增 × 振幅列表） */
    public float getValue(final float x, final float y) {
        float value = 0;
        float pow = 1;
        for (int i = 0; i < this.levels; i++) {
            value += this.noiseLevels[i].getValue(x * pow, y * pow) / pow * this.amplitude(i);
            pow /= 2;
        }
        return value;
    }

    public float getValue(final float x, final float y, final float z) {
        float value = 0;
        float pow = 1;
        for (int i = 0; i < this.levels; i++) {
            value += this.noiseLevels[i].getValue(x * pow, y * pow, z * pow) / pow * this.amplitude(i);
            pow /= 2;
        }
        return value;
    }

    /** 区域采样累加（逐层 add×振幅列表；buffer==null 时新建，调用方可复用数组） */
    public float[] getRegion(float[] buffer, final float x, final float y, final float z,
                             final int xSize, final int ySize, final int zSize,
                             final float xScale, final float yScale, final float zScale) {
        final int size = xSize * ySize * zSize;
        if (buffer == null) {
            buffer = new float[size];
        }
        Arrays.fill(buffer, 0, size, 0.0f);
        float pow = 1;
        for (int i = 0; i < this.levels; i++) {
            this.noiseLevels[i].add(buffer, x, y, z, xSize, ySize, zSize, xScale * pow, yScale * pow, zScale * pow, pow, this.amplitude(i));
            pow /= 2;
        }
        return buffer;
    }

    /** PE 2D 包装（y 固定 10.0f；末参 pow 在转调时被丢弃——PE 源码原样怪味，照抄） */
    public float[] getRegion(final float[] buffer, final int x, final int z, final int xSize, final int zSize,
                             final float xScale, final float zScale, final float pow) {
        return this.getRegion(buffer, (float) x, 10.0f, (float) z, xSize, 1, zSize, xScale, 1, zScale);
    }

    /**
     * 2D 区域采样的单点版（走 add ySize==1 分支的 point2D——与 getRegion 采样点逐位等价，
     * ⚠️ 与 getValue 不同）。y 固定 10.0f（照 PE 2D 包装）。
     */
    public float getRegionPoint2D(final float x, final float z, final float xScale, final float zScale) {
        float value = 0;
        float pow = 1;
        for (int i = 0; i < this.levels; i++) {
            value += this.noiseLevels[i].point2D(x, z, xScale * pow, zScale * pow) / pow * this.amplitude(i);
            pow /= 2;
        }
        return value;
    }

    /** 3D 区域采样的单点版（走 add 3D 分支的 point3D——与 getRegion 采样点逐位等价） */
    public float getRegionPoint3D(final float x, final float y, final float z, final float xScale, final float yScale, final float zScale) {
        float value = 0;
        float pow = 1;
        for (int i = 0; i < this.levels; i++) {
            value += this.noiseLevels[i].point3D(x, y, z, xScale * pow, yScale * pow, zScale * pow) / pow * this.amplitude(i);
            pow /= 2;
        }
        return value;
    }
}
