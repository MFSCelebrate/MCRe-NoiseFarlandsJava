package net.minecraft.world.phys;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public class BlockHitResult extends HitResult {
    private final Direction direction;
    private final BlockPos blockPos;
    private final boolean miss;
    private final boolean inside;

    // 静态工厂：构造一个“未命中”的结果
    public static BlockHitResult miss(final Vec3 location, final Direction direction, final BlockPos pos) {
        return new BlockHitResult(true, location, direction, pos, false);
    }

    // 公共构造函数：用于正常命中
    public BlockHitResult(final Vec3 location, final Direction direction, final BlockPos pos, final boolean inside) {
        this(false, location, direction, pos, inside);
    }

    // 私有主构造函数（所有字段在此初始化）
    private BlockHitResult(
        final boolean miss,
        final Vec3 location,
        final Direction direction,
        final BlockPos blockPos,
        final boolean inside
    ) {
        super(location);
        this.miss = miss;
        this.direction = direction;
        this.blockPos = blockPos;
        this.inside = inside;
    }

    // 修改方向（保留其他字段）
    public BlockHitResult withDirection(final Direction direction) {
        return new BlockHitResult(this.miss, this.location, direction, this.blockPos, this.inside);
    }

    // 修改位置（保留其他字段）
    public BlockHitResult withPosition(final BlockPos blockPos) {
        return new BlockHitResult(this.miss, this.location, this.direction, blockPos, this.inside);
    }

    // ===== 已移除 hitBorder() 方法（世界边界相关） =====

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