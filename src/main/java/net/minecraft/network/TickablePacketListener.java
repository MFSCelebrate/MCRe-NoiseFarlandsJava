package net.minecraft.network;
import it.unimi.dsi.fastutil.longs.LongSet;

public interface TickablePacketListener extends PacketListener {
    void tick();
}