package net.minecraft.gametest.framework;
import it.unimi.dsi.fastutil.longs.LongSet;

public interface GameTestListener {
    void testStructureLoaded(GameTestInfo testInfo);

    void testPassed(GameTestInfo testInfo, GameTestRunner runner);

    void testFailed(GameTestInfo testInfo, GameTestRunner runner);

    void testAddedForRerun(GameTestInfo original, GameTestInfo copy, GameTestRunner runner);
}