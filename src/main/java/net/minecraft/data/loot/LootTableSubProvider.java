package net.minecraft.data.loot;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.function.BiConsumer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;

@FunctionalInterface
public interface LootTableSubProvider {
    void generate(BiConsumer<ResourceKey<LootTable>, LootTable.Builder> output);
}