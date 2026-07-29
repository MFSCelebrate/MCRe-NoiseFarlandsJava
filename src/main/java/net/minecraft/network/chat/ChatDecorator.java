package net.minecraft.network.chat;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface ChatDecorator {
    ChatDecorator PLAIN = (player, plain) -> plain;

    Component decorate(@Nullable ServerPlayer player, Component plain);
}