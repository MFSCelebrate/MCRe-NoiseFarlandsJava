package net.MinecraftTools.terrain.pipeline;

import java.util.concurrent.CompletableFuture;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseRouterData;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

/**
 * 🔧 MCRe 地形管线系统——正式管线：<b>MCVanilla1_18_1Pipeline</b>（1.18.1 密度链）。
 *
 * <p>内容：复刻 <b>1.18.1 官方 overworld noise_router</b> 语义——与 26.2 主线 router 的差异
 * 全部落在 {@code final_density} / {@code preliminary_surface_level} 两个字段：
 * <ul>
 *   <li><b>无 jagged</b>：jagged 噪声是 1.18.2 引入（jagged peaks 特性），1.18.1 的 final_density 无此项；</li>
 *   <li><b>slide 为内联 y_clamped_gradient 形态</b>（顶 240→256 / 底 -64→-40），1.18.2+ 改用
 *       slide() 函数且参数不同——两者不等价，勿混用；</li>
 *   <li><b>range_choice 边界 = 1.18.1 官方巨数哨兵</b>：1.18.1 没有 1.18.2+ 的 ±1000000 限制，
 *       数据包不支持 -Infinity 字面量，官方用接近 double 负极限的巨数（≈ -1.0E308）模拟——本管线
 *       逐字沿用该巨数（实测真 -Infinity 会在平原→山体过渡产生区块切断裂缝，见 {@link #NEGATIVE_SENTINEL}）；</li>
 *   <li>其余 13 个 router 字段（气候通道/preliminarySurfaceLevel 之外/aquifer/矿脉）与 26.2 相同，照抄 vanilla。</li>
 * </ul>
 *
 * <p>引用机制：sloped_cheese/entrances/pillars/spaghetti_2d/spaghetti_roughness/noodle/offset/factor
 * 通过 {@link NoiseRouterData#bootstrapHolder} 拿 bootstrap 注册的 Holder（运行时无 DENSITY_FUNCTION
 * registry lookup 可用）——组装树后经 {@link RandomState#wireDensityTree} wiring（bootstrap 定义里的
 * 噪声未绑定，求值恒 0，必须绑定 seed 正确的实例；HolderHolder 内部由 mapChildren 递归自动处理）。
 *
 * <p>性能：组装树全为 26.2 vanilla 节点（不可变），per-vanilla RandomState 缓存（照 MCBedrockGenPipeline
 * 的 CachedPatched 模式）——每 world 只组装一次；fillNoise 阶段 patch 命中缓存零成本。
 * BIOMES/SURFACE/CARVERS 不接管（createBiomesVanilla 委托——1.18.1 的群系照 26.2 气候通道语义）。
 */
public final class MCVanilla1_18_1Pipeline implements TerrainPipeline {

    public static final MCVanilla1_18_1Pipeline INSTANCE = new MCVanilla1_18_1Pipeline();

    /** 1.18.1 的洞穴入口判定阈值（surfaceWithEntrances / caves 分界） */
    private static final double SURFACE_DENSITY_THRESHOLD = 1.5625;

    /**
     * 🔧 1.18.1 数据包的"负无穷"哨兵值（INF32768 指点）：官方 JSON 用这个巨数模拟负无穷
     * （数据包不支持 -Infinity 字面量）。实测锤：该值 ≈ -1.0E308（hex -0x1.1ccf385ebc8ap1023，
     * 接近 double 负极限）。实测用真 -Infinity 会在平原→山体过渡产生区块切断裂缝
     * （-∞ 的 minValue=-∞ 沿节点树传播污染上层值域计算，与官方巨数的有限值域行为有差异）——
     * 按指点改回原版巨数，Java double 字面量舍入后与 1.18.1 官方解析结果逐位一致。
     */
    private static final double NEGATIVE_SENTINEL =
        -100000000000000001097906362944045541740492309677311846336810682903157585404911491537163328978494688899061249669721172515611590283743140088328307009198146046031271664502933027185697489699588559043338384466165001178426897626212945177628091195786707458122783970171784415105291802893207873272974885715430223118336.0;

    private static final ResourceKey<DensityFunction> KEY_SLOPED_CHEESE = dfKey("overworld/sloped_cheese");
    private static final ResourceKey<DensityFunction> KEY_ENTRANCES = dfKey("overworld/caves/entrances");
    private static final ResourceKey<DensityFunction> KEY_PILLARS = dfKey("overworld/caves/pillars");
    private static final ResourceKey<DensityFunction> KEY_SPAGHETTI_2D = dfKey("overworld/caves/spaghetti_2d");
    private static final ResourceKey<DensityFunction> KEY_SPAGHETTI_ROUGHNESS = dfKey("overworld/caves/spaghetti_roughness_function");
    private static final ResourceKey<DensityFunction> KEY_NOODLE = dfKey("overworld/caves/noodle");
    private static final ResourceKey<DensityFunction> KEY_OFFSET = dfKey("overworld/offset");
    private static final ResourceKey<DensityFunction> KEY_FACTOR = dfKey("overworld/factor");

