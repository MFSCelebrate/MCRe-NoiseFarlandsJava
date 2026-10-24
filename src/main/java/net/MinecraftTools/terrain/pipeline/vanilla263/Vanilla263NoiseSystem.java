package net.MinecraftTools.terrain.pipeline.vanilla263;

import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.WorldgenRandom;

/**
 * 26.3 主世界密度链的噪声系统（移植自 Inf's Farlands Vanilla263，剥离其系统框架为纯 Java）。
 *
 * <p>复刻范围是 26.3 的整条 {@code final_density}：噪声叶、密度节点、样条、拓扑全部换成本包内按 26.3
 * 逐行移植的实现，只把最后的 beardifier 留给 26.2 的 {@code NoiseChunk} 自己加。
 *
 * <p>逐位一致的可达范围止于本系统 {@code createFinalDensity} 的返回值。26.2 的 {@code NoiseChunk} 之后
 * 还要做插值、加 beardifier、与 aquifer 交互，那些都是 26.2 的 double 算术（与 infs 对 26.1.2 的处理同语义）。
 *
 * <p>不支持热配置：参数只在实例化时读一次。树带状态缓存（NoiseInterpolator 场景），<b>禁止跨 chunk 共享实例</b>
 * ——每次使用应新建本类。
 */
public final class Vanilla263NoiseSystem {

    private final boolean enableFarlands;
    private final boolean topFadeEnabled;
    private final boolean enableJagged;
    private final boolean enableSkyGrid;
    private final long seed;

    /** 按 vanilla 路由身份缓存的树。同 router 身份复用，不缓存就会每段重造一棵。 */
    private volatile NoiseRouter cachedVanilla;
    private volatile FloatNode263 cachedTree;
    /** 🔧 MCRe：外部噪声随机源覆盖（Test263 管线传 randomState 的根工厂——同源保证逐位一致）。 */
    private volatile PositionalRandomFactory noiseFactoryOverride;

    public Vanilla263NoiseSystem(
        final long seed,
        final boolean enableFarlands,
        final boolean topFadeEnabled,
        final boolean enableJagged,
        final boolean enableSkyGrid
    ) {
        this.seed = seed;
        this.enableFarlands = enableFarlands;
        this.topFadeEnabled = topFadeEnabled;
        this.enableJagged = enableJagged;
        this.enableSkyGrid = enableSkyGrid;
    }

    /** 返回该系统的 finalDensity（26.3 逐行移植的树，根为 FloatNode263）。 */
    public DensityFunction createFinalDensity(final NoiseRouter router) {
        if (this.cachedVanilla == router) {
            final FloatNode263 cached = this.cachedTree;
            if (cached != null) {
                return cached;
            }
        }
        // 26.3 语义：XOROSHIRO.newInstance(seed).forkPositional() 派生噪声随机源；
        // 外部覆盖优先（Test263 管线传 randomState 根工厂——overworld 默认同为 XOROSHIRO，同源同流）
        final PositionalRandomFactory noises = this.noiseFactoryOverride != null
            ? this.noiseFactoryOverride
            : WorldgenRandom.Algorithm.XOROSHIRO.newInstance(this.seed).forkPositional();
        final FloatNode263 tree = OverworldDensity263.build(noises, this.enableFarlands, this.topFadeEnabled,
            this.enableJagged, this.enableSkyGrid);
        this.cachedTree = tree;
        this.cachedVanilla = router;
        return tree;
    }

    /**
     * 返回该系统的整条噪声路由：只把 finalDensity 换成本系统的，其余字段照抄 vanilla
     * （语义同 Inf's Farlands NoiseSystem 的 default createRouter——preliminarySurfaceLevel、
     * 矿脉门等字段保持 vanilla 26.2 行为）。
     */
    public NoiseRouter createRouter(final NoiseRouter vanilla) {
        return new NoiseRouter(
            vanilla.barrierNoise(),
            vanilla.fluidLevelFloodednessNoise(),
            vanilla.fluidLevelSpreadNoise(),
            vanilla.lavaNoise(),
            vanilla.temperature(),
            vanilla.vegetation(),
            vanilla.continents(),
            vanilla.erosion(),
            vanilla.depth(),
            vanilla.ridges(),
            vanilla.preliminarySurfaceLevel(),
            this.createFinalDensity(vanilla),
            vanilla.veinToggle(),
            vanilla.veinRidged(),
            vanilla.veinGap());
    }


    /** 🔧 MCRe：带外部噪声随机源的 router 变体（Test263 管线传 randomState 根工厂——同源同流）。 */
    public NoiseRouter createRouterWithNoiseFactory(final NoiseRouter vanilla, final PositionalRandomFactory noiseFactory) {
        this.noiseFactoryOverride = noiseFactory;
        this.cachedVanilla = null; // 随机源变了，作废缓存强制重建
        this.cachedTree = null;
        return this.createRouter(vanilla);
    }
}
