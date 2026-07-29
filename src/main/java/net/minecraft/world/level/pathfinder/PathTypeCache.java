package net.minecraft.world.level.pathfinder;
import it.unimi.dsi.fastutil.longs.LongSet;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import org.jspecify.annotations.Nullable;

public class PathTypeCache {
    // ===== 使用 BlockPos 作为键的 Map =====
    private final Object2ObjectMap<BlockPos, PathType> cache = new Object2ObjectOpenHashMap<>();

    public PathType getOrCompute(final BlockGetter level, final BlockPos pos) {
        return this.cache.computeIfAbsent(pos, p -> WalkNodeEvaluator.getPathTypeFromState(level, p));
    }

    public void invalidate(final BlockPos pos) {
        this.cache.remove(pos);
    }
}