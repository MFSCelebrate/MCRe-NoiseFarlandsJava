package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.Identifier;

public class ClientboundSetBorderLerpSizePacket implements Packet<ClientGamePacketListener> {
    public ClientboundSetBorderLerpSizePacket() {
    }

    public ClientboundSetBorderLerpSizePacket(final FriendlyByteBuf buf) {
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
    public PacketType<ClientboundSetBorderLerpSizePacket> type() {
        return new PacketType<>() {
            @Override
            public Identifier id() {
                return Identifier.withDefaultNamespace("set_border_lerp_size");
            }

            @Override
            public Flow flow() {
                return Flow.PLAY;
            }
        };
    }

    public static final StreamCodec<FriendlyByteBuf, ClientboundSetBorderLerpSizePacket> STREAM_CODEC =
        Packet.codec(ClientboundSetBorderLerpSizePacket::write, ClientboundSetBorderLerpSizePacket::new);
}