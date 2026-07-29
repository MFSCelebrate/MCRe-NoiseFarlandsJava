package net.minecraft.commands.functions;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.List;
import net.minecraft.commands.execution.UnboundEntryAction;
import net.minecraft.resources.Identifier;

public interface InstantiatedFunction<T> {
    Identifier id();

    List<UnboundEntryAction<T>> entries();
}