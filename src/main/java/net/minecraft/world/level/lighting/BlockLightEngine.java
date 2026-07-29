package net.minecraft.world.level.lighting;

import com.google.common.annotations.VisibleForTesting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;

public final class BlockLightEngine extends LightEngine<BlockLightSectionStorage.BlockDataLayerStorageMap, BlockLightSectionStorage> {

    public BlockLightEngine(final LightChunkGetter chunkSource) {
        this(chunkSource, new BlockLightSectionStorage(chunkSource));
    }

    @VisibleForTesting
    public BlockLightEngine(final LightChunkGetter chunkSource, final BlockLightSectionStorage storage) {
        super(chunkSource, storage);
    }

    // ===== 重写 checkNode，参数改为 BlockPos =====
    @Override
    protected void checkNode(final BlockPos pos) {
        long blockNode = pos.asLong();
        long sectionNode = SectionPos.blockToSection(blockNode);
        if (this.storage.storingLightForSection(sectionNode)) {
            BlockState state = this.getState(pos);
            int lightEmission = this.getEmission(pos, state);
            int oldLevel = this.storage.getStoredLevel(blockNode);
            if (lightEmission < oldLevel) {
                this.storage.setStoredLevel(blockNode, 0);
                this.enqueueDecrease(blockNode, LightEngine.QueueEntry.decreaseAllDirections(oldLevel));
            } else {
                this.enqueueDecrease(blockNode, PULL_LIGHT_IN_ENTRY);
            }
            if (lightEmission > 0) {
                this.enqueueIncrease(blockNode, LightEngine.QueueEntry.increaseLightFromEmission(lightEmission, isEmptyShape(state)));
            }
        }
    }

    // ===== 重写 propagateIncrease，参数改为 BlockPos =====
    @Override
    protected void propagateIncrease(final BlockPos fromPos, final long increaseData, final int fromLevel) {
        long fromNode = fromPos.asLong();
        BlockState fromState = null;

        for (Direction propagationDirection : PROPAGATION_DIRECTIONS) {
            if (LightEngine.QueueEntry.shouldPropagateInDirection(increaseData, propagationDirection)) {
                BlockPos toPos = fromPos.relative(propagationDirection);
                long toNode = toPos.asLong();
                if (this.storage.storingLightForSection(SectionPos.blockToSection(toNode))) {
                    int toLevel = this.storage.getStoredLevel(toNode);
                    int maxPossibleNewToLevel = fromLevel - 1;
                    if (maxPossibleNewToLevel > toLevel) {
                        BlockState toState = this.getState(toPos);
                        int newToLevel = fromLevel - this.getOpacity(toState);
                        if (newToLevel > toLevel) {
                            if (fromState == null) {
                                fromState = LightEngine.QueueEntry.isFromEmptyShape(increaseData)
                                    ? Blocks.AIR.defaultBlockState()
                                    : this.getState(fromPos);
                            }
                            if (!this.shapeOccludes(fromState, toState, propagationDirection)) {
                                this.storage.setStoredLevel(toNode, newToLevel);
                                if (newToLevel > 1) {
                                    this.enqueueIncrease(
                                        toNode,
                                        LightEngine.QueueEntry.increaseSkipOneDirection(newToLevel, isEmptyShape(toState), propagationDirection.getOpposite())
                                    );
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ===== 重写 propagateDecrease，参数改为 BlockPos =====
    @Override
    protected void propagateDecrease(final BlockPos fromPos, final long decreaseData) {
        long fromNode = fromPos.asLong();
        int oldFromLevel = LightEngine.QueueEntry.getFromLevel(decreaseData);

        for (Direction propagationDirection : PROPAGATION_DIRECTIONS) {
            if (LightEngine.QueueEntry.shouldPropagateInDirection(decreaseData, propagationDirection)) {
                BlockPos toPos = fromPos.relative(propagationDirection);
                long toNode = toPos.asLong();
                if (this.storage.storingLightForSection(SectionPos.blockToSection(toNode))) {
                    int toLevel = this.storage.getStoredLevel(toNode);
                    if (toLevel != 0) {
                        if (toLevel <= oldFromLevel - 1) {
                            BlockState toState = this.getState(toPos);
                            int toEmission = this.getEmission(toPos, toState);
                            this.storage.setStoredLevel(toNode, 0);
                            if (toEmission < toLevel) {
                                this.enqueueDecrease(toNode, LightEngine.QueueEntry.decreaseSkipOneDirection(toLevel, propagationDirection.getOpposite()));
                            }
                            if (toEmission > 0) {
                                this.enqueueIncrease(toNode, LightEngine.QueueEntry.increaseLightFromEmission(toEmission, isEmptyShape(toState)));
                            }
                        } else {
                            this.enqueueIncrease(toNode, LightEngine.QueueEntry.increaseOnlyOneDirection(toLevel, false, propagationDirection.getOpposite()));
                        }
                    }
                }
            }
        }
    }

    // ===== getEmission 参数改为 BlockPos =====
    private int getEmission(final BlockPos pos, final BlockState state) {
        long blockNode = pos.asLong();
        int emission = state.getLightEmission();
        return emission > 0 && this.storage.lightOnInSection(SectionPos.blockToSection(blockNode)) ? emission : 0;
    }

    // ===== propagateLightSources 使用 ChunkPos 对象 =====
    @Override
    public void propagateLightSources(final ChunkPos pos) {
        this.setLightEnabled(pos, true);
        LightChunk chunk = this.chunkSource.getChunkForLighting(pos.x(), pos.z());
        if (chunk != null) {
            // ===== 回调直接传递 BlockPos 对象，不再调用 asLong =====
            chunk.findBlockLightSources((lightPos, state) -> {
                int lightEmission = state.getLightEmission();
                this.enqueueIncrease(lightPos.asLong(), LightEngine.QueueEntry.increaseLightFromEmission(lightEmission, isEmptyShape(state)));
            });
        }
    }
}