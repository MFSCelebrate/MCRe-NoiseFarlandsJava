package net.minecraft.client.renderer;

import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.ChunkLoadingRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class SectionOcclusionGraph {
    private static final int HALF_SECTION_SIZE = 8;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final int MINIMUM_ADVANCED_CULLING_DISTANCE = 60;
    private static final int MINIMUM_ADVANCED_CULLING_SECTION_DISTANCE = SectionPos.blockToSectionCoord(60);
    private static final double CEILED_SECTION_DIAGONAL = Math.ceil(Math.sqrt(3.0) * 16.0);
    private boolean needsFullUpdate = true;
    private @Nullable Future<?> fullUpdateTask;
    private @Nullable ViewArea viewArea;
    private final AtomicReference<SectionOcclusionGraph.@Nullable GraphState> currentGraph = new AtomicReference<>();
    private final AtomicBoolean needsFrustumUpdate = new AtomicBoolean(false);

    // ===== 改为对象集合 =====
    private final ObjectOpenHashSet<SectionPos> emptySections = new ObjectOpenHashSet<>();
    private final ObjectOpenHashSet<ChunkPos> loadedChunks = new ObjectOpenHashSet<>();

    private volatile @Nullable BlockingQueue<SectionRenderDispatcher.RenderSection> nextSectionsToPropagateFrom;
    private double prevCamX = Double.MIN_VALUE;
    private double prevCamY = Double.MIN_VALUE;
    private double prevCamZ = Double.MIN_VALUE;
    private int prevFov = Integer.MAX_VALUE;
    private boolean lastSmartCull = true;

    public void waitAndReset(final @Nullable ViewArea viewArea) {
        if (this.fullUpdateTask != null) {
            try {
                this.fullUpdateTask.get();
                this.fullUpdateTask = null;
            } catch (Exception e) {
                LOGGER.warn("Full update failed", e);
            }
        }
        this.viewArea = viewArea;
        if (viewArea != null) {
            this.currentGraph.set(new SectionOcclusionGraph.GraphState(viewArea));
            this.invalidate();
        } else {
            this.currentGraph.set(null);
            this.emptySections.clear();
            this.loadedChunks.clear();
        }
    }

    // ===== 返回 ChunkPos 集合 =====
    public Set<ChunkPos> expectedChunks() {
        SectionOcclusionGraph.GraphState graphState = this.currentGraph.get();
        if (graphState != null) {
            return graphState.storage.sectionsWaitingForChunkLoads.keySet();
        }
        return Collections.emptySet();
    }

    public void invalidate() {
        this.needsFullUpdate = true;
    }

    public void invalidateIfNeeded(final CameraRenderState camera, final int fov) {
        Vec3 cameraPos = camera.pos;
        double camX = Math.floor(cameraPos.x / 8.0);
        double camY = Math.floor(cameraPos.y / 8.0);
        double camZ = Math.floor(cameraPos.z / 8.0);
        if (camX != this.prevCamX || camY != this.prevCamY || camZ != this.prevCamZ || this.prevFov != fov || this.lastSmartCull != camera.smartCull) {
            this.invalidate();
        }
        this.prevCamX = camX;
        this.prevCamY = camY;
        this.prevCamZ = camZ;
        this.prevFov = fov;
        this.lastSmartCull = camera.smartCull;
    }

    public void addSectionsInFrustum(
        final Frustum frustum,
        final List<SectionRenderDispatcher.RenderSection> visibleSections,
        final List<SectionRenderDispatcher.RenderSection> nearbyVisibleSection
    ) {
        Frustum offsetFrustum = offsetFrustum(frustum);
        this.currentGraph.get().storage().sectionTree.visitNodes((node, fullyVisible, depth, isClose) -> {
            SectionRenderDispatcher.RenderSection renderSection = node.getSection();
            if (renderSection != null) {
                visibleSections.add(renderSection);
                if (isClose) {
                    nearbyVisibleSection.add(renderSection);
                }
            }
        }, offsetFrustum, 32);
    }

    public boolean consumeFrustumUpdate() {
        return this.needsFrustumUpdate.compareAndSet(true, false);
    }

    public void schedulePropagationFrom(final SectionRenderDispatcher.RenderSection section) {
        BlockingQueue<SectionRenderDispatcher.RenderSection> nextSectionsToPropagateFrom = this.nextSectionsToPropagateFrom;
        if (nextSectionsToPropagateFrom != null) {
            nextSectionsToPropagateFrom.add(section);
        }
        BlockingQueue<SectionRenderDispatcher.RenderSection> sectionsToPropagateFrom = this.currentGraph.get().sectionsToPropagateFrom;
        if (sectionsToPropagateFrom != nextSectionsToPropagateFrom) {
            sectionsToPropagateFrom.add(section);
        }
    }

    public void update(final CameraRenderState camera, final int fov, final ChunkLoadingRenderState chunkLoadingRenderState) {
        // ===== 参数仍为 LongOpenHashSet，内部转换为对象 =====
        this.updateLoadedChunks(chunkLoadingRenderState.addedLoadedChunks, chunkLoadingRenderState.removedLoadedChunks);
        this.updateEmptySections(chunkLoadingRenderState.addedEmptySections, chunkLoadingRenderState.removedEmptySections);

        if (!camera.isFrustumCaptured) {
            this.invalidateIfNeeded(camera, fov);
            if (this.needsFullUpdate && (this.fullUpdateTask == null || this.fullUpdateTask.isDone())) {
                this.scheduleFullUpdate(camera);
            }
            this.runPartialUpdate(camera, chunkLoadingRenderState.loadedExpectedChunks);
        }
    }

    private void scheduleFullUpdate(final CameraRenderState camera) {
        this.needsFullUpdate = false;
        // ===== 克隆对象集合 =====
        ObjectOpenHashSet<SectionPos> clonedEmptySections = this.emptySections.clone();
        ObjectOpenHashSet<ChunkPos> clonedLoadedChunks = this.loadedChunks.clone();
        this.fullUpdateTask = CompletableFuture.runAsync(() -> {
            SectionOcclusionGraph.GraphState newState = new SectionOcclusionGraph.GraphState(this.viewArea);
            this.nextSectionsToPropagateFrom = newState.sectionsToPropagateFrom;
            Queue<SectionOcclusionGraph.Node> queue = Queues.newArrayDeque();
            this.initializeQueueForFullUpdate(camera.blockPos, queue);
            queue.forEach(node -> newState.storage.sectionToNodeMap.put(node.section, node));
            this.runUpdates(newState.storage, camera.pos, queue, camera.smartCull, node -> {}, clonedEmptySections, clonedLoadedChunks);
            this.currentGraph.set(newState);
            this.nextSectionsToPropagateFrom = null;
            this.needsFrustumUpdate.set(true);
        }, Util.backgroundExecutor());
    }

    private void runPartialUpdate(final CameraRenderState camera, final ObjectOpenHashSet<ChunkPos> loadedExpectedChunks) {
        SectionOcclusionGraph.GraphState state = this.currentGraph.get();
        // ===== 遍历 ChunkPos =====
        for (ChunkPos chunkPos : loadedExpectedChunks) {
            List<SectionPos> waitingSections = state.storage.sectionsWaitingForChunkLoads.remove(chunkPos);
            if (waitingSections != null) {
                for (SectionPos sectionPos : waitingSections) {
                    SectionRenderDispatcher.RenderSection section = this.viewArea.getRenderSection(sectionPos);
                    if (section != null) {
                        this.schedulePropagationFrom(section);
                    }
                }
            }
        }
        if (!state.sectionsToPropagateFrom.isEmpty()) {
            Queue<SectionOcclusionGraph.Node> queue = Queues.newArrayDeque();
            while (!state.sectionsToPropagateFrom.isEmpty()) {
                SectionRenderDispatcher.RenderSection renderSection = state.sectionsToPropagateFrom.poll();
                SectionOcclusionGraph.Node node = state.storage.sectionToNodeMap.get(renderSection);
                if (node != null && node.section == renderSection) {
                    queue.add(node);
                }
            }
            Frustum offsetFrustum = offsetFrustum(camera.cullFrustum);
            Consumer<SectionRenderDispatcher.RenderSection> onSectionAdded = section -> {
                if (offsetFrustum.isVisible(section.getBoundingBox())) {
                    this.needsFrustumUpdate.set(true);
                }
            };
            this.runUpdates(state.storage, camera.pos, queue, camera.smartCull, onSectionAdded, this.emptySections, this.loadedChunks);
        }
    }

    private void initializeQueueForFullUpdate(final BlockPos cameraPosition, final Queue<SectionOcclusionGraph.Node> queue) {
        // ===== 使用 SectionPos 对象 =====
        SectionPos cameraSectionPos = SectionPos.of(cameraPosition);
        int cameraSectionY = cameraSectionPos.getY();
        SectionRenderDispatcher.RenderSection cameraSection = this.viewArea.getRenderSection(cameraSectionPos);
        if (cameraSection == null) {
            boolean isBelowTheWorld = cameraSectionY < this.viewArea.minSectionY();
            int sectionY = isBelowTheWorld ? this.viewArea.minSectionY() : this.viewArea.maxSectionY();
            int viewDistance = this.viewArea.getViewDistance();
            List<SectionOcclusionGraph.Node> toAdd = Lists.newArrayList();
            int cameraSectionX = cameraSectionPos.getX();
            int cameraSectionZ = cameraSectionPos.getZ();

            for (int sectionX = -viewDistance; sectionX <= viewDistance; sectionX++) {
                for (int sectionZ = -viewDistance; sectionZ <= viewDistance; sectionZ++) {
                    SectionPos sectionPos = SectionPos.of(sectionX + cameraSectionX, sectionY, sectionZ + cameraSectionZ);
                    SectionRenderDispatcher.RenderSection renderSectionAt = this.viewArea.getRenderSection(sectionPos);
                    if (renderSectionAt != null && this.isInViewDistance(cameraSectionPos, renderSectionAt.getSectionPos())) {
                        Direction sourceDirection = isBelowTheWorld ? Direction.UP : Direction.DOWN;
                        SectionOcclusionGraph.Node node = new SectionOcclusionGraph.Node(renderSectionAt, sourceDirection, 0);
                        node.setDirections(node.directions, sourceDirection);
                        if (sectionX > 0) {
                            node.setDirections(node.directions, Direction.EAST);
                        } else if (sectionX < 0) {
                            node.setDirections(node.directions, Direction.WEST);
                        }
                        if (sectionZ > 0) {
                            node.setDirections(node.directions, Direction.SOUTH);
                        } else if (sectionZ < 0) {
                            node.setDirections(node.directions, Direction.NORTH);
                        }
                        toAdd.add(node);
                    }
                }
            }

            toAdd.sort(Comparator.comparingDouble(c -> cameraPosition.distSqr(sectionPos.center())));
            queue.addAll(toAdd);
        } else {
            queue.add(new SectionOcclusionGraph.Node(cameraSection, null, 0));
        }
    }

    private void runUpdates(
        final SectionOcclusionGraph.GraphStorage storage,
        final Vec3 cameraPos,
        final Queue<SectionOcclusionGraph.Node> queue,
        final boolean smartCull,
        final Consumer<SectionRenderDispatcher.RenderSection> onSectionAdded,
        final ObjectOpenHashSet<SectionPos> emptySections,
        final ObjectOpenHashSet<ChunkPos> loadedChunks
    ) {
        SectionPos cameraSectionPos = SectionPos.of(cameraPos);
        BlockPos cameraSectionCenter = cameraSectionPos.center();

        while (!queue.isEmpty()) {
            SectionOcclusionGraph.Node node = queue.poll();
            SectionRenderDispatcher.RenderSection currentSection = node.section;
            SectionPos sectionPos = currentSection.getSectionPos();
            ChunkPos chunkPos = sectionPos.chunk();

            if (!loadedChunks.contains(chunkPos)) {
                storage.sectionsWaitingForChunkLoads.computeIfAbsent(chunkPos, k -> new ArrayList<>()).add(sectionPos);
            } else {
                if (!emptySections.contains(sectionPos)) {
                    if (storage.sectionTree.add(currentSection)) {
                        onSectionAdded.accept(currentSection);
                    }
                } else {
                    currentSection.sectionMesh.compareAndSet(CompiledSectionMesh.UNCOMPILED, CompiledSectionMesh.EMPTY);
                }

                boolean distantFromCamera = Math.abs(sectionPos.getX() - cameraSectionPos.getX()) > MINIMUM_ADVANCED_CULLING_SECTION_DISTANCE
                    || Math.abs(sectionPos.getY() - cameraSectionPos.getY()) > MINIMUM_ADVANCED_CULLING_SECTION_DISTANCE
                    || Math.abs(sectionPos.getZ() - cameraSectionPos.getZ()) > MINIMUM_ADVANCED_CULLING_SECTION_DISTANCE;

                for (Direction direction : DIRECTIONS) {
                    SectionRenderDispatcher.RenderSection renderSectionAt = this.getRelativeFrom(cameraSectionPos, currentSection, direction);
                    if (renderSectionAt != null && (!smartCull || !node.hasDirection(direction.getOpposite()))) {
                        if (smartCull && node.hasSourceDirections()) {
                            SectionMesh sectionMesh = currentSection.getSectionMesh();
                            boolean visible = false;
                            for (int i = 0; i < DIRECTIONS.length; i++) {
                                if (node.hasSourceDirection(i) && sectionMesh.facesCanSeeEachother(DIRECTIONS[i].getOpposite(), direction)) {
                                    visible = true;
                                    break;
                                }
                            }
                            if (!visible) {
                                continue;
                            }
                        }

                        if (smartCull && distantFromCamera) {
                            int renderSectionOriginX = SectionPos.sectionToBlockCoord(sectionPos.getX());
                            int renderSectionOriginY = SectionPos.sectionToBlockCoord(sectionPos.getY());
                            int renderSectionOriginZ = SectionPos.sectionToBlockCoord(sectionPos.getZ());
                            boolean maxX = direction.getAxis() == Direction.Axis.X
                                ? cameraSectionCenter.getX() > renderSectionOriginX
                                : cameraSectionCenter.getX() < renderSectionOriginX;
                            boolean maxY = direction.getAxis() == Direction.Axis.Y
                                ? cameraSectionCenter.getY() > renderSectionOriginY
                                : cameraSectionCenter.getY() < renderSectionOriginY;
                            boolean maxZ = direction.getAxis() == Direction.Axis.Z
                                ? cameraSectionCenter.getZ() > renderSectionOriginZ
                                : cameraSectionCenter.getZ() < renderSectionOriginZ;
                            Vector3d checkPos = new Vector3d(
                                renderSectionOriginX + (maxX ? 16 : 0), renderSectionOriginY + (maxY ? 16 : 0), renderSectionOriginZ + (maxZ ? 16 : 0)
                            );
                            Vector3d step = new Vector3d(cameraPos.x, cameraPos.y, cameraPos.z).sub(checkPos).normalize().mul(CEILED_SECTION_DIAGONAL);
                            boolean visible = true;
                            while (checkPos.distanceSquared(cameraPos.x, cameraPos.y, cameraPos.z) > 3600.0) {
                                checkPos.add(step);
                                if (checkPos.y > this.viewArea.maxY() || checkPos.y < this.viewArea.minY()) {
                                    break;
                                }
                                SectionRenderDispatcher.RenderSection checkSection = this.viewArea
                                    .getRenderSectionAt(BlockPos.containing(checkPos.x, checkPos.y, checkPos.z));
                                if (checkSection == null || storage.sectionToNodeMap.get(checkSection) == null) {
                                    visible = false;
                                    break;
                                }
                            }
                            if (!visible) {
                                continue;
                            }
                        }

                        SectionOcclusionGraph.Node existingNode = storage.sectionToNodeMap.get(renderSectionAt);
                        if (existingNode != null) {
                            existingNode.addSourceDirection(direction);
                        } else {
                            SectionOcclusionGraph.Node newNode = new SectionOcclusionGraph.Node(renderSectionAt, direction, node.step + 1);
                            newNode.setDirections(node.directions, direction);
                            queue.add(newNode);
                            storage.sectionToNodeMap.put(renderSectionAt, newNode);
                        }
                    }
                }
            }
        }
    }

    private static Frustum offsetFrustum(final Frustum frustum) {
        return new Frustum(frustum).offsetToFullyIncludeCameraCube(8);
    }

    private boolean isInViewDistance(final SectionPos cameraSectionPos, final SectionPos sectionPos) {
        return ChunkTrackingView.isInViewDistance(
            cameraSectionPos.getX(),
            cameraSectionPos.getZ(),
            this.viewArea.getViewDistance(),
            sectionPos.getX(),
            sectionPos.getZ()
        );
    }

    private SectionRenderDispatcher.@Nullable RenderSection getRelativeFrom(
        final SectionPos cameraSectionPos,
        final SectionRenderDispatcher.RenderSection renderSection,
        final Direction direction
    ) {
        SectionPos relativePos = renderSection.getNeighborSectionPos(direction);
        if (!this.isInViewDistance(cameraSectionPos, relativePos)) {
            return null;
        }
        if (Mth.abs(cameraSectionPos.getY() - relativePos.getY()) > this.viewArea.getViewDistance()) {
            return null;
        }
        return this.viewArea.getRenderSection(relativePos);
    }

    // ===== updateEmptySections 参数仍为 LongOpenHashSet，内部转换 =====
    public void updateEmptySections(final it.unimi.dsi.fastutil.longs.LongOpenHashSet added, final it.unimi.dsi.fastutil.longs.LongOpenHashSet removed) {
        for (long node : added) {
            this.emptySections.add(SectionPos.of(node));
        }
        for (long node : removed) {
            SectionPos sectionPos = SectionPos.of(node);
            if (this.emptySections.remove(sectionPos)) {
                SectionRenderDispatcher.RenderSection section = this.viewArea.getRenderSection(sectionPos);
                if (section != null) {
                    this.schedulePropagationFrom(section);
                    section.setWasPreviouslyEmpty(true);
                }
            }
        }
    }

    // ===== updateLoadedChunks 参数仍为 LongOpenHashSet，内部转换 =====
    public void updateLoadedChunks(final it.unimi.dsi.fastutil.longs.LongOpenHashSet added, final it.unimi.dsi.fastutil.longs.LongOpenHashSet removed) {
        for (long node : added) {
            this.loadedChunks.add(ChunkPos.unpack(node));
        }
        for (long node : removed) {
            this.loadedChunks.remove(ChunkPos.unpack(node));
        }
    }

    public Octree getOctree() {
        return this.currentGraph.get().storage.sectionTree;
    }

    @VisibleForDebug
    public SectionOcclusionGraph.@Nullable Node getNode(final SectionRenderDispatcher.RenderSection section) {
        return this.currentGraph.get().storage.sectionToNodeMap.get(section);
    }

    // ==================== 内部类 ====================

    @OnlyIn(Dist.CLIENT)
    private record GraphState(SectionOcclusionGraph.GraphStorage storage, BlockingQueue<SectionRenderDispatcher.RenderSection> sectionsToPropagateFrom) {
        private GraphState(final ViewArea viewArea) {
            this(new SectionOcclusionGraph.GraphStorage(viewArea), new LinkedBlockingQueue<>());
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static class GraphStorage {
        public final SectionOcclusionGraph.SectionToNodeMap sectionToNodeMap;
        public final Octree sectionTree;
        // ===== 改为 ChunkPos -> List<SectionPos> =====
        public final Object2ObjectMap<ChunkPos, List<SectionPos>> sectionsWaitingForChunkLoads;

        public GraphStorage(final ViewArea viewArea) {
            this.sectionToNodeMap = new SectionOcclusionGraph.SectionToNodeMap(viewArea.size());
            this.sectionTree = new Octree(viewArea.getCameraSectionPos(), viewArea.getViewDistance(), viewArea.sectionCount(), viewArea.minY());
            this.sectionsWaitingForChunkLoads = new Object2ObjectOpenHashMap<>();
        }
    }

    @OnlyIn(Dist.CLIENT)
    @VisibleForDebug
    public static class Node {
        @VisibleForDebug
        protected final SectionRenderDispatcher.RenderSection section;
        private byte sourceDirections;
        private byte directions;
        @VisibleForDebug
        public final int step;

        private Node(final SectionRenderDispatcher.RenderSection section, final @Nullable Direction sourceDirection, final int step) {
            this.section = section;
            if (sourceDirection != null) {
                this.addSourceDirection(sourceDirection);
            }
            this.step = step;
        }

        private void setDirections(final byte oldDirections, final Direction direction) {
            this.directions = (byte)(this.directions | oldDirections | 1 << direction.ordinal());
        }

        private boolean hasDirection(final Direction direction) {
            return (this.directions & 1 << direction.ordinal()) > 0;
        }

        private void addSourceDirection(final Direction direction) {
            this.sourceDirections = (byte)(this.sourceDirections | this.sourceDirections | 1 << direction.ordinal());
        }

        @VisibleForDebug
        public boolean hasSourceDirection(final int directionOrdinal) {
            return (this.sourceDirections & 1 << directionOrdinal) > 0;
        }

        private boolean hasSourceDirections() {
            return this.sourceDirections != 0;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(this.section.getSectionPos().asLong());
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj instanceof SectionOcclusionGraph.Node other) {
                return this.section.getSectionPos().equals(other.section.getSectionPos());
            }
            return false;
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static class SectionToNodeMap {
        private final SectionOcclusionGraph.Node[] nodes;

        private SectionToNodeMap(final int sectionCount) {
            this.nodes = new SectionOcclusionGraph.Node[sectionCount];
        }

        public void put(final SectionRenderDispatcher.RenderSection renderSection, final SectionOcclusionGraph.Node node) {
            this.nodes[renderSection.index] = node;
        }

        public SectionOcclusionGraph.@Nullable Node get(final SectionRenderDispatcher.RenderSection renderSection) {
            int index = renderSection.index;
            return index >= 0 && index < this.nodes.length ? this.nodes[index] : null;
        }
    }
}