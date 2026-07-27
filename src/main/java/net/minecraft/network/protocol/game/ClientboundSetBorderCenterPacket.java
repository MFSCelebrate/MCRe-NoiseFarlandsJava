package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.Identifier;

public class ClientboundSetBorderCenterPacket implements Packet<ClientGamePacketListener> {
    public ClientboundSetBorderCenterPacket() {
    }

    public ClientboundSetBorderCenterPacket(final FriendlyByteBuf buf) {
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
    public PacketType<ClientboundSetBorderCenterPacket> type() {
        return new PacketType<>() {
            @Override
            public Identifier id() {
                return Identifier.withDefaultNamespace("set_border_center");
            }

            @Override
            public Flow flow() {
                return Flow.PLAY;
            }
        };
    }

    public static final StreamCodec<FriendlyByteBuf, ClientboundSetBorderCenterPacket> STREAM_CODEC =
        Packet.codec(ClientboundSetBorderCenterPacket::write, ClientboundSetBorderCenterPacket::new);
}