    private MCVanilla1_18_1Pipeline() {
    }

    private static ResourceKey<DensityFunction> dfKey(final String path) {
        return ResourceKey.create(Registries.DENSITY_FUNCTION, Identifier.withDefaultNamespace(path));
    }

    /** bootstrap 注册的 Holder 引用（走 {@link RandomState#wireDensityTree} 绑定噪声后求值正确） */
    private static DensityFunction reference(final ResourceKey<DensityFunction> key) {
        final Holder<DensityFunction> holder = NoiseRouterData.bootstrapHolder(key);
        if (holder == null) {
            throw new IllegalStateException("Density function not bootstrapped: " + key);
        }
        return new DensityFunctions.HolderHolder(holder);
    }

    @Override
    public String id() {
        return "Java-1.18.1";
    }

    @Override
    public String displayName() {
        return "MC Vanilla 1.18.1 密度链";
    }

    /** per-vanilla 身份缓存（volatile 不可变 holder 原子换对——照 MCBedrockGenPipeline；树全 vanilla 节点不可变，跨 chunk 共享安全） */
    private record CachedPatched(RandomState vanillaRef, RandomState patched) {
    }

    private volatile CachedPatched cachedPatched;

    @Override
    public RandomState patchRandomState(final NoiseGeneratorSettings settings, final RandomState vanilla) {
        final CachedPatched cached = this.cachedPatched;
        if (cached != null && cached.vanillaRef() == vanilla) {
            return cached.patched();
        }
        final NoiseRouter v = vanilla.router();
        final DensityFunction finalDensity = vanilla.wireDensityTree(this.finalDensity1181(vanilla));
        final DensityFunction preliminarySurfaceLevel = vanilla.wireDensityTree(this.preliminarySurfaceLevel1181(vanilla));
        final NoiseRouter custom = new NoiseRouter(
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
            preliminarySurfaceLevel,
            finalDensity,
            v.veinToggle(),
            v.veinRidged(),
            v.veinGap());
        final RandomState patched = vanilla.withRouter(custom);
        this.cachedPatched = new CachedPatched(vanilla, patched);
        return patched;
    }

    /**
     * 1.18.1 的 final_density（大佬提供的官方 JSON 逐字组装）：
     * min( squeeze(interpolated(mul(0.64, blend_density(内联 slide(caves))))), noodle )。
     * caves 的 range_choice 下界 = 负无穷（1.18.1 无 ±1000000 限制；数据包巨数哨兵的代码版真身）。
     */
    private DensityFunction finalDensity1181(final RandomState vanilla) {
        final DensityFunction slopedCheese = DensityFunctions.cacheOnce(reference(KEY_SLOPED_CHEESE));
        final DensityFunction surfaceWithEntrances = DensityFunctions.min(
            slopedCheese,
            DensityFunctions.mul(DensityFunctions.constant(5.0), reference(KEY_ENTRANCES))
        );
        final DensityFunction caves = DensityFunctions.rangeChoice(
            slopedCheese, NEGATIVE_SENTINEL, SURFACE_DENSITY_THRESHOLD,
            surfaceWithEntrances, this.underground1181(vanilla, slopedCheese));
        // 1.18.1 内联 slide：底部 -64→-40 渐变 0.1171875、顶部 240→256 渐变 -0.078125
        final DensityFunction inlineSlide = DensityFunctions.add(
            DensityFunctions.constant(0.1171875),
            DensityFunctions.mul(
                DensityFunctions.yClampedGradient(-64, -40, 0.0, 1.0),
                DensityFunctions.add(
                    DensityFunctions.constant(-0.1171875),
                    DensityFunctions.add(
                        DensityFunctions.constant(-0.078125),
                        DensityFunctions.mul(
                            DensityFunctions.yClampedGradient(240, 256, 1.0, 0.0),
                            DensityFunctions.add(DensityFunctions.constant(0.078125), caves))))));
        final DensityFunction blended = DensityFunctions.blendDensity(inlineSlide);
        final DensityFunction squeezed = DensityFunctions.interpolated(
            DensityFunctions.mul(blended, DensityFunctions.constant(0.64))).squeeze();
        return DensityFunctions.min(squeezed, reference(KEY_NOODLE));
    }

