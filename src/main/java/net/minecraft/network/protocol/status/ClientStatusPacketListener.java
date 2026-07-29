package net.minecraft.network.protocol.status;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.network.ClientboundPacketListener;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.ping.ClientPongPacketListener;

public interface ClientStatusPacketListener extends ClientboundPacketListener, ClientPongPacketListener {
    @Override
    default ConnectionProtocol protocol() {
        return ConnectionProtocol.STATUS;
    }

    void handleStatusResponse(ClientboundStatusResponsePacket packet);
}