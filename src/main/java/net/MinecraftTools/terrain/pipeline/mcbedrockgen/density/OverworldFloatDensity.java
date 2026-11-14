package net.MinecraftTools.terrain.pipeline.mcbedrockgen.density;

import com.mojang.serialization.MapCodec;

import net.minecraft.data.worldgen.TerrainProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.CubicSpline;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.MinecraftTools.terrain.pipeline.mcbedrockgen.density.FloatDensity.AbsF;
import net.MinecraftTools.terrain.pipeline.mcbedrockgen.density.FloatDensity.AddF;
import net.MinecraftTools.terrain.pipeline.mcbedrockgen.density.FloatDensity.Cache2dF;
import net.MinecraftTools.terrain.pipeline.mcbedrockgen.density.FloatDensity.CacheOnceF;
import net.MinecraftTools.terrain.pipeline.mcbedrockgen.density.FloatDensity.ClampF;
import net.MinecraftTools.terrain.pipeline.mcbedrockgen.density.FloatDensity.ConstantF;
import net.MinecraftTools.terrain.pipeline.mcbedrockgen.density.FloatDensity.CubeF;
import net.MinecraftTools.terrain.pipeline.mcbedrockgen.density.FloatDensity.F;
import net.MinecraftTools.terrain.pipeline.mcbedrockgen.density.FloatDensity.FindTopSurfaceF;
import net.MinecraftTools.terrain.pipeline.mcbedrockgen.density.FloatDensity.FlatCacheF;
import net.MinecraftTools.terrain.pipeline.mcbedrockgen.density.FloatDensity.HalfNegativeF;
import net.MinecraftTools.terrain.pipeline.mcbedrockgen.density.FloatDensity.InvertF;
import net.MinecraftTools.terrain.pipeline.mcbedrockgen.density.FloatDensity.InterpolatedF;
import net.MinecraftTools.terrain.pipeline.mcbedrockgen.density.FloatDensity.LerpF;
import net.MinecraftTools.terrain.pipeline.mcbedrockgen.density.FloatDensity.MaxF;
import net.MinecraftTools.terrain.pipeline.mcbedrockgen.density.FloatDensity.MinF;
import net.MinecraftTools.terrain.pipeline.mcbedrockgen.density.FloatDensity.MulF;
import net.MinecraftTools.terrain.pipeline.mcbedrockgen.density.FloatDensity.NoiseF;
import net.MinecraftTools.terrain.pipeline.mcbedrockgen.density.FloatDensity.QuarterNegativeF;
import net.MinecraftTools.terrain.pipeline.mcbedrockgen.density.FloatDensity.RangeChoiceF;
import net.MinecraftTools.terrain.pipeline.mcbedrockgen.density.FloatDensity.ShiftAF;
import net.MinecraftTools.terrain.pipeline.mcbedrockgen.density.FloatDensity.ShiftBF;
import net.MinecraftTools.terrain.pipeline.mcbedrockgen.density.FloatDensity.ShiftedNoise2dF;
import net.MinecraftTools.terrain.pipeline.mcbedrockgen.density.FloatDensity.SquareF;
import net.MinecraftTools.terrain.pipeline.mcbedrockgen.density.FloatDensity.SqueezeF;
import net.MinecraftTools.terrain.pipeline.mcbedrockgen.density.FloatDensity.WindowedDepthGradientF;
import net.MinecraftTools.terrain.pipeline.mcbedrockgen.density.FloatDensity.YClampedGradientF;
import net.MinecraftTools.terrain.pipeline.mcbedrockgen.density.FloatDensity.YF;

import static net.MinecraftTools.terrain.pipeline.mcbedrockgen.density.FloatDensity.mappedNoiseF;

