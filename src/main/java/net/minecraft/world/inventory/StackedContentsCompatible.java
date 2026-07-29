package net.minecraft.world.inventory;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.world.entity.player.StackedItemContents;

@FunctionalInterface
public interface StackedContentsCompatible {
    void fillStackedContents(StackedItemContents contents);
}