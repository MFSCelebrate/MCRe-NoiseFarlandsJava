package net.MinecraftTools.terrain.pipeline;

import java.util.concurrent.CompletableFuture;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.TerrainProvider;
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
import net.minecraft.world.level.levelgen.synth.BlendedNoise;

/**
 * 🔧 MCRe 地形管线系统——正式管线：<b>MCVanilla1_18_1Pipeline</b>（1.18.1 密度链）。
 *
 * <p><b>本版（2026-11-28 重做）的核心变化</b>：旧版只把 <b>final_density / preliminary_surface_level</b>
 * 换成 1.18.1 的形状，却让它们去引用 <b>26.2 的 bootstrap 密度函数</b>（{@code overworld/sloped_cheese} 等）
 * —— 那些定义里带着 26.2 的 <b>jaggedness</b>、<b>MCRe 窗口感知 depth</b>、<b>RIDGES_FOLDED 坐标</b> 与
 * <b>1.19+ 的洞穴厚度调制</b>，与 1.18.1 的地形参数自相矛盾 → 密度场错乱 → 世界不可玩、边境之地形态也不同。
 * 本版改为<b>自建 1.18.1 的 overworld 地形参数</b>（不再引用 26.2 的 holder）。
 *
 * <p><b>1.18.1 ↔ 26.2 的语义对照（全部经源码逐行核对）</b>：
 * <table border="1">
 *   <caption>关键等价 / 差异</caption>
 *   <tr><th>项</th><th>1.18.1</th><th>26.2</th><th>结论</th></tr>
 *   <tr><td>TerrainShaper / TerrainProvider</td>
 *       <td>{@code net/minecraft/world/level/biome/TerrainShaper.java}（offset / factor / jaggedness 三条样条）</td>
 *       <td>{@code net/minecraft/data/worldgen/TerrainProvider.java}</td>
 *       <td><b>逐点等价</b> ✅ 直接复用 26.2 的 TerrainProvider</td></tr>
 *   <tr><td>CubicSpline 求值</td><td>区间二分 + lerp 公式</td><td>同</td><td><b>逐位等价</b> ✅</td></tr>
 *   <tr><td>CubicSpline 端点外推</td><td>value + slope×(input−loc)（0×Inf = NaN）</td>
 *       <td>1.19.2+ 的 linearExtend 捷径（零斜率直接返回端点值）</td>
 *       <td><b>差异</b> ⚠ 管线强制走 1.18.1 语义（{@link SplineExtrapolation1181}）</td></tr>
 *   <tr><td>depth（削峰补枯）</td>
 *       <td>{@code NoiseSampler.computeBaseDensity} = {@code (1 − Y/128) + offset}</td>
 *       <td>{@code yClampedGradient(−64,320,1.5,−1.5) + offset}（26.2）/ MCRe 窗口感知版</td>
 *       <td>在 Y∈[−64,320] 内<b>完全等价</b> ✅；窗外 1.18.1 继续线性延伸</td></tr>
 *   <tr><td>初始密度</td><td>{@code g=(depth+base3d)×factor; density=g×(g>0?4:1)}</td>
 *       <td>{@code 4×quarterNegative(g)}</td><td><b>逐位等价</b> ✅（quarterNegative 即 g<0?g/4:g）</td></tr>
 *   <tr><td>jaggedness 合成</td><td>{@code h>0 ? d×h : d/2×h}</td><td>{@code halfNegative()}</td>
 *       <td><b>逐位等价</b> ✅</td></tr>
 *   <tr><td>洞穴（spaghetti_2d 等）</td><td>1.18.1 无密度函数洞穴（NoiseSampler 内联）</td>
 *       <td>1.19+ 洞穴（带 SPAGHETTI_2D_THICKNESS_MODULATOR）</td>
 *       <td><b>差异</b> ⚠ 沿用数据包引用的 26.2 洞穴（1.18.2-style），见「已知边界」</td></tr>
 * </table>
 *
 * <p><b>已知边界</b>：
 * <ul>
 *   <li>数据包 {@code 1_18_1_overworld.json} 的 {@code final_density} / {@code preliminary_surface_level}
 *       是 <b>1.18.2 风格</b>的密度函数写法（1.18.1 原版没有密度函数）—— 本管线<b>逐字沿用</b>该数据包，
 *       只把其中引用的 {@code overworld/*} 换成 1.18.1 语义的自建定义；</li>
 *   <li>洞穴沿用 26.2 的定义（数据包的引用），与 1.18.1 原版的 NoiseSampler 内联洞穴有形态差异；</li>
 *   <li>surface_rule / biome_source 不接管（仍走 26.2）——1.18.1 原版的表面规则体系与 1.18.2 不同。</li>
 * </ul>
 *
 * <p>引用机制：caves 家族通过 {@link NoiseRouterData#bootstrapHolder} 拿 bootstrap 注册的 Holder
 * （运行时无 DENSITY_FUNCTION registry lookup 可用）——组装树后经 {@link RandomState#wireDensityTree} wiring
 * （bootstrap 定义里的噪声未绑定，求值恒 0，必须绑定 seed 正确的实例）。
 *
 * <p>性能：组装树全为 26.2 vanilla 节点（不可变），per-vanilla RandomState 缓存（照 MCBedrockGenPipeline
 * 的 CachedPatched 模式）——每 world 只组装一次；fillNoise 阶段 patch 命中缓存零成本。
 * BIOMES/SURFACE/CARVERS 不接管（createBiomesVanilla 委托）。
 */
