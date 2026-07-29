package net.minecraft.network.chat.numbers;
import it.unimi.dsi.fastutil.longs.LongSet;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public interface NumberFormatType<T extends NumberFormat> {
    MapCodec<T> mapCodec();

    StreamCodec<RegistryFriendlyByteBuf, T> streamCodec();
}