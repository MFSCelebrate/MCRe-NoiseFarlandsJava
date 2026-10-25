package net.MinecraftTools.terrain.pipeline;

import java.util.concurrent.CompletableFuture;

import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
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
 * <p>密度函数接线（分批实施）：vanilla 密度树由 JSON（noise_router）驱动，噪声实例在
 * RandomState 的 NoiseWiringHelper.visitNoise 绑定——本管线将自建密度函数节点家族
 * （照 infs Vanilla263 模式：节点直接持副本噪声实例），树组装照 vanilla overworld 链。
 * <b>当前批次</b>：噪声副本层完成；fillNoise 暂走原版填充路径（密度树接线后切换）。
 */
public final class MCBedrockGenPipeline implements TerrainPipeline {

    public static final MCBedrockGenPipeline INSTANCE = new MCBedrockGenPipeline();

    private MCBedrockGenPipeline() {
    }

    @Override
    public String id() {
        return "mcbedrockgen";
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
    @Override
    public RandomState patchRandomState(final NoiseGeneratorSettings settings, final RandomState vanilla) {
        final var rootFactory = vanilla.noiseRandomFactory();
        final var paramsGetter = vanilla.noiseParametersGetter();
        return RandomState.createWithNoiseFactory(
            settings,
            paramsGetter,
            rootFactory,
            key -> net.MinecraftTools.terrain.pipeline.mcbedrockgen.noise.NormalNoise.create(
                rootFactory.fromHashOf(key.identifier()),
                paramsGetter.getOrThrow(key).value()),
            random -> net.MinecraftTools.terrain.pipeline.mcbedrockgen.noise.BlendedNoise.create(random));
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
