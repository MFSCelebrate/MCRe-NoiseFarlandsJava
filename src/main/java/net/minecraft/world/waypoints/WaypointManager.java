package net.minecraft.world.waypoints;
import it.unimi.dsi.fastutil.longs.LongSet;

public interface WaypointManager<T extends Waypoint> {
    void trackWaypoint(T waypoint);

    void updateWaypoint(T waypoint);

    void untrackWaypoint(T waypoint);
}