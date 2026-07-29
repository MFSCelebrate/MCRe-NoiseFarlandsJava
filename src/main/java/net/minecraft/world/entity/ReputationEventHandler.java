package net.minecraft.world.entity;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.world.entity.ai.village.ReputationEventType;

public interface ReputationEventHandler {
    void onReputationEventFrom(ReputationEventType type, Entity source);
}