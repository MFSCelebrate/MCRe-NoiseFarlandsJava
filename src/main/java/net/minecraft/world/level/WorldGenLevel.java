package net.minecraft.world.level;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

public interface WorldGenLevel extends ServerLevelAccessor {
    long getSeed();

    default boolean ensureCanWrite(final BlockPos pos) {
        return true;
    }

    default void setCurrentlyGenerating(final @Nullable Supplier<String> currentlyGenerating) {
    }
}