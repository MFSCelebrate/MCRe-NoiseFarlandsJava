package net.minecraft.network.protocol.login.custom;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

public record DiscardedQueryPayload(Identifier id) implements CustomQueryPayload {
    @Override
    public void write(final FriendlyByteBuf output) {
    }
}