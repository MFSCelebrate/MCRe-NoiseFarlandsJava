package net.MinecraftTools.terrain.pipeline;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.material.Fluids;

/**
 * 🔧 MCRe 地形管线系统——Test 测试管线：超平坦地形（照原版 FlatLevelSource 语义）。
 *
 * <p>层结构 = 经典超平坦：bedrock(1) + dirt(2) + grass_block(1)，群系恒 plains。
 * <ul>
 *   <li>fillBiomes：全 plains（{@code fillBiomesFromNoise}，sampler 恒零——plains 不用气候值）；</li>
 *   <li>fillNoise：no-op（层在 buildSurface 填，与 FlatLevelSource 相同阶段语义——
 *       SURFACE step 在 NOISE 之后，高度图由填层时 update）；</li>
 *   <li>buildSurface：逐层 setBlockState + OCEAN_FLOOR_WG/WORLD_SURFACE_WG 高度图更新
 *       （照抄 FlatLevelSource.fillFromNoise）；</li>
 *   <li>carvers：no-op（超平坦无洞穴）。</li>
 * </ul>
 * <p>窗口化适配：层只写 {@code chunk.getMinY()} 起的 4 格（第一带内），
 * 其余段天然空气；滑窗/分带重新触发 buildSurface 时重复填充幂等无害。
 */
public final class FlatTestPipeline implements TerrainPipeline {

    public static final FlatTestPipeline INSTANCE = new FlatTestPipeline();

    /** 经典超平坦层（自下而上）：bedrock、dirt×2、grass_block */
    private static final List<BlockState> LAYERS = List.of(
        Blocks.BEDROCK.defaultBlockState(),
        Blocks.DIRT.defaultBlockState(),
        Blocks.DIRT.defaultBlockState(),
        Blocks.GRASS_BLOCK.defaultBlockState()
    );

    private FlatTestPipeline() {
    }

    @Override
    public String id() {
        return "test";
    }

    @Override
    public String displayName() {
        return "Test 测试（超平坦）";
    }

    @Override
    public boolean takesOverSurface() {
        return true;
    }

    @Override
    public boolean takesOverCarvers() {
        return true;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillBiomes(
        final NoiseBasedChunkGenerator generator,
        final RandomState randomState,
        final Blender blender,
        final StructureManager structureManager,
        final ChunkAccess chunk
    ) {
        // sampler 恒零（Climate.empty() 现成）：plains 不消费气候值，避免任何 NPE 风险
        // resolver 用 containerFactory.defaultBiome()——其内部就是 biomes.getOrThrow(Biomes.PLAINS)（plains），
        // 零 registry 依赖，与"建空段读群系"同源
        final Climate.Sampler flatSampler = Climate.empty();
        final var factory = chunk.getContainerFactory();
        chunk.fillBiomesFromNoise(
            (quartX, quartY, quartZ, sampler) -> factory.defaultBiome(),
            flatSampler
        );
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public CompletableFuture<ChunkAccess> fillNoise(
        final NoiseBasedChunkGenerator generator,
        final Blender blender,
        final RandomState randomState,
        final StructureManager structureManager,
        final ChunkAccess chunk
    ) {
        // 超平坦无噪声阶段（层在 buildSurface 填——FlatLevelSource 同款语义）
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void buildSurface(
        final NoiseBasedChunkGenerator generator,
        final WorldGenRegion region,
        final StructureManager structureManager,
        final RandomState randomState,
        final ChunkAccess chunk
    ) {
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        final Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        final Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);

        for (int layerIndex = 0; layerIndex < Math.min(chunk.getHeight(), LAYERS.size()); layerIndex++) {
            final BlockState state = LAYERS.get(layerIndex);
            final int y = chunk.getMinY() + layerIndex;

            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    chunk.setBlockState(pos.set(x, y, z), state);
                    oceanFloor.update(x, y, z, state);
                    worldSurface.update(x, y, z, state);
                }
            }
        }

        // 流体引用预热（防未初始化告警），无实际写入
        Fluids.EMPTY.defaultFluidState();
    }

    /** 便于日志/调试 */
    @Override
    public String describeAt(final ChunkPos pos) {
        return this.displayName() + " @ " + pos;
    }
}