public final class MCVanilla1_18_1Pipeline implements TerrainPipeline {

    public static final MCVanilla1_18_1Pipeline INSTANCE = new MCVanilla1_18_1Pipeline();

    /** 1.18.1 的洞穴入口判定阈值（surfaceWithEntrances / caves 分界） */
    private static final double SURFACE_DENSITY_THRESHOLD = 1.5625;

    /**
     * 🔧 1.18.1 数据包的"负无穷"哨兵值：官方 JSON 用这个巨数模拟负无穷（数据包不支持 -Infinity 字面量）。
     * <p>实测：该值 = <b>-1.0E308</b>（309 位精确十进制 = double {@code 0x1.1ccf385ebc8ap1023} 的精确值；
     * <b>不是 -2^1024</b> —— 2^1024 作 double 会溢出成 Infinity）。Java double 字面量舍入后与 1.18.1 官方解析结果逐位一致。
     */
    private static final double NEGATIVE_SENTINEL =
        -100000000000000001097906362944045541740492309677311846336810682903157585404911491537163328978494688899061249669721172515611590283743140088328307009198146046031271664502933027185697489699588559043338384466165001178426897626212945177628091195786707458122783970171784415105291802893207873272974885715430223118336.0;

    private static final ResourceKey<DensityFunction> KEY_ENTRANCES = dfKey("overworld/caves/entrances");
    private static final ResourceKey<DensityFunction> KEY_PILLARS = dfKey("overworld/caves/pillars");
    private static final ResourceKey<DensityFunction> KEY_SPAGHETTI_2D = dfKey("overworld/caves/spaghetti_2d");
    private static final ResourceKey<DensityFunction> KEY_SPAGHETTI_ROUGHNESS = dfKey("overworld/caves/spaghetti_roughness_function");
    private static final ResourceKey<DensityFunction> KEY_NOODLE = dfKey("overworld/caves/noodle");

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

    /**
     * 🔧 1.18.1 的 overworld 地形参数（<b>自建</b>，不再引用 26.2 的 bootstrap holder）。
     *
     * @param offset       1.18.1 的 offset 样条（= TerrainShaper.offset = spline − 0.50375）
     * @param factor       1.18.1 的 factor 样条
     * @param depth        1.18.1 的 depth = {@code (1 − Y/128) + offset}（在 Y∈[−64,320] 内等价于 26.2 的 yClampedGradient）
     * @param slopedCheese 1.18.1 的 sloped_cheese（= 初始密度 + base_3d_noise）
     */
    private record Terrain1181(DensityFunction offset, DensityFunction factor, DensityFunction depth, DensityFunction slopedCheese) {
    }

