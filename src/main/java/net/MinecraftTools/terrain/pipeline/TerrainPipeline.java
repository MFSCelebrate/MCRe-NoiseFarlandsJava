package net.MinecraftTools.terrain.pipeline;

import java.util.concurrent.CompletableFuture;

import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.MinecraftTools.terrain.pipeline.noise.NoiseSource;
import net.MinecraftTools.terrain.pipeline.noise.VanillaNoiseSource;

/**
 * 🔧 MCRe 地形管线系统（MinecraftChunkPyramid）——地形管线抽象。
 *
 * <p>设计原则（参考 Inf's Farlands 的 Oct 体系 + vanilla DensityFunction 接口多态）：
 * <ul>
 *   <li><b>阶段级接口</b>：管线按 ChunkStatus 阶段接管（BIOMES/NOISE/SURFACE/CARVERS），
 *       未接管的阶段自动回退原版（渐进接管，不逼全家桶）；</li>
 *   <li><b>多线程继承</b>：方法签名 CompletableFuture 风格，天然跑在 GlobalWorldGenExecutors；
 *       写活 chunk 的实现必须走 {@code begin/endGenerationWrite}（tick 队列 NPE 防线）；</li>
 *   <li><b>分带适配</b>：FarLandsBandGenerator 提交带任务时同样经过本接口（fillNoise 需尊重
 *       chunk 的 {@code generationBand*} 域）；</li>
 *   <li><b>绕过数据包</b>：自定义管线的密度函数/噪声树走代码组装（MapCodec.unit 思路），
 *       不读 worldgen JSON。</li>
 * </ul>
 *
 * <p>分派收口：{@code NoiseBasedChunkGenerator} 的 createBiomes / fillFromNoise / buildSurface /
 * applyCarvers 四方法开头——{@code TerrainPipelineRegistry.active()} 返回 null 即走原版路径。
 */
public interface TerrainPipeline {

    /** 管线 ID（配置持久化用；"vanilla" 保留给原版管线，注册表不存） */
    String id();

    /** 管线显示名（调试屏幕/日志用） */
    String displayName();

    /** 是否接管 SURFACE 阶段（true = 原版 buildSurface 跳过，防 NPE/覆盖） */
    default boolean takesOverSurface() {
        return false;
    }

    /** 是否接管 CARVERS 阶段 */
    default boolean takesOverCarvers() {
        return false;
    }

    /** BIOMES 阶段：填充群系（对应 NoiseBasedChunkGenerator.createBiomes） */
    CompletableFuture<ChunkAccess> fillBiomes(
        final NoiseBasedChunkGenerator generator,
        final RandomState randomState,
        final Blender blender,
        final StructureManager structureManager,
        final ChunkAccess chunk
    );

    /** NOISE 阶段：地形本体填充（对应 fillFromNoise；需尊重 chunk 的生成带/窗口域） */
    CompletableFuture<ChunkAccess> fillNoise(
        final NoiseBasedChunkGenerator generator,
        final Blender blender,
        final RandomState randomState,
        final StructureManager structureManager,
        final ChunkAccess chunk
    );

    /** SURFACE 阶段（仅 {@link #takesOverSurface()} = true 时被调用） */
    default void buildSurface(
        final NoiseBasedChunkGenerator generator,
        final WorldGenRegion region,
        final StructureManager structureManager,
        final RandomState randomState,
        final ChunkAccess chunk
    ) {
    }

    /** CARVERS 阶段（仅 {@link #takesOverCarvers()} = true 时被调用） */
    default void applyCarvers(
        final NoiseBasedChunkGenerator generator,
        final WorldGenRegion region,
        final long seed,
        final RandomState randomState,
        final BiomeManager biomeManager,
        final StructureManager structureManager,
        final ChunkAccess chunk
    ) {
    }

    /** 本管线生成的 chunk 是否可安全参与 FarLandsBandGenerator 的带任务（默认 true） */
    default boolean supportsBandGeneration() {
        return true;
    }

    /**
     * 🔧 MCRe 管线接管修复：把 vanilla RandomState 变换成管线语义的 RandomState（噪声实例=副本/
     * 密度树=自定义）。null = 不变换。收口点在 {@code NoiseBasedChunkGenerator.createNoiseChunk}——
     * 否则 BIOMES 阶段（先于 NOISE）会用主线 RandomState 先建 NoiseChunk 缓存，NOISE 阶段拿到缓存
     * 导致自定义噪声/密度函数全部失效。
     */
    @Nullable
    default RandomState patchRandomState(final NoiseGeneratorSettings settings, final RandomState vanilla) {
        return null;
    }

    /** 🔧 MCRe C 批：为本管线创建噪声源（默认原版共享缓存；自定义管线可换 PipelineNoiseSource 自建隔离实例） */
    default NoiseSource createNoiseSource(final RandomState randomState) {
        return VanillaNoiseSource.of(randomState);
    }

    /** 便于日志/调试 */
    default String describeAt(final ChunkPos pos) {
        return this.displayName() + " @ " + pos;
    }
}
