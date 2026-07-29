package net.minecraft.network.protocol.login.custom;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

public interface CustomQueryPayload {
    Identifier id();

    void write(FriendlyByteBuf output);
}