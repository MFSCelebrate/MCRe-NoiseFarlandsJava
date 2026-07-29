package net.minecraft.gizmos;
import it.unimi.dsi.fastutil.longs.LongSet;

public interface Gizmo {
    void emit(GizmoPrimitives primitives, float alphaMultiplier);
}