/**
 * 🔧 MCRe Bedrock-Edition 管线：原版 overworld 密度链的<b>全 Float</b> 组装（大佬 2026-11-13 派活）。
 *
 * <p>逐节点镜像主线 {@code NoiseRouterData}（26.2 代码装配版）：offset/factor/depth/jaggedness →
 * initialDensity → sloped_cheese（base_3d 插值）→ entrances/underground/pillars → caves →
 * slideOverworld → postProcess（squeeze）→ min(noodle)；外加 preliminary_surface_level 全链。
 *
 * <p><b>复用与自管的边界</b>：
 * <ul>
 *   <li>Spline 层<b>零移植</b>——26.2 主线 CubicSpline 本就 float 原生（locations/values = float[]），
 *       直接用 {@link TerrainProvider} 数据 + vanilla {@code DensityFunctions.Spline} 节点，
 *       坐标喂本家族的 float 气候函数（经 double 载体精确往返）。</li>
 *   <li>噪声实例 = mcbedrockgen 副本（{@code NormalNoise.getValue} 内部 float 全链 + BedrockMode/wrap 魔改）。</li>
 *   <li>base_3d（BlendedNoise）= 从 patched 树提取的<b>重播种副本实例</b>（与主线接线同源同流），外面包
 *       本家族的 float 插值（替代 vanilla 的 double NoiseInterpolator）。</li>
 *   <li>BlendAlpha/BlendOffset/BlendDensity 按 Blender.empty() 语义内联（1.0 / 0.0 / 透传）——
 *       旧世界升级混合不支持（管线回落原版路径）。</li>
 * </ul>
 *
 * <p><b>已知限制（记录在案）</b>：①amplified/largeBiomes 变体暂未检测（按普通主世界装配）；
 * ②WindowedDepthGradient 窗口取 vanilla 主世界域 -64..320（超高世界退化，见 FloatDensity 注释）。
 *
 * <p><b>生命周期</b>：{@link #build} 每次 fillNoise/patchRandomState 调用新建（缓存节点带每 chunk 状态，
 * 禁跨 chunk 共享）；噪声实例与 base_3d 经 {@link NoiseResolver} 走 per-world 缓存（构建开销 = 纯节点组装）。
 */
public final class OverworldFloatDensity {

    private OverworldFloatDensity() {
    }

    /** 噪声解析器：副本实例（per-world 缓存，同 seed 派生确定性）+ 重播种的 base_3d。 */
    public interface NoiseResolver {

        NormalNoise noise(ResourceKey<NormalNoise.NoiseParameters> key);

        BlendedNoise base3d();
    }

    /**
     * 🔧 大佬 2026-11-14 裁定：<b>float 链禁用 jagged</b>（照 26.3 管线 Vanilla263 的 enableJagged=false 语义
     * —— <b>摘节点而非塞 0</b>："0×NaN 仍会照常传播"，塞 0 不挡 NaN）。
     * <p>原因：jagged 的 xzScale=1500 使噪声输入在 2^31 边界溢出（float 域产物为 Inf/NaN），
     * 而 jaggedness 外的 flatCache 不拦 NaN → 传播至整条链 → 地形退化为虚无、密度全面 NaN。
     * <p>禁用法 = 把 jaggedness 从 {@code add(depth, jaggedness)} 里摘掉（initialDensity 只剩 depth×factor）。
     */
    private static final boolean ENABLE_JAGGED = false;

    /** 产出：可直接喂进 NoiseRouter 的 double 载体适配（内部全 float 求值）。 */
    public record Chain(DensityFunction finalDensity, DensityFunction preliminarySurfaceLevel) {
    }

