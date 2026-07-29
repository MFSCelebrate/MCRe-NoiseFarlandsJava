package net.minecraft.network.protocol.cookie;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.network.ClientboundPacketListener;

public interface ClientCookiePacketListener extends ClientboundPacketListener {
    void handleRequestCookie(ClientboundCookieRequestPacket packet);
}