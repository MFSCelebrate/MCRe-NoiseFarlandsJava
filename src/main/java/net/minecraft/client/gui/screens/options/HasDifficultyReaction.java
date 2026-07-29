package net.minecraft.client.gui.screens.options;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface HasDifficultyReaction {
    void onDifficultyChanged();
}