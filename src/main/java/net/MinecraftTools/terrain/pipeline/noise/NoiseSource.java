package net.MinecraftTools.terrain.pipeline.noise;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * 🔧 MCRe 地形管线 C 批：噪声底层抽象（MinecraftChunkPyramid 噪声可插拔注入点）。
 *
 * <p>设计参照 Inf's Farlands 的 {@code OctNoiseSource}（采集参数 + seed 重建实例归管线所有），
 * 但<b>偏移缩放必须走我们自己的 WorldReposition 重定向体系</b>（采样坐标过
 * {@code WorldReposition.reposition}）——不引入 OctScale 式的独立缩放机制（那是 infs 自己的
 * 偏移缩放手段，与本管线系统语义不同）。
 *
 * <p>两个内建实现：
 * <ul>
 *   <li>{@link VanillaNoiseSource}——委托 {@code RandomState.noiseInstances()} 共享缓存（逐位等价基准）；</li>
 *   <li>{@link PipelineNoiseSource}——按 key 用 seed 派生随机源自建实例（与共享缓存隔离，
 *       参数同 {@code NoiseParameters} Holder），缓存归管线所有、可整体清除。</li>
 * </ul>
 */
public interface NoiseSource {

    /** 取噪声实例（实现保证同 key 同实例；可能为 null——key 未注册时） */
    @Nullable
    NormalNoise getOrCreate(final ResourceKey<NormalNoise.NoiseParameters> key);

    /** 该 key 是否可解析（参数已注册/已采集） */
    boolean has(final ResourceKey<NormalNoise.NoiseParameters> key);

    /** 清空本源持有的实例（共享缓存实现为 no-op——不归它所有） */
    void clear();
}
