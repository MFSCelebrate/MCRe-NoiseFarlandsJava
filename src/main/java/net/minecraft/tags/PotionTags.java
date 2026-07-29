package net.minecraft.tags;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.alchemy.Potion;

public class PotionTags {
    public static final TagKey<Potion> TRADEABLE = create("tradeable");

    private PotionTags() {
    }

    private static TagKey<Potion> create(final String name) {
        return TagKey.create(Registries.POTION, Identifier.withDefaultNamespace(name));
    }
}