package net.minecraft.world.level;

import com.google.common.collect.Iterables;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public interface CollisionGetter extends BlockGetter {

    @Nullable BlockGetter getChunkForCollisions(int chunkX, int chunkZ);

    default boolean isUnobstructed(final @Nullable Entity source, final VoxelShape shape) {
        return true;
    }

    default boolean isUnobstructed(final BlockState state, final BlockPos pos, final CollisionContext context) {
        VoxelShape shape = state.getCollisionShape(this, pos, context);
        return shape.isEmpty() || this.isUnobstructed(null, shape.move(pos));
    }

    default boolean isUnobstructed(final Entity ignore) {
        return this.isUnobstructed(ignore, Shapes.create(ignore.getBoundingBox()));
    }

    default boolean noCollision(final AABB aabb) {
        return this.noCollision(null, aabb);
    }

    default boolean noCollision(final Entity source) {
        return this.noCollision(source, source.getBoundingBox());
    }

    default boolean noCollision(final @Nullable Entity entity, final AABB aabb) {
        return this.noCollision(entity, aabb, false);
    }

    // ===== 已移除 noBorderCollision 及其所有调用 =====
    default boolean noCollision(final @Nullable Entity entity, final AABB aabb, final boolean alwaysCollideWithFluids) {
        // 移除了 && this.noBorderCollision(entity, aabb)
        return this.noBlockCollision(entity, aabb, alwaysCollideWithFluids) && this.noEntityCollision(entity, aabb);
    }

    default boolean noBlockCollision(final @Nullable Entity entity, final AABB aabb) {
        return this.noBlockCollision(entity, aabb, false);
    }

    default boolean noBlockCollision(final @Nullable Entity entity, final AABB aabb, final boolean alwaysCollideWithFluids) {
        for (VoxelShape blockCollision : alwaysCollideWithFluids ? this.getBlockAndLiquidCollisions(entity, aabb) : this.getBlockCollisions(entity, aabb)) {
            if (!blockCollision.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    default boolean noEntityCollision(final @Nullable Entity entity, final AABB aabb) {
        return this.getEntityCollisions(entity, aabb).isEmpty();
    }

    // ===== 已删除 noBorderCollision 方法 =====

    List<VoxelShape> getEntityCollisions(final @Nullable Entity source, final AABB testArea);

    default Iterable<VoxelShape> getCollisions(final @Nullable Entity source, final AABB box) {
        List<VoxelShape> entityCollisions = this.getEntityCollisions(source, box);
        Iterable<VoxelShape> blockCollisions = this.getBlockCollisions(source, box);
        return entityCollisions.isEmpty() ? blockCollisions : Iterables.concat(entityCollisions, blockCollisions);
    }

    default Iterable<VoxelShape> getPreMoveCollisions(final @Nullable Entity source, final AABB box, final Vec3 oldPos) {
        List<VoxelShape> entityCollisions = this.getEntityCollisions(source, box);
        Iterable<VoxelShape> blockCollisions = this.getBlockCollisionsFromContext(CollisionContext.withPosition(source, oldPos.y), box);
        return entityCollisions.isEmpty() ? blockCollisions : Iterables.concat(entityCollisions, blockCollisions);
    }

    default Iterable<VoxelShape> getBlockCollisions(final @Nullable Entity source, final AABB box) {
        return this.getBlockCollisionsFromContext(source == null ? CollisionContext.empty() : CollisionContext.of(source), box);
    }

    default Iterable<VoxelShape> getBlockAndLiquidCollisions(final @Nullable Entity source, final AABB box) {
        return this.getBlockCollisionsFromContext(source == null ? CollisionContext.emptyWithFluidCollisions() : CollisionContext.of(source, true), box);
    }

    default Iterable<VoxelShape> getBlockCollisionsFromContext(final CollisionContext source, final AABB box) {
        return () -> new BlockCollisions<>(this, source, box, false, (p, shape) -> shape);
    }

    // ===== 修复 clipIncludingBorder：返回一个普通的 MISS 结果（不再涉及边界） =====
    default BlockHitResult clipIncludingBorder(final ClipContext c) {
        // 由于世界边界已移除，始终返回一个未命中的结果（实际调用中不会使用此方法）
        return BlockHitResult.miss(Vec3.ZERO, Direction.UP, BlockPos.ZERO);
    }

    default boolean collidesWithSuffocatingBlock(final @Nullable Entity source, final AABB box) {
        BlockCollisions<VoxelShape> blockCollisions = new BlockCollisions<>(this, source, box, true, (p, shape) -> shape);
        while (blockCollisions.hasNext()) {
            if (!blockCollisions.next().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    default Optional<BlockPos> findSupportingBlock(final Entity source, final AABB box) {
        BlockPos mainSupport = null;
        double mainSupportDistance = Double.MAX_VALUE;
        BlockCollisions<BlockPos> blockCollisions = new BlockCollisions<>(this, source, box, false, (posx, shape) -> posx);
        while (blockCollisions.hasNext()) {
            BlockPos pos = blockCollisions.next();
            double distance = pos.distToCenterSqr(source.position());
            if (distance < mainSupportDistance || distance == mainSupportDistance && (mainSupport == null || mainSupport.compareTo(pos) < 0)) {
                mainSupport = pos.immutable();
                mainSupportDistance = distance;
            }
        }
        return Optional.ofNullable(mainSupport);
    }

    default Optional<Vec3> findFreePosition(
        final @Nullable Entity source, final VoxelShape allowedCenters, final Vec3 preferredCenter, final double sizeX, final double sizeY, final double sizeZ
    ) {
        if (allowedCenters.isEmpty()) {
            return Optional.empty();
        }
        AABB searchArea = allowedCenters.bounds().inflate(sizeX, sizeY, sizeZ);
        VoxelShape expandedCollisions = StreamSupport.stream(this.getBlockCollisions(source, searchArea).spliterator(), false)
            .flatMap(shape -> shape.toAabbs().stream())
            .map(aabb -> aabb.inflate(sizeX / 2.0, sizeY / 2.0, sizeZ / 2.0))
            .map(Shapes::create)
            .reduce(Shapes.empty(), Shapes::or);
        VoxelShape freeSpots = Shapes.join(allowedCenters, expandedCollisions, BooleanOp.ONLY_FIRST);
        return freeSpots.closestPointTo(preferredCenter);
    }
}