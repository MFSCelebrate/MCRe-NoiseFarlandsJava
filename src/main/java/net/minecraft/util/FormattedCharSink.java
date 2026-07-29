package net.minecraft.util;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.network.chat.Style;

@FunctionalInterface
public interface FormattedCharSink {
    boolean accept(int position, Style style, int codepoint);
}