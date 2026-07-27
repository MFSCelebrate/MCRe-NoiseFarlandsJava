package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.Identifier;

public class ClientboundInitializeBorderPacket implements Packet<ClientGamePacketListener> {
    public ClientboundInitializeBorderPacket() {}

    public ClientboundInitializeBorderPacket(final FriendlyByteBuf buf) {
        // 世界边界已移除，不读取任何数据
    }

    public void write(final FriendlyByteBuf buf) {
        // 不写入任何数据
    }

    @Override
    public void handle(final ClientGamePacketListener listener) {
        // 忽略
    }

    @Override
    public PacketType<ClientboundInitializeBorderPacket> type() {
        return new PacketType<>() {
            @Override
            public Identifier id() {
                return Identifier.withDefaultNamespace("initialize_border");
            }

            @Override
            public Flow flow() {
                return Flow.PLAY;
            }
        };
    }

    public static final StreamCodec<
            FriendlyByteBuf,
            ClientboundInitializeBorderPacket> STREAM_CODEC = Packet.codec(ClientboundInitializeBorderPacket
            ::write, ClientboundInitializeBorderPacket::new);
}