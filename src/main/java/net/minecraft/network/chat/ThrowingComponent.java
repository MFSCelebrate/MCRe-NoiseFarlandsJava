package net.minecraft.network.chat;
import it.unimi.dsi.fastutil.longs.LongSet;

public class ThrowingComponent extends Exception {
    private final Component component;

    public ThrowingComponent(final Component component) {
        super(component.getString());
        this.component = component;
    }

    public ThrowingComponent(final Component component, final Throwable cause) {
        super(component.getString(), cause);
        this.component = component;
    }

    public Component getComponent() {
        return this.component;
    }
}