    public static Chain build(final NoiseResolver resolver, final boolean amplified) {
        // ── 气候/偏移（主线 bootstrap：SHIFT_X/SHIFT_Z/CONTINENTS/EROSION/RIDGES/RIDGES_FOLDED）──
        final F shiftX = new FlatCacheF(new Cache2dF(new ShiftAF(resolver.noise(Noises.SHIFT))));
        final F shiftZ = new FlatCacheF(new Cache2dF(new ShiftBF(resolver.noise(Noises.SHIFT))));
        final F continents = new FlatCacheF(new ShiftedNoise2dF(shiftX, shiftZ, 0.25, resolver.noise(Noises.CONTINENTALNESS)));
        final F erosion = new FlatCacheF(new ShiftedNoise2dF(shiftX, shiftZ, 0.25, resolver.noise(Noises.EROSION)));
        final F ridge = new FlatCacheF(new ShiftedNoise2dF(shiftX, shiftZ, 0.25, resolver.noise(Noises.RIDGE)));
        final F ridgesFolded = peaksAndValleysF(ridge);
        final F jaggedNoise = new NoiseF(resolver.noise(Noises.JAGGED), 1500.0, 0.0);

        // ── registerTerrainNoises（offset/factor/depth/jaggedness/initialDensity/sloped_cheese）──
        final F blendAlpha = new ConstantF(1.0f);
        final F offset = new FlatCacheF(new Cache2dF(new LerpF(blendAlpha, new ConstantF(0.0f),
                new AddF(new ConstantF(-0.50375f),
                        splineF(TerrainProvider.overworldOffset(coord(continents), coord(erosion), coord(ridgesFolded), amplified))))));
        final F factor = new FlatCacheF(new Cache2dF(new LerpF(blendAlpha, new ConstantF(10.0f),
                splineF(TerrainProvider.overworldFactor(coord(continents), coord(erosion), coord(ridge), coord(ridgesFolded), amplified)))));
        final F depth = new AddF(new WindowedDepthGradientF(offset), offset);
        final F unscaledJaggedness = new FlatCacheF(new Cache2dF(new LerpF(blendAlpha, new ConstantF(0.0f),
                splineF(TerrainProvider.overworldJaggedness(coord(continents), coord(erosion), coord(ridge), coord(ridgesFolded), amplified)))));
        final F jaggedness = new FlatCacheF(new MulF(unscaledJaggedness, new HalfNegativeF(jaggedNoise)));
        // 🔧 jagged 禁用（ENABLE_JAGGED=false）：摘掉 jaggedness 节点（不是乘 0——0×NaN 照样传播）
        final F initialDensity = ENABLE_JAGGED
                ? new MulF(new ConstantF(4.0f), new QuarterNegativeF(new MulF(new AddF(depth, jaggedness), factor)))
                : new MulF(new ConstantF(4.0f), new QuarterNegativeF(new MulF(depth, factor)));
        final F base3d = new InterpolatedF(
                (x, y, z) -> (float) resolver.base3d().compute(new DensityFunction.SinglePointContext(x, y, z)));
        final F slopedCheese = new CacheOnceF(new AddF(initialDensity, base3d));

        // ── overworld() caves 装配 ──
        final F entrances = entrancesF(resolver);
        final F surfaceWithEntrances = new MinF(slopedCheese, new MulF(new ConstantF(5.0f), entrances));
        final F underground = undergroundF(resolver, slopedCheese);
        final F caves = new RangeChoiceF(slopedCheese, -1000000.0f, 1.5625f, surfaceWithEntrances, underground);

        // ── slide + postProcess（blendDensity 透传 + 0.64 + squeeze，全 float）──
        final F postProcessed = new SqueezeF(new InterpolatedF(new MulF(slideOverworldF(caves, amplified), new ConstantF(0.64f))));
        final F noodle = noodleF(resolver);
        final F finalDensityF = new MinF(postProcessed, noodle);

        // ── preliminary_surface_level ──
        final F psF = preliminarySurfaceLevelF(offset, factor, amplified);

        return new Chain(new Adapter(finalDensityF, -4000.0f, 4000.0f), new Adapter(psF, -64.0f, 320.0f));
    }

    // ──────── registerTerrainNoises 同款子装配 ────────

    private static F peaksAndValleysF(final F weirdness) {
        return new MulF(
                new AddF(new AbsF(new AddF(new AbsF(weirdness), new ConstantF(-0.6666666666666666f))), new ConstantF(-0.3333333333333333f)),
                new ConstantF(-3.0f));
    }

    private static F noiseGradientDensityF(final F factor, final F depthWithJaggedness) {
        return new MulF(new ConstantF(4.0f), new QuarterNegativeF(new MulF(depthWithJaggedness, factor)));
    }

