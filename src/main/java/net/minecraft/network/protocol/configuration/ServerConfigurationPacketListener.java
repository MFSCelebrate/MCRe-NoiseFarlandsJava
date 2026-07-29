package net.minecraft.network.protocol.configuration;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.common.ServerCommonPacketListener;

public interface ServerConfigurationPacketListener extends ServerCommonPacketListener {
    @Override
    default ConnectionProtocol protocol() {
        return ConnectionProtocol.CONFIGURATION;
    }

    void handleConfigurationFinished(ServerboundFinishConfigurationPacket packet);

    void handleSelectKnownPacks(ServerboundSelectKnownPacks packet);

    void handleAcceptCodeOfConduct(ServerboundAcceptCodeOfConductPacket packet);
}