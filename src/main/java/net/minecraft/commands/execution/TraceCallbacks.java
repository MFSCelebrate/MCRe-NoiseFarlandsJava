package net.minecraft.commands.execution;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.resources.Identifier;

public interface TraceCallbacks extends AutoCloseable {
    void onCommand(int depth, String command);

    void onReturn(int depth, String command, int result);

    void onError(String message);

    void onCall(int depth, Identifier function, int size);

    @Override
    void close();
}