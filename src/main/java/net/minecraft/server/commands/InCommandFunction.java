package net.minecraft.server.commands;
import it.unimi.dsi.fastutil.longs.LongSet;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

@FunctionalInterface
public interface InCommandFunction<T, R> {
    R apply(T t) throws CommandSyntaxException;
}