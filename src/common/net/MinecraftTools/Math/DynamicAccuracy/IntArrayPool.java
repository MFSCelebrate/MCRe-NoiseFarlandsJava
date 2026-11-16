package net.MinecraftTools.Math.DynamicAccuracy;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 🔧 MCRe NoiseFarlands（2026-10-15 性能优化）：线程局部 int[] 缓冲池。
 *
 * <p>按 2 的幂分桶，acquire(minLen) 取 ≥minLen 的最小桶。
 * 大数运算（add/subtract/multiply/divide）的中间数组高频分配是 GC 压力主源。
 * 此池消除热路径 int[] 分配，预期减少 70%+ 内存分配。
 *
 * <p>⚠️ 线程安全：ThreadLocal，无竞争。
 * <p>⚠️ 归还的数组内容不保证清零（调用方自行覆盖）。
 * <p>⚠️ 池容量按桶限流（默认 4 个/桶），超出直接丢弃（GC 回收）。
 *
 * @author MCRe NoiseFarlands
 */
final class IntArrayPool {

    /** 最小桶大小（2^4 = 16 ints = 64 bytes） */
    private static final int MIN_BUCKET_SHIFT = 4;

    /** 最大桶大小（2^18 = 262144 ints = 1 MB） */
    private static final int MAX_BUCKET_SHIFT = 18;

    /** 每桶最多缓存的数组数 */
    private static final int MAX_PER_BUCKET = 4;

    /** 桶数量 */
    private static final int BUCKET_COUNT = MAX_BUCKET_SHIFT - MIN_BUCKET_SHIFT + 1;

    @SuppressWarnings("unchecked")
    private static final ThreadLocal<Deque<int[]>[]> POOLS = ThreadLocal.withInitial(() -> {
        @SuppressWarnings("unchecked")
        Deque<int[]>[] pools = new Deque[BUCKET_COUNT];
        for (int i = 0; i < BUCKET_COUNT; i++) {
            pools[i] = new ArrayDeque<>(MAX_PER_BUCKET);
        }
        return pools;
    });

    private IntArrayPool() {
    }

    /**
     * 计算给定长度对应的桶索引。
     * bucket shift = max(MIN_BUCKET_SHIFT, ceil(log2(minLen)))
     */
    private static int bucketIndex(final int minLen) {
        if (minLen <= (1 << MIN_BUCKET_SHIFT)) {
            return 0;
        }
        // ceil(log2(minLen))
        int shift = 32 - Integer.numberOfLeadingZeros(minLen - 1);
        if (shift > MAX_BUCKET_SHIFT) {
            return -1; // 超出最大桶，直接分配
        }
        return shift - MIN_BUCKET_SHIFT;
    }

    /**
     * 从池中借取一个 ≥ minLen 的 int[]。
     * 如果池中没有合适的，分配新的。
     *
     * @param minLen 最小长度（元素数）
     * @return ≥ minLen 的 int[]（内容未定义，调用方必须覆盖）
     */
    static int[] acquire(final int minLen) {
        int idx = bucketIndex(minLen);
        if (idx < 0) {
            // 超出最大桶，直接分配
            return new int[minLen];
        }
        Deque<int[]> pool = POOLS.get()[idx];
        int[] arr = pool.pollFirst();
        if (arr != null) {
            return arr;
        }
        // 池空，分配该桶标准大小
        return new int[1 << (idx + MIN_BUCKET_SHIFT)];
    }

    /**
     * 归还一个 int[] 到池中（内容不清零）。
     * 超出最大桶大小或桶已满时直接丢弃（让 GC 回收）。
     *
     * @param arr 要归还的数组（调用方保证不再引用）
     */
    static void release(final int[] arr) {
        if (arr == null || arr.length == 0) {
            return;
        }
        int idx = bucketIndex(arr.length);
        if (idx < 0) {
            return; // 超大数组，不池化
        }
        Deque<int[]> pool = POOLS.get()[idx];
        if (pool.size() < MAX_PER_BUCKET) {
            pool.offerFirst(arr);
        }
    }

    /**
     * 清空当前线程的所有池（用于极端内存压力场景）。
     */
    static void clearAll() {
        Deque<int[]>[] pools = POOLS.get();
        for (Deque<int[]> pool : pools) {
            pool.clear();
        }
    }

    /**
     * 获取池统计信息（调试用）。
     */
    static String poolStats() {
        Deque<int[]>[] pools = POOLS.get();
        StringBuilder sb = new StringBuilder("IntArrayPool[");
        for (int i = 0; i < BUCKET_COUNT; i++) {
            int size = 1 << (i + MIN_BUCKET_SHIFT);
            sb.append(size).append(':').append(pools[i].size());
            if (i < BUCKET_COUNT - 1) sb.append(", ");
        }
        return sb.append(']').toString();
    }
}