    /**
     * 构建 1.18.1 的 overworld 地形参数。
     *
     * <p>offset / factor / jaggedness 样条直接复用 26.2 的 {@link TerrainProvider} —— 它与 1.18.1 的
     * {@code TerrainShaper} <b>逐点等价</b>（已按 1.18.1 源码逐点核对：offset 的 10 个控制点、
     * factor 的 5 个控制点、jaggedness 的 3 个控制点、buildErosionOffsetSpline / getErosionFactor /
     * buildMountainRidgeSplineWithPoints / ridgeSpline 的全部参数与导数）。
     *
     * <p>depth 用 1.18.1 的 {@code NoiseSampler.computeBaseDensity} 语义：
     * {@code (1 − Y/128) + offset}；在正常世界高度 Y∈[−64,320] 内与 26.2 的
     * {@code yClampedGradient(−64,320,1.5,−1.5)} <b>完全等价</b>（两端 1.5 / −1.5、斜率 −1/128 一致），
     * 窗外 1.18.1 继续线性延伸（26.2 则被钳制）。
     *
     * @param vanilla 原版 RandomState（取噪声参数 + 绑定噪声实例）
     * @return 1.18.1 的地形参数
     */
    private static Terrain1181 terrain1181(final RandomState vanilla) {
        final var noises = vanilla.noiseParametersGetter();
        // SHIFT 偏移噪声（1.18.1 的 Noises.SHIFT = "offset"，与 26.2 同名同参）
        final DensityFunction shiftX = DensityFunctions.flatCache(
            DensityFunctions.cache2d(DensityFunctions.shiftA(noises.getOrThrow(Noises.SHIFT))));
        final DensityFunction shiftZ = DensityFunctions.flatCache(
            DensityFunctions.cache2d(DensityFunctions.shiftB(noises.getOrThrow(Noises.SHIFT))));
        // 气候通道（1.18.1 NoiseSampler 的 continentalness / erosion / weirdness）
        final DensityFunction continents = DensityFunctions.flatCache(
            DensityFunctions.shiftedNoise2d(shiftX, shiftZ, 0.25, noises.getOrThrow(Noises.CONTINENTALNESS)));
        final DensityFunction erosion = DensityFunctions.flatCache(
            DensityFunctions.shiftedNoise2d(shiftX, shiftZ, 0.25, noises.getOrThrow(Noises.EROSION)));
        final DensityFunction ridge = DensityFunctions.flatCache(
            DensityFunctions.shiftedNoise2d(shiftX, shiftZ, 0.25, noises.getOrThrow(Noises.RIDGE)));
        // 1.18.1 的 TerrainShaper.makePoint：ridges 坐标 = peaksAndValleys(weirdness)（= 26.2 的 RIDGES_FOLDED）
        final DensityFunction ridgesFolded = new PeaksAndValleys1181(ridge);
        // offset / factor / jaggedness 样条（= 1.18.1 的 TerrainShaper，逐点等价）
        final DensityFunction offset = DensityFunctions.add(
            DensityFunctions.constant(-0.50375),
            DensityFunctions.spline(
                TerrainProvider.overworldOffset(
                    coord(continents), coord(erosion), coord(ridgesFolded), false)));
        final DensityFunction factor = DensityFunctions.spline(
            TerrainProvider.overworldFactor(
                coord(continents), coord(erosion), coord(ridge), coord(ridgesFolded), false));
        final DensityFunction jaggedNoise = DensityFunctions.noise(noises.getOrThrow(Noises.JAGGED), 1500.0, 0.0);
        final DensityFunction jaggedness = DensityFunctions.flatCache(
            DensityFunctions.mul(
                DensityFunctions.spline(
                    TerrainProvider.overworldJaggedness(
                        coord(continents), coord(erosion), coord(ridge), coord(ridgesFolded), false)),
                jaggedNoise.halfNegative()));
        // ★ 1.18.1 的 depth：NoiseSampler.computeBaseDensity = (1 − Y/128) + offset
        //   （正常高度区间内与 26.2 的 yClampedGradient(−64,320,1.5,−1.5) 完全等价；
        //     26.2 的 overworld/depth 是 MCRe 的【窗口感知】魔改 → 1.18.1 管线必须用原版语义）
        final DensityFunction depth = DensityFunctions.add(
            DensityFunctions.yClampedGradient(-64, 320, 1.5, -1.5), offset);
        // ★ 1.18.1 的初始密度：g = (depth + jaggedness) × factor; density = g × (g > 0 ? 4 : 1)
        //   （= 26.2 的 4 × quarterNegative(g)，逐位等价；1.18.1 的 piecewise 在 g=0 时取 1，quarterNegative 亦取 0×4=0 ✓）
        final DensityFunction gradient = DensityFunctions.mul(
            DensityFunctions.constant(4.0),
            DensityFunctions.mul(DensityFunctions.add(depth, jaggedness), factor).quarterNegative());
        final DensityFunction slopedCheese = DensityFunctions.add(
            gradient, BlendedNoise.createUnseeded(0.25, 0.125, 80.0, 160.0, 8.0));
        return new Terrain1181(offset, factor, depth, slopedCheese);
    }

    private static DensityFunctions.Spline.Coordinate coord(final DensityFunction function) {
        return new DensityFunctions.Spline.Coordinate(function);
    }

