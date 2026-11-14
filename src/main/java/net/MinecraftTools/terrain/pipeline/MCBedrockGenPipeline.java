package net.MinecraftTools.terrain.pipeline;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.MinecraftTools.terrain.pipeline.mcbedrockgen.density.OverworldFloatDensity;
import net.MinecraftTools.terrain.pipeline.noise.NoiseSource;
import net.MinecraftTools.terrain.pipeline.noise.VanillaNoiseSource;

/**
 * 🔧 MCRe 地形管线系统——正式管线：<b>MCBedrockGenPipeline</b>（改版柏林噪声底层专属）。
 *
 * <p>背景（大佬 2026-10-24 裁定）：主线柏林噪声底层（synth 包）即将进行大改革——本管线把
 * <b>改革前的改版柏林噪声底层完整复制</b>为管线专属副本（{@code mcbedrockgen.noise} 包：
 * ImprovedNoise/PerlinNoise/NormalNoise/BlendedNoise，含全部魔改——BedrockMode wrap/wrapExact、
 * expandSinglePrecision、exact 精确路径、WorldReposition 接线），改革后主线怎么变都不影响本管线语义。
 *
 * <p>config 联动：副本内的 WorldMainSettingScreen.FarLandsConfigData 引用保持——用户开关
 * （BedrockMode/expandSinglePrecision/exact 等）对本管线照常生效。
 *
 * <p>密度函数接线（2026-11-13 终态）：<b>全 Float 密度链</b>（大佬派活）——
 * {@code mcbedrockgen.density.OverworldFloatDensity} 逐节点镜像主线 NoiseRouterData 的 overworld 链
 * （组合层 add/mul/clamp/spline 插值/滑梯/糖粒全 float；噪声实例 = 本管线副本，Spline 层复用 vanilla
 * float 原生 CubicSpline；base_3d = patched 树提取的重播种副本实例 + 自管 float 插值）。
 * patchRandomState（createNoiseChunk + fillNoise 统一收口）换 finalDensity + preliminarySurfaceLevel，
 * 其余 router 字段（气候/含水层/矿脉——生物群系侧）保持原版 double 域。
 */
public final class MCBedrockGenPipeline implements TerrainPipeline {

    public static final MCBedrockGenPipeline INSTANCE = new MCBedrockGenPipeline();

    private MCBedrockGenPipeline() {
    }

    @Override
    public String id() {
        return "Bedrock-Edition";
    }

    @Override
    public String displayName() {
        return "MC Bedrock Gen（改版柏林底层）";
    }

    @Override
    public NoiseSource createNoiseSource(final RandomState randomState) {
        // 密度树接线后：PipelineNoiseSource 变体（自建实例池喂副本噪声的参数——副本 create 直用主线
        // NormalNoise.NoiseParameters 类型，参数数据共享、实现隔离）
        return VanillaNoiseSource.of(randomState);
    }

    @Override
    public CompletableFuture<ChunkAccess> fillBiomes(
        final NoiseBasedChunkGenerator generator,
        final RandomState randomState,
        final Blender blender,
        final StructureManager structureManager,
        final ChunkAccess chunk
    ) {
        // BIOMES 不接管（气候通道与柏林底层无关——主线 Climate 照跑）
        return generator.createBiomesVanilla(randomState, blender, structureManager, chunk);
    }

    /**
     * 🔧 管线接管修复：全 router 树的 noise 节点绑定本管线副本噪声实例（mcbedrockgen.noise.*，
     * extends 主线类型进 NoiseHolder）——主线噪声改革后本管线语义不变。
     * NoiseChunk 缓存收口（createNoiseChunk）与 fillNoise 都走这里，保证同一语义。
     */
    /**
     * 🔧 性能修复：patched RandomState 缓存（per vanilla 身份，volatile 不可变 holder 原子换对）。
     * Bedrock 树 = 主线 JSON（节点不可变）+ 副本噪声（不可变，同 seed 数值确定——NoiseSourceTest
     * 实测重建确定性），可跨 chunk 共享（vanilla 语义本就 per-world 一份 RandomState）。无缓存时
     * createNoiseChunk + fillNoise 每 chunk 两次全量 createWithNoiseFactory（JSON 树重建 +
     * 副本噪声 octave 初始化）拖慢生成。竞态安全：多线程同时 miss 各自重建，数值等价，后写覆盖无害。
     */
    private record CachedPatched(RandomState vanillaRef, RandomState patched) {
    }

    private volatile CachedPatched cachedPatched;

    /**
     * per-world float 链资产：副本噪声实例池（同 seed 确定性派生——NoiseSourceTest 实锤重建确定性）
     * + 重播种 base_3d（从 patched 树提取，与主线接线同源同流）。
     */
    private record WorldFloatCache(
            RandomState vanillaRef,
            ConcurrentHashMap<ResourceKey<NormalNoise.NoiseParameters>, net.MinecraftTools.terrain.pipeline.mcbedrockgen.noise.NormalNoise> noises,
            BlendedNoise base3d
    ) implements OverworldFloatDensity.NoiseResolver {

        @Override
        public NormalNoise noise(final ResourceKey<NormalNoise.NoiseParameters> key) {
            return this.noises.computeIfAbsent(key, k -> net.MinecraftTools.terrain.pipeline.mcbedrockgen.noise.NormalNoise.create(
                    this.vanillaRef.noiseRandomFactory().fromHashOf(k.identifier()),
                    this.vanillaRef.noiseParametersGetter().getOrThrow(k).value()));
        }
    }

