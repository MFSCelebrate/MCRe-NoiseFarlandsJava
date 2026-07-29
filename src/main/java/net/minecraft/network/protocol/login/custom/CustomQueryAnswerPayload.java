package net.minecraft.network.protocol.login.custom;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.network.FriendlyByteBuf;

public interface CustomQueryAnswerPayload {
    void write(FriendlyByteBuf output);
}