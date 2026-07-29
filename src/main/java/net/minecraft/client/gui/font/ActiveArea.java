package net.minecraft.client.gui.font;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.network.chat.Style;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface ActiveArea {
    Style style();

    float activeLeft();

    float activeTop();

    float activeRight();

    float activeBottom();
}