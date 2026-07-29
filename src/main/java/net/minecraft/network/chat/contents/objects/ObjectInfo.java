package net.minecraft.network.chat.contents.objects;
import it.unimi.dsi.fastutil.longs.LongSet;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.chat.FontDescription;

public interface ObjectInfo {
    FontDescription fontDescription();

    String defaultFallback();

    MapCodec<? extends ObjectInfo> codec();
}