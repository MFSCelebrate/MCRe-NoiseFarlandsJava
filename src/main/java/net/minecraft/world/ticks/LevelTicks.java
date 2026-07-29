package net.minecraft.world.ticks;
import it.unimi.dsi.fastutil.longs.LongSet;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.LongPredicate;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public class LevelTicks<T> implements LevelTickAccess<T> {
    private static final Comparator<LevelChunkTicks<?>> CONTAINER_DRAIN_ORDER = (o1, o2) -> ScheduledTick.INTRA_TICK_DRAIN_ORDER.compare(o1.peek(), o2.peek());
    private final LongPredicate tickCheck;
    // ===== 修改：键类型 long -> ChunkPos =====
    private final Object2ObjectMap<ChunkPos, LevelChunkTicks<T>> allContainers = new Object2ObjectOpenHashMap<>();
    private final Object2LongMap<ChunkPos> nextTickForContainer = Util.make(new Object2LongOpenHashMap<>(), m -> m.defaultReturnValue(Long.MAX_VALUE));
    private final Queue<LevelChunkTicks<T>> containersToTick = new PriorityQueue<>(CONTAINER_DRAIN_ORDER);
    private final Queue<ScheduledTick<T>> toRunThisTick = new ArrayDeque<>();
    private final List<ScheduledTick<T>> alreadyRunThisTick = new ArrayList<>();
    private final Set<ScheduledTick<?>> toRunThisTickSet = new ObjectOpenCustomHashSet<>(ScheduledTick.UNIQUE_TICK_HASH);
    private final BiConsumer<LevelChunkTicks<T>, ScheduledTick<T>> chunkScheduleUpdater = (container, newTick) -> {
        if (newTick.equals(container.peek())) {
            this.updateContainerScheduling(newTick);
        }
    };

    public LevelTicks(final LongPredicate tickCheck) {
        this.tickCheck = tickCheck;
    }

    public void addContainer(final ChunkPos pos, final LevelChunkTicks<T> container) {
        // ===== 直接使用 ChunkPos 对象作为键 =====
        this.allContainers.put(pos, container);
        ScheduledTick<T> nextTick = container.peek();
        if (nextTick != null) {
            this.nextTickForContainer.put(pos, nextTick.triggerTick());
        }

        container.setOnTickAdded(this.chunkScheduleUpdater);
    }

    public void removeContainer(final ChunkPos pos) {
        LevelChunkTicks<T> removedContainer = this.allContainers.remove(pos);
        this.nextTickForContainer.removeLong(pos);
        if (removedContainer != null) {
            removedContainer.setOnTickAdded(null);
        }
    }

    @Override
    public void schedule(final ScheduledTick<T> tick) {
        // ===== 使用 new ChunkPos(tick.pos()) 替代 ChunkPos.pack =====
        ChunkPos chunkPos = new ChunkPos(tick.pos());
        LevelChunkTicks<T> tickContainer = this.allContainers.get(chunkPos);
        if (tickContainer == null) {
            Util.logAndPauseIfInIde("Trying to schedule tick in not loaded position " + tick.pos());
        } else {
            tickContainer.schedule(tick);
        }
    }

    public void tick(final long currentTick, final int maxTicksToProcess, final BiConsumer<BlockPos, T> output) {
        ProfilerFiller profiler = Profiler.get();
        profiler.push("collect");
        this.collectTicks(currentTick, maxTicksToProcess, profiler);
        profiler.popPush("run");
        profiler.incrementCounter("ticksToRun", this.toRunThisTick.size());
        this.runCollectedTicks(output);
        profiler.popPush("cleanup");
        this.cleanupAfterTick();
        profiler.pop();
    }

    private void collectTicks(final long currentTick, final int maxTicksToProcess, final ProfilerFiller profiler) {
        this.sortContainersToTick(currentTick);
        profiler.incrementCounter("containersToTick", this.containersToTick.size());
        this.drainContainers(currentTick, maxTicksToProcess);
        this.rescheduleLeftoverContainers();
    }

    private void sortContainersToTick(final long currentTick) {
        // ===== 使用 Object2LongMap 的迭代方式 =====
        ObjectIterator<Object2LongMap.Entry<ChunkPos>> it = Object2LongMaps.fastIterator(this.nextTickForContainer);

        while (it.hasNext()) {
            Object2LongMap.Entry<ChunkPos> entry = it.next();
            ChunkPos chunkPos = entry.getKey();
            long nextTick = entry.getLongValue();
            if (nextTick <= currentTick) {
                LevelChunkTicks<T> candidateContainer = this.allContainers.get(chunkPos);
                if (candidateContainer == null) {
                    it.remove();
                } else {
                    ScheduledTick<T> scheduledTick = candidateContainer.peek();
                    if (scheduledTick == null) {
                        it.remove();
                    } else if (scheduledTick.triggerTick() > currentTick) {
                        entry.setValue(scheduledTick.triggerTick());
                    } else if (this.tickCheck.test(chunkPos.pack())) { // 外部 API 仍用 long，保留 pack()
                        it.remove();
                        this.containersToTick.add(candidateContainer);
                    }
                }
            }
        }
    }

    private void drainContainers(final long currentTick, final int maxTicksToProcess) {
        LevelChunkTicks<T> topContainer;
        while (this.canScheduleMoreTicks(maxTicksToProcess) && (topContainer = this.containersToTick.poll()) != null) {
            ScheduledTick<T> tick = topContainer.poll();
            this.scheduleForThisTick(tick);
            this.drainFromCurrentContainer(this.containersToTick, topContainer, currentTick, maxTicksToProcess);
            ScheduledTick<T> nextTick = topContainer.peek();
            if (nextTick != null) {
                if (nextTick.triggerTick() <= currentTick && this.canScheduleMoreTicks(maxTicksToProcess)) {
                    this.containersToTick.add(topContainer);
                } else {
                    this.updateContainerScheduling(nextTick);
                }
            }
        }
    }

    private void rescheduleLeftoverContainers() {
        for (LevelChunkTicks<T> container : this.containersToTick) {
            this.updateContainerScheduling(container.peek());
        }
    }

    private void updateContainerScheduling(final ScheduledTick<T> nextTick) {
        // ===== 使用 new ChunkPos 替代 ChunkPos.pack =====
        this.nextTickForContainer.put(new ChunkPos(nextTick.pos()), nextTick.triggerTick());
    }

    private void drainFromCurrentContainer(
        final Queue<LevelChunkTicks<T>> containersToTick, final LevelChunkTicks<T> currentContainer, final long currentTick, final int maxTicksToProcess
    ) {
        if (this.canScheduleMoreTicks(maxTicksToProcess)) {
            LevelChunkTicks<T> nextBestContainer = containersToTick.peek();
            ScheduledTick<T> nextFromNextContainer = nextBestContainer != null ? nextBestContainer.peek() : null;

            while (this.canScheduleMoreTicks(maxTicksToProcess)) {
                ScheduledTick<T> nextFromCurrentContainer = currentContainer.peek();
                if (nextFromCurrentContainer == null
                    || nextFromCurrentContainer.triggerTick() > currentTick
                    || nextFromNextContainer != null && ScheduledTick.INTRA_TICK_DRAIN_ORDER.compare(nextFromCurrentContainer, nextFromNextContainer) > 0) {
                    break;
                }

                currentContainer.poll();
                this.scheduleForThisTick(nextFromCurrentContainer);
            }
        }
    }

    private void scheduleForThisTick(final ScheduledTick<T> tick) {
        this.toRunThisTick.add(tick);
    }

    private boolean canScheduleMoreTicks(final int maxTicksToProcess) {
        return this.toRunThisTick.size() < maxTicksToProcess;
    }

    private void runCollectedTicks(final BiConsumer<BlockPos, T> output) {
        while (!this.toRunThisTick.isEmpty()) {
            ScheduledTick<T> entry = this.toRunThisTick.poll();
            if (!this.toRunThisTickSet.isEmpty()) {
                this.toRunThisTickSet.remove(entry);
            }

            this.alreadyRunThisTick.add(entry);
            output.accept(entry.pos(), entry.type());
        }
    }

    private void cleanupAfterTick() {
        this.toRunThisTick.clear();
        this.containersToTick.clear();
        this.alreadyRunThisTick.clear();
        this.toRunThisTickSet.clear();
    }

    @Override
    public boolean hasScheduledTick(final BlockPos pos, final T block) {
        // ===== 使用 new ChunkPos(pos) 替代 ChunkPos.pack =====
        LevelChunkTicks<T> tickContainer = this.allContainers.get(new ChunkPos(pos));
        return tickContainer != null && tickContainer.hasScheduledTick(pos, block);
    }

    @Override
    public boolean willTickThisTick(final BlockPos pos, final T type) {
        this.calculateTickSetIfNeeded();
        return this.toRunThisTickSet.contains(ScheduledTick.probe(type, pos));
    }

    private void calculateTickSetIfNeeded() {
        if (this.toRunThisTickSet.isEmpty() && !this.toRunThisTick.isEmpty()) {
            this.toRunThisTickSet.addAll(this.toRunThisTick);
        }
    }

    private void forContainersInArea(final BoundingBox bb, final LevelTicks.PosAndContainerConsumer<T> output) {
        int xMin = SectionPos.posToSectionCoord(bb.minX());
        int zMin = SectionPos.posToSectionCoord(bb.minZ());
        int xMax = SectionPos.posToSectionCoord(bb.maxX());
        int zMax = SectionPos.posToSectionCoord(bb.maxZ());

        for (int x = xMin; x <= xMax; x++) {
            for (int z = zMin; z <= zMax; z++) {
                // ===== 使用 new ChunkPos(x, z) 替代 ChunkPos.pack =====
                ChunkPos containerPos = new ChunkPos(x, z);
                LevelChunkTicks<T> container = this.allContainers.get(containerPos);
                if (container != null) {
                    output.accept(containerPos, container);
                }
            }
        }
    }

    public void clearArea(final BoundingBox area) {
        Predicate<ScheduledTick<T>> tickInsideBB = t -> area.isInside(t.pos());
        this.forContainersInArea(area, (pos, container) -> {
            ScheduledTick<T> previousTop = container.peek();
            container.removeIf(tickInsideBB);
            ScheduledTick<T> newTop = container.peek();
            if (newTop != previousTop) {
                if (newTop != null) {
                    this.updateContainerScheduling(newTop);
                } else {
                    this.nextTickForContainer.removeLong(pos);
                }
            }
        });
        this.alreadyRunThisTick.removeIf(tickInsideBB);
        this.toRunThisTick.removeIf(tickInsideBB);
    }

    public void copyArea(final BoundingBox area, final Vec3i offset) {
        this.copyAreaFrom(this, area, offset);
    }

    public void copyAreaFrom(final LevelTicks<T> source, final BoundingBox area, final Vec3i offset) {
        List<ScheduledTick<T>> ticksToAdd = new ArrayList<>();
        Predicate<ScheduledTick<T>> tickInsideBB = t -> area.isInside(t.pos());
        source.alreadyRunThisTick.stream().filter(tickInsideBB).forEach(ticksToAdd::add);
        source.toRunThisTick.stream().filter(tickInsideBB).forEach(ticksToAdd::add);
        source.forContainersInArea(area, (pos, container) -> container.getAll().filter(tickInsideBB).forEach(ticksToAdd::add));
        LongSummaryStatistics info = ticksToAdd.stream().mapToLong(ScheduledTick::subTickOrder).summaryStatistics();
        long minSubTick = info.getMin();
        long maxSubTick = info.getMax();
        ticksToAdd.forEach(
            tick -> this.schedule(
                new ScheduledTick<>(
                    tick.type(), tick.pos().offset(offset), tick.triggerTick(), tick.priority(), tick.subTickOrder() - minSubTick + maxSubTick + 1L
                )
            )
        );
    }

    @Override
    public int count() {
        return this.allContainers.values().stream().mapToInt(TickAccess::count).sum();
    }

    // ===== 修改：内部接口参数改为 ChunkPos =====
    @FunctionalInterface
    private interface PosAndContainerConsumer<T> {
        void accept(ChunkPos pos, LevelChunkTicks<T> container);
    }
}