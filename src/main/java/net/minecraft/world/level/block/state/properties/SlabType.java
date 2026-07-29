package net.minecraft.world.level.block.state.properties;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.util.StringRepresentable;

public enum SlabType implements StringRepresentable {
    TOP("top"),
    BOTTOM("bottom"),
    DOUBLE("double");

    private final String name;

    SlabType(final String name) {
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