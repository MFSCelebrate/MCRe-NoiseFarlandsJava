package net.minecraft.world.level.entity;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.UUID;

public interface UniquelyIdentifyable {
    UUID getUUID();

    boolean isRemoved();
}