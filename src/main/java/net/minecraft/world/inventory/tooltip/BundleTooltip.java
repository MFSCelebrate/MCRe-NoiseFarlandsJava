package net.minecraft.world.inventory.tooltip;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.world.item.component.BundleContents;

public record BundleTooltip(BundleContents contents) implements TooltipComponent {
}