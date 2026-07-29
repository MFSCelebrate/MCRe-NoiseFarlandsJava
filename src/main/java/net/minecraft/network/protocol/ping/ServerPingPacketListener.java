package net.minecraft.network.protocol.ping;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.network.PacketListener;

public interface ServerPingPacketListener extends PacketListener {
    void handlePingRequest(ServerboundPingRequestPacket packet);
}