package net.minecraft.server.packs;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.server.packs.repository.Pack;

public record PackSelectionConfig(boolean required, Pack.Position defaultPosition, boolean fixedPosition) {
}