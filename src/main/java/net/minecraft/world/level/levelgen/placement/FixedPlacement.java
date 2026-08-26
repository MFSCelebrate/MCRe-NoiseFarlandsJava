package net.minecraft.world.level.levelgen.placement;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;

public class FixedPlacement extends PlacementModifier {
    public static final MapCodec<FixedPlacement> CODEC = RecordCodecBuilder.mapCodec(
        i -> i.group(BlockPos.CODEC.listOf().fieldOf("positions").forGetter(c -> c.positions)).apply(i, FixedPlacement::new)
    );
    private final List<BlockPos> positions;

    public static FixedPlacement of(final BlockPos... pos) {
        return new FixedPlacement(List.of(pos));
    }

    private FixedPlacement(final List<BlockPos> positions) {
        this.positions = positions;
    }

    @Override
    public Stream<BlockPos> getPositions(final PlacementContext context, final RandomSource random, final BlockPos origin) {
        long chunkX = SectionPos.blockToSectionCoord(origin.getX());
        long chunkZ = SectionPos.blockToSectionCoord(origin.getZ());
        boolean hasPositions = false;

        for (BlockPos position : this.positions) {
            if (isSameChunk(chunkX, chunkZ, position)) {
                hasPositions = true;
                break;
            }
        }

        return !hasPositions ? Stream.empty() : this.positions.stream().filter(pos -> isSameChunk(chunkX, chunkZ, pos));
    }

    // MCRe NoiseFarlands: chunk 坐标 Long 化
    private static boolean isSameChunk(final long chunkX, final long chunkZ, final BlockPos position) {
        return chunkX == SectionPos.blockToSectionCoord(position.getX()) && chunkZ == SectionPos.blockToSectionCoord(position.getZ());
    }

    @Override
    public PlacementModifierType<?> type() {
        return PlacementModifierType.FIXED_PLACEMENT;
    }
}