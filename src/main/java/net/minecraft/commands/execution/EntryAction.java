package net.minecraft.commands.execution;
import it.unimi.dsi.fastutil.longs.LongSet;

@FunctionalInterface
public interface EntryAction<T> {
    void execute(ExecutionContext<T> context, Frame frame);
}