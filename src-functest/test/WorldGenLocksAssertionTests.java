package test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.gui.screens.worldselection.WorldMainSettingScreen;
import net.minecraft.util.thread.WorldGenLocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 🔧 MCRe 功能性测试：<b>「断言调试」的互斥归属断言在并发下不得误报</b>。
 *
 * <p>本测试开启 {@code debugAssertionsMultiThread} 后，让多个线程并发争抢**重叠**的世界生成半径锁区域，
 * 断言 {@code WorldGenLocks.runLocked} 内部的「锁单元互斥归属」检查**一次都不该触发**。
 *
 * <p>历史背景：归属登记表曾在 {@code finally} 里「先放锁、后撤登记」，中间留下窗口 → 下一个线程
 * 拿到锁后会看到**陈旧登记** → 大量假阳性「同一锁单元同时被两个线程持有」（真机日志实录）。
 * 修复方式：<b>先撤销归属登记，再放锁</b>，使不变量「登记表里有我」⟺「锁还握在我手里」成立。
 *
 * <p>真实互斥本身由 {@code test.WorldGenLocksUnitTests}（单元测试根）用独立计数器守住；
 * 本测试专门守住「断言工具本身不误报」。
 */
class WorldGenLocksAssertionTests {

    private WorldMainSettingScreen.FarLandsConfigData config;

    @BeforeEach
    void setUp() {
        this.config = WorldMainSettingScreen.FarLandsConfigData.activeConfig;
        this.config.debugAssertionsMultiThread = true;
    }

    @AfterEach
    void tearDown() {
        this.config.debugAssertionsMultiThread = false;
    }

    @Test
    @DisplayName("并发争抢重叠半径锁时，「互斥归属」断言零误报")
    void exclusiveOwnershipAssertionDoesNotFalsePositive() throws Exception {
        final int threadCount = 8;
        final int iterations = 200;
        final AtomicInteger conflicts = new AtomicInteger();
        final AtomicInteger completed = new AtomicInteger();
        final AtomicReference<Throwable> firstError = new AtomicReference<>();

        final ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        final CountDownLatch start = new CountDownLatch(1);

        for (int t = 0; t < threadCount; t++) {
            final int id = t;
            pool.submit(() -> {
                try {
                    start.await();

                    for (int i = 0; i < iterations; i++) {
                        final long centerX = id * 2L + (i % 3);
                        final long centerZ = (i % 5) - 2;

                        try {
                            WorldGenLocks.runLocked(centerX, centerZ, completed::incrementAndGet);
                        } catch (final IllegalStateException e) {
                            conflicts.incrementAndGet();
                            firstError.compareAndSet(null, e);
                        }
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
        assertEquals(0, conflicts.get(),
                "互斥归属断言误报：" + (firstError.get() == null ? "" : firstError.get().getMessage()));
    }
}
