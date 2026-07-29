package net.minecraft.server.packs;
import it.unimi.dsi.fastutil.longs.LongSet;

public enum PackType {
    CLIENT_RESOURCES("assets"),
    SERVER_DATA("data");

    private final String directory;

    PackType(final String directory) {
        this.directory = directory;
    }

    public String getDirectory() {
        return this.directory;
    }
}