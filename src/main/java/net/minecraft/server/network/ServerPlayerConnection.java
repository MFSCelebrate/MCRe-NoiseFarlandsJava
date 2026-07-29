package net.minecraft.server.network;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;

public interface ServerPlayerConnection {
    ServerPlayer getPlayer();

    void send(final Packet<?> packet);
}