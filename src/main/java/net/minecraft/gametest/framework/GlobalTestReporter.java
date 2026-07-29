package net.minecraft.gametest.framework;
import it.unimi.dsi.fastutil.longs.LongSet;

public class GlobalTestReporter {
    private static TestReporter DELEGATE = new LogTestReporter();

    public static void replaceWith(final TestReporter testReporter) {
        DELEGATE = testReporter;
    }

    public static void onTestFailed(final GameTestInfo testInfo) {
        DELEGATE.onTestFailed(testInfo);
    }

    public static void onTestSuccess(final GameTestInfo testInfo) {
        DELEGATE.onTestSuccess(testInfo);
    }

    public static void finish() {
        DELEGATE.finish();
    }
}