    private volatile WorldFloatCache worldFloat;

    @Override
    public RandomState patchRandomState(final NoiseGeneratorSettings settings, final RandomState vanilla) {
        final RandomState base = this.resolvedBase(settings, vanilla);
        // 🔧 全 float 密度链收口：createNoiseChunk（BIOMES）与 fillNoise（NOISE）各调一次——各自新建
        // float 树（缓存节点带每 chunk 状态，禁跨 chunk 共享）；噪声实例/base_3d 走 per-world 缓存，
        // 两棵树数值逐位一致（同 seed 派生 + 同 base_3d 实例）。NoiseChunk 按 chunk 缓存——先到者的
        // float 树被固化使用，后到者的 router 闲置，语义无差。
        // 已知限制：①非空 blender（旧世界升级混合）的 blend 语义未接——float 链按 Blender.empty() 语义；
        // ②amplified/largeBiomes 变体暂按普通主世界装配。
        return this.withFloatDensity(base);
    }

    /** 原 patched RandomState（主线 JSON 双精度树 + 副本噪声实例绑定）——per-world 缓存（30 号性能修复保留）。 */
    private RandomState resolvedBase(final NoiseGeneratorSettings settings, final RandomState vanilla) {
        final CachedPatched cached = this.cachedPatched;
        if (cached != null && cached.vanillaRef() == vanilla) {
            return cached.patched();
        }
        final var rootFactory = vanilla.noiseRandomFactory();
        final var paramsGetter = vanilla.noiseParametersGetter();
        final RandomState patched = RandomState.createWithNoiseFactory(
            settings,
            paramsGetter,
            rootFactory,
            key -> net.MinecraftTools.terrain.pipeline.mcbedrockgen.noise.NormalNoise.create(
                rootFactory.fromHashOf(key.identifier()),
                paramsGetter.getOrThrow(key).value()),
            random -> net.MinecraftTools.terrain.pipeline.mcbedrockgen.noise.BlendedNoise.create(random));
        this.cachedPatched = new CachedPatched(vanilla, patched);
        return patched;
    }

    /**
     * 🔧 全 float 密度链（大佬 2026-11-13 派活）：finalDensity + preliminarySurfaceLevel 换
     * {@link OverworldFloatDensity}（组合层全 float）；其余 13 个 router 字段（气候/含水层/矿脉——
     * 生物群系侧）保持 base 的原版 double 域。withRouter 共享噪声缓存/随机工厂/sampler，只换 router。
     */
    private RandomState withFloatDensity(final RandomState base) {
        final WorldFloatCache world = this.worldFloatCache(base);
        final OverworldFloatDensity.Chain chain = OverworldFloatDensity.build(world, false);
        final NoiseRouter v = base.router();
        final NoiseRouter floatRouter = new NoiseRouter(
                v.barrierNoise(),
                v.fluidLevelFloodednessNoise(),
                v.fluidLevelSpreadNoise(),
                v.lavaNoise(),
                v.temperature(),
                v.vegetation(),
                v.continents(),
                v.erosion(),
                v.depth(),
                v.ridges(),
                chain.preliminarySurfaceLevel(),
                chain.finalDensity(),
                v.veinToggle(),
                v.veinRidged(),
                v.veinGap());
        return base.withRouter(floatRouter);
    }

    private WorldFloatCache worldFloatCache(final RandomState base) {
        final WorldFloatCache cached = this.worldFloat;
        if (cached != null && cached.vanillaRef() == base) {
            return cached;
        }
        final BlendedNoise[] captured = new BlendedNoise[1];
        base.router().finalDensity().mapAll(node -> {
            if (captured[0] == null && node instanceof BlendedNoise blended) {
                // 重播种后的副本实例（NoiseWiringHelper 用 terrain 随机源重播种——与主线接线同源同流）
                captured[0] = blended;
            }
            return node;
        });
        if (captured[0] == null) {
            throw new IllegalStateException("MCBedrockGen float chain: base_3d (BlendedNoise) not found in patched router");
        }
        final WorldFloatCache built = new WorldFloatCache(base, new ConcurrentHashMap<>(), captured[0]);
        this.worldFloat = built;
        return built;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillNoise(
        final NoiseBasedChunkGenerator generator,
        final Blender blender,
        final RandomState randomState,
        final StructureManager structureManager,
        final ChunkAccess chunk
    ) {
        // 🔧 密度函数树接线（大佬裁定"直接接密度函数主线"）：树用主线（JSON 数据驱动 + NoiseWiringHelper 绑定），
        // **噪声实例换本管线副本**——createWithNoiseFactory 注入噪声工厂，副本 NormalNoise extends 主线（类型兼容进 NoiseHolder），
        // 全 router 的 noise 节点绑定副本实例 → 树跑时全部走副本柏林底层（BedrockMode/exact 等魔改语义随副本）
        // 统一走 patchRandomState（与 createNoiseChunk 缓存收口同一语义）
        final RandomState custom = this.patchRandomState(generator.generatorSettings().value(), randomState);
        return generator.fillFromNoiseVanilla(blender, custom, structureManager, chunk, true);
    }
}
