package net.MinecraftTools.terrain.pipeline;

import java.util.concurrent.CompletableFuture;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.dimension.DimensionType;
import net.MinecraftTools.terrain.pipeline.mcbedrock0601.Bedrock0601NoiseSystem;
import net.MinecraftTools.terrain.pipeline.mcbedrock0601.BiomeSource0601;
import net.MinecraftTools.terrain.pipeline.mcbedrock0601.PEBiome;
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
    private record CachedPatched(RandomState vanillaRef, RandomState patched, BiomeSource0601 biomeSource0601,
                                 NoiseGeneratorSettings settings0601, Bedrock0601NoiseSystem system) {
    }

    private volatile CachedPatched cachedPatched;

    /** 🔧 0.6.1 流体语义（大佬裁定：禁用现代含水层，转用 0.6.1 的硬编码水） */
    private static final Aquifer.FluidPicker FLUID_PICKER_0601 = createFluidPicker0601();

    /**
     * 🔧 0.6.1 流体语义（大佬裁定：禁用现代含水层，转用 0.6.1 的硬编码水）：y<0/y≥128 域外空、
     * y<64 水（0.6.1 海——prepareHeights 的 waterHeight=64）、y≥64 空。无岩浆层
     * （0.6.1 的岩浆是泉水 feature，批5）。结构照 26.2 原版 picker（FluidStatus(fluidLevel, fluidType)）。
     */
    private static Aquifer.FluidPicker createFluidPicker0601() {
        final Aquifer.FluidStatus waterStatus = new Aquifer.FluidStatus(63, Blocks.WATER.defaultBlockState());
        final Aquifer.FluidStatus emptyStatus = new Aquifer.FluidStatus(DimensionType.MIN_Y, Blocks.AIR.defaultBlockState());
        return (x, y, z) -> {
            if (y < 0 || y >= 128) {
                return emptyStatus;
            }
            return y < 64 ? waterStatus : emptyStatus;
        };
    }

    /**
     * 🔧 0.6.1 settings：record 全字段复制 + aquifersEnabled=false（禁现代含水层——大佬裁定）
     * + 0.6.1 router。surfaceRule 照抄（takesOverSurface=true 后不消费）。
     */
    private static NoiseGeneratorSettings settings0601(final NoiseGeneratorSettings v, final NoiseRouter customRouter) {
        return new NoiseGeneratorSettings(
            v.noiseSettings(),
            v.defaultBlock(),
            v.defaultFluid(),
            customRouter,
            v.surfaceRule(),
            v.spawnTarget(),
            v.seaLevel(),
            v.disableMobGeneration(),
            false, // aquifersEnabled=false：Aquifer.createDisabled（大佬裁定禁用现代含水层）
            v.oreVeinsEnabled(),
            v.useLegacyRandomSource());
    }

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
        final BiomeSource0601 biomeSource0601 = new BiomeSource0601(seed0601);
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
        this.cachedPatched = new CachedPatched(vanilla, patched, biomeSource0601, settings0601(settings, custom), system);
        return patched;
    }

    @Override
    public boolean takesOverSurface() {
        // 🔧 批2.5：surface 接管——0.6.1 有效域（0~127）之外的残留（26.2 流体体系在 y<0 填水/岩浆、
        // y≥128 的石墙、含水层屏障石头→深板岩）由 buildSurface 清空；PE 表层逻辑批4 补齐
        return true;
    }

    /**
     * 🔧 批2.5：0.6.1 域外清空。密度负值在 26.2 的 Aquifer.computeSubstance 里不等于空气——
     * density≤0 走 globalFluidPicker（y<63 水、y<-54 岩浆），含水层屏障石头经 deepslate surface rule
     * 变深板岩——这些都是 26.2 流体体系在 0.6.1 原版不存在区域（y<0、y≥128）的残留，直接清空。
     * PE 表层（沙/砂砾/砂岩/基岩）批4 接入本方法。
     */
    @Override
    public void buildSurface(
        final NoiseBasedChunkGenerator generator,
        final WorldGenRegion region,
        final StructureManager structureManager,
        final RandomState randomState,
        final ChunkAccess chunk
    ) {
        final int baseX = (int) chunk.getPos().getBlockX(0L);
        final int baseZ = (int) chunk.getPos().getBlockZ(0L);
        final BlockState air = Blocks.AIR.defaultBlockState();
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        // ===== 0.6.1 域外清空（批2.5）=====
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                // y<0（-64~-1）：26.2 流体/含水层残留
                for (int y = chunk.getMinY(); y < 0; y++) {
                    pos.set(baseX + lx, y, baseZ + lz);
                    if (chunk.getBlockState(pos) != air) {
                        chunk.setBlockState(pos, air, Block.UPDATE_ALL);
                    }
                }
                // y≥128（128~319）：slide 外推石墙
                for (int y = 128; y < chunk.getMaxY(); y++) {
                    pos.set(baseX + lx, y, baseZ + lz);
                    if (chunk.getBlockState(pos) != air) {
                        chunk.setBlockState(pos, air, Block.UPDATE_ALL);
                    }
                }
            }
        }

        // ===== PE buildSurfaces 逐字（RandomLevelSource.cpp:138-218）=====
        final CachedPatched cached = this.cachedPatched;
        if (cached == null) {
            return; // 防御（正常流程 fillNoise 先行必有缓存）
        }
        final Bedrock0601NoiseSystem system = cached.system();
        final int chunkX = Math.floorDiv(baseX, 16);
        final int chunkZ = Math.floorDiv(baseZ, 16);
        // PE getChunk 的 chunk 种子（源码实锤：(xOffs*341872712 + zOffs*132899541)）
        system.random().setSeed((long) (chunkX * 341872712L + chunkZ * 132899541L));

        final int waterHeight = 64; // PE：Level::DEPTH - 64 = 128 - 64
        final float s = 1 / 32.0f;

        // 三表层 buffer（PE 参数序怪味照抄：y 参数传块 z、buffer 索引读写转置）
        final float[] sandBuffer = new float[16 * 16];
        final float[] gravelBuffer = new float[16 * 16];
        final float[] depthBuffer = new float[16 * 16];
        system.perlinNoise2().getRegion(sandBuffer, baseX, baseZ, 0, 16, 16, 1, s, s, 1);
        system.perlinNoise2().getRegion(gravelBuffer, baseX, 109.01340f, baseZ, 16, 1, 16, s, 1, s);
        system.perlinNoise3().getRegion(depthBuffer, baseX, baseZ, 0, 16, 16, 1, s * 2, s * 2, s * 2);

        final BiomeSource0601 biomeSource0601 = this.biomeSource0601(randomState);
        final BlockState stone = Blocks.STONE.defaultBlockState();
        final BlockState gravel = Blocks.GRAVEL.defaultBlockState();
        final BlockState sand = Blocks.SAND.defaultBlockState();
        final BlockState sandstone = Blocks.SANDSTONE.defaultBlockState();
        final BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
        final BlockState water = Blocks.WATER.defaultBlockState();

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                final PEBiome pe = PEBiome.judge(
                    biomeSource0601.temperatureAt(baseX + lx, baseZ + lz),
                    biomeSource0601.downfallAt(baseX + lx, baseZ + lz));
                final boolean sandHere = sandBuffer[lx + lz * 16] + system.random().nextFloat() * 0.2f > 0;
                final boolean gravelHere = gravelBuffer[lx + lz * 16] + system.random().nextFloat() * 0.2f > 3;
                final int runDepth = (int) (depthBuffer[lx + lz * 16] / 3 + 3 + system.random().nextFloat() * 0.25f);

                // PE 的表层材质（Biome 构造默认 grass/dirt；desert/iceDesert = sand/sand；temp=1 为源码 @todo 照抄）
                BlockState top = Blocks.GRASS_BLOCK.defaultBlockState();
                BlockState material = Blocks.DIRT.defaultBlockState();
                if (pe == PEBiome.DESERT) {
                    top = sand;
                    material = sand;
                }

                int run = -1;
                for (int y = 127; y >= 0; y--) {
                    pos.set(baseX + lx, y, baseZ + lz);
                    if (y <= 0 + system.random().nextInt(5)) {
                        chunk.setBlockState(pos, bedrock, Block.UPDATE_ALL);
                    } else {
                        final BlockState old = chunk.getBlockState(pos);
                        if (old.isAir()) {
                            run = -1;
                        } else if (old.is(Blocks.STONE)) {
                            if (run == -1) {
                                if (runDepth <= 0) {
                                    top = air;
                                    material = stone;
                                } else if (y >= waterHeight - 4 && y <= waterHeight + 1) {
                                    top = Blocks.GRASS_BLOCK.defaultBlockState();
                                    material = Blocks.DIRT.defaultBlockState();
                                    if (gravelHere) {
                                        top = air;
                                        material = gravel;
                                    }
                                    if (sandHere) {
                                        top = sand;
                                        material = sand;
                                    }
                                }
                                if (y < waterHeight && top == air) {
                                    // PE：temp<0.15 → ice（temp=1 todo 照抄，不触发）
                                    top = water;
                                }
                                run = runDepth;
                                if (y >= waterHeight - 1) {
                                    chunk.setBlockState(pos, top, Block.UPDATE_ALL);
                                } else {
                                    chunk.setBlockState(pos, material, Block.UPDATE_ALL);
                                }
                            } else if (run > 0) {
                                run--;
                                chunk.setBlockState(pos, material, Block.UPDATE_ALL);
                                if (run == 0 && material == sand) {
                                    run = system.random().nextInt(4);
                                    material = sandstone;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** 0.6.1 群系源获取（与 patchRandomState 共享 per-vanilla 缓存；竞态未命中时独立建——seed 派生同款确定性） */
    private BiomeSource0601 biomeSource0601(final RandomState vanilla) {
        final CachedPatched cached = this.cachedPatched;
        if (cached != null && cached.vanillaRef() == vanilla) {
            return cached.biomeSource0601();
        }
        final long seed0601 = vanilla.noiseRandomFactory()
            .fromHashOf(Identifier.withDefaultNamespace("mcre_bedrock0601"))
            .nextLong();
        return new BiomeSource0601(seed0601);
    }

    /**
     * 🔧 批3：群系接管——PE 0.6.1 的温度/湿度判定树（{@link PEBiome#judge} 逐字）+ 26.2 映射
     * （从 vanilla biomeSource 的 possibleBiomes() 按 key 匹配 Holder——BuiltInRegistries 无 BIOME）。
     * PE 群系是 2D 列语义（任意 quartY 同值）；quart 中心块 (+2,+2) 作判定点。
     * Climate.empty()（PE 不用 6 通道气候）；NoiseChunk 不在此预建（fillNoise 的 doFill 自行收口）。
     */
    @Override
    public CompletableFuture<ChunkAccess> fillBiomes(
        final NoiseBasedChunkGenerator generator,
        final RandomState randomState,
        final Blender blender,
        final StructureManager structureManager,
        final ChunkAccess chunk
    ) {
        final BiomeSource0601 biomeSource0601 = this.biomeSource0601(randomState);

        // Holder 查找（一次）：possibleBiomes 按 vanillaPath 匹配 10 个 PE 群系
        final java.util.Map<PEBiome, net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>> holders =
            new java.util.EnumMap<>(PEBiome.class);
        net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> fallback = null;
        for (final net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> holder : generator.biomeSource().possibleBiomes()) {
            if (fallback == null) {
                fallback = holder;
            }
            holder.unwrapKey().ifPresent(key -> {
                final String path = key.identifier().getPath();
                for (final PEBiome pe : PEBiome.values()) {
                    if (pe.vanillaPath.equals(path)) {
                        holders.putIfAbsent(pe, holder);
                    }
                }
            });
        }
        if (holders.isEmpty()) {
            // possibleBiomes 无匹配（异常 settings）——vanilla 兜底
            return generator.createBiomesVanilla(randomState, blender, structureManager, chunk);
        }
        final net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> fallbackHolder = fallback;

        final net.minecraft.world.level.biome.BiomeResolver resolver =
            (quartX, quartY, quartZ, sampler) -> {
                final int blockX = quartX * 4 + 2; // quart 中心块作判定点
                final int blockZ = quartZ * 4 + 2;
                final float temperature = biomeSource0601.temperatureAt(blockX, blockZ);
                final float downfall = biomeSource0601.downfallAt(blockX, blockZ);
                final PEBiome pe = PEBiome.judge(temperature, downfall);
                final net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> holder = holders.get(pe);
                return holder != null ? holder : fallbackHolder;
            };
        chunk.fillBiomesFromNoise(resolver, net.minecraft.world.level.biome.Climate.empty());
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
        final RandomState custom = this.patchRandomState(generator.generatorSettings().value(), randomState);
        // 🔧 批4：预建 NoiseChunk（0601 settings：aquifersEnabled=false 禁现代含水层 + 0601 流体语义
        // y<64 水/域外 air）——doFill 的 getOrCreateNoiseChunk 命中本缓存，走 fillFromNoiseVanilla 全套基建
        final CachedPatched cached = this.cachedPatched;
        final NoiseGeneratorSettings chunkSettings = cached != null && cached.vanillaRef() == randomState
            ? cached.settings0601()
            : settings0601(generator.generatorSettings().value(), custom.router());
        chunk.getOrCreateNoiseChunk(c -> NoiseChunk.forChunk(
            c,
            custom,
            Beardifier.forStructuresInChunk(structureManager, c.getPos()),
            chunkSettings,
            FLUID_PICKER_0601,
            blender));
        return generator.fillFromNoiseVanilla(blender, custom, structureManager, chunk, true);
    }
}
