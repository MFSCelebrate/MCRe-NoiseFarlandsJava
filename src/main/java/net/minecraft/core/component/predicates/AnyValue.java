package net.minecraft.core.component.predicates;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;

public record AnyValue(DataComponentType<?> type) implements DataComponentPredicate {
    @Override
    public boolean matches(final DataComponentGetter components) {
        return components.get(this.type) != null;
    }
}