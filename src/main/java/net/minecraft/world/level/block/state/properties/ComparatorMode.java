package net.minecraft.world.level.block.state.properties;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.util.StringRepresentable;

public enum ComparatorMode implements StringRepresentable {
    COMPARE("compare"),
    SUBTRACT("subtract");

    private final String name;

    ComparatorMode(final String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return this.name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}