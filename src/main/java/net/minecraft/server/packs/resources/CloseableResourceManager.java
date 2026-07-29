package net.minecraft.server.packs.resources;
import it.unimi.dsi.fastutil.longs.LongSet;

public interface CloseableResourceManager extends ResourceManager, AutoCloseable {
    @Override
    void close();
}