    // ──────── slide（主线 slide 同参数序）────────

    private static F slideOverworldF(final F caves, final boolean amplified) {
        return slideF(caves, -64, 384,
                amplified ? 16 : 80, amplified ? 0 : 64, -0.078125f,
                0, 24, amplified ? 0.4f : 0.1171875f);
    }

    private static F slideF(final F caves, final int minY, final int height, final int topStartY, final int topEndY,
            final float topTarget, final int bottomStartY, final int bottomEndY, final float bottomTarget) {
        final F topFactor = new YClampedGradientF(minY + height - topStartY, minY + height - topEndY, 1.0f, 0.0f);
        final F withTop = new LerpF(topFactor, new ConstantF(topTarget), caves);
        final F bottomFactor = new YClampedGradientF(minY + bottomStartY, minY + bottomEndY, 0.0f, 1.0f);
        return new LerpF(bottomFactor, new ConstantF(bottomTarget), withTop);
    }

    // ──────── caves 节点（entrances/spaghetti/noodle/pillars/underground）────────

    private static F entrancesF(final NoiseResolver r) {
        final F rarity = new CacheOnceF(new NoiseF(r.noise(Noises.SPAGHETTI_3D_RARITY), 2.0, 1.0));
        final F thick = mappedNoiseF(r.noise(Noises.SPAGHETTI_3D_THICKNESS), 1.0, 1.0, -0.065f, -0.088f);
        final F cave1 = wrapRarity3dF(rarity, r.noise(Noises.SPAGHETTI_3D_1));
        final F cave2 = wrapRarity3dF(rarity, r.noise(Noises.SPAGHETTI_3D_2));
        final F spaghetti3D = new ClampF(new AddF(new MaxF(cave1, cave2), thick), -1.0f, 1.0f);
        final F roughness = spaghettiRoughnessF(r);
        final F bigEntrance = new AddF(
                new AddF(new NoiseF(r.noise(Noises.CAVE_ENTRANCE), 0.75, 0.5), new ConstantF(0.37f)),
                new YClampedGradientF(-10, 30, 0.3f, 0.0f));
        return new CacheOnceF(new MinF(bigEntrance, new AddF(roughness, spaghetti3D)));
    }

    private static F spaghettiRoughnessF(final NoiseResolver r) {
        return new MulF(
                mappedNoiseF(r.noise(Noises.SPAGHETTI_ROUGHNESS_MODULATOR), 1.0, 1.0, 0.0f, -0.1f),
                new AddF(new AbsF(new NoiseF(r.noise(Noises.SPAGHETTI_ROUGHNESS), 1.0, 1.0)), new ConstantF(-0.4f)));
    }

    private static F spaghetti2DF(final NoiseResolver r) {
        final F s2dRarity = new NoiseF(r.noise(Noises.SPAGHETTI_2D_MODULATOR), 2.0, 1.0);
        final F s2dCave = wrapRarity2dF(s2dRarity, r.noise(Noises.SPAGHETTI_2D));
        final F elev = mappedNoiseF(r.noise(Noises.SPAGHETTI_2D_ELEVATION), 1.0, 1.0, 0.0f, -8.0f);
        final F thickMod = new CacheOnceF(mappedNoiseF(r.noise(Noises.SPAGHETTI_2D_THICKNESS), 2.0, 1.0, -0.6f, -1.3f));
        final F sloped = new AbsF(new AddF(new FlatCacheF(elev), new YClampedGradientF(-64, 320, 8.0f, -40.0f)));
        final F layerRidged = new CubeF(new AddF(sloped, thickMod));
        final F caveNoise = new AddF(s2dCave, new MulF(new ConstantF(0.083f), thickMod));
        return new ClampF(new MaxF(caveNoise, layerRidged), -1.0f, 1.0f);
    }

