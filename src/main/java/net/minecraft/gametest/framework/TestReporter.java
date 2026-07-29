package net.minecraft.gametest.framework;
import it.unimi.dsi.fastutil.longs.LongSet;

public interface TestReporter {
    void onTestFailed(GameTestInfo testInfo);

    void onTestSuccess(GameTestInfo testInfo);

    default void finish() {
    }
}