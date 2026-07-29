package net.minecraft.gametest.framework;
import it.unimi.dsi.fastutil.longs.LongSet;

public interface GameTestBatchListener {
    void testBatchStarting(final GameTestBatch batch);

    void testBatchFinished(final GameTestBatch batch);
}