    /**
     * 1.18.1 的 underground（caves 的 when_out_of_range——sloped_cheese ≥ 1.5625 的高柱区）：
     * max( min( min(cave_layer²×4 + 双 clamp 奶酪, entrances), spaghetti 系 ),
     *      pillars 开关（< 0.03 → 负无穷哨兵被 max 吃掉 = 禁用柱子）)。
     */
    private DensityFunction underground1181(final RandomState vanilla, final DensityFunction slopedCheeseCached) {
        final var noises = vanilla.noiseParametersGetter();
        final DensityFunction layerNoise = DensityFunctions.noise(noises.getOrThrow(Noises.CAVE_LAYER), 8.0);
        final DensityFunction layerizedCaverns = DensityFunctions.mul(DensityFunctions.constant(4.0), layerNoise.square());
        final DensityFunction cheese = DensityFunctions.noise(noises.getOrThrow(Noises.CAVE_CHEESE), 0.6666666666666666);
        final DensityFunction solidifiedCheeseWithTopSlide = DensityFunctions.add(
            DensityFunctions.add(DensityFunctions.constant(0.27), cheese).clamp(-1.0, 1.0),
            DensityFunctions.add(
                DensityFunctions.constant(1.5),
                DensityFunctions.mul(DensityFunctions.constant(-0.64), slopedCheeseCached)).clamp(0.0, 0.5)
        );
        final DensityFunction baseCaveDensity = DensityFunctions.add(layerizedCaverns, solidifiedCheeseWithTopSlide);
        final DensityFunction undergroundSubtractions = DensityFunctions.min(
            DensityFunctions.min(baseCaveDensity, reference(KEY_ENTRANCES)),
            DensityFunctions.add(reference(KEY_SPAGHETTI_2D), reference(KEY_SPAGHETTI_ROUGHNESS))
        );
        final DensityFunction pillarsWithoutCutoff = reference(KEY_PILLARS);
        final DensityFunction pillars = DensityFunctions.rangeChoice(
            pillarsWithoutCutoff, NEGATIVE_SENTINEL, 0.03,
            DensityFunctions.constant(NEGATIVE_SENTINEL), pillarsWithoutCutoff
        );
        return DensityFunctions.max(undergroundSubtractions, pillars);
    }

    /**
     * 1.18.1 的 preliminary_surface_level：find_top_surface(cell_height=8, lower_bound=-64) +
     * 与 final_density 同款内联 slide；depth 为显式内联 y_clamped_gradient(-64→320, 1.5→-1.5)
     * （26.2 的 offsetToDepth 是窗口感知版，1.18.1 语义用原版梯度）；upper_bound 为 remap 展开式。
     */
    private DensityFunction preliminarySurfaceLevel1181(final RandomState vanilla) {
        final DensityFunction cachedOffset = DensityFunctions.cache2d(reference(KEY_OFFSET));
        final DensityFunction cachedFactor = DensityFunctions.cache2d(reference(KEY_FACTOR));
        final DensityFunction gradientDensity = DensityFunctions.mul(
            DensityFunctions.constant(4.0),
            DensityFunctions.mul(
                DensityFunctions.add(
                    DensityFunctions.yClampedGradient(-64, 320, 1.5, -1.5),
                    cachedOffset),
                cachedFactor).quarterNegative());
        final DensityFunction clamped = DensityFunctions.add(
            DensityFunctions.constant(-0.703125), gradientDensity).clamp(-64.0, 64.0);
        final DensityFunction density = DensityFunctions.add(
            DensityFunctions.constant(-0.390625),
            DensityFunctions.add(
                DensityFunctions.constant(0.1171875),
                DensityFunctions.mul(
                    DensityFunctions.yClampedGradient(-64, -40, 0.0, 1.0),
                    DensityFunctions.add(
                        DensityFunctions.constant(-0.1171875),
                        DensityFunctions.add(
                            DensityFunctions.constant(-0.078125),
                            DensityFunctions.mul(
                                DensityFunctions.yClampedGradient(240, 256, 1.0, 0.0),
                                DensityFunctions.add(DensityFunctions.constant(0.078125), clamped)))))));
        final DensityFunction upperBound = DensityFunctions.add(
            DensityFunctions.constant(128.0),
            DensityFunctions.mul(
                DensityFunctions.constant(-128.0),
                DensityFunctions.add(
                    DensityFunctions.mul(DensityFunctions.constant(0.2734375), cachedFactor.invert()),
                    DensityFunctions.mul(DensityFunctions.constant(-1.0), cachedOffset))))
            .clamp(-40.0, 320.0);
        return DensityFunctions.findTopSurface(density, upperBound, -64, 8);
    }

    @Override
    public CompletableFuture<ChunkAccess> fillBiomes(
        final NoiseBasedChunkGenerator generator,
        final RandomState randomState,
        final Blender blender,
        final StructureManager structureManager,
        final ChunkAccess chunk
    ) {
        // 不接管 BIOMES——委托原版路径（1.18.1 树只换密度字段，气候通道照 26.2）
        return generator.createBiomesVanilla(randomState, blender, structureManager, chunk);
    }

    @Override
    public CompletableFuture<ChunkAccess> fillNoise(
        final NoiseBasedChunkGenerator generator,
        final Blender blender,
        final RandomState randomState,
        final StructureManager structureManager,
        final ChunkAccess chunk
    ) {
        // patch 命中 per-world 缓存零成本；走原版 NOISE 填充路径（NoiseChunk/插值/aquifer/heightmap 全套 26.2 基建照跑）
        final RandomState custom = this.patchRandomState(generator.generatorSettings().value(), randomState);
        return generator.fillFromNoiseVanilla(blender, custom, structureManager, chunk, true);
    }
}
