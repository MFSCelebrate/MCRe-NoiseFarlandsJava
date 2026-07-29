package net.minecraft.commands;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.network.chat.Component;

public class FunctionInstantiationException extends Exception {
    private final Component messageComponent;

    public FunctionInstantiationException(final Component messageComponent) {
        super(messageComponent.getString());
        this.messageComponent = messageComponent;
    }

    public Component messageComponent() {
        return this.messageComponent;
    }
}