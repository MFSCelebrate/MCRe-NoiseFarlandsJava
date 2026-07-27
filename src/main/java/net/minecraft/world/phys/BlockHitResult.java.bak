package net.minecraft.world.phys;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public class BlockHitResult extends HitResult {
    private final Direction direction;
    private final BlockPos blockPos;
    private final boolean miss;
    private final boolean inside;

    public static BlockHitResult miss(final Vec3 location, final Direction direction, final BlockPos pos) {
        return new BlockHitResult(true, location, direction, pos, false, false);
    }

    public BlockHitResult(final Vec3 location, final Direction direction, final BlockPos pos, final boolean inside) {
        this(false, location, direction, pos, inside, false);
    }

    public BlockHitResult(final Vec3 location, final Direction direction, final BlockPos pos, final boolean inside) {
        this(false, location, direction, pos, inside);
    }

    private BlockHitResult(
        final boolean miss, final Vec3 location, final Direction direction, final BlockPos blockPos, final boolean inside
    ) {
        super(location);
        this.miss = miss;
        this.direction = direction;
        this.blockPos = blockPos;
        this.inside = inside;
    }

    public BlockHitResult withDirection(final Direction direction) {
        return new BlockHitResult(this.miss, this.location, direction, this.blockPos, this.inside);
    }

    public BlockHitResult withPosition(final BlockPos blockPos) {
        return new BlockHitResult(this.miss, this.location, this.direction, blockPos, this.inside);
    }

    public BlockHitResult hitBorder() {
        return new BlockHitResult(this.miss, this.location, this.direction, this.blockPos, this.inside, true);
    }

    public BlockPos getBlockPos() {
        return this.blockPos;
    }

    public Direction getDirection() {
        return this.direction;
    }

    @Override
    public HitResult.Type getType() {
        return this.miss ? HitResult.Type.MISS : HitResult.Type.BLOCK;
    }

    public boolean isInside() {
        return this.inside;
    }
}