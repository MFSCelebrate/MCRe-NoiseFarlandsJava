package net.minecraft.gizmos;
import it.unimi.dsi.fastutil.longs.LongSet;

public interface GizmoProperties {
    GizmoProperties setAlwaysOnTop();

    GizmoProperties persistForMillis(int milliseconds);

    GizmoProperties fadeOut();
}