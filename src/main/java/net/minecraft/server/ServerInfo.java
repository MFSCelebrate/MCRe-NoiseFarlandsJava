package net.minecraft.server;
import it.unimi.dsi.fastutil.longs.LongSet;

public interface ServerInfo {
    String getMotd();

    String getServerVersion();

    int getPlayerCount();

    int getMaxPlayers();
}