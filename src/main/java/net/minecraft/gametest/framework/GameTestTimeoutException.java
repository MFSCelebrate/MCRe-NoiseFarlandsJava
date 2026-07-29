package net.minecraft.gametest.framework;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.network.chat.Component;

public class GameTestTimeoutException extends GameTestException {
    protected final Component message;

    public GameTestTimeoutException(final Component message) {
        super(message.getString());
        this.message = message;
    }

    @Override
    public Component getDescription() {
        return this.message;
    }
}