package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;

public interface BoundingBoxRenderable {
    BoundingBoxRenderable.Mode renderMode();

    BoundingBoxRenderable.RenderableBox getRenderableBox();

    enum Mode {
        NONE,
        BOX,
        BOX_AND_INVISIBLE_BLOCKS;
    }

    record RenderableBox(BlockPos localPos, Vec3i size) {
        public static BoundingBoxRenderable.RenderableBox fromCorners(final long x1, final long y1, final long z1, final long x2, final long y2, final long z2) {
            long x = Math.min(x1, x2);
            long y = Math.min(y1, y2);
            long z = Math.min(z1, z2);
            return new BoundingBoxRenderable.RenderableBox(new BlockPos(x, y, z), new Vec3i(Math.max(x1, x2) - x, Math.max(y1, y2) - y, Math.max(z1, z2) - z));
        }
    }
}