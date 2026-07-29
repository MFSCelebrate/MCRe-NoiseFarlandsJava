package net.minecraft.server.packs.repository;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.function.Consumer;

@FunctionalInterface
public interface RepositorySource {
    void loadPacks(Consumer<Pack> result);
}