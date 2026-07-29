package net.minecraft.client.resources.model;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@FunctionalInterface
@OnlyIn(Dist.CLIENT)
public interface ModelDebugName {
    String debugName();
}