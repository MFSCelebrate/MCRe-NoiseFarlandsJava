package test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.util.thread.WorldGenLocks;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 🔧 MCRe 单元测试：<b>世界生成半径锁（{@code WorldGenLocks}）的真实互斥性</b>。
 *
 * <p>本类位于 {@code net.minecraft.util.thread}，但**零 Minecraft 运行时依赖**（纯 JDK：锁 + 原子计数），
 * 因此归入单元测试根（{@code src-unittest/test}）——它守的是并行 worldgen 最核心的不变量：
 * <b>同一个锁单元任何时刻只能被一个线程持有</b>。
 *
 * <p>背景：本项目曾因「断言调试」的归属登记表**释放顺序**写错（先放锁、后撤登记）而产生大量
 * 「同一锁单元同时被两个线程持有」的**假阳性**。本测试用**与断言登记表完全无关的独立计数器**
 * 来判定真实互斥，从而把「锁坏了」与「断言误报」这两件事彻底分开。
 */
class WorldGenLocksUnitTests {

    /** 锁半径（与 {@code WorldGenLocks} 内部一致：±2 → 5×5 = 25 个单元）。 */
    private static final int LOCK_RADIUS = 2;

    @Test
    @DisplayName("半径锁：重叠区域必须互斥（任何时刻同一锁单元只被一个线程持有），且不死锁")
    void overlappingRegionsAreMutuallyExclusive() throws Exception {
        final int threadCount = 8;
        final int iterations = 200;

        // 每个锁单元的「当前持有者计数」——独立于断言登记表
        final ConcurrentHashMap<Long, AtomicInteger> holders = new ConcurrentHashMap<>();
        final AtomicInteger violations = new AtomicInteger();
        final AtomicInteger completed = new AtomicInteger();

        final ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        final CountDownLatch start = new CountDownLatch(1);

        for (int t = 0; t < threadCount; t++) {
            final int id = t;
            pool.submit(() -> {
                try {
                    start.await();

                    for (int i = 0; i < iterations; i++) {
                        // 相邻中心 → ±2 半径的区域必然重叠（正是历史上出问题的位置）
                        final long centerX = id * 2L + (i % 3);
                        final long centerZ = (i % 5) - 2;

                        WorldGenLocks.runLocked(centerX, centerZ, () -> {
                            for (long dx = -LOCK_RADIUS; dx <= LOCK_RADIUS; dx++) {
                                for (long dz = -LOCK_RADIUS; dz <= LOCK_RADIUS; dz++) {
                                    final long key = ((centerX + dx) << 32) | ((centerZ + dz) & 0xFFFFFFFFL);
                                    final AtomicInteger counter = holders.computeIfAbsent(key, k -> new AtomicInteger());
                                    final int count = counter.incrementAndGet();

                                    if (count > 1) {
                                        violations.incrementAndGet();
                                    }

                                    // 稍作让步，放大临界区窗口，让潜在违规更容易被观测到
                                    Thread.onSpinWait();
                                    counter.decrementAndGet();
                                }
                            }

                            completed.incrementAndGet();
                        });
                    }
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        start.countDown();
        pool.shutdown();

        assertTrue(pool.awaitTermination(120, TimeUnit.SECONDS), "线程池未在 120 秒内结束（疑似死锁）");
        assertEquals(threadCount * iterations, completed.get(), "并非全部任务都执行完成");
        assertEquals(0, violations.get(), "同一锁单元被两个线程同时持有 → 半径锁的真实互斥被破坏");
    }
}
