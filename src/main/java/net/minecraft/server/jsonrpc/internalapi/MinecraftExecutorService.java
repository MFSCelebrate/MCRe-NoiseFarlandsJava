package net.minecraft.server.jsonrpc.internalapi;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public interface MinecraftExecutorService {
    <V> CompletableFuture<V> submit(final Supplier<V> supplier);

    CompletableFuture<Void> submit(final Runnable runnable);
}