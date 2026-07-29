package net.minecraft.gametest.framework;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.network.chat.Component;

public abstract class GameTestException extends RuntimeException {
    public GameTestException(final String message) {
        super(message);
    }

    public abstract Component getDescription();
}