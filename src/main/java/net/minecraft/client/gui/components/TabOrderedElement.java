package net.minecraft.client.gui.components;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface TabOrderedElement {
    default int getTabOrderGroup() {
        return 0;
    }
}