package net.minecraft.network.protocol.ping;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.network.PacketListener;

public interface ClientPongPacketListener extends PacketListener {
    void handlePongResponse(ClientboundPongResponsePacket packet);
}