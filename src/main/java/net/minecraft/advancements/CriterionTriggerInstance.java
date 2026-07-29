package net.minecraft.advancements;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.world.level.storage.loot.ValidationContextSource;

public interface CriterionTriggerInstance {
    void validate(ValidationContextSource validator);
}