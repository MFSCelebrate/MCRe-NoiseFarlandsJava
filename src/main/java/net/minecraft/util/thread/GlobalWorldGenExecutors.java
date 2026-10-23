package net.minecraft.util.thread;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 🔧 MCRe（C2ME A2 简化版移植）：世界生成全局并行池。
 * 26.2 原版：ChunkTaskDispatcher 的任务经 ConsecutiveExecutor 一次一个串行执行（全局串行）；
 * 简化版：生成批直接提交到此池（配合 WorldGenLocks 写入半径锁）→ 不同 chunk 的生成任务并行。
 * 线程 NORM_PRIORITY-1（C2ME 同款，不与游戏主逻辑抢调度）。
 * <p>
 * 🔧 Vulkan 三期 step2a（GPU 时间戳面板实测：跑图卡顿时 GPU 饿等 400~800ms）：
 * 渲染临界区机制 — 渲染线程进入 render 段时把 worker 压到 MIN_PRIORITY（ART 映射 nice 19，
 * 几乎完全让路），帧末恢复。防止 BigInteger/BigDecimal 密集计算把渲染提交挤出 CPU。
 * 异常路径自愈：beginRenderCritical 检测上帧未关时先恢复再进入。
 */
public class GlobalWorldGenExecutors {
    private static final AtomicInteger COUNTER = new AtomicInteger(0);
    public static final int PARALLELISM = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
    private static final int WORKER_PRIORITY = Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1);
    private static final Set<Thread> WORKERS = ConcurrentHashMap.newKeySet();
    private static volatile boolean renderCritical = false;
    private static final ExecutorService POOL = Executors.newFixedThreadPool(PARALLELISM, runnable -> {
        final Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        thread.setPriority(WORKER_PRIORITY);
        thread.setName("MCRe-worldgen-%d".formatted(COUNTER.getAndIncrement()));
        WORKERS.add(thread);
        return thread;
    });

    private GlobalWorldGenExecutors() {
    }

    public static void execute(final Runnable task) {
        POOL.execute(task);
    }

    /** 渲染帧关键段开始：worker 全部压到 MIN_PRIORITY 让路（异常自愈：上帧未关先恢复）。 */
    public static void beginRenderCritical() {
        if (renderCritical) {
            renderCritical = false; // 上帧异常路径漏了 end，先恢复再进入
        }
        renderCritical = true;
        setWorkersPriority(Thread.MIN_PRIORITY);
    }

    /** 渲染帧关键段结束：worker 恢复正常优先级。 */
    public static void endRenderCritical() {
        if (!renderCritical) {
            return;
        }
        renderCritical = false;
        setWorkersPriority(WORKER_PRIORITY);
    }

    private static void setWorkersPriority(final int priority) {
        for (final Thread thread : WORKERS) {
            try {
                thread.setPriority(priority);
            } catch (final Throwable ignored) {
            }
        }
    }
}