package net.minecraft.network.chat.numbers;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.network.chat.MutableComponent;

public interface NumberFormat {
    MutableComponent format(int value);

    NumberFormatType<? extends NumberFormat> type();
}