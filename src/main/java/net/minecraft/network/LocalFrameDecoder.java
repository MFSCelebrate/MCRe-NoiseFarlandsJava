package net.minecraft.network;
import it.unimi.dsi.fastutil.longs.LongSet;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class LocalFrameDecoder extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        ctx.fireChannelRead(HiddenByteBuf.unpack(msg));
    }
}