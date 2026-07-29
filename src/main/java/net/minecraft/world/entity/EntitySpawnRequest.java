package net.minecraft.world.entity;
import it.unimi.dsi.fastutil.longs.LongSet;

public record EntitySpawnRequest(EntitySpawnReason reason, boolean ignoreChecks) {
}