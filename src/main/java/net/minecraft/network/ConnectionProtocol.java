package net.minecraft.network;
import it.unimi.dsi.fastutil.longs.LongSet;

public enum ConnectionProtocol {
    HANDSHAKING("handshake"),
    PLAY("play"),
    STATUS("status"),
    LOGIN("login"),
    CONFIGURATION("configuration");

    private final String id;

    ConnectionProtocol(final String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }
}