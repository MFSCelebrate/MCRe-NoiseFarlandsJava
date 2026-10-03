#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""C2ME step2：A3b idle autosave + A2 简化版多线程 worldgen（dry-run 默认，--apply 落盘）"""
import sys

BASE = "/workspace/src/main/java"
CTD = BASE + "/net/minecraft/server/level/ChunkTaskDispatcher.java"
CM = BASE + "/net/minecraft/server/level/ChunkMap.java"
SCC = BASE + "/net/minecraft/server/level/ServerChunkCache.java"
MC = BASE + "/net/minecraft/server/MinecraftServer.java"

EDITS = [
    # ============ A2：ChunkTaskDispatcher 并行开关 ============
    (CTD,
"""import net.minecraft.util.thread.TaskScheduler;""",
"""import net.minecraft.util.thread.GlobalWorldGenExecutors;
import net.minecraft.util.thread.TaskScheduler;
import net.minecraft.util.thread.WorldGenLocks;""",
     1),
    (CTD,
"""    private final PriorityConsecutiveExecutor dispatcher;
    protected boolean sleeping;""",
"""    private final PriorityConsecutiveExecutor dispatcher;
    protected boolean sleeping;
    private final boolean parallel;""",
     1),
    (CTD,
"""    public ChunkTaskDispatcher(final TaskScheduler<Runnable> executor, final Executor dispatcherExecutor) {
        this.queue = new ChunkTaskPriorityQueue(executor.name() + "_queue");
        this.executor = executor;
        this.dispatcher = new PriorityConsecutiveExecutor(4, dispatcherExecutor, "dispatcher");
        this.sleeping = true;
    }""",
"""    public ChunkTaskDispatcher(final TaskScheduler<Runnable> executor, final Executor dispatcherExecutor, final boolean parallel) {
        this.queue = new ChunkTaskPriorityQueue(executor.name() + "_queue");
        this.executor = executor;
        this.dispatcher = new PriorityConsecutiveExecutor(4, dispatcherExecutor, "dispatcher");
        this.sleeping = true;
        this.parallel = parallel;
    }""",
     1),
    (CTD,
"""    protected void scheduleForExecution(final ChunkTaskPriorityQueue.TasksForChunk tasksForChunk) {
        CompletableFuture.allOf(tasksForChunk.tasks().stream().map(message -> this.executor.scheduleWithResult(future -> {
            message.run();
            future.complete(Unit.INSTANCE);
        })).toArray(CompletableFuture[]::new)).thenAccept(r -> this.pollTask());
    }""",
"""    protected void scheduleForExecution(final ChunkTaskPriorityQueue.TasksForChunk tasksForChunk) {
        if (!this.parallel) {
            // 原版串行链：light dispatcher 保持串行（光照引擎共享状态非线程安全，并行会竞争）
            CompletableFuture.allOf(tasksForChunk.tasks().stream().map(message -> this.executor.scheduleWithResult(future -> {
                message.run();
                future.complete(Unit.INSTANCE);
            })).toArray(CompletableFuture[]::new)).thenAccept(r -> this.pollTask());
            return;
        }

        // 🔧 MCRe（C2ME A2 简化版移植）：破除批间串行 —— 批提交到全局并行池（写入半径锁互斥：
        // 同 chunk 跨批串行 + 结构 piece 跨 chunk 写入防竞争），立即 pollTask 补货（队列驱动无栈递归）
        // → 多个 chunk 的生成任务并行执行。原版：allOf 等批完成 + ConsecutiveExecutor 一次一个 = 全局串行。
        final long lockCenterX = tasksForChunk.chunkPos().x();
        final long lockCenterZ = tasksForChunk.chunkPos().z();
        GlobalWorldGenExecutors.execute(() -> WorldGenLocks.runLocked(lockCenterX, lockCenterZ, () -> {
            for (Runnable task : tasksForChunk.tasks()) {
                task.run();
            }
        }));
        this.pollTask();
    }""",
     1),
    # ============ A2：ChunkMap 两个 dispatcher 的并行参数 ============
    (CM,
"""        this.worldgenTaskDispatcher = new ChunkTaskDispatcher(worldgen, executor);
        this.lightTaskDispatcher = new ChunkTaskDispatcher(light, executor);""",
"""        // 🔧 MCRe（A2 简化版）：worldgen 并行化（全局池 + 写入半径锁）；light 保持串行（光照引擎共享状态非线程安全）
        this.worldgenTaskDispatcher = new ChunkTaskDispatcher(worldgen, executor, true);
        this.lightTaskDispatcher = new ChunkTaskDispatcher(light, executor, false);""",
     1),
    # ============ A3b：ChunkMap 空闲渐进保存一个区块 ============
    (CM,
"""    private boolean save(final ChunkAccess chunk) {
        this.poiManager.flush(chunk.getPos());
        if (!chunk.tryMarkSaved()) {
            return false;""",
"""    // 🔧 MCRe（C2ME idle autosave 移植）：空闲间隙渐进式保存一个未保存区块（防 tick 内全量自动保存卡顿尖峰）
    // 语义对齐 saveAllChunks：filter(wasAccessibleSinceLastSave) → refreshAccessibility（先重置标志）→ save
    public boolean saveNextIdleChunk() {
        int searched = 0;
        for (ChunkHolder chunkHolder : this.visibleChunkMap.values()) {
            if (searched++ >= 256) {
                break;
            }

            if (!chunkHolder.wasAccessibleSinceLastSave() || !chunkHolder.isReadyForSaving()) {
                continue;
            }

            chunkHolder.refreshAccessibility();

            ChunkAccess chunk = chunkHolder.getLatestChunk();
            if (chunk == null || !(chunk instanceof ImposterProtoChunk || chunk instanceof LevelChunk)) {
                continue;
            }

            if (this.save(chunk)) {
                return true;
            }
        }

        return false;
    }

    private boolean save(final ChunkAccess chunk) {
        this.poiManager.flush(chunk.getPos());
        if (!chunk.tryMarkSaved()) {
            return false;""",
     1),
    # ============ A3b：ServerChunkCache 委托 ============
    (SCC,
"""    public boolean pollTask() {
        return this.mainThreadProcessor.pollTask();
    }""",
"""    public boolean pollTask() {
        return this.mainThreadProcessor.pollTask();
    }

    // 🔧 MCRe（C2ME idle autosave 移植）：空闲间隙渐进式保存一个未保存区块
    public boolean saveNextIdleChunk() {
        return this.chunkMap.saveNextIdleChunk();
    }""",
     1),
    # ============ A3b：MinecraftServer.pollTaskInternal 空闲保存段 ============
    (MC,
"""        if (this.tickRateManager.isSprinting() || this.shouldRunAllTasks() || this.haveTime()) {
            for (ServerLevel level : this.getAllLevels()) {
                if (level.getChunkSource().pollTask()) {
                    return true;
                }
            }
        }

        return false;
    }""",
"""        if (this.tickRateManager.isSprinting() || this.shouldRunAllTasks() || this.haveTime()) {
            for (ServerLevel level : this.getAllLevels()) {
                if (level.getChunkSource().pollTask()) {
                    return true;
                }
            }
        }

        // 🔧 MCRe（C2ME idle autosave 移植）：空闲间隙渐进式保存一个未保存区块（防 tick 内全量自动保存卡顿尖峰）
        if (!this.tickRateManager.isSprinting() && this.haveTime()) {
            for (ServerLevel level : this.getAllLevels()) {
                if (level.getChunkSource().saveNextIdleChunk()) {
                    return false;
                }
            }
        }

        return false;
    }""",
     1),
]

def main():
    apply = "--apply" in sys.argv
    files = {}
    failures = 0
    for path, old, new, cnt in EDITS:
        if path not in files:
            with open(path, encoding="utf-8") as f:
                files[path] = f.read()
        n = files[path].count(old)
        if n != cnt:
            print("MISMATCH %s expect=%d got=%d :: %r" % (path.split("/")[-1], cnt, n, old[:70]))
            failures += 1
    if failures:
        print("ABORT: %d mismatches, nothing written" % failures)
        sys.exit(1)
    if not apply:
        print("DRY RUN OK: %d edits, all counts match" % len(EDITS))
        return
    for path, old, new, cnt in EDITS:
        files[path] = files[path].replace(old, new)
    for path, text in files.items():
        with open(path, "w", encoding="utf-8") as f:
            f.write(text)
        print("APPLIED %s" % path.split("/")[-1])
    print("ALL APPLIED: %d edits across %d files" % (len(EDITS), len(files)))

main()