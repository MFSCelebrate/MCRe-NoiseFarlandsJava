package net.minecraft.client.multiplayer.chat;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public enum GuiMessageSource {
    PLAYER,
    SYSTEM_SERVER,
    SYSTEM_CLIENT;
}