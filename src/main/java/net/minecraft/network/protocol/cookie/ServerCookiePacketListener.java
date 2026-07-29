package net.minecraft.network.protocol.cookie;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.network.protocol.game.ServerPacketListener;

public interface ServerCookiePacketListener extends ServerPacketListener {
    void handleCookieResponse(ServerboundCookieResponsePacket packet);
}