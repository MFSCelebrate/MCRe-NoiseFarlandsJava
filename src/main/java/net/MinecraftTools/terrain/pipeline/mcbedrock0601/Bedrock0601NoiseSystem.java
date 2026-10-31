package net.MinecraftTools.terrain.pipeline.mcbedrock0601;

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
    private final Mt19937Random random;

    public Bedrock0601NoiseSystem(final long levelSeed) {
        final Mt19937Random random = new Mt19937Random(levelSeed);
        this.random = random;
        this.lperlinNoise1 = new PerlinNoise0601(random, 16);
        this.lperlinNoise2 = new PerlinNoise0601(random, 16);
        this.perlinNoise1 = new PerlinNoise0601(random, 8);
        this.perlinNoise2 = new PerlinNoise0601(random, 4);
        this.perlinNoise3 = new PerlinNoise0601(random, 4);
        this.scaleNoise = new PerlinNoise0601(random, 10);
        this.depthNoise = new PerlinNoise0601(random, 16);
        this.forestNoise = new PerlinNoise0601(random, 8);
        this.biomeSource = new BiomeSource0601(levelSeed);
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

    /** 主 MT 随机流访问器（PE 的 getChunk per-chunk setSeed——buildSurface/postProcess 的表层随机用） */
    public Mt19937Random random() {
        return this.random;
    }
}
