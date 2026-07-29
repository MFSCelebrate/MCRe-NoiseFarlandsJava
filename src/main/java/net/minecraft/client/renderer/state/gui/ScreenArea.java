package net.minecraft.client.renderer.state.gui;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public interface ScreenArea {
    @Nullable ScreenRectangle bounds();
}