package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.Identifier;

public class ClientboundSetBorderWarningDistancePacket implements Packet<ClientGamePacketListener> {
    public ClientboundSetBorderWarningDistancePacket() {}

    public ClientboundSetBorderWarningDistancePacket(final FriendlyByteBuf buf) {
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
    return new PacketType<>(PacketFlow.PLAY, Identifier.withDefaultNamespace("set_border_warning_distance"));
}

    public static final StreamCodec<FriendlyByteBuf, ClientboundSetBorderWarningDistancePacket> STREAM_CODEC =
        Packet.codec(ClientboundSetBorderWarningDistancePacket::write, ClientboundSetBorderWarningDistancePacket::new);
}