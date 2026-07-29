package net.minecraft.network.protocol;
import it.unimi.dsi.fastutil.longs.LongSet;

public enum PacketFlow {
    SERVERBOUND("serverbound"),
    CLIENTBOUND("clientbound");

    private final String id;

    PacketFlow(final String id) {
        this.id = id;
    }

    public PacketFlow getOpposite() {
        return this == CLIENTBOUND ? SERVERBOUND : CLIENTBOUND;
    }

    public String id() {
        return this.id;
    }
}