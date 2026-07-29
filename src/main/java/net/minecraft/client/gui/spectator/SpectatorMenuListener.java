package net.minecraft.client.gui.spectator;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface SpectatorMenuListener {
    void onSpectatorMenuClosed(SpectatorMenu menu);
}