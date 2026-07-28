package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.Identifier;

public class ClientboundSetBorderWarningDelayPacket implements Packet<ClientGamePacketListener> {
    public ClientboundSetBorderWarningDelayPacket() {
    }

    public ClientboundSetBorderWarningDelayPacket(final FriendlyByteBuf buf) {
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
    public PacketType<ClientboundSetBorderWarningDelayPacket> type() {
        return new PacketType<>() {
            @Override
            public Identifier id() {
                return Identifier.withDefaultNamespace("set_border_warning_delay");
            }

            @Override
            public Flow flow() {
                return Flow.PLAY;
            }
        };
    }

    public static final StreamCodec<FriendlyByteBuf, ClientboundSetBorderWarningDelayPacket> STREAM_CODEC =
        Packet.codec(ClientboundSetBorderWarningDelayPacket::write, ClientboundSetBorderWarningDelayPacket::new);
}