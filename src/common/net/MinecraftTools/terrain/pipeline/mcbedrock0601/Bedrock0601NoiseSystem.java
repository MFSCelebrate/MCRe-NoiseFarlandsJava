package net.MinecraftTools.terrain.pipeline.mcbedrock0601;

import net.minecraft.client.gui.screens.worldselection.WorldMainSettingScreen;

import net.MinecraftTools.terrain.pipeline.mcbedrock0601.noise.PerlinNoise0601;

/**
 * 🔧 MCBedrock0601GenPipeline：PE 0.6.1 的世界噪声系统（照 levelgen/RandomLevelSource 构造逐字）。
 *
 * <p>8 个 PerlinNoise 堆按源码顺序消耗同一个 MT 随机流：
 * lperlinNoise1(16)/lperlinNoise2(16)/perlinNoise1(8)/perlinNoise2(4)/perlinNoise3(4)/
 * scaleNoise(10)/depthNoise(16)/forestNoise(8)。BiomeSource 的三张 simplex 图用 levelSeed 派生
 * （×9871/×39811/×543321——与主随机流独立，照 PE 源码）。
 *
 * <p>线程安全：构造期消耗随机流，之后全只读——compute 多线程安全；per-world 单例（管线缓存）。
 */
public final class Bedrock0601NoiseSystem {

    private final PerlinNoise0601 lperlinNoise1;
    private final PerlinNoise0601 lperlinNoise2;
    private final PerlinNoise0601 perlinNoise1;
    private final PerlinNoise0601 perlinNoise2;
    private final PerlinNoise0601 perlinNoise3;
    private final PerlinNoise0601 scaleNoise;
    private final PerlinNoise0601 depthNoise;
    private final PerlinNoise0601 forestNoise;
    private final BiomeSource0601 biomeSource;

    public Bedrock0601NoiseSystem(final long levelSeed) {
        final Mt19937Random random = new Mt19937Random(levelSeed);
        // 🔧 批4b：随机实例只在构造期消耗（8 堆初始化），不外借——per-chunk 随机由
        // MCBedrock0601GenPipeline 各自 new（共享实例多线程竞态见该类批4b 注释）
        // 🔧 批6：1.18-exp 振幅列表（precisionMode 分派）——1.18-exp-* 档：lperlin 堆 = [0×6, 1×10]
        //（PE 堆 i 层 = 八度 -i：i=0~5（八度 0~-5）振幅 0、i=6~15（八度 -6~-15）振幅 1——照 1.18-exp-4 的
        // minLimit/maxLimit 振幅表 [1×10, 0×6] 的 PE 堆序映射，i=7 层墙 = 2^38/171.103 = 1,606,505,472 ✓）
        final float[] lperlinAmplitudes = lperlinAmplitudesForMode();
        this.lperlinNoise1 = new PerlinNoise0601(random, 16, lperlinAmplitudes);
        this.lperlinNoise2 = new PerlinNoise0601(random, 16, lperlinAmplitudes);
        this.perlinNoise1 = new PerlinNoise0601(random, 8);
        this.perlinNoise2 = new PerlinNoise0601(random, 4);
        this.perlinNoise3 = new PerlinNoise0601(random, 4);
        this.scaleNoise = new PerlinNoise0601(random, 10);
        this.depthNoise = new PerlinNoise0601(random, 16);
        this.forestNoise = new PerlinNoise0601(random, 8);
        this.biomeSource = new BiomeSource0601(levelSeed);
    }

    /** 🔧 批6：lperlin 振幅表（precisionMode 分派——1.18-exp-* 档启用 [0×6, 1×10]，其余 null=原版全 1） */
    private static float[] lperlinAmplitudesForMode() {
        final WorldMainSettingScreen.FarLandsConfigData cfg = WorldMainSettingScreen.FarLandsConfigData.activeConfig;
        if (cfg != null && ("1.18-exp-32bit".equals(cfg.precisionMode) || "1.18-exp-64bit".equals(cfg.precisionMode))) {
            final float[] table = new float[16];
            for (int i = 6; i < 16; i++) {
                table[i] = 1.0f;
            }
            return table;
        }
        return null;
    }

    public PerlinNoise0601 lperlinNoise1() {
        return this.lperlinNoise1;
    }

    public PerlinNoise0601 lperlinNoise2() {
        return this.lperlinNoise2;
    }

    public PerlinNoise0601 perlinNoise1() {
        return this.perlinNoise1;
    }

    public PerlinNoise0601 perlinNoise2() {
        return this.perlinNoise2;
    }

    public PerlinNoise0601 perlinNoise3() {
        return this.perlinNoise3;
    }

    public PerlinNoise0601 scaleNoise() {
        return this.scaleNoise;
    }

    public PerlinNoise0601 depthNoise() {
        return this.depthNoise;
    }

    public PerlinNoise0601 forestNoise() {
        return this.forestNoise;
    }

    public BiomeSource0601 biomeSource() {
        return this.biomeSource;
    }

}
