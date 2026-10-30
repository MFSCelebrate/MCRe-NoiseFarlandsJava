package net.minecraft.world.level.levelgen;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import org.jspecify.annotations.Nullable;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public final class RandomState {
    private final PositionalRandomFactory random;
    private final HolderGetter<NormalNoise.NoiseParameters> noises;
    private final NoiseRouter router;
    private final Climate.Sampler sampler;
    private final SurfaceSystem surfaceSystem;
    private final PositionalRandomFactory aquiferRandom;
    private final PositionalRandomFactory oreRandom;
    private final Map<ResourceKey<NormalNoise.NoiseParameters>, NormalNoise> noiseIntances;
    private final Map<Identifier, PositionalRandomFactory> positionalRandoms;
    // 🔧 MCRe 地形管线：噪声实例工厂（@Nullable）——mcbedrockgen 管线注入副本噪声用；null = 原版 Noises.instantiate
    // 🔧 MCRe 地形管线：非 null 时 getOrCreateNoise 走此工厂（MCBedrockGen 副本噪声注入点）
    private final java.util.function.Function<ResourceKey<NormalNoise.NoiseParameters>, NormalNoise> noiseFactory;
    // 🔧 MCRe 地形管线 C 批：BlendedNoise 工厂（@Nullable）——old_blended_noise 节点的副本注入点
    private final java.util.function.Function<RandomSource, BlendedNoise> blendedNoiseFactory;

    public static RandomState create(final HolderGetter.Provider holders, final ResourceKey<NoiseGeneratorSettings> noiseSettings, final long seed) {
        return create(holders.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(noiseSettings).value(), holders.lookupOrThrow(Registries.NOISE), seed);
    }

    public static RandomState create(final NoiseGeneratorSettings settings, final HolderGetter<NormalNoise.NoiseParameters> noises, final long seed) {
        return new RandomState(settings, noises, seed, settings.getRandomSource().newInstance(seed).forkPositional(), null, null);
    }

    /** 🔧 MCRe 地形管线：噪声工厂注入变体——rootFactory 用调用方 RandomState 的根工厂（同源），噪声实例由 noiseFactory 提供（副本噪声）。 */
    public static RandomState createWithNoiseFactory(
        final NoiseGeneratorSettings settings,
        final HolderGetter<NormalNoise.NoiseParameters> noises,
        final PositionalRandomFactory rootFactory,
        final java.util.function.Function<ResourceKey<NormalNoise.NoiseParameters>, NormalNoise> noiseFactory,
        final java.util.function.Function<RandomSource, BlendedNoise> blendedNoiseFactory
    ) {
        return new RandomState(settings, noises, 0L, rootFactory, noiseFactory, blendedNoiseFactory);
    }

    private RandomState(
        final NoiseGeneratorSettings settings,
        final HolderGetter<NormalNoise.NoiseParameters> noises,
        final long seed,
        final PositionalRandomFactory rootFactory,
        final java.util.function.Function<ResourceKey<NormalNoise.NoiseParameters>, NormalNoise> noiseFactory,
        final java.util.function.Function<RandomSource, BlendedNoise> blendedNoiseFactory
    ) {
        this.noiseFactory = noiseFactory;
        this.blendedNoiseFactory = blendedNoiseFactory;
        this.random = rootFactory;
        this.noises = noises;
        this.aquiferRandom = this.random.fromHashOf(Identifier.withDefaultNamespace("aquifer")).forkPositional();
        this.oreRandom = this.random.fromHashOf(Identifier.withDefaultNamespace("ore")).forkPositional();
        this.noiseIntances = new ConcurrentHashMap<>();
        this.positionalRandoms = new ConcurrentHashMap<>();
        this.surfaceSystem = new SurfaceSystem(this, settings.defaultBlock(), settings.seaLevel(), this.random);
        final boolean useLegacyInit = settings.useLegacyRandomSource();

        class NoiseWiringHelper implements DensityFunction.Visitor {
            private final Map<DensityFunction, DensityFunction> wrapped = new HashMap<>();

            private RandomSource newLegacyInstance(final long seedOffset) {
                return new LegacyRandomSource(seed + seedOffset);
            }

            @Override
            public DensityFunction.NoiseHolder visitNoise(final DensityFunction.NoiseHolder noise) {
                Holder<NormalNoise.NoiseParameters> noiseData = noise.noiseData();
                if (noiseData.is(Noises.TEMPERATURE_NETHER)) {
                    NormalNoise newNoise = NormalNoise.createLegacyNetherBiome(this.newLegacyInstance(0L), noiseData.value());
                    return new DensityFunction.NoiseHolder(noiseData, newNoise);
                } else if (noiseData.is(Noises.VEGETATION_NETHER)) {
                    NormalNoise newNoise = NormalNoise.createLegacyNetherBiome(this.newLegacyInstance(1L), noiseData.value());
                    return new DensityFunction.NoiseHolder(noiseData, newNoise);
                } else {
                    NormalNoise instantiate = RandomState.this.getOrCreateNoise(noiseData.unwrapKey().orElseThrow());
                    return new DensityFunction.NoiseHolder(noiseData, instantiate);
                }
            }

            private DensityFunction wrapNew(final DensityFunction function) {
                if (function instanceof BlendedNoise noise) {
                    RandomSource terrainRandom = useLegacyInit
                        ? this.newLegacyInstance(0L)
                        : RandomState.this.random.fromHashOf(Identifier.withDefaultNamespace("terrain"));
                    // 🔧 MCRe 管线 C 批：blendedNoiseFactory 非 null 时用副本 BlendedNoise（旧_blended_noise 节点接管）
                    return RandomState.this.blendedNoiseFactory != null
                        ? RandomState.this.blendedNoiseFactory.apply(terrainRandom)
                        : noise.withNewRandom(terrainRandom);
                } else {
                    return function instanceof DensityFunctions.EndIslandDensityFunction ? new DensityFunctions.EndIslandDensityFunction(seed) : function;
                }
            }

            @Override
            public DensityFunction apply(final DensityFunction function) {
                // 🔧 MCRe 管线接管修复（仅 factory≠null 的管线路径，原版路径零变化）：
                // final_density 主链的 reference（sloped_cheese/caves 系，HolderHolder）mapAll 不递归——
                // 里面的 noise 节点从未被 visitNoise 绑定，求值时走 NoiseChunk.wrapNew 的 registry 实例（主线噪声）。
                // 管线路径展开递归：内部 noise 节点走 visitNoise → noiseFactory → 副本实例
                if (RandomState.this.noiseFactory != null && function instanceof DensityFunctions.HolderHolder holder) {
                    // 🔧 修复 CME：不能在 computeIfAbsent 的 mapping function 里递归 mapAll（嵌套 reference/DAG
                    // 共享节点会再次触发同 map 的 computeIfAbsent → HashMap 递归自修改 → CME）。get/put 两步走。
                    DensityFunction expanded = this.wrapped.get(function);
                    if (expanded == null) {
                        expanded = holder.function().value().mapAll(this);
                        this.wrapped.put(function, expanded);
                    }
                    return expanded;
                }
                return this.wrapped.computeIfAbsent(function, this::wrapNew);
            }
        }

        this.router = settings.noiseRouter().mapAll(new NoiseWiringHelper());
        DensityFunction.Visitor noiseFlattener = new DensityFunction.Visitor() {
            private final Map<DensityFunction, DensityFunction> wrapped = new HashMap<>();

            private DensityFunction wrapNew(final DensityFunction function) {
                if (function instanceof DensityFunctions.HolderHolder holder) {
                    return holder.function().value();
                } else {
                    return function instanceof DensityFunctions.Marker marker ? marker.wrapped() : function;
                }
            }

            @Override
            public DensityFunction apply(final DensityFunction input) {
                return this.wrapped.computeIfAbsent(input, this::wrapNew);
            }
        };
        this.sampler = new Climate.Sampler(
            this.router.temperature().mapAll(noiseFlattener),
            this.router.vegetation().mapAll(noiseFlattener),
            this.router.continents().mapAll(noiseFlattener),
            this.router.erosion().mapAll(noiseFlattener),
            this.router.depth().mapAll(noiseFlattener),
            this.router.ridges().mapAll(noiseFlattener),
            settings.spawnTarget()
        );
    }

    public NormalNoise getOrCreateNoise(final ResourceKey<NormalNoise.NoiseParameters> noise) {
        return this.noiseIntances.computeIfAbsent(noise, key -> this.noiseFactory != null
            ? this.noiseFactory.apply(key)
            : Noises.instantiate(this.noises, this.random, noise));
    }

    /** 🔧 MCRe：噪声实例缓存视图（调试屏幕 AllNoiseList 条目遍历用）——地形生成器用到的噪声全部经 getOrCreateNoise 实例化 */
    public Map<ResourceKey<NormalNoise.NoiseParameters>, NormalNoise> noiseInstances() {
        return this.noiseIntances;
    }

    /** 🔧 MCRe 地形管线：派生一个仅替换 router 的 RandomState（其余状态——噪声实例缓存/随机工厂/sampler 等全部共享）。 */
    public RandomState withRouter(final NoiseRouter newRouter) {
        return new RandomState(this.random, this.noises, newRouter, this.sampler, this.surfaceSystem,
            this.aquiferRandom, this.oreRandom, this.noiseIntances, this.positionalRandoms, this.noiseFactory, this.blendedNoiseFactory);
    }

    /**
     * 🔧 MCRe 1.18.1 管线：把代码组装的自定义密度树 wiring 到本 RandomState。
     * <ul>
     *   <li>NoiseHolder 绑定 {@link #getOrCreateNoise} 实例（bootstrap 定义里的噪声未绑定，求值恒 0——必须 wiring）；</li>
     *   <li>BlendedNoise（sloped_cheese 内的 base_3d_noise）用 terrain 随机源重播种（照 NoiseWiringHelper——
     *       否则地形与 seed 无关；legacy_random_source 的 settings 不走此路径，1.18.1 管线仅用于 overworld）；</li>
     *   <li>HolderHolder（bootstrap 引用）无需显式展开——其 mapChildren 会把定义交回 visitor 递归处理，包装保留。</li>
     * </ul>
     * 放本类原因：{@code DensityFunction.NoiseHolder}/{@code DensityFunction.Visitor} 为同包可见，跨包无法实现。
     */
    public DensityFunction wireDensityTree(final DensityFunction tree) {
        class TreeWiringVisitor implements DensityFunction.Visitor {
            @Override
            public DensityFunction.NoiseHolder visitNoise(final DensityFunction.NoiseHolder noise) {
                return new DensityFunction.NoiseHolder(
                    noise.noiseData(), RandomState.this.getOrCreateNoise(noise.noiseData().unwrapKey().orElseThrow()));
            }

            @Override
            public DensityFunction apply(final DensityFunction function) {
                if (function instanceof BlendedNoise noise) {
                    return noise.withNewRandom(RandomState.this.random.fromHashOf(Identifier.withDefaultNamespace("terrain")));
                }
                return function;
            }
        }
        return tree.mapAll(new TreeWiringVisitor());
    }

    private RandomState(
        final PositionalRandomFactory random,
        final HolderGetter<NormalNoise.NoiseParameters> noises,
        final NoiseRouter router,
        final Climate.Sampler sampler,
        final SurfaceSystem surfaceSystem,
        final PositionalRandomFactory aquiferRandom,
        final PositionalRandomFactory oreRandom,
        final Map<ResourceKey<NormalNoise.NoiseParameters>, NormalNoise> noiseIntances,
        final Map<Identifier, PositionalRandomFactory> positionalRandoms,
        final java.util.function.Function<ResourceKey<NormalNoise.NoiseParameters>, NormalNoise> noiseFactory,
        final java.util.function.Function<RandomSource, BlendedNoise> blendedNoiseFactory
    ) {
        this.random = random;
        this.noises = noises;
        this.router = router;
        this.sampler = sampler;
        this.surfaceSystem = surfaceSystem;
        this.aquiferRandom = aquiferRandom;
        this.oreRandom = oreRandom;
        this.noiseIntances = noiseIntances;
        this.positionalRandoms = positionalRandoms;
        this.noiseFactory = noiseFactory;
        this.blendedNoiseFactory = blendedNoiseFactory;
    }

    /** 🔧 MCRe 地形管线 C 批：根随机工厂访问器——PipelineNoiseSource 自建噪声实例（seed 派生同 vanilla 接线）用 */
    public PositionalRandomFactory noiseRandomFactory() {
        return this.random;
    }

    /** 🔧 MCRe 地形管线 C 批：噪声参数 Getter 访问器——PipelineNoiseSource 重建实例需要 NoiseParameters Holder */
    public HolderGetter<NormalNoise.NoiseParameters> noiseParametersGetter() {
        return this.noises;
    }

    public PositionalRandomFactory getOrCreateRandomFactory(final Identifier name) {
        return this.positionalRandoms.computeIfAbsent(name, key -> this.random.fromHashOf(name).forkPositional());
    }

    public NoiseRouter router() {
        return this.router;
    }

    public Climate.Sampler sampler() {
        return this.sampler;
    }

    public SurfaceSystem surfaceSystem() {
        return this.surfaceSystem;
    }

    public PositionalRandomFactory aquiferRandom() {
        return this.aquiferRandom;
    }

    public PositionalRandomFactory oreRandom() {
        return this.oreRandom;
    }
}