    private static F pillarsF(final NoiseResolver r) {
        final F pillarNoise = new NoiseF(r.noise(Noises.PILLAR), 25.0, 0.3);
        final F rareness = mappedNoiseF(r.noise(Noises.PILLAR_RARENESS), 1.0, 1.0, 0.0f, -2.0f);
        final F thickness = mappedNoiseF(r.noise(Noises.PILLAR_THICKNESS), 1.0, 1.0, 0.0f, 1.1f);
        final F withRareness = new AddF(new MulF(pillarNoise, new ConstantF(2.0f)), rareness);
        final F raw = new CacheOnceF(new MulF(withRareness, new CubeF(thickness)));
        return new RangeChoiceF(raw, -1000000.0f, 0.03f, new ConstantF(-1000000.0f), raw);
    }

    private static F noodleF(final NoiseResolver r) {
        final F y = new YF(DimensionType.MIN_Y, DimensionType.MAX_Y);
        final F toggle = new InterpolatedF(new RangeChoiceF(y, -60.0f, 321.0f,
                new NoiseF(r.noise(Noises.NOODLE), 1.0, 1.0), new ConstantF(-1.0f)));
        final F thickness = new InterpolatedF(new RangeChoiceF(y, -60.0f, 321.0f,
                mappedNoiseF(r.noise(Noises.NOODLE_THICKNESS), 1.0, 1.0, -0.05f, -0.1f), new ConstantF(0.0f)));
        final F ridgeA = new InterpolatedF(new RangeChoiceF(y, -60.0f, 321.0f,
                new NoiseF(r.noise(Noises.NOODLE_RIDGE_A), 2.6666666666666665, 2.6666666666666665), new ConstantF(0.0f)));
        final F ridgeB = new InterpolatedF(new RangeChoiceF(y, -60.0f, 321.0f,
                new NoiseF(r.noise(Noises.NOODLE_RIDGE_B), 2.6666666666666665, 2.6666666666666665), new ConstantF(0.0f)));
        final F ridged = new MulF(new ConstantF(1.5f), new MaxF(new AbsF(ridgeA), new AbsF(ridgeB)));
        return new RangeChoiceF(toggle, -1000000.0f, 0.0f, new ConstantF(64.0f), new AddF(thickness, ridged));
    }

    private static F undergroundF(final NoiseResolver r, final F slopedCheese) {
        final F spaghetti2D = spaghetti2DF(r);
        final F roughness = spaghettiRoughnessF(r);
        final F layerNoise = new NoiseF(r.noise(Noises.CAVE_LAYER), 1.0, 8.0);
        final F cheese = new NoiseF(r.noise(Noises.CAVE_CHEESE), 1.0, 0.6666666666666666);
        final F solidifiedCheese = new AddF(
                new ClampF(new AddF(new ConstantF(0.27f), cheese), -1.0f, 1.0f),
                new ClampF(new AddF(new ConstantF(1.5f), new MulF(new ConstantF(-0.64f), slopedCheese)), 0.0f, 0.5f));
        final F baseCaveDensity = new AddF(new MulF(new ConstantF(4.0f), new SquareF(layerNoise)), solidifiedCheese);
        final F subtractions = new MinF(
                new MinF(baseCaveDensity, entrancesF(r)),
                new AddF(spaghetti2D, roughness));
        return new MaxF(subtractions, pillarsF(r));
    }

    // ──────── QuantizedSpaghettiRarity（intervalSelect = 阈值分档，主线同语义）────────

    private static F noiseForRarityF(final NormalNoise noise, final double rarity) {
        return new MulF(new ConstantF((float) rarity), new NoiseF(noise, 1.0 / rarity, 1.0 / rarity));
    }

    private static F wrapRarity3dF(final F input, final NormalNoise noise) {
        return intervalSelectF(input, new float[] { -0.5f, 0.0f, 0.5f },
                noiseForRarityF(noise, 0.75), noiseForRarityF(noise, 1.0), noiseForRarityF(noise, 1.5), noiseForRarityF(noise, 2.0));
    }

