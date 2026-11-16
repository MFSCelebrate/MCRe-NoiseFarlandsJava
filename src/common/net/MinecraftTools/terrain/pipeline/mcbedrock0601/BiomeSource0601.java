package net.MinecraftTools.terrain.pipeline.mcbedrock0601;

import net.MinecraftTools.terrain.pipeline.mcbedrock0601.noise.PerlinSimplexNoise0601;

/**
 * 🔧 MCBedrock0601GenPipeline：PE 0.6.1 的 BiomeSource 复刻（biome/BiomeSource.cpp）。
 *
 * <p>三张 PerlinSimplexNoise 图（种子 = levelSeed×9871 / ×39811 / ×543321，PE 源码常量）：
 * 温度 4 层（scale 2/80、pow 0.25）、湿度 4 层（scale 2/40、pow 0.3333）、噪声 2 层（scale 1/4、pow 0.588）、
 * zoom=2。单点采样照 getBiomeBlock 的公式逐字：噪声图混合（split 0.01/0.002）+ 温度平方拉伸
 * {@code 1-(1-t)²} + clamp[0,1]。
 *
 * <p><b>连续化说明</b>：原版按 chunk 独立采样 16×16 数组（chunk 边界的温度点不共享——PE 的 chunk 缝来源之一）；
 * 本管线用单点连续采样（采样格 k = 块坐标/4，温度块坐标 = 3k+1），消除 chunk 缝——0.6.1 的 chunk 独立
 * 生成是早期实现缺陷而非特性，如要 1:1 缝隙再说。
 */
public final class BiomeSource0601 {

    public static final float ZOOM = 2.0f;
    public static final float TEMP_SCALE = ZOOM / 80.0f;
    public static final float DOWNFALL_SCALE = ZOOM / 40.0f;
    public static final float NOISE_SCALE = 1.0f / 4.0f;
    /** 0.6.1 采样网格温度块坐标的 chunk 内偏移步长（wScale = 16/5 = 3 整除） */
    public static final int TEMP_COORD_STEP = 3;

    private final PerlinSimplexNoise0601 temperatureMap;
    private final PerlinSimplexNoise0601 downfallMap;
    private final PerlinSimplexNoise0601 noiseMap;

    public BiomeSource0601(final long levelSeed) {
        this.temperatureMap = new PerlinSimplexNoise0601(new Mt19937Random(levelSeed * 9871L), 4);
        this.downfallMap = new PerlinSimplexNoise0601(new Mt19937Random(levelSeed * 39811L), 4);
        this.noiseMap = new PerlinSimplexNoise0601(new Mt19937Random(levelSeed * 543321L), 2);
    }

    /** 温度图访问（验证测试用——单点/区域一致性对照） */
    public PerlinSimplexNoise0601 temperatureMap() {
        return this.temperatureMap;
    }

    /**
     * 0.6.1 采样网格的温度块坐标（chunk 语义）：xc = (块坐标 mod 16)/4、温度点 = chunkX×16 + xc×3+1
     * （原版 xp = xx*wScale + wScale/2，wScale = 16/5 = 3 整除）。interpolated 角点对齐 4 的倍数，
     * xc ∈ [0,3]（下一 chunk 起点自然接管 xc=4 的插值 partner——chunk 缝消解）。
     */
    public static int temperatureCoord(final int blockCoord) {
        final int chunk = Math.floorDiv(blockCoord, 16);
        final int xc = Math.floorMod(blockCoord, 16) / 4;
        return chunk * 16 + xc * TEMP_COORD_STEP + 1;
    }

    /** 单点温度（getBiomeBlock 公式逐字：平方拉伸 + clamp；坐标可为 reposition 后的连续采样坐标） */
    public float temperatureAt(final float x, final float z) {
        float temp = this.temperatureMap.getStacked(x, z, TEMP_SCALE, TEMP_SCALE, 0.25f);
        float noise = this.noiseMap.getStacked(x, z, NOISE_SCALE, NOISE_SCALE, 0.588f) * 1.1f + 0.5f;
        final float split2 = 0.01f;
        final float split1 = 1 - split2;
        float t = (temp * 0.15f + 0.7f) * split1 + noise * split2;
        t = 1 - ((1 - t) * (1 - t));
        if (t < 0) t = 0;
        if (t > 1) t = 1;
        return t;
    }

    /** 单点湿度（getBiomeBlock 公式逐字：split 0.002 + clamp；注意 0.6.1 原版湿度不做平方拉伸） */
    public float downfallAt(final float x, final float z) {
        float downfall = this.downfallMap.getStacked(x, z, DOWNFALL_SCALE, DOWNFALL_SCALE, 0.3333f);
        float noise = this.noiseMap.getStacked(x, z, NOISE_SCALE, NOISE_SCALE, 0.588f) * 1.1f + 0.5f;
        final float split2 = 0.002f;
        final float split1 = 1 - split2;
        float d = (downfall * 0.15f + 0.5f) * split1 + noise * split2;
        if (d < 0) d = 0;
        if (d > 1) d = 1;
        return d;
    }
}
