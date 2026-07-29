package net.minecraft.world.level.block.state.properties;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.util.StringRepresentable;

public enum SpeleothemThickness implements StringRepresentable {
    TIP_MERGE("tip_merge"),
    TIP("tip"),
    FRUSTUM("frustum"),
    MIDDLE("middle"),
    BASE("base");

    private final String name;

    SpeleothemThickness(final String name) {
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