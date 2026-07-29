package net.minecraft.commands.execution;
import it.unimi.dsi.fastutil.longs.LongSet;

public record CommandQueueEntry<T>(Frame frame, EntryAction<T> action) {
    public void execute(final ExecutionContext<T> context) {
        this.action.execute(context, this.frame);
    }
}