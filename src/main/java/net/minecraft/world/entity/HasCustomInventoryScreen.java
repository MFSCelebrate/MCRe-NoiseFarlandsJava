package net.minecraft.world.entity;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.world.entity.player.Player;

public interface HasCustomInventoryScreen {
    void openCustomInventoryScreen(Player player);
}