package net.minecraft.world.level.biome;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import org.jspecify.annotations.Nullable;

public abstract class BiomeSource implements BiomeResolver {
    public static final Codec<BiomeSource> CODEC = BuiltInRegistries.BIOME_SOURCE.byNameCodec().dispatchStable(BiomeSource::codec, Function.identity());
    private final Supplier<Set<Holder<Biome>>> possibleBiomes = Suppliers.memoize(
        () -> this.collectPossibleBiomes().distinct().collect(ImmutableSet.toImmutableSet())
    );

    protected BiomeSource() {
    }

    protected abstract MapCodec<? extends BiomeSource> codec();

    protected abstract Stream<Holder<Biome>> collectPossibleBiomes();

    public Set<Holder<Biome>> possibleBiomes() {
        return this.possibleBiomes.get();
    }

    public Set<Holder<Biome>> getBiomesWithin(final long x, final long y, final long z, final int r, final Climate.Sampler sampler) {
        long x0 = QuartPos.fromBlock(x - r);
        long y0 = QuartPos.fromBlock(y - r);
        long z0 = QuartPos.fromBlock(z - r);
        long x1 = QuartPos.fromBlock(x + r);
        long y1 = QuartPos.fromBlock(y + r);
        long z1 = QuartPos.fromBlock(z + r);
        // MCRe NoiseFarlands: w/d/h 为采样网格尺寸（r 半径量级），int 域边界
        int w = (int) (x1 - x0 + 1);
        int d = (int) (y1 - y0 + 1);
        int h = (int) (z1 - z0 + 1);
        Set<Holder<Biome>> biomeSet = Sets.newHashSet();

        for (int row = 0; row < h; row++) {
            for (int column = 0; column < w; column++) {
                for (int depth = 0; depth < d; depth++) {
                    long noiseX = x0 + column;
                    long noiseY = y0 + depth;
                    long noiseZ = z0 + row;
                    biomeSet.add(this.getNoiseBiome(noiseX, noiseY, noiseZ, sampler));
                }
            }
        }

        return biomeSet;
    }

    // MCRe NoiseFarlands: 世界坐标 Long 化
    public @Nullable Pair<BlockPos, Holder<Biome>> findBiomeHorizontal(
        final long x,
        final long y,
        final long z,
        final int searchRadius,
        final Predicate<Holder<Biome>> allowed,
        final RandomSource random,
        final Climate.Sampler sampler
    ) {
        return this.findBiomeHorizontal(x, y, z, searchRadius, 1, allowed, random, false, sampler);
    }

    public @Nullable Pair<BlockPos, Holder<Biome>> findClosestBiome3d(
        final BlockPos origin,
        final int searchRadius,
        final int sampleResolutionHorizontal,
        final int sampleResolutionVertical,
        final Predicate<Holder<Biome>> allowed,
        final Climate.Sampler sampler,
        final LevelReader level
    ) {
        Set<Holder<Biome>> candidateBiomes = this.possibleBiomes().stream().filter(allowed).collect(Collectors.toUnmodifiableSet());
        if (candidateBiomes.isEmpty()) {
            return null;
        }

        int sampleRadius = Math.floorDiv(searchRadius, sampleResolutionHorizontal);
        // MCRe NoiseFarlands: Y 为高度配置域（int），入口边界强转
        int[] sampleYs = Mth.outFromOrigin((int) origin.getY(), level.getMinY() + 1, level.getMaxY() + 1, sampleResolutionVertical).toArray();

        for (BlockPos.MutableBlockPos sampleColumn : BlockPos.spiralAround(BlockPos.ZERO, sampleRadius, Direction.EAST, Direction.SOUTH)) {
            long blockX = origin.getX() + (long) sampleColumn.getX() * sampleResolutionHorizontal;
            long blockZ = origin.getZ() + (long) sampleColumn.getZ() * sampleResolutionHorizontal;
            long noiseX = QuartPos.fromBlock(blockX);
            long noiseZ = QuartPos.fromBlock(blockZ);

            for (int blockY : sampleYs) {
                long noiseY = QuartPos.fromBlock(blockY);
                Holder<Biome> biome = this.getNoiseBiome(noiseX, noiseY, noiseZ, sampler);
                if (candidateBiomes.contains(biome)) {
                    return Pair.of(new BlockPos(blockX, blockY, blockZ), biome);
                }
            }
        }

        return null;
    }

    public @Nullable Pair<BlockPos, Holder<Biome>> findBiomeHorizontal(
        final long originX,
        final long originY,
        final long originZ,
        final int searchRadius,
        final int skipSteps,
        final Predicate<Holder<Biome>> allowed,
        final RandomSource random,
        final boolean findClosest,
        final Climate.Sampler sampler
    ) {
        long noiseCenterX = QuartPos.fromBlock(originX);
        long noiseCenterZ = QuartPos.fromBlock(originZ);
        int noiseRadius = (int) QuartPos.fromBlock(searchRadius);
        long noiseY = QuartPos.fromBlock(originY);
        Pair<BlockPos, Holder<Biome>> result = null;
        int found = 0;
        int startRadius = findClosest ? 0 : noiseRadius;
        int currentRadius = startRadius;

        while (currentRadius <= noiseRadius) {
            for (int z = !SharedConstants.DEBUG_ONLY_GENERATE_HALF_THE_WORLD && !SharedConstants.debugGenerateSquareTerrainWithoutNoise ? -currentRadius : 0;
                z <= currentRadius;
                z += skipSteps
            ) {
                boolean zEdge = Math.abs(z) == currentRadius;

                for (int x = -currentRadius; x <= currentRadius; x += skipSteps) {
                    if (findClosest) {
                        boolean xEdge = Math.abs(x) == currentRadius;
                        if (!xEdge && !zEdge) {
                            continue;
                        }
                    }

                    long noiseX = noiseCenterX + x;
                    long noiseZ = noiseCenterZ + z;
                    Holder<Biome> biome = this.getNoiseBiome(noiseX, noiseY, noiseZ, sampler);
                    if (allowed.test(biome)) {
                        if (result == null || random.nextInt(found + 1) == 0) {
                            BlockPos resultPos = new BlockPos(QuartPos.toBlock(noiseX), originY, QuartPos.toBlock(noiseZ));
                            if (findClosest) {
                                return Pair.of(resultPos, biome);
                            }

                            result = Pair.of(resultPos, biome);
                        }

                        found++;
                    }
                }
            }

            currentRadius += skipSteps;
        }

        return result;
    }

    @Override
    public abstract Holder<Biome> getNoiseBiome(final long quartX, final long quartY, final long quartZ, final Climate.Sampler sampler);

    public void addDebugInfo(final List<String> result, final BlockPos feetPos, final Climate.Sampler sampler) {
    }
}