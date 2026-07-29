package net.minecraft.data.advancements;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.function.Consumer;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;

public interface AdvancementSubProvider {
    void generate(HolderLookup.Provider registries, Consumer<AdvancementHolder> output);

    static AdvancementHolder createPlaceholder(final String id) {
        return Advancement.Builder.advancement().build(Identifier.parse(id));
    }
}