    /**
     * 🔧 1.18.1 的 {@code peaksAndValleys}（<b>float 语义</b>，逐位复刻）。
     *
     * <p>26.2 的节点版（{@code NoiseRouterData.peaksAndValleys}）用 <b>double</b> 常量
     * （−0.6666666666666666 / −0.3333333333333333）计算，而 1.18.1 的 {@code TerrainShaper.peaksAndValleys}
     * 用 <b>float</b> 常量（0.6666667F / 0.33333334F）→ 末位 ulp 不同。本节点先按 1.18.1 的方式
     * 把输入降到 float，再走 float 公式 → 与 1.18.1 逐位一致。
     *
     * @param input 原始 weirdness（ridge 噪声）
     */
    private record PeaksAndValleys1181(DensityFunction input) implements DensityFunction.SimpleFunction {
        @Override
        public double compute(final DensityFunction.FunctionContext context) {
            return TerrainProvider.peaksAndValleys((float) this.input.compute(context));
        }

        @Override
        public double minValue() {
            // |w|∈[0,1] → |w|−2/3∈[−2/3,1/3] → |·|∈[0,2/3] → −1/3 → [−1,1/3] → ×(−3) → [−1,3]
            return -1.0;
        }

        @Override
        public double maxValue() {
            return 3.0;
        }

        @Override
        public DensityFunction mapChildren(final DensityFunction.Visitor visitor) {
            // ⚠ 必须覆写：默认实现返回 this → 树遍历不会进入本节点 → 内部噪声不会被 wireDensityTree 绑定
            return new PeaksAndValleys1181(visitor.apply(this.input));
        }

        @Override
        public net.minecraft.util.KeyDispatchDataCodec<? extends DensityFunction> codec() {
            throw new UnsupportedOperationException("PeaksAndValleys1181 is code-only (constructed by MCVanilla1_18_1Pipeline)");
        }
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
        // 🔧 管线激活 → 整条世界生成器链走 1.18.1 的样条外推语义（1.19.2+ 的 linearExtend 捷径关闭）
        SplineExtrapolation1181.setForced1181(true);
        final NoiseRouter v = vanilla.router();
        final Terrain1181 terrain = terrain1181(vanilla);
        final var noises = vanilla.noiseParametersGetter();
        final DensityFunction finalDensity = vanilla.wireDensityTree(this.finalDensity1181(terrain, noises));
        final DensityFunction preliminarySurfaceLevel = vanilla.wireDensityTree(this.preliminarySurfaceLevel1181(terrain));
        final NoiseRouter custom = new NoiseRouter(
            v.barrierNoise(),
            v.fluidLevelFloodednessNoise(),
            v.fluidLevelSpreadNoise(),
            v.lavaNoise(),
            v.temperature(),
            v.vegetation(),
            v.continents(),
            v.erosion(),
            terrain.depth(),
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
     * 1.18.1 的 final_density（数据包 {@code 1_18_1_overworld.json} 逐字组装）：
     * min( squeeze(interpolated(mul(0.64, blend_density(内联 slide(caves))))), noodle )。
     * caves 的 range_choice 下界 = 负无穷哨兵（1.18.1 无 ±1000000 限制；数据包巨数哨兵的代码版真身）。
     *
     * <p>★ 与旧版的差异：{@code sloped_cheese} 用的是<b>自建的 1.18.1 定义</b>（旧版引用 26.2 的 holder →
     * 带着 26.2 的 jaggedness / 窗口感知 depth / 1.19+ 洞穴）。
     */
    private DensityFunction finalDensity1181(
        final Terrain1181 terrain,
        final net.minecraft.core.HolderGetter<net.minecraft.world.level.levelgen.synth.NormalNoise.NoiseParameters> noises
    ) {
        final DensityFunction slopedCheese = DensityFunctions.cacheOnce(terrain.slopedCheese());
        final DensityFunction surfaceWithEntrances = DensityFunctions.min(
            slopedCheese,
            DensityFunctions.mul(DensityFunctions.constant(5.0), reference(KEY_ENTRANCES))
        );
        final DensityFunction caves = DensityFunctions.rangeChoice(
            slopedCheese, NEGATIVE_SENTINEL, SURFACE_DENSITY_THRESHOLD,
            surfaceWithEntrances, this.underground1181(slopedCheese, noises));
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
    private DensityFunction underground1181(
        final DensityFunction slopedCheeseCached,
        final net.minecraft.core.HolderGetter<net.minecraft.world.level.levelgen.synth.NormalNoise.NoiseParameters> noises
    ) {
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
    private DensityFunction preliminarySurfaceLevel1181(final Terrain1181 terrain) {
        final DensityFunction cachedOffset = DensityFunctions.cache2d(terrain.offset());
        final DensityFunction cachedFactor = DensityFunctions.cache2d(terrain.factor());
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
        return generator.fillFromNoiseVanilla(blender, custom, structureManager, chunk, !chunk.isGenerationWrite());
    }
}
