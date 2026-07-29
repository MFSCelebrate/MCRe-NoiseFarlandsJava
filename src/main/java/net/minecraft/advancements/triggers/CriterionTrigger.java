package net.minecraft.advancements.triggers;
import it.unimi.dsi.fastutil.longs.LongSet;

import com.mojang.serialization.Codec;
import net.minecraft.advancements.CriterionTriggerInstance;

public interface CriterionTrigger<T extends CriterionTriggerInstance> {
    Codec<T> codec();

    default Criterion<T> createCriterion(final T instance) {
        return new Criterion<>(this, instance);
    }
}