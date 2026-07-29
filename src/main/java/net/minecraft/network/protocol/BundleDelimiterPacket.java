package net.minecraft.network.protocol;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.network.PacketListener;

public abstract class BundleDelimiterPacket<T extends PacketListener> implements Packet<T> {
    @Override
    public final void handle(final T listener) {
        throw new AssertionError("This packet should be handled by pipeline");
    }

    @Override
    public abstract PacketType<? extends BundleDelimiterPacket<T>> type();
}