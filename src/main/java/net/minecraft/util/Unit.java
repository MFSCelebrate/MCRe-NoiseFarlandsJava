package net.minecraft.util;
import it.unimi.dsi.fastutil.longs.LongSet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

public enum Unit {
    INSTANCE;

    public static final Codec<Unit> CODEC = MapCodec.unitCodec(INSTANCE);
    public static final StreamCodec<ByteBuf, Unit> STREAM_CODEC = StreamCodec.unit(INSTANCE);
}