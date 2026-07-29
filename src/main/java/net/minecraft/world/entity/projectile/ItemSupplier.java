package net.minecraft.world.entity.projectile;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.world.item.ItemStack;

public interface ItemSupplier {
    ItemStack getItem();
}