    private static F wrapRarity2dF(final F input, final NormalNoise noise) {
        return intervalSelectF(input, new float[] { -0.75f, -0.5f, 0.5f, 0.75f },
                noiseForRarityF(noise, 0.5), noiseForRarityF(noise, 0.75), noiseForRarityF(noise, 1.0), noiseForRarityF(noise, 2.0),
                noiseForRarityF(noise, 3.0));
    }

    private static F intervalSelectF(final F input, final float[] thresholds, final F... functions) {
        return new IntervalSelectF(input, thresholds, functions);
    }

    private record IntervalSelectF(F input, float[] thresholds, F[] functions) implements F {

        @Override
        public float compute(final int x, final int y, final int z) {
            final float v = this.input.compute(x, y, z);
            for (int i = 0; i < this.thresholds.length; i++) {
                if (v < this.thresholds[i]) {
                    return this.functions[i].compute(x, y, z);
                }
            }
            return this.functions[this.functions.length - 1].compute(x, y, z);
        }
    }

    // ──────── preliminary_surface_level（主线同款装配）────────

    private static F preliminarySurfaceLevelF(final F offset, final F factor, final boolean amplified) {
        final F cachedFactor = new Cache2dF(factor);
        final F cachedOffset = new Cache2dF(offset);
        // remap(input, 1.5, -1.5, -64, 320)：factor = 384/-3 = -128，offset = -64 - 1.5×(-128) = 128
        F upperBound = new AddF(
                new MulF(new ConstantF(0.2734375f), new InvertF(cachedFactor)),
                new MulF(new ConstantF(-1.0f), cachedOffset));
        upperBound = new AddF(new MulF(new ConstantF(-128.0f), upperBound), new ConstantF(128.0f));
        upperBound = new ClampF(upperBound, -40.0f, 320.0f);
        F density = new AddF(noiseGradientDensityF(cachedFactor, new AddF(new WindowedDepthGradientF(cachedOffset), cachedOffset)),
                new ConstantF(-0.703125f));
        density = new ClampF(density, -64.0f, 64.0f);
        density = slideOverworldF(density, amplified);
        density = new AddF(density, new ConstantF(-0.390625f));
        return new FindTopSurfaceF(density, upperBound, -64, 8);
    }

    // ──────── Spline 复用（vanilla float 原生层）────────

    private static DensityFunctions.Spline.Coordinate coord(final F source) {
        return new DensityFunctions.Spline.Coordinate(new Adapter(source, -4000.0f, 4000.0f));
    }

    private static F splineF(final CubicSpline<DensityFunctions.Spline.Coordinate> spline) {
        final DensityFunction node = DensityFunctions.spline(spline);
        return (x, y, z) -> (float) node.compute(new DensityFunction.SinglePointContext(x, y, z));
    }

    // ──────── double 载体适配（噪声/树接口仍是 double——内部全 float 求值）────────

    static final class Adapter implements DensityFunction.SimpleFunction {

        private static final MapCodec<DensityFunction> UNIT_CODEC = MapCodec.unit(new java.util.function.Supplier<DensityFunction>() {

            @Override
            public DensityFunction get() {
                throw new IllegalStateException("MCRe float density adapter is code-only (not serializable)");
            }
        });

        private final F source;
        private final float minValue;
        private final float maxValue;

        Adapter(final F source, final float minValue, final float maxValue) {
            this.source = source;
            this.minValue = minValue;
            this.maxValue = maxValue;
        }

        F source() {
            return this.source;
        }

        @Override
        public double compute(final DensityFunction.FunctionContext context) {
            return this.source.compute((int) context.blockX(), (int) context.blockY(), (int) context.blockZ());
        }

        @Override
        public void fillArray(final double[] output, final DensityFunction.ContextProvider provider) {
            provider.fillAllDirectly(output, this);
        }

        @Override
        public double minValue() {
            return this.minValue;
        }

        @Override
        public double maxValue() {
            return this.maxValue;
        }

        @Override
        public DensityFunction mapChildren(final DensityFunction.Visitor visitor) {
            return this;
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return KeyDispatchDataCodec.of(UNIT_CODEC);
        }
    }
}
