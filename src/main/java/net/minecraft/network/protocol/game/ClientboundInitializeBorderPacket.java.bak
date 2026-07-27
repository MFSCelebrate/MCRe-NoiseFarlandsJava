package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
public class ClientboundInitializeBorderPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<
            FriendlyByteBuf, ClientboundInitializeBorderPacket> STREAM_CODEC = Packet.codec(
            ClientboundInitializeBorderPacket::write, ClientboundInitializeBorderPacket::new
    );
}