package net.minecraft.client.renderer.state.level;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class WorldBorderRenderState {
    public double minX;
    public double maxX;
    public double minZ;
    public double maxZ;
    public int tint;
    public double alpha;

    public void reset() {
        this.alpha = 0.0;
    }

    @OnlyIn(Dist.CLIENT)
    public record DistancePerDirection(Direction direction, double distance) {}
}