package net.MinecraftTools.terrain.pipeline.noise;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * 🔧 MCRe 地形管线 C 批：原版噪声源——委托 {@link RandomState#noiseInstances()} 共享缓存。
 *
 * <p>与原版行为<b>逐位等价</b>（同一实例池）；{@link #clear()} 为 no-op（缓存归 RandomState 所有）。
 * 自定义管线做"与原版逐位一致"的回归基准时用它。
 */
public final class VanillaNoiseSource implements NoiseSource {

    private final RandomState randomState;

    private VanillaNoiseSource(final RandomState randomState) {
        this.randomState = randomState;
    }

    public static VanillaNoiseSource of(final RandomState randomState) {
        return new VanillaNoiseSource(randomState);
    }

    @Override
    @Nullable
    public NormalNoise getOrCreate(final ResourceKey<NormalNoise.NoiseParameters> key) {
        return this.randomState.noiseInstances().get(key);
    }

    @Override
    public boolean has(final ResourceKey<NormalNoise.NoiseParameters> key) {
        return this.randomState.noiseInstances().containsKey(key);
    }

    @Override
    public void clear() {
        // 共享缓存归 RandomState 所有，不在此清
    }
}
