package net.minecraft.world.level.levelgen.structure;
import it.unimi.dsi.fastutil.longs.LongSet;

public enum StructureCheckResult {
    START_PRESENT,
    START_NOT_PRESENT,
    CHUNK_LOAD_NEEDED;
}