package net.minecraft.client.gui.screens.recipebook;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface RecipeUpdateListener {
    void recipesUpdated();

    void fillGhostRecipe(RecipeDisplay display);
}