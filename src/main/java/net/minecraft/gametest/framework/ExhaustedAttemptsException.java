package net.minecraft.gametest.framework;
import it.unimi.dsi.fastutil.longs.LongSet;

public class ExhaustedAttemptsException extends Throwable {
    public ExhaustedAttemptsException(final int attempts, final int successes, final GameTestInfo testInfo) {
        super(
            "Not enough successes: "
                + successes
                + " out of "
                + attempts
                + " attempts. Required successes: "
                + testInfo.requiredSuccesses()
                + ". max attempts: "
                + testInfo.maxAttempts()
                + ".",
            testInfo.getError()
        );
    }
}