package net.minecraft.network.protocol.game;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.network.protocol.BundleDelimiterPacket;
import net.minecraft.network.protocol.PacketType;

public class ClientboundBundleDelimiterPacket extends BundleDelimiterPacket<ClientGamePacketListener> {
    @Override
    public PacketType<ClientboundBundleDelimiterPacket> type() {
        return GamePacketTypes.CLIENTBOUND_BUNDLE_DELIMITER;
    }
}