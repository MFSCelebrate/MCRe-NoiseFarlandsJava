package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.Identifier;

public class ClientboundInitializeBorderPacket implements Packet<ClientGamePacketListener> {
    public ClientboundInitializeBorderPacket() {}

    public ClientboundInitializeBorderPacket(final FriendlyByteBuf buf) {
        // 不读取
    }

    public void write(final FriendlyByteBuf buf) {
        // 不写入
    }

    @Override
    public void handle(final ClientGamePacketListener listener) {
        // 忽略
    }

@Override
public PacketType<YourClass> type() {
    return new PacketType<>(PacketFlow.PLAY, Identifier.withDefaultNamespace("initialize_border"));
}

    public static final StreamCodec<FriendlyByteBuf, ClientboundInitializeBorderPacket> STREAM_CODEC =
        Packet.codec(ClientboundInitializeBorderPacket::write, ClientboundInitializeBorderPacket::new);
}