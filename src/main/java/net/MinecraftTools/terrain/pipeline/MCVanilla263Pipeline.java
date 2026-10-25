package net.MinecraftTools.terrain.pipeline;

import java.util.concurrent.CompletableFuture;

import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.MinecraftTools.terrain.pipeline.vanilla263.Vanilla263NoiseSystem;

/**
 * 🔧 MCRe 地形管线系统——正式自定义管线（元老）：移植 Inf's Farlands 的 <b>Vanilla263</b> 密度链。
 *
 * <p>内容（彻底重写，不再超平坦）：
 * <ul>
 *   <li>NOISE 阶段接管：以 26.3 语义<b>逐行移植</b>的密度树（{@link Vanilla263NoiseSystem}——噪声叶、
 *       节点、样条、拓扑全部自实现）替换 vanilla router 的 {@code finalDensity}，其余 router 字段
 *       （气候通道/preliminarySurfaceLevel/矿脉门）照抄 26.2——即"26.3 的密度链 + 26.2 的其余管线"；</li>
 *   <li>通过 {@link RandomState#withRouter} 派生自定义 RandomState，走<b>原版</b>填充路径
 *       （{@code fillFromNoiseVanilla}）——NoiseChunk 插值/aquifer/beardifier/heightmap 全套 26.2
 *       基建照跑，只换密度值来源；</li>
 *   <li>BIOMES/SURFACE/CARVERS 不接管（原版照跑——infs 同语义：26.x 的其余管线照常，
 *       仅 createBiomes 走 vanilla 路径委托）；</li>
 *   <li>多线程/分带生成继承：分带任务同样经此管线（每次调用新建 26.3 系统——树带状态缓存禁共享）。</li>
 * </ul>
 *
 * <p>噪声随机源：直接用 {@code randomState.noiseRandomFactory()}——overworld 类 settings 的默认
 * 随机源即 XOROSHIRO（与 26.3 语义的 {@code WorldgenRandom.Algorithm.XOROSHIRO.newInstance(seed)}
 * 同源同流），与"同 seed 的 26.3 原版"逐位一致；legacy_random_source=true 的 settings 下随机流
 * 不同（测试管线可接受）。
 *
 * <p>参数（infs 同款默认）：enableFarlands=true（wrap 原样返回，边境之地在 32 位边界）、
 * topFadeEnabled=false、enableJagged=true、enableSkyGrid=false。
 */
public final class MCVanilla263Pipeline implements TerrainPipeline {

    public static final MCVanilla263Pipeline INSTANCE = new MCVanilla263Pipeline();

    private MCVanilla263Pipeline() {
    }

    @Override
    public String id() {
        return "mcvanilla263";
    }

    @Override
    public String displayName() {
        return "MC Vanilla 26.3 密度链";
    }

    @Override
    public CompletableFuture<ChunkAccess> fillBiomes(
        final NoiseBasedChunkGenerator generator,
        final RandomState randomState,
        final Blender blender,
        final StructureManager structureManager,
        final ChunkAccess chunk
    ) {
        // 不接管 BIOMES——委托原版路径（263 树只换 finalDensity，气候通道照 vanilla 26.2）
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
        // 🔧 每次调用新建 26.3 系统（树带状态缓存，禁止跨 chunk 共享——infs 注释同款纪律）
        // 参数（大佬 10-24 裁定）：enableJagged 强制始终开启；enableSkyGrid 跟随
        // WorldMainSettingScreen 的 forceSkyGrid 开关；enableFarlands 恒 true / topFade 恒 false（infs 同款默认）
        final var cfg = net.minecraft.client.gui.screens.worldselection.WorldMainSettingScreen.FarLandsConfigData.activeConfig;
        final Vanilla263NoiseSystem system = new Vanilla263NoiseSystem(
            /* seed：噪声随机源改由 randomState 根工厂提供（overworld=XOROSHIRO，见类注释），seed 参数无实际作用 */
            0L, /* enableFarlands */ true, /* topFadeEnabled */ false,
            /* enableJagged */ true, /* enableSkyGrid */ cfg != null && cfg.forceSkyGrid);
        // 用 randomState 的根工厂作为 26.3 树的 noises 随机源（与"同 seed 的 26.3 原版"同源同流）
        final NoiseRouter customRouter = system.createRouterWithNoiseFactory(randomState.router(), randomState.noiseRandomFactory());
        final RandomState customRandomState = randomState.withRouter(customRouter);

        // 走原版 NOISE 填充路径（带自定义 RandomState——NoiseChunk/插值/aquifer/heightmap 全套 26.2 基建照跑）
        return generator.fillFromNoiseVanilla(blender, customRandomState, structureManager, chunk, true);
    }
}
