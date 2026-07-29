package net.minecraft.client.model.geom.builders;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class MaterialDefinition {
    final int xTexSize;
    final int yTexSize;

    public MaterialDefinition(final int xTexSize, final int yTexSize) {
        this.xTexSize = xTexSize;
        this.yTexSize = yTexSize;
    }
}