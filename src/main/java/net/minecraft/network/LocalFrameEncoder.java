package net.minecraft.network;
import it.unimi.dsi.fastutil.longs.LongSet;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;

public class LocalFrameEncoder extends ChannelOutboundHandlerAdapter {
    @Override
    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) {
        ctx.write(HiddenByteBuf.pack(msg), promise);
    }
}