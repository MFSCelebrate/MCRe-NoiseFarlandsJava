package net.minecraft.world.level.block.state.properties;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.util.StringRepresentable;

public enum DoorHingeSide implements StringRepresentable {
    LEFT,
    RIGHT;

    @Override
    public String toString() {
        return this.getSerializedName();
    }

    @Override
    public String getSerializedName() {
        return this == LEFT ? "left" : "right";
    }
}