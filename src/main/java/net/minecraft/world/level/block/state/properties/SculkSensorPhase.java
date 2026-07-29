package net.minecraft.world.level.block.state.properties;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.util.StringRepresentable;

public enum SculkSensorPhase implements StringRepresentable {
    INACTIVE("inactive"),
    ACTIVE("active"),
    COOLDOWN("cooldown");

    private final String name;

    SculkSensorPhase(final String name) {
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