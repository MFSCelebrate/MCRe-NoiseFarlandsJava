package net.minecraft.world.level.levelgen.placement;

import com.mojang.serialization.MapCodec;
import java.util.stream.Stream;
import java.util.stream.Stream.Builder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

@Deprecated
public class CountOnEveryLayerPlacement extends PlacementModifier {
    public static final MapCodec<CountOnEveryLayerPlacement> CODEC = IntProviders.codec(0, 256)
        .fieldOf("count")
        .xmap(CountOnEveryLayerPlacement::new, c -> c.count);
    private final IntProvider count;

    private CountOnEveryLayerPlacement(final IntProvider count) {
        this.count = count;
    }

    public static CountOnEveryLayerPlacement of(final IntProvider count) {
        return new CountOnEveryLayerPlacement(count);
    }

    public static CountOnEveryLayerPlacement of(final int count) {
        return of(ConstantInt.of(count));
    }

    @Override
    public Stream<BlockPos> getPositions(final PlacementContext context, final RandomSource random, final BlockPos origin) {
        Builder<BlockPos> positions = Stream.builder();
        int layer = 0;

        boolean foundAny;
        do {
            foundAny = false;

            for (int i = 0; i < this.count.sample(random); i++) {
                long x = random.nextInt(16) + origin.getX();
                long z = random.nextInt(16) + origin.getZ();
                long startY = context.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                long y = findOnGroundYPosition(context, x, startY, z, layer);
                if (y != Long.MAX_VALUE) {
                    positions.add(new BlockPos(x, y, z));
                    foundAny = true;
                }
            }

            layer++;
        } while (foundAny);

        return positions.build();
    }

    @Override
    public PlacementModifierType<?> type() {
        return PlacementModifierType.COUNT_ON_EVERY_LAYER;
    }

    // MCRe NoiseFarlands: 放置坐标世界域 Long 化，哨兵 Long.MAX_VALUE
    private static long findOnGroundYPosition(final PlacementContext context, final long xStart, final long yStart, final long zStart, final int layerToPlaceOn) {
        BlockPos.MutableBlockPos currentPos = new BlockPos.MutableBlockPos(xStart, yStart, zStart);
        int currentLayer = 0;
        BlockState currentBlock = context.getBlockState(currentPos);

        for (long y = yStart; y >= context.getMinY() + 1; y--) {
            currentPos.setY(y - 1);
            BlockState belowBlock = context.getBlockState(currentPos);
            if (!isEmpty(belowBlock) && isEmpty(currentBlock) && !belowBlock.is(Blocks.BEDROCK)) {
                if (currentLayer == layerToPlaceOn) {
                    return currentPos.getY() + 1;
                }

                currentLayer++;
            }

            currentBlock = belowBlock;
        }

        return Integer.MAX_VALUE;
    }

    private static boolean isEmpty(final BlockState blockState) {
        return blockState.isAir() || blockState.is(Blocks.WATER) || blockState.is(Blocks.LAVA);
    }
}