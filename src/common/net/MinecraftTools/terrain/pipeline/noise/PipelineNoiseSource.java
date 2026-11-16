package net.MinecraftTools.terrain.pipeline.noise;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * 🔧 MCRe 地形管线 C 批：管线自有噪声源——按 key 用 seed 派生随机源<b>重建独立实例</b>
 * （参照 Inf's Farlands {@code OctNoiseSource} 的"重建归本类所有"思路）。
 *
 * <p>重建接线与 vanilla 完全一致（{@code Noises.instantiate}：PositionalRandomFactory.fromHashOf
 * + NoiseParameters Holder）→ 同 seed 同参数下与原版实例<b>逐位一致</b>，但实例归本源所有：
 * <ul>
 *   <li>与 {@code RandomState.noiseIntances} 共享池隔离——管线可整体 {@link #clear()}；</li>
 *   <li>多线程安全：ConcurrentHashMap.computeIfAbsent 保证同 key 单实例（跨 genPool 线程）；</li>
 *   <li><b>偏移缩放走 WorldReposition</b>（采样坐标过 {@code WorldReposition.reposition}）——
 *       本类不引入任何 OctScale 式缩放机制（那是 infs 自己的偏移缩放手段，语义不同）。</li>
 * </ul>
 */
public final class PipelineNoiseSource implements NoiseSource {

    private final RandomState randomState;
    private final Map<ResourceKey<NormalNoise.NoiseParameters>, NormalNoise> rebuilt = new ConcurrentHashMap<>();

    private PipelineNoiseSource(final RandomState randomState) {
        this.randomState = randomState;
    }

    public static PipelineNoiseSource of(final RandomState randomState) {
        return new PipelineNoiseSource(randomState);
    }

    @Override
    @Nullable
    public NormalNoise getOrCreate(final ResourceKey<NormalNoise.NoiseParameters> key) {
        return this.rebuilt.computeIfAbsent(key, k -> {
            // 与 vanilla 接线逐字一致：Noises.instantiate(noises, randomFactory, key)
            // 同 seed 同参数 → 与共享缓存里的实例逐位一致，但实例身份独立
            return Noises.instantiate(this.randomState.noiseParametersGetter(), this.randomState.noiseRandomFactory(), k);
        });
    }

    @Override
    public boolean has(final ResourceKey<NormalNoise.NoiseParameters> key) {
        return this.rebuilt.containsKey(key) || this.randomState.noiseParametersGetter().get(key).isPresent();
    }

    @Override
    public void clear() {
        this.rebuilt.clear();
    }
}
