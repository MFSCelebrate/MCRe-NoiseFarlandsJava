#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""C2ME step1 批次2：OreFeature BitSet 池 + MinecraftServer mid-tick 偷跑（dry-run 默认，--apply 落盘）"""
import sys

BASE = "/workspace/src/main/java"
OF = BASE + "/net/minecraft/world/level/levelgen/feature/OreFeature.java"
MS = BASE + "/net/minecraft/server/MinecraftServer.java"

EDITS = [
    # ============ OreFeature：generateVeinPart 的 NEW BitSet → 线程池复用 ============
    (OF,
"""import net.minecraft.util.Mth;""",
"""import net.minecraft.util.BitSetCacheUtil;
import net.minecraft.util.Mth;""",
     1),
    (OF,
"""        BitSet tested = new BitSet(sizeXZ * sizeY * sizeXZ);""",
"""        BitSet tested = BitSetCacheUtil.getCachedOrNewBitSet(sizeXZ * sizeY * sizeXZ);""",
     1),
    # ============ MinecraftServer：mid-tick 区块任务偷跑（间隔 2ms，防积压到 tick 尾） ============
    (MS,
"""    protected void tickServer(final BooleanSupplier haveTime) {""",
"""    // 🔧 MCRe（C2ME mid-tick 移植）：区块主线程执行器任务在 tick 中间偷跑（间隔控制，降区块相关卡顿尖峰）
    private static final long MID_TICK_CHUNK_TASKS_INTERVAL_NANOS = 2_000_000L;
    private long midTickLastRunNanos = System.nanoTime();

    protected void tickServer(final BooleanSupplier haveTime) {""",
     1),
    (MS,
"""        for (ServerLevel level : this.getAllLevels()) {
            profiler.push(() -> level + " " + level.dimension().identifier());
            profiler.push("tick");

            try {
                level.tick(haveTime);
            } catch (Throwable t) {
                CrashReport report = CrashReport.forThrowable(t, "Exception ticking world");
                level.fillReportDetails(report);
                throw new ReportedException(report);
            }

            profiler.pop();
            profiler.pop();
        }""",
"""        for (ServerLevel level : this.getAllLevels()) {
            // 🔧 MCRe（C2ME mid-tick 移植）：每个 world tick 前偷跑一次区块主线程任务（2ms 间隔内只跑一次）
            if (System.nanoTime() - this.midTickLastRunNanos >= MID_TICK_CHUNK_TASKS_INTERVAL_NANOS) {
                this.midTickLastRunNanos = System.nanoTime();
                level.getChunkSource().pollTask();
            }

            profiler.push(() -> level + " " + level.dimension().identifier());
            profiler.push("tick");

            try {
                level.tick(haveTime);
            } catch (Throwable t) {
                CrashReport report = CrashReport.forThrowable(t, "Exception ticking world");
                level.fillReportDetails(report);
                throw new ReportedException(report);
            }

            profiler.pop();
            profiler.pop();
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