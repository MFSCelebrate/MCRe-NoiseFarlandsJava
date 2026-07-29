package net.minecraft.network.protocol.login.custom;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.network.FriendlyByteBuf;

public record DiscardedQueryAnswerPayload() implements CustomQueryAnswerPayload {
    public static final DiscardedQueryAnswerPayload INSTANCE = new DiscardedQueryAnswerPayload();

    @Override
    public void write(final FriendlyByteBuf output) {
    }
}