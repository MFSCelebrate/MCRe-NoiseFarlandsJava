package net.minecraft.server;
import it.unimi.dsi.fastutil.longs.LongSet;

public class TickTask implements Runnable {
    private final int tick;
    private final Runnable runnable;

    public TickTask(final int tick, final Runnable runnable) {
        this.tick = tick;
        this.runnable = runnable;
    }

    public int getTick() {
        return this.tick;
    }

    @Override
    public void run() {
        this.runnable.run();
    }
}