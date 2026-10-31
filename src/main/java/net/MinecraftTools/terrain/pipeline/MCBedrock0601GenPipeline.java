package net.MinecraftTools.terrain.pipeline;

import java.util.concurrent.CompletableFuture;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.MinecraftTools.terrain.pipeline.mcbedrock0601.Bedrock0601NoiseSystem;
import net.MinecraftTools.terrain.pipeline.mcbedrock0601.TerrainDensity0601;

/**
 * 🔧 MCRe 地形管线系统——正式管线：<b>MCBedrock0601GenPipeline</b>（携带版 0.6.1 密度链）。
 *
 * <p>基线：Materials/minecraftcpp-master（Mojang 远程构建 PE 0.6.1 真实源码）——
 * Beta 1.7.3 血统的地形公式（684.412 缩放/选择器/深度调制）+ <b>float 全程</b> + <b>MT19937 随机流</b>
 * （PE 地形与 Java Beta 不同的两大根源）。
 *
 * <p>批2 实现：fillNoise 接管——0.6.1 的 getHeights 公式单点化为
 * {@link TerrainDensity0601}（噪声域 n = 块坐标/4（x/z）、块坐标/8（y），与 getRegion 采样点逐位等价），
 * 用 26.2 的 {@code interpolated} 包装驱动（0.6.1 采样网格 4×8×4 与 26.2 cell 网格完全对齐——
 * NoiseInterpolator 角点采样正好落在 0.6.1 采样点上，白嫖插值/多线程/分带基建）。
 * 水走 26.2 的 sea_level=63（大佬裁定）；洞穴不移植（0.6.1 实况：未接线）；BIOMES 批3 接管，
 * SURFACE/CARVERS 批4/5。
 *
 * <p>seed 派生：26.2 的 RandomState 不持有 world seed 数值——用
 * {@code vanilla.noiseRandomFactory().fromHashOf("mcre_bedrock0601").nextLong()} 派生
 * （Xoroshiro 的 fromHashOf 是确定性纯派生：同 world 同值、跨 world 不同）。
 */
public final class MCBedrock0601GenPipeline implements TerrainPipeline {

    public static final MCBedrock0601GenPipeline INSTANCE = new MCBedrock0601GenPipeline();

    private MCBedrock0601GenPipeline() {
    }

    @Override
    public String id() {
        return "Bedrock-0.6.1";
    }

    @Override
    public String displayName() {
        return "MC Bedrock 0.6.1（携带版）";
    }

    /** per-vanilla 身份缓存（volatile 不可变 holder 原子换对，照 MCBedrockGenPipeline） */
    private record CachedPatched(RandomState vanillaRef, RandomState patched) {
    }

    private volatile CachedPatched cachedPatched;

    @Override
    public RandomState patchRandomState(final NoiseGeneratorSettings settings, final RandomState vanilla) {
        final CachedPatched cached = this.cachedPatched;
        if (cached != null && cached.vanillaRef() == vanilla) {
            return cached.patched();
        }
        // 0.6.1 系统 seed：从 vanilla 根随机工厂确定性派生（每 world 稳定）
        final long seed0601 = vanilla.noiseRandomFactory()
            .fromHashOf(Identifier.withDefaultNamespace("mcre_bedrock0601"))
            .nextLong();
        final Bedrock0601NoiseSystem system = new Bedrock0601NoiseSystem(seed0601);
        final TerrainDensity0601 density = new TerrainDensity0601(system);

        final NoiseRouter v = vanilla.router();
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
            v.preliminarySurfaceLevel(), // PS 照抄（surface 批4 全接管后仅 aquifer 消费，无害）
            DensityFunctions.interpolated(density), // finalDensity = interpolated(0.6.1 密度)
            v.veinToggle(),
            v.veinRidged(),
            v.veinGap());
        final RandomState patched = vanilla.withRouter(custom);
        this.cachedPatched = new CachedPatched(vanilla, patched);
        return patched;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillBiomes(
        final NoiseBasedChunkGenerator generator,
        final RandomState randomState,
        final Blender blender,
        final StructureManager structureManager,
        final ChunkAccess chunk
    ) {
        // 批3 接管（PE 10 群系判定 + 26.2 映射）；当前委托原版（地形先行验证）
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
        final RandomState custom = this.patchRandomState(generator.generatorSettings().value(), randomState);
        return generator.fillFromNoiseVanilla(blender, custom, structureManager, chunk, true);
    }
}
